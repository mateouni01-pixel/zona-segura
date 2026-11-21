package com.zonasegura.zona_segura_api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class TerritorioController {
    private final TerritorioService territorio;
    private final AuthService auth;
    public TerritorioController(TerritorioService territorio,AuthService auth){this.territorio=territorio;this.auth=auth;}
    @GetMapping("/api/territorio/cobertura")
    public JsonNode cobertura(@AuthenticationPrincipal Jwt jwt){auth.profile(jwt.getSubject());return territorio.cobertura();}
}
