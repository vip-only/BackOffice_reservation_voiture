-- =========================================
-- SCENARIOS DE TEST - SPRINT 8
-- Prerequis:
--   1) Executer db/reset-db-sprint8.sql
--   2) Demarrer l'application
--   3) Lancer POST /sprint8/executer avec date = 2026-03-11
-- =========================================

-- Nettoyage metier (on garde referentiels)
TRUNCATE TABLE reservation_vehicule RESTART IDENTITY;
TRUNCATE TABLE reservation RESTART IDENTITY;

-- -----------------------------------------------------------------
-- CAS A: charge_attente >= capacite vehicule qui revient => DEPART IMMEDIAT
-- Vehicule VH-RET-8 (8 places) doit revenir a 14:00
-- -----------------------------------------------------------------

-- Reservation deja assignee pour generer un retour a 14:00:
-- Hotel Lokanga = 30 km, vitesse = 30 km/h => duree AR = 120 min
-- 12:00 + 120 min = 14:00
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT
    'RET-TRIP-14H',
    2,
    TIMESTAMP '2026-03-11 12:00:00',
    h.id_hotel,
    v.id
FROM hotel h
JOIN vehicule v ON v.reference = 'VH-RET-8'
WHERE h.nom = 'Lokanga';

-- Backlog non assigne avant 14:00 = 9 passagers (5 + 4)
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'BA-01', 5, TIMESTAMP '2026-03-11 13:10:00', h.id_hotel, NULL
FROM hotel h WHERE h.nom = 'Colbert';

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'BA-02', 4, TIMESTAMP '2026-03-11 13:20:00', h.id_hotel, NULL
FROM hotel h WHERE h.nom = 'Ibis';

-- Controle avant execution Sprint 8
SELECT 'CAS A - AVANT EXECUTION' AS phase;
SELECT
    COUNT(*) FILTER (WHERE id_vehicule IS NULL) AS nb_non_assignees,
    COALESCE(SUM(nombre_passager) FILTER (WHERE id_vehicule IS NULL), 0) AS charge_attente
FROM reservation
WHERE DATE(date_heure_arrivee) = DATE '2026-03-11'
  AND date_heure_arrivee <= TIMESTAMP '2026-03-11 14:00:00';

-- >>> Action manuelle a faire maintenant <<<
-- POST /sprint8/executer date=2026-03-11

-- Verifications apres execution CAS A
SELECT 'CAS A - APRES EXECUTION' AS phase;
SELECT mode_assignation, COUNT(*) AS nb_lignes, COALESCE(SUM(nb_passagers), 0) AS total_pax
FROM reservation_vehicule
GROUP BY mode_assignation
ORDER BY mode_assignation;

SELECT
    rv.reservation_id,
    r.client,
    rv.mode_assignation,
    rv.nb_passagers,
    rv.date_assignation,
    v.reference AS vehicule
FROM reservation_vehicule rv
JOIN reservation r ON r.id = rv.reservation_id
JOIN vehicule v ON v.id = rv.vehicule_id
WHERE DATE(rv.date_assignation) = DATE '2026-03-11'
ORDER BY rv.date_assignation, rv.reservation_id;

-- -----------------------------------------------------------------
-- CAS B: charge_attente < capacite retour => REPORT TA
-- -----------------------------------------------------------------

TRUNCATE TABLE reservation_vehicule RESTART IDENTITY;
TRUNCATE TABLE reservation RESTART IDENTITY;

-- Meme vehicule revient a 14:00
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT
    'RET-TRIP-14H-B',
    2,
    TIMESTAMP '2026-03-11 12:00:00',
    h.id_hotel,
    v.id
FROM hotel h
JOIN vehicule v ON v.reference = 'VH-RET-8'
WHERE h.nom = 'Lokanga';

-- Backlog = 5 (< 8)
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'BB-01', 3, TIMESTAMP '2026-03-11 13:05:00', h.id_hotel, NULL
FROM hotel h WHERE h.nom = 'Novotel';

INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
SELECT 'BB-02', 2, TIMESTAMP '2026-03-11 13:15:00', h.id_hotel, NULL
FROM hotel h WHERE h.nom = 'Colbert';

SELECT 'CAS B - AVANT EXECUTION' AS phase;
SELECT
    COUNT(*) FILTER (WHERE id_vehicule IS NULL) AS nb_non_assignees,
    COALESCE(SUM(nombre_passager) FILTER (WHERE id_vehicule IS NULL), 0) AS charge_attente
FROM reservation
WHERE DATE(date_heure_arrivee) = DATE '2026-03-11'
  AND date_heure_arrivee <= TIMESTAMP '2026-03-11 14:00:00';

-- >>> Action manuelle a faire maintenant <<<
-- POST /sprint8/executer date=2026-03-11

-- Verification cible CAS B:
-- Pas de trace RETOUR_IMMEDIAT attendue pour ce jeu si la regle report est appliquee.
SELECT 'CAS B - APRES EXECUTION' AS phase;
SELECT mode_assignation, COUNT(*) AS nb_lignes
FROM reservation_vehicule
GROUP BY mode_assignation
ORDER BY mode_assignation;

-- Controles generaux d'integrite
SELECT reservation_id, COUNT(*) AS nb_vehicules
FROM reservation_vehicule
GROUP BY reservation_id
HAVING COUNT(*) > 1
ORDER BY reservation_id;

SELECT id, client, nombre_passager, date_heure_arrivee, id_vehicule
FROM reservation
WHERE DATE(date_heure_arrivee) = DATE '2026-03-11'
ORDER BY date_heure_arrivee, id;
