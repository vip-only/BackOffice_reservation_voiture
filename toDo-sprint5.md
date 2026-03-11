

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
   - = `MAX(date_heure_arrivee)` des réservations du groupe.
   - Les passagers arrivés plus tôt attendent.

4. **Exemple concret** (TA = 30 min) :
   ```
   Vol A : 08h00  ─┐
   Vol B : 08h15  ─┼─► Groupe 1 (fenêtre 08h00-08h30)
   Vol C : 08h25  ─┘   → Départ véhicule : 08h25
   
   Vol D : 10h00  ─┐
   Vol E : 10h20  ─┼─► Groupe 2 (fenêtre 10h00-10h30)
                  ─┘   → Départ véhicule : 10h20
   
   Vol F : 14h00  ────► Groupe 3 (seul dans la fenêtre)
                        → Départ véhicule : 14h00
   ```

5. **Règles de base conservées** :
   - R1 : Capacité >= nombre de passagers
   - R2 : Plus petite capacité satisfaisante
   - R3 : Préférence Diesel si égalité de capacité
   - R4 : Priorité par nombre de passagers décroissant
   - R5 : Nearest-neighbour pour ordre de dépose
   - R6 : Départage alphabétique si même distance
   - R7 : Chevauchement = véhicule indisponible

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

2. **Calcul heure départ** : `calculerHeureDepart(List<Reservation> groupe)`
   - Retourne le MAX des `date_heure_arrivee`.

3. **Modification de `assignerVehiculesAutomatiquement()`** :
   - Avant : grouper par timestamp exact.
   - Après : grouper par fenêtre TA, puis appliquer les règles existantes.

4. **Affichage JSP** :
   - Colonne "Heure vol" = `date_heure_arrivee`
   - Colonne "Heure départ véhicule" = MAX du groupe

### Tests & critères d'acceptation

| Test | Scénario | Résultat attendu |
|------|----------|------------------|
| T1 | Vols 08h00 + 08h15 + 08h25, TA=30 | Un seul groupe, départ 08h25 |
| T2 | Vols 08h00 + 08h45, TA=30 | Deux groupes (08h45 > 08h30) |
| T3 | Vol unique 10h00, TA=30 | Un groupe, départ 10h00 |
| T4 | Vols 08h00 + 08h15, total 5 passagers, VH-002(4p) | Deux véhicules (capacité insuffisante) |
| T5 | Vols 08h00 + 08h10 (hôtels différents) | Nearest-neighbour appliqué |
| T6 | Groupe avec Colbert + Panorama (même distance) | Ordre alphabétique : Colbert → Panorama |

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


