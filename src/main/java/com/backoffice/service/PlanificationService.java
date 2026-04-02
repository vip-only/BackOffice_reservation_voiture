package com.backoffice.service;

import com.backoffice.dao.ParametreDAO;
import com.backoffice.dao.ReservationDAO;
import com.backoffice.database.DBConnection;
import com.backoffice.model.GroupeVehicule;
import com.backoffice.model.GroupeVehicule.EtapeItineraire;
import com.backoffice.model.PlanificationReservation;
import com.backoffice.model.Reservation;
import com.backoffice.model.Vehicule;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;

public class PlanificationService {
    
    private ReservationDAO reservationDAO = new ReservationDAO();
    private ReservationService reservationService = new ReservationService();
    private VehiculeAffectationService affectationService = new VehiculeAffectationService();
    private ParametreDAO parametreDAO = new ParametreDAO();
    
    /**
     * Récupère les planifications pour une date donnée.
     * Utilise la vue v_historique_assignation pour obtenir les heures de retour calculées.
     */
    public List<PlanificationReservation> getPlanificationsByDate(Date date) throws SQLException {
        List<PlanificationReservation> planifications = new ArrayList<>();
        
        // Requête sur la vue qui calcule automatiquement les heures de retour
        String sql = 
            "SELECT " +
            "    ha.reservation_id, " +
            "    ha.client, " +
            "    ha.nombre_passager, " +
            "    ha.date_heure_arrivee, " +
            "    ha.hotel, " +
            "    r.id_hotel, " +
            "    ha.vehicule_id, " +
            "    ha.vehicule, " +
            "    ha.capacite_vehicule, " +
            "    ha.distance_km, " +
            "    ha.duree_aller_minutes, " +
            "    ha.duree_totale_minutes, " +
            "    ha.date_heure_retour, " +
            "    v.type_carburant " +
            "FROM v_historique_assignation ha " +
            "JOIN vehicule v ON ha.vehicule_id = v.id " +
            "JOIN reservation r ON ha.reservation_id = r.id " +
            "WHERE DATE(ha.date_heure_arrivee) = ? " +
            "ORDER BY ha.date_heure_arrivee";
        
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setDate(1, date);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // Créer la réservation
                    Reservation reservation = new Reservation();
                    reservation.setId(rs.getInt("reservation_id"));
                    reservation.setClient(rs.getString("client"));
                    reservation.setNombrePassager(rs.getInt("nombre_passager"));
                    reservation.setDateHeureArrivee(rs.getTimestamp("date_heure_arrivee"));
                    reservation.setNomHotel(rs.getString("hotel"));
                    reservation.setIdHotel(rs.getInt("id_hotel"));
                    reservation.setIdVehicule(rs.getInt("vehicule_id"));
                    reservation.setReferenceVehicule(rs.getString("vehicule"));
                    reservation.setCapaciteVehicule(rs.getInt("capacite_vehicule"));
                    
                    // Heure de départ = date_heure_arrivee (le véhicule part avec le client à l'arrivée du vol)
                    // Heure de retour = date_heure_arrivee + (2 × durée trajet) - calculé dans la vue
                    // PAS de temps d'attente pris en compte
                    int dureeAllerMinutes = rs.getInt("duree_aller_minutes");
                    Timestamp heureDepart = rs.getTimestamp("date_heure_arrivee"); // Départ = arrivée vol
                    Timestamp heureRetour = rs.getTimestamp("date_heure_retour");  // Retour = calculé dans la vue
                    double distanceKm = rs.getDouble("distance_km");
                    
                    // Créer la planification
                    PlanificationReservation planification = new PlanificationReservation(
                        reservation,
                        heureDepart,
                        heureRetour,
                        distanceKm,
                        dureeAllerMinutes
                    );
                    
                    // Stocker le type carburant pour l'affichage
                    reservation.setTypeCarburant(rs.getString("type_carburant"));
                    
                    planifications.add(planification);
                }
            }
        }
        
        return planifications;
    }
    
    /**
     * Récupère les réservations sans véhicule pour une date donnée
     */
    public List<Reservation> getReservationsSansVehicule(Date date) throws SQLException {
        return reservationDAO.findWithoutVehiculeByDate(date);
    }
       public List<Reservation> getReservationsAll(Date date) throws SQLException {
        return reservationDAO.getReservations(date);
    }
    
    /* R0 tri des reservations du groupe par ordre descroissant du nombre de passager 
     */
    
    /**
     * Assigner automatiquement les véhicules aux réservations sans véhicule.
     * 
     * REGLES SPRINT 5:
     * 1. Regrouper par fenêtre de temps d'attente (TA) au lieu du timestamp exact
     * 2. Fenêtre = [heure_premier_vol, heure_premier_vol + TA]
     * 3. Heure de départ véhicule = MAX(date_heure_arrivee) du groupe
     * 4. Traiter les groupes dans l'ORDRE CHRONOLOGIQUE
     * 5. Au sein de chaque groupe, traiter par nombre de passagers décroissant
     * 6. Ordre de dépose par nearest-neighbour
     */
    public void assignerVehiculesAutomatiquement(Date date) throws SQLException {
        List<Reservation> reservationsSansVehicule = reservationDAO.findWithoutVehiculeByDate(date);
        
        if (reservationsSansVehicule == null || reservationsSansVehicule.isEmpty()) {
            return;
        }
        
        // Lire le paramètre TA depuis la base
        int taMinutes = parametreDAO.getTempsAttente();
        
        // Trier par date_heure_arrivee croissante
        reservationsSansVehicule.sort((r1, r2) -> r1.getDateHeureArrivee().compareTo(r2.getDateHeureArrivee()));
        
        // Construire les fenêtres de regroupement TA
        List<List<Reservation>> fenetres = construireFenetresTA(reservationsSansVehicule, taMinutes);
        Map<Integer, Integer> trajetsDuJourParVehicule = getNombreTrajetsDuJourParVehicule(date);

        // Reservations non assignees reportees sur la fenetre suivante
        List<Reservation> reportees = new ArrayList<>();
        
        // Traiter chaque fenêtre
        for (List<Reservation> fenetre : fenetres) {
            Set<Integer> idsReportees = new HashSet<>();
            for (Reservation rr : reportees) {
                idsReportees.add(rr.getId());
            }

            List<Reservation> groupe = new ArrayList<>();
            Comparator<Reservation> prioriteInterne = Comparator
                .comparingInt(Reservation::getNombrePassager).reversed()
                .thenComparing(Reservation::getDateHeureArrivee)
                .thenComparingInt(Reservation::getId);

            // Sprint 8: priorite stricte au stock reporte, puis nouvelles reservations.
            List<Reservation> reporteesTriees = new ArrayList<>(reportees);
            reporteesTriees.sort(prioriteInterne);
            groupe.addAll(reporteesTriees);

            List<Reservation> nouvellesTriees = new ArrayList<>(fenetre);
            nouvellesTriees.sort(prioriteInterne);
            groupe.addAll(nouvellesTriees);
            
            // Heure de départ = MAX(date_heure_arrivee) du groupe
            Timestamp heureDepart = calculerHeureDepart(groupe);

            // Regle supplementaire: si aucun vehicule capable n'est disponible a cette heure,
            // on decale le depart a la prochaine heure de retour d'un vehicule capable.
            heureDepart = ajusterHeureDepartSelonDisponibilite(groupe, heureDepart);
            
            // Les non assignes de cette fenetre seront retentes dans la suivante
            reportees = assignerGroupeReservations(groupe, heureDepart, idsReportees, trajetsDuJourParVehicule);
        }
    }
    
    /**
     * Construit les fenêtres de regroupement basées sur le temps d'attente (TA).
     * Fenêtre = [heure_premier_vol, heure_premier_vol + TA]
     * Le début de fenêtre est fixe (premier vol du groupe), pas glissant.
     */
    private List<List<Reservation>> construireFenetresTA(List<Reservation> reservations, int taMinutes) {
        List<List<Reservation>> fenetres = new ArrayList<>();
        
        if (reservations == null || reservations.isEmpty()) {
            return fenetres;
        }
        
        List<Reservation> fenetreActuelle = new ArrayList<>();
        // debutFenetre est FIXE = heure du premier vol du groupe courant (ne glisse pas)
        Timestamp debutFenetre = reservations.get(0).getDateHeureArrivee();
        long taMillis = taMinutes * 60L * 1000L;
        
        for (Reservation r : reservations) {
            Timestamp arrivee = r.getDateHeureArrivee();
            
            if (arrivee.getTime() <= debutFenetre.getTime() + taMillis) {
                // Le vol est dans la fenêtre courante [debutFenetre, debutFenetre + TA]
                fenetreActuelle.add(r);
            } else {
                // Le vol est hors fenêtre → fermer le groupe courant
                fenetres.add(new ArrayList<>(fenetreActuelle));
                // Nouveau groupe : ce vol est le premier, il définit le nouveau début de fenêtre
                fenetreActuelle = new ArrayList<>();
                fenetreActuelle.add(r);
                debutFenetre = arrivee; // Nouveau début = heure de CE vol (fixe pour le nouveau groupe)
            }
        }
        
        // Ajouter le dernier groupe
        if (!fenetreActuelle.isEmpty()) {
            fenetres.add(fenetreActuelle);
        }
        
        return fenetres;
    }

    /**
     * Assigne des véhicules à un groupe de réservations.
     * heureDepart = MAX(arrivées du groupe) utilisé pour la vérification de disponibilité.
     */
    private List<Reservation> assignerGroupeReservations(List<Reservation> groupe, Timestamp heureDepart, Set<Integer> idsReportees, Map<Integer, Integer> trajetsDuJourParVehicule) throws SQLException {
        if (groupe == null || groupe.isEmpty()) {
            return new ArrayList<>();
        }

        int idHotelPlusLoin = trouverHotelPlusLoin(groupe);
        List<Vehicule> vehiculesDisponibles = reservationService.getVehiculesDisponibles(heureDepart, idHotelPlusLoin);

        Map<Integer, Integer> placesRestantes = new HashMap<>();
        Map<Integer, Vehicule> vehiculesMap = new HashMap<>();
        Map<Integer, List<Reservation>> reservationsParVehicule = new HashMap<>();
        List<Reservation> nonAssignees = new ArrayList<>();

        for (int i = 0; i < groupe.size(); i++) {
            Reservation r = groupe.get(i);
            int nbPax = r.getNombrePassager();

            // R1a: priorite absolue aux vehicules deja assignes dans le groupe
            Vehicule meilleur = choisirVehiculeDejaAssigneR1a(nbPax, placesRestantes, vehiculesMap, trajetsDuJourParVehicule);

            // Si aucun vehicule deja assigne ne peut absorber, on cherche un nouveau vehicule
            if (meilleur == null) {
                meilleur = choisirNouveauVehiculeDisponibleAnticipatif(
                    nbPax, groupe, i, vehiculesDisponibles, placesRestantes, trajetsDuJourParVehicule
                );
            }

            if (meilleur == null) {
                // Aucune solution pour cette reservation
                nonAssignees.add(r);
                continue;
            }

            int vehId = meilleur.getId();

            if (!placesRestantes.containsKey(vehId)) {
                placesRestantes.put(vehId, meilleur.getNombrePlace() - nbPax);
                vehiculesMap.put(vehId, meilleur);
                // Nouveau vehicule mobilise pour ce depart = 1 trajet de plus aujourd'hui.
                trajetsDuJourParVehicule.put(vehId, trajetsDuJourParVehicule.getOrDefault(vehId, 0) + 1);

                List<Reservation> liste = new ArrayList<>();
                liste.add(r);
                reservationsParVehicule.put(vehId, liste);
            } else {
                placesRestantes.put(vehId, placesRestantes.get(vehId) - nbPax);
                reservationsParVehicule.get(vehId).add(r);
            }
        }

        // Persistance finale
        for (Map.Entry<Integer, List<Reservation>> entry : reservationsParVehicule.entrySet()) {
            int vehId = entry.getKey();
            List<Reservation> sousGroupe = entry.getValue();
            List<Reservation> ordreDepose = calculerOrdreDepose(sousGroupe);

            for (Reservation r : ordreDepose) {
                // Une reservation reportee est ancree sur l'heure de depart de la nouvelle fenetre.
                if (idsReportees != null && idsReportees.contains(r.getId()) && r.getDateHeureArrivee().before(heureDepart)) {
                    reservationDAO.updateDateHeureArrivee(r.getId(), heureDepart);
                    r.setDateHeureArrivee(heureDepart);
                }
                reservationDAO.assignVehicule(r.getId(), vehId);
                r.setIdVehicule(vehId);
            }
        }

        return nonAssignees;
    }

    private Vehicule choisirVehiculeDejaAssigneR1a(
        int nbPax,
        Map<Integer, Integer> placesRestantes,
        Map<Integer, Vehicule> vehiculesMap,
        Map<Integer, Integer> trajetsDuJourParVehicule
    ) {
        Vehicule meilleur = null;
        int meilleureMarge = Integer.MAX_VALUE;

        for (Map.Entry<Integer, Integer> entry : placesRestantes.entrySet()) {
            int vehId = entry.getKey();
            int reste = entry.getValue();

            if (reste < nbPax) {
                continue;
            }

            Vehicule candidat = vehiculesMap.get(vehId);
            if (candidat == null) {
                continue;
            }

            // R1c: minimiser le gaspillage de places
            if (reste < meilleureMarge) {
                meilleureMarge = reste;
                meilleur = candidat;
                continue;
            }

            // R1d: egalite -> Diesel puis id le plus petit
            if (reste == meilleureMarge && meilleur != null) {
                int trajetsCandidat = trajetsDuJourParVehicule.getOrDefault(candidat.getId(), 0);
                int trajetsMeilleur = trajetsDuJourParVehicule.getOrDefault(meilleur.getId(), 0);

                // Nouveau departage entre R1c et R1d: moins de trajets du jour.
                if (trajetsCandidat < trajetsMeilleur) {
                    meilleur = candidat;
                    continue;
                } else if (trajetsCandidat > trajetsMeilleur) {
                    continue;
                }

                boolean candidatDiesel = "D".equals(candidat.getTypeCarburant());
                boolean meilleurDiesel = "D".equals(meilleur.getTypeCarburant());

                if (candidatDiesel && !meilleurDiesel) {
                    meilleur = candidat;
                } else if (candidatDiesel == meilleurDiesel && candidat.getId() < meilleur.getId()) {
                    meilleur = candidat;
                }
            }
        }

        return meilleur;
    }

    private Vehicule choisirNouveauVehiculeDisponibleAnticipatif(
        int nbPax,
        List<Reservation> groupe,
        int indexCourant,
        List<Vehicule> vehiculesDisponibles,
        Map<Integer, Integer> placesRestantes,
        Map<Integer, Integer> trajetsDuJourParVehicule
    ) {
        Vehicule meilleur = null;
        int meilleurNbAbsorbables = -1;
        int meilleureMarge = Integer.MAX_VALUE;

        for (Vehicule v : vehiculesDisponibles) {
            if (placesRestantes.containsKey(v.getId())) {
                continue; // deja utilise dans ce groupe
            }

            int capacite = v.getNombrePlace();
            if (capacite < nbPax) {
                continue;
            }

            int marge = capacite - nbPax;

            // Anticipation: combien de reservations suivantes pourraient tenir dans cette marge
            int nbAbsorbables = 0;
            for (int j = indexCourant + 1; j < groupe.size(); j++) {
                if (groupe.get(j).getNombrePassager() <= marge) {
                    nbAbsorbables++;
                }
            }

            if (nbAbsorbables > meilleurNbAbsorbables) {
                meilleurNbAbsorbables = nbAbsorbables;
                meilleureMarge = marge;
                meilleur = v;
                continue;
            }

            if (nbAbsorbables == meilleurNbAbsorbables) {
                // R1c: a egalite d'anticipation, on minimise la marge
                if (marge < meilleureMarge) {
                    meilleureMarge = marge;
                    meilleur = v;
                    continue;
                }

                // R1d: a egalite, preference Diesel puis id
                if (marge == meilleureMarge && meilleur != null) {
                    int trajetsCandidat = trajetsDuJourParVehicule.getOrDefault(v.getId(), 0);
                    int trajetsMeilleur = trajetsDuJourParVehicule.getOrDefault(meilleur.getId(), 0);

                    // Nouveau departage entre R1c et R1d: moins de trajets du jour.
                    if (trajetsCandidat < trajetsMeilleur) {
                        meilleur = v;
                        continue;
                    } else if (trajetsCandidat > trajetsMeilleur) {
                        continue;
                    }

                    boolean vDiesel = "D".equals(v.getTypeCarburant());
                    boolean meilleurDiesel = "D".equals(meilleur.getTypeCarburant());

                    if (vDiesel && !meilleurDiesel) {
                        meilleur = v;
                    } else if (vDiesel == meilleurDiesel && v.getId() < meilleur.getId()) {
                        meilleur = v;
                    }
                }
            }
        }

        return meilleur;
    }

    private Map<Integer, Integer> getNombreTrajetsDuJourParVehicule(Date date) throws SQLException {
        Map<Integer, Integer> trajets = new HashMap<>();
        String sql =
            "SELECT id_vehicule, COUNT(DISTINCT date_heure_arrivee) AS nb_trajets " +
            "FROM reservation " +
            "WHERE id_vehicule IS NOT NULL " +
            "AND DATE(date_heure_arrivee) = ? " +
            "GROUP BY id_vehicule";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, date);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    trajets.put(rs.getInt("id_vehicule"), rs.getInt("nb_trajets"));
                }
            }
        }

        return trajets;
    }
    /**
     * Construit les groupes de réservations pour une date donnée.
     * Regroupe par fenêtre TA D'ABORD (toutes réservations confondues),
     * puis par véhicule à l'intérieur de chaque fenêtre.
     * Tous les véhicules d'une même fenêtre partagent le même heureDepart = MAX(arrivées de la fenêtre).
     */
    public List<GroupeVehicule> construireGroupesParVehicule(Date date) throws SQLException {
        List<GroupeVehicule> groupes = new ArrayList<>();
        Map<Integer, Timestamp> disponibiliteParVehicule = new HashMap<>();
        
        List<PlanificationReservation> planifications = getPlanificationsByDate(date);
        
        if (planifications.isEmpty()) {
            return groupes;
        }
        
        int taMinutes = parametreDAO.getTempsAttente();
        long taMillis = taMinutes * 60L * 1000L;
        
        // Trier TOUTES les planifications par date_heure_arrivee
        planifications.sort((a, b) -> a.getReservation().getDateHeureArrivee()
            .compareTo(b.getReservation().getDateHeureArrivee()));
        
        // 1) Construire les fenêtres TA sur TOUTES les planifications (indépendamment du véhicule)
        List<List<PlanificationReservation>> fenetres = new ArrayList<>();
        List<PlanificationReservation> fenetreActuelle = new ArrayList<>();
        long debutFenetre = planifications.get(0).getReservation().getDateHeureArrivee().getTime();
        
        for (PlanificationReservation p : planifications) {
            long arrivee = p.getReservation().getDateHeureArrivee().getTime();
            if (arrivee <= debutFenetre + taMillis) {
                fenetreActuelle.add(p);
            } else {
                if (!fenetreActuelle.isEmpty()) {
                    fenetres.add(new ArrayList<>(fenetreActuelle));
                }
                fenetreActuelle = new ArrayList<>();
                fenetreActuelle.add(p);
                debutFenetre = arrivee;
            }
        }
        if (!fenetreActuelle.isEmpty()) {
            fenetres.add(fenetreActuelle);
        }
        
        // 2) Pour chaque fenêtre TA, calculer heureDepart = MAX(arrivées), puis sous-grouper par véhicule
        for (List<PlanificationReservation> fenetre : fenetres) {
            // Calculer le MAX(date_heure_arrivee) de toute la fenêtre
            Timestamp maxArriveeFenetre = fenetre.get(0).getReservation().getDateHeureArrivee();
            for (PlanificationReservation p : fenetre) {
                Timestamp arr = p.getReservation().getDateHeureArrivee();
                if (arr.after(maxArriveeFenetre)) {
                    maxArriveeFenetre = arr;
                }
            }
            
            // Sous-grouper par véhicule (ordre d'apparition conservé)
            Map<Integer, List<PlanificationReservation>> parVehicule = new LinkedHashMap<>();
            for (PlanificationReservation p : fenetre) {
                int vehId = p.getReservation().getIdVehicule();
                parVehicule.computeIfAbsent(vehId, k -> new ArrayList<>()).add(p);
            }
            
            for (Map.Entry<Integer, List<PlanificationReservation>> entryVeh : parVehicule.entrySet()) {
                int vehId = entryVeh.getKey();
                List<PlanificationReservation> planifVehicule = entryVeh.getValue();

                // Regle supplementaire: un vehicule ne peut repartir qu'apres son retour precedent.
                Timestamp heureDepartVehicule = maxArriveeFenetre;
                Timestamp dispoVehicule = disponibiliteParVehicule.get(vehId);
                if (dispoVehicule != null && dispoVehicule.after(heureDepartVehicule)) {
                    heureDepartVehicule = dispoVehicule;
                }

                GroupeVehicule groupeConstruit = construireUnGroupe(planifVehicule, heureDepartVehicule);
                groupes.add(groupeConstruit);
                disponibiliteParVehicule.put(vehId, groupeConstruit.getHeureRetour());
            }
        }
        
        return groupes;
    }

    /**
     * Construit un GroupeVehicule à partir d'une liste de planifications du même véhicule/fenêtre.
     * Calcule l'itinéraire nearest-neighbour avec les vraies distances.
     */
    private GroupeVehicule construireUnGroupe(List<PlanificationReservation> planifList, Timestamp heureDepartFenetre) throws SQLException {
        PlanificationReservation first = planifList.get(0);
        Reservation firstRes = first.getReservation();
        
        // Créer le véhicule
        Vehicule vehicule = new Vehicule();
        vehicule.setId(firstRes.getIdVehicule());
        vehicule.setReference(firstRes.getReferenceVehicule());
        vehicule.setTypeCarburant(firstRes.getTypeCarburant());
        vehicule.setNombrePlace(firstRes.getCapaciteVehicule());
        
        GroupeVehicule groupe = new GroupeVehicule(vehicule);
        
        // Collecter les réservations
        List<Reservation> reservations = new ArrayList<>();
        int totalPassagers = 0;
        
        for (PlanificationReservation p : planifList) {
            Reservation r = p.getReservation();
            reservations.add(r);
            totalPassagers += r.getNombrePassager();
        }
        
        // Heure de départ = celle de la fenêtre TA (MAX de TOUTES les arrivées de la fenêtre)
        Timestamp heureDepart = heureDepartFenetre;
        groupe.setHeureDepart(heureDepart);
        groupe.setTotalPassagers(totalPassagers);
        
        // Calculer l'ordre de dépose nearest-neighbour
        List<Reservation> ordreDepose = calculerOrdreDepose(reservations);
        groupe.setReservations(ordreDepose);
        
        // Récupérer la vitesse moyenne
        double vitesseMoyenne = parametreDAO.getVitesseMoyenne();
        
        // Construire l'itinéraire avec les vraies distances
        Map<String, Double> allDistances = getAllDistances(reservations);
        List<EtapeItineraire> itineraire = new ArrayList<>();
        String positionActuelle = "TNR";
        String nomPositionActuelle = "TNR";
        Timestamp heureActuelle = new Timestamp(heureDepart.getTime());
        double distanceTotale = 0;
        int dureeTotale = 0;
        
        for (Reservation r : ordreDepose) {
            String key = positionActuelle + "-" + r.getIdHotel();
            String reverseKey = r.getIdHotel() + "-" + positionActuelle;
            double distanceKm = allDistances.getOrDefault(key, allDistances.getOrDefault(reverseKey, 0.0));
            int dureeMinutes = (int) Math.round((distanceKm / vitesseMoyenne) * 60);
            
            heureActuelle = new Timestamp(heureActuelle.getTime() + dureeMinutes * 60L * 1000L);
            distanceTotale += distanceKm;
            dureeTotale += dureeMinutes;
            
            EtapeItineraire etape = new EtapeItineraire(
                nomPositionActuelle, r.getNomHotel(), distanceKm, dureeMinutes, heureActuelle);
            etape.getPassagersDeposes().add(r.getClient());
            itineraire.add(etape);
            
            positionActuelle = String.valueOf(r.getIdHotel());
            nomPositionActuelle = r.getNomHotel();
        }
        
        // Étape retour vers TNR
        // Pour le retour, utiliser la distance TNR->dernier hotel (symétrique)
        String keyRetour = "TNR-" + positionActuelle;
        String keyRetourInverse = positionActuelle + "-TNR";
        double distanceRetour = allDistances.getOrDefault(keyRetour, allDistances.getOrDefault(keyRetourInverse, 0.0));
        int dureeRetour = (int) Math.round((distanceRetour / vitesseMoyenne) * 60);
        heureActuelle = new Timestamp(heureActuelle.getTime() + dureeRetour * 60L * 1000L);
        distanceTotale += distanceRetour;
        dureeTotale += dureeRetour;
        
        EtapeItineraire retour = new EtapeItineraire(
            nomPositionActuelle, "TNR", distanceRetour, dureeRetour, heureActuelle);
        itineraire.add(retour);
        
        groupe.setItineraire(itineraire);
        groupe.setHeureRetour(heureActuelle);
        groupe.setDistanceTotaleKm(distanceTotale);
        groupe.setDureeTotaleMinutes(dureeTotale);
        
        return groupe;
    }
    
    /**
     * Regroupe et assigne automatiquement les véhicules pour une date.
     * Exécute l'algorithme d'assignation puis construit les groupes.
     */
    public List<GroupeVehicule> regrouperEtAssigner(Date date) throws SQLException {
        // D'abord assigner les véhicules
        assignerVehiculesAutomatiquement(date);
        
        // Puis construire les groupes
        return construireGroupesParVehicule(date);
    }

    /**
     * Calcule l'heure de départ d'un groupe = MAX(date_heure_arrivee).
     */
    private Timestamp calculerHeureDepart(List<Reservation> groupe) {
        Timestamp max = groupe.get(0).getDateHeureArrivee();
        for (Reservation r : groupe) {
            if (r.getDateHeureArrivee().after(max)) {
                max = r.getDateHeureArrivee();
            }
        }
        return max;
    }

    /**
     * Expose la logique Sprint 5 de calcul d'heure de depart effective:
     * - base = MAX(date_heure_arrivee) du groupe
     * - ajustement selon disponibilite d'au moins un vehicule capable
     */
    public Timestamp calculerHeureDepartAjustee(List<Reservation> groupe) throws SQLException {
        if (groupe == null || groupe.isEmpty()) {
            return null;
        }
        Timestamp heureDepart = calculerHeureDepart(groupe);
        return ajusterHeureDepartSelonDisponibilite(groupe, heureDepart);
    }

    private int getMaxPassagers(List<Reservation> groupe) {
        int max = 0;
        for (Reservation r : groupe) {
            if (r.getNombrePassager() > max) {
                max = r.getNombrePassager();
            }
        }
        return max;
    }

    private Timestamp ajusterHeureDepartSelonDisponibilite(List<Reservation> groupe, Timestamp heureDepartInitiale) throws SQLException {
        Timestamp heureDepart = heureDepartInitiale;
        int nbPassagersMax = getMaxPassagers(groupe);
        int idHotelPlusLoin = trouverHotelPlusLoin(groupe);

        // On borne les tentatives pour eviter les boucles infinies en cas de donnees incoherentes.
        for (int i = 0; i < 10; i++) {
            List<Vehicule> disponibles = reservationService.getVehiculesDisponibles(heureDepart, idHotelPlusLoin);
            boolean auMoinsUnCapable = false;
            for (Vehicule v : disponibles) {
                if (v.getNombrePlace() >= nbPassagersMax) {
                    auMoinsUnCapable = true;
                    break;
                }
            }

            if (auMoinsUnCapable) {
                return heureDepart;
            }

            Timestamp prochaine = reservationService.getProchaineDisponibiliteVehiculeCapable(nbPassagersMax, heureDepart);
            if (prochaine == null || !prochaine.after(heureDepart)) {
                return heureDepart;
            }
            heureDepart = prochaine;
        }

        return heureDepart;
    }

    /**
     * Trouve l'hôtel le plus éloigné parmi les réservations du groupe.
     * Utilisé pour estimer le temps de retour du véhicule (disponibilité).
     */
    private int trouverHotelPlusLoin(List<Reservation> reservations) throws SQLException {
        int idHotelPlusLoin = reservations.get(0).getIdHotel();
        double maxDistance = 0;
        
        String sql = "SELECT kilometer FROM distance WHERE from_id = 'TNR' AND to_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Reservation r : reservations) {
                ps.setString(1, String.valueOf(r.getIdHotel()));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        double dist = rs.getDouble("kilometer");
                        if (dist > maxDistance) {
                            maxDistance = dist;
                            idHotelPlusLoin = r.getIdHotel();
                        }
                    }
                }
            }
        }
        return idHotelPlusLoin;
    }

    /**
     * Calcule l'ordre de dépose par nearest-neighbour.
     * Départ depuis TNR, à chaque étape on va vers l'hôtel le plus proche.
     * Départage alphabétique si même distance.
     */
   private List<Reservation> calculerOrdreDepose(List<Reservation> reservations) throws SQLException {
    if (reservations.size() <= 1) {
        return new ArrayList<>(reservations);
    }

    Map<String, Double> distances = getAllDistances(reservations);
    List<Reservation> restantes = new ArrayList<>(reservations);
    List<Reservation> ordre = new ArrayList<>();
    String positionActuelle = "TNR";

    while (!restantes.isEmpty()) {
        Reservation plusProche = null;
        double minDist = Double.MAX_VALUE;

        for (Reservation r : restantes) {
            String hotelCible = String.valueOf(r.getIdHotel());
            double dist;

            // Meme hotel => distance nulle
            if (positionActuelle.equals(hotelCible)) {
                dist = 0.0;
            } else {
                String key = positionActuelle + "-" + hotelCible;
                String reverseKey = hotelCible + "-" + positionActuelle;

                // Fallback sur sens inverse si la distance directe manque
                dist = distances.getOrDefault(key, distances.getOrDefault(reverseKey, Double.MAX_VALUE));
            }

            // Important: accepter le 1er candidat meme si dist == Double.MAX_VALUE
            if (plusProche == null || dist < minDist) {
                minDist = dist;
                plusProche = r;
            } else if (dist == minDist) {
                String nomR = r.getNomHotel() == null ? "" : r.getNomHotel();
                String nomP = plusProche.getNomHotel() == null ? "" : plusProche.getNomHotel();

                if (nomR.compareTo(nomP) < 0) {
                    plusProche = r;
                }
            }
        }

        if (plusProche == null) {
            break;
        }

        ordre.add(plusProche);
        restantes.remove(plusProche);
        positionActuelle = String.valueOf(plusProche.getIdHotel());
    }

    // Filet de securite: ne jamais perdre de reservation
    if (!restantes.isEmpty()) {
        ordre.addAll(restantes);
    }

    return ordre;
}
    /**
     * Récupère toutes les distances nécessaires pour le calcul d'itinéraire.
     * TNR → chaque hôtel + inter-hôtels (dans les deux sens).
     */
    // private Map<String, Double> getAllDistances(List<Reservation> reservations) throws SQLException {
    //     Map<String, Double> distances = new HashMap<>();
    //     Set<Integer> hotelIds = new HashSet<>();
    //     for (Reservation r : reservations) {
    //         hotelIds.add(r.getIdHotel());
    //     }
        
    //     // TNR → hôtels
    //     String sql = "SELECT from_id, to_id, kilometer FROM distance WHERE from_id = 'TNR' AND to_id = ANY(?)";
    //     try (Connection conn = DBConnection.getConnection()) {
    //         String[] ids = new String[hotelIds.size()];
    //         int i = 0;
    //         for (int id : hotelIds) {
    //             ids[i++] = String.valueOf(id);
    //         }
            
    //         try (PreparedStatement ps = conn.prepareStatement(sql)) {
    //             ps.setArray(1, conn.createArrayOf("VARCHAR", ids));
    //             try (ResultSet rs = ps.executeQuery()) {
    //                 while (rs.next()) {
    //                     String key = rs.getString("from_id") + "-" + rs.getString("to_id");
    //                     distances.put(key, rs.getDouble("kilometer"));
    //                 }
    //             }
    //         }
            
    //         // Inter-hôtels (les deux directions)
    //         String sql2 = "SELECT from_id, to_id, kilometer FROM distance WHERE from_id = ANY(?) AND to_id = ANY(?)";
    //         try (PreparedStatement ps2 = conn.prepareStatement(sql2)) {
    //             ps2.setArray(1, conn.createArrayOf("VARCHAR", ids));
    //             ps2.setArray(2, conn.createArrayOf("VARCHAR", ids));
    //             try (ResultSet rs = ps2.executeQuery()) {
    //                 while (rs.next()) {
    //                     String key = rs.getString("from_id") + "-" + rs.getString("to_id");
    //                     distances.put(key, rs.getDouble("kilometer"));
    //                 }
    //             }
    //         }
    //     }
        
    //     // Ajouter explicitement les distances identite hotel->hotel = 0
    //     for (String id : ids) {
    //         distances.put(id + "-" + id, 0.0);
    //     }
    //     distances.put("TNR-TNR", 0.0);
    // }
private Map<String, Double> getAllDistances(List<Reservation> reservations) throws SQLException {
    Map<String, Double> distances = new HashMap<>();
    Set<Integer> hotelIds = new HashSet<>();
    for (Reservation r : reservations) {
        hotelIds.add(r.getIdHotel());
    }

    String sql = "SELECT from_id, to_id, kilometer FROM distance WHERE from_id = 'TNR' AND to_id = ANY(?)";

    try (Connection conn = DBConnection.getConnection()) {
        String[] ids = new String[hotelIds.size()];
        int i = 0;
        for (int id : hotelIds) {
            ids[i++] = String.valueOf(id);
        }

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setArray(1, conn.createArrayOf("VARCHAR", ids));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("from_id") + "-" + rs.getString("to_id");
                    distances.put(key, rs.getDouble("kilometer"));
                }
            }
        }

        String sql2 = "SELECT from_id, to_id, kilometer FROM distance WHERE from_id = ANY(?) AND to_id = ANY(?)";
        try (PreparedStatement ps2 = conn.prepareStatement(sql2)) {
            ps2.setArray(1, conn.createArrayOf("VARCHAR", ids));
            ps2.setArray(2, conn.createArrayOf("VARCHAR", ids));
            try (ResultSet rs = ps2.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("from_id") + "-" + rs.getString("to_id");
                    distances.put(key, rs.getDouble("kilometer"));
                }
            }
        }

        // Hotel -> TNR (utile pour le fallback symetrique TNR -> Hotel)
        String sql3 = "SELECT from_id, to_id, kilometer FROM distance WHERE from_id = ANY(?) AND to_id = 'TNR'";
        try (PreparedStatement ps3 = conn.prepareStatement(sql3)) {
            ps3.setArray(1, conn.createArrayOf("VARCHAR", ids));
            try (ResultSet rs = ps3.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("from_id") + "-" + rs.getString("to_id");
                    distances.put(key, rs.getDouble("kilometer"));
                }
            }
        }

        // Ajouter explicitement les distances identite hotel->hotel = 0
        for (String id : ids) {
            distances.put(id + "-" + id, 0.0);
        }
        distances.put("TNR-TNR", 0.0);
    }

    return distances;
}
}