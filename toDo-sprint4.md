## BackOffice - To Do (Sprint 4)

### Objectif
Améliorer l'algorithme d'affectation pour :
- regrouper automatiquement des passagers arrivant à la même date/heure dans une même voiture si des places sont disponibles (sans temps d'attente),
- optimiser l'ordre des déposes quand ils ont plusieurs hôtels (heuristique du plus proche),
- et prioriser l'affectation des réservations avec le plus de personnes en premier.

### Fonctionnalités (liste)
- [X] Regroupement des clients arrivant à la même date/heure dans la même voiture si capacité restante.
  - Pas de temps d'attente (TA) pour ce regroupement.

- [X] Toujours traiter/assigner en priorité la réservation ayant le plus de passagers (ordre décroissant) avant les plus petites.
- [ ] Fournir action "Regrouper et assigner" (POST) dans /planification pour exécuter l'algorithme amélioré.
  <!-- - Les clients regroupés peuvent avoir des hôtels différents ; déterminer l'ordre de dépose par distance minimale (heuristique nearest-neighbour). -->
- [ ] Mettre à jour l'interface de planification pour afficher les groupes/itinéraires par véhicule.
-
- [ ] Tests unitaires de l'algorithme de regroupement et d'optimisation d'itinéraire.

### Contraintes / Règles métiers
1. Une voiture ne doit jamais être surchargée (capacité >= total passagers affectés).
2. Pour une même arrivée (même timestamp), regrouper autant que possible dans une voiture avant d'en utiliser une autre.
3. Lors du regroupement avec plusieurs hôtels, choisir l'ordre de dépose qui minimise la distance totale (approx. nearest-  à partir du lieu de départ).
4. Choisir la voiture dont la capacité est la plus proche (la plus petite capacité satisfaisante).
5. En cas d'égalité de capacité candidate, préférer véhicule Diesel.
6. Toujours assigner d'abord les réservations avec le plus grand nombre de passagers.

### Données & schéma (rappels / dépendances)
- Tables attendues (voir sprint3) :
  - reservation (id, client, nombre_passager, date_heure_arrivee, id_hotel, id_vehicule)
  - vehicule (id, reference, nombre_place, type_carburant, disponible)
  - lieu (id, type, code, libelle, latitude, longitude) — remplace la table `aeroport`; sert pour aéroports, hôtels ou autres points
  - hotel (id, nom, adresse, id_lieu) — lier un hôtel à un lieu (optionnel si lieu contient déjà les hôtels)
  - distance (id, from_id, to_id, kilometer) — utiliser lieu.id pour représenter la distance entre deux lieux (hôtel↔hôtel, hôtel↔aéroport, etc.)
  - parametre (cle, valeur) — TA et VITESSE_MOYENNE présents si utiles
- Index recommandés : reservation(date_heure_arrivee), vehicule(disponible), distance(from_id,to_id), lieu(type).

### Scripts SQL & migration
- [ ] Ajouter / adapter scripts :
  - db/script-sprint4.sql : fonctions pour peupler distances entre lieux (aéroports/hôtels), et initialiser disponibilités véhicules.
  - db/reset-db-sprint4.sql : reset + données de test pour scénarios de regroupement.
- Prévoir contraintes FK et données de test couvrant cas multi-hôtels et multi-réservations même timestamp.

### Implémentation technique (haute niveau)
- Service Planification :
  - Récupérer toutes réservations du jour groupées par date_heure_arrivee.
  - Pour chaque groupe (même timestamp) :
    - Trier réservations par nombre_passager décroissant.
    - Parcourir véhicules disponibles ; pour chaque véhicule, essayer d'ajouter autant de réservations que possible sans dépasser la capacité.
    - Si plusieurs hôtels ajoutés, construire itinéraire : départ = lieu (ex: aéroport → lieu.id), puis ordre des hôtels par nearest-neighbour en utilisant table distance (lieu.from_id / lieu.to_id).
    - Marquer vehicule comme utilisé et mettre à jour reservation.id_vehicule.
- Garder chemin simple : greedy + nearest-neighbour ; documenter limites et cas d'amélioration (TSP).
- Mettre à jour PlanificationController et JSP pour afficher : véhicule, liste des passagers affectés, ordre de dépose, heures départ/retour.

### Tests & critères d'acceptation
- Cas 1 : 3 réservations mêmes timestamp (2,1,1 passagers), véhicule capacité 4 -> les trois doivent tenir dans le véhicule si somme <= 4.
- Cas 2 : mêmes timestamp, plusieurs véhicules disponibles -> priorité au remplissage du plus petit véhicule adéquat, puis diesel en cas d'égalité.
- Cas 3 : réservations groupées avec 2 hôtels différents -> vérifier ordre de dépose optimal approximé (distance totale plus courte que ordre aléatoire).
- UI : la page /planification affiche groupes par véhicule et une section pour non assignés.

### Notes / décisions à prendre
- Distance entre lieux : stocker distances symétriques ou calculer via coordonnées (lat/lon) ? Préférence actuelle : table distance (valeurs symétriques) pour simplicité.
- Algorithme d'optimisation : nearest-neighbour suffisant pour sprint4 ; envisager TSP amélioré plus tard.
- Décision de modélisation : utiliser `lieu` centralise points (aéroport, hôtels) et simplifie calculs distance entre deux hôtels.

### Livrables sprint4
- Code :
  - mise à jour PlanificationService (regroupement & routing)
  - nouveaux DAO si nécessaire (DistanceDAO, LieuDAO)
  - contrôleur /planification étendu + POST /planification/regrouper-assigner
  - JSP mise à jour (affichage groupes/itinéraires)
- SQL scripts (script-sprint4.sql, reset-db-sprint4.sql)
- Tests unitaires couvrant règles métiers