-- =========================================
-- SCRIPT COMPLET - SPRINT 8 (+ couverture Sprint 7)
-- Style "s3-sprint7.sql": DROP + CREATE + DATASET RICHE
-- Date de service cible: 2026-03-11
-- =========================================

-- =========================================
-- 1) DROP (ordre inverse des dependances)
-- =========================================
DROP VIEW IF EXISTS v_historique_assignation CASCADE;
DROP TABLE IF EXISTS reservation_vehicule CASCADE;
DROP TABLE IF EXISTS reservation CASCADE;
DROP TABLE IF EXISTS distance CASCADE;
DROP TABLE IF EXISTS lieu CASCADE;
DROP TABLE IF EXISTS parametre CASCADE;
DROP TABLE IF EXISTS vehicule CASCADE;
DROP TABLE IF EXISTS hotel CASCADE;
DROP TABLE IF EXISTS token CASCADE;

-- =========================================
-- 2) CREATE schema
-- =========================================
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
CREATE INDEX idx_rv_mode ON reservation_vehicule(mode_assignation);

-- =========================================
-- 3) Donnees de reference
-- =========================================
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

-- Flotte
-- Notes:
-- - VH-RET-8 et VH-RET-12 servent aux evenements de retour Sprint 8.
-- - VH-5-D-A / VH-5-ES-A testent l'egalite capacite + diesel.
-- - VH-5-D-BUSY simule un vehicule avec plus de trajets.
INSERT INTO vehicule (reference, nombre_place, type_carburant, heure_disponibilite) VALUES
    ('VH-RET-8', 8, 'D', '00:00:00'),
    ('VH-RET-12', 12, 'ES', '00:00:00'),
    ('VH-5-D-A', 5, 'D', '00:00:00'),
    ('VH-5-ES-A', 5, 'ES', '00:00:00'),
    ('VH-5-D-BUSY', 5, 'D', '00:00:00'),
    ('VH-7-D', 7, 'D', '00:00:00'),
    ('VH-10-ES', 10, 'ES', '00:00:00'),
    ('VH-12-D', 12, 'D', '00:00:00');

-- =========================================
-- 4) Vue historique (utilisee par disponibilite + retours)
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
    ROUND((d.kilometer / p.valeur) * 60, 0)::INTEGER AS duree_aller_minutes,
    ROUND((d.kilometer / p.valeur) * 60 * 2, 0)::INTEGER AS duree_totale_minutes,
    r.date_heure_arrivee +
      (ROUND((d.kilometer / p.valeur) * 60 * 2, 0)::INTEGER * INTERVAL '1 minute') AS date_heure_retour
FROM reservation r
JOIN hotel h ON h.id_hotel = r.id_hotel
JOIN vehicule v ON v.id = r.id_vehicule
JOIN distance d ON d.from_id = 'TNR' AND d.to_id = CAST(r.id_hotel AS VARCHAR)
CROSS JOIN (SELECT valeur FROM parametre WHERE cle = 'VITESSE_MOYENNE') p
WHERE r.id_vehicule IS NOT NULL;

-- =========================================
-- 5) DATASET TEST SPRINT 8 + SPRINT 7
-- =========================================
-- Date cible: 2026-03-11
--
-- Objectifs couverts:
--  A) Sprint 8 depart immediat (charge >= capacite)
--  B) Sprint 8 report TA (charge < capacite)
--  C) Sprint 7 fractionnement
--  D) Sprint 7 egalite capacite + nb trajets + diesel
--
-- IMPORTANT: apres execution de ce script, lancer /sprint8/executer?date=2026-03-11

-- ------------------------------------------------------------
-- Seed "deja assigne" pour creer des retours vehicules
-- ------------------------------------------------------------
-- VH-RET-8 revient a 10:00 (09:00 vers Colbert, AR=60min)
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'SEED-RET8-1000', 2, TIMESTAMP '2026-03-11 09:00:00', h.id_hotel, v.id
FROM hotel h, vehicule v
WHERE h.nom = 'Colbert' AND v.reference = 'VH-RET-8';

-- VH-RET-12 revient a 11:00 (09:00 vers Lokanga, AR=120min)
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'SEED-RET12-1100', 3, TIMESTAMP '2026-03-11 09:00:00', h.id_hotel, v.id
FROM hotel h, vehicule v
WHERE h.nom = 'Lokanga' AND v.reference = 'VH-RET-12';

-- VH-5-D-BUSY: 2 trajets deja faits (compteur trajet Sprint 7)
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'SEED-BUSY-1', 1, TIMESTAMP '2026-03-11 06:00:00', h.id_hotel, v.id
FROM hotel h, vehicule v
WHERE h.nom = 'Novotel' AND v.reference = 'VH-5-D-BUSY';

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'SEED-BUSY-2', 1, TIMESTAMP '2026-03-11 07:00:00', h.id_hotel, v.id
FROM hotel h, vehicule v
WHERE h.nom = 'Ibis' AND v.reference = 'VH-5-D-BUSY';

-- ------------------------------------------------------------
-- Backlog non assigne avant 10:00 pour forcer depart immediat
-- charge = 9 (>= 8)
-- ------------------------------------------------------------
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'A-BA-01', 6, TIMESTAMP '2026-03-11 09:20:00', h.id_hotel, NULL
FROM hotel h WHERE h.nom = 'Colbert';

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'A-BA-02', 3, TIMESTAMP '2026-03-11 09:40:00', h.id_hotel, NULL
FROM hotel h WHERE h.nom = 'Ibis';

-- ------------------------------------------------------------
-- Backlog complementaire avant 11:00 pour tester "report TA"
-- Apres depart de 10:00, on vise une charge restante < 12
-- ------------------------------------------------------------
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'B-BB-01', 2, TIMESTAMP '2026-03-11 10:20:00', h.id_hotel, NULL
FROM hotel h WHERE h.nom = 'Novotel';

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'B-BB-02', 3, TIMESTAMP '2026-03-11 10:30:00', h.id_hotel, NULL
FROM hotel h WHERE h.nom = 'Panorama';

-- ------------------------------------------------------------
-- Bloc Sprint 7 pur (fenetres TA, fractionnement, departages)
-- ------------------------------------------------------------
-- Fenetre vers 11:10-11:35 avec grosse reservation (fractionnement attendu)
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'S7-SPLIT-15', 15, TIMESTAMP '2026-03-11 11:10:00', h.id_hotel, NULL
FROM hotel h WHERE h.nom = 'Lokanga';

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'S7-SMALL-04', 4, TIMESTAMP '2026-03-11 11:25:00', h.id_hotel, NULL
FROM hotel h WHERE h.nom = 'Colbert';

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'S7-SMALL-03', 3, TIMESTAMP '2026-03-11 11:35:00', h.id_hotel, NULL
FROM hotel h WHERE h.nom = 'Ibis';

-- Egalite capacite 5 places autour de midi
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'S7-EQ-5A', 5, TIMESTAMP '2026-03-11 12:00:00', h.id_hotel, NULL
FROM hotel h WHERE h.nom = 'Novotel';

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'S7-EQ-5B', 5, TIMESTAMP '2026-03-11 12:05:00', h.id_hotel, NULL
FROM hotel h WHERE h.nom = 'Colbert';

-- Fenetre suivante pour verifier report + priorite backlog
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'S7-TAIL-02', 2, TIMESTAMP '2026-03-11 13:00:00', h.id_hotel, NULL
FROM hotel h WHERE h.nom = 'Carlton';

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'S7-TAIL-06', 6, TIMESTAMP '2026-03-11 13:10:00', h.id_hotel, NULL
FROM hotel h WHERE h.nom = 'Lokanga';

-- =========================================
-- 6) Verifications pre-execution
-- =========================================
SELECT 'DATASET PRET - LANCER /sprint8/executer?date=2026-03-11' AS instruction;

SELECT
    COUNT(*) FILTER (WHERE id_vehicule IS NULL) AS nb_non_assignees,
    COALESCE(SUM(nombre_passager) FILTER (WHERE id_vehicule IS NULL), 0) AS charge_non_assignee
FROM reservation
WHERE DATE(date_heure_arrivee) = DATE '2026-03-11';

-- Retours attendus (seed)
SELECT
    vehicule,
    date_heure_arrivee,
    date_heure_retour
FROM v_historique_assignation
WHERE DATE(date_heure_arrivee) = DATE '2026-03-11'
ORDER BY date_heure_retour, vehicule;

-- =========================================
-- 7) Requetes post-execution (a lancer APRES /sprint8/executer)
-- =========================================
-- 7.1 Repartition par mode
-- SELECT mode_assignation, COUNT(*) AS nb_lignes, SUM(nb_passagers) AS total_pax
-- FROM reservation_vehicule
-- GROUP BY mode_assignation
-- ORDER BY mode_assignation;

-- 7.2 Duplications anormales
-- SELECT reservation_id, COUNT(*) AS nb_vehicules
-- FROM reservation_vehicule
-- GROUP BY reservation_id
-- HAVING COUNT(*) > 1;

-- 7.3 Etat final reservations
-- SELECT id, client, nombre_passager, date_heure_arrivee, id_vehicule
-- FROM reservation
-- WHERE DATE(date_heure_arrivee) = DATE '2026-03-11'
-- ORDER BY date_heure_arrivee, id;
