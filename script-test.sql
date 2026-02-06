-- =========================
-- TABLE CLIENT
-- =========================
CREATE TABLE client (
    id_client SERIAL PRIMARY KEY,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100)
);

-- =========================
-- TABLE HOTEL
-- =========================
CREATE TABLE hotel (
    id_hotel SERIAL PRIMARY KEY,
    nom VARCHAR(150) NOT NULL,
    adresse TEXT
);

-- =========================
-- TABLE VEHICULE
-- =========================
CREATE TABLE vehicule (
    id_vehicule SERIAL PRIMARY KEY,
    capacite INTEGER NOT NULL CHECK (capacite > 0),
    carburant VARCHAR(50) CHECK (carburant IN ('diesel', 'essence', 'electrique'))
);

-- =========================
-- TABLE PARAMETRE
-- =========================
CREATE TABLE parametre (
    id_parametre SERIAL PRIMARY KEY,
    vitesse_moyenne NUMERIC(5,2) NOT NULL, -- km/h
    temps_attente INTEGER NOT NULL -- minutes
);

-- =========================
-- TABLE RESERVATION
-- =========================
CREATE TABLE reservation (
    id_reservation SERIAL PRIMARY KEY,
    id_client INTEGER NOT NULL,
    id_hotel INTEGER NOT NULL,
    id_vehicule INTEGER NOT NULL,
    id_parametre INTEGER NOT NULL,

    nbr_passager INTEGER NOT NULL CHECK (nbr_passager > 0),
    date_heure_arrivee TIMESTAMP NOT NULL,

    CONSTRAINT fk_client FOREIGN KEY (id_client) REFERENCES client(id_client),
    CONSTRAINT fk_hotel FOREIGN KEY (id_hotel) REFERENCES hotel(id_hotel),
    CONSTRAINT fk_vehicule FOREIGN KEY (id_vehicule) REFERENCES vehicule(id_vehicule),
    CONSTRAINT fk_parametre FOREIGN KEY (id_parametre) REFERENCES parametre(id_parametre)
);
