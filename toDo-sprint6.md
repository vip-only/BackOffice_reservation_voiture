### Livrables sprint5
- Code :
  - mise a jour PlanificationService (departage par nb trajets + report inter-fenetres)
  - ajout methode DAO pour compter les trajets du jour par vehicule
  - controleur /planification conserve (utilise la nouvelle logique)
  - JSP mise a jour si necessaire (affichage infos regroupement / report)
- SQL scripts (script-sprint6.sql, reset-db-sprint6.sql)
- Tests unitaires couvrant regles metiers sprint 6


sprint6
consideration du nombre de trajets + report regroupement suivant




---

## BackOffice - To Do (Sprint 6)

### Objectif
Ajouter la consideration du **nombre de trajets effectues aujourd'hui** dans l'assignation des vehicules. En cas d'egalite de places, choisir la voiture ayant le moins de trajets du jour, puis departager par carburant (**Diesel puis Essence**). Si une reservation n'est pas assignee dans son regroupement de temps, la reessayer dans le regroupement horaire suivant selon les regles sprint 5, en conservant la consideration du nombre de trajets.

### Fonctionnalites (liste)
- [ ] **Departage par nombre de trajets du jour**
  - En cas d'egalite de places, choisir le vehicule ayant effectue le **moins de trajets aujourd'hui**.
  - Si egalite encore, appliquer la preference **Diesel**, puis Essence.

- [ ] Conserver toutes les regles du Sprint 5 :
  - Capacite >= total passagers
  - Plus petit vehicule satisfaisant
  - Regroupement par fenetre TA
  - Ordre de depose par nearest-neighbour
  - Departage alphabetique si meme distance

- [ ] **Report des non-assignees vers le regroupement suivant**
  - Une reservation non assignee dans la fenetre courante est reportee dans la fenetre suivante.
  - Le report suit les memes regles d'assignation (Sprint 5 + critere nb trajets).

- [ ] Mise a jour de l'algorithme `assignerVehiculesAutomatiquement()` pour integrer :
  - critere nb trajets aujourd'hui
  - mecanisme de carry-over des reservations non assignees

### Contraintes / Regles metiers

1. **Parametre TA** : Temps d'attente en minutes (cle = 'TA'), conserve comme dans sprint 5.

2. **Construction des fenetres de regroupement** :
   - Trier les reservations par `date_heure_arrivee` croissante.
   - Fenetre = `[heure_vol_1, heure_vol_1 + TA]`
   - Le prochain vol hors fenetre demarre une nouvelle fenetre.

3. **Regles d'assignement des vehicules (par priorite)** :

   ---

   ### Ordre complet des regles d'assignement (par priorite decroissante)

   **Etape 0 - Preparation du groupe** :
   - **R0** : Trier les reservations du groupe par ordre **decroissant** du nombre de passagers.

   ---

   **Etape 1 - Selection du vehicule** :

   **R1a - Priorite absolue : reutiliser un vehicule deja assigne dans le groupe** :
   - Avant toute recherche de vehicule libre, verifier si un vehicule deja assigne a une reservation du meme groupe peut absorber les passagers restants.
   - Si plusieurs vehicules deja assignes sont eligibles -> appliquer R1c puis R1e puis R1d.

   **R1b - Disponibilite sur la fenetre TA** *(uniquement si aucun vehicule deja assigne ne convient)* :
   - Un vehicule est disponible s'il est libre selon la regle sprint 5 sur la fenetre courante.

   **R1c - Capacite minimale satisfaisante** :
   - Choisir la capacite la plus petite avec capacite >= passagers.

   **R1e - NOUVEAU Sprint 6 : Moins de trajets aujourd'hui** :
   - En cas d'egalite de capacite, choisir le vehicule avec le **nombre de trajets du jour le plus faible**.

   **R1d - Carburant** :
   - Si egalite persiste apres R1e, choisir **Diesel** en priorite, puis Essence.

   ---

   **Etape 2 - Gestion des non-assignees** :

   **R2 - Report vers la fenetre suivante** :
   - Si aucune voiture ne peut etre assignee dans la fenetre courante, la reservation est reportee dans la fenetre suivante.
   - Si c'est la derniere fenetre de la journee et qu'il n'y a pas de solution, la reservation reste sans vehicule.

   ---

   **Etape 3 - Calcul de l'itineraire** :

   **R3 - Nearest-neighbour pour l'ordre de depose** :
   - Depuis l'aeroport, aller a l'hotel le plus proche, puis au suivant le plus proche, etc.

   **R4 - Departage alphabetique si meme distance** :
   - Si deux hotels sont a egale distance -> choisir par ordre alphabetique.

### Resume visuel des regles

```
Pour chaque groupe TA :
|
+- R0  : Trier reservations par nb_passagers DESC
|
+- R1a : Vehicule deja assigne dans le groupe ?
|        +- OUI -> verifier capacite -> appliquer R1c + R1e + R1d
|        +- NON -> continuer
|
+- R1b : Chercher vehicules disponibles sur la fenetre
|
+- R1c : Parmi eligibles -> plus petite capacite >= passagers
|
+- R1e : Egalite capacite -> moins de trajets aujourd'hui
|
+- R1d : Egalite restante -> Diesel puis Essence
|
+- R2  : Non assignee -> reporter a la fenetre suivante
|
+- R3  : Nearest-neighbour pour l'ordre de depose
|
+- R4  : Egalite distance -> ordre alphabetique hotel
```

### Donnees & schema (rappels / dependances)

- **Base de donnees** : `backoffice5` (ou `backoffice6` selon votre convention)

- **Table parametre** :
  ```sql
  INSERT INTO parametre (cle, valeur, unite) VALUES ('TA', 30, 'minutes');
  ```

- **Tables existantes** (inchangees) :
  - `reservation` (id, client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
  - `vehicule` (id, reference, nombre_place, type_carburant)
  - `hotel` (id_hotel, nom)
  - `distance` (from_id, to_id, kilometer)

- **Nouveau besoin DAO** :
  - Compter les trajets par vehicule pour une date donnee
  - Inclure les trajets deja en base + mises a jour faites pendant l'execution

### Implementation technique

1. **Nouvelle methode DAO** : `getNombreTrajetsParVehicule(Date date)`
   - Retourne un mapping `vehicule_id -> nb_trajets`.

2. **Modification de `assignerVehiculesAutomatiquement()`** :
   - Construire les fenetres TA.
   - Maintenir une liste des non-assignees a reporter sur la fenetre suivante.
   - Mettre a jour le compteur de trajets au fil des nouvelles assignations.

3. **Modification de la selection vehicule** :
   - Dans les choix R1a et R1b, ajouter le departage par nb trajets avant la regle carburant.

4. **Ajustement de `assignerGroupeReservations()`** :
   - Retourner la liste des reservations non assignees pour le report inter-fenetres.

### Tests & criteres d'acceptation

| Test | Scenario | Resultat attendu |
|------|----------|------------------|
| T1 | Deux vehicules meme capacite, trajets jour: V1=3, V2=1 | V2 choisi |
| T2 | Meme capacite, meme trajets, Diesel vs Essence | Diesel choisi |
| T3 | Meme capacite, meme trajets, meme carburant | Plus petit id choisi |
| T4 | Reservation non assignable dans fenetre 1 | Report en fenetre 2 |
| T5 | Vehicule libre en fenetre suivante | Reservation reportee assignee |
| T6 | Aucune solution jusqu'a la derniere fenetre | Reservation reste sans vehicule |
| T7 | Cas mixte TA + capacite + trajets + carburant | Respect strict des priorites |

### Scripts SQL

- [ ] `db/reset-db-sprint6.sql` : reset complet avec scenarios sprint 6
- [ ] Scenarios de test couvrant :
  - egalite de places + departage nb trajets
  - egalite nb trajets + departage Diesel/Essence
  - report inter-fenetres des non-assignees

### Notes / decisions a prendre

1. Le compteur de trajets compte-t-il chaque reservation assignee ou chaque mission consolidee ?
2. Afficher le nombre de trajets du jour dans l'interface planification ?
3. En cas de reports multiples, conserver l'ordre de priorite par nombre de passagers ?
