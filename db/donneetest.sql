-- 1) Nettoyage scenario (garde les tables)
DELETE FROM reservation_vehicule;
DELETE FROM reservation;

-- Optionnel: remettre la flotte exactement comme ton exemple
DELETE FROM vehicule;
INSERT INTO vehicule (reference, nombre_place, type_carburant) VALUES
('V1-8P', 8, 'D'),
('V2-3P', 3, 'ES');

-- Vérifier/assurer les prerequis minimaux
-- hotel id=1 doit exister
-- distance TNR -> hotel 1 doit exister
-- parametre TA / VITESSE_MOYENNE doit exister

-- 2) Reservations du cas test
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
('R1-6P', 6, '2026-03-19 08:00:00', 1),
('R2-4P', 4, '2026-03-19 08:05:00', 1),
('R3-3P', 3, '2026-03-19 08:10:00', 1);



--Cas 2: R1c moins de trajets (capacité égale)
DELETE FROM reservation_vehicule;
DELETE FROM reservation;
DELETE FROM vehicule;

INSERT INTO vehicule(reference, nombre_place, type_carburant) VALUES
('V1-6P', 6, 'D'),
('V2-6P', 6, 'D');

INSERT INTO reservation(client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
('A1', 6, '2026-03-21 06:00:00', 1),
('A2', 6, '2026-03-21 08:10:00', 1);


--Cas 3: R1d Diesel prioritaire à égalité parfait
DELETE FROM reservation_vehicule;
DELETE FROM reservation;
DELETE FROM vehicule;

INSERT INTO vehicule(reference, nombre_place, type_carburant) VALUES
('V1-4P-ES', 4, 'ES'),
('V2-4P-D',  4, 'D');

INSERT INTO reservation(client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
('D1', 4, '2026-03-22 09:00:00', 1);

--Cas 4: Fractionnement fort + non assignés multiples
--Date test: 2026-03-23


DELETE FROM reservation_vehicule;
DELETE FROM reservation;
DELETE FROM vehicule;

INSERT INTO vehicule(reference, nombre_place, type_carburant) VALUES
('V1-7P', 7, 'D'),
('V2-4P', 4, 'ES');

INSERT INTO reservation(client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
('F1', 9, '2026-03-23 08:00:00', 1),
('F2', 6, '2026-03-23 08:07:00', 1),
('F3', 5, '2026-03-23 08:12:00', 1);


-- Cas 5: TA = 30 min, deux fenetres, priorite vehicule sans trajet
-- Objectif:
-- 1) Fenetre 1 (08:00-08:30): remplir d'abord V1
-- 2) Fenetre 2 (10:40): V1 et V2 sont dispo, priorite a V2 (0 trajet)
UPDATE parametre SET valeur = 30 WHERE cle = 'TA';

DELETE FROM reservation_vehicule;
DELETE FROM reservation;
DELETE FROM vehicule;

INSERT INTO vehicule(reference, nombre_place, type_carburant) VALUES
('T30-V1-10P', 10, 'D'),
('T30-V2-10P', 10, 'D');

INSERT INTO reservation(client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
('T30-R1', 6, '2026-03-24 08:00:00', 1),
('T30-R2', 3, '2026-03-24 08:10:00', 1),
('T30-R3', 5, '2026-03-24 10:40:00', 1);

-- Attendu:
-- Fenetre 1: R1 et R2 dans le meme vehicule (fill-first)
-- Fenetre 2: R3 doit prendre l'autre vehicule (celui avec 0 trajet)


-- Cas 6: TA = 60 min, regroupement large + reutilisation controlee
-- Objectif:
-- 1) Fenetre 1 (08:00-09:00): R1/R2 regroupes et remplissage prioritaire
-- 2) Fenetre 2 (10:10+): priorite au vehicule avec moins de trajets
-- 3) Verifier reutilisation si le vehicule prioritaire n'a plus assez de place
UPDATE parametre SET valeur = 60 WHERE cle = 'TA';

DELETE FROM reservation_vehicule;
DELETE FROM reservation;
DELETE FROM vehicule;

INSERT INTO vehicule(reference, nombre_place, type_carburant) VALUES
('T60-V1-8P', 8, 'D'),
('T60-V2-8P', 8, 'ES');

INSERT INTO reservation(client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
('T60-R1', 6, '2026-03-25 08:00:00', 1),
('T60-R2', 2, '2026-03-25 08:50:00', 1),
('T60-R3', 6, '2026-03-25 10:10:00', 1),
('T60-R4', 4, '2026-03-25 10:20:00', 1);

-- Attendu:
-- Fenetre 1: R1 puis R2 dans le meme vehicule (8 rempli)
-- Fenetre 2: R3 doit prioriser le vehicule avec 0 trajet
-- Puis R4 remplit d'abord la place restante de ce vehicule, reliquat sur l'autre si besoin


-- Cas 7: Scenario combine (probable et plus difficile)
-- Objectif combine:
-- 1) Reutilisation d'un vehicule apres retour de trajet
-- 2) Tous les vehicules avec le meme nombre de trajets avant une fenetre critique
-- 3) A egalite de trajets/capacite, priorite Diesel
-- 4) Fill-first dans la fenetre puis fractionnement
UPDATE parametre SET valeur = 30 WHERE cle = 'TA';

DELETE FROM reservation_vehicule;
DELETE FROM reservation;
DELETE FROM vehicule;

INSERT INTO vehicule(reference, nombre_place, type_carburant) VALUES
('CMB-V1-6P-D', 6, 'D'),
('CMB-V2-6P-ES', 6, 'ES'),
('CMB-V3-4P-D', 4, 'D');

-- Date test: 2026-03-26
-- Fenetre A (08:00-08:30)
INSERT INTO reservation(client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
('CMB-R1', 6, '2026-03-26 08:00:00', 1);

-- Fenetre B (10:20-10:50) -> priorite vehicule sans trajet
INSERT INTO reservation(client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
('CMB-R2', 6, '2026-03-26 10:20:00', 1);

-- Fenetre C (12:40-13:10) -> priorite au dernier vehicule sans trajet
INSERT INTO reservation(client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
('CMB-R3', 4, '2026-03-26 12:40:00', 1);

-- A ce stade, chaque vehicule doit avoir 1 trajet.
-- Fenetre D (15:00-15:30): egalite de trajets + regles combinees
INSERT INTO reservation(client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
('CMB-R4', 5, '2026-03-26 15:00:00', 1),
('CMB-R5', 4, '2026-03-26 15:05:00', 1),
('CMB-R6', 7, '2026-03-26 15:10:00', 1);

-- Attendu (ordre logique corrige, tri passagers DESC):
-- 1) R6(7) est traite en premier:
--    - 6 passagers dans V1-6P-D (egalite de capacite/trajets avec V2, priorite Diesel)
--    - 1 passager restant dans V2-6P-ES (en fractionnement on privilegie grande capacite)
-- 2) R4(5) ensuite: remplit d'abord V2 deja mobilise (reste 5) -> R4 dans V2
-- 3) R5(4) ensuite: V1 et V2 sont pleins dans la fenetre, prend V3-4P-D
-- 4) Aucun non assigne attendu sur ce cas.


-- Cas 8: Report multi-TA des non assignes (TA1 -> TA2 -> TA3)
-- Objectif:
-- 1) Forcer des non assignes en fenetre 1
-- 2) Verifier qu'ils sont retentes en fenetre 2 tout en respectant les priorites
-- 3) Si encore non assignes, ils sont retentes en fenetre 3
UPDATE parametre SET valeur = 30 WHERE cle = 'TA';

DELETE FROM reservation_vehicule;
DELETE FROM reservation;
DELETE FROM vehicule;

INSERT INTO vehicule(reference, nombre_place, type_carburant) VALUES
('CR-V1-5P-D', 5, 'D'),
('CR-V2-5P-ES', 5, 'ES');

-- Date test: 2026-03-27
-- Fenetre 1 (08:00-08:30): demande 13 pour capacite 10 -> reliquat attendu = 3
INSERT INTO reservation(client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
('CR-R1', 9, '2026-03-27 08:00:00', 1),
('CR-R2', 4, '2026-03-27 08:10:00', 1);

-- Fenetre 2 (10:40-11:10): nouvelle demande 9; avec reliquat 3 => 12 pour capacite 10
-- reliquat attendu apres fenetre 2 = 2
INSERT INTO reservation(client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
('CR-R3', 9, '2026-03-27 10:40:00', 1);

-- Fenetre 3 (13:20-13:50): nouvelle demande 1; avec reliquat 2 => 3, tout doit passer
INSERT INTO reservation(client, nombre_passager, date_heure_arrivee, id_hotel) VALUES
('CR-R4', 1, '2026-03-27 13:20:00', 1);

-- Attendu si la regle de report multi-TA est bien appliquee:
-- - Non assignes en fenetre 1, puis reportes en fenetre 2
-- - Si encore non assignes en fenetre 2, reportes en fenetre 3
-- - Non assignes finaux = 0

-- Verification conseillee:
-- SELECT id, client, nombre_passager, date_heure_arrivee, id_vehicule
-- FROM reservation
-- WHERE DATE(date_heure_arrivee) = '2026-03-27'
-- ORDER BY date_heure_arrivee, nombre_passager DESC, id;
--
-- SELECT reservation_id, vehicule_id, nb_passagers, date_assignation
-- FROM reservation_vehicule
-- ORDER BY date_assignation, reservation_id, vehicule_id;