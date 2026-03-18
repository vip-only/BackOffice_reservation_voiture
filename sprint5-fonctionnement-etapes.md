# Sprint 5 - Fonctionnement etape par etape

Objectif du document:
- Expliquer le flux Sprint 5 dans l ordre d execution reel.
- Associer chaque etape a ses fonctions.
- Donner l utilite de l algo (pour maintenance et evolution).

---

## Vue globale (ordre reel)

1. PlanificationController.regrouperEtAssigner(date)
2. PlanificationService.regrouperEtAssigner(date)
3. PlanificationService.assignerVehiculesAutomatiquement(date)
4. PlanificationService.construireFenetresTA(...)
5. PlanificationService.calculerHeureDepart(...)
6. PlanificationService.assignerGroupeReservations(...)
7. PlanificationService.choisirVehiculeDejaAssigneR1a(...)
8. PlanificationService.choisirNouveauVehiculeDisponibleAnticipatif(...)
9. ReservationDAO.assignVehicule(...)
10. PlanificationService.construireGroupesParVehicule(date)
11. PlanificationService.construireUnGroupe(...)
12. PlanificationService.calculerOrdreDepose(...)
13. PlanificationService.getAllDistances(...)

---

## Etape 1 - Point d entree Sprint 5 (R1 global)

Fonction:
- PlanificationController.regrouperEtAssigner(date)

Ce que la fonction fait:
- Recoit la date depuis le bouton Regrouper et assigner.
- Appelle le service metier.

Retour:
- ModelView avec:
  - planifications
  - reservationsSansVehicule
  - groupesVehicules

Utilite algo:
- Declencher un pipeline unique: assignation puis reconstruction d affichage.

---

## Etape 2 - Orchestration metier

Fonction:
- PlanificationService.regrouperEtAssigner(date)

Ce que la fonction fait:
1. Appelle assignerVehiculesAutomatiquement(date)
2. Appelle construireGroupesParVehicule(date)

Retour:
- Liste GroupeVehicule finale

Utilite algo:
- Garantir que les groupes affiches sont bases sur l etat de base le plus recent.

---

## Etape 3 - Chargement des reservations a traiter (R0)

Fonctions:
- PlanificationService.assignerVehiculesAutomatiquement(date)
- ReservationDAO.findWithoutVehiculeByDate(date)

Ce que la fonction fait:
- Charge uniquement les reservations sans vehicule de la date.
- Le SQL les trie deja par nombre_passager DESC, puis date_heure_arrivee.

Retour:
- List<Reservation> a assigner

Utilite algo:
- Prioriser les gros volumes passagers pour reduire les echecs d assignation.

---

## Etape 4 - Lecture du TA et creation des fenetres

Fonctions:
- ParametreDAO.getTempsAttente()
- PlanificationService.construireFenetresTA(reservations, taMinutes)

Ce que la fonction fait:
- Lit TA (temps attente) depuis la base.
- Construit des groupes temporels fixes:
  - debut = premier vol de la fenetre
  - fenetre = [debut, debut + TA]
  - si vol hors fenetre -> nouvelle fenetre

Retour:
- List<List<Reservation>> (groupes temporels)

Utilite algo:
- Permettre le covoiturage inter-vols proches (meme fenetre temporelle).

---

## Etape 5 - Heure de depart de groupe

Fonction:
- PlanificationService.calculerHeureDepart(groupe)

Ce que la fonction fait:
- Prend MAX(date_heure_arrivee) dans le groupe.

Retour:
- Timestamp heureDepart

Utilite algo:
- Tous les passagers d une fenetre partent ensemble a l heure du dernier vol de la fenetre.

---

## Etape 6 - Preparation des candidats vehicules

Fonctions:
- PlanificationService.assignerGroupeReservations(groupe, heureDepart)
- PlanificationService.trouverHotelPlusLoin(groupe)
- ReservationService.getVehiculesDisponibles(heureDepart, idHotelPlusLoin)

Ce que la fonction fait:
- Estime le scenario de conflit avec l hotel le plus loin.
- Charge les vehicules non occupes sur l intervalle estime.

Retour:
- Liste vehicules disponibles pour ce groupe

Utilite algo:
- Eviter de re-utiliser un vehicule deja en conflit horaire.

---

## Etape 7 - R1a priorite absolue (reutiliser vehicule deja pris)

Fonction:
- PlanificationService.choisirVehiculeDejaAssigneR1a(nbPax, placesRestantes, vehiculesMap)

Ce que la fonction fait:
- Parcourt les vehicules deja utilises dans le groupe courant.
- Garde ceux avec capacite restante >= nbPax.
- Departage:
  1) marge restante minimale (R1c)
  2) Diesel puis id plus petit (R1d)

Retour:
- Vehicule choisi ou null

Utilite algo:
- Minimiser le nombre de vehicules mobilises.

---

## Etape 8 - R1b fallback (nouveau vehicule)

Fonction:
- PlanificationService.choisirNouveauVehiculeDisponibleAnticipatif(nbPax, groupe, indexCourant, vehiculesDisponibles, placesRestantes)

Ce que la fonction fait:
- Si R1a echoue, cherche un vehicule libre.
- Filtre capacite >= nbPax.
- Critere anticipatif: maximise le nombre de futures reservations qui pourront tenir dans la marge restante.
- Egalites:
  1) marge la plus faible (R1c)
  2) Diesel puis id plus petit (R1d)

Retour:
- Vehicule choisi ou null

Utilite algo:
- Eviter les choix myopes qui bloquent les reservations suivantes.

---

## Etape 9 - Enregistrement en base

Fonctions:
- PlanificationService.assignerGroupeReservations(...)
- PlanificationService.calculerOrdreDepose(...)
- ReservationDAO.assignVehicule(reservationId, vehiculeId)

Ce que la fonction fait:
- Regroupe les reservations par vehicule choisi.
- Calcule l ordre de depose.
- Persiste chaque affectation en base.

Retour:
- Aucun retour metier (side effect SQL update)

Utilite algo:
- Ecrire un etat coherent reservation -> vehicule.

---

## Etape 10 - Reconstruction des groupes d affichage

Fonctions:
- PlanificationService.construireGroupesParVehicule(date)
- PlanificationService.getPlanificationsByDate(date)

Ce que la fonction fait:
- Relit les planifications du jour.
- Re-fait les fenetres TA.
- Sous-groupe par vehicule.
- Appelle construireUnGroupe pour creer les blocs affiches.

Retour:
- List<GroupeVehicule>

Utilite algo:
- Affichage metier clair: depart, retour, passagers, itineraire.

---

## Etape 11 - Calcul itineraire et horaires finaux

Fonctions:
- PlanificationService.construireUnGroupe(...)
- PlanificationService.calculerOrdreDepose(...)
- PlanificationService.getAllDistances(...)
- ParametreDAO.getVitesseMoyenne()

Ce que la fonction fait:
- Cree le vehicule de groupe.
- Calcule ordre de depose nearest-neighbour.
- Calcule chaque etape (distance, duree, heure arrivee).
- Ajoute retour TNR.
- Fixe heureRetour, distanceTotaleKm, dureeTotaleMinutes.

Retour:
- GroupeVehicule complet

Utilite algo:
- Donner un plan trajet executable et un affichage comprehensible par les ops.

---

## Etape 12 - Regles R1 resumees (pratiques)

R1a:
- Reutiliser d abord un vehicule deja engage dans le groupe si possible.

R1b:
- Si impossible, prendre un vehicule libre.

R1c:
- Minimiser la marge de capacite (moins de gaspillage).

R1d:
- A egalite, priorite Diesel, puis id plus petit.

---

## Pourquoi cet algo est utile (maintenabilite)

1. Separation claire des couches:
- Controller: orchestration web
- Service: regles metier
- DAO: SQL

2. Fonctions specialisees:
- Une fonction = une responsabilite principale.

3. Evolution simple:
- Pour changer les regles, modifier surtout:
  - choisirVehiculeDejaAssigneR1a
  - choisirNouveauVehiculeDisponibleAnticipatif

4. Rejouabilite:
- Le flux regrouperEtAssigner permet de recalculer proprement un jour complet.

---

## Checklist rapide de verification (pour equipe)

1. TA lu depuis parametre
2. Fenetres TA correctes
3. Tri passagers DESC par groupe
4. R1a tente avant vehicule libre
5. Persistance SQL faite pour chaque reservation assignee
6. Groupes affiches avec heureDepart/heureRetour coherentes
7. Non assignees = uniquement reservations sans vehicule
