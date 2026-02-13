package com.backoffice.dao;

import com.backoffice.database.DBConnection;
import com.backoffice.model.Token;

import java.sql.*;

public class TokenDAO {

    // Insérer un nouveau token dans la base
    public void insert(Token token) throws SQLException {
        String sql = "INSERT INTO token (token, date_heure_expiration) VALUES (?, ?)";
        
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, token.getToken());
            stmt.setTimestamp(2, token.getDateHeureExpiration());
            
            int rowsAffected = stmt.executeUpdate();
            System.out.println("[TokenDAO] Token inséré dans la base : " + rowsAffected + " ligne(s)");
            
            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                token.setId(rs.getInt(1));
                System.out.println("[TokenDAO] ID généré : " + token.getId());
            }
        }
    }

    // Rechercher un token par sa valeur
    public Token findByToken(String tokenValue) throws SQLException {
        String sql = "SELECT id, token, date_heure_expiration FROM token WHERE token = ?";
        
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, tokenValue);
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new Token(
                    rs.getInt("id"),
                    rs.getString("token"),
                    rs.getTimestamp("date_heure_expiration")
                );
            }
            return null;
        }
    }

    // Vérifier si un token est valide
    public boolean isTokenValid(String tokenValue) throws SQLException {
        Token token = findByToken(tokenValue);
        
        if (token == null) {
            return false;
        }
        
        return !token.isExpired();
    }

    // Supprimer les tokens expirés
    public int deleteExpiredTokens() throws SQLException {
        String sql = "DELETE FROM token WHERE date_heure_expiration < ?";
        
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            return stmt.executeUpdate();
        }
    }
}