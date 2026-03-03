package com.backoffice.service;

import com.backoffice.dao.ParametreDAO;
import com.backoffice.dao.ReservationDAO;
import com.backoffice.dao.VehiculeDAO;
import com.backoffice.model.PlanificationReservation;
import com.backoffice.model.Reservation;
import com.backoffice.model.Vehicule;

import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class PlanificationService {
    
    private ReservationDAO reservationDAO = new ReservationDAO();
    private VehiculeDAO vehiculeDAO = new VehiculeDAO();
    private ParametreDAO parametreDAO = new ParametreDAO();
    
    // Calculer l'heure de départ (arrivée - temps d'attente)
    private Timestamp calculerHeureDepart(Timestamp heureArrivee, int tempsAttenteMinutes) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(heureArrivee);
        cal.add(Calendar.MINUTE, -tempsAttenteMinutes);
        return new Timestamp(cal.getTimeInMillis());
    }
    
    // Calculer l'heure de retour (arrivée + temps d'attente)
    private Timestamp calculerHeureRetour(Timestamp heureArrivee, int tempsAttenteMinutes) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(heureArrivee);
        cal.add(Calendar.MINUTE, tempsAttenteMinutes);
        return new Timestamp(cal.getTimeInMillis());
    }
    
    // Trouver le meilleur véhicule selon les règles
    private Vehicule trouverMeilleurVehicule(int nombrePassagers, List<Vehicule> vehiculesDisponibles) {
        Vehicule meilleurVehicule = null;
        int meilleureCapacite = Integer.MAX_VALUE;
        
        for (Vehicule v : vehiculesDisponibles) {
            // Règle 1: capacité >= nombre de passagers
            if (v.getNombrePlace() >= nombrePassagers) {
                // Règle 2: capacité la plus proche
                if (v.getNombrePlace() < meilleureCapacite) {
                    meilleurVehicule = v;
                    meilleureCapacite = v.getNombrePlace();
                }
                // Règle 3: préférer Diesel à capacité égale
                else if (v.getNombrePlace() == meilleureCapacite && "D".equals(v.getTypeCarburant())) {
                    if (meilleurVehicule == null || !"D".equals(meilleurVehicule.getTypeCarburant())) {
                        meilleurVehicule = v;
                    }
                }
            }
        }
        
        return meilleurVehicule;
    }
    
    // Assigner automatiquement les véhicules aux réservations
    public void assignerVehiculesAutomatiquement(Date date) throws SQLException {
        int tempsAttente = parametreDAO.getTempsAttente();
        
        // Récupérer les réservations sans véhicule pour cette date
        List<Reservation> reservationsSansVehicule = reservationDAO.findWithoutVehiculeByDate(date);
        
        // Récupérer tous les véhicules disponibles
        List<Vehicule> vehiculesDisponibles = vehiculeDAO.findAll();
        
        for (Reservation reservation : reservationsSansVehicule) {
            Vehicule vehicule = trouverMeilleurVehicule(reservation.getNombrePassager(), vehiculesDisponibles);
            
            if (vehicule != null) {
                // Assigner le véhicule
                reservationDAO.assignVehicule(reservation.getId(), vehicule.getId());
                
                // Retirer le véhicule de la liste des disponibles
                vehiculesDisponibles.remove(vehicule);
            }
        }
    }
    
    // Récupérer les planifications pour une date donnée
    public List<PlanificationReservation> getPlanificationsByDate(Date date) throws SQLException {
        List<PlanificationReservation> planifications = new ArrayList<>();
        int tempsAttente = parametreDAO.getTempsAttente();
        
        // Récupérer toutes les réservations avec véhicule pour cette date
        List<Reservation> reservations = reservationDAO.findByDate(date);
        
        for (Reservation reservation : reservations) {
            if (reservation.hasVehicule()) {
                Timestamp heureDepart = calculerHeureDepart(reservation.getDateHeureArrivee(), tempsAttente);
                Timestamp heureRetour = calculerHeureRetour(reservation.getDateHeureArrivee(), tempsAttente);
                
                planifications.add(new PlanificationReservation(reservation, heureDepart, heureRetour));
            }
        }
        
        return planifications;
    }
    
    // Récupérer les réservations sans véhicule pour une date donnée
    public List<Reservation> getReservationsSansVehicule(Date date) throws SQLException {
        return reservationDAO.findWithoutVehiculeByDate(date);
    }
}