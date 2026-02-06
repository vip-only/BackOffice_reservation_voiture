package com.backoffice.model;

public class Hotel {
    private int idHotel;
    private String nom;

    public Hotel() {}

    public Hotel(int idHotel, String nom) {
        this.idHotel = idHotel;
        this.nom = nom;
    }

    public int getIdHotel() {
        return idHotel;
    }

    public void setIdHotel(int idHotel) {
        this.idHotel = idHotel;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }
}
