package com.backoffice.service;

import com.backoffice.dao.ParametreDAO;
import com.backoffice.dao.ReservationDAO;
import com.backoffice.dao.VehiculeDAO;
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
    private final VehiculeDAO vehiculeDAO = new VehiculeDAO();

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

    private static class FractionnementResult {
        private final int morceauxAssignes;
        private final boolean assignmentEffectuee;
        private final boolean fractionnee;
        private final Reservation reliquat;

        private FractionnementResult(int morceauxAssignes,
                                     boolean assignmentEffectuee,
                                     boolean fractionnee,
                                     Reservation reliquat) {
            this.morceauxAssignes = morceauxAssignes;
            this.assignmentEffectuee = assignmentEffectuee;
            this.fractionnee = fractionnee;
            this.reliquat = reliquat;
        }
    }

    public ExecutionResult executer(Date date) throws SQLException {
        reservationDAO.ensureReservationVehiculeTable();

        // Recalcul complet Sprint 7 pour respecter l'ordre prioritaire dans la fenetre TA:
        // on evite de figer des choix Sprint 5 qui peuvent priver une reservation plus prioritaire.
        int nonAssigneesInitiales = reservationDAO.findWithoutVehiculeByDate(date).size();
        reservationDAO.deleteReservationVehiculeByDate(date);
        reservationDAO.resetAssignationsByDate(date);

        List<Reservation> reservationsDuJour = reservationDAO.findByDate(date);
        if (reservationsDuJour == null || reservationsDuJour.isEmpty()) {
            return new ExecutionResult(0, 0, 0, 0, 0, 0);
        }

        reservationsDuJour.sort(Comparator.comparing(Reservation::getDateHeureArrivee));

        int taMinutes = parametreDAO.getTempsAttente();
        List<List<Reservation>> fenetres = construireFenetresTA(reservationsDuJour, taMinutes);

        Map<Integer, Integer> trajetsParVehicule = new HashMap<>(reservationDAO.getNombreTrajetsParVehicule(date));
        Map<Integer, Vehicule> vehiculesParId = chargerVehiculesParId();

        int reservationsTraitees = 0;
        int reservationsFractionnees = 0;
        int morceauxCrees = 0;
        int passagersFractionnes = 0;

        List<Reservation> reportees = new ArrayList<>();

        for (List<Reservation> fenetreBase : fenetres) {
            List<Reservation> groupe = new ArrayList<>();
            List<Reservation> prioritaires = new ArrayList<>();
            List<Reservation> nouvellesFenetre = new ArrayList<>();

            if (!reportees.isEmpty()) {
                prioritaires.addAll(reportees);
            }
            if (fenetreBase != null && !fenetreBase.isEmpty()) {
                nouvellesFenetre.addAll(fenetreBase);
            }

            if (prioritaires.isEmpty() && nouvellesFenetre.isEmpty()) {
                continue;
            }

            // Priorite absolue aux reportees/restes, sans dependre du tri.
            groupe.addAll(prioritaires);

            // Le tri decroissant s'applique uniquement aux nouvelles reservations de la fenetre.
            nouvellesFenetre.sort((a, b) -> {
                int cmpPax = Integer.compare(b.getNombrePassager(), a.getNombrePassager());
                if (cmpPax != 0) {
                    return cmpPax;
                }
                return a.getDateHeureArrivee().compareTo(b.getDateHeureArrivee());
            });
            groupe.addAll(nouvellesFenetre);

            // Etat de capacite partage dans la meme fenetre TA pour permettre
            // le fractionnement progressif entre reservations du groupe.
            Map<Integer, Integer> placesRestantesFenetre = new HashMap<>();
            Map<Integer, Vehicule> vehiculesUtilisesFenetre = new HashMap<>();
            initialiserEtatFenetreDepuisAssignationsExistantes(
                groupe,
                vehiculesParId,
                placesRestantesFenetre,
                vehiculesUtilisesFenetre
            );

            Timestamp heureDepartFenetre = planificationService.calculerHeureDepartAjustee(groupe);
            if (heureDepartFenetre == null) {
                continue;
            }

            List<Reservation> nouvellesReportees = new ArrayList<>();

            for (Reservation reservation : groupe) {
                if (reservation.getIdVehicule() != null) {
                    continue;
                }

                int totalPax = reservation.getNombrePassager();
                FractionnementResult resultat = assignerAvecFractionnement(
                    reservation,
                    heureDepartFenetre,
                    trajetsParVehicule,
                    placesRestantesFenetre,
                    vehiculesUtilisesFenetre
                );
                reservationsTraitees++;

                if (!resultat.assignmentEffectuee) {
                    nouvellesReportees.add(reservation);
                    continue;
                }

                if (resultat.reliquat != null) {
                    nouvellesReportees.add(resultat.reliquat);
                }

                if (resultat.fractionnee) {
                    reservationsFractionnees++;
                    morceauxCrees += Math.max(0, resultat.morceauxAssignes - 1);
                    passagersFractionnes += totalPax;
                }
            }

            reportees = nouvellesReportees;
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

    /**
     * Retourne le nombre de morceaux crees pour cette reservation (1 = pas de fractionnement).
     */
    private FractionnementResult assignerAvecFractionnement(Reservation reservation,
                                                            Timestamp heureDepart,
                                                            Map<Integer, Integer> trajetsParVehicule,
                                                            Map<Integer, Integer> placesRestantesFenetre,
                                                            Map<Integer, Vehicule> vehiculesUtilisesFenetre) throws SQLException {
        int paxRestants = reservation.getNombrePassager();
        int chunks = 0;
        boolean reservationPrincipaleUtilisee = false;
        Reservation reliquatCree = null;

        while (paxRestants > 0) {
            Vehicule choisi = choisirVehiculeDejaMobilise(
                placesRestantesFenetre,
                paxRestants,
                vehiculesUtilisesFenetre,
                trajetsParVehicule
            );
            boolean nouveauVehicule = false;

            if (choisi == null) {
                List<Vehicule> vehiculesDisponibles = reservationService.getVehiculesDisponibles(heureDepart, reservation.getIdHotel());
                vehiculesDisponibles.removeIf(v -> vehiculesUtilisesFenetre.containsKey(v.getId()));
                if (vehiculesDisponibles.isEmpty()) {
                    break;
                }

                choisi = choisirVehiculeSprint7(
                    vehiculesDisponibles,
                    paxRestants,
                    trajetsParVehicule
                );
                nouveauVehicule = (choisi != null);
            }

            if (choisi == null) {
                break;
            }

            int capaciteAffectee;
            if (placesRestantesFenetre.containsKey(choisi.getId())) {
                capaciteAffectee = Math.min(paxRestants, placesRestantesFenetre.get(choisi.getId()));
            } else {
                capaciteAffectee = Math.min(paxRestants, choisi.getNombrePlace());
            }

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

            if (nouveauVehicule) {
                vehiculesUtilisesFenetre.put(choisi.getId(), choisi);
                placesRestantesFenetre.put(choisi.getId(), choisi.getNombrePlace() - capaciteAffectee);
                // Un nouveau vehicule mobilise dans la fenetre = un trajet de plus.
                trajetsParVehicule.put(choisi.getId(), trajetsParVehicule.getOrDefault(choisi.getId(), 0) + 1);
            } else {
                int resteActuel = placesRestantesFenetre.getOrDefault(choisi.getId(), 0);
                placesRestantesFenetre.put(choisi.getId(), Math.max(0, resteActuel - capaciteAffectee));
            }

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
            reliquatCree = reliquat;
        }

        // Aucun vehicule n'a ete assigne: reservation d'origine intacte.
        if (chunks == 0) {
            return new FractionnementResult(0, false, false, null);
        }

        boolean fractionnee = chunks > 1 || reliquatCree != null;
        return new FractionnementResult(chunks, true, fractionnee, reliquatCree);
    }

    private Vehicule choisirVehiculeDejaMobilise(Map<Integer, Integer> placesRestantesFenetre,
                                                 int passagers,
                                                 Map<Integer, Vehicule> vehiculesUtilisesFenetre,
                                                 Map<Integer, Integer> trajetsParVehicule) {
        Vehicule meilleur = null;
        int meilleurReste = Integer.MAX_VALUE;

        // Priorite 1: un vehicule deja mobilise qui absorbe totalement le besoin.
        for (Map.Entry<Integer, Integer> entry : placesRestantesFenetre.entrySet()) {
            int vehiculeId = entry.getKey();
            int reste = entry.getValue();
            if (reste < passagers) {
                continue;
            }

            Vehicule candidat = vehiculesUtilisesFenetre.get(vehiculeId);
            if (candidat == null) {
                continue;
            }

            if (reste < meilleurReste) {
                meilleurReste = reste;
                meilleur = candidat;
            } else if (reste == meilleurReste && meilleur != null
                && comparerPrioriteCharge(candidat, meilleur, trajetsParVehicule) < 0) {
                meilleur = candidat;
            }
        }

        if (meilleur != null) {
            return meilleur;
        }

        // Priorite 2: fractionnement sur un vehicule deja mobilise (prendre le plus grand reste).
        int maxReste = -1;
        for (Map.Entry<Integer, Integer> entry : placesRestantesFenetre.entrySet()) {
            int vehiculeId = entry.getKey();
            int reste = entry.getValue();
            if (reste <= 0) {
                continue;
            }

            Vehicule candidat = vehiculesUtilisesFenetre.get(vehiculeId);
            if (candidat == null) {
                continue;
            }

            if (reste > maxReste) {
                maxReste = reste;
                meilleur = candidat;
            } else if (reste == maxReste && meilleur != null
                && comparerPrioriteCharge(candidat, meilleur, trajetsParVehicule) < 0) {
                meilleur = candidat;
            }
        }

        return meilleur;
    }

    private int comparerPrioriteCharge(Vehicule a,
                                       Vehicule b,
                                       Map<Integer, Integer> trajetsParVehicule) {
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

    private Map<Integer, Vehicule> chargerVehiculesParId() throws SQLException {
        Map<Integer, Vehicule> vehiculesParId = new HashMap<>();
        for (Vehicule vehicule : vehiculeDAO.findAll()) {
            vehiculesParId.put(vehicule.getId(), vehicule);
        }
        return vehiculesParId;
    }

    private void initialiserEtatFenetreDepuisAssignationsExistantes(
        List<Reservation> fenetre,
        Map<Integer, Vehicule> vehiculesParId,
        Map<Integer, Integer> placesRestantesFenetre,
        Map<Integer, Vehicule> vehiculesUtilisesFenetre
    ) {
        Map<Integer, Integer> passagersParVehicule = new HashMap<>();

        for (Reservation reservation : fenetre) {
            Integer idVehicule = reservation.getIdVehicule();
            if (idVehicule == null) {
                continue;
            }

            passagersParVehicule.put(
                idVehicule,
                passagersParVehicule.getOrDefault(idVehicule, 0) + reservation.getNombrePassager()
            );
        }

        for (Map.Entry<Integer, Integer> entry : passagersParVehicule.entrySet()) {
            int idVehicule = entry.getKey();
            int passagersAffectes = entry.getValue();
            Vehicule vehicule = vehiculesParId.get(idVehicule);

            if (vehicule == null) {
                continue;
            }

            vehiculesUtilisesFenetre.put(idVehicule, vehicule);
            int reste = Math.max(0, vehicule.getNombrePlace() - passagersAffectes);
            placesRestantesFenetre.put(idVehicule, reste);
        }
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
            // Regle: capacite d'abord, puis trajets si egalite de capacite, puis Diesel.
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
