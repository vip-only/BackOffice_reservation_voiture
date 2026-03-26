-- Ajout de l'heure de disponibilite initiale des vehicules
-- Une voiture ne peut etre affectee que si l'heure de depart demandee >= heure_disponibilite

ALTER TABLE vehicule
ADD COLUMN IF NOT EXISTS heure_disponibilite TIME NOT NULL DEFAULT '00:00:00';

-- Initialisation explicite des lignes existantes (si necessaire)
UPDATE vehicule
SET heure_disponibilite = COALESCE(heure_disponibilite, '00:00:00');
