package com.backoffice.model;

import java.sql.Timestamp;

public class PlanificationReservation {
    private Reservation reservation;
    private Timestamp heureDepart;
    private Timestamp heureRetour;
    
    public PlanificationReservation(Reservation reservation, Timestamp heureDepart, Timestamp heureRetour) {
        this.reservation = reservation;
        this.heureDepart = heureDepart;
        this.heureRetour = heureRetour;
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
}