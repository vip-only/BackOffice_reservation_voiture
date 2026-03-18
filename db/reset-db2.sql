-- =========================================
-- SCRIPT DE REINITIALISATION COMPLETE - SPRINT 5
-- Date: 11-03-2026
-- Test du Temps d'Attente (TA = 30 minutes)
-- Base de donnees: backoffice
-- =========================================

-- =========================================
-- 1. SUPPRESSION DES TABLES (ordre inverse des dépendances)
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

-- TABLE RESERVATION_VEHICULE (journal des assignations)
CREATE TABLE reservation_vehicule (
    id SERIAL PRIMARY KEY,
    reservation_id INTEGER NOT NULL,
    vehicule_id INTEGER NOT NULL,
    nb_passagers INTEGER NOT NULL CHECK (nb_passagers > 0),
    date_assignation TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_rv_reservation FOREIGN KEY (reservation_id) REFERENCES reservation(id) ON DELETE CASCADE,
    CONSTRAINT fk_rv_vehicule FOREIGN KEY (vehicule_id) REFERENCES vehicule(id),
    CONSTRAINT uq_rv UNIQUE (reservation_id, vehicule_id)
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
    ('Colbert'),      -- id=1
    ('Novotel'),      -- id=2
    ('Ibis'),         -- id=3
    ('Lokanga'),      -- id=4
    ('Carlton'),      -- id=5 (même distance que Ibis pour tester départage)
    ('Panorama');     -- id=6 (même distance que Colbert pour tester départage)

-- Lieux (aéroports, gares, etc.)
INSERT INTO lieu (code, libelle) VALUES 
    ('TNR', 'Antananarivo - Ivato');

-- Paramètres
INSERT INTO parametre (cle, valeur, unite) VALUES
    ('TA', 30, 'minutes'),              -- Temps d'attente = 30 minutes
    ('VITESSE_MOYENNE', 30, 'km/h');    -- Vitesse moyenne

-- Distances depuis TNR (aeroport)
INSERT INTO distance (from_id, to_id, kilometer) VALUES 
    ('TNR', '1', 15.0),   -- TNR -> Colbert: 15 km
    ('TNR', '2', 22.5),   -- TNR -> Novotel: 22.5 km
    ('TNR', '3', 18.0),   -- TNR -> Ibis: 18 km
    ('TNR', '4', 30.0),   -- TNR -> Lokanga: 30 km
    ('TNR', '5', 18.0),   -- TNR -> Carlton: 18 km (MEME que Ibis!)
    ('TNR', '6', 15.0);   -- TNR -> Panorama: 15 km (MEME que Colbert!)

-- Distances INTER-HOTELS (pour nearest-neighbour)
INSERT INTO distance (from_id, to_id, kilometer) VALUES 
    ('1', '2', 8.0),    -- Colbert <-> Novotel
    ('1', '3', 5.0),    -- Colbert <-> Ibis
    ('1', '4', 16.0),   -- Colbert <-> Lokanga
    ('1', '5', 5.0),    -- Colbert <-> Carlton
    ('1', '6', 3.0),    -- Colbert <-> Panorama (proches!)
    ('2', '3', 6.0),    -- Novotel <-> Ibis
    ('2', '4', 10.0),   -- Novotel <-> Lokanga
    ('2', '5', 6.0),    -- Novotel <-> Carlton
    ('2', '6', 9.0),    -- Novotel <-> Panorama
    ('3', '4', 14.0),   -- Ibis <-> Lokanga
    ('3', '5', 2.0),    -- Ibis <-> Carlton (proches!)
    ('3', '6', 6.0),    -- Ibis <-> Panorama
    ('4', '5', 14.0),   -- Lokanga <-> Carlton
    ('4', '6', 17.0),   -- Lokanga <-> Panorama
    ('5', '6', 6.0);    -- Carlton <-> Panorama

-- Vehicules (tries par capacite et type carburant)
INSERT INTO vehicule (reference, nombre_place, type_carburant) VALUES
    ('VH-001', 4, 'ES'),    -- Petite voiture essence
    ('VH-002', 4, 'D'),     -- Petite voiture diesel (preferee si 4 places demandees)
    ('VH-003', 7, 'D'),     -- Monospace diesel
    ('VH-004', 7, 'ES'),    -- Monospace essence
    ('VH-005', 12, 'D'),    -- Minibus diesel
    ('VH-006', 15, 'D'),    -- Bus diesel
    ('VH-007', 20, 'D'),    -- Grand bus diesel
    ('VH-008', 30, 'D');    -- Tres grand bus diesel


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
-- 5. SCENARIO DE TEST - SPRINT 5 (TA = Temps d'Attente)
-- =========================================
-- Date: 11 Mars 2026
-- TA = 30 minutes (paramètre en base)
-- 
-- ╔══════════════════════════════════════════════════════════════════════════╗
-- ║                    FONCTIONNALITES A TESTER                              ║
-- ╠══════════════════════════════════════════════════════════════════════════╣
-- ║ F1. Capacité >= nombre de passagers                                      ║
-- ║ F2. Plus petite capacité satisfaisante                                   ║
-- ║ F3. Préférence Diesel si égalité de capacité                             ║
-- ║ F4. Priorité par nombre de passagers décroissant                         ║
-- ║ F5. Regroupement par fenêtre TA (pas timestamp exact)                    ║
-- ║ F6. Chevauchement: véhicule occupé = indisponible                        ║
-- ║ F7. Nearest-neighbour pour ordre de dépose                               ║
-- ║ F8. Départage ALPHABÉTIQUE si même distance                              ║
-- ║ F9. Heure de départ véhicule = MAX(arrivées) du groupe TA               ║
-- ╚══════════════════════════════════════════════════════════════════════════╝
--
-- DONNÉES DE RÉFÉRENCE:
-- ┌─────────────┬─────────┬──────────┐
-- │ Hôtel       │ ID      │ Distance │
-- ├─────────────┼─────────┼──────────┤
-- │ Carlton     │ 5       │ 18 km    │
-- │ Colbert     │ 1       │ 15 km    │
-- │ Ibis        │ 3       │ 18 km    │
-- │ Lokanga     │ 4       │ 30 km    │
-- │ Novotel     │ 2       │ 22.5 km  │
-- │ Panorama    │ 6       │ 15 km    │
-- └─────────────┴─────────┴──────────┘
--
-- Véhicules: VH-001(4,ES), VH-002(4,D), VH-003(7,D), VH-004(7,ES),
--            VH-005(12,D), VH-006(15,D), VH-007(20,D), VH-008(30,D)
--
-- ═══════════════════════════════════════════════════════════════════════════
-- TEST 1: VOL SEUL - REGLES DE BASE (F1, F2, F3)
-- ═══════════════════════════════════════════════════════════════════════════
-- 
-- R1: 07:00 -> Colbert, 3 passagers
--     Seul dans la fenêtre [07:00, 07:30]
--     Départ véhicule = 07:00 (pas d'attente)
--     F2+F3: VH-002 (4 places, Diesel) ✓
--     Retour: 07:00 + 60min = 08:00
--
-- ═══════════════════════════════════════════════════════════════════════════
-- TEST 2: REGROUPEMENT PAR FENÊTRE TA (F5, F7, F9)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- R2: 08:30 -> Colbert (15km), 2 passagers  ─┐
-- R3: 08:45 -> Ibis (18km), 2 passagers      ─┼─ Fenêtre [08:30, 09:00]
-- R4: 08:55 -> Novotel (22.5km), 2 passagers ─┘
--     Total = 6 passagers
--     F9: Départ véhicule = MAX(08:30, 08:45, 08:55) = 08:55
--     F5: Regroupement dans VH-003 (7 places) ✓
--     F7: Nearest-neighbour: Colbert(15) → Ibis(5km) → Novotel(6km)
--     Attentes: R2=25min, R3=10min, R4=0min
--
-- ═══════════════════════════════════════════════════════════════════════════
-- TEST 3: FENÊTRE TA AVEC GRAND GROUPE (F5, F9)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- R5: 10:30 -> Lokanga, 11 passagers   ─┐ Fenêtre [10:30, 11:00]
-- R6: 10:50 -> Colbert, 2 passagers    ─┘
--     Total = 13 passagers
--     F9: Départ = MAX(10:30, 10:50) = 10:50
--     VH-006 (15 places) ✓
--     Attente: R5=20min, R6=0min
--
-- ═══════════════════════════════════════════════════════════════════════════
-- TEST 4: VOL SEUL + CHEVAUCHEMENT (F6)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- R7: 12:00 -> Novotel (22.5km), 5 passagers
--     Seul dans fenêtre [12:00, 12:30]
--     Départ = 12:00
--     VH-003 (7 places, Diesel) ✓
--     Retour: 12:00 + 90min = 13:30
--
-- ═══════════════════════════════════════════════════════════════════════════
-- TEST 5: DEPARTAGE ALPHABÉTIQUE + TA (F8, F5, F9)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- R8:  14:00 -> Carlton (18km), 2 passagers  ─┐
-- R9:  14:10 -> Ibis (18km), 2 passagers     ─┼─ Fenêtre [14:00, 14:30]
-- R10: 14:20 -> Lokanga (30km), 2 passagers  ─┘
--     Total = 6 passagers
--     F9: Départ = MAX(14:00, 14:10, 14:20) = 14:20
--     VH-003 libre (retour 13:30 < 14:20) ✓
--     F7+F8: Carlton < Ibis (même distance 18km, C < I)
--     Attentes: R8=20min, R9=10min, R10=0min
--
-- ═══════════════════════════════════════════════════════════════════════════
-- TEST 6: CHEVAUCHEMENT + TA + ALPHABÉTIQUE (F5+F6+F8+F9)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- R11: 15:30 -> Colbert (15km), 2 passagers  ─┐ Fenêtre [15:30, 16:00]
-- R12: 15:45 -> Panorama (15km), 2 passagers ─┘
--     Total = 4 passagers
--     F9: Départ = MAX(15:30, 15:45) = 15:45
--     VH-002 (4 places, Diesel, libre) ✓
--     F8: Colbert < Panorama (C < P)
--     Attente: R11=15min, R12=0min
--
-- ═══════════════════════════════════════════════════════════════════════════
-- TEST 7: VOL SEUL - DIESEL INDISPONIBLE (F1, F2, F6)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- R13: 16:00 -> Ibis (18km), 3 passagers
--     Seul dans fenêtre [16:00, 16:30]
--     VH-002 occupé, VH-003 occupé → VH-001 (4 places, Essence)
--
-- ═══════════════════════════════════════════════════════════════════════════
-- TEST 8: REGROUPEMENT CAPACITÉ EXACTE + TA (F5, F9)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- R14: 18:00 -> Colbert (15km), 4 passagers ─┐ Fenêtre [18:00, 18:30]
-- R15: 18:20 -> Novotel (22.5km), 3 passagers ─┘
--     Total = 7 passagers
--     F9: Départ = MAX(18:00, 18:20) = 18:20
--     VH-003 (7 places, exactement) ✓
--     Attente: R14=20min, R15=0min
--
-- ═══════════════════════════════════════════════════════════════════════════
--
-- RÉSUMÉ ATTENDU (après assignation automatique):
-- ╔═══════╦════════════╦═══════════╦═════════════════╦═══════════╦══════════════╦══════════╗
-- ║ Rés.  ║ Heure vol  ║ Passagers ║ Hôtel           ║ Véhicule  ║ Départ véh.  ║ Attente  ║
-- ╠═══════╬════════════╬═══════════╬═════════════════╬═══════════╬══════════════╬══════════╣
-- ║ R1    ║ 07:00      ║ 3         ║ Colbert         ║ VH-002    ║ 07:00        ║ 0 min    ║
-- ╠═══════╬════════════╬═══════════╬═════════════════╬═══════════╬══════════════╬══════════╣
-- ║ R2    ║ 08:30      ║ 2         ║ Colbert         ║ VH-003    ║ 08:55        ║ 25 min   ║
-- ║ R3    ║ 08:45      ║ 2         ║ Ibis            ║ VH-003    ║ 08:55        ║ 10 min   ║
-- ║ R4    ║ 08:55      ║ 2         ║ Novotel         ║ VH-003    ║ 08:55        ║ 0 min    ║
-- ╠═══════╬════════════╬═══════════╬═════════════════╬═══════════╬══════════════╬══════════╣
-- ║ R5    ║ 10:30      ║ 11        ║ Lokanga         ║ VH-006    ║ 10:50        ║ 20 min   ║
-- ║ R6    ║ 10:50      ║ 2         ║ Colbert         ║ VH-006    ║ 10:50        ║ 0 min    ║
-- ╠═══════╬════════════╬═══════════╬═════════════════╬═══════════╬══════════════╬══════════╣
-- ║ R7    ║ 12:00      ║ 5         ║ Novotel         ║ VH-003    ║ 12:00        ║ 0 min    ║
-- ╠═══════╬════════════╬═══════════╬═════════════════╬═══════════╬══════════════╬══════════╣
-- ║ R8    ║ 14:00      ║ 2         ║ Carlton         ║ VH-003    ║ 14:20        ║ 20 min   ║
-- ║ R9    ║ 14:10      ║ 2         ║ Ibis            ║ VH-003    ║ 14:20        ║ 10 min   ║
-- ║ R10   ║ 14:20      ║ 2         ║ Lokanga         ║ VH-003    ║ 14:20        ║ 0 min    ║
-- ╠═══════╬════════════╬═══════════╬═════════════════╬═══════════╬══════════════╬══════════╣
-- ║ R11   ║ 15:30      ║ 2         ║ Colbert         ║ VH-002    ║ 15:45        ║ 15 min   ║
-- ║ R12   ║ 15:45      ║ 2         ║ Panorama        ║ VH-002    ║ 15:45        ║ 0 min    ║
-- ╠═══════╬════════════╬═══════════╬═════════════════╬═══════════╬══════════════╬══════════╣
-- ║ R13   ║ 16:00      ║ 3         ║ Ibis            ║ VH-001    ║ 16:00        ║ 0 min    ║
-- ╠═══════╬════════════╬═══════════╬═════════════════╬═══════════╬══════════════╬══════════╣
-- ║ R14   ║ 18:00      ║ 4         ║ Colbert         ║ VH-003    ║ 18:20        ║ 20 min   ║
-- ║ R15   ║ 18:20      ║ 3         ║ Novotel         ║ VH-003    ║ 18:20        ║ 0 min    ║
-- ╚═══════╩════════════╩═══════════╩═════════════════╩═══════════╩══════════════╩══════════╝


-- =========================================
-- 6. RESERVATIONS DE TEST (15 reservations) - Sprint 5
-- =========================================
-- Date: 11 Mars 2026 - Heures variées dans les fenêtres TA

-- TEST 1: Vol seul
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('R1-Alice Martin', 3, '2026-03-11 07:00:00', 1);

-- TEST 2: 3 vols dans fenêtre TA [08:30, 09:00] → départ véhicule 08:55
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('R2-Bob Dupont', 2, '2026-03-11 08:30:00', 1);

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('R3-Claire Durand', 2, '2026-03-11 08:45:00', 3);

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('R4-David Bernard', 2, '2026-03-11 08:55:00', 2);

-- TEST 3: 2 vols dans fenêtre TA [10:30, 11:00] → départ véhicule 10:50
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('R5-Emma Petit', 11, '2026-03-11 10:30:00', 4);

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('R6-Francois Moreau', 2, '2026-03-11 10:50:00', 1);

-- TEST 4: Vol seul
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('R7-Gabrielle Simon', 5, '2026-03-11 12:00:00', 2);

-- TEST 5: 3 vols dans fenêtre TA [14:00, 14:30] → départ véhicule 14:20
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('R8-Henri Laurent', 2, '2026-03-11 14:00:00', 5);

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('R9-Isabelle Roux', 2, '2026-03-11 14:10:00', 3);

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('R10-Jean Leroy', 2, '2026-03-11 14:20:00', 4);

-- TEST 6: 2 vols dans fenêtre TA [15:30, 16:00] → départ véhicule 15:45
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('R11-Kevin Blanc', 2, '2026-03-11 15:30:00', 1);

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('R12-Lucie Mercier', 2, '2026-03-11 15:45:00', 6);

-- TEST 7: Vol seul (diesel indisponible)
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('R13-Marie Fournier', 3, '2026-03-11 16:00:00', 3);

-- TEST 8: 2 vols dans fenêtre TA [18:00, 18:30] → départ véhicule 18:20
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('R14-Nicolas Garnier', 4, '2026-03-11 18:00:00', 1);

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('R15-Olivier Perrin', 3, '2026-03-11 18:20:00', 2);


-- =========================================
-- 7. VERIFICATION (après assignation)
-- =========================================
-- SELECT 
--     reservation_id, client, nombre_passager,
--     date_heure_arrivee, hotel, vehicule,
--     distance_km, duree_totale_minutes || ' min' AS duree_trajet
-- FROM v_historique_assignation
-- ORDER BY date_heure_arrivee, hotel;
