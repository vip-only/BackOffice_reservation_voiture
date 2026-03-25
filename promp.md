on va changer les regles d'assignation, 
_ on va d'abord ranger les reservations par ordre decroissant, on assigne le plus grand si il y a des restes de places dans la voiture on va assigner une reservation avec le nombre de place le plus proche par valeur absolu (en cas d'egalite, prendre le plus grand)

_on reprendra l'ordre decroissant. Dans le cas où la reservation sera divisée, on regardera sur la priorité du nb place. Si on a 2 reservations de meme nb: 5/7 de resa1 a ete assigné, pour la prochaine assignation il faut assigner d'abord la resa2(pas encore assigner avant la resa 2)

_ On a un vehicule déjà assigné à une reservaation mais pas encore rempli completement, on va regarder les reservations les plus proches de ce dernier par exempple on a 2places dispo et une reservation 7,3,1, on regarde le plus proche dans ce cas on a soit 3, soit 1 mais on prendra 3 

_ Dans le cas d'une separation de reservation entre 2 fenetre horaire, la reservation sera considéré tel une nouvelle réservation: donc reconsidération des ordres décroissants, donc 0 priorité par rapport aux autres reservationd cette horaire.