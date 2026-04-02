package com.backoffice.controller;

import com.backoffice.dao.ReservationDAO;
import com.backoffice.model.GroupeVehicule;
import com.backoffice.model.PlanificationReservation;
import com.backoffice.model.Reservation;
import com.backoffice.service.PlanificationService;
import com.backoffice.service.Sprint8Service;
import com.itu.demo.annotations.Controller;
import com.itu.demo.annotations.GetMapping;
import com.itu.demo.annotations.PostMapping;
import com.itu.demo.annotations.RequestParam;
import com.itu.demo.tools.ModelView;

import java.sql.Date;
import java.util.List;

@Controller
public class Sprint8Controller {

    private final Sprint8Service sprint8Service = new Sprint8Service();
    private final PlanificationService planificationService = new PlanificationService();
    private final ReservationDAO reservationDAO = new ReservationDAO();

    @GetMapping("/sprint8")
    public ModelView pageSprint8(@RequestParam("date") String dateStr) {
        ModelView mv = new ModelView("sprint8.jsp");

        try {
            Date date;
            if (dateStr == null || dateStr.isEmpty()) {
                Date derniereDate = reservationDAO.getDerniereDateReservation();
                date = (derniereDate != null) ? derniereDate : new Date(System.currentTimeMillis());
            } else {
                date = Date.valueOf(dateStr);
            }

            chargerDonneesPage(mv, date);
        } catch (Throwable e) {
            Throwable cause = (e.getCause() != null) ? e.getCause() : e;
            mv.addData("error", "Erreur lors du chargement Sprint 8 : " + cause.getClass().getSimpleName() + " - " + cause.getMessage());
            cause.printStackTrace();
        }

        return mv;
    }

    @PostMapping("/sprint8/executer")
    public ModelView executerSprint8(@RequestParam("date") String dateStr) {
        ModelView mv = new ModelView("sprint8.jsp");

        try {
            Date date;
            if (dateStr == null || dateStr.isEmpty()) {
                Date derniereDate = reservationDAO.getDerniereDateReservation();
                date = (derniereDate != null) ? derniereDate : new Date(System.currentTimeMillis());
            } else {
                date = Date.valueOf(dateStr);
            }

            Sprint8Service.ExecutionResult resultat = sprint8Service.executer(date);

            reservationDAO.ensureReservationVehiculeTable();
            reservationDAO.synchroniserReservationVehiculeDepuisReservation(date);

            chargerDonneesPage(mv, date);
            mv.addData("resultatSprint8", resultat);
            mv.addData("success", "Sprint 8 execute avec succes.");
        } catch (Throwable e) {
            Throwable cause = (e.getCause() != null) ? e.getCause() : e;
            mv.addData("error", "Erreur lors de l'execution Sprint 8 : " + cause.getClass().getSimpleName() + " - " + cause.getMessage());
            cause.printStackTrace();
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
