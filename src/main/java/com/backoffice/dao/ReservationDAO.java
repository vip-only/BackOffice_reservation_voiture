package com.backoffice.dao;

import com.backoffice.database.DBConnection;
import com.backoffice.model.Reservation;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ReservationDAO {

    // INSERT
    public void insert(Reservation reservation) throws SQLException {
        String sql = "INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, reservation.getClient());
            ps.setInt(2, reservation.getNombrePassager());
            ps.setTimestamp(3, reservation.getDateHeureArrivee());
            ps.setInt(4, reservation.getIdHotel());
            
            if (reservation.getIdVehicule() != null) {
                ps.setInt(5, reservation.getIdVehicule());
            } else {
                ps.setNull(5, Types.INTEGER);
            }
            
            ps.executeUpdate();
            
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                reservation.setId(rs.getInt(1));
            }
        }
    }

    // UPDATE
    public void update(Reservation reservation) throws SQLException {
        String sql = "UPDATE reservation SET client = ?, nombre_passager = ?, date_heure_arrivee = ?, id_hotel = ?, id_vehicule = ? WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reservation.getClient());
            ps.setInt(2, reservation.getNombrePassager());
            ps.setTimestamp(3, reservation.getDateHeureArrivee());
            ps.setInt(4, reservation.getIdHotel());
            
            if (reservation.getIdVehicule() != null) {
                ps.setInt(5, reservation.getIdVehicule());
            } else {
                ps.setNull(5, Types.INTEGER);
            }
            
            ps.setInt(6, reservation.getId());
            ps.executeUpdate();
        }
    }

    // ASSIGNER UN VEHICULE
    public void assignVehicule(int reservationId, int vehiculeId) throws SQLException {
        String sql = "UPDATE reservation SET id_vehicule = ? WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, vehiculeId);
            ps.setInt(2, reservationId);
            ps.executeUpdate();
        }
    }

    // FIND ALL (avec jointures)
    public List<Reservation> findAll() throws SQLException {
        List<Reservation> reservations = new ArrayList<>();
        String sql = "SELECT r.id, r.client, r.nombre_passager, r.date_heure_arrivee, r.id_hotel, r.id_vehicule, " +
                     "h.nom AS nom_hotel, v.reference AS reference_vehicule " +
                     "FROM reservation r " +
                     "JOIN hotel h ON r.id_hotel = h.id_hotel " +
                     "LEFT JOIN vehicule v ON r.id_vehicule = v.id " +
                     "ORDER BY r.date_heure_arrivee DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Reservation res = mapResultSet(rs);
                reservations.add(res);
            }
        }
        return reservations;
    }
    // public List<Reservation> findByVehiculeId(int vehiculeId) throws SQLException {
    //     List<Reservation> reservations = new ArrayList<>();
    //     String sql = "SELECT r.id, r.client, r.nombre_passager, r.date_heure_arrivee, r.id_hotel, r.id_vehicule, " +
    //                  "h.nom AS nom_hotel, v.reference AS reference_vehicule " +
    //                  "FROM reservation r " +
    //                  "JOIN hotel h ON r.id_hotel = h.id_hotel " +
    //                  "LEFT JOIN vehicule v ON r.id_vehicule = v.id " +
    //                  "WHERE r.id_vehicule = ? " +
    //                  "ORDER BY r.date_heure_arrivee DESC";

    //     return reservation;
    // }
    // FIND BY ID
    public Reservation findById(int id) throws SQLException {
        String sql = "SELECT r.id, r.client, r.nombre_passager, r.date_heure_arrivee, r.id_hotel, r.id_vehicule, " +
                     "h.nom AS nom_hotel, v.reference AS reference_vehicule " +
                     "FROM reservation r " +
                     "JOIN hotel h ON r.id_hotel = h.id_hotel " +
                     "LEFT JOIN vehicule v ON r.id_vehicule = v.id " +
                     "WHERE r.id = ?";
        
        Reservation reservation = null;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    reservation = mapResultSet(rs);
                }
            }
        }
        return reservation;
    }

    // FIND BY DATE
    public List<Reservation> findByDate(java.sql.Date date) throws SQLException {
        List<Reservation> reservations = new ArrayList<>();
        String sql = "SELECT r.id, r.client, r.nombre_passager, r.date_heure_arrivee, r.id_hotel, r.id_vehicule, " +
                     "h.nom AS nom_hotel, v.reference AS reference_vehicule " +
                     "FROM reservation r " +
                     "JOIN hotel h ON r.id_hotel = h.id_hotel " +
                     "LEFT JOIN vehicule v ON r.id_vehicule = v.id " +
                     "WHERE DATE(r.date_heure_arrivee) = ? " +
                     "ORDER BY r.date_heure_arrivee";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, date);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Reservation res = mapResultSet(rs);
                    reservations.add(res);
                }
            }
        }
        return reservations;
    }

    // FIND RESERVATIONS SANS VEHICULE
    public List<Reservation> findWithoutVehicule() throws SQLException {
        List<Reservation> reservations = new ArrayList<>();
        String sql = "SELECT r.id, r.client, r.nombre_passager, r.date_heure_arrivee, r.id_hotel, r.id_vehicule, " +
                     "h.nom AS nom_hotel " +
                     "FROM reservation r " +
                     "JOIN hotel h ON r.id_hotel = h.id_hotel " +
                     "WHERE r.id_vehicule IS NULL " +
                     "ORDER BY r.date_heure_arrivee";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Reservation res = mapResultSet(rs);
                reservations.add(res);
            }
        }
        return reservations;
    }

    // FIND RESERVATIONS SANS VEHICULE PAR DATE (triées par nombre de passagers décroissant)
    public List<Reservation> findWithoutVehiculeByDate(java.sql.Date date) throws SQLException {
        List<Reservation> reservations = new ArrayList<>();
        String sql = "SELECT r.id, r.client, r.nombre_passager, r.date_heure_arrivee, r.id_hotel, r.id_vehicule, " +
                     "h.nom AS nom_hotel " +
                     "FROM reservation r " +
                     "JOIN hotel h ON r.id_hotel = h.id_hotel " +
                     "WHERE r.id_vehicule IS NULL AND DATE(r.date_heure_arrivee) = ? " +
                     "ORDER BY r.nombre_passager DESC, r.date_heure_arrivee";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, date);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Reservation res = mapResultSet(rs);
                    reservations.add(res);
                }
            }
        }
        return reservations;
    }

    // DELETE
    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM reservation WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // Méthode utilitaire pour mapper un ResultSet en Reservation
    private Reservation mapResultSet(ResultSet rs) throws SQLException {
        Reservation res = new Reservation();
        res.setId(rs.getInt("id"));
        res.setClient(rs.getString("client"));
        res.setNombrePassager(rs.getInt("nombre_passager"));
        res.setDateHeureArrivee(rs.getTimestamp("date_heure_arrivee"));
        res.setIdHotel(rs.getInt("id_hotel"));
        
        // id_vehicule peut être NULL
        int idVehicule = rs.getInt("id_vehicule");
        if (!rs.wasNull()) {
            res.setIdVehicule(idVehicule);
        }
        
        res.setNomHotel(rs.getString("nom_hotel"));
        
        // reference_vehicule peut ne pas exister dans certaines requêtes
        try {
            res.setReferenceVehicule(rs.getString("reference_vehicule"));
        } catch (SQLException e) {
            // Colonne non présente, on ignore
        }
        
        return res;
    }
}
