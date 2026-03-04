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
     * 1. Traiter en priorité les réservations avec le plus de passagers (déjà trié dans DAO)
     * 2. Regrouper les clients arrivant à la même date/heure dans la même voiture si capacité le permet
     * 3. Pas de temps d'attente pour le regroupement
     * 4. Ordre de dépose par distance minimale (nearest-neighbour)
     */
    public void assignerVehiculesAutomatiquement(Date date) throws SQLException {
        // Récupérer toutes les réservations sans véhicule, triées par nombre de passagers DESC
        List<Reservation> reservationsSansVehicule = reservationDAO.findWithoutVehiculeByDate(date);
        
        if (reservationsSansVehicule == null || reservationsSansVehicule.isEmpty()) {
            return;
        }
        
        // Grouper les réservations par date/heure d'arrivée exacte
        Map<Timestamp, List<Reservation>> groupesParHeure = new LinkedHashMap<>();
        for (Reservation r : reservationsSansVehicule) {
            Timestamp ts = r.getDateHeureArrivee();
            if (ts != null) {
                if (!groupesParHeure.containsKey(ts)) {
                    groupesParHeure.put(ts, new ArrayList<>());
                }
                groupesParHeure.get(ts).add(r);
            }
        }
        
        // Traiter chaque groupe de réservations (même heure d'arrivée)
        for (Map.Entry<Timestamp, List<Reservation>> entry : groupesParHeure.entrySet()) {
            Timestamp heureArrivee = entry.getKey();
            List<Reservation> groupe = entry.getValue();
            
            // Le groupe est déjà trié par nombre de passagers DESC (grâce au DAO)
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
     * Récupère les distances depuis TNR pour les hôtels du groupe.
     */
    private Map<Integer, Double> getDistancesHotels(List<Reservation> groupe) throws SQLException {
        Map<Integer, Double> distances = new HashMap<>();
        
        if (groupe == null || groupe.isEmpty()) {
            return distances;
        }
        
        // Récupérer les IDs d'hôtels uniques
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
     * Calcule l'ordre de dépose selon l'heuristique du plus proche voisin (nearest-neighbour).
     * Part de TNR, va au plus proche, puis au suivant le plus proche, etc.
     */
    private List<Reservation> calculerOrdreDepose(List<Reservation> groupe) throws SQLException {
        if (groupe == null || groupe.isEmpty()) {
            return new ArrayList<>();
        }
        if (groupe.size() == 1) {
            return new ArrayList<>(groupe);
        }
        
        // Récupérer les distances depuis TNR
        Map<Integer, Double> distancesTNR = getDistancesHotels(groupe);
        
        // Pour nearest-neighbour, on a besoin des distances inter-hôtels
        // Simplification: on utilise les distances depuis TNR comme approximation
        // (en réalité, il faudrait une matrice de distances entre tous les hôtels)
        
        List<Reservation> ordreDepose = new ArrayList<>();
        List<Reservation> restants = new ArrayList<>(groupe);
        
        // Point de départ: TNR (distance 0)
        double positionActuelle = 0;
        int hotelActuel = -1; // Représente TNR
        
        while (!restants.isEmpty()) {
            // Trouver la réservation dont l'hôtel est le plus proche de la position actuelle
            Reservation plusProche = null;
            double distanceMinimale = Double.MAX_VALUE;
            
            for (Reservation r : restants) {
                double distanceHotel = distancesTNR.getOrDefault(r.getIdHotel(), Double.MAX_VALUE);
                
                // Si on est à TNR (hotelActuel = -1), on prend la distance directe
                // Sinon, on approxime par |distance(hotel) - distance(actuel)|
                double distanceDepuisActuel;
                if (hotelActuel == -1) {
                    distanceDepuisActuel = distanceHotel;
                } else {
                    double distanceActuelle = distancesTNR.getOrDefault(hotelActuel, 0.0);
                    distanceDepuisActuel = Math.abs(distanceHotel - distanceActuelle);
                }
                
                if (distanceDepuisActuel < distanceMinimale) {
                    distanceMinimale = distanceDepuisActuel;
                    plusProche = r;
                }
            }
            
            if (plusProche != null) {
                ordreDepose.add(plusProche);
                restants.remove(plusProche);
                hotelActuel = plusProche.getIdHotel();
            }
        }
        
        return ordreDepose;
    }
}