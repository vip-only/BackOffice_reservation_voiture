## Sprint 2

### Modèle
- Vehicule : `id`, `reference`, `nombre_place`, `type_carburant` (D, ES, H(hyb), EL)

---

### Backoffice

#### Tâche 1 : CRUD Véhicule (avec filtre et recherche)
- [ ] Création de la table `vehicule` (id, reference, nombre_place, type_carburant)
- [ ] Création du modèle `Vehicule`
- [ ] Création du DAO `VehiculeDAO` (insert, update, delete, findAll, findById, filtre, recherche)
- [ ] Création du controller `VehiculeController` (CRUD complet)
- [ ] Création de la page JSP véhicule (formulaire + liste avec filtre et recherche)

#### Tâche 2 : Protection appel API (list reservation)
- [ ] Créer la table `token` (id, token, date_heure_expiration) → généré via UUID
  - Pas d'interface, juste le code pour compléter dans la base
- [ ] Création du modèle `Token`
- [ ] Création du DAO `TokenDAO` (insert, findByToken, vérification expiration)
- [ ] Appel API (exemple list reservation) → envoi du token, avant de lister les réservations, vérifier l'existence du token → gestion exception

---

### Frontoffice
- [ ] Stockage du token dans un fichier de configuration