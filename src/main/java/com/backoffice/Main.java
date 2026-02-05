package com.backoffice;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

public class Main {
    public static void main(String[] args) throws Exception {
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