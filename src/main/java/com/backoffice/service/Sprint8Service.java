package com.backoffice.service;

import com.backoffice.dao.ReservationDAO;
import com.backoffice.dao.VehiculeDAO;
import com.backoffice.model.Reservation;
import com.backoffice.model.Vehicule;

import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Sprint 8:
 * - Traite les retours vehicules pour declencher des departs immediats sur backlog non assigne.
 * - Puis continue le flux normal Sprint 7 sur les reservations restantes.
 */
public class Sprint8Service {

    private final ReservationDAO reservationDAO = new ReservationDAO();
    private final VehiculeDAO vehiculeDAO = new VehiculeDAO();
    private final Sprint7Service sprint7Service = new Sprint7Service();

    public static class ExecutionResult {
        private final int nonAssigneesInitiales;
        private final int departsImmediats;
        private final int decisionsReportTa;
        private final int reservationsAffecteesRetour;
        private final int reservationsFractionneesRetour;
        private final int reservationsTraiteesTotal;
        private final int nonAssigneesFinales;

        public ExecutionResult(int nonAssigneesInitiales,
                               int departsImmediats,
                               int decisionsReportTa,
                               int reservationsAffecteesRetour,
                               int reservationsFractionneesRetour,
                               int reservationsTraiteesTotal,
                               int nonAssigneesFinales) {
            this.nonAssigneesInitiales = nonAssigneesInitiales;
            this.departsImmediats = departsImmediats;
            this.decisionsReportTa = decisionsReportTa;
            this.reservationsAffecteesRetour = reservationsAffecteesRetour;
            this.reservationsFractionneesRetour = reservationsFractionneesRetour;
            this.reservationsTraiteesTotal = reservationsTraiteesTotal;
            this.nonAssigneesFinales = nonAssigneesFinales;
        }

        public int getNonAssigneesInitiales() {
            return nonAssigneesInitiales;
        }

        public int getDepartsImmediats() {
            return departsImmediats;
        }

        public int getDecisionsReportTa() {
            return decisionsReportTa;
        }

        public int getReservationsAffecteesRetour() {
            return reservationsAffecteesRetour;
        }

        public int getReservationsFractionneesRetour() {
            return reservationsFractionneesRetour;
        }

        public int getReservationsTraiteesTotal() {
            return reservationsTraiteesTotal;
        }

        public int getNonAssigneesFinales() {
            return nonAssigneesFinales;
        }
    }

    public ExecutionResult executer(Date date) throws SQLException {
        reservationDAO.ensureReservationVehiculeTable();

        int nonAssigneesInitiales = reservationDAO.findWithoutVehiculeByDate(date).size();
        int departsImmediats = 0;
        int decisionsReportTa = 0;
        int reservationsAffecteesRetour = 0;
        int reservationsFractionneesRetour = 0;

        List<Map<String, Object>> retours = reservationDAO.getVehiculeRetourEventsByDate(date);
        for (Map<String, Object> event : retours) {
            Integer vehiculeId = (Integer) event.get("vehiculeId");
            Timestamp heureRetour = (Timestamp) event.get("dateHeureRetour");
            if (vehiculeId == null || heureRetour == null) {
                continue;
            }

            Vehicule vehicule = vehiculeDAO.findById(vehiculeId);
            if (vehicule == null) {
                continue;
            }

            int chargeAttente = reservationDAO.countPassagersSansVehiculeByDateAndBeforeTime(date, heureRetour);
            if (chargeAttente < vehicule.getNombrePlace()) {
                decisionsReportTa++;
                continue;
            }

            List<Reservation> backlog = reservationDAO.findWithoutVehiculeByDateAndBeforeTime(date, heureRetour);
            if (backlog.isEmpty()) {
                continue;
            }

            backlog.sort(Comparator
                .comparingInt(Reservation::getNombrePassager).reversed()
                .thenComparing(Reservation::getDateHeureArrivee)
                .thenComparingInt(Reservation::getId));

            AffectationRetourResult affectation = affecterDepartImmediat(vehicule, backlog, heureRetour);
            if (affectation.aAffecte) {
                departsImmediats++;
                reservationsAffecteesRetour += affectation.reservationsAffectees;
                reservationsFractionneesRetour += affectation.reservationsFractionnees;
            } else {
                decisionsReportTa++;
            }
        }

        Sprint7Service.ExecutionResult suiteSprint7 = sprint7Service.executerDepuisEtatCourant(date, "NORMAL_TA");
        int reservationsTraiteesTotal = reservationsAffecteesRetour + suiteSprint7.getReservationsTraitees();

        return new ExecutionResult(
            nonAssigneesInitiales,
            departsImmediats,
            decisionsReportTa,
            reservationsAffecteesRetour,
            reservationsFractionneesRetour,
            reservationsTraiteesTotal,
            suiteSprint7.getNonAssigneesFinales()
        );
    }

    private AffectationRetourResult affecterDepartImmediat(Vehicule vehicule,
                                                            List<Reservation> backlog,
                                                            Timestamp heureRetour) throws SQLException {
        int placesRestantes = vehicule.getNombrePlace();
        int reservationsAffectees = 0;
        int reservationsFractionnees = 0;
        boolean aAffecte = false;

        List<Reservation> copie = new ArrayList<>(backlog);
        for (Reservation reservation : copie) {
            if (placesRestantes <= 0) {
                break;
            }

            Reservation relecture = reservationDAO.findById(reservation.getId());
            if (relecture == null || relecture.getIdVehicule() != null) {
                continue;
            }

            int paxDemandes = relecture.getNombrePassager();
            if (paxDemandes <= 0) {
                continue;
            }

            int paxAffectes = Math.min(paxDemandes, placesRestantes);
            if (paxAffectes <= 0) {
                continue;
            }

            relecture.setNombrePassager(paxAffectes);
            relecture.setDateHeureArrivee(heureRetour);
            reservationDAO.update(relecture);
            reservationDAO.assignVehicule(relecture.getId(), vehicule.getId());
            reservationDAO.upsertReservationVehicule(
                relecture.getId(),
                vehicule.getId(),
                paxAffectes,
                heureRetour,
                "RETOUR_IMMEDIAT"
            );

            aAffecte = true;
            reservationsAffectees++;
            placesRestantes -= paxAffectes;

            if (paxDemandes > paxAffectes) {
                Reservation reliquat = new Reservation();
                reliquat.setClient(relecture.getClient() + " (split)");
                reliquat.setNombrePassager(paxDemandes - paxAffectes);
                reliquat.setDateHeureArrivee(heureRetour);
                reliquat.setIdHotel(relecture.getIdHotel());
                reliquat.setNomHotel(relecture.getNomHotel());
                reliquat.setIdVehicule(null);
                reservationDAO.insert(reliquat);
                reservationsFractionnees++;
            }
        }

        return new AffectationRetourResult(aAffecte, reservationsAffectees, reservationsFractionnees);
    }

    private static class AffectationRetourResult {
        private final boolean aAffecte;
        private final int reservationsAffectees;
        private final int reservationsFractionnees;

        private AffectationRetourResult(boolean aAffecte, int reservationsAffectees, int reservationsFractionnees) {
            this.aAffecte = aAffecte;
            this.reservationsAffectees = reservationsAffectees;
            this.reservationsFractionnees = reservationsFractionnees;
        }
    }
}
