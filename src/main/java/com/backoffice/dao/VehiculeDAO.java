package com.backoffice.dao;

import com.backoffice.database.DBConnection;
import com.backoffice.model.Vehicule;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class VehiculeDAO {

    // INSERT
    public void insert(Vehicule vehicule) throws SQLException {
        String sql = "INSERT INTO vehicule (reference, nombre_place, type_carburant) VALUES (?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vehicule.getReference());
            ps.setInt(2, vehicule.getNombrePlace());
            ps.setString(3, vehicule.getTypeCarburant());
            ps.executeUpdate();
        }
    }

    // UPDATE
    public void update(Vehicule vehicule) throws SQLException {
        String sql = "UPDATE vehicule SET reference = ?, nombre_place = ?, type_carburant = ? WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vehicule.getReference());
            ps.setInt(2, vehicule.getNombrePlace());
            ps.setString(3, vehicule.getTypeCarburant());
            ps.setInt(4, vehicule.getId());
            ps.executeUpdate();
        }
    }

    // DELETE
    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM vehicule WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // FIND ALL
    public List<Vehicule> findAll() throws SQLException {
        List<Vehicule> vehicules = new ArrayList<>();
        String sql = "SELECT id, reference, nombre_place, type_carburant FROM vehicule ORDER BY reference";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Vehicule v = mapResultSet(rs);
                vehicules.add(v);
            }
        }
        return vehicules;
    }
    // public List<Vehicule> findPlaceRestantes(Vehicule v)throws SQLException{
    //     List<Reservation>reservations = new ReservationDAO().findByVehiculeId(v.getId());
    // }

    // FIND BY ID
    public Vehicule findById(int id) throws SQLException {
        String sql = "SELECT id, reference, nombre_place, type_carburant FROM vehicule WHERE id = ?";
        Vehicule vehicule = null;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    vehicule = mapResultSet(rs);
                }
            }
        }
        return vehicule;
    }

    // FILTRE PAR TYPE CARBURANT
    public List<Vehicule> findByTypeCarburant(String typeCarburant) throws SQLException {
        List<Vehicule> vehicules = new ArrayList<>();
        String sql = "SELECT id, reference, nombre_place, type_carburant FROM vehicule WHERE type_carburant = ? ORDER BY reference";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, typeCarburant);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Vehicule v = mapResultSet(rs);
                    vehicules.add(v);
                }
            }
        }
        return vehicules;
    }

    // RECHERCHE PAR REFERENCE (LIKE)
    public List<Vehicule> searchByReference(String keyword) throws SQLException {
        List<Vehicule> vehicules = new ArrayList<>();
        String sql = "SELECT id, reference, nombre_place, type_carburant FROM vehicule WHERE LOWER(reference) LIKE LOWER(?) ORDER BY reference";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + keyword + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Vehicule v = mapResultSet(rs);
                    vehicules.add(v);
                }
            }
        }
        return vehicules;
    }

    // FILTRE + RECHERCHE COMBINÉS
    public List<Vehicule> findWithFilters(String typeCarburant, String keyword) throws SQLException {
        List<Vehicule> vehicules = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT id, reference, nombre_place, type_carburant FROM vehicule WHERE 1=1");

        if (typeCarburant != null && !typeCarburant.isEmpty()) {
            sql.append(" AND type_carburant = ?");
        }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append(" AND LOWER(reference) LIKE LOWER(?)");
        }
        sql.append(" ORDER BY reference");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            if (typeCarburant != null && !typeCarburant.isEmpty()) {
                ps.setString(paramIndex++, typeCarburant);
            }
            if (keyword != null && !keyword.isEmpty()) {
                ps.setString(paramIndex++, "%" + keyword + "%");
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Vehicule v = mapResultSet(rs);
                    vehicules.add(v);
                }
            }
        }
        return vehicules;
    }

    // Méthode utilitaire pour mapper un ResultSet en Vehicule
    private Vehicule mapResultSet(ResultSet rs) throws SQLException {
        Vehicule v = new Vehicule();
        v.setId(rs.getInt("id"));
        v.setReference(rs.getString("reference"));
        v.setNombrePlace(rs.getInt("nombre_place"));
        v.setTypeCarburant(rs.getString("type_carburant"));
        return v;
    }
}
