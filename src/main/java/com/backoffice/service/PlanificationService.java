package com.backoffice.service;

import com.backoffice.dao.ReservationDAO;
import com.backoffice.database.DBConnection;
import com.backoffice.model.PlanificationReservation;
import com.backoffice.model.Reservation;
import com.backoffice.model.Vehicule;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

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
     * Assigner automatiquement les véhicules aux réservations sans véhicule
     * Utilise le ReservationService qui applique les règles d'affectation correctement
     */
    public void assignerVehiculesAutomatiquement(Date date) throws SQLException {
        List<Reservation> reservationsSansVehicule = reservationDAO.findWithoutVehiculeByDate(date);
        
        for (Reservation reservation : reservationsSansVehicule) {
            // Utiliser le service d'assignation automatique
            reservationService.assignerVehiculeAuto(reservation);
        }
    }
}