DELETE FROM reservation;

-- ═══════════════════════════════════════════════════
-- DÉPART 1 : 07:00 — 1 réservation seule
-- Fenêtre [07:00, 07:30]
-- R1 (3 pax) → VH-002 (4p, Diesel) places restantes = 1
-- ═══════════════════════════════════════════════════
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES ('R1-Alice Martin', 3, '2026-03-11 07:00:00', 1);

-- ═══════════════════════════════════════════════════
-- DÉPART 2 : 08:25 — 3 résas, multi-véhicules
-- Fenêtre [08:00, 08:30]
-- Tri pax DESC : R2(15) → R3(10) → R4(5)
--
-- R2 (15 pax → Colbert) : cherche meilleur véhicule
--    VH-006(15p,D) reste=15 → plus proche de 15 ✓
-- R3 (10 pax → Novotel) : VH-006 reste=0 ✗
--    VH-005(12p,D) reste=12 → plus proche de 10 ✓
-- R4 (5 pax → Ibis) : VH-006 reste=0 ✗, VH-005 reste=2 ✗
--    VH-003(7p,D) reste=7 → plus proche de 5 ✓
--
-- Résultat : VH-006[R2], VH-005[R3], VH-003[R4]
-- Attentes : R2=25min, R3=15min, R4=0min
-- ═══════════════════════════════════════════════════
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES ('R2-Groupe Alpha', 15, '2026-03-11 08:00:00', 1);
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES ('R3-Groupe Beta', 10, '2026-03-11 08:10:00', 2);
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES ('R4-Groupe Gamma', 5, '2026-03-11 08:25:00', 3);

-- ═══════════════════════════════════════════════════
-- DÉPART 3 : 11:20 — 2 résas dans MÊME véhicule
-- Fenêtre [11:00, 11:30]
-- Tri pax DESC : R5(4) → R6(2)
--
-- R5 (4 pax → Carlton) : VH-003(7p,D) reste=7 → ✓
-- R6 (2 pax → Lokanga) : VH-003 reste=3 ≥ 2 → ✓ même véhicule !
--
-- Résultat : VH-003[R5, R6] — 6 pax dans 7 places
-- Nearest-neighbour : TNR → Carlton(18km) → Lokanga(14km)
-- Attentes : R5=20min, R6=0min
-- ═══════════════════════════════════════════════════
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES ('R5-Famille Dupont', 4, '2026-03-11 11:00:00', 5);
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES ('R6-Couple Martin', 2, '2026-03-11 11:20:00', 4);

-- ═══════════════════════════════════════════════════
-- DÉPART 4 : 14:15 — 3 résas, 2 dans même véhicule + 1 séparé
-- Fenêtre [14:00, 14:30]
-- Tri pax DESC : R7(5) → R8(3) → R9(2)
--
-- R7 (5 pax → Novotel) : VH-003(7p,D) reste=7 → ✓
-- R8 (3 pax → Colbert) : VH-003 reste=2 < 3 ✗
--    VH-002(4p,D) reste=4 → plus proche de 3 ✓
-- R9 (2 pax → Panorama) : VH-003 reste=2 ≥ 2 → ✓ même véhicule que R7 !
--
-- Résultat : VH-003[R7,R9] (7 pax), VH-002[R8] (3 pax)
-- VH-003 nearest-neighbour : TNR → Panorama(15km) → Novotel(9km)
-- Attentes : R7=15min, R8=10min, R9=0min
-- ═══════════════════════════════════════════════════
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES ('R7-Paul Durand', 5, '2026-03-11 14:00:00', 2);
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES ('R8-Marie Lambert', 3, '2026-03-11 14:05:00', 1);
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES ('R9-Sophie Blanc', 2, '2026-03-11 14:15:00', 6);

-- ═══════════════════════════════════════════════════
-- DÉPART 5 : 16:30 — Départage Diesel (égalité capacité)
-- Fenêtre [16:00, 16:30]
-- Tri pax DESC : R10(6) → R11(3)
--
-- R10 (6 pax → Ibis) : VH-003(7p,D) reste=7 → ✓
-- R11 (3 pax → Carlton) : VH-003 reste=1 < 3 ✗
--    VH-002(4p,D) reste=4 vs VH-001(4p,ES) reste=4
--    Égalité 4 places → Diesel préféré → VH-002 ✓
--
-- Résultat : VH-003[R10], VH-002[R11]
-- Attentes : R10=30min, R11=0min
-- ═══════════════════════════════════════════════════
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES ('R10-Equipe Sport', 6, '2026-03-11 16:00:00', 3);
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES ('R11-Jean Petit', 3, '2026-03-11 16:30:00', 5);

-- ═══════════════════════════════════════════════════
-- DÉPART 6 : 18:20 — Départage alphabétique (même distance)
-- Fenêtre [18:00, 18:30]
-- Tri pax DESC : R12(3) → R13(2)
--
-- R12 (3 pax → Colbert 15km) : VH-002(4p,D) reste=4 → ✓
-- R13 (2 pax → Panorama 15km) : VH-002 reste=1 < 2 ✗
--    VH-001(4p,ES) reste=4 → ✓
--
-- VH-002[R12] nearest-neighbour : TNR → Colbert
-- VH-001[R13] nearest-neighbour : TNR → Panorama
-- Colbert(15km) = Panorama(15km) → départage alphabétique : Colbert < Panorama
-- Attentes : R12=20min, R13=0min
-- ═══════════════════════════════════════════════════
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES ('R12-Nicolas Garnier', 3, '2026-03-11 18:00:00', 1);
INSERT INTO reservation (client, nombre_passager, date_heure_arrivee, id_hotel) VALUES ('R13-Olivier Perrin', 2, '2026-03-11 18:20:00', 6);