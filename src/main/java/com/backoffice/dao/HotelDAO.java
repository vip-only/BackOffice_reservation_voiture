package com.backoffice.dao;

import com.backoffice.database.DBConnection;
import com.backoffice.model.Hotel;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class HotelDAO {

    public List<Hotel> findAll() throws SQLException {
        List<Hotel> hotels = new ArrayList<>();
        String sql = "SELECT id_hotel, nom FROM hotel ORDER BY nom";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Hotel hotel = new Hotel();
                hotel.setIdHotel(rs.getInt("id_hotel"));
                hotel.setNom(rs.getString("nom"));
                hotels.add(hotel);
            }
        }
        return hotels;
    }

    public Hotel findById(int id) throws SQLException {
        String sql = "SELECT id_hotel, nom FROM hotel WHERE id_hotel = ?";
        Hotel hotel = null;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    hotel = new Hotel();
                    hotel.setIdHotel(rs.getInt("id_hotel"));
                    hotel.setNom(rs.getString("nom"));
                }
            }
        }
        return hotel;
    }
}
