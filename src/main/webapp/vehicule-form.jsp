<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.backoffice.model.Vehicule" %>
<!DOCTYPE html>
<html lang="fr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>BackOffice - <%= "update".equals(request.getAttribute("action")) ? "Modifier" : "Ajouter" %> Vehicule</title>
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
            display: flex;
            justify-content: center;
            align-items: center;
        }
        .container {
            background: white;
            padding: 40px;
            border-radius: 10px;
            box-shadow: 0 10px 25px rgba(0,0,0,0.2);
            max-width: 500px;
            width: 90%;
        }
        h1 {
            color: #667eea;
            margin-bottom: 10px;
            text-align: center;
        }
        .subtitle {
            text-align: center;
            color: #999;
            margin-bottom: 25px;
            font-size: 14px;
        }
        .alert-error {
            background: #f8d7da;
            color: #721c24;
            padding: 12px 15px;
            border-radius: 5px;
            margin-bottom: 20px;
            border-left: 4px solid #dc3545;
        }
        .vehicule-form {
            display: flex;
            flex-direction: column;
            gap: 18px;
        }
        .form-group {
            display: flex;
            flex-direction: column;
        }
        label {
            margin-bottom: 5px;
            color: #555;
            font-weight: 500;
        }
        input[type="text"],
        input[type="number"],
        select {
            padding: 12px;
            border: 2px solid #e0e0e0;
            border-radius: 5px;
            font-size: 14px;
            transition: border-color 0.3s;
        }
        input:focus,
        select:focus {
            outline: none;
            border-color: #667eea;
        }
        button {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 14px;
            border: none;
            border-radius: 5px;
            font-size: 16px;
            font-weight: 600;
            cursor: pointer;
            transition: transform 0.2s;
            margin-top: 5px;
        }
        button:hover {
            transform: translateY(-2px);
            box-shadow: 0 5px 15px rgba(102, 126, 234, 0.4);
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
    </style>
</head>
<body>
    <div class="container">
        <%
            String action = (String) request.getAttribute("action");
            Vehicule vehicule = (Vehicule) request.getAttribute("vehicule");
            boolean isUpdate = "update".equals(action);
        %>

        <h1><%= isUpdate ? "Modifier" : "Ajouter" %> un Vehicule</h1>
        <p class="subtitle"><%= isUpdate ? "Modification des informations du vehicule" : "Formulaire d'ajout d'un nouveau vehicule" %></p>

        <% if (request.getAttribute("error") != null) { %>
            <div class="alert-error">
                <%= request.getAttribute("error") %>
            </div>
        <% } %>

        <form action="/vehicule/<%= isUpdate ? "update" : "insert" %>" method="POST" class="vehicule-form">
            <% if (isUpdate && vehicule != null) { %>
                <input type="hidden" name="id" value="<%= vehicule.getId() %>">
            <% } %>

            <div class="form-group">
                <label for="reference">Reference</label>
                <input type="text" id="reference" name="reference" placeholder="Ex: VH-001" 
                       value="<%= vehicule != null ? vehicule.getReference() : "" %>" required>
            </div>

            <div class="form-group">
                <label for="nombre_place">Nombre de places</label>
                <input type="number" id="nombre_place" name="nombre_place" min="1" placeholder="Ex: 5" 
                       value="<%= vehicule != null ? vehicule.getNombrePlace() : "" %>" required>
            </div>

            <div class="form-group">
                <label for="type_carburant">Type de carburant</label>
                <select id="type_carburant" name="type_carburant" required>
                    <option value="">-- Selectionnez --</option>
                    <option value="D" <%= vehicule != null && "D".equals(vehicule.getTypeCarburant()) ? "selected" : "" %>>Diesel</option>
                    <option value="ES" <%= vehicule != null && "ES".equals(vehicule.getTypeCarburant()) ? "selected" : "" %>>Essence</option>
                    <option value="H" <%= vehicule != null && "H".equals(vehicule.getTypeCarburant()) ? "selected" : "" %>>Hybride</option>
                    <option value="EL" <%= vehicule != null && "EL".equals(vehicule.getTypeCarburant()) ? "selected" : "" %>>Electrique</option>
                </select>
            </div>

            <button type="submit"><%= isUpdate ? "Mettre a jour" : "Ajouter" %></button>
        </form>

        <a href="/vehicule" class="back-link">Retour a la liste</a>
    </div>
</body>
</html>
