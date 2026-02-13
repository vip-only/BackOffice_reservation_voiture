package com.backoffice.listener;

import com.backoffice.util.TokenGenerator;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@WebListener
public class AppStartupListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("[AppStartupListener] Démarrage de l'application...");
        
        try {
            String token = TokenGenerator.generateToken(24);
            System.out.println("========================================");
            System.out.println("Token API généré : " + token);
            System.out.println("Expiration : 24 heures");
            System.out.println("========================================");
            
            sce.getServletContext().setAttribute("apiToken", token);
            
        } catch (Exception e) {
            System.err.println("[AppStartupListener] Erreur : " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("[AppStartupListener] Arrêt de l'application...");
    }
}