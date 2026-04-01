# Sprint 8 - Preuve Soutenance (T1..T7)

## Prerequis unique
1. Executer [db/reset-db-sprint8.sql](../reset-db-sprint8.sql)
2. Demarrer l'application
3. Pour chaque test Tn:
   - Executer le script SQL Tn
   - Appeler `POST /sprint8/executer` avec `date=2026-03-11`
   - Reexecuter la section assertions du script (bloc `POST-ASSERTIONS`)

## Notes
- Chaque script T1..T7 fait `TRUNCATE reservation_vehicule` + `TRUNCATE reservation`.
- Les referentiels (hotel, vehicule, distance, parametre) restent ceux du reset.
- Les assertions renvoient `PASS` ou `FAIL`.
