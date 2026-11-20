package com.zonasegura.zona_segura_api;

import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ConexionController {
    private final JdbcTemplate jdbcTemplate;

    public ConexionController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/conexion")
    public Map<String, String> comprobarConexion() {
        String base = jdbcTemplate.queryForObject("SELECT DB_NAME()", String.class);
        return Map.of("estado", "conectado", "base", base);
    }
}
