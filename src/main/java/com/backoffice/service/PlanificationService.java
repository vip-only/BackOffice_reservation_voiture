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
     * Tant qu'un vol suivant arrive dans cette fenêtre, il est ajouté au groupe.
     * Le prochain vol hors fenêtre démarre une nouvelle fenêtre.
     *
     * @param reservations réservations triées par date_heure_arrivee croissante
     * @param taMinutes temps d'attente en minutes
     * @return liste de groupes (chaque groupe = réservations dans la même fenêtre)
     */
    private List<List<Reservation>> construireFenetresTA(List<Reservation> reservations, int taMinutes) {
        List<List<Reservation>> fenetres = new ArrayList<>();
        
        if (reservations == null || reservations.isEmpty()) {
            return fenetres;
        }
        
        List<Reservation> fenetreActuelle = new ArrayList<>();
        Timestamp debutFenetre = reservations.get(0).getDateHeureArrivee();
        long taMillis = taMinutes * 60L * 1000L;
        
        for (Reservation r : reservations) {
            Timestamp arrivee = r.getDateHeureArrivee();
            
            if (arrivee.getTime() <= debutFenetre.getTime() + taMillis) {
                // Le vol est dans la fenêtre courante
                fenetreActuelle.add(r);
            } else {
                // Le vol est hors fenêtre → sauvegarder le groupe courant et démarrer un nouveau
                if (!fenetreActuelle.isEmpty()) {
                    fenetres.add(fenetreActuelle);
                }
                fenetreActuelle = new ArrayList<>();
                fenetreActuelle.add(r);
                debutFenetre = arrivee;
            }
        }
        
        // Ajouter le dernier groupe
        if (!fenetreActuelle.isEmpty()) {
            fenetres.add(fenetreActuelle);
        }
        
        return fenetres;
    }
    
    /**
     * Calcule l'heure de départ du véhicule = MAX(date_heure_arrivee) du groupe.
     */
    private Timestamp calculerHeureDepart(List<Reservation> groupe) {
        Timestamp max = groupe.get(0).getDateHeureArrivee();
        for (Reservation r : groupe) {
            if (r.getDateHeureArrivee().after(max)) {
                max = r.getDateHeureArrivee();
            }
        }
        return max;
    }
    
    /**
     * Assigne des véhicules à un groupe de réservations dans la même fenêtre TA.
     * 
     * ALGORITHME (respect strict Sprint 4) :
     * 1. Réservations triées par passagers DESC (fait par l'appelant)
     * 2. Pour CHAQUE réservation :
     *    - Regarder TOUS les véhicules : déjà utilisés dans cette fenêtre (place restante) + nouveaux
     *    - Choisir celui dont la place restante est la PLUS PROCHE du nb passagers (>= nb pax)
     *    - Si égalité : préférence Diesel
     *    - Un véhicule peut contenir PLUSIEURS réservations
     * 3. Pour chaque véhicule : nearest-neighbour + départage alphabétique
     */
    private void assignerGroupeReservations(List<Reservation> groupe, Timestamp heureArrivee) throws SQLException {
        if (groupe.isEmpty()) {
            return;
        }
        
        // Trouver l'hôtel le plus éloigné (pour calculer la dispo véhicule)
        int idHotelPlusLoin = trouverHotelPlusLoin(groupe);
        
        // Récupérer les véhicules disponibles (non occupés à cette heure)
        List<Vehicule> vehiculesDisponibles = reservationService.getVehiculesDisponibles(heureArrivee, idHotelPlusLoin);
        
        // Suivi des véhicules utilisés dans cette fenêtre
        Map<Integer, Integer> placesRestantes = new HashMap<>();
        Map<Integer, Vehicule> vehiculesMap = new HashMap<>();
        Map<Integer, List<Reservation>> reservationsParVehicule = new HashMap<>();
        
        // Pour chaque réservation (déjà triées par passagers DESC)
        for (Reservation r : groupe) {
            int nbPax = r.getNombrePassager();
            
            Vehicule meilleur = null;
            int meilleureCapaRestante = Integer.MAX_VALUE;
            
            // 1. Vérifier les véhicules déjà utilisés dans cette fenêtre
            for (Map.Entry<Integer, Integer> entry : placesRestantes.entrySet()) {
                int vehId = entry.getKey();
                int reste = entry.getValue();
                if (reste >= nbPax) {
                    if (reste < meilleureCapaRestante) {
                        meilleureCapaRestante = reste;
                        meilleur = vehiculesMap.get(vehId);
                    } else if (reste == meilleureCapaRestante && meilleur != null) {
                        Vehicule candidat = vehiculesMap.get(vehId);
                        if ("D".equals(candidat.getTypeCarburant()) && !"D".equals(meilleur.getTypeCarburant())) {
                            meilleur = candidat;
                        }
                    }
                }
            }
            
            // 2. Vérifier aussi les véhicules disponibles pas encore utilisés
            for (Vehicule v : vehiculesDisponibles) {
                if (placesRestantes.containsKey(v.getId())) {
                    continue;
                }
                int capacite = v.getNombrePlace();
                if (capacite >= nbPax) {
                    if (capacite < meilleureCapaRestante) {
                        meilleureCapaRestante = capacite;
                        meilleur = v;
                    } else if (capacite == meilleureCapaRestante && meilleur != null) {
                        if ("D".equals(v.getTypeCarburant()) && !"D".equals(meilleur.getTypeCarburant())) {
                            meilleur = v;
                        }
                    }
                }
            }
            
            // Assigner au meilleur véhicule trouvé
            if (meilleur != null) {
                int vehId = meilleur.getId();
                
                if (!placesRestantes.containsKey(vehId)) {
                    // Nouveau véhicule
                    placesRestantes.put(vehId, meilleur.getNombrePlace() - nbPax);
                    vehiculesMap.put(vehId, meilleur);
                    List<Reservation> liste = new ArrayList<>();
                    liste.add(r);
                    reservationsParVehicule.put(vehId, liste);
                } else {
                    // Véhicule déjà utilisé
                    placesRestantes.put(vehId, placesRestantes.get(vehId) - nbPax);
                    reservationsParVehicule.get(vehId).add(r);
                }
            }
        }
        
        // Pour chaque véhicule : nearest-neighbour et assigner en base
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
     * Recupere les distances depuis TNR pour les hotels du groupe.
     */
    private Map<Integer, Double> getDistancesHotels(List<Reservation> groupe) throws SQLException {
        Map<Integer, Double> distances = new HashMap<>();
        
        if (groupe == null || groupe.isEmpty()) {
            return distances;
        }
        
        // Recuperer les IDs d'hotels uniques
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
     * Recupere toutes les distances (TNR vers hotels ET inter-hotels).
     * Cle de la map: "from_id-to_id" (ex: "TNR-1", "1-2", etc.)
     */
    private Map<String, Double> getAllDistances(List<Reservation> groupe) throws SQLException {
        Map<String, Double> distances = new HashMap<>();
        
        if (groupe == null || groupe.isEmpty()) {
            return distances;
        }
        
        // Recuperer les IDs d'hotels uniques
        Set<Integer> hotelIds = new HashSet<>();
        for (Reservation r : groupe) {
            hotelIds.add(r.getIdHotel());
        }
        
        if (hotelIds.isEmpty()) {
            return distances;
        }
        
        // Construire les placeholders pour les hotels
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < hotelIds.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }
        
        // Recuperer distances depuis TNR et entre hotels
        String sql = "SELECT from_id, to_id, kilometer FROM distance " +
                     "WHERE (from_id = 'TNR' AND to_id IN (" + placeholders + ")) " +
                     "   OR (from_id IN (" + placeholders + ") AND to_id IN (" + placeholders + "))";
        
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            int idx = 1;
            // Pour TNR -> hotels
            for (Integer hotelId : hotelIds) {
                ps.setString(idx++, String.valueOf(hotelId));
            }
            // Pour inter-hotels (from)
            for (Integer hotelId : hotelIds) {
                ps.setString(idx++, String.valueOf(hotelId));
            }
            // Pour inter-hotels (to)
            for (Integer hotelId : hotelIds) {
                ps.setString(idx++, String.valueOf(hotelId));
            }
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String fromId = rs.getString("from_id");
                    String toId = rs.getString("to_id");
                    double km = rs.getDouble("kilometer");
                    
                    // Stocker la distance dans le sens original
                    distances.put(fromId + "-" + toId, km);
                    
                    // Pour les distances inter-hotels (pas TNR), stocker aussi le sens inverse
                    // Cela permet d'avoir une seule entree en base pour chaque paire d'hotels
                    if (!"TNR".equals(fromId) && !"TNR".equals(toId)) {
                        distances.put(toId + "-" + fromId, km);
                    }
                }
            }
        }
        
        return distances;
    }
    
    /**
     * Calcule l'ordre de depose selon l'heuristique du plus proche voisin (nearest-neighbour).
     * Part de TNR, va au plus proche, puis au suivant le plus proche, etc.
     * Utilise les vraies distances inter-hotels stockees en base.
     */
    private List<Reservation> calculerOrdreDepose(List<Reservation> groupe) throws SQLException {
        if (groupe == null || groupe.isEmpty()) {
            return new ArrayList<>();
        }
        if (groupe.size() == 1) {
            return new ArrayList<>(groupe);
        }
        
        // Recuperer TOUTES les distances (TNR->hotels et inter-hotels)
        Map<String, Double> allDistances = getAllDistances(groupe);
        
        List<Reservation> ordreDepose = new ArrayList<>();
        List<Reservation> restants = new ArrayList<>(groupe);
        
        // Point de depart: TNR
        String positionActuelle = "TNR";
        
        while (!restants.isEmpty()) {
            // Trouver la reservation dont l'hotel est le plus proche de la position actuelle
            Reservation plusProche = null;
            double distanceMinimale = Double.MAX_VALUE;
            String nomHotelMinimal = null; // Pour departage par ordre alphabetique si meme distance
            
            for (Reservation r : restants) {
                // Cle pour trouver la distance: "position_actuelle-id_hotel"
                String key = positionActuelle + "-" + r.getIdHotel();
                double distanceDepuisActuel = allDistances.getOrDefault(key, Double.MAX_VALUE);
                
                // Choisir le plus proche, ou en cas d'egalite, ordre alphabetique du nom d'hotel
                if (distanceDepuisActuel < distanceMinimale || 
                    (distanceDepuisActuel == distanceMinimale && nomHotelMinimal != null && 
                     r.getNomHotel().compareTo(nomHotelMinimal) < 0)) {
                    distanceMinimale = distanceDepuisActuel;
                    plusProche = r;
                    nomHotelMinimal = r.getNomHotel();
                }
            }
            
            if (plusProche != null) {
                ordreDepose.add(plusProche);
                restants.remove(plusProche);
                // Nouvelle position = l'hotel qu'on vient de visiter
                positionActuelle = String.valueOf(plusProche.getIdHotel());
            } else {
                // Aucune distance trouvee, ajouter les restants dans l'ordre
                ordreDepose.addAll(restants);
                break;
            }
        }
        
        return ordreDepose;
    }
    
    /**
     * Construit les groupes de réservations par véhicule pour une date donnée.
     * Regroupe les réservations ayant le même véhicule dans la même fenêtre TA.
     * Heure de départ = MAX(date_heure_arrivee) du groupe.
     */
    public List<GroupeVehicule> construireGroupesParVehicule(Date date) throws SQLException {
        List<GroupeVehicule> groupes = new ArrayList<>();
        
        // Récupérer les planifications (réservations avec véhicule assigné)
        List<PlanificationReservation> planifications = getPlanificationsByDate(date);
        
        if (planifications.isEmpty()) {
            return groupes;
        }
        
        // Lire le paramètre TA
        int taMinutes = parametreDAO.getTempsAttente();
        long taMillis = taMinutes * 60L * 1000L;
        
        // Grouper par véhicule d'abord
        Map<Integer, List<PlanificationReservation>> parVehicule = new LinkedHashMap<>();
        for (PlanificationReservation p : planifications) {
            int vehId = p.getReservation().getIdVehicule();
            parVehicule.computeIfAbsent(vehId, k -> new ArrayList<>()).add(p);
        }
        
        // Pour chaque véhicule, sous-grouper par fenêtre TA
        for (List<PlanificationReservation> planifVehicule : parVehicule.values()) {
            // Trier par date_heure_arrivee
            planifVehicule.sort((a, b) -> a.getReservation().getDateHeureArrivee()
                .compareTo(b.getReservation().getDateHeureArrivee()));
            
            // Construire les sous-groupes TA
            List<List<PlanificationReservation>> sousGroupes = new ArrayList<>();
            List<PlanificationReservation> groupeActuel = new ArrayList<>();
            long debutFenetre = planifVehicule.get(0).getReservation().getDateHeureArrivee().getTime();
            
            for (PlanificationReservation p : planifVehicule) {
                long arrivee = p.getReservation().getDateHeureArrivee().getTime();
                if (arrivee <= debutFenetre + taMillis) {
                    groupeActuel.add(p);
                } else {
                    if (!groupeActuel.isEmpty()) {
                        sousGroupes.add(groupeActuel);
                    }
                    groupeActuel = new ArrayList<>();
                    groupeActuel.add(p);
                    debutFenetre = arrivee;
                }
            }
            if (!groupeActuel.isEmpty()) {
                sousGroupes.add(groupeActuel);
            }
            
            // Construire un GroupeVehicule pour chaque sous-groupe
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