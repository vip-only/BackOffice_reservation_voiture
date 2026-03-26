<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List" %>
<%@ page import="com.backoffice.model.Vehicule" %>
<!DOCTYPE html>
<html lang="fr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>BackOffice - Gestion des Vehicules</title>
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
            max-width: 1000px;
            margin: 0 auto;
        }
        h1 {
            color: #667eea;
            margin-bottom: 20px;
            text-align: center;
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
        .filter-form {
            display: flex;
            gap: 10px;
            flex-wrap: wrap;
            align-items: center;
        }
        .filter-form input,
        .filter-form select {
            padding: 10px;
            border: 2px solid #e0e0e0;
            border-radius: 5px;
            font-size: 14px;
        }
        .filter-form input:focus,
        .filter-form select:focus {
            outline: none;
            border-color: #667eea;
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
        .btn-warning {
            background: #ffc107;
            color: #333;
        }
        .btn-danger {
            background: #dc3545;
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
        }
        tr:hover {
            background: #f5f5f5;
        }
        .actions-cell {
            display: flex;
            gap: 5px;
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
    </style>
</head>
<body>
    <div class="container">
        <h1>Gestion des Vehicules</h1>

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
            <form action="/vehicule" method="GET" class="filter-form">
                <input type="text" name="keyword" placeholder="Rechercher reference..." 
                       value="<%= request.getAttribute("keywordFiltre") != null ? request.getAttribute("keywordFiltre") : "" %>">
                <select name="typeCarburant">
                    <option value="">-- Type carburant --</option>
                    <option value="D" <%= "D".equals(request.getAttribute("typeCarburantFiltre")) ? "selected" : "" %>>Diesel</option>
                    <option value="ES" <%= "ES".equals(request.getAttribute("typeCarburantFiltre")) ? "selected" : "" %>>Essence</option>
                    <option value="H" <%= "H".equals(request.getAttribute("typeCarburantFiltre")) ? "selected" : "" %>>Hybride</option>
                    <option value="EL" <%= "EL".equals(request.getAttribute("typeCarburantFiltre")) ? "selected" : "" %>>Electrique</option>
                </select>
                <button type="submit" class="btn btn-primary">Filtrer</button>
                <a href="/vehicule" class="btn btn-secondary">Reset</a>
            </form>
            <a href="/vehicule/add" class="btn btn-success">+ Ajouter Vehicule</a>
        </div>

        <table>
            <thead>
                <tr>
                    <th>ID</th>
                    <th>Reference</th>
                    <th>Nombre de places</th>
                    <th>Type carburant</th>
                    <th>Heure dispo</th>
                    <th>Actions</th>
                </tr>
            </thead>
            <tbody>
                <%
                    List<Vehicule> vehicules = (List<Vehicule>) request.getAttribute("vehicules");
                    if (vehicules != null && !vehicules.isEmpty()) {
                        for (Vehicule v : vehicules) {
                            String badgeClass = "";
                            switch (v.getTypeCarburant()) {
                                case "D": badgeClass = "badge-diesel"; break;
                                case "ES": badgeClass = "badge-essence"; break;
                                case "H": badgeClass = "badge-hybride"; break;
                                case "EL": badgeClass = "badge-electrique"; break;
                            }
                %>
                <tr>
                    <td><%= v.getId() %></td>
                    <td><%= v.getReference() %></td>
                    <td><%= v.getNombrePlace() %></td>
                    <td><span class="badge <%= badgeClass %>"><%= v.getTypeCarburantLibelle() %></span></td>
                    <td><%= v.getHeureDisponibilite() != null ? v.getHeureDisponibilite().toString().substring(0, 5) : "00:00" %></td>
                    <td class="actions-cell">
                        <a href="/vehicule/edit?id=<%= v.getId() %>" class="btn btn-warning">Modifier</a>
                        <form action="/vehicule/delete" method="POST" style="display:inline;" onsubmit="return confirm('Supprimer ce vehicule ?');">
                            <input type="hidden" name="id" value="<%= v.getId() %>">
                            <button type="submit" class="btn btn-danger">Supprimer</button>
                        </form>
                    </td>
                </tr>
                <%
                        }
                    } else {
                %>
                <tr>
                    <td colspan="6" style="text-align: center; color: #999;">Aucun vehicule trouve</td>
                </tr>
                <%
                    }
                %>
            </tbody>
        </table>

        <a href="/home" class="back-link">Retour a l'accueil</a>
    </div>
</body>
</html>
