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
            "trajets_vehicule AS ( " +
            "    SELECT rv.vehicule_id, rv.date_assignation AS date_heure_depart, " +
            "           MAX(rv.date_assignation + (ROUND((d.kilometer / p.valeur) * 60 * 2, 0)::INTEGER * INTERVAL '1 minute')) AS date_heure_retour " +
            "    FROM reservation_vehicule rv " +
            "    JOIN reservation r ON r.id = rv.reservation_id " +
            "    JOIN distance d ON d.from_id = 'TNR' AND d.to_id = CAST(r.id_hotel AS VARCHAR) " +
            "    CROSS JOIN (SELECT valeur FROM parametre WHERE cle = 'VITESSE_MOYENNE') p " +
            "    GROUP BY rv.vehicule_id, rv.date_assignation " +
            "), " +
            "vehicules_occupes AS ( " +
            "    SELECT DISTINCT tv.vehicule_id " +
            "    FROM trajets_vehicule tv " +
            "    CROSS JOIN nouvelle_reservation nr " +
            "    WHERE tv.date_heure_depart < nr.date_heure_retour " +
            "    AND tv.date_heure_retour > nr.date_heure_arrivee " +
            ") " +
            "SELECT v.id, v.reference, v.nombre_place, v.type_carburant, v.heure_disponibilite " +
            "FROM vehicule v " +
            "WHERE v.id NOT IN (SELECT vehicule_id FROM vehicules_occupes) " +
            "AND ?::time >= COALESCE(v.heure_disponibilite, TIME '00:00:00')";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setTimestamp(1, dateHeureArrivee);
            ps.setTimestamp(2, dateHeureArrivee);
            ps.setInt(3, idHotel);
            ps.setTime(4, new Time(dateHeureArrivee.getTime()));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Vehicule v = new Vehicule(
                        rs.getInt("id"),
                        rs.getString("reference"),
                        rs.getInt("nombre_place"),
                        rs.getString("type_carburant"),
                        rs.getTime("heure_disponibilite")
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
            "), " +
            "trajets_vehicule AS ( " +
            "    SELECT rv.vehicule_id, rv.date_assignation AS date_heure_depart, " +
            "           MAX(rv.date_assignation + (ROUND((d.kilometer / p.valeur) * 60 * 2, 0)::INTEGER * INTERVAL '1 minute')) AS date_heure_retour " +
            "    FROM reservation_vehicule rv " +
            "    JOIN reservation r ON r.id = rv.reservation_id " +
            "    JOIN distance d ON d.from_id = 'TNR' AND d.to_id = CAST(r.id_hotel AS VARCHAR) " +
            "    CROSS JOIN (SELECT valeur FROM parametre WHERE cle = 'VITESSE_MOYENNE') p " +
            "    GROUP BY rv.vehicule_id, rv.date_assignation " +
            ") " +
            "SELECT CASE " +
            "    WHEN ?::time < COALESCE(v.heure_disponibilite, TIME '00:00:00') THEN 1 " +
            "    ELSE ( " +
            "        SELECT COUNT(*) " +
            "        FROM trajets_vehicule tv " +
            "        CROSS JOIN nouvelle_reservation nr " +
            "        WHERE tv.vehicule_id = v.id " +
            "        AND tv.date_heure_depart < nr.date_heure_retour " +
            "        AND tv.date_heure_retour > nr.date_heure_arrivee " +
            "    ) " +
            "END AS nb_conflits " +
            "FROM vehicule v " +
            "WHERE v.id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setTimestamp(1, dateHeureArrivee);
            ps.setTimestamp(2, dateHeureArrivee);
            ps.setInt(3, idHotel);
            ps.setTime(4, new Time(dateHeureArrivee.getTime()));
            ps.setInt(5, vehiculeId);

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

    /**
     * Retourne la prochaine heure de retour d'un vehicule capable (capacite >= nbPassagers)
     * apres une heure donnee.
     */
    public Timestamp getProchaineDisponibiliteVehiculeCapable(int nbPassagers, Timestamp afterTime) throws SQLException {
        String sql =
            "WITH trajets_vehicule AS ( " +
            "    SELECT rv.vehicule_id, rv.date_assignation AS date_heure_depart, " +
            "           MAX(rv.date_assignation + (ROUND((d.kilometer / p.valeur) * 60 * 2, 0)::INTEGER * INTERVAL '1 minute')) AS date_heure_retour " +
            "    FROM reservation_vehicule rv " +
            "    JOIN reservation r ON r.id = rv.reservation_id " +
            "    JOIN distance d ON d.from_id = 'TNR' AND d.to_id = CAST(r.id_hotel AS VARCHAR) " +
            "    CROSS JOIN (SELECT valeur FROM parametre WHERE cle = 'VITESSE_MOYENNE') p " +
            "    GROUP BY rv.vehicule_id, rv.date_assignation " +
            ") " +
            "SELECT MIN(tv.date_heure_retour) AS prochaine_disponibilite " +
            "FROM trajets_vehicule tv " +
            "JOIN vehicule v ON tv.vehicule_id = v.id " +
            "WHERE v.nombre_place >= ? " +
            "AND tv.date_heure_retour > ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, nbPassagers);
            ps.setTimestamp(2, afterTime);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getTimestamp("prochaine_disponibilite");
                }
            }
        }

        return null;
    }
}
