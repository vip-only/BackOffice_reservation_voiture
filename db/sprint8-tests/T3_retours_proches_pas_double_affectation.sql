-- T3: Deux vehicules reviennent proches (14:00, 14:05) => pas de double affectation

TRUNCATE TABLE reservation_vehicule, reservation RESTART IDENTITY CASCADE;

-- Retour 14:00 (VH-RET-8)
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'T3-SEED-RET8', 2, TIMESTAMP '2026-03-11 12:00:00', h.id_hotel, v.id
FROM hotel h, vehicule v
WHERE h.nom = 'Lokanga' AND v.reference = 'VH-RET-8';

-- Retour 14:05 (VH-RET-12): 12:05 -> Lokanga => +120 = 14:05
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'T3-SEED-RET12', 2, TIMESTAMP '2026-03-11 12:05:00', h.id_hotel, v.id
FROM hotel h, vehicule v
WHERE h.nom = 'Lokanga' AND v.reference = 'VH-RET-12';

-- Backlog important
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'T3-BA-1', 7, TIMESTAMP '2026-03-11 13:00:00', h.id_hotel, NULL FROM hotel h WHERE h.nom='Colbert';
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'T3-BA-2', 6, TIMESTAMP '2026-03-11 13:10:00', h.id_hotel, NULL FROM hotel h WHERE h.nom='Ibis';
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'T3-BA-3', 5, TIMESTAMP '2026-03-11 13:20:00', h.id_hotel, NULL FROM hotel h WHERE h.nom='Novotel';

-- ACTION: executer POST /sprint8/executer?date=2026-03-11

-- POST-ASSERTIONS
SELECT 'T3_no_double_assign' AS test,
       CASE WHEN NOT EXISTS (
           SELECT rv.reservation_id
           FROM reservation_vehicule rv
           GROUP BY rv.reservation_id
           HAVING COUNT(*) > 1
       ) THEN 'PASS' ELSE 'FAIL' END AS resultat;
