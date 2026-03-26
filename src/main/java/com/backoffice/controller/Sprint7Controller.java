package com.backoffice.controller;

import com.backoffice.dao.ReservationDAO;
import com.backoffice.model.GroupeVehicule;
import com.backoffice.model.PlanificationReservation;
import com.backoffice.model.Reservation;
import com.backoffice.service.PlanificationService;
import com.backoffice.service.Sprint7Service;
import com.itu.demo.annotations.Controller;
import com.itu.demo.annotations.GetMapping;
import com.itu.demo.annotations.PostMapping;
import com.itu.demo.annotations.RequestParam;
import com.itu.demo.tools.ModelView;

import java.sql.Date;
import java.util.List;

@Controller
public class Sprint7Controller {

    private final Sprint7Service sprint7Service = new Sprint7Service();
    private final PlanificationService planificationService = new PlanificationService();
    private final ReservationDAO reservationDAO = new ReservationDAO();

    @GetMapping("/sprint7")
    public ModelView pageSprint7(@RequestParam("date") String dateStr) {
        ModelView mv = new ModelView("sprint7.jsp");

        try {
            Date date;
            if (dateStr == null || dateStr.isEmpty()) {
                Date derniereDate = reservationDAO.getDerniereDateReservation();
                date = (derniereDate != null) ? derniereDate : new Date(System.currentTimeMillis());
            } else {
                date = Date.valueOf(dateStr);
            }

            chargerDonneesPage(mv, date);
        } catch (Exception e) {
            mv.addData("error", "Erreur lors du chargement Sprint 7 : " + e.getMessage());
            e.printStackTrace();
        }

        return mv;
    }

    @PostMapping("/sprint7/executer")
    public ModelView executerSprint7(@RequestParam("date") String dateStr) {
        ModelView mv = new ModelView("sprint7.jsp");

        try {
            Date date;
            if (dateStr == null || dateStr.isEmpty()) {
                Date derniereDate = reservationDAO.getDerniereDateReservation();
                date = (derniereDate != null) ? derniereDate : new Date(System.currentTimeMillis());
            } else {
                date = Date.valueOf(dateStr);
            }

            Sprint7Service.ExecutionResult resultat = sprint7Service.executer(date);

            // Securite supplementaire: forcer la synchro du journal apres execution.
            reservationDAO.ensureReservationVehiculeTable();
            reservationDAO.synchroniserReservationVehiculeDepuisReservation(date);

            chargerDonneesPage(mv, date);
            mv.addData("resultatSprint7", resultat);
            mv.addData("success", "Sprint 7 execute avec succes.");
        } catch (Exception e) {
            mv.addData("error", "Erreur lors de l'execution Sprint 7 : " + e.getMessage());
            e.printStackTrace();
        }

        return mv;
    }

    private void chargerDonneesPage(ModelView mv, Date date) throws Exception {
        reservationDAO.ensureReservationVehiculeTable();
        reservationDAO.synchroniserReservationVehiculeDepuisReservation(date);

        List<PlanificationReservation> planifications = planificationService.getPlanificationsByDate(date);
        List<Reservation> reservationsSansVehicule = planificationService.getReservationsSansVehicule(date);
        List<GroupeVehicule> groupesVehicules = planificationService.construireGroupesParVehicule(date);

        mv.addData("dateSelectionnee", date.toString());
        mv.addData("planifications", planifications);
        mv.addData("reservationsSansVehicule", reservationsSansVehicule);
        mv.addData("groupesVehicules", groupesVehicules);
        mv.addData("reservationVehiculeRows", reservationDAO.getReservationVehiculeByDate(date));
        mv.addData("nbTracesReservationVehicule", reservationDAO.countReservationVehiculeByDate(date));
    }
}
