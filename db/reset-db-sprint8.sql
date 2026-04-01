-- =========================================
-- RESET COMPLET - SPRINT 8
-- BackOffice / PostgreSQL
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

-- 2) CREATE tables
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
    ('Lokanga');

INSERT INTO lieu (code, libelle) VALUES
    ('TNR', 'Antananarivo - Ivato');

INSERT INTO parametre (cle, valeur, unite) VALUES
    ('TA', 30, 'minutes'),
    ('VITESSE_MOYENNE', 30, 'km/h');

-- Distances TNR -> hotel
INSERT INTO distance (from_id, to_id, kilometer) VALUES
    ('TNR', '1', 15.0),
    ('TNR', '2', 22.5),
    ('TNR', '3', 18.0),
    ('TNR', '4', 30.0);

-- Distances inter-hotels minimales
INSERT INTO distance (from_id, to_id, kilometer) VALUES
    ('1', '2', 8.0),
    ('1', '3', 5.0),
    ('1', '4', 16.0),
    ('2', '3', 6.0),
    ('2', '4', 10.0),
    ('3', '4', 14.0);

INSERT INTO vehicule (reference, nombre_place, type_carburant, heure_disponibilite) VALUES
    ('VH-RET-8', 8, 'D', '00:00:00'),
    ('VH-RET-12', 12, 'ES', '00:00:00'),
    ('VH-STD-5', 5, 'ES', '00:00:00'),
    ('VH-STD-7', 7, 'D', '00:00:00');

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

-- 5) Sanity checks
SELECT 'RESET SPRINT 8 OK' AS status;
SELECT COUNT(*) AS nb_hotels FROM hotel;
SELECT COUNT(*) AS nb_vehicules FROM vehicule;
