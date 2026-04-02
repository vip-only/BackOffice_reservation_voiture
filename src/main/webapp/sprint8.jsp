<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="com.backoffice.model.PlanificationReservation" %>
<%@ page import="com.backoffice.model.Reservation" %>
<%@ page import="com.backoffice.model.GroupeVehicule" %>
<%@ page import="com.backoffice.model.GroupeVehicule.EtapeItineraire" %>
<%@ page import="com.backoffice.model.Vehicule" %>
<%@ page import="com.backoffice.service.Sprint8Service" %>
<%@ page import="java.sql.Timestamp" %>
<%@ page import="java.text.SimpleDateFormat" %>
<!DOCTYPE html>
<html lang="fr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>BackOffice - Planification</title>
    <style>
        /* ================================================================
   PLANIFICATION – CSS PROFESSIONNEL
   Remplace uniquement le bloc <style> de la JSP.
   Aucun code Java / JSP modifié.
   Palette : fond blanc cassé / surfaces gris clair / accent indigo
   ================================================================ */

* { margin: 0; padding: 0; box-sizing: border-box; }

body {
    font-family: 'Segoe UI', system-ui, -apple-system, sans-serif;
    background: #f0f2f5;
    min-height: 100vh;
    padding: 28px 16px;
    color: #1a1d23;
}

/* ── Conteneur principal ── */
.container {
    background: #ffffff;
    padding: 36px 40px;
    border-radius: 12px;
    border: 1px solid #e2e5ea;
    box-shadow: 0 2px 12px rgba(0,0,0,0.06);
    max-width: 1200px;
    margin: 0 auto;
}

/* ── Titres ── */
h1 {
    color: #1a1d23;
    margin-bottom: 6px;
    text-align: center;
    font-size: 22px;
    font-weight: 600;
    letter-spacing: -0.3px;
}

h2 {
    color: #1a1d23;
    margin-top: 36px;
    margin-bottom: 14px;
    font-size: 15px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.6px;
    border-bottom: 1.5px solid #e2e5ea;
    padding-bottom: 8px;
}

/* ── Alertes ── */
.alert-success {
    background: #f0faf4;
    color: #1a6b3c;
    padding: 12px 16px;
    border-radius: 8px;
    margin-bottom: 20px;
    border-left: 3px solid #2d9c5f;
    font-size: 14px;
}

.alert-error {
    background: #fdf2f2;
    color: #8b1a1a;
    padding: 12px 16px;
    border-radius: 8px;
    margin-bottom: 20px;
    border-left: 3px solid #d94040;
    font-size: 14px;
}

/* ── Barre d'actions ── */
.actions-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 24px;
    flex-wrap: wrap;
    gap: 12px;
}

.date-form { display: flex; gap: 10px; align-items: center; }

.date-form label {
    font-size: 13px;
    font-weight: 500;
    color: #5a6070;
}

.date-form input {
    padding: 9px 12px;
    border: 1px solid #d1d5db;
    border-radius: 7px;
    font-size: 14px;
    color: #1a1d23;
    background: #fafbfc;
    transition: border-color 0.15s;
}

.date-form input:focus {
    outline: none;
    border-color: #4f63d2;
    background: #fff;
}

.btn-group { display: flex; gap: 8px; flex-wrap: wrap; }

.btn {
    padding: 9px 18px;
    border: none;
    border-radius: 7px;
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
    text-decoration: none;
    display: inline-block;
    transition: opacity 0.15s, transform 0.1s;
    letter-spacing: 0.1px;
}

.btn:hover { opacity: 0.88; transform: translateY(-1px); }
.btn:active { transform: translateY(0); }

.btn-primary  { background: #4f63d2; color: #fff; }
.btn-success  { background: #2d9c5f; color: #fff; }
.btn-info     { background: #0e7faa; color: #fff; }
.btn-secondary{ background: #6b7280; color: #fff; }

/* ── Info-box ── */
.info-box {
    background: #f5f7ff;
    padding: 14px 16px;
    border-radius: 8px;
    margin-bottom: 20px;
    border-left: 3px solid #4f63d2;
    font-size: 13.5px;
    color: #3a4060;
    line-height: 1.6;
}

.info-box strong { color: #1a1d23; }

/* ── Tables ── */
table { width: 100%; border-collapse: collapse; margin-top: 8px; font-size: 13.5px; }

th {
    background: #f5f6fa;
    color: #4b5263;
    font-weight: 600;
    font-size: 11.5px;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    padding: 10px 13px;
    text-align: left;
    border-bottom: 1.5px solid #e2e5ea;
}

td {
    padding: 10px 13px;
    text-align: left;
    border-bottom: 1px solid #f0f1f4;
    color: #2c3040;
}

tr:last-child td { border-bottom: none; }
tr:hover td { background: #f8f9fc; }

/* ── Badges carburant ── */
.badge {
    padding: 3px 9px;
    border-radius: 5px;
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.3px;
    display: inline-block;
}

.badge-diesel     { background: #e8e9ec; color: #2c2f38; }
.badge-essence    { background: #e8f7ee; color: #1a6b3c; }
.badge-hybride    { background: #e1f4fb; color: #0a5a7a; }
.badge-electrique { background: #eaedff; color: #3040a0; }

/* ── Heure mise en valeur ── */
.time-display { font-weight: 600; color: #4f63d2; font-size: 13px; }

/* ── État vide ── */
.empty-state {
    text-align: center;
    color: #9ba3b2;
    padding: 28px;
    font-style: italic;
    font-size: 13.5px;
}

/* ── Lien retour ── */
.back-link {
    display: block;
    text-align: center;
    margin-top: 28px;
    color: #4f63d2;
    text-decoration: none;
    font-weight: 500;
    font-size: 13.5px;
}

.back-link:hover { text-decoration: underline; }

/* ── Carte véhicule ── */
.vehicule-card {
    border: 1px solid #e2e5ea;
    border-radius: 10px;
    margin-bottom: 24px;
    overflow: hidden;
}

.vehicule-card-header {
    background: #1e2540;
    color: #e8ecf5;
    padding: 14px 20px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 10px;
}

.vehicule-card-header h3 {
    color: #e8ecf5;
    margin: 0;
    font-size: 15px;
    font-weight: 600;
    letter-spacing: 0.2px;
}

.vehicule-meta { display: flex; gap: 10px; font-size: 12px; }

.vehicule-meta span {
    background: rgba(255,255,255,0.1);
    border: 1px solid rgba(255,255,255,0.15);
    padding: 3px 10px;
    border-radius: 5px;
    color: #c8cfe8;
}

.vehicule-card-body { padding: 18px 20px; background: #fff; }

.passagers-section { margin-bottom: 18px; }

.passagers-section h4 {
    color: #6b7280;
    font-size: 11.5px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    margin-bottom: 10px;
}

/* ── Itinéraire ── */
.itineraire {
    position: relative;
    padding-left: 28px;
    margin: 14px 0;
}

.itineraire::before {
    content: '';
    position: absolute;
    left: 9px;
    top: 0;
    bottom: 0;
    width: 2px;
    background: #d1d5e8;
}

.etape {
    position: relative;
    margin-bottom: 12px;
    padding: 10px 14px;
    background: #f8f9fc;
    border-radius: 8px;
    border-left: 3px solid #4f63d2;
}

.etape::before {
    content: '';
    position: absolute;
    left: -24px;
    top: 13px;
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: #4f63d2;
    border: 2px solid #fff;
    box-shadow: 0 0 0 1px #4f63d2;
}

.etape-retour { border-left-color: #2d9c5f; }
.etape-retour::before { background: #2d9c5f; box-shadow: 0 0 0 1px #2d9c5f; }

.etape-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 4px;
}

.etape-trajet { font-weight: 600; color: #1a1d23; font-size: 13.5px; }
.etape-heure  { color: #4f63d2; font-weight: 600; font-size: 13px; }
.etape-details { font-size: 12px; color: #7a8299; }
.etape-passagers { font-size: 12px; color: #3a4060; margin-top: 4px; }

/* ── Résumé horaires ── */
.horaires-resume {
    display: flex;
    gap: 20px;
    padding: 12px 16px;
    background: #f5f7ff;
    border-radius: 8px;
    margin-top: 12px;
    flex-wrap: wrap;
    border: 1px solid #e0e4f8;
}

.horaire-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    min-width: 80px;
}

.horaire-label {
    font-size: 10.5px;
    color: #8b93a8;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    margin-bottom: 2px;
}

.horaire-value        { font-size: 15px; font-weight: 700; color: #4f63d2; }
.horaire-value.retour { color: #2d9c5f; }

/* ── Sous-carte véhicule dans un départ ── */
div[style*="border: 1px solid #e0e0e0"] {
    border: 1px solid #e8eaf0 !important;
    border-radius: 8px !important;
    background: #fafbfc !important;
}

    </style>
</head>
<body>
    <div class="container">
        <h1>Planification des Réservations</h1>

        <% if (request.getAttribute("success") != null) { %>
            <div class="alert-success"><%= request.getAttribute("success") %></div>
        <% } %>
        <% if (request.getAttribute("error") != null) { %>
            <div class="alert-error"><%= request.getAttribute("error") %></div>
        <% } %>

        <div class="actions-bar">
            <form action="/sprint8" method="GET" class="date-form">
                <label for="date">Date :</label>
                <input type="date" id="date" name="date"
                       value="<%= request.getAttribute("dateSelectionnee") != null ? request.getAttribute("dateSelectionnee") : "" %>" required>
                <button type="submit" class="btn btn-primary">Afficher</button>
            </form>
            <% if (request.getAttribute("dateSelectionnee") != null) { %>
            <div class="btn-group">
                <form action="/sprint8/executer" method="POST" style="display:inline;">
                    <input type="hidden" name="date" value="<%= request.getAttribute("dateSelectionnee") %>">
                    <button type="submit" class="btn btn-success">Executer Sprint 8</button>
                </form>
                <a href="/sprint7?date=<%= request.getAttribute("dateSelectionnee") %>" class="btn btn-secondary">Sprint 7</a>
                <a href="/planification?date=<%= request.getAttribute("dateSelectionnee") %>" class="btn btn-info">Voir planification</a>
            </div>
            <% } %>
        </div>

        <div class="info-box">
            <strong>Date sélectionnée :</strong> <%= request.getAttribute("dateSelectionnee") != null ? request.getAttribute("dateSelectionnee") : "Aucune" %>
            <br>
            <strong>Traces reservation_vehicule :</strong> <%= request.getAttribute("nbTracesReservationVehicule") != null ? request.getAttribute("nbTracesReservationVehicule") : 0 %>
        </div>

        <%
            Sprint8Service.ExecutionResult resultat = (Sprint8Service.ExecutionResult) request.getAttribute("resultatSprint8");
            if (resultat != null) {
        %>
        <div class="info-box">
            <strong>Resume Sprint 8 :</strong>
            Non assignées initiales = <%= resultat.getNonAssigneesInitiales() %>,
            Departs immediats = <%= resultat.getDepartsImmediats() %>,
            Decisions report TA = <%= resultat.getDecisionsReportTa() %>,
            Reservations affectees au retour = <%= resultat.getReservationsAffecteesRetour() %>,
            Reservations fractionnees au retour = <%= resultat.getReservationsFractionneesRetour() %>,
            Reservations traitees total = <%= resultat.getReservationsTraiteesTotal() %>,
            Non assignées finales = <%= resultat.getNonAssigneesFinales() %>.
        </div>
        <% } %>

        <h2>Regroupements par départ</h2>
        <%
            SimpleDateFormat dtFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            List groupesVehicules = (List) request.getAttribute("groupesVehicules");

            if (groupesVehicules != null && !groupesVehicules.isEmpty()) {
                java.util.LinkedHashMap parDepart = new java.util.LinkedHashMap();
                for (int gi = 0; gi < groupesVehicules.size(); gi++) {
                    GroupeVehicule g = (GroupeVehicule) groupesVehicules.get(gi);
                    String cleDep = g.getHeureDepart() != null ? dtFormat.format(g.getHeureDepart()) : "Inconnu";
                    if (!parDepart.containsKey(cleDep)) {
                        parDepart.put(cleDep, new java.util.ArrayList());
                    }
                    ((List) parDepart.get(cleDep)).add(g);
                }

                int numDepart = 0;
                java.util.Iterator itDepart = parDepart.entrySet().iterator();
                while (itDepart.hasNext()) {
                    java.util.Map.Entry entry = (java.util.Map.Entry) itDepart.next();
                    numDepart++;
                    String heureDep = (String) entry.getKey();
                    List vehiculesDuDepart = (List) entry.getValue();

                    int totalPaxDepart = 0;
                    int totalVehicules = vehiculesDuDepart.size();
                    for (int vi = 0; vi < vehiculesDuDepart.size(); vi++) {
                        totalPaxDepart += ((GroupeVehicule) vehiculesDuDepart.get(vi)).getTotalPassagers();
                    }
        %>
        <div class="vehicule-card">
            <div class="vehicule-card-header">
                <h3>DÉPART <%= numDepart %> — <%= heureDep %></h3>
                <div class="vehicule-meta">
                    <span><%= totalPaxDepart %> passager(s)</span>
                    <span><%= totalVehicules %> véhicule(s)</span>
                </div>
            </div>
            <div class="vehicule-card-body">
                <div class="passagers-section">
                    <h4>Passagers regroupés :</h4>
                    <table>
                        <thead><tr><th>Client</th><th>Passagers</th><th>Hôtel destination</th><th>Heure vol</th><th>Attente</th><th>Véhicule</th></tr></thead>
                        <tbody>
                        <%
                            Timestamp departCommun = ((GroupeVehicule) vehiculesDuDepart.get(0)).getHeureDepart();
                            for (int vi = 0; vi < vehiculesDuDepart.size(); vi++) {
                                GroupeVehicule gv = (GroupeVehicule) vehiculesDuDepart.get(vi);
                                for (int ri = 0; ri < gv.getReservations().size(); ri++) {
                                    Reservation res = (Reservation) gv.getReservations().get(ri);
                                    long attenteMin = 0;
                                    if (departCommun != null && res.getDateHeureArrivee() != null) {
                                        attenteMin = (departCommun.getTime() - res.getDateHeureArrivee().getTime()) / 60000;
                                    }
                        %>
                            <tr>
                                <td><%= res.getClient() %></td>
                                <td><%= res.getNombrePassager() %></td>
                                <td><%= res.getNomHotel() %></td>
                                <td class="time-display"><%= res.getDateHeureArrivee() != null ? dtFormat.format(res.getDateHeureArrivee()) : "-" %></td>
                                <td><%= attenteMin > 0 ? attenteMin + " min" : "0 min (départ)" %></td>
                                <td><%= gv.getVehicule().getReference() %></td>
                            </tr>
                        <%      }
                            }
                        %>
                        </tbody>
                    </table>
                </div>

                <!-- Itinéraire par véhicule dans ce départ -->
                <%
                    for (int vi2 = 0; vi2 < vehiculesDuDepart.size(); vi2++) {
                        GroupeVehicule gv = (GroupeVehicule) vehiculesDuDepart.get(vi2);
                        Vehicule veh = gv.getVehicule();
                        String typeCarb = veh.getTypeCarburant();
                        String badgeCl = ""; String typeLbl = "";
                        if (typeCarb != null) {
                            switch (typeCarb) {
                                case "D":  badgeCl = "badge-diesel";     typeLbl = "Diesel";     break;
                                case "ES": badgeCl = "badge-essence";    typeLbl = "Essence";    break;
                                case "H":  badgeCl = "badge-hybride";    typeLbl = "Hybride";    break;
                                case "EL": badgeCl = "badge-electrique"; typeLbl = "Électrique"; break;
                                default:   typeLbl = typeCarb;
                            }
                        }
                %>
                <div style="border: 1px solid #e0e0e0; border-radius: 8px; padding: 15px; margin-top: 15px; background: #fafafa;">
                    <h4 style="margin-bottom: 10px;"><%= veh.getReference() %>
                        <span class="badge <%= badgeCl %>"><%= typeLbl %></span>
                        — <%= gv.getTotalPassagers() %> / <%= veh.getNombrePlace() %> places
                    </h4>

                    <div class="itineraire">
                    <%
                        java.util.List etapes = gv.getItineraire();
                        if (etapes != null) {
                            for (int i = 0; i < etapes.size(); i++) {
                                EtapeItineraire etape = (EtapeItineraire) etapes.get(i);
                                boolean isRetour = (i == etapes.size() - 1);
                    %>
                        <div class="etape <%= isRetour ? "etape-retour" : "" %>">
                            <div class="etape-header">
                                <span class="etape-trajet"><%= etape.getLieuDepart() %> → <%= etape.getLieuArrivee() %></span>
                                <span class="etape-heure"><%= etape.getHeureArrivee() != null ? dtFormat.format(etape.getHeureArrivee()) : "" %></span>
                            </div>
                            <div class="etape-details">
                                <%= String.format("%.1f", etape.getDistanceKm()) %> km — <%= etape.getDureeMinutes() %> min
                            </div>
                            <% if (etape.getPassagersDeposes() != null && !etape.getPassagersDeposes().isEmpty()) { %>
                            <div class="etape-passagers">
                                Dépose : <%= String.join(", ", etape.getPassagersDeposes()) %>
                            </div>
                            <% } %>
                        </div>
                    <%      }
                        }
                    %>
                    </div>

                    <div class="horaires-resume">
                        <div class="horaire-item">
                            <span class="horaire-label">Départ TNR</span>
                            <span class="horaire-value"><%= gv.getHeureDepart() != null ? dtFormat.format(gv.getHeureDepart()) : "-" %></span>
                        </div>
                        <div class="horaire-item">
                            <span class="horaire-label">Retour TNR</span>
                            <span class="horaire-value retour"><%= gv.getHeureRetour() != null ? dtFormat.format(gv.getHeureRetour()) : "-" %></span>
                        </div>
                        <div class="horaire-item">
                            <span class="horaire-label">Distance</span>
                            <span class="horaire-value"><%= String.format("%.1f", gv.getDistanceTotaleKm()) %> km</span>
                        </div>
                        <div class="horaire-item">
                            <span class="horaire-label">Durée</span>
                            <span class="horaire-value"><%= gv.getDureeTotaleMinutes() %> min</span>
                        </div>
                    </div>
                </div>
                <% } %>
            </div>
        </div>
        <%  }
            } else {
        %>
        <div class="empty-state">Aucun regroupement pour cette date</div>
        <% } %>

        <h2>Table reservation_vehicule (assignements)</h2>
        <table>
            <thead>
                <tr>
                    <th>Reservation ID</th><th>Client</th><th>Vehicule</th><th>Vehicule ID</th>
                    <th>Nb passagers</th><th>Mode</th><th>Hotel</th><th>Heure vol</th><th>Date assignation</th>
                </tr>
            </thead>
            <tbody>
            <%
                List rvRows = (List) request.getAttribute("reservationVehiculeRows");
                if (rvRows != null && !rvRows.isEmpty()) {
                    for (int i = 0; i < rvRows.size(); i++) {
                        Map row = (Map) rvRows.get(i);
                        Timestamp tVol = (Timestamp) row.get("dateHeureArrivee");
                        Timestamp tAss = (Timestamp) row.get("dateAssignation");
            %>
                <tr>
                    <td><%= row.get("reservationId") %></td>
                    <td><%= row.get("client") %></td>
                    <td><%= row.get("referenceVehicule") %></td>
                    <td><%= row.get("vehiculeId") %></td>
                    <td><%= row.get("nbPassagers") %></td>
                    <td><%= row.get("modeAssignation") %></td>
                    <td><%= row.get("nomHotel") %></td>
                    <td class="time-display"><%= tVol != null ? dtFormat.format(tVol) : "-" %></td>
                    <td class="time-display"><%= tAss != null ? dtFormat.format(tAss) : "-" %></td>
                </tr>
            <%      }
                } else {
            %>
                <tr><td colspan="9" class="empty-state">Aucun enregistrement dans reservation_vehicule pour cette date</td></tr>
            <% } %>
            </tbody>
        </table>

        <h2>Réservations planifiées</h2>
        <table>
            <thead>
                <tr>
                    <th>Client</th><th>Passagers</th><th>Hotel</th><th>Distance</th>
                    <th>Vehicule</th><th>Type</th><th>Date/Heure depart</th>
                    <th>Date/Heure retour</th><th>Duree totale</th>
                </tr>
            </thead>
            <tbody>
            <%
                List planifications = (List) request.getAttribute("planifications");
                List groupesVehiculesTbl = (List) request.getAttribute("groupesVehicules");

                java.util.Map depParReservation = new java.util.HashMap();
                java.util.Map retParReservation = new java.util.HashMap();
                java.util.Map distParReservation = new java.util.HashMap();
                java.util.Map dureeParReservation = new java.util.HashMap();
                java.util.Map depAssignationParReservation = new java.util.HashMap();
                java.util.Map modeAssignationParReservation = new java.util.HashMap();
                java.util.Map retourRedispoParReservation = new java.util.HashMap();

                List rvRowsForPlanif = (List) request.getAttribute("reservationVehiculeRows");
                if (rvRowsForPlanif != null) {
                    for (int rvi = 0; rvi < rvRowsForPlanif.size(); rvi++) {
                        Map rowRv = (Map) rvRowsForPlanif.get(rvi);
                        Object resIdObj = rowRv.get("reservationId");
                        Object depObj = rowRv.get("dateAssignation");
                        if (resIdObj != null && depObj instanceof Timestamp) {
                            depAssignationParReservation.put(resIdObj, (Timestamp) depObj);
                        }
                        if (resIdObj != null && rowRv.get("modeAssignation") != null) {
                            modeAssignationParReservation.put(resIdObj, String.valueOf(rowRv.get("modeAssignation")));
                        }
                        Object retObj = rowRv.get("dateHeureRedisponibilite");
                        if (resIdObj != null && retObj instanceof Timestamp) {
                            retourRedispoParReservation.put(resIdObj, (Timestamp) retObj);
                        }
                    }
                }

                if (groupesVehiculesTbl != null) {
                    for (int gti = 0; gti < groupesVehiculesTbl.size(); gti++) {
                        GroupeVehicule gvTbl = (GroupeVehicule) groupesVehiculesTbl.get(gti);
                        Timestamp depG = gvTbl.getHeureDepart();
                        Timestamp retG = gvTbl.getHeureRetour();

                        List resG = gvTbl.getReservations();
                        if (resG != null) {
                            for (int rgi = 0; rgi < resG.size(); rgi++) {
                                Reservation rr = (Reservation) resG.get(rgi);
                                depParReservation.put(rr.getId(), depG);
                                retParReservation.put(rr.getId(), retG);
                                distParReservation.put(rr.getId(), gvTbl.getDistanceTotaleKm());
                                dureeParReservation.put(rr.getId(), gvTbl.getDureeTotaleMinutes());
                            }
                        }
                    }
                }

                if (planifications != null && !planifications.isEmpty()) {
                    for (int pi = 0; pi < planifications.size(); pi++) {
                        PlanificationReservation p = (PlanificationReservation) planifications.get(pi);
                        Reservation r = p.getReservation();
                        String tc = r.getTypeCarburant();
                        String bc = ""; String tl = "";
                        if (tc != null) {
                            switch (tc) {
                                case "D":  bc = "badge-diesel";     tl = "Diesel";     break;
                                case "ES": bc = "badge-essence";    tl = "Essence";    break;
                                case "H":  bc = "badge-hybride";    tl = "Hybride";    break;
                                case "EL": bc = "badge-electrique"; tl = "Electrique"; break;
                                default:   tl = tc;
                            }
                        }

                        // Date/Heure depart selon mode:
                        // - NORMAL_TA: depart groupe (regroupement + TA)
                        // - RETOUR_IMMEDIAT: depart immediat (date_assignation)
                        // + fallback robuste.
                        String modeAff = (String) modeAssignationParReservation.get(r.getId());
                        Timestamp depAff;
                        if ("NORMAL_TA".equals(modeAff)) {
                            depAff = (Timestamp) depParReservation.get(r.getId());
                            if (depAff == null) depAff = p.getHeureDepart();
                            if (depAff == null) depAff = (Timestamp) depAssignationParReservation.get(r.getId());
                            if (depAff == null) depAff = r.getDateHeureArrivee();
                        } else if ("RETOUR_IMMEDIAT".equals(modeAff)) {
                            depAff = (Timestamp) depAssignationParReservation.get(r.getId());
                            if (depAff == null) depAff = (Timestamp) depParReservation.get(r.getId());
                            if (depAff == null) depAff = p.getHeureDepart();
                            if (depAff == null) depAff = r.getDateHeureArrivee();
                        } else {
                            depAff = (Timestamp) depParReservation.get(r.getId());
                            if (depAff == null) depAff = p.getHeureDepart();
                            if (depAff == null) depAff = (Timestamp) depAssignationParReservation.get(r.getId());
                            if (depAff == null) depAff = r.getDateHeureArrivee();
                        }

                        Integer dureeAff = (Integer) dureeParReservation.get(r.getId());
                        if (dureeAff == null) {
                            dureeAff = p.getDureeTotaleMinutes();
                        }

                        // Date/Heure retour priorisee sur la redisponibilite calculee (backend).
                        Timestamp retAff = (Timestamp) retourRedispoParReservation.get(r.getId());
                        if (retAff == null && depAff != null) {
                            retAff = new Timestamp(depAff.getTime() + (long) dureeAff * 60L * 1000L);
                        }
                        if (retAff == null) {
                            retAff = (Timestamp) retParReservation.get(r.getId());
                        }
                        if (retAff == null) {
                            retAff = p.getHeureRetour();
                        }

                        Double distAff = (Double) distParReservation.get(r.getId());
                        if (distAff == null) {
                            distAff = p.getDistanceKm();
                        }
            %>
                <tr>
                    <td><%= r.getClient() %></td>
                    <td><%= r.getNombrePassager() %></td>
                    <td><%= r.getNomHotel() %></td>
                    <td><%= String.format("%.1f", distAff) %> km</td>
                    <td><%= r.getReferenceVehicule() %></td>
                    <td><span class="badge <%= bc %>"><%= tl %></span></td>
                    <td class="time-display"><%= depAff != null ? dtFormat.format(depAff) : "-" %></td>
                    <td class="time-display"><%= retAff != null ? dtFormat.format(retAff) : "-" %></td>
                    <td><%= dureeAff %> min</td>
                </tr>
            <%      }
                } else {
            %>
                <tr><td colspan="9" class="empty-state">Aucune reservation planifiee pour cette date</td></tr>
            <% } %>
            </tbody>
        </table>

        <h2>Réservations non assignées</h2>
        <table>
            <thead>
                <tr><th>ID</th><th>Client</th><th>Passagers</th><th>Hôtel</th><th>Date/Heure d'arrivée</th></tr>
            </thead>
            <tbody>
            <%
                List reservationsSansVehicule = (List) request.getAttribute("reservationsSansVehicule");
                if (reservationsSansVehicule != null && !reservationsSansVehicule.isEmpty()) {
                    for (int ri = 0; ri < reservationsSansVehicule.size(); ri++) {
                        Reservation r = (Reservation) reservationsSansVehicule.get(ri);
            %>
                <tr>
                    <td><%= r.getId() %></td>
                    <td><%= r.getClient() %></td>
                    <td><%= r.getNombrePassager() %></td>
                    <td><%= r.getNomHotel() %></td>
                    <td class="time-display"><%= dtFormat.format(r.getDateHeureArrivee()) %></td>
                </tr>
            <%      }
                } else {
            %>
                <tr><td colspan="5" class="empty-state">Toutes les réservations ont un véhicule assigné</td></tr>
            <% } %>
            </tbody>
        </table>

        <a href="/home" class="back-link">Retour à l'accueil</a>
    </div>
</body>
</html>
