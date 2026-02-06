package com.backoffice.dao;

import com.backoffice.database.DBConnection;
import com.backoffice.model.Reservation;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ReservationDAO {

    public void insert(Reservation reservation) throws SQLException {
        String sql = "INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES (?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reservation.getClient());
            ps.setInt(2, reservation.getNombrePassager());
            ps.setTimestamp(3, reservation.getDateHeureArrivee());
            ps.setInt(4, reservation.getIdHotel());
            ps.executeUpdate();
        }
    }

    public List<Reservation> findAll() throws SQLException {
        List<Reservation> reservations = new ArrayList<>();
        String sql = "SELECT r.id, r.client, r.nombre_passager, r.date_heure_arrivee, r.id_hotel, h.nom AS nom_hotel "
                   + "FROM reservation r "
                   + "JOIN hotel h ON r.id_hotel = h.id_hotel "
                   + "ORDER BY r.date_heure_arrivee DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Reservation res = new Reservation();
                res.setId(rs.getInt("id"));
                res.setClient(rs.getString("client"));
                res.setNombrePassager(rs.getInt("nombre_passager"));
                res.setDateHeureArrivee(rs.getTimestamp("date_heure_arrivee"));
                res.setIdHotel(rs.getInt("id_hotel"));
                res.setNomHotel(rs.getString("nom_hotel"));
                reservations.add(res);
            }
        }
        return reservations;
    }
}
