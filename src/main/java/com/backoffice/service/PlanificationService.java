package com.backoffice.service;

import com.backoffice.dao.ReservationDAO;
import com.backoffice.database.DBConnection;
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

public class PlanificationService {
    
    private ReservationDAO reservationDAO = new ReservationDAO();
    private ReservationService reservationService = new ReservationService();
    private VehiculeAffectationService affectationService = new VehiculeAffectationService();
    
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
                    reservation.setIdVehicule(rs.getInt("vehicule_id"));
                    reservation.setReferenceVehicule(rs.getString("vehicule"));
                    
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
    
    /**
     * Assigner automatiquement les véhicules aux réservations sans véhicule.
     * 
     * NOUVELLES REGLES:
     * 1. Traiter les groupes dans l'ORDRE CHRONOLOGIQUE (date/heure d'arrivée)
     * 2. Au sein de chaque groupe, traiter par nombre de passagers décroissant
     * 3. Regrouper les clients arrivant à la même date/heure dans la même voiture si capacité le permet
     * 4. Pas de temps d'attente pour le regroupement
     * 5. Ordre de dépose par distance minimale (nearest-neighbour)
     */
    public void assignerVehiculesAutomatiquement(Date date) throws SQLException {
        // Récupérer toutes les réservations sans véhicule, triées par nombre de passagers DESC
        List<Reservation> reservationsSansVehicule = reservationDAO.findWithoutVehiculeByDate(date);
        
        if (reservationsSansVehicule == null || reservationsSansVehicule.isEmpty()) {
            return;
        }
        
        // Grouper les réservations par date/heure d'arrivée exacte
        // Utiliser TreeMap pour garantir l'ordre CHRONOLOGIQUE des clés
        Map<Timestamp, List<Reservation>> groupesParHeure = new java.util.TreeMap<>();
        for (Reservation r : reservationsSansVehicule) {
            Timestamp ts = r.getDateHeureArrivee();
            if (ts != null) {
                if (!groupesParHeure.containsKey(ts)) {
                    groupesParHeure.put(ts, new ArrayList<>());
                }
                groupesParHeure.get(ts).add(r);
            }
        }
        
        // Trier chaque groupe par nombre de passagers DESC avant traitement
        for (List<Reservation> groupe : groupesParHeure.values()) {
            groupe.sort((r1, r2) -> Integer.compare(r2.getNombrePassager(), r1.getNombrePassager()));
        }
        
        // Traiter chaque groupe de réservations dans l'ORDRE CHRONOLOGIQUE
        for (Map.Entry<Timestamp, List<Reservation>> entry : groupesParHeure.entrySet()) {
            Timestamp heureArrivee = entry.getKey();
            List<Reservation> groupe = entry.getValue();
            
            // Le groupe est maintenant trié par nombre de passagers DESC
            assignerGroupeReservations(groupe, heureArrivee);
        }
    }
    
    /**
     * Assigne un véhicule à un groupe de réservations arrivant à la même heure.
     * Essaie de regrouper dans un seul véhicule si la capacité le permet.
     */
    private void assignerGroupeReservations(List<Reservation> groupe, Timestamp heureArrivee) throws SQLException {
        if (groupe.isEmpty()) {
            return;
        }
        
        // Calculer le total de passagers du groupe
        int totalPassagers = groupe.stream().mapToInt(Reservation::getNombrePassager).sum();
        
        // Trouver l'hôtel le plus éloigné (pour calculer le temps de retour max)
        int idHotelPlusLoin = trouverHotelPlusLoin(groupe);
        
        // Essayer de trouver un véhicule pouvant accueillir tout le groupe
        List<Vehicule> vehiculesDisponibles = reservationService.getVehiculesDisponibles(heureArrivee, idHotelPlusLoin);
        Vehicule vehiculeGroupe = affectationService.choisirMeilleurVehicule(vehiculesDisponibles, totalPassagers);
        
        if (vehiculeGroupe != null) {
            // Un seul véhicule peut prendre tout le groupe
            // Déterminer l'ordre de dépose (nearest-neighbour)
            List<Reservation> ordreDepose = calculerOrdreDepose(groupe);
            
            // Assigner le même véhicule à toutes les réservations du groupe
            for (Reservation r : ordreDepose) {
                reservationDAO.assignVehicule(r.getId(), vehiculeGroupe.getId());
                r.setIdVehicule(vehiculeGroupe.getId());
            }
        } else {
            // Pas assez de capacité pour regrouper, assigner individuellement
            // (traité par ordre de passagers décroissant)
            for (Reservation r : groupe) {
                // Vérifier si déjà assigné (par un groupe précédent)
                if (r.getIdVehicule() == null) {
                    reservationService.assignerVehiculeAuto(r);
                }
            }
        }
    }
    
    /**
     * Trouve l'hôtel le plus éloigné du groupe (pour le calcul du temps de retour).
     */
    private int trouverHotelPlusLoin(List<Reservation> groupe) throws SQLException {
        if (groupe == null || groupe.isEmpty()) {
            return 1; // Valeur par défaut
        }
        if (groupe.size() == 1) {
            return groupe.get(0).getIdHotel();
        }
        
        // Récupérer les distances pour tous les hôtels du groupe
        Map<Integer, Double> distances = getDistancesHotels(groupe);
        
        if (distances.isEmpty()) {
            return groupe.get(0).getIdHotel();
        }
        
        // Trouver l'hôtel avec la distance max
        int hotelPlusLoin = groupe.get(0).getIdHotel();
        double distanceMax = 0;
        for (Map.Entry<Integer, Double> entry : distances.entrySet()) {
            if (entry.getValue() > distanceMax) {
                distanceMax = entry.getValue();
                hotelPlusLoin = entry.getKey();
            }
        }
        return hotelPlusLoin;
    }
    
    /**
     * Recupere les distances depuis TNR pour les hotels du groupe.
     */
    private Map<Integer, Double> getDistancesHotels(List<Reservation> groupe) throws SQLException {
        Map<Integer, Double> distances = new HashMap<>();
        
        if (groupe == null || groupe.isEmpty()) {
            return distances;
        }
        
        // Recuperer les IDs d'hotels uniques
        Set<Integer> hotelIds = new HashSet<>();
        for (Reservation r : groupe) {
            hotelIds.add(r.getIdHotel());
        }
        
        if (hotelIds.isEmpty()) {
            return distances;
        }
        
        // Construire les placeholders
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < hotelIds.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }
        
        String sql = "SELECT CAST(to_id AS INTEGER) AS hotel_id, kilometer " +
                     "FROM distance WHERE from_id = 'TNR' AND to_id IN (" + placeholders.toString() + ")";
        
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            int idx = 1;
            for (Integer hotelId : hotelIds) {
                ps.setString(idx++, String.valueOf(hotelId));
            }
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    distances.put(rs.getInt("hotel_id"), rs.getDouble("kilometer"));
                }
            }
        }
        
        return distances;
    }
    
    /**
     * Recupere toutes les distances (TNR vers hotels ET inter-hotels).
     * Cle de la map: "from_id-to_id" (ex: "TNR-1", "1-2", etc.)
     */
    private Map<String, Double> getAllDistances(List<Reservation> groupe) throws SQLException {
        Map<String, Double> distances = new HashMap<>();
        
        if (groupe == null || groupe.isEmpty()) {
            return distances;
        }
        
        // Recuperer les IDs d'hotels uniques
        Set<Integer> hotelIds = new HashSet<>();
        for (Reservation r : groupe) {
            hotelIds.add(r.getIdHotel());
        }
        
        if (hotelIds.isEmpty()) {
            return distances;
        }
        
        // Construire les placeholders pour les hotels
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < hotelIds.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }
        
        // Recuperer distances depuis TNR et entre hotels
        String sql = "SELECT from_id, to_id, kilometer FROM distance " +
                     "WHERE (from_id = 'TNR' AND to_id IN (" + placeholders + ")) " +
                     "   OR (from_id IN (" + placeholders + ") AND to_id IN (" + placeholders + "))";
        
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            int idx = 1;
            // Pour TNR -> hotels
            for (Integer hotelId : hotelIds) {
                ps.setString(idx++, String.valueOf(hotelId));
            }
            // Pour inter-hotels (from)
            for (Integer hotelId : hotelIds) {
                ps.setString(idx++, String.valueOf(hotelId));
            }
            // Pour inter-hotels (to)
            for (Integer hotelId : hotelIds) {
                ps.setString(idx++, String.valueOf(hotelId));
            }
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String fromId = rs.getString("from_id");
                    String toId = rs.getString("to_id");
                    double km = rs.getDouble("kilometer");
                    
                    // Stocker la distance dans le sens original
                    distances.put(fromId + "-" + toId, km);
                    
                    // Pour les distances inter-hotels (pas TNR), stocker aussi le sens inverse
                    // Cela permet d'avoir une seule entree en base pour chaque paire d'hotels
                    if (!"TNR".equals(fromId) && !"TNR".equals(toId)) {
                        distances.put(toId + "-" + fromId, km);
                    }
                }
            }
        }
        
        return distances;
    }
    
    /**
     * Calcule l'ordre de depose selon l'heuristique du plus proche voisin (nearest-neighbour).
     * Part de TNR, va au plus proche, puis au suivant le plus proche, etc.
     * Utilise les vraies distances inter-hotels stockees en base.
     */
    private List<Reservation> calculerOrdreDepose(List<Reservation> groupe) throws SQLException {
        if (groupe == null || groupe.isEmpty()) {
            return new ArrayList<>();
        }
        if (groupe.size() == 1) {
            return new ArrayList<>(groupe);
        }
        
        // Recuperer TOUTES les distances (TNR->hotels et inter-hotels)
        Map<String, Double> allDistances = getAllDistances(groupe);
        
        List<Reservation> ordreDepose = new ArrayList<>();
        List<Reservation> restants = new ArrayList<>(groupe);
        
        // Point de depart: TNR
        String positionActuelle = "TNR";
        
        while (!restants.isEmpty()) {
            // Trouver la reservation dont l'hotel est le plus proche de la position actuelle
            Reservation plusProche = null;
            double distanceMinimale = Double.MAX_VALUE;
            String nomHotelMinimal = null; // Pour departage par ordre alphabetique si meme distance
            
            for (Reservation r : restants) {
                // Cle pour trouver la distance: "position_actuelle-id_hotel"
                String key = positionActuelle + "-" + r.getIdHotel();
                double distanceDepuisActuel = allDistances.getOrDefault(key, Double.MAX_VALUE);
                
                // Choisir le plus proche, ou en cas d'egalite, ordre alphabetique du nom d'hotel
                if (distanceDepuisActuel < distanceMinimale || 
                    (distanceDepuisActuel == distanceMinimale && nomHotelMinimal != null && 
                     r.getNomHotel().compareTo(nomHotelMinimal) < 0)) {
                    distanceMinimale = distanceDepuisActuel;
                    plusProche = r;
                    nomHotelMinimal = r.getNomHotel();
                }
            }
            
            if (plusProche != null) {
                ordreDepose.add(plusProche);
                restants.remove(plusProche);
                // Nouvelle position = l'hotel qu'on vient de visiter
                positionActuelle = String.valueOf(plusProche.getIdHotel());
            } else {
                // Aucune distance trouvee, ajouter les restants dans l'ordre
                ordreDepose.addAll(restants);
                break;
            }
        }
        
        return ordreDepose;
    }
}