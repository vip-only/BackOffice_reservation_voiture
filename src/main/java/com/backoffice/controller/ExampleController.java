package com.backoffice.controller;

import com.itu.demo.annotations.Controller;
import com.itu.demo.annotations.GetMapping;
import com.itu.demo.annotations.PostMapping;
import com.itu.demo.annotations.RequestParam;
import com.itu.demo.tools.ModelView;

@Controller
public class ExampleController {

    @GetMapping("/home")
    public ModelView home() {
        ModelView mv = new ModelView("home.jsp");
        mv.addData("message", "Bienvenue sur BackOffice");
        return mv;
    }

    @PostMapping("/login")
    public ModelView login(@RequestParam("username") String username, 
                           @RequestParam("password") String password) {
        ModelView mv = new ModelView("dashboard.jsp");
        // Logique d'authentification
        mv.addData("user", username);
        return mv;
    }
}