-- T2: Vehicule revient a 14:00, backlog = 5, capacite = 8 => PAS de depart immediat (REPORT TA)

TRUNCATE TABLE reservation_vehicule RESTART IDENTITY;
TRUNCATE TABLE reservation RESTART IDENTITY;

-- Seed retour VH-RET-8 a 14:00
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'T2-SEED-RET8', 2, TIMESTAMP '2026-03-11 12:00:00', h.id_hotel, v.id
FROM hotel h, vehicule v
WHERE h.nom = 'Lokanga' AND v.reference = 'VH-RET-8';

-- Backlog = 5 (<8)
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'T2-BA-1', 3, TIMESTAMP '2026-03-11 13:05:00', h.id_hotel, NULL
FROM hotel h WHERE h.nom = 'Novotel';

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'T2-BA-2', 2, TIMESTAMP '2026-03-11 13:15:00', h.id_hotel, NULL
FROM hotel h WHERE h.nom = 'Colbert';

-- ACTION: executer POST /sprint8/executer?date=2026-03-11

-- POST-ASSERTIONS
SELECT 'T2_report_ta' AS test,
       CASE WHEN NOT EXISTS (
           SELECT 1
           FROM reservation_vehicule rv
           JOIN reservation r ON r.id = rv.reservation_id
           WHERE rv.mode_assignation = 'RETOUR_IMMEDIAT'
             AND r.client IN ('T2-BA-1', 'T2-BA-2')
       ) THEN 'PASS' ELSE 'FAIL' END AS resultat;
