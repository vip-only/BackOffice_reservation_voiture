package com.backoffice.model;

import java.sql.Timestamp;

/**
 * Représente une planification de réservation avec les heures de trajet.
 * 
 * Calcul (SANS temps d'attente):
 * - Départ aéroport = date_heure_arrivee (heure saisie = arrivée du vol)
 * - Retour aéroport = date_heure_arrivee + (2 × durée trajet)
 * - Durée trajet = distance / vitesse_moyenne
 */
public class PlanificationReservation {
    private Reservation reservation;
    private Timestamp heureDepart;       // Heure de départ de l'aéroport (= arrivée vol)
    private Timestamp heureRetour;       // Heure de retour à l'aéroport
    private double distanceKm;           // Distance aéroport -> hôtel
    private int dureeAllerMinutes;       // Durée aller en minutes
    private int dureeTotaleMinutes;      // Durée aller-retour en minutes
    
    public PlanificationReservation(Reservation reservation, Timestamp heureDepart, 
                                    Timestamp heureRetour, double distanceKm, int dureeAllerMinutes) {
        this.reservation = reservation;
        this.heureDepart = heureDepart;
        this.heureRetour = heureRetour;
        this.distanceKm = distanceKm;
        this.dureeAllerMinutes = dureeAllerMinutes;
        this.dureeTotaleMinutes = dureeAllerMinutes * 2;
    }
    
    public Reservation getReservation() {
        return reservation;
    }
    
    public void setReservation(Reservation reservation) {
        this.reservation = reservation;
    }
    
    public Timestamp getHeureDepart() {
        return heureDepart;
    }
    
    public void setHeureDepart(Timestamp heureDepart) {
        this.heureDepart = heureDepart;
    }
    
    public Timestamp getHeureRetour() {
        return heureRetour;
    }
    
    public void setHeureRetour(Timestamp heureRetour) {
        this.heureRetour = heureRetour;
    }
    
    public double getDistanceKm() {
        return distanceKm;
    }
    
    public void setDistanceKm(double distanceKm) {
        this.distanceKm = distanceKm;
    }
    
    public int getDureeAllerMinutes() {
        return dureeAllerMinutes;
    }
    
    public void setDureeAllerMinutes(int dureeAllerMinutes) {
        this.dureeAllerMinutes = dureeAllerMinutes;
    }
    
    public int getDureeTotaleMinutes() {
        return dureeTotaleMinutes;
    }
    
    public void setDureeTotaleMinutes(int dureeTotaleMinutes) {
        this.dureeTotaleMinutes = dureeTotaleMinutes;
    }
}