## BackOffice - To Do (Sprint 3)

### Fonctionnalités
- [ ] Ajout de la table `parametre` (ex: TA (temps d'attente): 30min, Vitessemoyenne: 30) — insertion uniquement, pas de CRUD
- [ ] Ajout de la table `lieu` : id, code, libelle
- [ ] Ajout de la table `distance` : id, from, to, kilometer (décider si `from`/`to` sont des ids ou des strings)
- [ ] Préparer script de réinitialisation de la base (drop/create + insert données de référence)
- [ ] BackOffice : page de planification qui prend un paramètre date — URL : `/planification?date=YYYY-MM-DD`
  - afficher les voitures associées à une réservation (départ et retour à telle heure) pour la même personne
  - afficher la liste des réservation qui n'ont pas pu etre assigné à un véhicule
  - la voiture part et revient toujours à l'aéroport

### Scripts SQL
- [ ] Script de création des tables : `db/script-sprint3.sql`
  - création `parametre` (clé, valeur, unité)
  - création `lieu` (id, code, libelle)
  - création `distance` (id, from, to, kilometer) — préciser type des champs from/to
- [ ] Script d'insertion des paramètres par défaut : `db/script-sprint3.sql`
- [ ] Script de réinitialisation : `db/reset-db-sprint3.sql`

### BackOffice / Planification
- Page: `/planification?date=YYYY-MM-DD`
- Fonctionnalité: pour chaque réservation du jour, proposer /assigner une voiture (départ et retour à l'aéroport) selon les règles ci-dessous.

### Règles d'affectation
1. La voiture doit 
avoir une capacité >= nombre de personnes de la réservation.  
2. Choisir la voiture dont la capacité est la plus proche (la plus petite capacité satisfaisante).  
3. Si plusieurs voitures respectent la règle 2, préférer une voiture Diesel si disponible. 

### Notes / Décisions à prendre
- Pour `distance.from` / `distance.to` : utiliser `lieu.id` (int) ou `code` (string) ? Décider pour normalisation et simplicité des jointures.
- Paramètres stockés en `parametre` : utiliser clé unique (ex: `TA`, `VITESSE_MOYENNE`) et colonne `valeur` (string/number).
- Prévoir index sur les colonnes utilisées pour filtrer par date et jointures (reservation.date, lieu.code/id).


-assigner par ordre de passager le plus grand