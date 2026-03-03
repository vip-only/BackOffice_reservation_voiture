package com.backoffice.model;

import java.sql.Timestamp;

public class Reservation {
    private int id;
    private String client;
    private int nombrePassager;
    private Timestamp dateHeureArrivee;
    private int idHotel;
    private Integer idVehicule; // Nullable car pas toujours assigné

    // Pour l'affichage
    private String nomHotel;
    private String referenceVehicule;

    public Reservation() {}

    public Reservation(String client, int nombrePassager, Timestamp dateHeureArrivee, int idHotel) {
        this.client = client;
        this.nombrePassager = nombrePassager;
        this.dateHeureArrivee = dateHeureArrivee;
        this.idHotel = idHotel;
    }

    public Reservation(String client, int nombrePassager, Timestamp dateHeureArrivee, int idHotel, Integer idVehicule) {
        this.client = client;
        this.nombrePassager = nombrePassager;
        this.dateHeureArrivee = dateHeureArrivee;
        this.idHotel = idHotel;
        this.idVehicule = idVehicule;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getClient() {
        return client;
    }

    public void setClient(String client) {
        this.client = client;
    }

    public int getNombrePassager() {
        return nombrePassager;
    }

    public void setNombrePassager(int nombrePassager) {
        this.nombrePassager = nombrePassager;
    }

    public Timestamp getDateHeureArrivee() {
        return dateHeureArrivee;
    }

    public void setDateHeureArrivee(Timestamp dateHeureArrivee) {
        this.dateHeureArrivee = dateHeureArrivee;
    }

    public int getIdHotel() {
        return idHotel;
    }

    public void setIdHotel(int idHotel) {
        this.idHotel = idHotel;
    }

    public Integer getIdVehicule() {
        return idVehicule;
    }

    public void setIdVehicule(Integer idVehicule) {
        this.idVehicule = idVehicule;
    }

    public String getNomHotel() {
        return nomHotel;
    }

    public void setNomHotel(String nomHotel) {
        this.nomHotel = nomHotel;
    }

    public String getReferenceVehicule() {
        return referenceVehicule;
    }

    public void setReferenceVehicule(String referenceVehicule) {
        this.referenceVehicule = referenceVehicule;
    }

    // Méthode utilitaire pour vérifier si un véhicule est assigné
    public boolean hasVehicule() {
        return idVehicule != null;
    }
}
