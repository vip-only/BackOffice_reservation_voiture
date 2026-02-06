package com.backoffice.controller;

import com.itu.demo.annotations.Controller;
import com.itu.demo.annotations.GetMapping;
import com.itu.demo.annotations.PostMapping;
import com.itu.demo.annotations.RequestParam;
import com.itu.demo.tools.ModelView;

import com.backoffice.dao.HotelDAO;
import com.backoffice.dao.ReservationDAO;
import com.backoffice.model.Hotel;
import com.backoffice.model.Reservation;

import java.sql.Timestamp;
import java.util.List;

@Controller
public class ReservationController {

    // Formulaire de reservation (GET)
    @GetMapping("/reservation")
    public ModelView formulaireReservation() {
        ModelView mv = new ModelView("reservation.jsp");
        try {
            HotelDAO hotelDAO = new HotelDAO();
            List<Hotel> hotels = hotelDAO.findAll();
            mv.addData("hotels", hotels);
        } catch (Exception e) {
            mv.addData("error", "Erreur lors du chargement des hotels : " + e.getMessage());
            e.printStackTrace();
        }
        return mv;
    }

    // Insertion de reservation (POST)
    @PostMapping("/reservation/insert")
    public ModelView insertReservation(@RequestParam("client") String client,
                                       @RequestParam("nombre_passager") String nombrePassagerStr,
                                       @RequestParam("date_heure_arrivee") String dateHeureStr,
                                       @RequestParam("id_hotel") String idHotelStr) {
        ModelView mv = new ModelView("reservation.jsp");
        try {
            int nombrePassager = Integer.parseInt(nombrePassagerStr);
            int idHotel = Integer.parseInt(idHotelStr);
            Timestamp dateHeureArrivee = Timestamp.valueOf(dateHeureStr.replace("T", " ") + ":00");

            Reservation reservation = new Reservation(client, nombrePassager, dateHeureArrivee, idHotel);

            ReservationDAO reservationDAO = new ReservationDAO();
            reservationDAO.insert(reservation);

            mv.addData("success", "Reservation inseree avec succes !");
        } catch (Exception e) {
            mv.addData("error", "Erreur lors de l'insertion : " + e.getMessage());
            e.printStackTrace();
        }

        // Recharger la liste des hotels pour le formulaire
        try {
            HotelDAO hotelDAO = new HotelDAO();
            List<Hotel> hotels = hotelDAO.findAll();
            mv.addData("hotels", hotels);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return mv;
    }
}
