package com.backoffice.service;

import com.backoffice.model.Vehicule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service d'affectation de véhicules selon les règles métier.
 * 
 * REGLES D'AFFECTATION:
 * 1. La voiture doit avoir une capacité >= nombre de personnes de la réservation
 * 2. Choisir la voiture dont la capacité est la plus proche (la plus petite capacité satisfaisante)
 * 3. Si plusieurs voitures respectent la règle 2, préférer une voiture Diesel si disponible
 */
public class VehiculeAffectationService {

    // =========================================================================
    // REGLE 1: Capacité suffisante
    // La voiture doit avoir une capacité >= nombre de personnes de la réservation
    // =========================================================================
    
    /**
     * Filtre les véhicules ayant une capacité suffisante pour le nombre de passagers.
     * 
     * @param vehicules Liste des véhicules à filtrer
     * @param nbPassagers Nombre de passagers de la réservation
     * @return Liste des véhicules avec capacité >= nbPassagers
     */
    public List<Vehicule> appliquerRegle1_CapaciteSuffisante(List<Vehicule> vehicules, int nbPassagers) {
        return vehicules.stream()
                .filter(v -> v.getNombrePlace() >= nbPassagers)
                .collect(Collectors.toList());
    }

    /**
     * Vérifie si un véhicule a une capacité suffisante.
     */
    public boolean respecteRegle1(Vehicule vehicule, int nbPassagers) {
        return vehicule.getNombrePlace() >= nbPassagers;
    }


    // =========================================================================
    // REGLE 2: Plus petite capacité satisfaisante
    // Choisir la voiture dont la capacité est la plus proche du nombre de passagers
    // =========================================================================
    
    /**
     * Trouve la capacité minimale parmi les véhicules disponibles.
     * 
     * @param vehicules Liste des véhicules (déjà filtrés par règle 1)
     * @return La capacité minimale, ou -1 si liste vide
     */
    public int trouverCapaciteMinimale(List<Vehicule> vehicules) {
        return vehicules.stream()
                .mapToInt(Vehicule::getNombrePlace)
                .min()
                .orElse(-1);
    }

    /**
     * Filtre les véhicules ayant la plus petite capacité satisfaisante.
     * 
     * @param vehicules Liste des véhicules (déjà filtrés par règle 1)
     * @return Liste des véhicules avec la capacité minimale
     */
    public List<Vehicule> appliquerRegle2_PlusPetiteCapacite(List<Vehicule> vehicules) {
        if (vehicules.isEmpty()) {
            return new ArrayList<>();
        }

        int capaciteMinimale = trouverCapaciteMinimale(vehicules);
        
        return vehicules.stream()
                .filter(v -> v.getNombrePlace() == capaciteMinimale)
                .collect(Collectors.toList());
    }


    // =========================================================================
    // REGLE 3: Préférence Diesel (si égalité de capacité)
    // Si plusieurs voitures respectent la règle 2, préférer une voiture Diesel
    // =========================================================================
    
    private static final String TYPE_CARBURANT_DIESEL = "D";

    /**
     * Vérifie si un véhicule est Diesel.
     */
    public boolean estDiesel(Vehicule vehicule) {
        return TYPE_CARBURANT_DIESEL.equals(vehicule.getTypeCarburant());
    }

    /**
     * Parmi les véhicules de même capacité, choisit un Diesel si disponible.
     * 
     * @param vehicules Liste des véhicules (déjà filtrés par règles 1 et 2)
     * @return Le véhicule sélectionné (Diesel en priorité), ou null si liste vide
     */
    public Vehicule appliquerRegle3_PreferenceDiesel(List<Vehicule> vehicules) {
        if (vehicules.isEmpty()) {
            return null;
        }

        // Chercher un Diesel d'abord
        for (Vehicule v : vehicules) {
            if (estDiesel(v)) {
                return v;
            }
        }

        // Sinon, prendre le premier véhicule
        return vehicules.get(0);
    }


    // =========================================================================
    // APPLICATION DE TOUTES LES REGLES
    // =========================================================================

    /**
     * Applique toutes les règles d'affectation et retourne le meilleur véhicule.
     * 
     * @param vehiculesDisponibles Liste des véhicules disponibles (non occupés)
     * @param nbPassagers Nombre de passagers de la réservation
     * @return Le véhicule optimal selon les règles, ou null si aucun disponible
     */
    public Vehicule choisirMeilleurVehicule(List<Vehicule> vehiculesDisponibles, int nbPassagers) {
        if (vehiculesDisponibles == null || vehiculesDisponibles.isEmpty()) {
            return null;
        }

        // Règle 1: Filtrer par capacité suffisante
        List<Vehicule> vehiculesCapaciteSuffisante = appliquerRegle1_CapaciteSuffisante(vehiculesDisponibles, nbPassagers);
        if (vehiculesCapaciteSuffisante.isEmpty()) {
            return null;
        }

        // Règle 2: Garder ceux avec la plus petite capacité
        List<Vehicule> vehiculesCapaciteMinimale = appliquerRegle2_PlusPetiteCapacite(vehiculesCapaciteSuffisante);
        if (vehiculesCapaciteMinimale.isEmpty()) {
            return null;
        }

        // Règle 3: Préférer Diesel si plusieurs candidats
        return appliquerRegle3_PreferenceDiesel(vehiculesCapaciteMinimale);
    }


    // =========================================================================
    // COMPARATEUR POUR TRI (utilisable dans les requêtes SQL ou Java)
    // =========================================================================

    /**
     * Retourne un comparateur qui trie selon les règles d'affectation:
     * 1. Par capacité croissante (règle 2)
     * 2. Diesel en premier si égalité (règle 3)
     */
    public Comparator<Vehicule> getComparateurAffectation() {
        return Comparator
                .comparingInt(Vehicule::getNombrePlace)  // Règle 2: plus petite capacité
                .thenComparing(this::getPrioriteDiesel); // Règle 3: Diesel si égalité
    }

    /**
     * Retourne la priorité pour le tri Diesel.
     * 0 = Diesel (priorité haute), 1 = Autre (priorité basse)
     */
    private int getPrioriteDiesel(Vehicule v) {
        return estDiesel(v) ? 0 : 1;
    }
}
