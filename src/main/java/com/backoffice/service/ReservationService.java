package com.backoffice.service;

import com.backoffice.database.DBConnection;
import com.backoffice.model.Vehicule;
import com.backoffice.model.Reservation;
import com.backoffice.dao.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Service de gestion des réservations.
 * Délègue l'affectation des véhicules au VehiculeAffectationService.
 */
public class ReservationService {

    private ReservationDAO reservationDAO = new ReservationDAO();
    private VehiculeAffectationService affectationService = new VehiculeAffectationService();

    /**
     * Assigne automatiquement un véhicule à une réservation.
     * Utilise VehiculeAffectationService pour appliquer les règles d'affectation.
     * 
     * @param reservation La réservation à assigner
     * @return Le véhicule assigné ou null si aucun disponible
     */
    public Vehicule assignerVehiculeAuto(Reservation reservation) throws SQLException {
        // Étape 1: Récupérer tous les véhicules disponibles (non occupés à cette date/heure)
        List<Vehicule> vehiculesDisponibles = getVehiculesDisponibles(
            reservation.getDateHeureArrivee(), 
            reservation.getIdHotel()
        );

        if (vehiculesDisponibles.isEmpty()) {
            return null;
        }

        // Étape 2: Appliquer les règles d'affectation via le service dédié
        Vehicule meilleurVehicule = affectationService.choisirMeilleurVehicule(
            vehiculesDisponibles, 
            reservation.getNombrePassager()
        );

        // Étape 3: Assigner le véhicule à la réservation
        if (meilleurVehicule != null) {
            reservationDAO.assignVehicule(reservation.getId(), meilleurVehicule.getId());
            reservation.setIdVehicule(meilleurVehicule.getId());
        }

        return meilleurVehicule;
    }

    /**
     * Récupère les véhicules disponibles (non occupés) à une date/heure donnée.
     * Un véhicule est disponible s'il n'est pas occupé pendant la période
     * [date_heure_arrivee, date_heure_retour] de la nouvelle réservation.
     * 
     * Note: Cette méthode ne filtre PAS par capacité. Le filtrage par les règles
     * d'affectation est fait par VehiculeAffectationService.
     */
    public List<Vehicule> getVehiculesDisponibles(Timestamp dateHeureArrivee, int idHotel) throws SQLException {
        List<Vehicule> vehiculesDisponibles = new ArrayList<>();

        String sql = 
            "WITH nouvelle_reservation AS ( " +
            "    SELECT " +
            "        ?::timestamp AS date_heure_arrivee, " +
            "        ?::timestamp + (ROUND((d.kilometer / p.valeur) * 60 * 2, 0)::INTEGER * INTERVAL '1 minute') AS date_heure_retour " +
            "    FROM distance d " +
            "    CROSS JOIN (SELECT valeur FROM parametre WHERE cle = 'VITESSE_MOYENNE') p " +
            "    WHERE d.from_id = 'TNR' AND d.to_id = CAST(? AS VARCHAR) " +
            "), " +
            "vehicules_occupes AS ( " +
            "    SELECT DISTINCT ha.vehicule_id " +
            "    FROM v_historique_assignation ha " +
            "    CROSS JOIN nouvelle_reservation nr " +
            "    WHERE ha.date_heure_arrivee < nr.date_heure_retour " +
            "    AND ha.date_heure_retour > nr.date_heure_arrivee " +
            ") " +
            "SELECT v.id, v.reference, v.nombre_place, v.type_carburant " +
            "FROM vehicule v " +
            "WHERE v.id NOT IN (SELECT vehicule_id FROM vehicules_occupes)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setTimestamp(1, dateHeureArrivee);
            ps.setTimestamp(2, dateHeureArrivee);
            ps.setInt(3, idHotel);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Vehicule v = new Vehicule(
                        rs.getInt("id"),
                        rs.getString("reference"),
                        rs.getInt("nombre_place"),
                        rs.getString("type_carburant")
                    );
                    vehiculesDisponibles.add(v);
                }
            }
        }

        return vehiculesDisponibles;
    }

    /**
     * Vérifie si un véhicule spécifique est disponible à une date/heure donnée.
     */
    public boolean isVehiculeDisponible(int vehiculeId, Timestamp dateHeureArrivee, int idHotel) throws SQLException {
        String sql = 
            "WITH nouvelle_reservation AS ( " +
            "    SELECT " +
            "        ?::timestamp AS date_heure_arrivee, " +
            "        ?::timestamp + (ROUND((d.kilometer / p.valeur) * 60 * 2, 0)::INTEGER * INTERVAL '1 minute') AS date_heure_retour " +
            "    FROM distance d " +
            "    CROSS JOIN (SELECT valeur FROM parametre WHERE cle = 'VITESSE_MOYENNE') p " +
            "    WHERE d.from_id = 'TNR' AND d.to_id = CAST(? AS VARCHAR) " +
            ") " +
            "SELECT COUNT(*) AS nb_conflits " +
            "FROM v_historique_assignation ha " +
            "CROSS JOIN nouvelle_reservation nr " +
            "WHERE ha.vehicule_id = ? " +
            "AND ha.date_heure_arrivee < nr.date_heure_retour " +
            "AND ha.date_heure_retour > nr.date_heure_arrivee";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setTimestamp(1, dateHeureArrivee);
            ps.setTimestamp(2, dateHeureArrivee);
            ps.setInt(3, idHotel);
            ps.setInt(4, vehiculeId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("nb_conflits") == 0;
                }
            }
        }
        return false;
    }

    /**
     * Calcule la date/heure de retour pour une réservation donnée.
     * Retour = Arrivée + (distance / vitesse_moyenne) * 2 (aller-retour)
     */
    public Timestamp calculerDateHeureRetour(Timestamp dateHeureArrivee, int idHotel) throws SQLException {
        String sql = 
            "SELECT ?::timestamp + (ROUND((d.kilometer / p.valeur) * 60 * 2, 0)::INTEGER * INTERVAL '1 minute') AS date_heure_retour " +
            "FROM distance d " +
            "CROSS JOIN (SELECT valeur FROM parametre WHERE cle = 'VITESSE_MOYENNE') p " +
            "WHERE d.from_id = 'TNR' AND d.to_id = CAST(? AS VARCHAR)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setTimestamp(1, dateHeureArrivee);
            ps.setInt(2, idHotel);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getTimestamp("date_heure_retour");
                }
            }
        }
        return null;
    }
}
