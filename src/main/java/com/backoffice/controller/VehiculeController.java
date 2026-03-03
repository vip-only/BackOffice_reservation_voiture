package com.backoffice.controller;

import com.itu.demo.annotations.Controller;
import com.itu.demo.annotations.GetMapping;
import com.itu.demo.annotations.PostMapping;
import com.itu.demo.annotations.RequestParam;
import com.itu.demo.tools.ModelView;

import com.backoffice.dao.VehiculeDAO;
import com.backoffice.model.Vehicule;

import java.util.List;

@Controller
public class VehiculeController {

    // Afficher la liste des véhicules avec filtre et recherche (GET)
    @GetMapping("/vehicule")
    public ModelView listeVehicules(@RequestParam("typeCarburant") String typeCarburant,
                                    @RequestParam("keyword") String keyword) {
        ModelView mv = new ModelView("vehicule.jsp");
        try {
            VehiculeDAO vehiculeDAO = new VehiculeDAO();
            List<Vehicule> vehicules;

            // Appliquer les filtres si présents
            if ((typeCarburant != null && !typeCarburant.isEmpty()) || (keyword != null && !keyword.isEmpty())) {
                vehicules = vehiculeDAO.findWithFilters(typeCarburant, keyword);
            } else {
                vehicules = vehiculeDAO.findAll();
            }

            mv.addData("vehicules", vehicules);
            mv.addData("typeCarburantFiltre", typeCarburant);
            mv.addData("keywordFiltre", keyword);
        } catch (Exception e) {
            mv.addData("error", "Erreur lors du chargement des vehicules : " + e.getMessage());
            e.printStackTrace();
        }
        return mv;
    }

    // Afficher le formulaire d'ajout (GET)
    @GetMapping("/vehicule/add")
    public ModelView formulaireAjout() {
        ModelView mv = new ModelView("vehicule-form.jsp");
        mv.addData("action", "insert");
        return mv;
    }

    // Afficher le formulaire de modification (GET)
    @GetMapping("/vehicule/edit")
    public ModelView formulaireModification(@RequestParam("id") String idStr) {
        ModelView mv = new ModelView("vehicule-form.jsp");
        try {
            int id = Integer.parseInt(idStr);
            VehiculeDAO vehiculeDAO = new VehiculeDAO();
            Vehicule vehicule = vehiculeDAO.findById(id);

            if (vehicule != null) {
                mv.addData("vehicule", vehicule);
                mv.addData("action", "update");
            } else {
                mv.addData("error", "Vehicule non trouve");
            }
        } catch (Exception e) {
            mv.addData("error", "Erreur : " + e.getMessage());
            e.printStackTrace();
        }
        return mv;
    }

    // Insérer un véhicule (POST)
    @PostMapping("/vehicule/insert")
    public ModelView insertVehicule(@RequestParam("reference") String reference,
                                    @RequestParam("nombre_place") String nombrePlaceStr,
                                    @RequestParam("type_carburant") String typeCarburant) {
        ModelView mv = new ModelView("vehicule.jsp");
        try {
            int nombrePlace = Integer.parseInt(nombrePlaceStr);
            Vehicule vehicule = new Vehicule(reference, nombrePlace, typeCarburant);

            VehiculeDAO vehiculeDAO = new VehiculeDAO();
            vehiculeDAO.insert(vehicule);

            // Recharger la liste
            List<Vehicule> vehicules = vehiculeDAO.findAll();
            mv.addData("vehicules", vehicules);
            mv.addData("success", "Vehicule insere avec succes !");
        } catch (Exception e) {
            mv.addData("error", "Erreur lors de l'insertion : " + e.getMessage());
            e.printStackTrace();
        }
        return mv;
    }

    // Mettre à jour un véhicule (POST)
    @PostMapping("/vehicule/update")
    public ModelView updateVehicule(@RequestParam("id") String idStr,
                                    @RequestParam("reference") String reference,
                                    @RequestParam("nombre_place") String nombrePlaceStr,
                                    @RequestParam("type_carburant") String typeCarburant) {
        ModelView mv = new ModelView("vehicule.jsp");
        try {
            int id = Integer.parseInt(idStr);
            int nombrePlace = Integer.parseInt(nombrePlaceStr);
            Vehicule vehicule = new Vehicule(id, reference, nombrePlace, typeCarburant);

            VehiculeDAO vehiculeDAO = new VehiculeDAO();
            vehiculeDAO.update(vehicule);

            // Recharger la liste
            List<Vehicule> vehicules = vehiculeDAO.findAll();
            mv.addData("vehicules", vehicules);
            mv.addData("success", "Vehicule mis a jour avec succes !");
        } catch (Exception e) {
            mv.addData("error", "Erreur lors de la mise a jour : " + e.getMessage());
            e.printStackTrace();
        }
        return mv;
    }

    // Supprimer un véhicule (POST)
    @PostMapping("/vehicule/delete")
    public ModelView deleteVehicule(@RequestParam("id") String idStr) {
        ModelView mv = new ModelView("vehicule.jsp");
        try {
            int id = Integer.parseInt(idStr);
            VehiculeDAO vehiculeDAO = new VehiculeDAO();
            vehiculeDAO.delete(id);

            // Recharger la liste
            List<Vehicule> vehicules = vehiculeDAO.findAll();
            mv.addData("vehicules", vehicules);
            mv.addData("success", "Vehicule supprime avec succes !");
        } catch (Exception e) {
            mv.addData("error", "Erreur lors de la suppression : " + e.getMessage());
            e.printStackTrace();
        }
        return mv;
    }
}
