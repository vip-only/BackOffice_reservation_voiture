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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Sprint 8:
 * - Traite les retours vehicules pour declencher des departs immediats sur backlog non assigne.
 * - Puis continue le flux normal Sprint 7 sur les reservations restantes.
 */
public class Sprint8Service {

    private final ParametreDAO parametreDAO = new ParametreDAO();
    private final ReservationDAO reservationDAO = new ReservationDAO();
    private final VehiculeDAO vehiculeDAO = new VehiculeDAO();
    private final ReservationService reservationService = new ReservationService();
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
        int taMinutes = parametreDAO.getTempsAttente();

        PriorityQueue<DeclenchementEvent> events = new PriorityQueue<>(Comparator
            .comparing(DeclenchementEvent::getInstant)
            .thenComparingInt(DeclenchementEvent::getOrder));
        Set<String> eventKeys = new HashSet<>();

        // Disponibilites initiales vehicules.
        for (Vehicule vehicule : vehiculeDAO.findAll()) {
            if (vehicule.getHeureDisponibilite() == null) {
                continue;
            }
            Timestamp instant = Timestamp.valueOf(date.toString() + " " + vehicule.getHeureDisponibilite().toString());
            pushEvent(events, eventKeys, new DeclenchementEvent("VEHICULE", vehicule.getId(), instant));
        }

        // Arrivees des reservations de la journee.
        for (Reservation reservation : reservationDAO.findByDate(date)) {
            if (reservation == null || reservation.getDateHeureArrivee() == null) {
                continue;
            }
            pushEvent(events, eventKeys, new DeclenchementEvent("RESERVATION", reservation.getId(), reservation.getDateHeureArrivee()));
        }

        // Retours deja connus (seed).
        injecterRetours(events, eventKeys, date);

        Timestamp debutGroupementActif = null;
        Timestamp finGroupementActif = null;

        while (!events.isEmpty()) {
            DeclenchementEvent event = events.poll();
            if (event == null || event.instant == null) {
                continue;
            }

            // Important: les nouvelles affectations peuvent creer de nouveaux retours dans la vue.
            injecterRetours(events, eventKeys, date);

            Timestamp instant = event.instant;
            if (finGroupementActif != null && instant.after(finGroupementActif)) {
                debutGroupementActif = null;
                finGroupementActif = null;
            }

            if ("VEHICULE".equals(event.type)) {
                List<Vehicule> vehiculesMemeInstant = new ArrayList<>();
                Vehicule v0 = vehiculeDAO.findById(event.sourceId);
                if (v0 != null) {
                    vehiculesMemeInstant.add(v0);
                }

                while (!events.isEmpty()) {
                    DeclenchementEvent suivant = events.peek();
                    if (suivant == null || !"VEHICULE".equals(suivant.type) || !instant.equals(suivant.instant)) {
                        break;
                    }
                    events.poll();
                    Vehicule vx = vehiculeDAO.findById(suivant.sourceId);
                    if (vx != null) {
                        vehiculesMemeInstant.add(vx);
                    }
                }

                Timestamp debutGroupement = (debutGroupementActif != null && finGroupementActif != null && !instant.after(finGroupementActif))
                    ? debutGroupementActif
                    : instant;

                if (debutGroupementActif == null || finGroupementActif == null || instant.after(finGroupementActif)) {
                    debutGroupementActif = debutGroupement;
                    finGroupementActif = ajouterMinutes(debutGroupement, taMinutes);
                }

                List<Vehicule> vehiculesRestants = new ArrayList<>(vehiculesMemeInstant);
                while (!vehiculesRestants.isEmpty()) {
                    List<Reservation> backlog = reservationDAO.findWithoutVehiculeByDateAndBeforeTime(date, debutGroupement);
                    if (backlog.isEmpty()) {
                        decisionsReportTa += vehiculesRestants.size();
                        break;
                    }

                    backlog.sort(Comparator
                        .comparingInt(Reservation::getNombrePassager).reversed()
                        .thenComparing(Reservation::getDateHeureArrivee)
                        .thenComparingInt(Reservation::getId));

                    Vehicule vehiculeChoisi = choisirVehiculeProcheDuPlusGrandNonAssigne(vehiculesRestants, backlog);
                    vehiculesRestants.remove(vehiculeChoisi);

                    AffectationRetourResult affectation = affecterDepartImmediat(vehiculeChoisi, backlog, debutGroupement);
                    if (affectation.aAffecte) {
                        departsImmediats++;
                        reservationsAffecteesRetour += affectation.reservationsAffectees;
                        reservationsFractionneesRetour += affectation.reservationsFractionnees;
                        injecterRetours(events, eventKeys, date);
                    } else {
                        decisionsReportTa++;
                    }
                }
                continue;
            }

            if ("RESERVATION".equals(event.type)) {
                Reservation reservation = reservationDAO.findById(event.sourceId);
                if (reservation == null || reservation.getIdVehicule() != null) {
                    continue;
                }

                // Si un groupement existe deja, la reservation le rejoint naturellement.
                if (debutGroupementActif != null && finGroupementActif != null && !instant.after(finGroupementActif)) {
                    continue;
                }

                // Sinon, une nouvelle reservation peut declencher un groupement si vehicule dispo.
                Vehicule declencheur = choisirVehiculeDeclencheur(instant, reservation);
                if (declencheur == null) {
                    continue;
                }

                debutGroupementActif = instant;
                finGroupementActif = ajouterMinutes(debutGroupementActif, taMinutes);

                List<Reservation> backlog = reservationDAO.findWithoutVehiculeByDateAndBeforeTime(date, debutGroupementActif);
                if (backlog.isEmpty()) {
                    continue;
                }

                backlog.sort(Comparator
                    .comparingInt(Reservation::getNombrePassager).reversed()
                    .thenComparing(Reservation::getDateHeureArrivee)
                    .thenComparingInt(Reservation::getId));

                AffectationRetourResult affectation = affecterDepartImmediat(declencheur, backlog, debutGroupementActif);
                if (affectation.aAffecte) {
                    departsImmediats++;
                    reservationsAffecteesRetour += affectation.reservationsAffectees;
                    reservationsFractionneesRetour += affectation.reservationsFractionnees;
                    injecterRetours(events, eventKeys, date);
                } else {
                    decisionsReportTa++;
                }
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

    private Vehicule choisirVehiculeDeclencheur(Timestamp instant, Reservation reservation) throws SQLException {
        List<Vehicule> disponibles = reservationService.getVehiculesDisponibles(instant, reservation.getIdHotel());
        if (disponibles == null || disponibles.isEmpty()) {
            return null;
        }

        disponibles.sort((a, b) -> {
            boolean aCapable = a.getNombrePlace() >= reservation.getNombrePassager();
            boolean bCapable = b.getNombrePlace() >= reservation.getNombrePassager();
            if (aCapable != bCapable) {
                return aCapable ? -1 : 1;
            }

            int cmpCap = aCapable
                ? Integer.compare(a.getNombrePlace(), b.getNombrePlace())
                : Integer.compare(b.getNombrePlace(), a.getNombrePlace());
            if (cmpCap != 0) {
                return cmpCap;
            }

            boolean dieselA = "D".equals(a.getTypeCarburant());
            boolean dieselB = "D".equals(b.getTypeCarburant());
            if (dieselA != dieselB) {
                return dieselA ? -1 : 1;
            }

            return Integer.compare(a.getId(), b.getId());
        });

        return disponibles.get(0);
    }

    private void injecterRetours(PriorityQueue<DeclenchementEvent> events,
                                 Set<String> eventKeys,
                                 Date date) throws SQLException {
        List<Map<String, Object>> retours = reservationDAO.getVehiculeRetourEventsByDate(date);
        for (Map<String, Object> retour : retours) {
            Integer vehiculeId = (Integer) retour.get("vehiculeId");
            Timestamp heureRetour = (Timestamp) retour.get("dateHeureRetour");
            if (vehiculeId == null || heureRetour == null) {
                continue;
            }
            pushEvent(events, eventKeys, new DeclenchementEvent("VEHICULE", vehiculeId, heureRetour));
        }
    }

    private void pushEvent(PriorityQueue<DeclenchementEvent> events,
                           Set<String> eventKeys,
                           DeclenchementEvent event) {
        if (event == null || event.instant == null) {
            return;
        }
        String key = event.type + "|" + event.sourceId + "|" + event.instant.getTime();
        if (eventKeys.add(key)) {
            events.offer(event);
        }
    }

    private Vehicule choisirVehiculeProcheDuPlusGrandNonAssigne(List<Vehicule> vehicules,
                                                                 List<Reservation> backlog) {
        if (vehicules == null || vehicules.isEmpty()) {
            return null;
        }
        int cible = (backlog == null || backlog.isEmpty()) ? 0 : backlog.get(0).getNombrePassager();

        vehicules.sort((a, b) -> {
            boolean aCapable = a.getNombrePlace() >= cible;
            boolean bCapable = b.getNombrePlace() >= cible;
            if (aCapable != bCapable) {
                return aCapable ? -1 : 1;
            }

            int distA = Math.abs(a.getNombrePlace() - cible);
            int distB = Math.abs(b.getNombrePlace() - cible);
            int cmpDist = Integer.compare(distA, distB);
            if (cmpDist != 0) {
                return cmpDist;
            }

            boolean dieselA = "D".equals(a.getTypeCarburant());
            boolean dieselB = "D".equals(b.getTypeCarburant());
            if (dieselA != dieselB) {
                return dieselA ? -1 : 1;
            }

            return Integer.compare(a.getId(), b.getId());
        });

        return vehicules.get(0);
    }

    private Timestamp ajouterMinutes(Timestamp base, int minutes) {
        if (base == null) {
            return null;
        }
        return new Timestamp(base.getTime() + minutes * 60L * 1000L);
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
                heureRetour
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

    private static class DeclenchementEvent {
        private final String type; // VEHICULE | RESERVATION
        private final int sourceId;
        private final Timestamp instant;

        private DeclenchementEvent(String type, int sourceId, Timestamp instant) {
            this.type = type;
            this.sourceId = sourceId;
            this.instant = instant;
        }

        private Timestamp getInstant() {
            return instant;
        }

        private int getOrder() {
            // Priorite aux disponibilites vehicule en cas d'egalite temporelle.
            return "VEHICULE".equals(type) ? 0 : 1;
        }
    }
}
