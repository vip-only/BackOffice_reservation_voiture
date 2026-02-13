package com.backoffice.model;

import java.sql.Timestamp;
import java.util.UUID;

public class Token {
    private int id;
    private String token;
    private Timestamp dateHeureExpiration;

    // Constructeur pour la création d'un nouveau token
    public Token(Timestamp dateHeureExpiration) {
        this.token = UUID.randomUUID().toString();
        this.dateHeureExpiration = dateHeureExpiration;
    }

    // Constructeur pour la récupération depuis la base
    public Token(int id, String token, Timestamp dateHeureExpiration) {
        this.id = id;
        this.token = token;
        this.dateHeureExpiration = dateHeureExpiration;
    }

    // Getters et Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Timestamp getDateHeureExpiration() {
        return dateHeureExpiration;
    }

    public void setDateHeureExpiration(Timestamp dateHeureExpiration) {
        this.dateHeureExpiration = dateHeureExpiration;
    }

    // Méthode pour vérifier si le token est expiré
    public boolean isExpired() {
        return new Timestamp(System.currentTimeMillis()).after(this.dateHeureExpiration);
    }
}