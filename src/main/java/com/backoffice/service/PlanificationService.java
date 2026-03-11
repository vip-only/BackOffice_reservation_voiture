package com.backoffice.service;

import com.backoffice.dao.ParametreDAO;
import com.backoffice.dao.ReservationDAO;
import com.backoffice.database.DBConnection;
import com.backoffice.model.GroupeVehicule;
import com.backoffice.model.GroupeVehicule.EtapeItineraire;
import com.backoffice.model.PlanificationReservation;
import com.backoffice.model.Reservation;
import com.backoffice.model.Vehicule;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PlanificationService {
    
    private ReservationDAO reservationDAO = new ReservationDAO();
    private ReservationService reservationService = new ReservationService();
    private VehiculeAffectationService affectationService = new VehiculeAffectationService();
    private ParametreDAO parametreDAO = new ParametreDAO();
    
    /**
     * Récupère les planifications pour une date donnée.
     * Utilise la vue v_historique_assignation pour obtenir les heures de retour calculées.
     */
    public List<PlanificationReservation> getPlanificationsByDate(Date date) throws SQLException {
        List<PlanificationReservation> planifications = new ArrayList<>();
        
        // Requête sur la vue qui calcule automatiquement les heures de retour
        String sql = 
            "SELECT " +
            "    ha.reservation_id, " +
            "    ha.client, " +
            "    ha.nombre_passager, " +
            "    ha.date_heure_arrivee, " +
            "    ha.hotel, " +
            "    r.id_hotel, " +
            "    ha.vehicule_id, " +
            "    ha.vehicule, " +
            "    ha.capacite_vehicule, " +
            "    ha.distance_km, " +
            "    ha.duree_aller_minutes, " +
            "    ha.duree_totale_minutes, " +
            "    ha.date_heure_retour, " +
            "    v.type_carburant " +
            "FROM v_historique_assignation ha " +
            "JOIN vehicule v ON ha.vehicule_id = v.id " +
            "JOIN reservation r ON ha.reservation_id = r.id " +
            "WHERE DATE(ha.date_heure_arrivee) = ? " +
            "ORDER BY ha.date_heure_arrivee";
        
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setDate(1, date);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // Créer la réservation
                    Reservation reservation = new Reservation();
                    reservation.setId(rs.getInt("reservation_id"));
                    reservation.setClient(rs.getString("client"));
                    reservation.setNombrePassager(rs.getInt("nombre_passager"));
                    reservation.setDateHeureArrivee(rs.getTimestamp("date_heure_arrivee"));
                    reservation.setNomHotel(rs.getString("hotel"));
                    reservation.setIdHotel(rs.getInt("id_hotel"));
                    reservation.setIdVehicule(rs.getInt("vehicule_id"));
                    reservation.setReferenceVehicule(rs.getString("vehicule"));
                    reservation.setCapaciteVehicule(rs.getInt("capacite_vehicule"));
                    
                    // Heure de départ = date_heure_arrivee (le véhicule part avec le client à l'arrivée du vol)
                    // Heure de retour = date_heure_arrivee + (2 × durée trajet) - calculé dans la vue
                    // PAS de temps d'attente pris en compte
                    int dureeAllerMinutes = rs.getInt("duree_aller_minutes");
                    Timestamp heureDepart = rs.getTimestamp("date_heure_arrivee"); // Départ = arrivée vol
                    Timestamp heureRetour = rs.getTimestamp("date_heure_retour");  // Retour = calculé dans la vue
                    double distanceKm = rs.getDouble("distance_km");
                    
                    // Créer la planification
                    PlanificationReservation planification = new PlanificationReservation(
                        reservation,
                        heureDepart,
                        heureRetour,
                        distanceKm,
                        dureeAllerMinutes
                    );
                    
                    // Stocker le type carburant pour l'affichage
                    reservation.setTypeCarburant(rs.getString("type_carburant"));
                    
                    planifications.add(planification);
                }
            }
        }
        
        return planifications;
    }
    
    /**
     * Récupère les réservations sans véhicule pour une date donnée
     */
    public List<Reservation> getReservationsSansVehicule(Date date) throws SQLException {
        return reservationDAO.findWithoutVehiculeByDate(date);
    }
    
    /**
     * Assigner automatiquement les véhicules aux réservations sans véhicule.
     * 
     * REGLES SPRINT 5:
     * 1. Regrouper par fenêtre de temps d'attente (TA) au lieu du timestamp exact
     * 2. Fenêtre = [heure_premier_vol, heure_premier_vol + TA]
     * 3. Heure de départ véhicule = MAX(date_heure_arrivee) du groupe
     * 4. Traiter les groupes dans l'ORDRE CHRONOLOGIQUE
     * 5. Au sein de chaque groupe, traiter par nombre de passagers décroissant
     * 6. Ordre de dépose par nearest-neighbour
     */
    public void assignerVehiculesAutomatiquement(Date date) throws SQLException {
        List<Reservation> reservationsSansVehicule = reservationDAO.findWithoutVehiculeByDate(date);
        
        if (reservationsSansVehicule == null || reservationsSansVehicule.isEmpty()) {
            return;
        }
        
        // Lire le paramètre TA depuis la base
        int taMinutes = parametreDAO.getTempsAttente();
        
        // Trier par date_heure_arrivee croissante
        reservationsSansVehicule.sort((r1, r2) -> r1.getDateHeureArrivee().compareTo(r2.getDateHeureArrivee()));
        
        // Construire les fenêtres de regroupement TA
        List<List<Reservation>> fenetres = construireFenetresTA(reservationsSansVehicule, taMinutes);
        
        // Traiter chaque fenêtre
        for (List<Reservation> groupe : fenetres) {
            // Trier chaque groupe par nombre de passagers DESC
            groupe.sort((r1, r2) -> Integer.compare(r2.getNombrePassager(), r1.getNombrePassager()));
            
            // Heure de départ = MAX(date_heure_arrivee) du groupe
            Timestamp heureDepart = calculerHeureDepart(groupe);
            
            assignerGroupeReservations(groupe, heureDepart);
        }
    }
    
    /**
     * Construit les fenêtres de regroupement basées sur le temps d'attente (TA).
     * Fenêtre = [heure_premier_vol, heure_premier_vol + TA]
     * Le début de fenêtre est fixe (premier vol du groupe), pas glissant.
     */
    private List<List<Reservation>> construireFenetresTA(List<Reservation> reservations, int taMinutes) {
        List<List<Reservation>> fenetres = new ArrayList<>();
        
        if (reservations == null || reservations.isEmpty()) {
            return fenetres;
        }
        
        List<Reservation> fenetreActuelle = new ArrayList<>();
        // debutFenetre est FIXE = heure du premier vol du groupe courant (ne glisse pas)
        Timestamp debutFenetre = reservations.get(0).getDateHeureArrivee();
        long taMillis = taMinutes * 60L * 1000L;
        
        for (Reservation r : reservations) {
            Timestamp arrivee = r.getDateHeureArrivee();
            
            if (arrivee.getTime() <= debutFenetre.getTime() + taMillis) {
                // Le vol est dans la fenêtre courante [debutFenetre, debutFenetre + TA]
                fenetreActuelle.add(r);
            } else {
                // Le vol est hors fenêtre → fermer le groupe courant
                fenetres.add(new ArrayList<>(fenetreActuelle));
                // Nouveau groupe : ce vol est le premier, il définit le nouveau début de fenêtre
                fenetreActuelle = new ArrayList<>();
                fenetreActuelle.add(r);
                debutFenetre = arrivee; // Nouveau début = heure de CE vol (fixe pour le nouveau groupe)
            }
        }
        
        // Ajouter le dernier groupe
        if (!fenetreActuelle.isEmpty()) {
            fenetres.add(fenetreActuelle);
        }
        
        return fenetres;
    }

    /**
     * Assigner automatiquement les véhicules aux réservations sans véhicule.
     * Sprint 5: regroupement par fenêtre TA fixe.
     */
    public void assignerVehiculesAutomatiquement(Date date) throws SQLException {
        List<Reservation> reservationsSansVehicule = reservationDAO.findWithoutVehiculeByDate(date);
        
        if (reservationsSansVehicule == null || reservationsSansVehicule.isEmpty()) {
            return;
        }
        
        int taMinutes = parametreDAO.getTempsAttente();
        
        // Trier par date_heure_arrivee croissante (obligatoire pour l'algo de fenêtrage)
        reservationsSansVehicule.sort((r1, r2) -> r1.getDateHeureArrivee().compareTo(r2.getDateHeureArrivee()));
        
        // Construire les fenêtres TA
        List<List<Reservation>> fenetres = construireFenetresTA(reservationsSansVehicule, taMinutes);
        
        // Traiter chaque fenêtre dans l'ordre chronologique
        for (List<Reservation> groupe : fenetres) {
            // Trier par passagers DESC dans chaque groupe
            groupe.sort((r1, r2) -> Integer.compare(r2.getNombrePassager(), r1.getNombrePassager()));
            
            // Heure de départ = MAX(date_heure_arrivee) du groupe
            Timestamp heureDepart = calculerHeureDepart(groupe);
            
            assignerGroupeReservations(groupe, heureDepart);
        }
    }

    /**
     * Assigne des véhicules à un groupe de réservations.
     * heureDepart = MAX(arrivées du groupe) utilisé pour la vérification de disponibilité.
     */
    private void assignerGroupeReservations(List<Reservation> groupe, Timestamp heureDepart) throws SQLException {
        if (groupe.isEmpty()) {
            return;
        }
        
        // Trouver l'hôtel le plus éloigné pour estimer le temps de retour du véhicule
        int idHotelPlusLoin = trouverHotelPlusLoin(groupe);
        
        // Véhicules disponibles à l'heure de départ effective du groupe (MAX arrivées)
        List<Vehicule> vehiculesDisponibles = reservationService.getVehiculesDisponibles(heureDepart, idHotelPlusLoin);
        
        Map<Integer, Integer> placesRestantes = new HashMap<>();
        Map<Integer, Vehicule> vehiculesMap = new HashMap<>();
        Map<Integer, List<Reservation>> reservationsParVehicule = new HashMap<>();
        
        for (Reservation r : groupe) {
            int nbPax = r.getNombrePassager();
            
            Vehicule meilleur = null;
            int meilleureCapaRestante = Integer.MAX_VALUE;
            
            // 1. Véhicules déjà utilisés dans ce groupe (places restantes)
            for (Map.Entry<Integer, Integer> entry : placesRestantes.entrySet()) {
                int vehId = entry.getKey();
                int reste = entry.getValue();
                if (reste >= nbPax) {
                    if (reste < meilleureCapaRestante) {
                        meilleureCapaRestante = reste;
                        meilleur = vehiculesMap.get(vehId);
                    } else if (reste == meilleureCapaRestante && meilleur != null) {
                        Vehicule candidat = vehiculesMap.get(vehId);
                        // Préférence Diesel en cas d'égalité
                        if ("D".equals(candidat.getTypeCarburant()) && !"D".equals(meilleur.getTypeCarburant())) {
                            meilleur = candidat;
                        }
                    }
                }
            }
            
            // 2. Véhicules disponibles pas encore utilisés dans ce groupe
            for (Vehicule v : vehiculesDisponibles) {
                if (placesRestantes.containsKey(v.getId())) {
                    continue; // Déjà pris en compte ci-dessus
                }
                int capacite = v.getNombrePlace();
                if (capacite >= nbPax) {
                    if (capacite < meilleureCapaRestante) {
                        meilleureCapaRestante = capacite;
                        meilleur = v;
                    } else if (capacite == meilleureCapaRestante && meilleur != null) {
                        // Préférence Diesel en cas d'égalité
                        if ("D".equals(v.getTypeCarburant()) && !"D".equals(meilleur.getTypeCarburant())) {
                            meilleur = v;
                        }
                    }
                }
            }
            
            if (meilleur != null) {
                int vehId = meilleur.getId();
                if (!placesRestantes.containsKey(vehId)) {
                    placesRestantes.put(vehId, meilleur.getNombrePlace() - nbPax);
                    vehiculesMap.put(vehId, meilleur);
                    List<Reservation> liste = new ArrayList<>();
                    liste.add(r);
                    reservationsParVehicule.put(vehId, liste);
                } else {
                    placesRestantes.put(vehId, placesRestantes.get(vehId) - nbPax);
                    reservationsParVehicule.get(vehId).add(r);
                }
            }
            // Si meilleur == null : aucun véhicule disponible, réservation reste sans véhicule
        }
        
        // Nearest-neighbour + enregistrement en base
        for (Map.Entry<Integer, List<Reservation>> entry : reservationsParVehicule.entrySet()) {
            int vehId = entry.getKey();
            List<Reservation> sousGroupe = entry.getValue();
            List<Reservation> ordreDepose = calculerOrdreDepose(sousGroupe);
            
            for (Reservation r : ordreDepose) {
                reservationDAO.assignVehicule(r.getId(), vehId);
                r.setIdVehicule(vehId);
            }
        }
    }

    /**
     * Construit les groupes de réservations par véhicule pour une date donnée.
     * Corrigé: debutFenetre se remet à jour correctement lors du changement de groupe.
     */
    public List<GroupeVehicule> construireGroupesParVehicule(Date date) throws SQLException {
        List<GroupeVehicule> groupes = new ArrayList<>();
        
        List<PlanificationReservation> planifications = getPlanificationsByDate(date);
        
        if (planifications.isEmpty()) {
            return groupes;
        }
        
        int taMinutes = parametreDAO.getTempsAttente();
        long taMillis = taMinutes * 60L * 1000L;
        
        // Grouper par véhicule
        Map<Integer, List<PlanificationReservation>> parVehicule = new LinkedHashMap<>();
        for (PlanificationReservation p : planifications) {
            int vehId = p.getReservation().getIdVehicule();
            parVehicule.computeIfAbsent(vehId, k -> new ArrayList<>()).add(p);
        }
        
        for (List<PlanificationReservation> planifVehicule : parVehicule.values()) {
            // Trier par date_heure_arrivee
            planifVehicule.sort((a, b) -> a.getReservation().getDateHeureArrivee()
                .compareTo(b.getReservation().getDateHeureArrivee()));
            
            List<List<PlanificationReservation>> sousGroupes = new ArrayList<>();
            List<PlanificationReservation> groupeActuel = new ArrayList<>();
            // debutFenetre FIXE = heure du premier vol du groupe courant
            long debutFenetre = planifVehicule.get(0).getReservation().getDateHeureArrivee().getTime();
            
            for (PlanificationReservation p : planifVehicule) {
                long arrivee = p.getReservation().getDateHeureArrivee().getTime();
                if (arrivee <= debutFenetre + taMillis) {
                    groupeActuel.add(p);
                } else {
                    if (!groupeActuel.isEmpty()) {
                        sousGroupes.add(new ArrayList<>(groupeActuel));
                    }
                    groupeActuel = new ArrayList<>();
                    groupeActuel.add(p);
                    debutFenetre = arrivee; // Nouveau début = heure de ce vol
                }
            }
            if (!groupeActuel.isEmpty()) {
                sousGroupes.add(groupeActuel);
            }
            
            for (List<PlanificationReservation> planifList : sousGroupes) {
                groupes.add(construireUnGroupe(planifList));
            }
        }
        
        return groupes;
    }

    /**
     * Construit un GroupeVehicule à partir d'une liste de planifications du même véhicule/fenêtre.
     * Calcule l'itinéraire nearest-neighbour avec les vraies distances.
     */
    private GroupeVehicule construireUnGroupe(List<PlanificationReservation> planifList) throws SQLException {
        PlanificationReservation first = planifList.get(0);
        Reservation firstRes = first.getReservation();
        
        // Créer le véhicule
        Vehicule vehicule = new Vehicule();
        vehicule.setId(firstRes.getIdVehicule());
        vehicule.setReference(firstRes.getReferenceVehicule());
        vehicule.setTypeCarburant(firstRes.getTypeCarburant());
        // Récupérer nombre_place depuis la première planification
        vehicule.setNombrePlace(firstRes.getCapaciteVehicule());
        
        GroupeVehicule groupe = new GroupeVehicule(vehicule);
        
        // Collecter les réservations
        List<Reservation> reservations = new ArrayList<>();
        int totalPassagers = 0;
        Timestamp maxArrivee = firstRes.getDateHeureArrivee();
        
        for (PlanificationReservation p : planifList) {
            Reservation r = p.getReservation();
            reservations.add(r);
            totalPassagers += r.getNombrePassager();
            if (r.getDateHeureArrivee().after(maxArrivee)) {
                maxArrivee = r.getDateHeureArrivee();
            }
        }
        
        // Heure de départ = MAX(date_heure_arrivee) du groupe
        Timestamp heureDepart = maxArrivee;
        groupe.setHeureDepart(heureDepart);
        groupe.setTotalPassagers(totalPassagers);
        
        // Calculer l'ordre de dépose nearest-neighbour
        List<Reservation> ordreDepose = calculerOrdreDepose(reservations);
        groupe.setReservations(ordreDepose);
        
        // Récupérer la vitesse moyenne
        double vitesseMoyenne = parametreDAO.getVitesseMoyenne();
        
        // Construire l'itinéraire avec les vraies distances
        Map<String, Double> allDistances = getAllDistances(reservations);
        List<EtapeItineraire> itineraire = new ArrayList<>();
        String positionActuelle = "TNR";
        String nomPositionActuelle = "TNR";
        Timestamp heureActuelle = new Timestamp(heureDepart.getTime());
        double distanceTotale = 0;
        int dureeTotale = 0;
        
        for (Reservation r : ordreDepose) {
            String key = positionActuelle + "-" + r.getIdHotel();
            double distanceKm = allDistances.getOrDefault(key, 0.0);
            int dureeMinutes = (int) Math.round((distanceKm / vitesseMoyenne) * 60);
            
            heureActuelle = new Timestamp(heureActuelle.getTime() + dureeMinutes * 60L * 1000L);
            distanceTotale += distanceKm;
            dureeTotale += dureeMinutes;
            
            EtapeItineraire etape = new EtapeItineraire(
                nomPositionActuelle, r.getNomHotel(), distanceKm, dureeMinutes, heureActuelle);
            etape.getPassagersDeposes().add(r.getClient());
            itineraire.add(etape);
            
            positionActuelle = String.valueOf(r.getIdHotel());
            nomPositionActuelle = r.getNomHotel();
        }
        
        // Étape retour vers TNR
        // Pour le retour, utiliser la distance TNR->dernier hotel (symétrique)
        double distanceRetour = allDistances.getOrDefault("TNR-" + positionActuelle, 0.0);
        int dureeRetour = (int) Math.round((distanceRetour / vitesseMoyenne) * 60);
        heureActuelle = new Timestamp(heureActuelle.getTime() + dureeRetour * 60L * 1000L);
        distanceTotale += distanceRetour;
        dureeTotale += dureeRetour;
        
        EtapeItineraire retour = new EtapeItineraire(
            nomPositionActuelle, "TNR", distanceRetour, dureeRetour, heureActuelle);
        itineraire.add(retour);
        
        groupe.setItineraire(itineraire);
        groupe.setHeureRetour(heureActuelle);
        groupe.setDistanceTotaleKm(distanceTotale);
        groupe.setDureeTotaleMinutes(dureeTotale);
        
        return groupe;
    }
    
    /**
     * Regroupe et assigne automatiquement les véhicules pour une date.
     * Exécute l'algorithme d'assignation puis construit les groupes.
     */
    public List<GroupeVehicule> regrouperEtAssigner(Date date) throws SQLException {
        // D'abord assigner les véhicules
        assignerVehiculesAutomatiquement(date);
        
        // Puis construire les groupes
        return construireGroupesParVehicule(date);
    }
}