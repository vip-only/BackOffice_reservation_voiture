package com.backoffice.model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Représente un groupe de réservations assignées à un même véhicule.
 * Contient l'itinéraire (liste d'étapes) et les horaires calculés.
 * 
 * Calcul horaires de retour :
 *   - Départ TNR = date_heure_arrivee (arrivée du vol)
 *   - Itinéraire : TNR -> Hotel1 -> Hotel2 -> ... -> TNR
 *   - Durée chaque étape = distance_etape / vitesse_moyenne (en minutes)
 *   - Heure retour = départ + somme de toutes les durées d'étapes
 */
public class GroupeVehicule {
    
    private Vehicule vehicule;
    private List<Reservation> reservations;
    private List<EtapeItineraire> itineraire;
    private Timestamp heureDepart;          // Heure départ de TNR
    private Timestamp heureRetour;          // Heure retour à TNR
    private double distanceTotaleKm;        // Distance totale du circuit
    private int dureeTotaleMinutes;         // Durée totale en minutes
    private int totalPassagers;             // Total passagers embarqués
    
    public GroupeVehicule() {
        this.reservations = new ArrayList<>();
        this.itineraire = new ArrayList<>();
    }
    
    public GroupeVehicule(Vehicule vehicule) {
        this.vehicule = vehicule;
        this.reservations = new ArrayList<>();
        this.itineraire = new ArrayList<>();
    }
    
    // --- Getters / Setters ---
    
    public Vehicule getVehicule() { return vehicule; }
    public void setVehicule(Vehicule vehicule) { this.vehicule = vehicule; }
    
    public List<Reservation> getReservations() { return reservations; }
    public void setReservations(List<Reservation> reservations) { this.reservations = reservations; }
    
    public List<EtapeItineraire> getItineraire() { return itineraire; }
    public void setItineraire(List<EtapeItineraire> itineraire) { this.itineraire = itineraire; }
    
    public Timestamp getHeureDepart() { return heureDepart; }
    public void setHeureDepart(Timestamp heureDepart) { this.heureDepart = heureDepart; }
    
    public Timestamp getHeureRetour() { return heureRetour; }
    public void setHeureRetour(Timestamp heureRetour) { this.heureRetour = heureRetour; }
    
    public double getDistanceTotaleKm() { return distanceTotaleKm; }
    public void setDistanceTotaleKm(double distanceTotaleKm) { this.distanceTotaleKm = distanceTotaleKm; }
    
    public int getDureeTotaleMinutes() { return dureeTotaleMinutes; }
    public void setDureeTotaleMinutes(int dureeTotaleMinutes) { this.dureeTotaleMinutes = dureeTotaleMinutes; }
    
    public int getTotalPassagers() { return totalPassagers; }
    public void setTotalPassagers(int totalPassagers) { this.totalPassagers = totalPassagers; }
    
    /**
     * Représente une étape de l'itinéraire (un trajet entre deux lieux).
     * Ex: TNR -> Colbert, Colbert -> Novotel, Novotel -> TNR
     */
    public static class EtapeItineraire {
        private String lieuDepart;          // Nom du lieu de départ
        private String lieuArrivee;         // Nom du lieu d'arrivée
        private double distanceKm;          // Distance de cette étape
        private int dureeMinutes;           // Durée de cette étape en minutes
        private Timestamp heureArrivee;     // Heure d'arrivée à ce point
        private List<String> passagersDeposes; // Clients déposés à cette étape (vide si retour TNR)
        
        public EtapeItineraire() {
            this.passagersDeposes = new ArrayList<>();
        }
        
        public EtapeItineraire(String lieuDepart, String lieuArrivee, double distanceKm, 
                               int dureeMinutes, Timestamp heureArrivee) {
            this.lieuDepart = lieuDepart;
            this.lieuArrivee = lieuArrivee;
            this.distanceKm = distanceKm;
            this.dureeMinutes = dureeMinutes;
            this.heureArrivee = heureArrivee;
            this.passagersDeposes = new ArrayList<>();
        }
        
        public String getLieuDepart() { return lieuDepart; }
        public void setLieuDepart(String lieuDepart) { this.lieuDepart = lieuDepart; }
        
        public String getLieuArrivee() { return lieuArrivee; }
        public void setLieuArrivee(String lieuArrivee) { this.lieuArrivee = lieuArrivee; }
        
        public double getDistanceKm() { return distanceKm; }
        public void setDistanceKm(double distanceKm) { this.distanceKm = distanceKm; }
        
        public int getDureeMinutes() { return dureeMinutes; }
        public void setDureeMinutes(int dureeMinutes) { this.dureeMinutes = dureeMinutes; }
        
        public Timestamp getHeureArrivee() { return heureArrivee; }
        public void setHeureArrivee(Timestamp heureArrivee) { this.heureArrivee = heureArrivee; }
        
        public List<String> getPassagersDeposes() { return passagersDeposes; }
        public void setPassagersDeposes(List<String> passagersDeposes) { this.passagersDeposes = passagersDeposes; }
    }
}
