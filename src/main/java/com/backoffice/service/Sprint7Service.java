package com.backoffice.service;

import com.backoffice.dao.ParametreDAO;
import com.backoffice.dao.ReservationDAO;
import com.backoffice.model.Reservation;
import com.backoffice.model.Vehicule;

import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service Sprint 7:
 * - Fractionnement des reservations si aucun vehicule ne peut prendre tous les passagers.
 * - A capacite egale, priorite au vehicule ayant le moins de trajets du jour.
 * - Conservation du flux Sprint 5 via l'assignation standard avant traitement des cas restants.
 */
public class Sprint7Service {

    private final PlanificationService planificationService = new PlanificationService();
    private final ReservationService reservationService = new ReservationService();
    private final ReservationDAO reservationDAO = new ReservationDAO();
    private final ParametreDAO parametreDAO = new ParametreDAO();

    public static class ExecutionResult {
        private final int nonAssigneesInitiales;
        private final int reservationsTraitees;
        private final int reservationsFractionnees;
        private final int morceauxCrees;
        private final int passagersFractionnes;
        private final int nonAssigneesFinales;

        public ExecutionResult(int nonAssigneesInitiales,
                               int reservationsTraitees,
                               int reservationsFractionnees,
                               int morceauxCrees,
                               int passagersFractionnes,
                               int nonAssigneesFinales) {
            this.nonAssigneesInitiales = nonAssigneesInitiales;
            this.reservationsTraitees = reservationsTraitees;
            this.reservationsFractionnees = reservationsFractionnees;
            this.morceauxCrees = morceauxCrees;
            this.passagersFractionnes = passagersFractionnes;
            this.nonAssigneesFinales = nonAssigneesFinales;
        }

        public int getNonAssigneesInitiales() {
            return nonAssigneesInitiales;
        }

        public int getReservationsTraitees() {
            return reservationsTraitees;
        }

        public int getReservationsFractionnees() {
            return reservationsFractionnees;
        }

        public int getMorceauxCrees() {
            return morceauxCrees;
        }

        public int getPassagersFractionnes() {
            return passagersFractionnes;
        }

        public int getNonAssigneesFinales() {
            return nonAssigneesFinales;
        }
    }

    public ExecutionResult executer(Date date) throws SQLException {
        reservationDAO.ensureReservationVehiculeTable();

        // 1) Conserver les regles Sprint 5 en les executant d'abord.
        planificationService.assignerVehiculesAutomatiquement(date);
        reservationDAO.synchroniserReservationVehiculeDepuisReservation(date);

        // 2) Traiter les reservations encore non assignees avec la logique Sprint 7.
        List<Reservation> nonAssignees = reservationDAO.findWithoutVehiculeByDate(date);
        int nonAssigneesInitiales = nonAssignees.size();

        if (nonAssignees.isEmpty()) {
            return new ExecutionResult(0, 0, 0, 0, 0, 0);
        }

        nonAssignees.sort(Comparator.comparing(Reservation::getDateHeureArrivee));

        int taMinutes = parametreDAO.getTempsAttente();
        List<List<Reservation>> fenetres = construireFenetresTA(nonAssignees, taMinutes);

        Map<Integer, Integer> trajetsParVehicule = new HashMap<>(reservationDAO.getNombreTrajetsParVehicule(date));

        int reservationsTraitees = 0;
        int reservationsFractionnees = 0;
        int morceauxCrees = 0;
        int passagersFractionnes = 0;

        for (List<Reservation> fenetre : fenetres) {
            if (fenetre.isEmpty()) {
                continue;
            }

            fenetre.sort((a, b) -> {
                int cmpPax = Integer.compare(b.getNombrePassager(), a.getNombrePassager());
                if (cmpPax != 0) {
                    return cmpPax;
                }
                return a.getDateHeureArrivee().compareTo(b.getDateHeureArrivee());
            });

            Timestamp heureDepartFenetre = calculerHeureDepartFenetre(fenetre);

            for (Reservation reservation : fenetre) {
                if (reservation.getIdVehicule() != null) {
                    continue;
                }

                int totalPax = reservation.getNombrePassager();
                int chunks = assignerAvecFractionnement(reservation, heureDepartFenetre, trajetsParVehicule);
                reservationsTraitees++;

                if (chunks > 1) {
                    reservationsFractionnees++;
                    morceauxCrees += (chunks - 1);
                    passagersFractionnes += totalPax;
                }
            }
        }

        int nonAssigneesFinales = reservationDAO.findWithoutVehiculeByDate(date).size();
        reservationDAO.synchroniserReservationVehiculeDepuisReservation(date);

        return new ExecutionResult(
            nonAssigneesInitiales,
            reservationsTraitees,
            reservationsFractionnees,
            morceauxCrees,
            passagersFractionnes,
            nonAssigneesFinales
        );
    }

    private List<List<Reservation>> construireFenetresTA(List<Reservation> reservations, int taMinutes) {
        List<List<Reservation>> fenetres = new ArrayList<>();

        if (reservations == null || reservations.isEmpty()) {
            return fenetres;
        }

        long taMillis = taMinutes * 60L * 1000L;
        List<Reservation> fenetreCourante = new ArrayList<>();
        Timestamp debutFenetre = reservations.get(0).getDateHeureArrivee();

        for (Reservation reservation : reservations) {
            Timestamp arrivee = reservation.getDateHeureArrivee();
            if (arrivee.getTime() <= debutFenetre.getTime() + taMillis) {
                fenetreCourante.add(reservation);
            } else {
                fenetres.add(new ArrayList<>(fenetreCourante));
                fenetreCourante.clear();
                fenetreCourante.add(reservation);
                debutFenetre = arrivee;
            }
        }

        if (!fenetreCourante.isEmpty()) {
            fenetres.add(fenetreCourante);
        }

        return fenetres;
    }

    private Timestamp calculerHeureDepartFenetre(List<Reservation> fenetre) {
        Timestamp max = fenetre.get(0).getDateHeureArrivee();
        for (Reservation reservation : fenetre) {
            if (reservation.getDateHeureArrivee().after(max)) {
                max = reservation.getDateHeureArrivee();
            }
        }
        return max;
    }

    /**
     * Retourne le nombre de morceaux crees pour cette reservation (1 = pas de fractionnement).
     */
    private int assignerAvecFractionnement(Reservation reservation,
                                           Timestamp heureDepart,
                                           Map<Integer, Integer> trajetsParVehicule) throws SQLException {
        int paxRestants = reservation.getNombrePassager();
        int chunks = 0;
        boolean reservationPrincipaleUtilisee = false;

        while (paxRestants > 0) {
            List<Vehicule> vehiculesDisponibles = reservationService.getVehiculesDisponibles(heureDepart, reservation.getIdHotel());
            if (vehiculesDisponibles.isEmpty()) {
                break;
            }

            Vehicule choisi = choisirVehiculeSprint7(vehiculesDisponibles, paxRestants, trajetsParVehicule);
            if (choisi == null) {
                break;
            }

            int capaciteAffectee = Math.min(paxRestants, choisi.getNombrePlace());
            if (capaciteAffectee <= 0) {
                break;
            }

            if (!reservationPrincipaleUtilisee) {
                // La reservation d'origine devient le premier morceau.
                reservation.setNombrePassager(capaciteAffectee);
                reservation.setDateHeureArrivee(heureDepart);
                reservationDAO.update(reservation);
                reservationDAO.assignVehicule(reservation.getId(), choisi.getId());
                reservationDAO.upsertReservationVehicule(
                    reservation.getId(),
                    choisi.getId(),
                    capaciteAffectee,
                    heureDepart
                );
                reservation.setIdVehicule(choisi.getId());
                reservationPrincipaleUtilisee = true;
            } else {
                Reservation morceau = new Reservation();
                morceau.setClient(reservation.getClient() + " (split)");
                morceau.setNombrePassager(capaciteAffectee);
                morceau.setDateHeureArrivee(heureDepart);
                morceau.setIdHotel(reservation.getIdHotel());
                morceau.setNomHotel(reservation.getNomHotel());
                morceau.setIdVehicule(null);

                reservationDAO.insert(morceau);
                reservationDAO.assignVehicule(morceau.getId(), choisi.getId());
                reservationDAO.upsertReservationVehicule(
                    morceau.getId(),
                    choisi.getId(),
                    capaciteAffectee,
                    heureDepart
                );
            }

            trajetsParVehicule.put(choisi.getId(), trajetsParVehicule.getOrDefault(choisi.getId(), 0) + 1);

            paxRestants -= capaciteAffectee;
            chunks++;
        }

        // Si une partie reste non affectee alors que la reservation principale a deja ete scindee,
        // conserver le reliquat sous forme d'une nouvelle reservation non assignee.
        if (paxRestants > 0 && reservationPrincipaleUtilisee) {
            Reservation reliquat = new Reservation();
            reliquat.setClient(reservation.getClient() + " (reste)");
            reliquat.setNombrePassager(paxRestants);
            reliquat.setDateHeureArrivee(heureDepart);
            reliquat.setIdHotel(reservation.getIdHotel());
            reliquat.setNomHotel(reservation.getNomHotel());
            reliquat.setIdVehicule(null);
            reservationDAO.insert(reliquat);
        }

        // Aucun vehicule n'a ete assigne: reservation d'origine intacte.
        if (chunks == 0) {
            return 1;
        }

        return chunks;
    }

    private Vehicule choisirVehiculeSprint7(List<Vehicule> vehicules,
                                            int passagers,
                                            Map<Integer, Integer> trajetsParVehicule) {
        List<Vehicule> eligibles = new ArrayList<>();
        for (Vehicule vehicule : vehicules) {
            if (vehicule.getNombrePlace() >= passagers) {
                eligibles.add(vehicule);
            }
        }

        if (!eligibles.isEmpty()) {
            eligibles.sort((a, b) -> comparerVehicules(a, b, trajetsParVehicule, true));
            return eligibles.get(0);
        }

        // Fractionnement: prendre le plus grand vehicule disponible si aucun ne peut absorber tout le groupe.
        vehicules.sort((a, b) -> comparerVehicules(a, b, trajetsParVehicule, false));
        return vehicules.isEmpty() ? null : vehicules.get(0);
    }

    private int comparerVehicules(Vehicule a,
                                  Vehicule b,
                                  Map<Integer, Integer> trajetsParVehicule,
                                  boolean capaciteCroissante) {
        int cmpCapacite = capaciteCroissante
            ? Integer.compare(a.getNombrePlace(), b.getNombrePlace())
            : Integer.compare(b.getNombrePlace(), a.getNombrePlace());
        if (cmpCapacite != 0) {
            return cmpCapacite;
        }

        int trajetsA = trajetsParVehicule.getOrDefault(a.getId(), 0);
        int trajetsB = trajetsParVehicule.getOrDefault(b.getId(), 0);
        int cmpTrajets = Integer.compare(trajetsA, trajetsB);
        if (cmpTrajets != 0) {
            return cmpTrajets;
        }

        boolean dieselA = estDiesel(a);
        boolean dieselB = estDiesel(b);
        if (dieselA != dieselB) {
            return dieselA ? -1 : 1;
        }

        return Integer.compare(a.getId(), b.getId());
    }

    private boolean estDiesel(Vehicule vehicule) {
        return "D".equals(vehicule.getTypeCarburant());
    }
}
