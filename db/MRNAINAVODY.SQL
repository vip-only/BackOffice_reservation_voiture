-- =========================================
-- SCRIPT DE REINITIALISATION COMPLETE
-- Date: 11-03-2026
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
DROP TABLE IF EXISTS reservation_vehicule CASCADE;

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
    type_carburant VARCHAR(2) NOT NULL CHECK (type_carburant IN ('D', 'ES', 'H', 'EL')),
    heure_disponibilite TIME NOT NULL DEFAULT '00:00:00'
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

CREATE TABLE reservation_vehicule (
    id SERIAL PRIMARY KEY,
    reservation_id INTEGER NOT NULL,
    vehicule_id INTEGER NOT NULL,
    nb_passagers INTEGER NOT NULL CHECK (nb_passagers > 0),
    date_assignation TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_reservation_vehicule UNIQUE (reservation_id, vehicule_id)
);

-- =========================================
-- 3. INSERTION DES DONNEES DE REFERENCE
-- =========================================

-- Hotels
INSERT INTO hotel (nom) VALUES 
    ('Hotel1'),
    ('Hotel2');     -- id=1

-- Lieux (aéroports, gares, etc.)
INSERT INTO lieu (code, libelle) VALUES 
    ('TNR', 'Antananarivo - Ivato');

-- Paramètres
INSERT INTO parametre (cle, valeur, unite) VALUES
    ('TA', 30, 'minutes'),              -- Temps d'attente (non utilisé dans le calcul)
    ('VITESSE_MOYENNE', 50, 'km/h');    -- Vitesse moyenne

--       Panorama (id=6) et Colbert (id=1) ont la meme distance (15 km)

-- Distances depuis TNR (aeroport)
INSERT INTO distance (from_id, to_id, kilometer) VALUES 
    ('TNR', '1', 90.0),
    ('TNR', '2', 35.0),   -- TNR -> Novotel: 22.5 km
    ('1', '2', 60.0);   -- TNR -> Ibis: 18 km
    
-- Vehicules (tries par capacite et type carburant)
INSERT INTO vehicule (reference, nombre_place, type_carburant, heure_disponibilite) VALUES
    ('vehicule1', 5,  'D', '09:00:00'),
    ('vehicule2', 5,  'ES', '09:00:00'),
    ('vehicule3', 12, 'D',  '00:00:00'),
    ('vehicule4', 9, 'D',  '09:00:00'),
    ('vehicule5', 12, 'ES', '13:00:00');


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

-- TEST 1: Regles de base (F1, F2, F3) - Diesel prefere
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('Client1', 7, '2026-03-19 09:00:00', 1);

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('Client2', 20, '2026-03-19 08:00:00', 2);

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('Client3', 3, '2026-03-19 09:10:00', 1);

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('Client4', 10, '2026-03-19 09:15:00', 1);

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('Client5',5, '2026-03-19 09:20:00', 1);

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('Client6', 12, '2026-03-19 13:30:00', 1);

