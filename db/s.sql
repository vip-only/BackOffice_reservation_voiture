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
    ('Hotel1');     -- id=1
    -- ('Novotel'),      -- id=2
    -- ('Ibis'),         -- id=3
    -- ('Lokanga'),      -- id=4
    -- ('Carlton'),      -- id=5 (même distance que Ibis pour tester départage)
    -- ('Panorama');     -- id=6 (même distance que Colbert pour tester départage)

-- Lieux (aéroports, gares, etc.)
INSERT INTO lieu (code, libelle) VALUES 
    ('TNR', 'Antananarivo - Ivato');

-- Paramètres
INSERT INTO parametre (cle, valeur, unite) VALUES
    ('TA', 30, 'minutes'),              -- Temps d'attente (non utilisé dans le calcul)
    ('VITESSE_MOYENNE', 50, 'km/h');    -- Vitesse moyenne

-- Distances (TNR vers chaque hotel + distances inter-hotels)
-- from_id = 'TNR' ou id_hotel, to_id = id_hotel (en VARCHAR)
-- NOTE: Carlton (id=5) et Ibis (id=3) ont la meme distance (18 km)
--       Panorama (id=6) et Colbert (id=1) ont la meme distance (15 km)

-- Distances depuis TNR (aeroport)
INSERT INTO distance (from_id, to_id, kilometer) VALUES 
    ('TNR', '1', 50.0);  -- TNR -> Colbert: 15 km
    -- ('TNR', '2', 22.5),   -- TNR -> Novotel: 22.5 km
    -- ('TNR', '3', 18.0),   -- TNR -> Ibis: 18 km
    -- ('TNR', '4', 30.0),   -- TNR -> Lokanga: 30 km
    -- ('TNR', '5', 18.0),   -- TNR -> Carlton: 18 km (MEME que Ibis!)
    -- ('TNR', '6', 15.0);   -- TNR -> Panorama: 15 km (MEME que Colbert!)

-- Distances INTER-HOTELS (pour nearest-neighbour)
-- OPTIMISATION: Une seule entree par paire d'hotels (pas de redondance)
-- Le code Java gere automatiquement la recherche dans les deux sens
-- Convention: from_id < to_id (plus petit ID vers plus grand ID)
-- INSERT INTO distance (from_id, to_id, kilometer) VALUES 
--     -- Depuis Colbert (1) vers hotels avec ID > 1
--     ('1', '2', 8.0),    -- Colbert <-> Novotel
--     ('1', '3', 5.0),    -- Colbert <-> Ibis
--     ('1', '4', 16.0),   -- Colbert <-> Lokanga
--     ('1', '5', 5.0),    -- Colbert <-> Carlton
--     ('1', '6', 3.0),    -- Colbert <-> Panorama (proches!)
--     -- Depuis Novotel (2) vers hotels avec ID > 2
--     ('2', '3', 6.0),    -- Novotel <-> Ibis
--     ('2', '4', 10.0),   -- Novotel <-> Lokanga
--     ('2', '5', 6.0),    -- Novotel <-> Carlton
--     ('2', '6', 9.0),    -- Novotel <-> Panorama
--     -- Depuis Ibis (3) vers hotels avec ID > 3
--     ('3', '4', 14.0),   -- Ibis <-> Lokanga
--     ('3', '5', 2.0),    -- Ibis <-> Carlton (proches!)
--     ('3', '6', 6.0),    -- Ibis <-> Panorama
--     -- Depuis Lokanga (4) vers hotels avec ID > 4
--     ('4', '5', 14.0),   -- Lokanga <-> Carlton
--     ('4', '6', 17.0),   -- Lokanga <-> Panorama
--     -- Depuis Carlton (5) vers hotels avec ID > 5
--     ('5', '6', 6.0);    -- Carlton <-> Panorama

-- Vehicules (tries par capacite et type carburant)
INSERT INTO vehicule (reference, nombre_place, type_carburant) VALUES
    ('vehicule1', 12, 'D'),    -- Petite voiture essence
    ('vehicule2', 5, 'ES'),     -- Petite voiture diesel (preferee si 4 places demandees)
    ('vehicule3', 5, 'D'),     -- Monospace diesel
    ('vehicule4', 12, 'ES') ; -- Monospace essence
    -- Tres grand bus diesel


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
-- 5. SCENARIO DE TEST COMPLET - TOUTES FONCTIONNALITES
-- =========================================
-- Date: 11 Mars 2026
-- 
-- ╔══════════════════════════════════════════════════════════════════════════╗
-- ║                    FONCTIONNALITES A TESTER                              ║
-- ╠══════════════════════════════════════════════════════════════════════════╣
-- ║ F1. Règle 1: capacité >= nombre de passagers                             ║
-- ║ F2. Règle 2: plus petite capacité satisfaisante                          ║
-- ║ F3. Règle 3: préférence Diesel si égalité de capacité                    ║
-- ║ F4. Priorité par nombre de passagers décroissant                         ║
-- ║ F5. Regroupement si même date/heure ET capacité suffisante               ║
-- ║ F6. Chevauchement: véhicule occupé = indisponible                        ║
-- ║ F7. Nearest-neighbour pour ordre de dépose                               ║
-- ║ F8. Départage par ORDRE ALPHABÉTIQUE du nom d'hôtel si même distance     ║
-- ║ F9. Combinaison chevauchement + regroupement                             ║
-- ╚══════════════════════════════════════════════════════════════════════════╝
--
-- DONNÉES DE RÉFÉRENCE:
-- ┌─────────────┬─────────┬──────────┐
-- │ Hôtel       │ ID      │ Distance │
-- ├─────────────┼─────────┼──────────┤
-- │ Carlton     │ 5       │ 18 km    │  ← C < I (alphabétique)
-- │ Colbert     │ 1       │ 15 km    │  ← C < P (alphabétique)
-- │ Ibis        │ 3       │ 18 km    │
-- │ Lokanga     │ 4       │ 30 km    │
-- │ Novotel     │ 2       │ 22.5 km  │
-- │ Panorama    │ 6       │ 15 km    │
-- └─────────────┴─────────┴──────────┘
--
-- Véhicules (triés par capacité):
-- vehicule1(4,ES), VH-002(4,D), VH-003(7,D), VH-004(7,ES),
-- VH-005(12,D), VH-006(15,D), VH-007(20,D), VH-008(30,D)
--
-- ═══════════════════════════════════════════════════════════════════════════
-- TEST 1: REGLES DE BASE (F1, F2, F3)
-- ═══════════════════════════════════════════════════════════════════════════
-- 
-- R1: 07:00 -> Colbert, 3 passagers
--     F1: Besoin >= 3 places → VH-001(4), VH-002(4), VH-003(7)... OK
--     F2: Plus petite = 4 places → VH-001 ou VH-002
--     F3: Préférer Diesel → VH-002 (4 places, Diesel) ✓
--     Retour: 07:00 + 60min = 08:00
--
-- ═══════════════════════════════════════════════════════════════════════════
-- TEST 2: REGROUPEMENT SIMPLE (F5, F7)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- R2: 08:30 -> Colbert (15km), 2 passagers
-- R3: 08:30 -> Ibis (18km), 2 passagers
-- R4: 08:30 -> Novotel (22.5km), 2 passagers
--     Total = 6 passagers à 08:30
--     F5: Regroupement possible dans VH-003 (7 places) ✓
--     F7: Ordre nearest-neighbour:
--         1. Colbert (15km)
--         2. Ibis (18km)
--         3. Novotel (22.5km)
--     Retour: 08:30 + 90min = 10:00
--
-- ═══════════════════════════════════════════════════════════════════════════
-- TEST 3: REGROUPEMENT AVEC VEHICULE PLUS GRAND (F5, F1, F2, F3)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- R5: 10:30 -> Lokanga, 11 passagers
-- R6: 10:30 -> Colbert, 2 passagers
--     Total = 13 passagers
--     VH-005 max = 12 places → trop petit
--     VH-006 max = 15 places → SUFFISANT!
--     F5: Regroupement POSSIBLE dans VH-006 (15 places)
--     F7: Ordre nearest-neighbour: Colbert(15km) → Lokanga(30km)
--     Retour: 10:30 + 120min = 12:30
--
-- ═══════════════════════════════════════════════════════════════════════════
-- TEST 4: CHEVAUCHEMENT SIMPLE (F6)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- R7: 12:00 -> Novotel (22.5km), 5 passagers
--     VH-002 occupé (10:30-11:30) → LIBRE à 12:00 ✓
--     VH-003 occupé (08:30-10:00) → LIBRE à 12:00 ✓
--     VH-005 occupé (10:30-12:30) → OCCUPÉ (chevauche 12:00) ✗
--     F6: Exclure VH-005
--     F2: Plus petite capacité >= 5 → VH-003 (7 places, Diesel) ✓
--     Retour: 12:00 + 90min = 13:30
--
-- ═══════════════════════════════════════════════════════════════════════════
-- TEST 5: DEPARTAGE ALPHABETIQUE MEME DISTANCE (F8, F5, F7)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- R8:  14:00 -> Carlton (18km), 2 passagers
-- R9:  14:00 -> Ibis (18km), 2 passagers
-- R10: 14:00 -> Lokanga (30km), 2 passagers
--     Total = 6 passagers à 14:00
--     F5: Regroupement possible dans VH-003 (7 places)
--         VH-003 occupé (12:00-13:30) → LIBRE à 14:00 ✓
--         → VH-003 (Diesel préféré)
--     F7+F8: Ordre nearest-neighbour avec départage ALPHABÉTIQUE:
--         Carlton et Ibis = 18km (même distance)
--         Départage alphabétique: "Carlton" < "Ibis" (C < I)
--         → Carlton AVANT Ibis
--         1. Carlton (18km)    ← premier alphabétiquement
--         2. Ibis (18km)       ← second alphabétiquement
--         3. Lokanga (30km)
--     Retour: 14:00 + 120min = 16:00
--
-- ═══════════════════════════════════════════════════════════════════════════
-- TEST 6: CHEVAUCHEMENT + REGROUPEMENT + DEPARTAGE ALPHABETIQUE (F9 = F5+F6+F8)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- R11: 15:30 -> Colbert (15km), 2 passagers
-- R12: 15:30 -> Panorama (15km), 2 passagers
--     Total = 4 passagers à 15:30
--     VH-003 occupé (14:00-16:00) → OCCUPÉ (chevauche 15:30) ✗
--     VH-005 libre (retour 12:30 < 15:30) ✓
--     F9: Regroupement possible mais VH-003 occupé
--         → VH-002 (4 places, Diesel, libre) ✓
--     F7+F8: Ordre nearest-neighbour avec départage ALPHABÉTIQUE:
--         Colbert et Panorama = 15km (même distance)
--         Départage alphabétique: "Colbert" < "Panorama" (C < P)
--         → Colbert AVANT Panorama
--         1. Colbert (15km)    ← premier alphabétiquement
--         2. Panorama (15km)   ← second alphabétiquement
--     Retour: 15:30 + 60min = 16:30
--
-- ═══════════════════════════════════════════════════════════════════════════
-- TEST 7: TOUS VEHICULES DIESEL PETITS OCCUPES (F1, F2, F6)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- R13: 16:00 -> Ibis (18km), 3 passagers
--     VH-002 occupé (15:30-16:30) → OCCUPÉ ✗
--     VH-003 occupé (14:00-16:00) → retour = 16:00 = ENCORE OCCUPÉ ✗
--     VH-001 libre (4 places, Essence) → disponible ✓
--     F1+F2: Plus petite capacité >= 3 et disponible → VH-001 (4 places, Essence)
--     Retour: 16:00 + 72min = 17:12
--
-- ═══════════════════════════════════════════════════════════════════════════
-- TEST 8: REGROUPEMENT MAX CAPACITE EXACTE (F5)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- R14: 18:00 -> Colbert (15km), 4 passagers
-- R15: 18:00 -> Novotel (22.5km), 3 passagers
--     Total = 7 passagers à 18:00
--     F5: Regroupement EXACTEMENT dans VH-003 (7 places) ✓
--     F7: Ordre: 1. Colbert (15km), 2. Novotel (22.5km)
--     Retour: 18:00 + 90min = 19:30
--
-- ═══════════════════════════════════════════════════════════════════════════
--
-- RÉSUMÉ ATTENDU (après assignation automatique):
-- ╔═══════╦════════════╦═══════════╦═════════════════╦═══════════╦══════════╗
-- ║ Rés.  ║ Heure      ║ Passagers ║ Hôtel           ║ Véhicule  ║ Retour   ║
-- ╠═══════╬════════════╬═══════════╬═════════════════╬═══════════╬══════════╣
-- ║ R1    ║ 07:00      ║ 3         ║ Colbert         ║ VH-002    ║ 08:00    ║
-- ╠═══════╬════════════╬═══════════╬═════════════════╬═══════════╬══════════╣
-- ║ R2    ║ 08:30      ║ 2         ║ Colbert         ║ VH-003    ║ 10:00    ║
-- ║ R3    ║ 08:30      ║ 2         ║ Ibis            ║ VH-003    ║ (groupe) ║
-- ║ R4    ║ 08:30      ║ 2         ║ Novotel         ║ VH-003    ║ (groupe) ║
-- ╠═══════╬════════════╬═══════════╬═════════════════╬═══════════╬══════════╣
-- ║ R5    ║ 10:30      ║ 11        ║ Lokanga         ║ VH-006    ║ 12:30    ║
-- ║ R6    ║ 10:30      ║ 2         ║ Colbert         ║ VH-006    ║ (groupe) ║
-- ╠═══════╬════════════╬═══════════╬═════════════════╬═══════════╬══════════╣
-- ║ R7    ║ 12:00      ║ 5         ║ Novotel         ║ VH-003    ║ 13:30    ║
-- ╠═══════╬════════════╬═══════════╬═════════════════╬═══════════╬══════════╣
-- ║ R8    ║ 14:00      ║ 2         ║ Carlton         ║ VH-003    ║ 16:00    ║
-- ║ R9    ║ 14:00      ║ 2         ║ Ibis            ║ VH-003    ║ (groupe) ║
-- ║ R10   ║ 14:00      ║ 2         ║ Lokanga         ║ VH-003    ║ (groupe) ║
-- ╠═══════╬════════════╬═══════════╬═════════════════╬═══════════╬══════════╣
-- ║ R11   ║ 15:30      ║ 2         ║ Colbert         ║ VH-002    ║ 16:30    ║
-- ║ R12   ║ 15:30      ║ 2         ║ Panorama        ║ VH-002    ║ (groupe) ║
-- ╠═══════╬════════════╬═══════════╬═════════════════╬═══════════╬══════════╣
-- ║ R13   ║ 16:00      ║ 3         ║ Ibis            ║ VH-001    ║ 17:12    ║
-- ╠═══════╬════════════╬═══════════╬═════════════════╬═══════════╬══════════╣
-- ║ R14   ║ 18:00      ║ 4         ║ Colbert         ║ VH-003    ║ 19:30    ║
-- ║ R15   ║ 18:00      ║ 3         ║ Novotel         ║ VH-003    ║ (groupe) ║
-- ╚═══════╩════════════╩═══════════╩═════════════════╩═══════════╩══════════╝
--
-- NOTES:
-- - R5/R6: 13 passagers regroupes dans VH-006 (15 places) - plus petit vehicule suffisant
-- - R8/R9: Carlton depose avant Ibis car "Carlton" < "Ibis" (alphabetique)
-- - R11/R12: Colbert depose avant Panorama car "Colbert" < "Panorama"
-- - R13: VH-001 (Essence) car VH-002 occupe (15:30-16:30) et VH-003 occupe (14:00-16:00)


-- =========================================
-- 6. RESERVATIONS DE TEST (15 reservations)
-- =========================================
-- Date: 11 Mars 2026 - Executer ce script puis lancer l'assignation automatique

-- TEST 1: Regles de base (F1, F2, F3) - Diesel prefere
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('Client1', 7, '2026-03-12 09:00:00', 1);

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('Client2', 11, '2026-03-12 09:00:00', 1);

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('Client3', 3, '2026-03-12 09:10:00', 1);

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('Client4', 1, '2026-03-12 09:00:00', 1);

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('Client5', 2, '2026-03-12 09:00:00', 1);

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
    ('Client6', 20, '2026-03-12 09:00:00', 1);

-- -- TEST 2: Regroupement simple (F5, F7) - Nearest-neighbour
-- INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
--     ('R2-Bob Dupont', 2, '2026-03-11 08:30:00', 1);

-- INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
--     ('R3-Claire Durand', 2, '2026-03-11 08:30:00', 3);

-- INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
--     ('R4-David Bernard', 2, '2026-03-11 08:30:00', 2);

-- -- TEST 3: Priorite passagers DESC (F4) - Pas de regroupement car > capacite max
-- INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
--     ('R5-Emma Petit', 11, '2026-03-11 10:30:00', 4);

-- INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
--     ('R6-Francois Moreau', 2, '2026-03-11 10:30:00', 1);

-- -- TEST 4: Chevauchement simple (F6) - VH-005 occupe
-- INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
--     ('R7-Gabrielle Simon', 5, '2026-03-11 12:00:00', 2);

-- -- TEST 5: Departage ALPHABETIQUE (F8) - Carlton < Ibis (meme distance 18km)
-- INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
--     ('R8-Henri Laurent', 2, '2026-03-11 14:00:00', 5);

-- INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
--     ('R9-Isabelle Roux', 2, '2026-03-11 14:00:00', 3);

-- INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
--     ('R10-Jean Leroy', 2, '2026-03-11 14:00:00', 4);

-- -- TEST 6: Chevauchement + Regroupement + Alphabetique (F9) - Colbert < Panorama
-- INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
--     ('R11-Kevin Blanc', 2, '2026-03-11 15:30:00', 1);

-- INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
--     ('R12-Lucie Mercier', 2, '2026-03-11 15:30:00', 6);

-- -- TEST 7: Vehicules petits occupes (F1, F2, F6)
-- INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
--     ('R13-Marie Fournier', 3, '2026-03-11 16:00:00', 3);

-- -- TEST 8: Regroupement capacite exacte (F5) - 7 passagers dans VH-003 (7 places)
-- INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
--     ('R14-Nicolas Garnier', 4, '2026-03-11 18:00:00', 1);

-- INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
--     ('R15-Olivier Perrin', 3, '2026-03-11 18:00:00', 2);


-- -- =========================================
-- -- 7. VERIFICATION (après assignation)
-- -- =========================================
-- -- SELECT 
-- --     reservation_id, client, nombre_passager,
-- --     date_heure_arrivee, hotel, vehicule,
-- --     distance_km, duree_totale_minutes || ' min' AS duree_trajet
-- -- FROM v_historique_assignation
-- -- ORDER BY date_heure_arrivee, hotel;
