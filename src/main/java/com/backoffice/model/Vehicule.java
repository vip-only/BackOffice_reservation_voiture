package com.backoffice.model;
import com.backoffice.database.DBConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
public class Vehicule {
    private int id;
    private String reference;
    private int nombrePlace;
    private String typeCarburant;

    public Vehicule() {}

    public Vehicule(String reference, int nombrePlace, String typeCarburant) {
        this.reference = reference;
        this.nombrePlace = nombrePlace;
        this.typeCarburant = typeCarburant;
    }

    public Vehicule(int id, String reference, int nombrePlace, String typeCarburant) {
        this.id = id;
        this.reference = reference;
        this.nombrePlace = nombrePlace;
        this.typeCarburant = typeCarburant;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public int getNombrePlace() {
        return nombrePlace;
    }

    public void setNombrePlace(int nombrePlace) {
        this.nombrePlace = nombrePlace;
    }

    public String getTypeCarburant() {
        return typeCarburant;
    }

    public void setTypeCarburant(String typeCarburant) {
        this.typeCarburant = typeCarburant;
    }

    // Méthode utilitaire pour afficher le type de carburant en clair
    public String getTypeCarburantLibelle() {
        switch (this.typeCarburant) {
            case "D": return "Diesel";
            case "ES": return "Essence";
            case "H": return "Hybride";
            case "EL": return "Electrique";
            default: return this.typeCarburant;
        }
    }
    
     private Vehicule mapResultSet(ResultSet rs) throws SQLException {
        Vehicule v = new Vehicule();
        v.setId(rs.getInt("id"));
        v.setReference(rs.getString("reference"));
        v.setNombrePlace(rs.getInt("nombre_place"));
        v.setTypeCarburant(rs.getString("type_carburant"));
        return v;
    }
    public int getNbPlaceReservees( )throws SQLException{
        int placeRestante = this.nombrePlace;
        String sql = "SELECT SUM(r.nombre_passager) AS total_passagers " +
                     "FROM reservation r " +
                     "WHERE r.id_vehicule = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1,getId());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                placeRestante = rs.getInt("total_passagers");
            }
        }
        return placeRestante;
    }
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
    
}
