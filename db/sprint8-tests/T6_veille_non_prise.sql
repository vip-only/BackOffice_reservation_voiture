-- T6: Reservations non assignees de la veille ne doivent pas declencher un depart immediat du jour

TRUNCATE TABLE reservation_vehicule, reservation RESTART IDENTITY CASCADE;

-- Retour 14:00 (jour cible 2026-03-11)
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'T6-SEED-RET8', 2, TIMESTAMP '2026-03-11 12:00:00', h.id_hotel, v.id
FROM hotel h, vehicule v
WHERE h.nom = 'Lokanga' AND v.reference = 'VH-RET-8';

-- Backlog veille (gros volume)
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'T6-YEST-1', 12, TIMESTAMP '2026-03-10 13:00:00', h.id_hotel, NULL FROM hotel h WHERE h.nom='Lokanga';
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'T6-YEST-2', 9, TIMESTAMP '2026-03-10 13:10:00', h.id_hotel, NULL FROM hotel h WHERE h.nom='Ibis';

-- Backlog du jour faible (<8)
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'T6-TODAY-1', 2, TIMESTAMP '2026-03-11 13:20:00', h.id_hotel, NULL FROM hotel h WHERE h.nom='Colbert';
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'T6-TODAY-2', 1, TIMESTAMP '2026-03-11 13:30:00', h.id_hotel, NULL FROM hotel h WHERE h.nom='Novotel';

-- ACTION: executer POST /sprint8/executer?date=2026-03-11

-- POST-ASSERTIONS
SELECT 'T6_ignore_veille_for_retour' AS test,
       CASE WHEN NOT EXISTS (
           SELECT 1
           FROM reservation_vehicule rv
           JOIN reservation r ON r.id = rv.reservation_id
           WHERE rv.mode_assignation = 'RETOUR_IMMEDIAT'
             AND r.client IN ('T6-TODAY-1', 'T6-TODAY-2')
       ) THEN 'PASS' ELSE 'FAIL' END AS resultat;
