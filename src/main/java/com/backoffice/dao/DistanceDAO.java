package com.backoffice.dao;

import com.backoffice.database.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * DAO pour accéder aux distances entre lieux (table distance).
 * from_id et to_id sont VARCHAR (ex: 'TNR' pour l'aéroport, '1','2',... pour les hôtels).
 */
public class DistanceDAO {
    
    /**
     * Distance générique entre deux lieux.
     * Retourne -1 si non trouvée.
     */
    public double getDistance(String fromId, String toId) throws SQLException {
        String sql = "SELECT kilometer FROM distance WHERE from_id = ? AND to_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fromId);
            ps.setString(2, toId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("kilometer");
                }
            }
        }
        return -1;
    }
    
    /**
     * Distance depuis TNR (aéroport) vers un hôtel.
     */
    public double getDistanceFromTNR(int idHotel) throws SQLException {
        return getDistance("TNR", String.valueOf(idHotel));
    }
    
    /**
     * Distance entre deux hôtels.
     * Cherche d'abord from→to, puis to→from (symétrique).
     * Si aucune distance trouvée, estime via TNR : |dist(TNR,h1) - dist(TNR,h2)|.
     */
    public double getDistanceEntreHotels(int idHotel1, int idHotel2) throws SQLException {
        if (idHotel1 == idHotel2) return 0;
        
        // Essayer from→to
        double dist = getDistance(String.valueOf(idHotel1), String.valueOf(idHotel2));
        if (dist >= 0) return dist;
        
        // Essayer to→from (symétrique)
        dist = getDistance(String.valueOf(idHotel2), String.valueOf(idHotel1));
        if (dist >= 0) return dist;
        
        // Fallback : estimation via distances TNR
        double d1 = getDistanceFromTNR(idHotel1);
        double d2 = getDistanceFromTNR(idHotel2);
        if (d1 >= 0 && d2 >= 0) {
            return Math.abs(d1 - d2);
        }
        
        return -1; // Aucune distance trouvée
    }
    
    /**
     * Récupère toutes les distances depuis TNR.
     * Retourne Map<idHotel, distanceKm>.
     */
    public Map<Integer, Double> getAllDistancesFromTNR() throws SQLException {
        Map<Integer, Double> distances = new HashMap<>();
        String sql = "SELECT to_id, kilometer FROM distance WHERE from_id = 'TNR'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    int hotelId = Integer.parseInt(rs.getString("to_id"));
                    distances.put(hotelId, rs.getDouble("kilometer"));
                } catch (NumberFormatException e) {
                    // Ignorer les to_id non numériques
                }
            }
        }
        return distances;
    }
}
