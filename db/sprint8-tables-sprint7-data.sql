-- =========================================
-- SPRINT 8 TABLES + SPRINT 7 DATASET
-- BackOffice / PostgreSQL
-- Date cible dataset: 2026-03-11
-- =========================================

-- 1) DROP objets
DROP VIEW IF EXISTS v_historique_assignation CASCADE;
DROP TABLE IF EXISTS reservation_vehicule CASCADE;
DROP TABLE IF EXISTS reservation CASCADE;
DROP TABLE IF EXISTS distance CASCADE;
DROP TABLE IF EXISTS lieu CASCADE;
DROP TABLE IF EXISTS parametre CASCADE;
DROP TABLE IF EXISTS vehicule CASCADE;
DROP TABLE IF EXISTS hotel CASCADE;
DROP TABLE IF EXISTS token CASCADE;

-- 2) CREATE tables (structure Sprint 8)
CREATE TABLE hotel (
    id_hotel SERIAL PRIMARY KEY,
    nom VARCHAR(150) NOT NULL UNIQUE
);

CREATE TABLE vehicule (
    id SERIAL PRIMARY KEY,
    reference VARCHAR(50) NOT NULL UNIQUE,
    nombre_place INTEGER NOT NULL CHECK (nombre_place > 0),
    type_carburant VARCHAR(2) NOT NULL CHECK (type_carburant IN ('D', 'ES', 'H', 'EL')),
    heure_disponibilite TIME
);

CREATE TABLE parametre (
    id SERIAL PRIMARY KEY,
    cle VARCHAR(100) UNIQUE NOT NULL,
    valeur NUMERIC(10,2) NOT NULL,
    unite VARCHAR(50)
);

CREATE TABLE lieu (
    id SERIAL PRIMARY KEY,
    code VARCHAR(10) UNIQUE NOT NULL,
    libelle VARCHAR(150) NOT NULL
);

CREATE TABLE distance (
    id SERIAL PRIMARY KEY,
    from_id VARCHAR(20),
    to_id VARCHAR(20),
    kilometer NUMERIC(10,2) NOT NULL CHECK (kilometer >= 0)
);

CREATE TABLE reservation (
    id SERIAL PRIMARY KEY,
    client VARCHAR(200) NOT NULL,
    nombre_passager INTEGER NOT NULL CHECK (nombre_passager > 0),
    date_heure_arrivee TIMESTAMP NOT NULL,
    id_hotel INTEGER NOT NULL REFERENCES hotel(id_hotel),
    id_vehicule INTEGER REFERENCES vehicule(id)
);

CREATE TABLE reservation_vehicule (
    id SERIAL PRIMARY KEY,
    reservation_id INTEGER NOT NULL REFERENCES reservation(id) ON DELETE CASCADE,
    vehicule_id INTEGER NOT NULL REFERENCES vehicule(id),
    nb_passagers INTEGER NOT NULL CHECK (nb_passagers > 0),
    date_assignation TIMESTAMP NOT NULL DEFAULT NOW(),
    mode_assignation VARCHAR(20) NOT NULL DEFAULT 'NORMAL_TA'
        CHECK (mode_assignation IN ('NORMAL_TA', 'RETOUR_IMMEDIAT')),
    CONSTRAINT uq_rv UNIQUE (reservation_id, vehicule_id)
);

CREATE TABLE token (
    id SERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    date_heure_expiration TIMESTAMP NOT NULL
);

CREATE INDEX idx_token_value ON token(token);
CREATE INDEX idx_token_expiration ON token(date_heure_expiration);
CREATE INDEX idx_rv_mode_assignation ON reservation_vehicule(mode_assignation);

-- 3) Donnees de reference
INSERT INTO hotel (nom) VALUES
    ('Colbert'),
    ('Novotel'),
    ('Ibis'),
    ('Lokanga'),
    ('Carlton'),
    ('Panorama');

INSERT INTO lieu (code, libelle) VALUES
    ('TNR', 'Antananarivo - Ivato');

INSERT INTO parametre (cle, valeur, unite) VALUES
    ('TA', 30, 'minutes'),
    ('VITESSE_MOYENNE', 30, 'km/h');

-- Distances aeroport -> hotels
INSERT INTO distance (from_id, to_id, kilometer) VALUES
    ('TNR', '1', 15.0),
    ('TNR', '2', 22.5),
    ('TNR', '3', 18.0),
    ('TNR', '4', 30.0),
    ('TNR', '5', 18.0),
    ('TNR', '6', 15.0);

-- Distances inter-hotels (minimum utile)
INSERT INTO distance (from_id, to_id, kilometer) VALUES
    ('1', '2', 8.0),
    ('1', '3', 5.0),
    ('1', '4', 16.0),
    ('1', '5', 5.0),
    ('1', '6', 3.0),
    ('2', '3', 6.0),
    ('2', '4', 10.0),
    ('2', '5', 6.0),
    ('2', '6', 9.0),
    ('3', '4', 14.0),
    ('3', '5', 2.0),
    ('3', '6', 6.0),
    ('4', '5', 14.0),
    ('4', '6', 17.0),
    ('5', '6', 6.0);

-- Flotte (capacites proches du scenario Sprint 7)
INSERT INTO vehicule (reference, nombre_place, type_carburant, heure_disponibilite) VALUES
    ('VH-001', 4, 'ES', '00:00:00'),
    ('VH-002', 4, 'D',  '00:00:00'),
    ('VH-003', 7, 'D',  '00:00:00'),
    ('VH-004', 7, 'ES', '00:00:00'),
    ('VH-005', 12, 'D', '00:00:00'),
    ('VH-006', 15, 'D', '00:00:00'),
    ('VH-007', 20, 'D', '00:00:00'),
    ('VH-008', 30, 'D', '00:00:00');

-- 4) Vue historique (utilisee par disponibilite + retours)
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
    ROUND((d.kilometer / p.valeur) * 60, 0)::INTEGER AS duree_aller_minutes,
    ROUND((d.kilometer / p.valeur) * 60 * 2, 0)::INTEGER AS duree_totale_minutes,
    r.date_heure_arrivee
        + (ROUND((d.kilometer / p.valeur) * 60 * 2, 0)::INTEGER * INTERVAL '1 minute')
        AS date_heure_retour
FROM reservation r
JOIN hotel h ON h.id_hotel = r.id_hotel
JOIN vehicule v ON v.id = r.id_vehicule
JOIN distance d ON d.from_id = 'TNR' AND d.to_id = CAST(r.id_hotel AS VARCHAR)
CROSS JOIN (SELECT valeur FROM parametre WHERE cle = 'VITESSE_MOYENNE') p
WHERE r.id_vehicule IS NOT NULL;

-- 5) Seeds Sprint 8: trajets deja assignes pour creer des evenements de retour
-- Ces retours sont visibles AVANT l'execution Sprint 7 et permettent de tester
-- DEPART_IMMEDIAT / REPORT_TA sur le backlog Sprint 7.
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'SEED-REPORT-0900', 1, TIMESTAMP '2026-03-11 07:00:00', h.id_hotel, v.id
FROM hotel h, vehicule v
WHERE h.nom = 'Lokanga' AND v.reference = 'VH-006';

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'SEED-IMMEDIAT-1000', 1, TIMESTAMP '2026-03-11 09:00:00', h.id_hotel, v.id
FROM hotel h, vehicule v
WHERE h.nom = 'Colbert' AND v.reference = 'VH-001';

-- 6) Donnees Sprint 7 (reservations non assignees)
-- Lancer ensuite POST /sprint8/executer?date=2026-03-11
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule) VALUES
    ('R1-Ana', 3, TIMESTAMP '2026-03-11 07:00:00', 1, NULL),
    ('R2-Bob', 2, TIMESTAMP '2026-03-11 08:30:00', 1, NULL),
    ('R3-Claire', 2, TIMESTAMP '2026-03-11 08:30:00', 3, NULL),
    ('R4-David', 2, TIMESTAMP '2026-03-11 08:30:00', 2, NULL),
    ('R5-Emma', 11, TIMESTAMP '2026-03-11 10:30:00', 4, NULL),
    ('R6-Francois', 2, TIMESTAMP '2026-03-11 10:30:00', 1, NULL),
    ('R7-Gabrielle', 5, TIMESTAMP '2026-03-11 12:00:00', 2, NULL),
    ('R8-Henri', 2, TIMESTAMP '2026-03-11 14:00:00', 5, NULL),
    ('R9-Isabelle', 2, TIMESTAMP '2026-03-11 14:00:00', 3, NULL),
    ('R10-Jean', 2, TIMESTAMP '2026-03-11 14:00:00', 4, NULL),
    ('R11-Kevin', 2, TIMESTAMP '2026-03-11 15:30:00', 1, NULL),
    ('R12-Lucie', 2, TIMESTAMP '2026-03-11 15:30:00', 6, NULL),
    ('R13-Marie', 3, TIMESTAMP '2026-03-11 16:00:00', 3, NULL),
    ('R14-Nicolas', 4, TIMESTAMP '2026-03-11 18:00:00', 1, NULL),
    ('R15-Olivier', 3, TIMESTAMP '2026-03-11 18:00:00', 2, NULL);

-- 7) Verification rapide
SELECT 'SPRINT 8 TABLES + SPRINT 7 DATA READY' AS status;
SELECT COUNT(*) AS nb_reservations_total FROM reservation;
SELECT COUNT(*) AS nb_non_assignees FROM reservation WHERE id_vehicule IS NULL;
SELECT vehicule, date_heure_arrivee, date_heure_retour
FROM v_historique_assignation
WHERE DATE(date_heure_arrivee) = DATE '2026-03-11'
ORDER BY date_heure_retour, vehicule;
