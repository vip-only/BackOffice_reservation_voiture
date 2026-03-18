Tsy mila tomcat io
Je suis sous java 17
Fa lancement jetty sur: localhost:8082 no misy (voir lancement.bat)

Ny route mandeha: localhost:8082/home

Zay bisous



sprint 3:
_ ajout de table parametre(
    ex: TA (temps d'attente):30min, Vitessemoyennne:30
    ) pas de crud jsute insertion
_ ajout de table lieu: id, code, libelle
_ ajout de table distance: id, from, to, kilometer (atao id ve sa atao string??)
_ préparation de script d'réinitialisation 

 BackOffice: interface qui prend un parametre date --> page planification: voiture associé à une réservation départ et retour à telle heure pour cette meme personne; la voiture départ et toujours retour sur l'aeroport
 et en dessous la liste des réservation qui n'ont pas pu etre assigné à un véhicule
 Règle:
 - 1. voiture de capacité sup ou égal au nb de pers
 - 2. on assigne la place la plus proche de la capacité
 - 3. on assigne la voiture Diesel si choix dispo apres la regle 2


 liste reservation sans vehicule
 


Sprint5 : Temps d attente 
Afaka atambatra anaty vehicule ray ny reservation maromar
Raha misy vol tonga ao anaty TA , dia miaraka ireo
ex: TA =30min , vol =8h 
izay vol tafiditra am ireo dia atambatra (8h30), raha tsisy vol apres 8h dia miainga 8h , raha misy vol 8h15 dia miara depart 8h15 satria meme regroupement, 
Heure de depart = miamikina am izay vol farany ndrindra
-Regle tsy miova :
Le plus pres
Diesel , essence 
random

 
sprint 5: temps d'attente = regroupement de reservations

On peut rassembler des reservations d'heures differentes dans une voiture
Le concept de regroupement sera elargi par le temps d'attente
Si on a un vol à 8h Ta:30MIN: regroupement 1
    on a un vol à 8h15 donc toutes les voitures seront de depart à 8h15 suivant le regroupement
Si on a un vol à 10h TA:30MIN: regroupement 2


Le regroupement fonctionne:
_ on prend le premier vol on regarde si d'autres vols sont dans le TA si oui toutes les voitures ont pour départ l'heure du dernier vol du regroupement
_ et le prochain vol hors regroupement sera la premiere heure pour le nouveau regroupement et on regarde les vols dans le regroupement 

<<<<<<< Updated upstream


Les voitures dispo sont les voitures dispos entre la 1ere heure du regroupement jusqu'à la 1ere heure+TA dans ce cas le depart du groupe sera la date la plus recente entre le dernier heure du regroupement et l'arrivée des voitures



sprint 6: considération des nb de trajets
Dans le choix de l'asignation, on prendra la voiture qui ayant effectué le moins de trajet aujourd'hui en cas d'egalité de places et ensuite on regarde si c'est diesel ou essence 
 

si une voiture n'a pas été assigné dans son regroupement de temps il faut l'assigner dans le prochain regroupement horaire suivant les regles.
=======
Sprint 6 :
Plannification (1 jour avant)
Considerer les nombres de trajets 
Rehefa tafaverina ny voiture ray dia afaka considererna ilay voiture
Rehefa hitady voiture approprie am resa iray dia jerena hoe iza no manana capacite betsaka noho ny nombre passager
Izay moins de trajet no alefa , na essence na diesel, rahamisy mitovy dia izay vao jerena ny carburant
-Considere les reservations non assignes apres le prochain regroupement
-heure de retour d'une voiture 



## Enchainement des fonctions - Sprint 5 (planification)

Cette section decrit l'ordre d'appel des fonctions pour expliquer le fonctionnement du code et faciliter la maintenance.

### 1) Point d'entree (controller)

Deux points d'entree principaux:

1. `PlanificationController.assignerVehicules(date)`
2. `PlanificationController.regrouperEtAssigner(date)`

Le second est le flux complet recommande pour Sprint 5:

1. assigner automatiquement les vehicules
2. reconstruire les groupes pour l'affichage

### 2) Orchestration metier (service)

`PlanificationService.regrouperEtAssigner(date)`:

1. appelle `assignerVehiculesAutomatiquement(date)`
2. puis appelle `construireGroupesParVehicule(date)`
3. retourne les groupes prets pour l'IHM

### 3) Assignation automatique (coeur Sprint 5)

`PlanificationService.assignerVehiculesAutomatiquement(date)`:

1. charge les reservations sans vehicule via `ReservationDAO.findWithoutVehiculeByDate(date)`
2. lit le parametre TA via `ParametreDAO.getTempsAttente()`
3. trie chronologiquement pour construire les fenetres TA
4. construit les fenetres avec `construireFenetresTA(...)`
5. pour chaque fenetre:
    - trie les reservations par passagers DESC (R0)
    - calcule l'heure de depart groupe via `calculerHeureDepart(...)` (= MAX arrivee)
    - appelle `assignerGroupeReservations(groupe, heureDepart)`

### 4) Regles d'assignation dans un groupe

`PlanificationService.assignerGroupeReservations(...)`:

1. calcule l'hotel le plus loin via `trouverHotelPlusLoin(...)`
2. recupere les vehicules disponibles via `ReservationService.getVehiculesDisponibles(heureDepart, idHotelPlusLoin)`
3. pour chaque reservation du groupe (deja triee R0):
    - tente d'abord R1a: `choisirVehiculeDejaAssigneR1a(...)`
    - si echec, tente vehicule libre: `choisirNouveauVehiculeDisponibleAnticipatif(...)`
    - met a jour les places restantes en memoire
4. persiste les affectations:
    - calcule l'ordre de depose via `calculerOrdreDepose(...)`
    - update SQL via `ReservationDAO.assignVehicule(reservationId, vehiculeId)`

### 5) Signification des regles R1a a R1d

1. R1a: priorite absolue a la reutilisation d'un vehicule deja utilise dans le meme groupe, si capacite restante suffisante
2. R1b (fallback): si aucun vehicule deja assigne ne peut absorber, on choisit un nouveau vehicule disponible
3. R1c: minimiser le gaspillage (marge la plus faible mais suffisante)
4. R1d: en cas d'egalite, preference Diesel puis id le plus petit

### 6) Construction des groupes pour l'affichage

`PlanificationService.construireGroupesParVehicule(date)`:

1. charge les planifications via `getPlanificationsByDate(date)`
2. reconstruit les fenetres TA
3. sous-regroupe par vehicule
4. pour chaque sous-groupe, appelle `construireUnGroupe(...)`

`construireUnGroupe(...)`:

1. prepare le `GroupeVehicule`
2. calcule l'ordre de depose nearest-neighbour (`calculerOrdreDepose`)
3. charge les distances (`getAllDistances`)
4. calcule heureDepart, heureRetour, distance totale, duree totale
5. produit l'itineraire detaille affiche dans la page planification

### 7) Rappel important sur l'IHM

Dans la page planification, le bloc "Regroupements par depart" est la reference pour les horaires de groupe (depart/retour communs par vehicule dans la fenetre TA).

Pour garder une IHM coherente:

1. les heures du tableau "Reservations planifiees" doivent reprendre les horaires du groupe
2. le tableau "Reservations non assignees" doit etre alimente par `getReservationsSansVehicule(...)`

### 8) Pourquoi cette architecture est maintenable

1. Controller: orchestration web uniquement
2. Service: regles metier et algorithmes Sprint
3. DAO: acces SQL et persistence
4. Model: structure de donnees transportee vers l'IHM

En cas d'evolution (Sprint 6+), modifier en priorite les fonctions de choix dans `PlanificationService` (et/ou `VehiculeAffectationService`) sans casser controller/DAO.



