-- T7: Egalite capacite / trajets / diesel => priorites Sprint 6-7 conservees

TRUNCATE TABLE reservation_vehicule, reservation RESTART IDENTITY CASCADE;

-- Vehicules dedies au test (independants de la flotte du reset)
DELETE FROM vehicule WHERE reference IN ('T7-VH-5-D-A', 'T7-VH-5-ES-A', 'T7-VH-5-D-BUSY');

INSERT INTO vehicule (reference, nombre_place, type_carburant, heure_disponibilite)
VALUES
  ('T7-VH-5-D-A', 5, 'D', '00:00:00'),
  ('T7-VH-5-ES-A', 5, 'ES', '00:00:00'),
  ('T7-VH-5-D-BUSY', 5, 'D', '00:00:00');

-- Vehicule busy (2 trajets deja faits) pour departage nb trajets
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'T7-SEED-BUSY-1', 1, TIMESTAMP '2026-03-11 06:00:00', h.id_hotel, v.id
FROM hotel h, vehicule v
WHERE h.nom='Novotel' AND v.reference='T7-VH-5-D-BUSY';

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'T7-SEED-BUSY-2', 1, TIMESTAMP '2026-03-11 07:00:00', h.id_hotel, v.id
FROM hotel h, vehicule v
WHERE h.nom='Ibis' AND v.reference='T7-VH-5-D-BUSY';

-- Deux reservations de 5 places
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'T7-EQ-1', 5, TIMESTAMP '2026-03-11 09:00:00', h.id_hotel, NULL FROM hotel h WHERE h.nom='Colbert';
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'T7-EQ-2', 5, TIMESTAMP '2026-03-11 09:05:00', h.id_hotel, NULL FROM hotel h WHERE h.nom='Ibis';

-- ACTION: executer POST /sprint8/executer?date=2026-03-11

-- POST-ASSERTIONS
-- T7-EQ-1 doit prioritairement aller sur diesel 5 places non-busy: VH-5-D-A
SELECT 'T7_diesel_priority' AS test,
       CASE WHEN EXISTS (
           SELECT 1
           FROM reservation r
           JOIN vehicule v ON v.id = r.id_vehicule
           WHERE r.client = 'T7-EQ-1'
             AND v.reference = 'T7-VH-5-D-A'
       ) THEN 'PASS' ELSE 'FAIL' END AS resultat;

-- Le vehicule busy ne doit pas etre prioritaire face a un diesel meme capacite avec moins de trajets
SELECT 'T7_less_trips_priority' AS test,
       CASE WHEN NOT EXISTS (
           SELECT 1
           FROM reservation r
           JOIN vehicule v ON v.id = r.id_vehicule
           WHERE r.client = 'T7-EQ-1'
             AND v.reference = 'T7-VH-5-D-BUSY'
       ) THEN 'PASS' ELSE 'FAIL' END AS resultat;
