package com.zonasegura.zona_segura_api;

import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.dao.EmptyResultDataAccessException;

@RestController
@RequestMapping("/api/alcaldia")
public class AlcaldiaController {
    private final JdbcTemplate jdbc;

    public AlcaldiaController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/perfil")
    public Map<String, Object> perfil(@AuthenticationPrincipal Jwt jwt) {
        int id = Integer.parseInt(jwt.getSubject());
        try {
            return jdbc.queryForObject(
                    "SELECT u.nombre, p.cargo, p.rol_acceso FROM dbo.Usuarios u " +
                            "JOIN dbo.PersonalAlcaldia p ON p.id_usuario = u.id_usuario " +
                            "WHERE u.id_usuario = ? AND u.activo = 1",
                    (rs, row) -> Map.of(
                            "nombre", rs.getString("nombre"),
                            "cargo", rs.getString("cargo"),
                            "rolAcceso", rs.getString("rol_acceso"),
                            "rol", "PERSONAL_ALCALDIA"),
                    id);
        } catch (EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Personal no encontrado.");
        }
    }
}
