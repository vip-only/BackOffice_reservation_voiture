-- ============================================
-- Scenario de test simple (base sprint 7)
-- Objectif:
-- 1) Cas avec passagers non assignes restants (prioritaires ensuite)
-- 2) Cas avec vehicules pleins sans non assignes
-- ============================================

BEGIN;

TRUNCATE TABLE planification RESTART IDENTITY CASCADE;
TRUNCATE TABLE reservation RESTART IDENTITY CASCADE;
TRUNCATE TABLE distance RESTART IDENTITY CASCADE;
TRUNCATE TABLE parametre RESTART IDENTITY CASCADE;
TRUNCATE TABLE vehicule RESTART IDENTITY CASCADE;
TRUNCATE TABLE aeroport RESTART IDENTITY CASCADE;
TRUNCATE TABLE hotel RESTART IDENTITY CASCADE;
TRUNCATE TABLE lieux RESTART IDENTITY CASCADE;

-- Lieux: uniquement aeroport + 1 hotel
INSERT INTO lieux (lieu) VALUES
('aeroport'),
('hotel1');

-- Hotel unique (lieux_id = 2)
INSERT INTO hotel (nom, adresse, ville, lieux_id) VALUES
('hotel1', 'adresse hotel1', 'ville1', 2);

-- Aeroport (lieux_id = 1)
INSERT INTO aeroport (code, libelle, lieux_id) VALUES
('AER', 'aeroport', 1);

 
-- 2 vehicules simples, tous disponibles a minuit
INSERT INTO vehicule (reference, nombre_place, type_carburant, heure_disponibilite) VALUES
('vehicule1', 4, 'D',  '00:00:00'),
('vehicule2', 4, 'ES', '00:00:00');

-- Parametre global
INSERT INTO parametre (temps_attente, vitesse_moyenne) VALUES
(30, 50);

-- Distances aller/retour aeroport <-> hotel1
INSERT INTO distance (lieux_from, lieux_to, valeur) VALUES
(1, 2, 40);

-- ====================================================
-- CAS 1: non assignes restants
-- Capacite totale instantanee = 12, demande = 14 => 2 non assignes
-- ====================================================
INSERT INTO reservation (client_id, nombre_passager, date_arrivee, hotel_id, aeroport_id) VALUES
('c1', 14, '2026-04-01 08:00:00', 1, 1);

-- Reservation suivante proche:
-- en theorie les non assignes precedents restent prioritaires
INSERT INTO reservation (client_id, nombre_passager, date_arrivee, hotel_id, aeroport_id) VALUES
('c2', 12, '2026-04-01 08:00:00', 1, 1);

-- ====================================================
-- CAS 2: regroupement declenche par retour vehicule
-- Reservation a 09:00, alors que les vehicules sont encore en course
-- => la planification doit attendre un retour de disponibilite
-- Demande =00  8 (2 vehicules pleins), sans non assignes
-- ====================================================
INSERT INTO reservation (client_id, nombre_passager, date_arrivee, hotel_id, aeroport_id) VALUES
('c3', 3, '2026-04-01 12:00:00', 1, 1);

COMMIT;

