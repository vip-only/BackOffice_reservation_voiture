package com.backoffice.controller;

import com.itu.demo.annotations.Controller;
import com.itu.demo.annotations.GetMapping;
import com.itu.demo.annotations.PostMapping;
import com.itu.demo.annotations.RequestParam;
import com.itu.demo.tools.ModelView;

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
            // Si aucune date n'est fournie, utiliser la date du jour
            Date date;
            if (dateStr == null || dateStr.isEmpty()) {
                date = new Date(System.currentTimeMillis());
            } else {
                date = Date.valueOf(dateStr);
            }
            
            // Récupérer les planifications avec véhicule
            List<PlanificationReservation> planifications = planificationService.getPlanificationsByDate(date);
            
            // Récupérer les réservations sans véhicule
            List<Reservation> reservationsSansVehicule = planificationService.getReservationsSansVehicule(date);
            
            mv.addData("planifications", planifications);
            mv.addData("reservationsSansVehicule", reservationsSansVehicule);
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
            
            // Assigner automatiquement les véhicules
            planificationService.assignerVehiculesAutomatiquement(date);
            
            // Recharger les données
            List<PlanificationReservation> planifications = planificationService.getPlanificationsByDate(date);
            List<Reservation> reservationsSansVehicule = planificationService.getReservationsSansVehicule(date);
            
            mv.addData("planifications", planifications);
            mv.addData("reservationsSansVehicule", reservationsSansVehicule);
            mv.addData("dateSelectionnee", date.toString());
            mv.addData("success", "Véhicules assignés automatiquement avec succès !");
            
        } catch (Exception e) {
            mv.addData("error", "Erreur lors de l'assignation : " + e.getMessage());
            e.printStackTrace();
        }
        
        return mv;
    }
}