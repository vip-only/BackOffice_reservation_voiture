package com.backoffice;

import com.backoffice.util.TokenGenerator;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

public class Main {
    public static void main(String[] args) throws Exception {
        // Générer le token avant de démarrer le serveur
        try {
            String token = TokenGenerator.generateToken(24); // 24 heures de validité
            System.out.println("========================================");
            System.out.println("Token API généré : " + token);
            System.out.println("Expiration : 24 heures");
            System.out.println("========================================");
        } catch (Exception e) {
            System.err.println("Erreur lors de la génération du token : " + e.getMessage());
            e.printStackTrace();
        }
        
        Server server = new Server(8082);
        
        WebAppContext webapp = new WebAppContext();
        webapp.setContextPath("/");
        webapp.setResourceBase("src/main/webapp");
        webapp.setDescriptor("src/main/webapp/WEB-INF/web.xml");
        
        server.setHandler(webapp);
        server.start();
        
        System.out.println("Serveur démarré sur http://localhost:8082");
        server.join();
    }
}