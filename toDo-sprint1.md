## BackOffice - To Do

### Fonctionnalités

- [x] Formulaire d'insertion de reservation (pas encore protégé) - URL : `/reservation`
- [x] Liste déroulante hotel venant de la base de données - URL : `/reservation`
- [x] API pour lister les réservations en JSON - URL : `/api/reservations`
- [x] API pour récupérer les détails d'une réservation par ID - URL : `/api/reservations/{id}`
- [x] API pour supprimer une réservation - URL : `/api/reservations/{id}` (DELETE)
- [x] API pour mettre à jour une réservation - URL : `/api/reservations/{id}` (PUT)
- [x] Formulaire d'insertion de reservation (pas encore protégé) 
- [x] Liste déroulante hotel venant de la base de données


### Scripts SQL
- [x] Script de création des tables (hotel, reservation) - Fichier : `db/script-test.sql`
- [x] Script d'insertion des hotels - Fichier : `db/script-test.sql`

### Front Office
- [x] Création controller pour lister les réservations - URL : `/api/reservations`
- [x] Création de la page list-reservations - URL : `/list-reservations`
- [x] Adapter la page list-reservations pour le filtre par date - URL : `/list-reservations?date=YYYY-MM-DD`
- [x] Adapter la page list-reservations pour l'affichage du nom de l'hôtel au lieu de l'id - URL : `/list-reservations`