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
>>>>>>> Stashed changes



