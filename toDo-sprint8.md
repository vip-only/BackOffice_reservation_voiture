# Sprint 8 - Gestion des non assignés et regroupements

## Objectif
Compléter le sprint 7 en ajoutant la gestion des **réservations non assignées** et les règles de **déclenchement des regroupements**.

## Fonctionnalités (liste)
- [ ] Gestion des réservations non assignées après un regroupement
- [ ] Priorité aux non assignés pendant un regroupement, il y a ordre décroissant
- [ ] S'il reste des places, compléter par la réservation la plus adaptée (nouvelle ou non assignée), avec priorité non assignée en cas d'égalité
- [ ] Déclenchement d’un regroupement par une voiture disponible
- [ ] Gestion du cas où une voiture rejoint un regroupement déjà existant
- [ ] Gestion de l’heure de départ selon la disponibilité et la TA

## Contraintes / Règles métiers

### Définition d’une réservation non assignée
Une réservation non assignée désigne une réservation **complète ou partielle** qui n’a pas eu de voiture :
- après son regroupement ;
- ou qui n’a jamais eu de regroupement faute de disponibilité de voiture.

Après cela, elle est considérée comme **non assignée** et doit être priorisée au prochain regroupement.

Exemple :
- `resa1` contient 8 personnes et arrive à 8h.
- Une voiture est disponible à 8h avec 4 places.
- 4 personnes de `resa1` restent sans véhicule.
- Elles sont alors considérées comme **non assignées** jusqu’à ce qu’une voiture leur soit attribuée.

### Pendant un regroupement
- Les réservations non assignées sont prioritaires.
- L’ordre de traitement des non assignés est décroissant.
- Une fois les non assignés traités, les places restantes reviennent à la réservation qui convient le mieux (logique de proximité de capacité comme sprint7), qu’elle soit nouvelle ou non assignée.
- En cas d’égalité sur l’adéquation, la réservation non assignée est prioritaire.
- Après ce remplissage, le reste des réservations suit la logique existante du sprint7 (référence au code).

### Déclenchement d’un regroupement
- Une voiture disponible déclenche un regroupement, en tenant compte de la TA.
- Exception : si une voiture redevient disponible pendant un regroupement déjà existant, elle rejoint ce regroupement au lieu d’en créer un nouveau.
- Tant qu’il reste des réservations non assignées, chaque voiture de retour crée un regroupement.
- Un regroupement commence aussi dès qu’il y a une nouvelle réservation et un véhicule disponible (sans passagers déjà embarqués) capable de la prendre.

Exemple :
- Une voiture devient disponible à 8h20.
- Un regroupement existe déjà à 8h, déclenché par une autre voiture ou par une réservation ultérieure.
- La voiture de 8h20 s’ajoute au regroupement existant.

### Heure de départ
- L’événement le plus tôt dans la séquence (arrivée, retour, (re)disponibilité, nouvelle reservation avec voiture disponible comme sprint7) fixe l’heure de début du groupement.
- Une voiture part immédiatement quand elle est remplie par des réservations non assignées.
- Une voiture part immédiatement quand elle est remplie par des réservations non assignées et par une ou plusieurs nouvelles réservations arrivées à la même heure que le début de sa (re)disponibilité.
- Dans les autres cas, un regroupement est créé à son arrivée : la TA est prise en compte pour déterminer l’heure de départ.
- Si une voiture commence un regroupement avec des non assignés mais n’est pas totalement remplie :
	- s’il n’y a pas de nouvelles réservations dans l’intervalle `[arrivée, arrivée + TA]`, elle repart directement ;
	- s’il y a des nouvelles réservations dans cet intervalle, on complète la voiture avec ces réservations avant le départ.

## Implémentation technique
1. Ajouter la gestion des réservations non assignées dans le service d’assignation.
2. Modifier la logique de regroupement pour prioriser les non assignés.
3. Gérer le cas où une voiture rejoint un regroupement déjà en cours.
4. Calculer l’heure de départ à partir de la TA et de l’heure de (re)disponibilité.
5. Pour l’exécution `POST /sprint8/executer` :
	- appliquer d’abord la priorité non assignés (ordre décroissant),
	- puis compléter les places restantes par meilleure adéquation (nouvelle ou non assignée), avec priorité non assignée en cas d’égalité,
	- puis laisser les réservations restantes suivre la logique existante du code sprint7.

## Tests & critères d’acceptation
| Test | Scénario | Résultat attendu |
|------|----------|------------------|
| T1 | 8 pax, voiture 4 places disponible | 4 pax assignés, 4 pax non assignés |
| T2 | Non assignés présents lors d’un regroupement | Les non assignés sont traités en priorité |
| T3 | Voiture redevenant disponible pendant un regroupement existant | Elle rejoint le regroupement déjà ouvert |
| T4 | Voiture remplie uniquement par non assignés | Départ immédiat |
| T5 | Voiture remplie par non assignés + nouvelles réservations à la même heure | Départ immédiat |