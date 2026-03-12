### Livrables sprint4
- Code :
  - mise à jour PlanificationService (regroupement & routing)
  - nouveaux DAO si nécessaire (DistanceDAO, LieuDAO)
  - contrôleur /planification étendu + POST /planification/regrouper-assigner
  - JSP mise à jour (affichage groupes/itinéraires)
- SQL scripts (script-sprint4.sql, reset-db-sprint4.sql)
- Tests unitaires couvrant règles métiers


sprint4
base de donnee + bundle




---

## BackOffice - To Do (Sprint 5)

### Objectif
Ajouter la gestion du **temps d'attente (TA)** pour regrouper les réservations dont les vols arrivent dans un intervalle de temps configurable. Le véhicule attend que tous les vols de la fenêtre soient arrivés avant de partir.

### Fonctionnalités (liste)
- [ ] **Regroupement par fenêtre de temps d'attente (TA)**
  - Les réservations dont les vols arrivent dans l'intervalle `[heure_premier_vol, heure_premier_vol + TA]` sont regroupées.
  - L'heure de départ du véhicule = heure du **dernier vol** du groupe.
  - Si aucun autre vol dans la fenêtre, départ = heure du premier (et seul) vol.

- [ ] Conserver toutes les règles du Sprint 4 :
  - Capacité >= total passagers
  - Plus petit véhicule satisfaisant
  - Préférence Diesel en cas d'égalité
  - Ordre de dépose par nearest-neighbour
  - Départage alphabétique si même distance

- [ ] Mise à jour de l'algorithme `assignerVehiculesAutomatiquement()` pour intégrer le TA.
- [ ] Affichage dans l'interface : heure d'arrivée de chaque vol + heure de départ effective du véhicule.

### Contraintes / Règles métiers

1. **Paramètre TA** : Temps d'attente en minutes (ex: 30 min), stocké dans `parametre` (clé = 'TA').

2. **Construction des fenêtres de regroupement** :
   - Trier les réservations par `date_heure_arrivee` croissante.
   - Fenêtre = `[heure_vol_1, heure_vol_1 + TA]`
   - Tant qu'un vol suivant arrive dans cette fenêtre, l'ajouter au groupe.
   - Le prochain vol hors fenêtre démarre une nouvelle fenêtre.

3. **Heure de départ du véhicule** :
   - = `MAX(MAX(date_heure_arrivee du groupe), heure_disponibilite_vehicule_assigné)`
   - Les passagers arrivés plus tôt attendent.

4. **Règles d'assignement des véhicules (par priorité)** :

   ---

   ### 🔢 Ordre complet des règles d'assignement (par priorité décroissante)

   **Étape 0 — Préparation du groupe** :
   - **R0** : Trier les réservations du groupe par ordre **décroissant** du nombre de passagers.

   ---

   **Étape 1 — Sélection du véhicule** :

   **R1a — Priorité absolue : réutiliser un véhicule déjà assigné dans le groupe** :
   - Avant toute recherche de véhicule libre, vérifier si un véhicule **déjà assigné** à une réservation du même groupe peut absorber **tous** les passagers restants.
   - Si oui → l'affecter en priorité, aucun nouveau véhicule n'est mobilisé.
   - Si plusieurs véhicules déjà assignés sont éligibles → appliquer R1c puis R1d pour départager.

   **R1b — Disponibilité sur la fenêtre TA** *(uniquement si aucun véhicule déjà assigné ne convient)* :
   - Un véhicule est **disponible** s'il est libre sur tout l'intervalle `[heure_vol_1_du_groupe, heure_vol_1_du_groupe + TA]`.
   - Tout véhicule dont une mission existante **chevauche** cet intervalle est **exclu**.

   **R1c — Capacité minimale satisfaisante** :
   - Parmi les véhicules éligibles (R1a ou R1b), choisir celui dont la capacité est la **plus petite** tout en étant **>= nombre total de passagers** du groupe.

   **R1d — Préférence Diesel** :
   - Si plusieurs véhicules ont la même capacité minimale satisfaisante → choisir le **Diesel** en priorité.

   ---

   **Étape 2 — Calcul de l'heure de départ effective** :

   **R2 — Heure de départ = MAX(dernier vol du groupe, disponibilité du véhicule assigné)** :
   ```
   heure_depart = MAX(MAX(date_heure_arrivee du groupe), heure_disponibilite_vehicule)
   ```
   - Si le véhicule est libre avant le dernier vol → départ = heure du dernier vol.
   - Si le véhicule finit une mission **après** le dernier vol → départ repoussé à la dispo du véhicule.
   - **Exemple** :
     ```
     Groupe : vols 08h00, 08h15, 08h20  → MAX vols = 08h20
     Véhicule disponible à : 08h35
     → Heure de départ effective = MAX(08h20, 08h35) = 08h35
     ```

   ---

   **Étape 3 — Calcul de l'itinéraire** :

   **R3 — Nearest-neighbour pour l'ordre de dépose** :
   - Depuis l'aéroport, aller à l'hôtel le plus proche, puis au suivant le plus proche, etc.

   **R4 — Départage alphabétique si même distance** :
   - Si deux hôtels sont à égale distance → choisir par ordre alphabétique du nom de l'hôtel.

   ---

   **Étape 4 — Vérification de chevauchement** :

   **R5 — Chevauchement basé sur l'heure de départ effective** :
   - Pour toute vérification de disponibilité future, utiliser `heure_depart_effective` (calculée en R2) comme référence, **pas** l'heure du premier vol.

   ---

   ### Résumé visuel des règles

   ```
   Pour chaque groupe TA :
   │
   ├─ R0  : Trier réservations par nb_passagers DESC
   │
   ├─ R1a : Véhicule déjà assigné dans le groupe ?
   │         ├─ OUI → vérifier capacité suffisante → utiliser (R1c + R1d si ambiguïté)
   │         └─ NON → continuer
   │
   ├─ R1b : Filtrer véhicules libres sur [vol_1, vol_1 + TA]
   │
   ├─ R1c : Parmi éligibles → plus petite capacité >= passagers
   │
   ├─ R1d : Égalité capacité → préférer Diesel
   │
   ├─ R2  : heure_depart = MAX(MAX_vols_groupe, dispo_véhicule)
   │
   ├─ R3  : Nearest-neighbour pour l'ordre de dépose
   │
   ├─ R4  : Égalité distance → ordre alphabétique hôtel
   │
   └─ R5  : Chevauchement calculé sur heure_depart_effective
   ```

### Données & schéma (rappels / dépendances)

- **Base de données** : `backoffice5`

- **Table parametre** :
  ```sql
  INSERT INTO parametre (cle, valeur, unite) VALUES ('TA', 30, 'minutes');
  ```

- **Tables existantes** (inchangées) :
  - `reservation` (id, client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
  - `vehicule` (id, reference, nombre_place, type_carburant)
  - `hotel` (id_hotel, nom)
  - `distance` (from_id, to_id, kilometer) — sans redondance (A→B seulement)

- **Vue v_historique_assignation** : adapter si nécessaire pour afficher heure départ ≠ heure arrivée vol.

### Implémentation technique

1. **Nouvelle méthode** : `construireFenetresTA(List<Reservation> reservations, int taMinutes)`
   - Retourne une liste de groupes (List<List<Reservation>>).
   - Chaque groupe = réservations dans la même fenêtre TA.

2. **Calcul heure départ effective** : `calculerHeureDepart(List<Reservation> groupe, Vehicule vehicule)`
   - Retourne `MAX(MAX(date_heure_arrivee du groupe), heure_disponibilite_vehicule)`.

3. **Modification de `assignerVehiculesAutomatiquement()`** :
   - Avant : grouper par timestamp exact.
   - Après :
     1. Grouper par fenêtre TA.
     2. **Pour chaque groupe**, chercher d'abord un véhicule déjà assigné (R0a).
     3. Sinon, chercher parmi les véhicules disponibles sur `[heure_vol_1, heure_vol_1 + TA]` (R0b).
     4. Calculer l'heure de départ effective via `MAX(dernière_arrivée, dispo_véhicule)` (R0c).
     5. Appliquer les règles R1–R7.

4. **Affichage JSP** :
   - Colonne "Heure vol" = `date_heure_arrivee`
   - Colonne "Heure départ véhicule" = heure de départ effective (MAX vol + dispo véhicule)
   - Colonne "Attente" = `heure_depart_effective - date_heure_arrivee` par passager

### Tests & critères d'acceptation

| Test | Scénario | Résultat attendu |
|------|----------|------------------|
| T1 | Vols 08h00 + 08h15 + 08h25, TA=30 | Un seul groupe, départ 08h25 |
| T2 | Vols 08h00 + 08h45, TA=30 | Deux groupes (08h45 > 08h30) |
| T3 | Vol unique 10h00, TA=30 | Un groupe, départ 10h00 |
| T4 | Vols 08h00 + 08h15, total 5 passagers, VH-002(4p) | Deux véhicules (capacité insuffisante) |
| T5 | Vols 08h00 + 08h10 (hôtels différents) | Nearest-neighbour appliqué |
| T6 | Groupe avec Colbert + Panorama (même distance) | Ordre alphabétique : Colbert → Panorama |
| T7 | Véhicule déjà assigné dans le groupe, capacité suffisante | Réutilisation du véhicule existant (R0a) |
| T8 | Véhicule dispo à 08h35, vols MAX=08h20 | Départ effectif = 08h35 (R0c) |
| T9 | Véhicule occupe [08h00–08h25], groupe [08h00–08h30] | Véhicule exclu de la sélection (R0b) |

### Scripts SQL

- [ ] `db/reset-db-sprint5.sql` : reset complet avec TA=30 et scénarios de test.
- [ ] Scénarios de test couvrant :
  - Regroupement dans fenêtre TA
  - Vols hors fenêtre (nouveau groupe)
  - Combinaison TA + capacité + nearest-neighbour

### Notes / décisions à prendre

1. **TA dynamique ou fixe ?** → Fixe pour sprint 5 (paramètre en base).
2. **Affichage temps d'attente ?** → Afficher "Attente : X min" pour les passagers arrivés avant le départ.
3. **Chevauchement avec TA** → L'heure de départ pour le calcul de chevauchement = MAX(arrivées du groupe).
4. **Extension future** : TA variable selon type de client ou heure de la journée.

---

### Précision importante : Heure de départ du véhicule

**Règle** : Le véhicule part à l'heure du **dernier vol** dans la fenêtre TA.

**Exemple** (TA = 30 min) :
```
Vols : 08h00, 08h15, 08h20

Fenêtre TA : [08h00, 08h30]
Les 3 vols sont dans la fenêtre → 1 seul groupe

Heure de départ véhicule = MAX(08h00, 08h15, 08h20) = 08h20

┌──────────┬──────────────┬─────────────────┐
│ Vol      │ Heure arrivée│ Attente client  │
├──────────┼──────────────┼─────────────────┤
│ Vol A    │ 08h00        │ 20 min          │
│ Vol B    │ 08h15        │ 5 min           │
│ Vol C    │ 08h20        │ 0 min (départ)  │
└──────────┴──────────────┴─────────────────┘

→ Tous les passagers partent ensemble à 08h20
```