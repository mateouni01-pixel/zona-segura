package com.zonasegura.zona_segura_api;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final JwtEncoder jwtEncoder;

    public AuthService(JdbcTemplate jdbc, PasswordEncoder passwords, JwtEncoder jwtEncoder) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.jwtEncoder = jwtEncoder;
    }

    @Transactional
    public int register(String nombre, String correo, String password) {
        String normalized = correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
        if (nombre == null || nombre.isBlank() || nombre.trim().length() > 150
                || normalized.length() > 254
                || !normalized.matches("(?i)^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$")
                || password == null || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nombre, correo válido y contraseña de 8 caracteres a 72 bytes son obligatorios.");
        }
        try {
            Integer id = jdbc.queryForObject(
                    "INSERT INTO dbo.Usuarios (nombre, correo, password_hash) OUTPUT INSERTED.id_usuario VALUES (?, ?, ?)",
                    Integer.class, nombre.trim(), normalized, passwords.encode(password));
            Integer roleId = jdbc.queryForObject(
                    "SELECT id_rol FROM dbo.Roles WHERE nombre = N'CIUDADANO'", Integer.class);
            jdbc.update("INSERT INTO dbo.UsuarioRoles (id_usuario, id_rol) VALUES (?, ?)", id, roleId);
            jdbc.update("INSERT INTO dbo.Ciudadanos (id_usuario) VALUES (?)", id);
            return id;
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo ya está registrado.");
        }
    }

    public Map<String, Object> login(String correo, String password) {
        if (correo == null || password == null || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw unauthorized();
        }
        String normalized = correo.trim().toLowerCase(Locale.ROOT);
        User user;
        try {
            user = jdbc.queryForObject(
                    "SELECT id_usuario, nombre, password_hash, activo FROM dbo.Usuarios WHERE correo = ?",
                    (rs, row) -> new User(rs.getInt("id_usuario"), rs.getString("nombre"),
                            rs.getString("password_hash"), rs.getBoolean("activo")),
                    normalized);
        } catch (EmptyResultDataAccessException exception) {
            throw unauthorized();
        }
        if (user == null || !user.active() || !passwords.matches(password, user.passwordHash())) {
            throw unauthorized();
        }
        Profile profile = profile(String.valueOf(user.id()));
        List<String> roles = profile.roles();
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("zona-segura-api")
                .audience(List.of("zona-segura-android"))
                .subject(String.valueOf(user.id()))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("nombre", profile.nombre())
                .claim("roles", roles)
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return Map.of("token", token, "tipo", "Bearer", "expiraEnSegundos", 3600,
                "idUsuario", user.id(), "nombre", profile.nombre(), "roles", roles,
                "ciudadanoId", profile.ciudadanoId());
    }

    public Profile profile(String subject) {
        int id;
        try {
            id = Integer.parseInt(subject);
        } catch (NumberFormatException exception) {
            throw unauthorized();
        }
        Account account;
        try {
            account = jdbc.queryForObject(
                    "SELECT u.id_usuario, u.nombre, u.activo, c.id_usuario AS ciudadano_id "
                            + "FROM dbo.Usuarios u LEFT JOIN dbo.Ciudadanos c ON c.id_usuario = u.id_usuario "
                            + "WHERE u.id_usuario = ?",
                    (rs, row) -> new Account(rs.getInt("id_usuario"), rs.getString("nombre"),
                            rs.getBoolean("activo"), rs.getInt("ciudadano_id")), id);
        } catch (EmptyResultDataAccessException exception) {
            throw unauthorized();
        }
        if (account == null || !account.active()) throw unauthorized();
        List<String> roles = jdbc.query(
                "SELECT r.nombre FROM dbo.UsuarioRoles ur JOIN dbo.Roles r ON ur.id_rol = r.id_rol WHERE ur.id_usuario = ?",
                (rs, row) -> rs.getString("nombre"), id);
        if (roles.isEmpty() || (roles.contains("CIUDADANO") && account.citizenId() <= 0)) throw unauthorized();
        return new Profile(account.id(), account.name(), roles, account.citizenId());
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas.");
    }

    private record User(int id, String name, String passwordHash, boolean active) { }
    private record Account(int id, String name, boolean active, int citizenId) { }
    public record Profile(int idUsuario, String nombre, List<String> roles, int ciudadanoId) { }
}
