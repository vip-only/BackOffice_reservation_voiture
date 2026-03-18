### Livrables sprint5
- Code :
  - mise à jour PlanificationService avec regroupement par fenêtre TA (Temps d'Attente)
  - report des réservations non assignées
- SQL scripts (s.sql, 02-06-2026.sql mis à jour)

---

## BackOffice - To Do (Sprint 7)

### Objectif
Améliorer l'algorithme d'assignation en y intégrant une répartition équitable de la charge de travail (**le moins de trajets aujourd'hui**) et en autorisant la **séparation des passagers** d'une même réservation si la capacité des véhicules l'exige.

### Fonctionnalités (liste)
- [ ] **Séparation des personnes (Fractionnement)** : Si le nombre de passagers d'une réservation dépasse la capacité du plus grand véhicule disponible, la réservation est divisée sur plusieurs véhicules.
- [ ] **Répartition de charge** : À capacité égale, privilégier le véhicule ayant effectué le moins de trajets sur la journée en cours.
- [ ] Conserver toutes les règles du Sprint 5 (TA, Nearest-neighbour, Ordre de dépose, etc.).
- [ ] Mise à jour de l'algorithme `assignerVehiculesAutomatiquement()`.

### Contraintes / Règles métiers

#### 🔢 Nouvel ordre complet des règles d'assignement (par priorité décroissante)

**Étape 0 — Préparation du groupe** :
- **R0** : Trier les réservations par ordre décroissant du nombre de passagers.

**Étape 1 — Sélection du véhicule & Fractionnement** :

- **R1a — Véhicule déjà assigné** : Si un véhicule déjà assigné au groupe peut absorber tous les passagers de la réservation, l'utiliser.
- **R1b — Plus petite capacité satisfaisante (sans séparation)** : Chercher un véhicule libre dans la fenêtre TA dont la capacité est >= passagers.
- **R1c — Équité des trajets (NOUVEAU)** : En cas d'égalité sur R1b (plusieurs véhicules avec la même capacité suffisante), choisir celui qui a effectué **le moins de trajets aujourd'hui**.
- **R1d — Préférence Carburant** : En cas d'égalité sur R1b et R1c, choisir le **Diesel** en priorité.

**R1e — Séparation des passagers (NOUVEAU)** :
- Si *aucun* véhicule ne possède une capacité >= passagers de la réservation :
  - Prendre le véhicule disponible (respectant la fenêtre TA) ayant la **plus grande capacité**.
  - Assigner le maximum de passagers possibles à ce véhicule.
  - Le reste des passagers forme une "sous-réservation" qui sera traitée selon les mêmes règles (récursion ou boucle).

**Étape 2 & 3 — Heure de départ & Itinéraire (Inchangé)** :
- **R2** : `heure_depart_effective = MAX(MAX(date_heure_arrivee du groupe), heure_disponibilite_vehicule)`.
- **R3** : Nearest-neighbour pour l'ordre de dépose.
- **R4** : Départage alphabétique des hôtels si même distance.
- **R5** : Chevauchement calculé sur l'heure de départ effective.

### Données & schéma (rappels / modifications éventuelles)

- **Gestion du fractionnement** : 
  - Puisqu'une réservation peut avoir plusieurs véhicules, soit on modifie le modèle métier en base (`reservation_vehicule` ou ajout d'une colonne `id_reservation_parente`), soit on scinde l'objet métier en mémoire (clonage) et on insère de nouvelles lignes dans `reservation`.

### Implémentation technique

1. **Calcul des trajets du jour** :
   - Ajouter une méthode `compterTrajetsAujourdhui(Vehicule v, Date date)` ou enrichir la requête de recherche des véhicules disponibles.
2. **Logique de Fractionnement** :
   - Modifier `assignerGroupeReservations` : utiliser une boucle `while (paxRestants > 0)`.
3. **Mise à jour de `compareTo` ou `Comparator`** pour les véhicules :
   - Trier par capacité croissante (si >= besoin) ou décroissante (si fractionnement).
   - Puis par `nombreTrajetsJour` croissant.
   - Puis par `type_carburant` ('D' avant 'ES').

### Tests & critères d'acceptation

| Test | Scénario | Résultat attendu |
|------|----------|------------------|
| T1 | Réservation 15 pax, véhicules 10pax et 6pax dispos | Réservation scindée : 10 pax dans V10, 5 pax dans V6 |
| T2 | 5 pax. 2 véhicules 5 pax dispos (V1: 0 trajet, V2: 2 trajets) | V1 assigné (R1c : moins de trajets) |
| T3 | 5 pax. V1 (5 pax, 1 trajet, ES) vs V2 (5 pax, 1 trajet, D) | V2 assigné (R1d : priorité Diesel) |
| T4 | 18 pax. Flotte: V1(12p), V2(8p), V3(4p) | Scindé<!-- filepath: f:\Study\S5\Cluster\Projet_voiture\BackOffice\toDo-sprint7.md -->
### Livrables sprint5
- Code :
  - mise à jour PlanificationService avec regroupement par fenêtre TA (Temps d'Attente)
  - report des réservations non assignées
- SQL scripts (s.sql, 02-06-2026.sql mis à jour)

---

## BackOffice - To Do (Sprint 7)

### Objectif
Améliorer l'algorithme d'assignation en y intégrant une répartition équitable de la charge de travail (**le moins de trajets aujourd'hui**) et en autorisant la **séparation des passagers** d'une même réservation si la capacité des véhicules l'exige.

### Fonctionnalités (liste)
- [ ] **Séparation des personnes (Fractionnement)** : Si le nombre de passagers d'une réservation dépasse la capacité du plus grand véhicule disponible, la réservation est divisée sur plusieurs véhicules.
- [ ] **Répartition de charge** : À capacité égale, privilégier le véhicule ayant effectué le moins de trajets sur la journée en cours.
- [ ] Conserver toutes les règles du Sprint 5 (TA, Nearest-neighbour, Ordre de dépose, etc.).
- [ ] Mise à jour de l'algorithme `assignerVehiculesAutomatiquement()`.

### Contraintes / Règles métiers

#### 🔢 Nouvel ordre complet des règles d'assignement (par priorité décroissante)

**Étape 0 — Préparation du groupe** :
- **R0** : Trier les réservations par ordre décroissant du nombre de passagers.

**Étape 1 — Sélection du véhicule & Fractionnement** :

- **R1a — Véhicule déjà assigné** : Si un véhicule déjà assigné au groupe peut absorber tous les passagers de la réservation, l'utiliser.
- **R1b — Plus petite capacité satisfaisante (sans séparation)** : Chercher un véhicule libre dans la fenêtre TA dont la capacité est >= passagers.
- **R1c — Équité des trajets (NOUVEAU)** : En cas d'égalité sur R1b (plusieurs véhicules avec la même capacité suffisante), choisir celui qui a effectué **le moins de trajets aujourd'hui**.
- **R1d — Préférence Carburant** : En cas d'égalité sur R1b et R1c, choisir le **Diesel** en priorité.

**R1e — Séparation des passagers (NOUVEAU)** :
- Si *aucun* véhicule ne possède une capacité >= passagers de la réservation :
  - Prendre le véhicule disponible (respectant la fenêtre TA) ayant la **plus grande capacité**.
  - Assigner le maximum de passagers possibles à ce véhicule.
  - Le reste des passagers forme une "sous-réservation" qui sera traitée selon les mêmes règles (récursion ou boucle).

**Étape 2 & 3 — Heure de départ & Itinéraire (Inchangé)** :
- **R2** : `heure_depart_effective = MAX(MAX(date_heure_arrivee assignée dans le groupe), heure_disponibilite_vehicule)`.
- **R3** : Nearest-neighbour pour l'ordre de dépose.
- **R4** : Départage alphabétique des hôtels si même distance.
- **R5** : Chevauchement calculé sur l'heure de départ effective.

### Données & schéma (rappels / modifications éventuelles)

- **Gestion du fractionnement** : 
  - Puisqu'une réservation peut avoir plusieurs véhicules, soit on modifie le modèle métier en base (`reservation_vehicule` ou ajout d'une colonne `id_reservation_parente`), soit on scinde l'objet métier en mémoire (clonage) et on insère de nouvelles lignes dans `reservation`.

### Implémentation technique

1. **Calcul des trajets du jour** :
   - Ajouter une méthode `compterTrajetsAujourdhui(Vehicule v, Date date)` ou enrichir la requête de recherche des véhicules disponibles.
2. **Logique de Fractionnement** :
   - Modifier `assignerGroupeReservations` : utiliser une boucle `while (paxRestants > 0)`.
3. **Mise à jour de `compareTo` ou `Comparator`** pour les véhicules :
   - Trier par capacité croissante (si >= besoin) ou décroissante (si fractionnement).
   - Puis par `nombreTrajetsJour` croissant.
   - Puis par `type_carburant` ('D' avant 'ES').

### Tests & critères d'acceptation

| Test | Scénario | Résultat attendu |
|------|----------|------------------|
| T1 | Réservation 15 pax, véhicules 10pax et 6pax dispos | Réservation scindée : 10 pax dans V10, 5 pax dans V6 |
| T2 | 5 pax. 2 véhicules 5 pax dispos (V1: 0 trajet, V2: 2 trajets) | V1 assigné (R1c : moins de trajets) |
| T3 | 5 pax. V1 (5 pax, 1 trajet, ES) vs V2 (5 pax, 1 trajet, D) | V2 assigné (R1d : priorité Diesel) |
| T4 | 18 pax. Flotte: V1(12p), V2(8p), V3(4p) | Scindé