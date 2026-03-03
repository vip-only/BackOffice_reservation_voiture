package com.backoffice.util;

import com.backoffice.dao.TokenDAO;
import com.backoffice.model.Token;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;

public class TokenGenerator {

    // Générer et INSÉRER un token dans la base
    public static String generateToken(int hoursValid) throws SQLException {
        // Calculer la date d'expiration
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR, hoursValid);
        Timestamp expiration = new Timestamp(calendar.getTimeInMillis());
        
        // Créer le token (UUID généré automatiquement)
        Token token = new Token(expiration);
        
        // INSERTION dans la base de données
        TokenDAO tokenDAO = new TokenDAO();
        tokenDAO.insert(token);
        
        return token.getToken();
    }

    // Version avec expiration par défaut (24h)
    public static String generateToken() throws SQLException {
        return generateToken(24);
    }
}