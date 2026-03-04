-- =========================================
-- SCRIPT DE REINITIALISATION COMPLETE
-- Date: 03-04-2026
-- Scénario de test pour l'assignation automatique
-- =========================================

-- =========================================
-- 1. SUPPRESSION DES TABLES (ordre inverse des dépendances)
-- =========================================
DROP VIEW IF EXISTS v_historique_assignation CASCADE;
DROP TABLE IF EXISTS reservation CASCADE;
DROP TABLE IF EXISTS distance CASCADE;
DROP TABLE IF EXISTS lieu CASCADE;
DROP TABLE IF EXISTS parametre CASCADE;
DROP TABLE IF EXISTS vehicule CASCADE;
DROP TABLE IF EXISTS hotel CASCADE;
DROP TABLE IF EXISTS token CASCADE;


-- =========================================
-- 2. CREATION DES TABLES
-- =========================================

-- TABLE HOTEL
CREATE TABLE hotel (
    id_hotel SERIAL PRIMARY KEY,
    nom VARCHAR(150) NOT NULL
);

-- TABLE VEHICULE
CREATE TABLE vehicule (
    id SERIAL PRIMARY KEY,
    reference VARCHAR(50) NOT NULL UNIQUE,
    nombre_place INTEGER NOT NULL CHECK (nombre_place > 0),
    type_carburant VARCHAR(2) NOT NULL CHECK (type_carburant IN ('D', 'ES', 'H', 'EL'))
);

-- TABLE PARAMETRE
CREATE TABLE parametre (
    id SERIAL PRIMARY KEY,
    cle VARCHAR(100) UNIQUE NOT NULL,
    valeur NUMERIC(10,2) NOT NULL,
    unite VARCHAR(50)
);

-- TABLE LIEU
CREATE TABLE lieu (
    id SERIAL PRIMARY KEY,
    code VARCHAR(10) UNIQUE NOT NULL,
    libelle VARCHAR(150) NOT NULL
);

-- TABLE DISTANCE
CREATE TABLE distance (
    id SERIAL PRIMARY KEY,
    from_id VARCHAR(20),
    to_id VARCHAR(20),
    kilometer NUMERIC(10,2) NOT NULL CHECK (kilometer >= 0)
);

-- TABLE RESERVATION
CREATE TABLE reservation (
    id SERIAL PRIMARY KEY,
    client VARCHAR(200) NOT NULL,
    nombre_passager INTEGER,
    date_heure_arrivee TIMESTAMP NOT NULL,
    id_hotel INTEGER NOT NULL,
    id_vehicule INTEGER,
    CONSTRAINT fk_hotel FOREIGN KEY (id_hotel) REFERENCES hotel(id_hotel),
    CONSTRAINT fk_vehicule FOREIGN KEY (id_vehicule) REFERENCES vehicule(id)
);

-- TABLE TOKEN
CREATE TABLE token (
    id SERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    date_heure_expiration TIMESTAMP NOT NULL
);

CREATE INDEX idx_token_value ON token(token);
CREATE INDEX idx_token_expiration ON token(date_heure_expiration);


-- =========================================
-- 3. INSERTION DES DONNEES DE REFERENCE
-- =========================================

-- Hotels
INSERT INTO hotel (nom) VALUES 
    ('Colbert'),
    ('Novotel'),
    ('Ibis'),
    ('Lokanga');

-- Lieux (aéroports, gares, etc.)
INSERT INTO lieu (code, libelle) VALUES 
    ('TNR', 'Antananarivo - Ivato');

-- Paramètres
INSERT INTO parametre (cle, valeur, unite) VALUES
    ('TA', 30, 'minutes'),              -- Temps d'attente (non utilisé dans le calcul)
    ('VITESSE_MOYENNE', 30, 'km/h');    -- Vitesse moyenne

-- Distances (TNR vers chaque hôtel)
-- from_id = 'TNR', to_id = id_hotel (en VARCHAR)
INSERT INTO distance (from_id, to_id, kilometer) VALUES 
    ('TNR', '1', 15.0),   -- TNR -> Colbert: 15 km  => 30 min aller, 1h aller-retour
    ('TNR', '2', 22.5),   -- TNR -> Novotel: 22.5 km => 45 min aller, 1h30 aller-retour
    ('TNR', '3', 18.0),   -- TNR -> Ibis: 18 km => 36 min aller, 1h12 aller-retour
    ('TNR', '4', 30.0);   -- TNR -> Lokanga: 30 km => 1h aller, 2h aller-retour

-- Véhicules (triés par capacité et type carburant)
INSERT INTO vehicule (reference, nombre_place, type_carburant) VALUES
    ('VH-001', 4, 'ES'),    -- Petite voiture essence
    ('VH-002', 4, 'D'),     -- Petite voiture diesel (préférée si 4 places demandées)
    ('VH-003', 7, 'D'),     -- Monospace diesel
    ('VH-004', 7, 'ES'),    -- Monospace essence
    ('VH-005', 12, 'D'),    -- Minibus diesel
    ('VH-006', 15, 'D'),    -- Bus diesel
    ('VH-007', 20, 'D'),    -- Grand bus diesel
    ('VH-008', 30, 'D');    -- Très grand bus diesel


-- =========================================
-- 4. VUE HISTORIQUE ASSIGNATION
-- =========================================

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
    ROUND((d.kilometer / p.valeur), 2) AS duree_aller_heures,
    ROUND((d.kilometer / p.valeur) * 60, 0)::INTEGER AS duree_aller_minutes,
    ROUND((d.kilometer / p.valeur) * 60 * 2, 0)::INTEGER AS duree_totale_minutes,
    r.date_heure_arrivee + (ROUND((d.kilometer / p.valeur) * 60 * 2, 0)::INTEGER * INTERVAL '1 minute') AS date_heure_retour
FROM reservation r
INNER JOIN hotel h ON r.id_hotel = h.id_hotel
INNER JOIN vehicule v ON r.id_vehicule = v.id
INNER JOIN distance d ON d.from_id = 'TNR' AND d.to_id = CAST(r.id_hotel AS VARCHAR)
CROSS JOIN (SELECT valeur FROM parametre WHERE cle = 'VITESSE_MOYENNE') p
WHERE r.id_vehicule IS NOT NULL
ORDER BY r.date_heure_arrivee;


-- =========================================
-- 5. SCENARIO DE TEST POUR ASSIGNATION AUTOMATIQUE
-- =========================================
-- Date: 04 Mars 2026
-- Objectif: Tester l'assignation automatique avec chevauchements
-- 
-- SCENARIO (créer via l'application dans CET ORDRE):
-- 
-- Réservation 1: 08:00 -> Colbert (15km), 3 passagers
--   Durée: 1h aller-retour => retour 09:00
--   Attendu: VH-002 (4 places, Diesel préféré)
--
-- Réservation 2: 08:30 -> Novotel (22.5km), 3 passagers  
--   Durée: 1h30 aller-retour => retour 10:00
--   VH-002 occupé (08:00-09:00), 08:30 chevauche => conflit
--   Attendu: VH-001 (4 places, Essence - VH-002 occupé)
--
-- Réservation 3: 09:30 -> Ibis (18km), 3 passagers
--   Durée: 1h12 aller-retour => retour 10:42
--   VH-002 libre (retour 09:00 < 09:30)
--   VH-001 occupé (08:30-10:00), 09:30 chevauche => conflit
--   Attendu: VH-002 (4 places, Diesel - libre)
--
-- Réservation 4: 10:00 -> Lokanga (30km), 6 passagers
--   Durée: 2h aller-retour => retour 12:00
--   Besoin 6+ places => VH-003 ou VH-004 (7 places)
--   Attendu: VH-003 (7 places, Diesel préféré)
--
-- Réservation 5: 10:30 -> Colbert (15km), 6 passagers
--   Durée: 1h aller-retour => retour 11:30
--   VH-003 occupé (10:00-12:00), 10:30 chevauche => conflit
--   Attendu: VH-004 (7 places, Essence - VH-003 occupé)
-- =========================================

-- PAS D'INSERTION DE RESERVATIONS ICI !
-- Les réservations doivent être créées via l'application pour tester l'assignation automatique


-- =========================================
-- 6. VERIFICATION DE LA VUE (après création des réservations)
-- =========================================

-- Afficher l'historique des assignations
-- SELECT 
--     reservation_id,
--     client,
--     nombre_passager,
--     date_heure_arrivee,
--     hotel,
--     vehicule,
--     capacite_vehicule,
--     distance_km,
--     duree_totale_minutes || ' min' AS duree_trajet,
--     date_heure_retour
-- FROM v_historique_assignation
-- ORDER BY date_heure_arrivee;


-- =========================================
-- 7. SI VOUS VOULEZ TESTER AVEC DES DONNEES PRE-EXISTANTES
-- =========================================
-- Décommentez la section ci-dessous pour avoir des réservations pré-assignées
-- et tester des réservations SUPPLEMENTAIRES

/*
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule) VALUES
    ('Jean Dupont', 3, '2026-03-04 08:00:00', 1, 2),      -- VH-002 (Diesel 4 places)
    ('Marie Martin', 3, '2026-03-04 08:30:00', 2, 1),     -- VH-001 (Essence 4 places)
    ('Pierre Bernard', 3, '2026-03-04 09:30:00', 3, 2),   -- VH-002 (Diesel)
    ('Sophie Durand', 6, '2026-03-04 10:00:00', 4, 3),    -- VH-003 (Diesel 7 places)
    ('Luc Moreau', 6, '2026-03-04 10:30:00', 1, 4);       -- VH-004 (Essence)
*/
