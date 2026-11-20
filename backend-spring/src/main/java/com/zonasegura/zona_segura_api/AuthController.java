package com.zonasegura.zona_segura_api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Integer> register(@RequestBody RegisterRequest request) {
        return Map.of("idUsuario", auth.register(request.nombre(), request.correo(), request.password()));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest request) {
        return auth.login(request.correo(), request.password());
    }

    @GetMapping("/me")
    public AuthService.Profile me(@AuthenticationPrincipal Jwt jwt) {
        return auth.profile(jwt.getSubject());
    }

    public record RegisterRequest(String nombre, String correo, String password) { }
    public record LoginRequest(String correo, String password) { }
}
