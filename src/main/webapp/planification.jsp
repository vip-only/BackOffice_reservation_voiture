<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List" %>
<%@ page import="com.backoffice.model.PlanificationReservation" %>
<%@ page import="com.backoffice.model.Reservation" %>
<%@ page import="com.backoffice.model.GroupeVehicule" %>
<%@ page import="com.backoffice.model.GroupeVehicule.EtapeItineraire" %>
<%@ page import="com.backoffice.model.Vehicule" %>
<%@ page import="java.text.SimpleDateFormat" %>
<!DOCTYPE html>
<html lang="fr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>BackOffice - Planification</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            padding: 20px;
        }
        .container {
            background: white; padding: 30px; border-radius: 10px;
            box-shadow: 0 10px 25px rgba(0,0,0,0.2);
            max-width: 1200px; margin: 0 auto;
        }
        h1 { color: #667eea; margin-bottom: 10px; text-align: center; }
        h2 {
            color: #667eea; margin-top: 30px; margin-bottom: 15px;
            font-size: 20px; border-bottom: 2px solid #667eea; padding-bottom: 8px;
        }
        .alert-success {
            background: #d4edda; color: #155724; padding: 12px 15px;
            border-radius: 5px; margin-bottom: 20px; border-left: 4px solid #28a745;
        }
        .alert-error {
            background: #f8d7da; color: #721c24; padding: 12px 15px;
            border-radius: 5px; margin-bottom: 20px; border-left: 4px solid #dc3545;
        }
        .actions-bar {
            display: flex; justify-content: space-between; align-items: center;
            margin-bottom: 20px; flex-wrap: wrap; gap: 15px;
        }
        .date-form { display: flex; gap: 10px; align-items: center; }
        .date-form input { padding: 10px; border: 2px solid #e0e0e0; border-radius: 5px; font-size: 14px; }
        .btn-group { display: flex; gap: 10px; flex-wrap: wrap; }
        .btn {
            padding: 10px 20px; border: none; border-radius: 5px;
            font-size: 14px; font-weight: 600; cursor: pointer;
            text-decoration: none; display: inline-block; transition: transform 0.2s;
        }
        .btn:hover { transform: translateY(-2px); }
        .btn-primary { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; }
        .btn-success { background: #28a745; color: white; }
        .btn-info { background: #17a2b8; color: white; }
        .btn-secondary { background: #6c757d; color: white; }
        table { width: 100%; border-collapse: collapse; margin-top: 10px; }
        th, td { padding: 10px 12px; text-align: left; border-bottom: 1px solid #e0e0e0; }
        th { background: #667eea; color: white; font-weight: 600; }
        tr:hover { background: #f5f5f5; }
        .badge { padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: 600; }
        .badge-diesel { background: #333; color: white; }
        .badge-essence { background: #28a745; color: white; }
        .badge-hybride { background: #17a2b8; color: white; }
        .badge-electrique { background: #007bff; color: white; }
        .time-display { font-weight: 600; color: #667eea; }
        .back-link {
            display: block; text-align: center; margin-top: 20px;
            color: #667eea; text-decoration: none; font-weight: 500;
        }
        .back-link:hover { text-decoration: underline; }
        .empty-state { text-align: center; color: #999; padding: 30px; font-style: italic; }
        .info-box {
            background: #f0f4ff; padding: 15px; border-radius: 5px;
            margin-bottom: 20px; border-left: 4px solid #667eea;
        }

        /* -- Carte véhicule -- */
        .vehicule-card { border: 2px solid #e0e0e0; border-radius: 8px; margin-bottom: 20px; overflow: hidden; }
        .vehicule-card-header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white; padding: 15px 20px;
            display: flex; justify-content: space-between; align-items: center;
            flex-wrap: wrap; gap: 10px;
        }
        .vehicule-card-header h3 { color: white; margin: 0; font-size: 18px; }
        .vehicule-meta { display: flex; gap: 15px; font-size: 13px; }
        .vehicule-meta span { background: rgba(255,255,255,0.2); padding: 4px 10px; border-radius: 4px; }
        .vehicule-card-body { padding: 15px 20px; }
        .passagers-section { margin-bottom: 15px; }
        .passagers-section h4 { color: #555; font-size: 14px; margin-bottom: 8px; }

        /* -- Itinéraire timeline -- */
        .itineraire { position: relative; padding-left: 30px; margin: 15px 0; }
        .itineraire::before {
            content: ''; position: absolute; left: 10px; top: 0; bottom: 0;
            width: 3px; background: #667eea;
        }
        .etape {
            position: relative; margin-bottom: 15px; padding: 10px 15px;
            background: #f8f9ff; border-radius: 6px; border-left: 3px solid #667eea;
        }
        .etape::before {
            content: ''; position: absolute; left: -26px; top: 14px;
            width: 12px; height: 12px; border-radius: 50%;
            background: #667eea; border: 2px solid white;
        }
        .etape-retour { border-left-color: #28a745; }
        .etape-retour::before { background: #28a745; }
        .etape-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 5px; }
        .etape-trajet { font-weight: 600; color: #333; }
        .etape-heure { color: #667eea; font-weight: 600; }
        .etape-details { font-size: 13px; color: #666; }
        .etape-passagers { font-size: 13px; color: #333; margin-top: 4px; }

        /* -- Résumé horaires -- */
        .horaires-resume {
            display: flex; gap: 20px; padding: 12px 15px;
            background: #f0f4ff; border-radius: 6px; margin-top: 10px; flex-wrap: wrap;
        }
        .horaire-item { display: flex; flex-direction: column; align-items: center; }
        .horaire-label { font-size: 11px; color: #666; text-transform: uppercase; }
        .horaire-value { font-size: 16px; font-weight: 700; color: #667eea; }
        .horaire-value.retour { color: #28a745; }
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
            <form action="/planification" method="GET" class="date-form">
                <label for="date">Date :</label>
                <input type="date" id="date" name="date" 
                       value="<%= request.getAttribute("dateSelectionnee") != null ? request.getAttribute("dateSelectionnee") : "" %>" required>
                <button type="submit" class="btn btn-primary">Afficher</button>
            </form>
            <% if (request.getAttribute("dateSelectionnee") != null) { %>
            <div class="btn-group">
                <form action="/planification/assigner" method="POST" style="display:inline;">
                    <input type="hidden" name="date" value="<%= request.getAttribute("dateSelectionnee") %>">
                    <button type="submit" class="btn btn-success">Assigner automatiquement</button>
                </form>
                <form action="/planification/regrouper-assigner" method="POST" style="display:inline;">
                    <input type="hidden" name="date" value="<%= request.getAttribute("dateSelectionnee") %>">
                    <button type="submit" class="btn btn-info">Regrouper et assigner</button>
                </form>
            </div>
            <% } %>
        </div>

        <div class="info-box">
            <strong>Date sélectionnée :</strong> <%= request.getAttribute("dateSelectionnee") != null ? request.getAttribute("dateSelectionnee") : "Aucune" %>
        </div>

        <!-- ===================== SECTION: Itinéraires par véhicule ===================== -->
        <h2>Itinéraires par véhicule</h2>
        <%
            SimpleDateFormat dtFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            List<GroupeVehicule> groupesVehicules = (List<GroupeVehicule>) request.getAttribute("groupesVehicules");

            if (groupesVehicules != null && !groupesVehicules.isEmpty()) {
                for (GroupeVehicule groupe : groupesVehicules) {
                    Vehicule veh = groupe.getVehicule();
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
        <div class="vehicule-card">
            <div class="vehicule-card-header">
                <h3><%= veh.getReference() %></h3>
                <div class="vehicule-meta">
                    <span><span class="badge <%= badgeCl %>"><%= typeLbl %></span></span>
                    <span><%= groupe.getTotalPassagers() %> / <%= veh.getNombrePlace() %> places</span>
                    <span><%= String.format("%.1f", groupe.getDistanceTotaleKm()) %> km</span>
                    <span><%= groupe.getDureeTotaleMinutes() %> min</span>
                </div>
            </div>
            <div class="vehicule-card-body">
                <!-- Passagers -->
                <div class="passagers-section">
                    <h4>Passagers embarqués :</h4>
                    <table>
                        <thead><tr><th>Client</th><th>Passagers</th><th>Hôtel destination</th></tr></thead>
                        <tbody>
                        <% for (Reservation res : groupe.getReservations()) { %>
                            <tr>
                                <td><%= res.getClient() %></td>
                                <td><%= res.getNombrePassager() %></td>
                                <td><%= res.getNomHotel() %></td>
                            </tr>
                        <% } %>
                        </tbody>
                    </table>
                </div>

                <!-- Itinéraire timeline -->
                <h4>Itinéraire :</h4>
                <div class="itineraire">
                <%
                    List<EtapeItineraire> etapes = groupe.getItineraire();
                    if (etapes != null) {
                        for (int i = 0; i < etapes.size(); i++) {
                            EtapeItineraire etape = etapes.get(i);
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

                <!-- Résumé horaires -->
                <div class="horaires-resume">
                    <div class="horaire-item">
                        <span class="horaire-label">Départ TNR</span>
                        <span class="horaire-value"><%= groupe.getHeureDepart() != null ? dtFormat.format(groupe.getHeureDepart()) : "-" %></span>
                    </div>
                    <div class="horaire-item">
                        <span class="horaire-label">Retour TNR</span>
                        <span class="horaire-value retour"><%= groupe.getHeureRetour() != null ? dtFormat.format(groupe.getHeureRetour()) : "-" %></span>
                    </div>
                    <div class="horaire-item">
                        <span class="horaire-label">Distance totale</span>
                        <span class="horaire-value"><%= String.format("%.1f", groupe.getDistanceTotaleKm()) %> km</span>
                    </div>
                    <div class="horaire-item">
                        <span class="horaire-label">Durée totale</span>
                        <span class="horaire-value"><%= groupe.getDureeTotaleMinutes() %> min</span>
                    </div>
                </div>
            </div>
        </div>
        <%  }
            } else {
        %>
        <div class="empty-state">Aucun itinéraire pour cette date</div>
        <% } %>

        <!-- ===================== SECTION: Réservations planifiées (individuel) ===================== -->
        <h2>Réservations planifiées</h2>
        <table>
            <thead>
                <tr>
                    <th>Client</th><th>Passagers</th><th>Hôtel</th><th>Distance</th>
                    <th>Véhicule</th><th>Type</th><th>Date/Heure départ</th>
                    <th>Date/Heure retour</th><th>Durée totale</th>
                </tr>
            </thead>
            <tbody>
            <%
                List<PlanificationReservation> planifications = (List<PlanificationReservation>) request.getAttribute("planifications");
                if (planifications != null && !planifications.isEmpty()) {
                    for (PlanificationReservation p : planifications) {
                        Reservation r = p.getReservation();
                        String tc = r.getTypeCarburant();
                        String bc = ""; String tl = "";
                        if (tc != null) {
                            switch (tc) {
                                case "D":  bc = "badge-diesel";     tl = "Diesel";     break;
                                case "ES": bc = "badge-essence";    tl = "Essence";    break;
                                case "H":  bc = "badge-hybride";    tl = "Hybride";    break;
                                case "EL": bc = "badge-electrique"; tl = "Électrique"; break;
                                default:   tl = tc;
                            }
                        }
            %>
                <tr>
                    <td><%= r.getClient() %></td>
                    <td><%= r.getNombrePassager() %></td>
                    <td><%= r.getNomHotel() %></td>
                    <td><%= String.format("%.1f", p.getDistanceKm()) %> km</td>
                    <td><%= r.getReferenceVehicule() %></td>
                    <td><span class="badge <%= bc %>"><%= tl %></span></td>
                    <td class="time-display"><%= dtFormat.format(p.getHeureDepart()) %></td>
                    <td class="time-display"><%= dtFormat.format(p.getHeureRetour()) %></td>
                    <td><%= p.getDureeTotaleMinutes() %> min</td>
                </tr>
            <%      }
                } else {
            %>
                <tr><td colspan="9" class="empty-state">Aucune réservation planifiée pour cette date</td></tr>
            <% } %>
            </tbody>
        </table>

        <!-- ===================== SECTION: Réservations non assignées ===================== -->
        <h2>Réservations non assignées</h2>
        <table>
            <thead>
                <tr><th>ID</th><th>Client</th><th>Passagers</th><th>Hôtel</th><th>Date/Heure d'arrivée</th></tr>
            </thead>
            <tbody>
            <%
                List<Reservation> reservationsSansVehicule = (List<Reservation>) request.getAttribute("reservationsSansVehicule");
                if (reservationsSansVehicule != null && !reservationsSansVehicule.isEmpty()) {
                    for (Reservation r : reservationsSansVehicule) {
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