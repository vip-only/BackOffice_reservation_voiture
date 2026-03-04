<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List" %>
<%@ page import="com.backoffice.model.PlanificationReservation" %>
<%@ page import="com.backoffice.model.Reservation" %>
<%@ page import="java.text.SimpleDateFormat" %>
<!DOCTYPE html>
<html lang="fr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>BackOffice - Planification</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            padding: 20px;
        }
        .container {
            background: white;
            padding: 30px;
            border-radius: 10px;
            box-shadow: 0 10px 25px rgba(0,0,0,0.2);
            max-width: 1200px;
            margin: 0 auto;
        }
        h1 {
            color: #667eea;
            margin-bottom: 10px;
            text-align: center;
        }
        h2 {
            color: #667eea;
            margin-top: 30px;
            margin-bottom: 15px;
            font-size: 20px;
            border-bottom: 2px solid #667eea;
            padding-bottom: 8px;
        }
        .alert-success {
            background: #d4edda;
            color: #155724;
            padding: 12px 15px;
            border-radius: 5px;
            margin-bottom: 20px;
            border-left: 4px solid #28a745;
        }
        .alert-error {
            background: #f8d7da;
            color: #721c24;
            padding: 12px 15px;
            border-radius: 5px;
            margin-bottom: 20px;
            border-left: 4px solid #dc3545;
        }
        .actions-bar {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 20px;
            flex-wrap: wrap;
            gap: 15px;
        }
        .date-form {
            display: flex;
            gap: 10px;
            align-items: center;
        }
        .date-form input {
            padding: 10px;
            border: 2px solid #e0e0e0;
            border-radius: 5px;
            font-size: 14px;
        }
        .btn {
            padding: 10px 20px;
            border: none;
            border-radius: 5px;
            font-size: 14px;
            font-weight: 600;
            cursor: pointer;
            text-decoration: none;
            display: inline-block;
            transition: transform 0.2s;
        }
        .btn:hover {
            transform: translateY(-2px);
        }
        .btn-primary {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
        }
        .btn-success {
            background: #28a745;
            color: white;
        }
        .btn-secondary {
            background: #6c757d;
            color: white;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            margin-top: 15px;
        }
        th, td {
            padding: 12px 15px;
            text-align: left;
            border-bottom: 1px solid #e0e0e0;
        }
        th {
            background: #667eea;
            color: white;
            font-weight: 600;
        }
        tr:hover {
            background: #f5f5f5;
        }
        .badge {
            padding: 4px 8px;
            border-radius: 4px;
            font-size: 12px;
            font-weight: 600;
        }
        .badge-diesel { background: #333; color: white; }
        .badge-essence { background: #28a745; color: white; }
        .badge-hybride { background: #17a2b8; color: white; }
        .badge-electrique { background: #007bff; color: white; }
        .time-display {
            font-weight: 600;
            color: #667eea;
        }
        .back-link {
            display: block;
            text-align: center;
            margin-top: 20px;
            color: #667eea;
            text-decoration: none;
            font-weight: 500;
        }
        .back-link:hover {
            text-decoration: underline;
        }
        .empty-state {
            text-align: center;
            color: #999;
            padding: 30px;
            font-style: italic;
        }
        .info-box {
            background: #f0f4ff;
            padding: 15px;
            border-radius: 5px;
            margin-bottom: 20px;
            border-left: 4px solid #667eea;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>Planification des Réservations</h1>

        <% if (request.getAttribute("success") != null) { %>
            <div class="alert-success">
                <%= request.getAttribute("success") %>
            </div>
        <% } %>

        <% if (request.getAttribute("error") != null) { %>
            <div class="alert-error">
                <%= request.getAttribute("error") %>
            </div>
        <% } %>

        <div class="actions-bar">
            <form action="/planification" method="GET" class="date-form">
                <label for="date">Date :</label>
                <input type="date" id="date" name="date" 
                       value="<%= request.getAttribute("dateSelectionnee") != null ? request.getAttribute("dateSelectionnee") : "" %>" 
                       required>
                <button type="submit" class="btn btn-primary">Afficher</button>
            </form>
            
            <%-- <% if (request.getAttribute("dateSelectionnee") != null) { %>
            <form action="/planification/assigner" method="POST" style="display:inline;">
                <input type="hidden" name="date" value="<%= request.getAttribute("dateSelectionnee") %>">
                <button type="submit" class="btn btn-success">Assigner automatiquement</button>
            </form>
            <% } %> --%>
        </div>

        <div class="info-box">
            <strong>Date sélectionnée :</strong> <%= request.getAttribute("dateSelectionnee") != null ? request.getAttribute("dateSelectionnee") : "Aucune" %>
        </div>

        <!-- SECTION: Réservations avec véhicule assigné -->
        <h2>🚗 Réservations planifiées</h2>
        <table>
            <thead>
                <tr>
                    <th>Client</th>
                    <th>Passagers</th>
                    <th>Hôtel</th>
                    <th>Distance</th>
                    <th>Véhicule</th>
                    <th>Type</th>
                    <th>Départ aéroport</th>
                    <th>Retour aéroport</th>
                    <th>Durée totale</th>
                </tr>
            </thead>
            <tbody>
                <%
                    SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
                    List<PlanificationReservation> planifications = (List<PlanificationReservation>) request.getAttribute("planifications");
                    
                    if (planifications != null && !planifications.isEmpty()) {
                        for (PlanificationReservation p : planifications) {
                            Reservation r = p.getReservation();
                            String typeCarburant = r.getTypeCarburant();
                            String badgeClass = "";
                            String typeLabel = "";
                            if (typeCarburant != null) {
                                switch (typeCarburant) {
                                    case "D": badgeClass = "badge-diesel"; typeLabel = "Diesel"; break;
                                    case "ES": badgeClass = "badge-essence"; typeLabel = "Essence"; break;
                                    case "H": badgeClass = "badge-hybride"; typeLabel = "Hybride"; break;
                                    case "EL": badgeClass = "badge-electrique"; typeLabel = "Électrique"; break;
                                    default: badgeClass = ""; typeLabel = typeCarburant;
                                }
                            }
                %>
                <tr>
                    <td><%= r.getClient() %></td>
                    <td><%= r.getNombrePassager() %></td>
                    <td><%= r.getNomHotel() %></td>
                    <td><%= String.format("%.1f", p.getDistanceKm()) %> km</td>
                    <td><%= r.getReferenceVehicule() %></td>
                    <td><span class="badge <%= badgeClass %>"><%= typeLabel %></span></td>
                    <td class="time-display"><%= timeFormat.format(p.getHeureDepart()) %></td>
                    <td class="time-display"><%= timeFormat.format(p.getHeureRetour()) %></td>
                    <td><%= p.getDureeTotaleMinutes() %> min</td>
                </tr>
                <%
                        }
                    } else {
                %>
                <tr>
                    <td colspan="9" class="empty-state">Aucune réservation planifiée pour cette date</td>
                </tr>
                <%
                    }
                %>
            </tbody>
        </table>

        <!-- SECTION: Réservations sans véhicule -->
        <h2>⚠️ Réservations non assignées</h2>
        <table>
            <thead>
                <tr>
                    <th>ID</th>
                    <th>Client</th>
                    <th>Passagers</th>
                    <th>Hôtel</th>
                    <th>Heure d'arrivée</th>
                </tr>
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
                    <td class="time-display"><%= timeFormat.format(r.getDateHeureArrivee()) %></td>
                </tr>
                <%
                        }
                    } else {
                %>
                <tr>
                    <td colspan="5" class="empty-state">✓ Toutes les réservations ont un véhicule assigné</td>
                </tr>
                <%
                    }
                %>
            </tbody>
        </table>

        <a href="/home" class="back-link">Retour à l'accueil</a>
    </div>
</body>
</html>