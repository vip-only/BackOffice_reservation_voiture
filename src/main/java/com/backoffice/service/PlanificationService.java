package com.backoffice.service;

import com.backoffice.dao.DistanceDAO;
import com.backoffice.dao.ParametreDAO;
import com.backoffice.dao.ReservationDAO;
import com.backoffice.dao.VehiculeDAO;
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
import java.util.Collections;
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
    private DistanceDAO distanceDAO = new DistanceDAO();
    private ParametreDAO parametreDAO = new ParametreDAO();
    private VehiculeDAO vehiculeDAO = new VehiculeDAO();
    
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
                    reservation.setIdVehicule(rs.getInt("vehicule_id"));
                    reservation.setReferenceVehicule(rs.getString("vehicule"));
                    
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
     * NOUVELLES REGLES:
     * 1. Traiter en priorité les réservations avec le plus de passagers (déjà trié dans DAO)
     * 2. Regrouper les clients arrivant à la même date/heure dans la même voiture si capacité le permet
     * 3. Pas de temps d'attente pour le regroupement
     * 4. Ordre de dépose par distance minimale (nearest-neighbour)
     */
    public void assignerVehiculesAutomatiquement(Date date) throws SQLException {
        // Récupérer toutes les réservations sans véhicule, triées par nombre de passagers DESC
        List<Reservation> reservationsSansVehicule = reservationDAO.findWithoutVehiculeByDate(date);
        
        if (reservationsSansVehicule == null || reservationsSansVehicule.isEmpty()) {
            return;
        }
        
        // Grouper les réservations par date/heure d'arrivée exacte
        Map<Timestamp, List<Reservation>> groupesParHeure = new LinkedHashMap<>();
        for (Reservation r : reservationsSansVehicule) {
            Timestamp ts = r.getDateHeureArrivee();
            if (ts != null) {
                if (!groupesParHeure.containsKey(ts)) {
                    groupesParHeure.put(ts, new ArrayList<>());
                }
                groupesParHeure.get(ts).add(r);
            }
        }
        
        // Traiter chaque groupe de réservations (même heure d'arrivée)
        for (Map.Entry<Timestamp, List<Reservation>> entry : groupesParHeure.entrySet()) {
            Timestamp heureArrivee = entry.getKey();
            List<Reservation> groupe = entry.getValue();
            
            // Le groupe est déjà trié par nombre de passagers DESC (grâce au DAO)
            assignerGroupeReservations(groupe, heureArrivee);
        }
    }
    
    /**
     * Assigne un véhicule à un groupe de réservations arrivant à la même heure.
     * Essaie de regrouper dans un seul véhicule si la capacité le permet.
     */
    private void assignerGroupeReservations(List<Reservation> groupe, Timestamp heureArrivee) throws SQLException {
        if (groupe.isEmpty()) {
            return;
        }
        
        // Calculer le total de passagers du groupe
        int totalPassagers = groupe.stream().mapToInt(Reservation::getNombrePassager).sum();
        
        // Trouver l'hôtel le plus éloigné (pour calculer le temps de retour max)
        int idHotelPlusLoin = trouverHotelPlusLoin(groupe);
        
        // Essayer de trouver un véhicule pouvant accueillir tout le groupe
        List<Vehicule> vehiculesDisponibles = reservationService.getVehiculesDisponibles(heureArrivee, idHotelPlusLoin);
        Vehicule vehiculeGroupe = affectationService.choisirMeilleurVehicule(vehiculesDisponibles, totalPassagers);
        
        if (vehiculeGroupe != null) {
            // Un seul véhicule peut prendre tout le groupe
            // Déterminer l'ordre de dépose (nearest-neighbour)
            List<Reservation> ordreDepose = calculerOrdreDepose(groupe);
            
            // Assigner le même véhicule à toutes les réservations du groupe
            for (Reservation r : ordreDepose) {
                reservationDAO.assignVehicule(r.getId(), vehiculeGroupe.getId());
                r.setIdVehicule(vehiculeGroupe.getId());
            }
        } else {
            // Pas assez de capacité pour regrouper, assigner individuellement
            // (traité par ordre de passagers décroissant)
            for (Reservation r : groupe) {
                // Vérifier si déjà assigné (par un groupe précédent)
                if (r.getIdVehicule() == null) {
                    reservationService.assignerVehiculeAuto(r);
                }
            }
        }
    }
    
    /**
     * Trouve l'hôtel le plus éloigné du groupe (pour le calcul du temps de retour).
     */
    private int trouverHotelPlusLoin(List<Reservation> groupe) throws SQLException {
        if (groupe == null || groupe.isEmpty()) {
            return 1; // Valeur par défaut
        }
        if (groupe.size() == 1) {
            return groupe.get(0).getIdHotel();
        }
        
        // Récupérer les distances pour tous les hôtels du groupe
        Map<Integer, Double> distances = getDistancesHotels(groupe);
        
        if (distances.isEmpty()) {
            return groupe.get(0).getIdHotel();
        }
        
        // Trouver l'hôtel avec la distance max
        int hotelPlusLoin = groupe.get(0).getIdHotel();
        double distanceMax = 0;
        for (Map.Entry<Integer, Double> entry : distances.entrySet()) {
            if (entry.getValue() > distanceMax) {
                distanceMax = entry.getValue();
                hotelPlusLoin = entry.getKey();
            }
        }
        return hotelPlusLoin;
    }
    
    /**
     * Récupère les distances depuis TNR pour les hôtels du groupe.
     */
    private Map<Integer, Double> getDistancesHotels(List<Reservation> groupe) throws SQLException {
        Map<Integer, Double> distances = new HashMap<>();
        
        if (groupe == null || groupe.isEmpty()) {
            return distances;
        }
        
        // Récupérer les IDs d'hôtels uniques
        Set<Integer> hotelIds = new HashSet<>();
        for (Reservation r : groupe) {
            hotelIds.add(r.getIdHotel());
        }
        
        if (hotelIds.isEmpty()) {
            return distances;
        }
        
        // Construire les placeholders
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < hotelIds.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }
        
        String sql = "SELECT CAST(to_id AS INTEGER) AS hotel_id, kilometer " +
                     "FROM distance WHERE from_id = 'TNR' AND to_id IN (" + placeholders.toString() + ")";
        
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            int idx = 1;
            for (Integer hotelId : hotelIds) {
                ps.setString(idx++, String.valueOf(hotelId));
            }
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    distances.put(rs.getInt("hotel_id"), rs.getDouble("kilometer"));
                }
            }
        }
        
        return distances;
    }
    
    /**
     * Calcule l'ordre de dépose selon l'heuristique du plus proche voisin (nearest-neighbour).
     * Part de TNR, va au plus proche, puis au suivant le plus proche, etc.
     * Utilise les vraies distances inter-hôtels via DistanceDAO.
     */
    private List<Reservation> calculerOrdreDepose(List<Reservation> groupe) throws SQLException {
        if (groupe == null || groupe.isEmpty()) {
            return new ArrayList<>();
        }
        if (groupe.size() == 1) {
            return new ArrayList<>(groupe);
        }
        
        List<Reservation> ordreDepose = new ArrayList<>();
        List<Reservation> restants = new ArrayList<>(groupe);
        int idLieuActuel = -1; // -1 = TNR
        
        while (!restants.isEmpty()) {
            Reservation plusProche = null;
            double distanceMinimale = Double.MAX_VALUE;
            
            for (Reservation r : restants) {
                double dist;
                if (idLieuActuel == -1) {
                    // Depuis TNR
                    dist = distanceDAO.getDistanceFromTNR(r.getIdHotel());
                } else {
                    // D'un hôtel à un autre (vraie distance)
                    dist = distanceDAO.getDistanceEntreHotels(idLieuActuel, r.getIdHotel());
                }
                if (dist < 0) dist = 999; // fallback
                
                if (dist < distanceMinimale) {
                    distanceMinimale = dist;
                    plusProche = r;
                }
            }
            
            if (plusProche != null) {
                ordreDepose.add(plusProche);
                restants.remove(plusProche);
                idLieuActuel = plusProche.getIdHotel();
            } else {
                ordreDepose.addAll(restants);
                break;
            }
        }
        
        return ordreDepose;
    }

    // =====================================================================
    // CONSTRUCTION DES GROUPES PAR VEHICULE (pour affichage itinéraires)
    // =====================================================================
    
    /**
     * Construit la liste des GroupeVehicule pour une date donnée.
     * Regroupe les réservations assignées par véhicule, puis calcule l'itinéraire
     * optimal (nearest-neighbour) et les horaires de retour pour chaque groupe.
     * 
     * Calcul horaires de retour d'un groupe multi-hôtels :
     *   Départ TNR = date_heure_arrivee
     *   TNR --(d1/v)--> Hotel1  --(d12/v)--> Hotel2  --(d2_TNR/v)--> TNR
     *   Heure retour = départ + (d1 + d12 + d2_TNR) / vitesse
     *   (PAS de temps d'attente pour les regroupements)
     */
    public List<GroupeVehicule> construireGroupesParVehicule(Date date) throws SQLException {
        List<GroupeVehicule> groupes = new ArrayList<>();
        
        // 1) Récupérer toutes les réservations assignées pour cette date
        List<Reservation> reservationsAssignees = reservationDAO.findByDate(date);
        
        // Filtrer : garder uniquement celles avec véhicule
        List<Reservation> avecVehicule = new ArrayList<>();
        for (Reservation r : reservationsAssignees) {
            if (r.getIdVehicule() != null) {
                avecVehicule.add(r);
            }
        }
        
        if (avecVehicule.isEmpty()) return groupes;
        
        // 2) Grouper par id_vehicule
        Map<Integer, List<Reservation>> parVehicule = new LinkedHashMap<>();
        for (Reservation r : avecVehicule) {
            int vid = r.getIdVehicule();
            if (!parVehicule.containsKey(vid)) {
                parVehicule.put(vid, new ArrayList<>());
            }
            parVehicule.get(vid).add(r);
        }
        
        // 3) Pour chaque véhicule, construire le GroupeVehicule avec itinéraire
        double vitesseMoyenne = parametreDAO.getVitesseMoyenne(); // km/h
        
        for (Map.Entry<Integer, List<Reservation>> entry : parVehicule.entrySet()) {
            int vehiculeId = entry.getKey();
            List<Reservation> resasVehicule = entry.getValue();
            
            // Charger le véhicule
            Vehicule vehicule = vehiculeDAO.findById(vehiculeId);
            if (vehicule == null) continue;
            
            GroupeVehicule groupe = new GroupeVehicule(vehicule);
            groupe.setReservations(resasVehicule);
            
            // Total passagers
            int totalPassagers = 0;
            for (Reservation r : resasVehicule) {
                totalPassagers += r.getNombrePassager();
            }
            groupe.setTotalPassagers(totalPassagers);
            
            // Heure de départ = date_heure_arrivée du vol (on prend la 1ère résa du groupe)
            Timestamp heureDepart = resasVehicule.get(0).getDateHeureArrivee();
            groupe.setHeureDepart(heureDepart);
            
            // Déterminer l'ordre optimal de visite des hôtels (nearest-neighbour)
            // et construire l'itinéraire avec les horaires
            calculerItineraire(groupe, vitesseMoyenne);
            
            groupes.add(groupe);
        }
        
        return groupes;
    }
    
    /**
     * Calcule l'itinéraire d'un GroupeVehicule :
     *   TNR -> Hotel_1 -> Hotel_2 -> ... -> Hotel_n -> TNR
     * avec nearest-neighbour pour l'ordre des hôtels.
     * 
     * Calcule aussi la distance totale, durée totale et heure de retour.
     */
    private void calculerItineraire(GroupeVehicule groupe, double vitesseMoyenne) throws SQLException {
        List<Reservation> reservations = groupe.getReservations();
        Timestamp heureDepart = groupe.getHeureDepart();
        
        // Collecter les hôtels uniques à visiter et les passagers par hôtel
        Map<Integer, String> nomHotelParId = new LinkedHashMap<>();
        Map<Integer, List<String>> passagersParHotel = new LinkedHashMap<>();
        
        for (Reservation r : reservations) {
            int idHotel = r.getIdHotel();
            nomHotelParId.put(idHotel, r.getNomHotel());
            if (!passagersParHotel.containsKey(idHotel)) {
                passagersParHotel.put(idHotel, new ArrayList<>());
            }
            passagersParHotel.get(idHotel).add(r.getClient() + " (" + r.getNombrePassager() + " pers.)");
        }
        
        // Ordre optimal via nearest-neighbour
        List<Integer> hotelsAVisiter = new ArrayList<>(passagersParHotel.keySet());
        List<Integer> ordreHotels = optimiserOrdreHotels(hotelsAVisiter);
        
        // Construire les étapes
        List<EtapeItineraire> etapes = new ArrayList<>();
        double distanceTotale = 0;
        int dureeTotale = 0;
        long tempsActuelMs = heureDepart.getTime();
        String lieuActuel = "TNR";
        int idLieuActuel = -1; // -1 = TNR
        
        for (int idHotel : ordreHotels) {
            // Calculer distance de l'étape
            double distEtape;
            if (idLieuActuel == -1) {
                // Depuis TNR
                distEtape = distanceDAO.getDistanceFromTNR(idHotel);
            } else {
                // D'un hôtel à un autre
                distEtape = distanceDAO.getDistanceEntreHotels(idLieuActuel, idHotel);
            }
            if (distEtape < 0) distEtape = 15; // fallback 15 km
            
            // Durée en minutes = distance / vitesse * 60
            int dureeMinutes = (int) Math.round((distEtape / vitesseMoyenne) * 60);
            
            // Heure d'arrivée à cette étape
            tempsActuelMs += dureeMinutes * 60L * 1000L;
            Timestamp heureArrivee = new Timestamp(tempsActuelMs);
            
            // Créer l'étape
            String nomArrivee = nomHotelParId.getOrDefault(idHotel, "Hôtel " + idHotel);
            EtapeItineraire etape = new EtapeItineraire(lieuActuel, nomArrivee, distEtape, dureeMinutes, heureArrivee);
            etape.setPassagersDeposes(passagersParHotel.getOrDefault(idHotel, new ArrayList<>()));
            
            etapes.add(etape);
            distanceTotale += distEtape;
            dureeTotale += dureeMinutes;
            
            // Avancer
            lieuActuel = nomArrivee;
            idLieuActuel = idHotel;
        }
        
        // Dernière étape : retour du dernier hôtel vers TNR
        double distRetour;
        if (idLieuActuel >= 0) {
            distRetour = distanceDAO.getDistanceFromTNR(idLieuActuel);
        } else {
            distRetour = 0;
        }
        if (distRetour < 0) distRetour = 15;
        
        int dureeRetour = (int) Math.round((distRetour / vitesseMoyenne) * 60);
        tempsActuelMs += dureeRetour * 60L * 1000L;
        Timestamp heureRetour = new Timestamp(tempsActuelMs);
        
        EtapeItineraire etapeRetour = new EtapeItineraire(lieuActuel, "TNR", distRetour, dureeRetour, heureRetour);
        etapes.add(etapeRetour);
        
        distanceTotale += distRetour;
        dureeTotale += dureeRetour;
        
        // Affecter au groupe
        groupe.setItineraire(etapes);
        groupe.setDistanceTotaleKm(distanceTotale);
        groupe.setDureeTotaleMinutes(dureeTotale);
        groupe.setHeureRetour(heureRetour);
    }
    
    /**
     * Optimise l'ordre de visite des hôtels avec l'heuristique nearest-neighbour.
     * Départ = TNR, à chaque étape on choisit l'hôtel non visité le plus proche.
     */
    private List<Integer> optimiserOrdreHotels(List<Integer> hotelsAVisiter) throws SQLException {
        if (hotelsAVisiter.size() <= 1) return new ArrayList<>(hotelsAVisiter);
        
        List<Integer> ordre = new ArrayList<>();
        List<Integer> restants = new ArrayList<>(hotelsAVisiter);
        int idLieuActuel = -1; // -1 = TNR
        
        while (!restants.isEmpty()) {
            int plusProche = -1;
            double distMin = Double.MAX_VALUE;
            
            for (int idHotel : restants) {
                double dist;
                if (idLieuActuel == -1) {
                    // Depuis TNR
                    dist = distanceDAO.getDistanceFromTNR(idHotel);
                } else {
                    dist = distanceDAO.getDistanceEntreHotels(idLieuActuel, idHotel);
                }
                if (dist < 0) dist = 999;
                
                if (dist < distMin) {
                    distMin = dist;
                    plusProche = idHotel;
                }
            }
            
            if (plusProche >= 0) {
                ordre.add(plusProche);
                restants.remove(Integer.valueOf(plusProche));
                idLieuActuel = plusProche;
            } else {
                ordre.addAll(restants);
                break;
            }
        }
        
        return ordre;
    }

    /**
     * Regroupe et assigne les réservations pour une date (algorithme amélioré).
     * 1. Récupère les réservations sans véhicule pour la date
     * 2. Groupe par timestamp (même heure d'arrivée)
     * 3. Pour chaque groupe, trie par nombre de passagers DESC
     * 4. Tente de regrouper dans un même véhicule (greedy bin packing)
     * 5. Retourne les groupes construits après assignation
     */
    public List<GroupeVehicule> regrouperEtAssigner(Date date) throws SQLException {
        // D'abord, assigner avec la logique de regroupement
        assignerVehiculesAutomatiquement(date);
        
        // Puis construire les groupes pour affichage
        return construireGroupesParVehicule(date);
    }
}