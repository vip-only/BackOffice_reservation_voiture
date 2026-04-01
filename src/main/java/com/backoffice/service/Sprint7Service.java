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

    private static class AffectationResult {
        private final boolean assignee;
        private final boolean fractionnee;
        private final Reservation reliquat;

        private AffectationResult(boolean assignee, boolean fractionnee, Reservation reliquat) {
            this.assignee = assignee;
            this.fractionnee = fractionnee;
            this.reliquat = reliquat;
        }
    }

    public ExecutionResult executer(Date date) throws SQLException {
        return executerInterne(date, true, "NORMAL_TA");
    }

    public ExecutionResult executerDepuisEtatCourant(Date date, String modeAssignation) throws SQLException {
        return executerInterne(date, false, modeAssignation == null ? "NORMAL_TA" : modeAssignation);
    }

    private ExecutionResult executerInterne(Date date,
                                            boolean resetAvantTraitement,
                                            String modeAssignation) throws SQLException {
        reservationDAO.ensureReservationVehiculeTable();

        // Recalcul complet Sprint 7 pour respecter l'ordre prioritaire dans la fenetre TA:
        // on evite de figer des choix Sprint 5 qui peuvent priver une reservation plus prioritaire.
        int nonAssigneesInitiales = reservationDAO.findWithoutVehiculeByDate(date).size();
        if (resetAvantTraitement) {
            reservationDAO.deleteReservationVehiculeByDate(date);
            reservationDAO.resetAssignationsByDate(date);
        }

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
            java.util.Set<Integer> idsReporteesFenetre = new java.util.HashSet<>();
            if (!reportees.isEmpty()) {
                groupe.addAll(reportees);
                for (Reservation reportee : reportees) {
                    idsReporteesFenetre.add(reportee.getId());
                }
            }
            if (fenetreBase != null && !fenetreBase.isEmpty()) {
                groupe.addAll(fenetreBase);
            }

            if (groupe.isEmpty()) {
                continue;
            }

            // Priorite de tri pour la fenetre courante:
            // on garde la taille AVANT assignation pour les splits dans la meme fenetre.
            // En fenetre suivante, une reservation reportee est reconsideree comme nouvelle.
            Map<Integer, Integer> prioritePaxFenetre = new HashMap<>();
            for (Reservation r : groupe) {
                prioritePaxFenetre.put(r.getId(), r.getNombrePassager());
            }

            trierReservationsDecroissant(groupe, prioritePaxFenetre, idsReporteesFenetre);

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

            while (!groupe.isEmpty()) {
                Vehicule vehiculePartiel = choisirVehiculePartiellementRempli(
                    placesRestantesFenetre,
                    vehiculesUtilisesFenetre,
                    trajetsParVehicule
                );

                if (vehiculePartiel != null) {
                    int placesRestantes = placesRestantesFenetre.getOrDefault(vehiculePartiel.getId(), 0);
                    Reservation reservationProche = extraireReservationPlusProche(groupe, placesRestantes);
                    if (reservationProche != null) {
                        int totalPaxAvant = reservationProche.getNombrePassager();
                        AffectationResult resultatPartiel = affecterSurVehicule(
                            reservationProche,
                            vehiculePartiel,
                            heureDepartFenetre,
                            false,
                            trajetsParVehicule,
                            placesRestantesFenetre,
                            vehiculesUtilisesFenetre,
                            prioritePaxFenetre,
                            modeAssignation
                        );
                        reservationsTraitees++;

                        if (resultatPartiel.reliquat != null) {
                            groupe.add(resultatPartiel.reliquat);
                        }

                        if (resultatPartiel.fractionnee) {
                            reservationsFractionnees++;
                            morceauxCrees += 1;
                            passagersFractionnes += totalPaxAvant;
                        }

                        trierReservationsDecroissant(groupe, prioritePaxFenetre, idsReporteesFenetre);
                        continue;
                    }
                }

                Reservation reservation = groupe.remove(0);
                if (reservation.getIdVehicule() != null) {
                    continue;
                }

                int totalPaxAvant = reservation.getNombrePassager();

                List<Vehicule> vehiculesDisponibles = reservationService.getVehiculesDisponibles(heureDepartFenetre, reservation.getIdHotel());
                vehiculesDisponibles.removeIf(v -> vehiculesUtilisesFenetre.containsKey(v.getId()));
                if (vehiculesDisponibles.isEmpty()) {
                    nouvellesReportees.add(reservation);
                    nouvellesReportees.addAll(groupe);
                    break;
                }

                Vehicule choisi = choisirVehiculeSprint7(
                    vehiculesDisponibles,
                    reservation.getNombrePassager(),
                    trajetsParVehicule
                );

                if (choisi == null) {
                    nouvellesReportees.add(reservation);
                    nouvellesReportees.addAll(groupe);
                    break;
                }

                AffectationResult resultat = affecterSurVehicule(
                    reservation,
                    choisi,
                    heureDepartFenetre,
                    true,
                    trajetsParVehicule,
                    placesRestantesFenetre,
                    vehiculesUtilisesFenetre,
                    prioritePaxFenetre,
                    modeAssignation
                );
                reservationsTraitees++;

                if (!resultat.assignee) {
                    nouvellesReportees.add(reservation);
                    continue;
                }

                if (resultat.reliquat != null) {
                    // Le reliquat est considere comme une nouvelle reservation,
                    // sans priorite speciale en fenetre suivante.
                    groupe.add(resultat.reliquat);
                }

                if (resultat.fractionnee) {
                    reservationsFractionnees++;
                    morceauxCrees += 1;
                    passagersFractionnes += totalPaxAvant;
                }

                trierReservationsDecroissant(groupe, prioritePaxFenetre, idsReporteesFenetre);
            }

            for (Reservation reservation : groupe) {
                if (reservation.getIdVehicule() == null) {
                    nouvellesReportees.add(reservation);
                }
            }

            // Fenetre suivante: 0 priorite speciale -> tri sur tailles actuelles.
            trierReservationsDecroissant(nouvellesReportees, new HashMap<>(), new java.util.HashSet<>());
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

    private AffectationResult affecterSurVehicule(Reservation reservation,
                                                  Vehicule vehicule,
                                                  Timestamp heureDepart,
                                                  boolean nouveauVehicule,
                                                  Map<Integer, Integer> trajetsParVehicule,
                                                  Map<Integer, Integer> placesRestantesFenetre,
                                                  Map<Integer, Vehicule> vehiculesUtilisesFenetre,
                                                  Map<Integer, Integer> prioritePaxFenetre,
                                                  String modeAssignation) throws SQLException {
        int paxDemandes = reservation.getNombrePassager();
        int prioriteInitiale = prioritePaxFenetre.getOrDefault(reservation.getId(), paxDemandes);
        int capaciteDisponible = nouveauVehicule
            ? vehicule.getNombrePlace()
            : placesRestantesFenetre.getOrDefault(vehicule.getId(), 0);

        int capaciteAffectee = Math.min(paxDemandes, Math.max(0, capaciteDisponible));
        if (capaciteAffectee <= 0) {
            return new AffectationResult(false, false, null);
        }

        reservation.setNombrePassager(capaciteAffectee);
        reservation.setDateHeureArrivee(heureDepart);
        reservationDAO.update(reservation);
        reservationDAO.assignVehicule(reservation.getId(), vehicule.getId());
        reservationDAO.upsertReservationVehicule(
            reservation.getId(),
            vehicule.getId(),
            capaciteAffectee,
            heureDepart,
            modeAssignation
        );
        reservation.setIdVehicule(vehicule.getId());

        if (nouveauVehicule) {
            vehiculesUtilisesFenetre.put(vehicule.getId(), vehicule);
            trajetsParVehicule.put(vehicule.getId(), trajetsParVehicule.getOrDefault(vehicule.getId(), 0) + 1);
        }

        int reste = Math.max(0, capaciteDisponible - capaciteAffectee);
        placesRestantesFenetre.put(vehicule.getId(), reste);

        Reservation reliquat = null;
        boolean fractionnee = paxDemandes > capaciteAffectee;
        if (fractionnee) {
            reliquat = new Reservation();
            reliquat.setClient(reservation.getClient() + " (split)");
            reliquat.setNombrePassager(paxDemandes - capaciteAffectee);
            reliquat.setDateHeureArrivee(heureDepart);
            reliquat.setIdHotel(reservation.getIdHotel());
            reliquat.setNomHotel(reservation.getNomHotel());
            reliquat.setIdVehicule(null);
            reservationDAO.insert(reliquat);
            prioritePaxFenetre.put(reliquat.getId(), prioriteInitiale);
        }

        return new AffectationResult(true, fractionnee, reliquat);
    }

    private void trierReservationsDecroissant(List<Reservation> reservations,
                                              Map<Integer, Integer> prioritePaxFenetre,
                                              java.util.Set<Integer> idsReporteesFenetre) {
        reservations.sort((a, b) -> {
            boolean aReportee = idsReporteesFenetre != null && idsReporteesFenetre.contains(a.getId());
            boolean bReportee = idsReporteesFenetre != null && idsReporteesFenetre.contains(b.getId());
            if (aReportee != bReportee) {
                // Sprint 8: les non assignes reportes sont prioritaires sur les nouvelles reservations.
                return aReportee ? -1 : 1;
            }

            int prioriteA = prioritePaxFenetre.getOrDefault(a.getId(), a.getNombrePassager());
            int prioriteB = prioritePaxFenetre.getOrDefault(b.getId(), b.getNombrePassager());

            int cmpPax = Integer.compare(prioriteB, prioriteA);
            if (cmpPax != 0) {
                return cmpPax;
            }

            // A egalite de taille: la reservation non splittee passe d'abord.
            boolean splitA = estReservationSeparee(a);
            boolean splitB = estReservationSeparee(b);
            if (splitA != splitB) {
                return splitA ? 1 : -1;
            }

            int cmpDate = a.getDateHeureArrivee().compareTo(b.getDateHeureArrivee());
            if (cmpDate != 0) {
                return cmpDate;
            }
            return Integer.compare(a.getId(), b.getId());
        });
    }

    private Reservation extraireReservationPlusProche(List<Reservation> reservations, int placesCibles) {
        if (reservations == null || reservations.isEmpty() || placesCibles <= 0) {
            return null;
        }

        Reservation meilleure = null;
        int indexMeilleur = -1;
        int meilleureDistance = Integer.MAX_VALUE;

        for (int i = 0; i < reservations.size(); i++) {
            Reservation courante = reservations.get(i);
            if (courante.getIdVehicule() != null) {
                continue;
            }

            int pax = courante.getNombrePassager();
            int distance = Math.abs(pax - placesCibles);

            if (distance < meilleureDistance) {
                meilleureDistance = distance;
                meilleure = courante;
                indexMeilleur = i;
            } else if (distance == meilleureDistance && meilleure != null) {
                if (pax > meilleure.getNombrePassager()) {
                    // Egalite de distance: prendre la plus grande reservation.
                    meilleure = courante;
                    indexMeilleur = i;
                } else if (pax == meilleure.getNombrePassager()) {
                    boolean splitCourante = estReservationSeparee(courante);
                    boolean splitMeilleure = estReservationSeparee(meilleure);
                    if (splitCourante != splitMeilleure) {
                        if (!splitCourante) {
                            meilleure = courante;
                            indexMeilleur = i;
                        }
                    } else if (courante.getDateHeureArrivee().before(meilleure.getDateHeureArrivee())
                        || (courante.getDateHeureArrivee().equals(meilleure.getDateHeureArrivee()) && courante.getId() < meilleure.getId())) {
                        meilleure = courante;
                        indexMeilleur = i;
                    }
                }
            }
        }

        if (indexMeilleur >= 0) {
            reservations.remove(indexMeilleur);
        }
        return meilleure;
    }

    private Vehicule choisirVehiculePartiellementRempli(Map<Integer, Integer> placesRestantesFenetre,
                                                        Map<Integer, Vehicule> vehiculesUtilisesFenetre,
                                                        Map<Integer, Integer> trajetsParVehicule) {
        Vehicule meilleur = null;
        int meilleurReste = -1;

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

            if (reste > meilleurReste) {
                meilleurReste = reste;
                meilleur = candidat;
            } else if (reste == meilleurReste && meilleur != null
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

    private boolean estReservationSeparee(Reservation reservation) {
        if (reservation == null || reservation.getClient() == null) {
            return false;
        }
        String client = reservation.getClient();
        return client.contains("(reste)") || client.contains("(split)");
    }
}
