package com.start.start_app.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @PostMapping("/create")
    public ResponseEntity<String> testAdminRoute() {
        return ResponseEntity.ok("Acesso Liberado! O filtro leu seu Token e confirmou que você é ADMIN.");
    }
}