-- =========================
-- Script du 14-02-2026
-- CRUD Vehicule
-- =========================

-- =========================
-- TABLE VEHICULE
-- =========================
CREATE TABLE vehicule (
    id SERIAL PRIMARY KEY,
    reference VARCHAR(50) NOT NULL UNIQUE,
    nombre_place INTEGER NOT NULL CHECK (nombre_place > 0),
    type_carburant VARCHAR(2) NOT NULL CHECK (type_carburant IN ('D', 'ES', 'H', 'EL'))
);

-- D = Diesel
-- ES = Essence
-- H = Hybride
-- EL = Electrique

-- =========================
-- INSERTION VEHICULE (données de test)
-- =========================
INSERT INTO vehicule (reference, nombre_place, type_carburant) VALUES ('VH-001', 4, 'ES');
INSERT INTO vehicule (reference, nombre_place, type_carburant) VALUES ('VH-002', 5, 'D');
INSERT INTO vehicule (reference, nombre_place, type_carburant) VALUES ('VH-003', 7, 'H');
INSERT INTO vehicule (reference, nombre_place, type_carburant) VALUES ('VH-004', 2, 'EL');
INSERT INTO vehicule (reference, nombre_place, type_carburant) VALUES ('VH-005', 5, 'ES');
INSERT INTO vehicule (reference, nombre_place, type_carburant) VALUES ('VH-006', 8, 'D');
