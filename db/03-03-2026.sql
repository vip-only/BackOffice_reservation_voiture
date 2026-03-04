-- =========================================
-- SPRINT 3 - CREATION DES TABLES
-- =========================================

DROP TABLE IF EXISTS distance CASCADE;
DROP TABLE IF EXISTS lieu CASCADE;
DROP TABLE IF EXISTS parametre CASCADE;


-- =========================================
-- TABLE PARAMETRE (AVEC ID)
-- =========================================

CREATE TABLE parametre (
    id SERIAL PRIMARY KEY,
    cle VARCHAR(100) UNIQUE NOT NULL,
    valeur NUMERIC(10,2) NOT NULL,
    unite VARCHAR(50)
);


-- =========================================
-- TABLE LIEU
-- =========================================

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

    -- Empêche qu’un aéroport ait une distance vers lui-même
   
);

drop table reservation cascade;

-- =========================
CREATE TABLE reservation (
    id SERIAL PRIMARY KEY,
    client VARCHAR(200) NOT NULL,
    nombre_passager  INTEGER,
    date_heure_arrivee TIMESTAMP NOT NULL,
    id_hotel INTEGER NOT NULL,
    id_vehicule INTEGER ,
    -- CONSTRAINT fk_client FOREIGN KEY (id_client) REFERENCES client(id_client),
    CONSTRAINT fk_hotel FOREIGN KEY (id_hotel) REFERENCES hotel(id_hotel),
    CONSTRAINT fk_vehicule FOREIGN KEY (id_vehicule) REFERENCES vehicule(id)
);

INSERT INTO vehicule (reference, nombre_place, type_carburant) VALUES
('VH-001', 4, 'ES'),
('VH-002', 7, 'D'),
('VH-003', 12, 'D'),
('VH-004', 15, 'D'),
('VH-005', 5, 'ES'),
('VH-006', 9, 'D'),
('VH-007', 20, 'D'),
('VH-008', 30, 'D'),
('VH-009', 8, 'ES'),
('VH-010', 14, 'D');

INSERT INTO lieu (code, libelle)
VALUES ('TNR', 'Antananarivo - Ivato');


INSERT INTO parametre (cle, valeur) VALUES
('TA', '30'),                -- Temps d'attente   -- en minutes
('VITESSE_MOYENNE', '30');  -- en km/h



