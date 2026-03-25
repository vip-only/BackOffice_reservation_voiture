package com.backoffice.model;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import com.backoffice.dao.*;
import com.backoffice.database.DBConnection;
public class Reservation {
    private int id;
    private String client;
    private int nombrePassager;
    private Timestamp dateHeureArrivee;
    private int idHotel;
    private Integer idVehicule; // Nullable car pas toujours assigné

    // Pour l'affichage
    private String nomHotel;
    private String referenceVehicule;
    private String typeCarburant;
    private int capaciteVehicule;

    public Reservation() {}

    public Reservation(String client, int nombrePassager, Timestamp dateHeureArrivee, int idHotel) {
        this.client = client;
        this.nombrePassager = nombrePassager;
        this.dateHeureArrivee = dateHeureArrivee;
        this.idHotel = idHotel;
    }

    public Reservation(String client, int nombrePassager, Timestamp dateHeureArrivee, int idHotel, Integer idVehicule) {
        this.client = client;
        this.nombrePassager = nombrePassager;
        this.dateHeureArrivee = dateHeureArrivee;
        this.idHotel = idHotel;
        this.idVehicule = idVehicule;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getClient() {
        return client;
    }

    public void setClient(String client) {
        this.client = client;
    }

    public int getNombrePassager() {
        return nombrePassager;
    }

    public void setNombrePassager(int nombrePassager) {
        this.nombrePassager = nombrePassager;
    }

    public Timestamp getDateHeureArrivee() {
        return dateHeureArrivee;
    }

    public void setDateHeureArrivee(Timestamp dateHeureArrivee) {
        this.dateHeureArrivee = dateHeureArrivee;
    }

    public int getIdHotel() {
        return idHotel;
    }

    public void setIdHotel(int idHotel) {
        this.idHotel = idHotel;
    }

    public Integer getIdVehicule() {
        return idVehicule;
    }

    public void setIdVehicule(Integer idVehicule) {
        this.idVehicule = idVehicule;
    }

    public String getNomHotel() {
        return nomHotel;
    }

    public void setNomHotel(String nomHotel) {
        this.nomHotel = nomHotel;
    }

    public String getReferenceVehicule() {
        return referenceVehicule;
    }

    public void setReferenceVehicule(String referenceVehicule) {
        this.referenceVehicule = referenceVehicule;
    }

    public String getTypeCarburant() {
        return typeCarburant;
    }

    public void setTypeCarburant(String typeCarburant) {
        this.typeCarburant = typeCarburant;
    }

    public int getCapaciteVehicule() {
        return capaciteVehicule;
    }

    public void setCapaciteVehicule(int capaciteVehicule) {
        this.capaciteVehicule = capaciteVehicule;
    }

    // Méthode utilitaire pour vérifier si un véhicule est assigné
    public boolean hasVehicule() {
        return idVehicule != null;
    }

    // Méthode utilitaire pour afficher le type de carburant en clair
    public String getTypeCarburantLibelle() {
        if (typeCarburant == null) return "";
        switch (typeCarburant) {
            case "D": return "Diesel";
            case "ES": return "Essence";
            case "H": return "Hybride";
            case "EL": return "Électrique";
            default: return typeCarburant;
        }
    }
    private Reservation mapResultSet(ResultSet rs) throws SQLException {
        Reservation res = new Reservation();
        res.setId(rs.getInt("id"));
        res.setClient(rs.getString("client"));
        res.setNombrePassager(rs.getInt("nombre_passager"));
        res.setDateHeureArrivee(rs.getTimestamp("date_heure_arrivee"));
        res.setIdHotel(rs.getInt("id_hotel"));
        
        // id_vehicule peut être NULL
        int idVehicule = rs.getInt("id_vehicule");
        if (!rs.wasNull()) {
            res.setIdVehicule(idVehicule);
        }
        
        res.setNomHotel(rs.getString("nom_hotel"));
        
        // reference_vehicule peut ne pas exister dans certaines requêtes
        try {
            res.setReferenceVehicule(rs.getString("reference_vehicule"));
        } catch (SQLException e) {
            // Colonne non présente, on ignore
        }
        
        return res;
    }
    public List<Reservation> getReservationAssignes() throws SQLException, ClassNotFoundException{
        // ReservationDAO reservationDAO = new ReservationDAO();
   
        // return r;
        
        List<Reservation> reservations = new ArrayList<>();
        String sql = "SELECT r.id, r.client, r.nombre_passager, r.date_heure_arrivee, r.id_hotel, r.id_vehicule, " +
                     "h.nom AS nom_hotel, v.reference AS reference_vehicule " +
                     "FROM reservation r " +
                     "JOIN hotel h ON r.id_hotel = h.id_hotel " +
                     "LEFT JOIN vehicule v ON r.id_vehicule = v.id " +
                    //  "WHERE r.id_vehicule =  " + idVehicule + " " + 
                     "WHERE r.id_vehicule is not null "  + 

                     "ORDER BY r.nombre_passager DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Reservation res = mapResultSet(rs);
                reservations.add(res);
            }
        }
        return reservations;
        // return reservationDAO.findByIdVehicule(idVehicule);
    }
     public List<Reservation> getReservationNonAssignes() throws SQLException, ClassNotFoundException{
        // ReservationDAO reservationDAO = new ReservationDAO();
   
        // return r;
        
        List<Reservation> reservations = new ArrayList<>();
        String sql = "SELECT r.id, r.client, r.nombre_passager, r.date_heure_arrivee, r.id_hotel, r.id_vehicule, " +
                     "h.nom AS nom_hotel, v.reference AS reference_vehicule " +
                     "FROM reservation r " +
                     "JOIN hotel h ON r.id_hotel = h.id_hotel " +
                     "LEFT JOIN vehicule v ON r.id_vehicule = v.id " +
                     "WHERE r.id_vehicule is null " +
                     "ORDER BY r.nombre_passager DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Reservation res = mapResultSet(rs);
                reservations.add(res);
            }
        }
        return reservations;
        // return reservationDAO.findByIdVehicule(idVehicule);
    }
    public void assignerVehicules(List<Reservation> reservations)throws SQLException, ClassNotFoundException{
        List<Reservation> reservationsNonAssignes = getReservationNonAssignes();
        List<Reservation> reservationsAssignes = getReservationAssignes();
        List<Vehicule> vehicule = getPlusPetiteCapacite(reservationsAssignes);
        if (reservationsAssignes.isEmpty()) {
           
        } else {
            for (Reservation reservation : reservationsAssignes) {
               for (Vehicule v : vehicule) {

            //fonction getVehiculeOPtimal(reservation)
                        // if(reservation.getNombrePassager())
               }
            }
        }
        // for (Reservation reservation : reservations) {
        //     Vehicule v = new Vehicule();
        //     int n = v.getNbPlaceReservees(1);
        //     // System.out.println( "place restantes: " + n);
        //     // reservation.setIdVehicule(1);
        //     // updateAssignationVehicule(reservation.getId(), 1);
        // }
    }
    public List<Vehicule> getPlusPetiteCapacite(List<Reservation> reservations) throws SQLException {
        List<Vehicule> reponse = new ArrayList<>();
        Vehicule v= new Vehicule();
        // int minimum_capacite = v.getNbPlaceReservees();
        // System.out.println("vehicule debut"+ id_vehicule_debut);
        VehiculeDAO v_dao = new VehiculeDAO();

        List<Vehicule> vehicules = v_dao.findAll();
        int id_vehicule_debut = vehicules.get(0).getId();
        int minimum_capacite = vehicules.get(0).getNbPlaceReservees();

        // for (Vehicule reservation : reservations) {

        for (Vehicule vehicule : vehicules) {
            int capaciteRestante = vehicule.getNombrePlace()- vehicule.getNbPlaceReservees();

            // if (capaciteRestante<0 ){
            //     System.out.println("beugggggg");
            // }
            if (capaciteRestante< minimum_capacite ) {
                if(vehicule.getId() != id_vehicule_debut) {
                minimum_capacite = capaciteRestante;
                }
            }
        }
        System.out.println("voici le vehicule ayant la plus petice capactie suffisante: "+ minimum_capacite);
        


    for (Vehicule vehicule : vehicules) {


            int capaciteRestante = vehicule.getNombrePlace()- vehicule.getNbPlaceReservees();
            System.out.println("capacite restante du vehicule "+ vehicule.getReference() + " : " + capaciteRestante);
            if (capaciteRestante== minimum_capacite ) {
                // if(vehicule.getId() != id_vehicule_debut) {
                    
                    System.out.println("vehiculeeeee"+ vehicule.getId());
                VehiculeDAO vehicule_final = new VehiculeDAO();

                Vehicule v2 = vehicule_final.findById(vehicule.getId());
               
            
                reponse.add(v2);
               
                // }
            }
        }
        return reponse;
        // Vehicule v = new Vehicule();
        // return v;
    }
    public void updateAssignationVehicule(int idReservation, int idVehicule) throws SQLException {
        String sql = "UPDATE reservation SET id_vehicule = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idVehicule);
            ps.setInt(2, idReservation);
            ps.executeUpdate();
        }
    }
}
