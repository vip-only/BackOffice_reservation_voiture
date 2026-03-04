package com.backoffice.controller;

import com.itu.demo.annotations.Controller;
import com.itu.demo.annotations.GetMapping;
import com.itu.demo.annotations.PostMapping;
import com.itu.demo.annotations.RequestParam;
import com.itu.demo.tools.ModelView;

import com.backoffice.model.GroupeVehicule;
import com.backoffice.model.PlanificationReservation;
import com.backoffice.model.Reservation;
import com.backoffice.service.PlanificationService;

import java.sql.Date;
import java.util.List;

@Controller
public class PlanificationController {
    
    private PlanificationService planificationService = new PlanificationService();
    
    // Afficher la page de planification (GET)
    @GetMapping("/planification")
    public ModelView afficherPlanification(@RequestParam("date") String dateStr) {
        ModelView mv = new ModelView("planification.jsp");
        
        try {
            Date date;
            if (dateStr == null || dateStr.isEmpty()) {
                date = new Date(System.currentTimeMillis());
            } else {
                date = Date.valueOf(dateStr);
            }
            
            List<PlanificationReservation> planifications = planificationService.getPlanificationsByDate(date);
            List<Reservation> reservationsSansVehicule = planificationService.getReservationsSansVehicule(date);
            List<GroupeVehicule> groupesVehicules = planificationService.construireGroupesParVehicule(date);
            
            mv.addData("planifications", planifications);
            mv.addData("reservationsSansVehicule", reservationsSansVehicule);
            mv.addData("groupesVehicules", groupesVehicules);
            mv.addData("dateSelectionnee", date.toString());
            
        } catch (Exception e) {
            mv.addData("error", "Erreur lors du chargement de la planification : " + e.getMessage());
            e.printStackTrace();
        }
        
        return mv;
    }
    
    // Assigner automatiquement les véhicules (POST)
    @PostMapping("/planification/assigner")
    public ModelView assignerVehicules(@RequestParam("date") String dateStr) {
        ModelView mv = new ModelView("planification.jsp");
        
        try {
            Date date = Date.valueOf(dateStr);
            
            planificationService.assignerVehiculesAutomatiquement(date);
            
            List<PlanificationReservation> planifications = planificationService.getPlanificationsByDate(date);
            List<Reservation> reservationsSansVehicule = planificationService.getReservationsSansVehicule(date);
            List<GroupeVehicule> groupesVehicules = planificationService.construireGroupesParVehicule(date);
            
            mv.addData("planifications", planifications);
            mv.addData("reservationsSansVehicule", reservationsSansVehicule);
            mv.addData("groupesVehicules", groupesVehicules);
            mv.addData("dateSelectionnee", date.toString());
            mv.addData("success", "Véhicules assignés automatiquement avec succès !");
            
        } catch (Exception e) {
            mv.addData("error", "Erreur lors de l'assignation : " + e.getMessage());
            e.printStackTrace();
        }
        
        return mv;
    }
    
    // Regrouper et assigner (POST) - algorithme amélioré
    @PostMapping("/planification/regrouper-assigner")
    public ModelView regrouperEtAssigner(@RequestParam("date") String dateStr) {
        ModelView mv = new ModelView("planification.jsp");
        
        try {
            Date date = Date.valueOf(dateStr);
            
            // Exécuter le regroupement + assignation
            List<GroupeVehicule> groupesVehicules = planificationService.regrouperEtAssigner(date);
            
            // Recharger les données
            List<PlanificationReservation> planifications = planificationService.getPlanificationsByDate(date);
            List<Reservation> reservationsSansVehicule = planificationService.getReservationsSansVehicule(date);
            
            mv.addData("planifications", planifications);
            mv.addData("reservationsSansVehicule", reservationsSansVehicule);
            mv.addData("groupesVehicules", groupesVehicules);
            mv.addData("dateSelectionnee", date.toString());
            
            int nbReservations = 0;
            for (GroupeVehicule g : groupesVehicules) {
                nbReservations += g.getReservations().size();
            }
            mv.addData("success", "Regroupement effectué : " + nbReservations + " réservation(s) dans " + groupesVehicules.size() + " véhicule(s).");
            
        } catch (Exception e) {
            mv.addData("error", "Erreur lors du regroupement : " + e.getMessage());
            e.printStackTrace();
        }
        
        return mv;
    }
}