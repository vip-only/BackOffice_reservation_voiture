-- =========================================
-- SPRINT 3 - VUE HISTORIQUE ASSIGNATION
-- Date: 03-04-2026
-- =========================================

-- =========================================
-- DONNEES DE TEST - DISTANCES (Aéroport -> Hôtels)
-- from_id = code aéroport, to_id = id_hotel
-- =========================================

DELETE FROM distance;

-- Distances depuis l'aéroport TNR vers les hôtels
INSERT INTO distance (from_id, to_id, kilometer) VALUES ('TNR', '1', 15.5);   -- TNR -> Colbert (15.5 km)
INSERT INTO distance (from_id, to_id, kilometer) VALUES ('TNR', '2', 22.0);   -- TNR -> Novotel (22 km)
INSERT INTO distance (from_id, to_id, kilometer) VALUES ('TNR', '3', 18.3);   -- TNR -> Ibis (18.3 km)
INSERT INTO distance (from_id, to_id, kilometer) VALUES ('TNR', '4', 25.7);   -- TNR -> Lokanga (25.7 km)


-- =========================================
-- DONNEES DE TEST - RESERVATIONS AVEC VEHICULE ASSIGNE
-- =========================================

DELETE FROM reservation;

-- Réservations avec véhicule assigné (pour la vue historique)
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule) VALUES 
-- ('Jean Dupont', 2, '2026-03-01 08:30:00', 1, 3),        -- Colbert, VH-001
-- ('Marie Martin', 4, '2026-03-01 10:00:00', 2, 2),       -- Novotel, VH-002
('Pierre Bernard', 1, '2026-03-02 14:15:00', 3, 3),     -- Ibis, VH-003
('Sophie Durand', 3, '2026-03-02 16:45:00', 4, 4),      -- Lokanga, VH-004
('Luc Moreau', 5, '2026-03-03 09:00:00', 1, 5),         -- Colbert, VH-005
('Emma Leroy', 8, '2026-03-03 11:30:00', 2, 6),         -- Novotel, VH-006
('Paul Roux', 1, '2026-03-04 07:00:00', 3, 7),         -- Ibis, VH-007
('Claire Simon', 1, '2026-03-04 13:00:00', 4, 8);      -- Lokanga, VH-008

-- Réservations SANS véhicule assigné (ne doivent PAS apparaître dans la vue)
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule) VALUES 
('Thomas Blanc', 2, '2026-03-05 08:00:00', 1, NULL),
('Julie Noir', 3, '2026-03-05 10:00:00', 2, NULL);


-- =========================================
-- VUE HISTORIQUE ASSIGNATION
-- =========================================
-- Affiche les réservations avec véhicule assigné
-- Calcule la date/heure de retour:
--   - Durée aller = distance / vitesse_moyenne
--   - Durée retour = distance / vitesse_moyenne  
--   - Date retour = date_heure_arrivee + (2 * durée trajet)
-- SANS prendre en compte le temps d'attente
-- =========================================

DROP VIEW IF EXISTS v_historique_assignation;

CREATE VIEW v_historique_assignation AS
SELECT 
    r.id AS reservation_id,
    r.client,
    r.nombre_passager,
    r.date_heure_arrivee,
    h.nom AS hotel,
    v.id AS vehicule_id,
    v.reference AS vehicule,
    v.nombre_place AS capacite_vehicule,
    d.kilometer AS distance_km,
    p.valeur AS vitesse_moyenne_kmh,
    
    -- Durée aller en heures (distance / vitesse)
    ROUND((d.kilometer / p.valeur), 2) AS duree_aller_heures,
    
    -- Durée aller en minutes
    ROUND((d.kilometer / p.valeur) * 60, 0)::INTEGER AS duree_aller_minutes,
    
    -- Durée totale aller-retour en minutes (sans temps d'attente)
    ROUND((d.kilometer / p.valeur) * 60 * 2, 0)::INTEGER AS duree_totale_minutes,
    
    -- Date et heure de retour = arrivée + durée aller-retour
    r.date_heure_arrivee + (ROUND((d.kilometer / p.valeur) * 60 * 2, 0)::INTEGER * INTERVAL '1 minute') AS date_heure_retour

FROM reservation r
INNER JOIN hotel h ON r.id_hotel = h.id_hotel
INNER JOIN vehicule v ON r.id_vehicule = v.id
INNER JOIN distance d ON d.from_id = 'TNR' AND d.to_id = CAST(r.id_hotel AS VARCHAR)
CROSS JOIN (SELECT valeur FROM parametre WHERE cle = 'VITESSE_MOYENNE') p
WHERE r.id_vehicule IS NOT NULL
ORDER BY r.date_heure_arrivee;


-- =========================================
-- VERIFICATION - SELECT SUR LA VUE
-- =========================================

-- Pour tester la vue:
-- SELECT * FROM v_historique_assignation;
