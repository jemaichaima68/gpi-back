package com.gpi.gpitracker.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class TestController {

    @GetMapping("/test")
    public Map<String, Object> test(@AuthenticationPrincipal Jwt jwt) {

        // Extraire realm_access.roles proprement
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");

        return Map.of(
                "message",   "Connexion réussie !",
                "user",      jwt.getClaim("preferred_username"),
                "email",     jwt.getClaim("email") != null ? jwt.getClaim("email") : "non renseigné",
                "roles",     realmAccess != null ? realmAccess.get("roles") : "aucun rôle",
                "subject",   jwt.getSubject(),   // keycloak ID de l'utilisateur
                "issued_at", jwt.getIssuedAt()
        );
    }
}