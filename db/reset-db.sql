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
    ('TNR', '1', 15.0), 
    -- ('1','2',20)  -- TNR -> Colbert: 15 km  => 30 min aller, 1h aller-retour
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
-- 5. SCENARIO DE TEST COMPLET (ANCIENNES + NOUVELLES FONCTIONNALITES)
-- =========================================
-- Date: 04 Mars 2026
-- Objectif: Tester:
--   - Assignation automatique avec chevauchements
--   - Priorité aux réservations avec plus de passagers
--   - Regroupement des clients à la même heure dans le même véhicule
--   - Ordre de dépose par nearest-neighbour
-- 
-- ===========================================
-- SCENARIO PRINCIPAL (créer via l'application)
-- ===========================================
--
-- == TEST REGROUPEMENT (même heure, hôtels différents) ==
-- 
-- Réservation A: 08:00 -> Colbert (15km), 2 passagers
-- Réservation B: 08:00 -> Novotel (22.5km), 3 passagers
-- Réservation C: 08:00 -> Ibis (18km), 2 passagers
--   Total: 7 passagers à 08:00
--   Attendu: VH-003 (7 places, Diesel) - REGROUPEMENT
--   Ordre de dépose (nearest-neighbour depuis TNR): 
--     1. Colbert (15km) 
--     2. Ibis (18km) 
--     3. Novotel (22.5km)
--   Retour basé sur Novotel (le plus loin): 22.5/30*60*2 = 90 min => retour 09:30
--
-- == TEST PRIORITE PASSAGERS (ordre décroissant) ==
--
-- Réservation D: 10:00 -> Lokanga (30km), 10 passagers
-- Réservation E: 10:00 -> Colbert (15km), 3 passagers
--   Total: 13 passagers à 10:00
--   VH-005 (12 places) trop petit pour regrouper (13 > 12)
--   => Assignation individuelle par ordre de passagers
--   D traité en premier (10 passagers): VH-005 (12 places, Diesel)
--   E traité ensuite (3 passagers): VH-002 (4 places, Diesel)
--   Retour D: 30/30*60*2 = 120 min => 12:00
--   Retour E: 15/30*60*2 = 60 min => 11:00
--
-- == TEST CHEVAUCHEMENT CLASSIQUE ==
--
-- Réservation F: 11:30 -> Novotel (22.5km), 5 passagers
--   VH-003 libre (retour 09:30 < 11:30)
--   VH-005 occupé (10:00-12:00), 11:30 chevauche
--   Attendu: VH-003 (7 places, Diesel)
--   Retour: 90 min => 13:00
--
-- Réservation G: 12:30 -> Ibis (18km), 6 passagers
--   VH-003 occupé (11:30-13:00), 12:30 chevauche
--   VH-005 libre (retour 12:00 < 12:30)
--   Attendu: VH-004 (7 places) car VH-003 occupé et besoin 6+ places
--   Retour: 72 min => 13:42
--
-- == TEST REGROUPEMENT AVEC CAPACITE JUSTE ==
--
-- Réservation H: 14:00 -> Colbert (15km), 2 passagers
-- Réservation I: 14:00 -> Lokanga (30km), 2 passagers
--   Total: 4 passagers à 14:00
--   Attendu: VH-002 (4 places, Diesel) - REGROUPEMENT parfait
--   Ordre de dépose: 1. Colbert (15km), 2. Lokanga (30km)
--   Retour basé sur Lokanga: 120 min => 16:00

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
-- 7. RESERVATIONS DE TEST (sans véhicule - pour tester l'assignation)
-- =========================================
-- Décommentez pour créer les réservations directement en base

/*
-- Test regroupement à 08:00 (total 7 passagers)
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('Alice Martin', 2, '2026-03-04 08:00:00', 1),     -- Colbert
    ('Bob Dupont', 3, '2026-03-04 08:00:00', 2),       -- Novotel  
    ('Claire Durand', 2, '2026-03-04 08:00:00', 3);    -- Ibis

-- Test priorité passagers à 10:00 (pas de regroupement possible)
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('David Bernard', 10, '2026-03-04 10:00:00', 4),   -- Lokanga (traité en 1er)
    ('Emma Petit', 3, '2026-03-04 10:00:00', 1);       -- Colbert (traité en 2ème)

-- Test chevauchement
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('François Moreau', 5, '2026-03-04 11:30:00', 2),  -- Novotel
    ('Gabrielle Simon', 6, '2026-03-04 12:30:00', 3);  -- Ibis

-- Test regroupement capacité exacte à 14:00 (total 4 passagers)
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('Henri Laurent', 2, '2026-03-04 14:00:00', 1),    -- Colbert
    ('Isabelle Roux', 2, '2026-03-04 14:00:00', 4);    -- Lokanga
*/
