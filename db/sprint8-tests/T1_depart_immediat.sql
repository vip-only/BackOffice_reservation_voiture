-- T1: Vehicule revient a 14:00, backlog = 9, capacite = 8 => DEPART IMMEDIAT attendu

TRUNCATE TABLE reservation_vehicule, reservation RESTART IDENTITY CASCADE;

-- Seed retour VH-RET-8 a 14:00 (12:00 vers Lokanga, AR=120 min)
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'T1-SEED-RET8', 2, TIMESTAMP '2026-03-11 12:00:00', h.id_hotel, v.id
FROM hotel h, vehicule v
WHERE h.nom = 'Lokanga' AND v.reference = 'VH-RET-8';

-- Backlog non assigne avant 14:00 => 9 pax
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'T1-BA-1', 5, TIMESTAMP '2026-03-11 13:10:00', h.id_hotel, NULL
FROM hotel h WHERE h.nom = 'Colbert';

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'T1-BA-2', 4, TIMESTAMP '2026-03-11 13:20:00', h.id_hotel, NULL
FROM hotel h WHERE h.nom = 'Ibis';

-- PRE-CHECK
SELECT 'T1_PRE_CHARGE' AS test,
       COALESCE(SUM(nombre_passager),0) AS charge
FROM reservation
WHERE id_vehicule IS NULL
  AND DATE(date_heure_arrivee)=DATE '2026-03-11'
  AND date_heure_arrivee <= TIMESTAMP '2026-03-11 14:00:00';

-- ACTION: executer POST /sprint8/executer?date=2026-03-11

-- POST-ASSERTIONS
SELECT 'T1_depart_immediat' AS test,
       CASE WHEN EXISTS (
           SELECT 1
           FROM reservation_vehicule rv
           JOIN reservation r ON r.id = rv.reservation_id
           WHERE rv.mode_assignation = 'RETOUR_IMMEDIAT'
             AND r.client IN ('T1-BA-1', 'T1-BA-2')
       ) THEN 'PASS' ELSE 'FAIL' END AS resultat;
