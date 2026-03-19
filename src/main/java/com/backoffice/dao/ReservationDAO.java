package com.backoffice.dao;

import com.backoffice.database.DBConnection;
import com.backoffice.model.Reservation;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class ReservationDAO {

    /**
     * Retourne la derniere date (sans l'heure) trouvee dans reservation.
     * Peut retourner null si aucune reservation n'existe.
     */
    public java.sql.Date getDerniereDateReservation() throws SQLException {
        String sql = "SELECT DATE(MAX(date_heure_arrivee)) AS derniere_date FROM reservation";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getDate("derniere_date");
            }
        }
        return null;
    }

    // INSERT
    public void insert(Reservation reservation) throws SQLException {
        String sql = "INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, reservation.getClient());
            ps.setInt(2, reservation.getNombrePassager());
            ps.setTimestamp(3, reservation.getDateHeureArrivee());
            ps.setInt(4, reservation.getIdHotel());
            
            if (reservation.getIdVehicule() != null) {
                ps.setInt(5, reservation.getIdVehicule());
            } else {
                ps.setNull(5, Types.INTEGER);
            }
            
            ps.executeUpdate();
            
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                reservation.setId(rs.getInt(1));
            }
        }
    }

    // UPDATE
    public void update(Reservation reservation) throws SQLException {
        String sql = "UPDATE reservation SET client = ?, nombre_passager = ?, date_heure_arrivee = ?, id_hotel = ?, id_vehicule = ? WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reservation.getClient());
            ps.setInt(2, reservation.getNombrePassager());
            ps.setTimestamp(3, reservation.getDateHeureArrivee());
            ps.setInt(4, reservation.getIdHotel());
            
            if (reservation.getIdVehicule() != null) {
                ps.setInt(5, reservation.getIdVehicule());
            } else {
                ps.setNull(5, Types.INTEGER);
            }
            
            ps.setInt(6, reservation.getId());
            ps.executeUpdate();
        }
    }

    // ASSIGNER UN VEHICULE
    public void assignVehicule(int reservationId, int vehiculeId) throws SQLException {
        String sqlUpdate = "UPDATE reservation SET id_vehicule = ? WHERE id = ?";
        String sqlSelect = "SELECT nombre_passager, date_heure_arrivee FROM reservation WHERE id = ?";

        try (Connection conn = DBConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(sqlUpdate)) {
                ps.setInt(1, vehiculeId);
                ps.setInt(2, reservationId);
                ps.executeUpdate();
            }

            // Journaliser systematiquement l'assignation dans reservation_vehicule.
            ensureReservationVehiculeTable();

            int nbPassagers = 0;
            Timestamp dateAssignation = null;
            try (PreparedStatement psSelect = conn.prepareStatement(sqlSelect)) {
                psSelect.setInt(1, reservationId);
                try (ResultSet rs = psSelect.executeQuery()) {
                    if (rs.next()) {
                        nbPassagers = rs.getInt("nombre_passager");
                        dateAssignation = rs.getTimestamp("date_heure_arrivee");
                    }
                }
            }

            if (nbPassagers > 0) {
                upsertReservationVehicule(reservationId, vehiculeId, nbPassagers, dateAssignation);
            }
        }
    }

    // METTRE A JOUR L'HEURE D'ARRIVEE (utilisee ici comme heure effective de prise en charge apres report)
    public void updateDateHeureArrivee(int reservationId, Timestamp dateHeureArrivee) throws SQLException {
        String sql = "UPDATE reservation SET date_heure_arrivee = ? WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, dateHeureArrivee);
            ps.setInt(2, reservationId);
            ps.executeUpdate();
        }
    }

    // FIND ALL (avec jointures)
    public List<Reservation> findAll() throws SQLException {
        List<Reservation> reservations = new ArrayList<>();
        String sql = "SELECT r.id, r.client, r.nombre_passager, r.date_heure_arrivee, r.id_hotel, r.id_vehicule, " +
                     "h.nom AS nom_hotel, v.reference AS reference_vehicule " +
                     "FROM reservation r " +
                     "JOIN hotel h ON r.id_hotel = h.id_hotel " +
                     "LEFT JOIN vehicule v ON r.id_vehicule = v.id " +
                     "ORDER BY r.date_heure_arrivee DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Reservation res = mapResultSet(rs);
                reservations.add(res);
            }
        }
        return reservations;
    }
    // public List<Reservation> findByVehiculeId(int vehiculeId) throws SQLException {
    //     List<Reservation> reservations = new ArrayList<>();
    //     String sql = "SELECT r.id, r.client, r.nombre_passager, r.date_heure_arrivee, r.id_hotel, r.id_vehicule, " +
    //                  "h.nom AS nom_hotel, v.reference AS reference_vehicule " +
    //                  "FROM reservation r " +
    //                  "JOIN hotel h ON r.id_hotel = h.id_hotel " +
    //                  "LEFT JOIN vehicule v ON r.id_vehicule = v.id " +
    //                  "WHERE r.id_vehicule = ? " +
    //                  "ORDER BY r.date_heure_arrivee DESC";

    //     return reservation;
    // }
    // FIND BY ID
    public Reservation findById(int id) throws SQLException {
        String sql = "SELECT r.id, r.client, r.nombre_passager, r.date_heure_arrivee, r.id_hotel, r.id_vehicule, " +
                     "h.nom AS nom_hotel, v.reference AS reference_vehicule " +
                     "FROM reservation r " +
                     "JOIN hotel h ON r.id_hotel = h.id_hotel " +
                     "LEFT JOIN vehicule v ON r.id_vehicule = v.id " +
                     "WHERE r.id = ?";
        
        Reservation reservation = null;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    reservation = mapResultSet(rs);
                }
            }
        }
        return reservation;
    }

    // FIND BY DATE
    public List<Reservation> findByDate(java.sql.Date date) throws SQLException {
        List<Reservation> reservations = new ArrayList<>();
        String sql = "SELECT r.id, r.client, r.nombre_passager, r.date_heure_arrivee, r.id_hotel, r.id_vehicule, " +
                     "h.nom AS nom_hotel, v.reference AS reference_vehicule " +
                     "FROM reservation r " +
                     "JOIN hotel h ON r.id_hotel = h.id_hotel " +
                     "LEFT JOIN vehicule v ON r.id_vehicule = v.id " +
                     "WHERE DATE(r.date_heure_arrivee) = ? " +
                     "ORDER BY r.date_heure_arrivee";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, date);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Reservation res = mapResultSet(rs);
                    reservations.add(res);
                }
            }
        }
        return reservations;
    }

    // FIND RESERVATIONS SANS VEHICULE
    public List<Reservation> findWithoutVehicule() throws SQLException {
        List<Reservation> reservations = new ArrayList<>();
        String sql = "SELECT r.id, r.client, r.nombre_passager, r.date_heure_arrivee, r.id_hotel, r.id_vehicule, " +
                     "h.nom AS nom_hotel " +
                     "FROM reservation r " +
                     "JOIN hotel h ON r.id_hotel = h.id_hotel " +
                     "WHERE r.id_vehicule IS NULL " +
                     "ORDER BY r.date_heure_arrivee";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Reservation res = mapResultSet(rs);
                reservations.add(res);
            }
        }
        return reservations;
    }

    // FIND RESERVATIONS SANS VEHICULE PAR DATE (triées par nombre de passagers décroissant)
    public List<Reservation> findWithoutVehiculeByDate(java.sql.Date date) throws SQLException {
        List<Reservation> reservations = new ArrayList<>();
        String sql = "SELECT r.id, r.client, r.nombre_passager, r.date_heure_arrivee, r.id_hotel, r.id_vehicule, " +
                     "h.nom AS nom_hotel " +
                     "FROM reservation r " +
                     "JOIN hotel h ON r.id_hotel = h.id_hotel " +
                     "WHERE r.id_vehicule IS NULL AND DATE(r.date_heure_arrivee) = ? " +
                     "ORDER BY r.nombre_passager DESC, r.date_heure_arrivee";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, date);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Reservation res = mapResultSet(rs);
                    reservations.add(res);
                }
            }
        }
        return reservations;
    }

    // DELETE
    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM reservation WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // Méthode utilitaire pour mapper un ResultSet en Reservation
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



    /* R0 select * from reservation order by nombre_passager desc; */
    public List<Reservation> getReservations(java.sql.Date date) throws SQLException {
        List<Reservation> reservations = new ArrayList<>();
        String sql = "SELECT r.id, r.client, r.nombre_passager, r.date_heure_arrivee, r.id_hotel, r.id_vehicule, " +
                     "h.nom AS nom_hotel " +
                     "FROM reservation r " +
                     "JOIN hotel h ON r.id_hotel = h.id_hotel " +
                     "WHERE DATE(r.date_heure_arrivee) = ? " +
                     "ORDER BY r.nombre_passager DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, date);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Reservation res = mapResultSet(rs);
                    reservations.add(res);
                }
            }
        }
        return reservations;
    }
    /* R1 get vehicules assigne */
    //  public List<Reservation> getReservationsDejaAssigne(java.sql.Date date) throws SQLException {
    //     List<Reservation> reservations = new ArrayList<>();
    //     String sql = "SELECT r.id, r.client, r.nombre_passager, r.date_heure_arrivee, r.id_hotel, r.id_vehicule, " +
    //                  "h.nom AS nom_hotel " +
    //                  "FROM reservation r " +
    //                  "JOIN hotel h ON r.id_hotel = h.id_hotel " +
    //                  "WHERE id_vehicule IS NOT NULL AND DATE(r.date_heure_arrivee) = ? " +
    //                  "ORDER BY r.n ombre_passager DESC";

    //     try (Connection conn = DBConnection.getConnection();
    //          PreparedStatement ps = conn.prepareStatement(sql)) {
    //         ps.setDate(1, date);
    //         try (ResultSet rs = ps.executeQuery()) {
    //             while (rs.next()) {
    //                 Reservation res = mapResultSet(rs);
    //                 reservations.add(res);
    //             }
    //         }
    //     }
    //     return reservations;
    // }
    // DAO - remplace la méthode getReservationsDejaAssigne(...)
    public List<Reservation> getReservationsDejaAssigne(java.sql.Date date) throws SQLException {
        List<Reservation> reservations = new ArrayList<>();
        String sql =
            "SELECT r.id, r.client, r.nombre_passager, r.date_heure_arrivee, r.id_hotel, r.id_vehicule, " +
            "       h.nom AS nom_hotel, v.reference AS reference_vehicule " +
            "FROM reservation r " +
            "JOIN hotel h ON r.id_hotel = h.id_hotel " +
            "JOIN vehicule v ON r.id_vehicule = v.id " +
            "WHERE r.id_vehicule IS NOT NULL " +
            "  AND DATE(r.date_heure_arrivee) = ? " +
            "ORDER BY r.nombre_passager DESC, r.date_heure_arrivee ASC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, date);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    reservations.add(mapResultSet(rs));
                }
            }
        }
        return reservations;
    }

    /**
     * Cree la table de liaison reservation_vehicule si elle n'existe pas.
     */
    public void ensureReservationVehiculeTable() throws SQLException {
        String sql =
            "CREATE TABLE IF NOT EXISTS reservation_vehicule (" +
            "    id SERIAL PRIMARY KEY," +
            "    reservation_id INTEGER NOT NULL REFERENCES reservation(id) ON DELETE CASCADE," +
            "    vehicule_id INTEGER NOT NULL REFERENCES vehicule(id)," +
            "    nb_passagers INTEGER NOT NULL CHECK (nb_passagers > 0)," +
            "    date_assignation TIMESTAMP NOT NULL DEFAULT NOW()," +
            "    UNIQUE (reservation_id, vehicule_id)" +
            ")";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }

    /**
     * Journalise une assignation dans la table reservation_vehicule.
     */
    public void upsertReservationVehicule(int reservationId, int vehiculeId, int nbPassagers, Timestamp dateAssignation) throws SQLException {
        String sql =
            "INSERT INTO reservation_vehicule (reservation_id, vehicule_id, nb_passagers, date_assignation) " +
            "VALUES (?, ?, ?, ?) " +
            "ON CONFLICT (reservation_id, vehicule_id) DO UPDATE SET " +
            "nb_passagers = EXCLUDED.nb_passagers, " +
            "date_assignation = EXCLUDED.date_assignation";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, reservationId);
            ps.setInt(2, vehiculeId);
            ps.setInt(3, nbPassagers);
            ps.setTimestamp(4, dateAssignation != null ? dateAssignation : new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        }
    }

    /**
     * Synchronise reservation_vehicule a partir de reservation.id_vehicule pour une date.
     */
    public void synchroniserReservationVehiculeDepuisReservation(java.sql.Date date) throws SQLException {
        String sql =
            "INSERT INTO reservation_vehicule (reservation_id, vehicule_id, nb_passagers, date_assignation) " +
            "SELECT r.id, r.id_vehicule, r.nombre_passager, r.date_heure_arrivee " +
            "FROM reservation r " +
            "WHERE r.id_vehicule IS NOT NULL AND DATE(r.date_heure_arrivee) = ? " +
            "ON CONFLICT (reservation_id, vehicule_id) DO UPDATE SET " +
            "nb_passagers = EXCLUDED.nb_passagers, " +
            "date_assignation = EXCLUDED.date_assignation";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, date);
            ps.executeUpdate();
        }
    }

    /**
     * Liste les assignations reservation_vehicule pour l'affichage Sprint 7.
     */
    public List<Map<String, Object>> getReservationVehiculeByDate(java.sql.Date date) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql =
            "SELECT rv.reservation_id, rv.vehicule_id, rv.nb_passagers, rv.date_assignation, " +
            "       r.client, r.date_heure_arrivee, h.nom AS nom_hotel, v.reference AS reference_vehicule " +
            "FROM reservation_vehicule rv " +
            "JOIN reservation r ON r.id = rv.reservation_id " +
            "JOIN vehicule v ON v.id = rv.vehicule_id " +
            "JOIN hotel h ON h.id_hotel = r.id_hotel " +
            "WHERE DATE(r.date_heure_arrivee) = ? " +
            "ORDER BY rv.date_assignation, rv.reservation_id";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, date);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("reservationId", rs.getInt("reservation_id"));
                    row.put("vehiculeId", rs.getInt("vehicule_id"));
                    row.put("nbPassagers", rs.getInt("nb_passagers"));
                    row.put("dateAssignation", rs.getTimestamp("date_assignation"));
                    row.put("client", rs.getString("client"));
                    row.put("dateHeureArrivee", rs.getTimestamp("date_heure_arrivee"));
                    row.put("nomHotel", rs.getString("nom_hotel"));
                    row.put("referenceVehicule", rs.getString("reference_vehicule"));
                    rows.add(row);
                }
            }
        }

        return rows;
    }

    /**
     * Compte le nombre de traces dans reservation_vehicule pour une date donnee.
     */
    public int countReservationVehiculeByDate(java.sql.Date date) throws SQLException {
        String sql =
            "SELECT COUNT(*) AS nb " +
            "FROM reservation_vehicule rv " +
            "JOIN reservation r ON r.id = rv.reservation_id " +
            "WHERE DATE(r.date_heure_arrivee) = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, date);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("nb");
                }
            }
        }

        return 0;
    }

    /**
     * Compte le nombre de trajets deja assignes par vehicule pour une date donnee.
     */
    public Map<Integer, Integer> getNombreTrajetsParVehicule(java.sql.Date date) throws SQLException {
        Map<Integer, Integer> trajetsParVehicule = new HashMap<>();

        String sql =
            // Un trajet = un depart (date_heure_arrivee) pour un vehicule sur la journee.
            "SELECT id_vehicule, COUNT(DISTINCT date_heure_arrivee) AS nb_trajets " +
            "FROM reservation " +
            "WHERE id_vehicule IS NOT NULL AND DATE(date_heure_arrivee) = ? " +
            "GROUP BY id_vehicule";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, date);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    trajetsParVehicule.put(rs.getInt("id_vehicule"), rs.getInt("nb_trajets"));
                }
            }
        }

        return trajetsParVehicule;
    }

    /**
     * Supprime les traces reservation_vehicule pour une date donnee.
     */
    public void deleteReservationVehiculeByDate(java.sql.Date date) throws SQLException {
        String sql =
            "DELETE FROM reservation_vehicule rv " +
            "USING reservation r " +
            "WHERE rv.reservation_id = r.id " +
            "AND DATE(r.date_heure_arrivee) = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, date);
            ps.executeUpdate();
        }
    }

    /**
     * Reinitialise les assignations vehicules d'une date (id_vehicule -> NULL).
     */
    public void resetAssignationsByDate(java.sql.Date date) throws SQLException {
        String sql =
            "UPDATE reservation " +
            "SET id_vehicule = NULL " +
            "WHERE DATE(date_heure_arrivee) = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, date);
            ps.executeUpdate();
        }
    }
}
