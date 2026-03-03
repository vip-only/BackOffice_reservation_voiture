package com.backoffice.dao;

import com.backoffice.database.DBConnection;

import java.sql.*;

public class ParametreDAO {
    
    // Récupérer une valeur de paramètre par sa clé
    public Double getValeurByCle(String cle) throws SQLException {
        String sql = "SELECT valeur FROM parametre WHERE cle = ?";
        
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cle);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("valeur");
                }
            }
        }
        return null;
    }
    
    // Récupérer le temps d'attente (TA) en minutes
    public int getTempsAttente() throws SQLException {
        Double valeur = getValeurByCle("TA");
        return valeur != null ? valeur.intValue() : 30; // 30 par défaut
    }
    
    // Récupérer la vitesse moyenne en km/h
    public double getVitesseMoyenne() throws SQLException {
        Double valeur = getValeurByCle("VITESSE_MOYENNE");
        return valeur != null ? valeur : 30.0; // 30 km/h par défaut
    }
}