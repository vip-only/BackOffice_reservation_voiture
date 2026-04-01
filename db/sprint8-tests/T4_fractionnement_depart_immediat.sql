-- T4: Fractionnement lors d'un depart immediat
-- Cas: backlog contient une reservation de 11 pax, vehicule retour de 8 places

TRUNCATE TABLE reservation_vehicule RESTART IDENTITY;
TRUNCATE TABLE reservation RESTART IDENTITY;

-- Retour 14:00 (VH-RET-8)
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'T4-SEED-RET8', 2, TIMESTAMP '2026-03-11 12:00:00', h.id_hotel, v.id
FROM hotel h, vehicule v
WHERE h.nom = 'Lokanga' AND v.reference = 'VH-RET-8';

-- Reservation cible fractionnement
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'T4-BIG', 11, TIMESTAMP '2026-03-11 13:30:00', h.id_hotel, NULL
FROM hotel h WHERE h.nom = 'Lokanga';

-- ACTION: executer POST /sprint8/executer?date=2026-03-11

-- POST-ASSERTIONS
SELECT 'T4_split_row_created' AS test,
       CASE WHEN EXISTS (
           SELECT 1 FROM reservation WHERE client = 'T4-BIG (split)'
       ) THEN 'PASS' ELSE 'FAIL' END AS resultat;

SELECT 'T4_conservation_passagers' AS test,
       CASE WHEN (
           SELECT COALESCE(SUM(nombre_passager),0)
           FROM reservation
           WHERE client LIKE 'T4-BIG%'
       ) = 11 THEN 'PASS' ELSE 'FAIL' END AS resultat;
