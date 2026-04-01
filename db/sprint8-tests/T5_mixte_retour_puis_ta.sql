-- T5: Cas mixte depart immediat puis fenetre TA suivante

TRUNCATE TABLE reservation_vehicule RESTART IDENTITY;
TRUNCATE TABLE reservation RESTART IDENTITY;

-- Retour 10:00 (VH-RET-8)
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'T5-SEED-RET8', 2, TIMESTAMP '2026-03-11 09:00:00', h.id_hotel, v.id
FROM hotel h, vehicule v
WHERE h.nom = 'Colbert' AND v.reference = 'VH-RET-8';

-- Backlog avant 10:00 => depart immediat
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'T5-IMM-1', 5, TIMESTAMP '2026-03-11 09:20:00', h.id_hotel, NULL FROM hotel h WHERE h.nom='Ibis';
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'T5-IMM-2', 4, TIMESTAMP '2026-03-11 09:25:00', h.id_hotel, NULL FROM hotel h WHERE h.nom='Novotel';

-- Nouvelles reservations fenetre TA apres 10:00
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'T5-TA-1', 3, TIMESTAMP '2026-03-11 10:20:00', h.id_hotel, NULL FROM hotel h WHERE h.nom='Colbert';
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'T5-TA-2', 2, TIMESTAMP '2026-03-11 10:35:00', h.id_hotel, NULL FROM hotel h WHERE h.nom='Panorama';

-- ACTION: executer POST /sprint8/executer?date=2026-03-11

-- POST-ASSERTIONS
SELECT 'T5_has_retour_mode' AS test,
       CASE WHEN EXISTS (
           SELECT 1 FROM reservation_vehicule rv
           JOIN reservation r ON r.id = rv.reservation_id
           WHERE r.client LIKE 'T5-%' AND rv.mode_assignation = 'RETOUR_IMMEDIAT'
       ) THEN 'PASS' ELSE 'FAIL' END AS resultat;

SELECT 'T5_has_normal_ta_mode' AS test,
       CASE WHEN EXISTS (
           SELECT 1 FROM reservation_vehicule rv
           JOIN reservation r ON r.id = rv.reservation_id
           WHERE r.client LIKE 'T5-%' AND rv.mode_assignation = 'NORMAL_TA'
       ) THEN 'PASS' ELSE 'FAIL' END AS resultat;
