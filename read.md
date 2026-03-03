Tsy mila tomcat io
Je suis sous java 17
Fa lancement jetty sur: localhost:8082 no misy (voir lancement.bat)

Ny route mandeha: localhost:8082/home

Zay bisous



sprint 3:
_ ajout de table parametre(
    ex: TA (temps d'attente):30min, Vitessemoyennne:30
    ) pas de crud jsute insertion
_ ajout de table aeroport: id, code, libelle
_ ajout de table distance: id, from, to, kilometer (atao id ve sa atao string??)
_ préparation de script d'réinitialisation 

 BackOffice: interface qui prend un parametre date --> page planification: voiture associé à une réservation départ et retour à telle heure pour cette meme personne; la voiture départ et toujours retour sur l'aeroport
 et en dessous la liste des réservation qui n'ont pas pu etre assigné à un véhicule
 Règle:
 - 1. voiture de capacité sup ou égal au nb de pers
 - 2. on assigne la place la plus proche de la capacité
 - 3. on assigne la voiture Diesel si choix dispo apres la regle 2


 liste reservation sans vehicule

