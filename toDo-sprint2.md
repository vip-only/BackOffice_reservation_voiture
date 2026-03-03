## Sprint 2

### Modèle
- Vehicule : `id`, `reference`, `nombre_place`, `type_carburant` (D, ES, H(hyb), EL)

---

### Backoffice

#### Tâche 1 : CRUD Véhicule (avec filtre et recherche)
- [x] Création de la table `vehicule` (id, reference, nombre_place, type_carburant) - Fichier : `db/14-02-2026.sql`
- [x] Création du modèle `Vehicule` - Fichier : `model/Vehicule.java`
- [x] Création du DAO `VehiculeDAO` (insert, update, delete, findAll, findById, filtre, recherche) - Fichier : `dao/VehiculeDAO.java`
- [x] Création du controller `VehiculeController` (CRUD complet) - Fichier : `controller/VehiculeController.java`
- [x] Création de la page JSP véhicule (formulaire + liste avec filtre et recherche) - Fichiers : `vehicule.jsp`, `vehicule-form.jsp`

**URLs disponibles :**
| Action | Méthode | URL |
|--------|---------|-----|
| Liste + Filtre | GET | `/vehicule` |
| Liste filtrée | GET | `/vehicule?typeCarburant=D&keyword=VH` |
| Formulaire ajout | GET | `/vehicule/add` |
| Formulaire modif | GET | `/vehicule/edit?id=1` |
| Insertion | POST | `/vehicule/insert` |
| Mise à jour | POST | `/vehicule/update` |
| Suppression | POST | `/vehicule/delete` |

#### Tâche 2 : Protection appel API (list reservation)
- [ ] Créer la table `token` (id, token, date_heure_expiration) → généré via UUID
  - Pas d'interface, juste le code pour compléter dans la base
- [ ] Création du modèle `Token`
- [ ] Création du DAO `TokenDAO` (insert, findByToken, vérification expiration)
- [ ] Création du token dans main
- [ ] Appel API (exemple list reservation) → envoi du token, avant de lister les réservations, vérifier l'existence du token → gestion exception

---

### Frontoffice
- [ ] Stockage du token dans un fichier de configuration