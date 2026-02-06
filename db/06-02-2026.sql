-- =========================
-- TABLE CLIENT
-- =========================
-- CREATE TABLE client (
--     id_client SERIAL PRIMARY KEY,
--     nom VARCHAR(100) NOT NULL,
--     prenom VARCHAR(100)
-- );
CREATE DATABASE backoffice;
\c backoffice;
-- =========================
-- TABLE HOTEL
-- =========================
CREATE TABLE hotel (
    id_hotel SERIAL PRIMARY KEY,
    nom VARCHAR(150) NOT NULL
    -- adresse TEXT
);

-- =========================
-- TABLE VEHICULE
-- =========================
-- CREATE TABLE vehicule (
--     id_vehicule SERIAL PRIMARY KEY,
--     capacite INTEGER NOT NULL CHECK (capacite > 0),
--     carburant VARCHAR(50) CHECK (carburant IN ('diesel', 'essence', 'electrique'))
-- );

-- =========================
-- TABLE PARAMETRE
-- -- =========================
-- CREATE TABLE parametre (
--     id_parametre SERIAL PRIMARY KEY,
--     vitesse_moyenne NUMERIC(5,2) NOT NULL, -- km/h
--     temps_attente INTEGER NOT NULL -- minutes
-- );

-- =========================
-- TABLE RESERVATION
-- =========================
CREATE TABLE reservation (
    id SERIAL PRIMARY KEY,
    client VARCHAR(200) NOT NULL,
    nombre_passager  INTEGER,
    date_heure_arrivee TIMESTAMP NOT NULL,
    id_hotel INTEGER NOT NULL,
    -- CONSTRAINT fk_client FOREIGN KEY (id_client) REFERENCES client(id_client),
    CONSTRAINT fk_hotel FOREIGN KEY (id_hotel) REFERENCES hotel(id_hotel)
);


-- =========================
-- INSERTION HOTEL
-- =========================
INSERT INTO hotel (nom) VALUES ('Hotel Carlton');
INSERT INTO hotel (nom) VALUES ('Hotel Colbert');
INSERT INTO hotel (nom) VALUES ('Hotel Ibis');
INSERT INTO hotel (nom) VALUES ('Hotel du Louvre');
INSERT INTO hotel (nom) VALUES ('Hotel Sakamanga');
INSERT INTO hotel (nom) VALUES ('Hotel Panorama');
INSERT INTO hotel (nom) VALUES ('Hotel Le Pavillon');
INSERT INTO hotel (nom) VALUES ('Hotel Radisson Blu');
INSERT INTO hotel (nom) VALUES ('Hotel Tamboho');
INSERT INTO hotel (nom) VALUES ('Hotel Restaurant Mellis');

-- sprint1 
-- backoffice
-- dans le backoffice formuaire pour inserer reservation
-- pas encore proteger
-- hotel pas encore d'insertion mais script d'insertion hotel livrable

-- frontoffice 
-- liste reservation afaka anaovana recherche par date filter 
-- li id hotel avadika en nom hotel















backoffice vraiii