### Livrables sprint8
- Code :
  - mise a jour PlanificationService / Sprint7Service pour gerer le cas "vehicule qui revient"
  - priorisation des reservations non assignees quand un vehicule redevient disponible
  - maintien des regles sprint 5 + sprint 6 + sprint 7
- SQL scripts (script-sprint8.sql, reset-db-sprint8.sql)
- Scenarios de test sprint 8 (dont cas "retour vehicule" sur meme jour)
- Trace/affichage du motif d'assignation (depart immediate vs report)


sprint8
vehicule qui revient + non assignees


---

## BackOffice - To Do (Sprint 8)

### Objectif
Ajouter une regle de reaction quand un vehicule termine un trajet et redevient disponible dans la journee.

Principe metier sprint 8 (version en cours de validation):
- Si un vehicule revient et qu'il existe des reservations non assignees,
- et si la somme des places en attente est >= capacite du vehicule,
- alors le vehicule repart immediatement avec un nouveau groupe.
- Sinon, il ne part pas tout de suite et les reservations restent dans le prochain regroupement TA.

### Fonctionnalites (liste)
- [ ] Detecter les evenements "retour vehicule" (heure de retour calculee).
- [ ] Evaluer les reservations non assignees a l'instant du retour.
- [ ] Regle Sprint 8 : depart immediate si charge en attente >= capacite vehicule revenu.
- [ ] Sinon : report automatique des reservations dans le regroupement suivant.
- [ ] Conserver toutes les regles Sprint 5/6/7 (TA, capacite, diesel, nb trajets, fractionnement, nearest-neighbour).
- [ ] Ajouter des informations de trace pour expliquer chaque decision.

### Contraintes / Regles metiers

#### Ordre des regles (priorite decroissante)

**Etape 0 - Base existante (inchangee)**
- **R0** : Trier les reservations d'une fenetre par passagers DESC puis heure.
- **R1** : Selection de vehicule selon regles sprint precedents (capacite minimale satisfaisante, nb trajets, carburant).
- **R2** : Fractionnement autorise si aucun vehicule ne peut absorber toute la reservation.

**Etape 1 - Nouveau declencheur Sprint 8 (retour vehicule)**
- **R8a - Detection retour** : Lorsqu'un vehicule atteint sa `date_heure_retour`, il devient candidat a une nouvelle mission.
- **R8b - Candidats non assignes** : Constituer la liste des reservations non assignees eligibles a cet instant (meme date de service).
- **R8c - Condition de depart immediate** :
  - Si `SUM(passagers_en_attente) >= capacite_vehicule_retour`,
  - alors depart immediate a `heure_depart = date_heure_retour` du vehicule.
- **R8d - Sinon (pas assez de charge)** :
  - Ne pas declencher de depart immediate,
  - conserver ces reservations pour le prochain regroupement TA.

**Etape 2 - Construction du groupe de depart immediate**
- Le groupe est rempli en priorisant les reservations selon les regles existantes (passagers DESC, heure, etc.).
- Si la somme depasse la capacite, appliquer la logique de remplissage/fractionnement deja definie.
- Appliquer nearest-neighbour pour l'ordre de depose du nouveau depart.

**Etape 3 - Cohabitation avec les fenetres TA**
- Le depart immediate sprint 8 ne supprime pas TA.
- Il s'insere entre deux fenetres comme un "mini regroupement opportuniste".
- Les non pris dans ce depart immediate restent pour la fenetre suivante.

### Resume visuel des regles Sprint 8

```
A chaque evenement "vehicule revient" :
|
+- 1) Recuperer reservations non assignees eligibles (meme jour)
|
+- 2) Calculer charge_attente = somme passagers
|
+- 3) charge_attente >= capacite_vehicule ?
|      +- OUI  -> depart immediate a heure_retour
|      |         (selection + ordre selon regles existantes)
|      +- NON  -> pas de depart immediate
|                 -> reservations conservees pour prochaine fenetre TA
|
+- 4) Continuer le flux normal Sprint 7
```

### Donnees & schema

- Tables existantes reutilisees :
  - `reservation`
  - `vehicule`
  - `reservation_vehicule`
  - `distance`
  - `parametre`

- A verifier si besoin sprint 8 :
  - [ ] Ajouter un champ `mode_assignation` (`NORMAL_TA`, `RETOUR_IMMEDIAT`) dans `reservation_vehicule`.
  - [ ] Ou stocker la raison via logs applicatifs si on ne touche pas au schema.

### Implementation technique

1. Detection du retour vehicule
- Ajouter une methode service qui calcule les vehicules redevenus disponibles a un instant donne.
- S'appuyer sur `v_historique_assignation.date_heure_retour`.

2. Selection des non assignees eligibles
- Reutiliser `findWithoutVehiculeByDate(date)` puis filtrer par heure <= heure de retour.
- Reutiliser la priorite existante (passagers DESC puis heure).

3. Regle de depart immediate
- Calculer `charge_attente`.
- Si `charge_attente >= capacite_vehicule`, construire et assigner un groupe immediat.
- Heure de depart effective du groupe immediate = `heure_retour_vehicule`.

4. Integrer sans casser Sprint 7
- Lancer la logique sprint 8 dans la boucle principale d'assignation.
- Conserver la synchronisation `reservation` <-> `reservation_vehicule`.
- Eviter les doubles assignations dans la meme minute (verrou fonctionnel ou verification pre-insert).

5. Traces / observabilite
- Logger pour chaque tentative :
  - vehicule, heure retour, charge attente, capacite,
  - decision (`DEPART_IMMEDIAT` ou `REPORT_TA`).

### Tests & criteres d'acceptation

| Test | Scenario | Resultat attendu |
|------|----------|------------------|
| T1 | Vehicule 2 revient a 14:00, non assignees total = 9, capacite vehicule = 8 | Depart immediate a 14:00 |
| T2 | Vehicule 2 revient a 14:00, non assignees total = 5, capacite = 8 | Pas de depart immediate, report TA |
| T3 | Deux vehicules reviennent proches (14:00, 14:05) | Pas de double affectation de la meme reservation |
| T4 | Cas avec fractionnement necessaire lors d'un depart immediate | Fractionnement applique, aucune perte passagers |
| T5 | Cas mixte: depart immediate puis fenetre TA suivante | Regles sprint 8 puis sprint 7 respectees |
| T6 | Reservations non assignees de la veille | Non prises dans un depart immediate du jour (si regle "meme jour" validee) |
| T7 | Egalite capacite / egalite trajets / diesel | Priorites sprint 6-7 conservees |

### Scripts SQL
- [ ] `db/reset-db-sprint8.sql` : reset complet + donnees de base.
- [ ] `db/script-sprint8.sql` : cas de test sprint 8.
- [ ] Donnees de test minimales :
  - vehicule qui revient a 14:00,
  - reservations non assignees avant 14:00,
  - un cas charge >= capacite,
  - un cas charge < capacite.

### Taches techniques detaillees
- [ ] Service : introduire une methode `traiterRetoursVehicules(...)`.
- [ ] DAO : methode de lecture des retours par fenetre de temps.
- [ ] DAO : methode pour charger les non assignees eligibles a l'heure H.
- [ ] Service : fonction de calcul `charge_attente`.
- [ ] Service : strategie de selection des reservations pour remplir un vehicule revenu.
- [ ] Service : insertion des traces de decision.
- [ ] Controller : option pour executer sprint 8 sur une date.
- [ ] JSP sprint7/sprint8 : afficher motif d'assignation.
- [ ] SQL : scenarios de verification automatique.

### Open questions (a valider metier)
1. La condition sprint 8 est-elle bien `charge_attente >= capacite_vehicule` (et non > 0) ?
2. Les reservations eligibles sont-elles limitees a la meme fenetre TA ou a tout le backlog du jour ?
3. En cas de charge partielle, peut-on partir quand meme pour minimiser le retard ?
4. Regle officielle pour "sinon il entre dans le prochain regroupement" (confirmer formulation finale).
5. Doit-on exposer cette regle via un parametre (`ACTIVER_RETOUR_IMMEDIAT`) ?
