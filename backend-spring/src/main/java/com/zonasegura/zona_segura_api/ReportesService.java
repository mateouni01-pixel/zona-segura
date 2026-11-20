package com.zonasegura.zona_segura_api;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportesService {
    private final JdbcTemplate jdbc;
    private final AuthService auth;
    private static final String REPORT_QUERY = "SELECT r.id_reporte, r.descripcion, r.fecha_hora, r.estado, "
            + "r.id_ciudadano, r.id_tipo, t.nombre AS tipo_nombre, u.latitud, u.longitud "
            + "FROM dbo.Reportes r JOIN dbo.TiposIncidente t ON t.id_tipo=r.id_tipo "
            + "JOIN dbo.UbicacionesGPS u ON u.id_ubicacion=r.id_ubicacion ";

    public ReportesService(JdbcTemplate jdbc, AuthService auth) {
        this.jdbc = jdbc;
        this.auth = auth;
    }

    private int citizen(String subject) {
        var profile = auth.profile(subject);
        if (!profile.roles().contains("CIUDADANO") || profile.ciudadanoId() <= 0)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere una cuenta ciudadana.");
        return profile.ciudadanoId();
    }

    public List<Tipo> types(String subject) {
        auth.profile(subject);
        return jdbc.query("SELECT id_tipo, nombre, activo FROM dbo.TiposIncidente ORDER BY id_tipo",
                (rs, row) -> new Tipo(rs.getInt("id_tipo"), rs.getString("nombre"), rs.getBoolean("activo")));
    }

    public Tipo type(String subject, int id) {
        return types(subject).stream().filter(type -> type.id() == id).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public List<Reporte> list(String subject) {
        int author = citizen(subject);
        return jdbc.query(REPORT_QUERY + "WHERE r.id_ciudadano=? ORDER BY r.id_reporte DESC",
                (rs, row) -> mapReport(rs), author);
    }

    public Reporte get(String subject, int id) {
        int author = citizen(subject);
        return find(id, author);
    }

    private Reporte find(int id, int author) {
        var reports = jdbc.query(REPORT_QUERY + "WHERE r.id_reporte=? AND r.id_ciudadano=?",
                (rs, row) -> mapReport(rs), id, author);
        if (reports.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return reports.get(0);
    }

    @Transactional
    public Reporte create(String subject, Entrada input) {
        int author = citizen(subject);
        String description = input.descripcion() == null ? "" : input.descripcion().strip();
        if (description.length() < 10 || description.length() > 2000
                || input.tipoIncidenteId() == null || input.tipoIncidenteId() <= 0
                || !coordinate(input.latitud(), 90) || !coordinate(input.longitud(), 180))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revisa descripción, tipo y coordenadas.");
        if (input.ciudadanoId() != null && input.ciudadanoId() != author)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El autor debe corresponder a la sesión.");
        Boolean active = jdbc.query("SELECT activo FROM dbo.TiposIncidente WHERE id_tipo=?",
                (rs, row) -> rs.getBoolean("activo"), input.tipoIncidenteId()).stream().findFirst().orElse(false);
        if (!active) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo inexistente o inactivo.");
        Integer location = jdbc.queryForObject("INSERT INTO dbo.UbicacionesGPS (latitud, longitud) "
                + "OUTPUT INSERTED.id_ubicacion VALUES (?, ?)", Integer.class, input.latitud(), input.longitud());
        Integer id = jdbc.queryForObject("INSERT INTO dbo.Reportes (id_ciudadano, id_tipo, id_ubicacion, descripcion) "
                + "OUTPUT INSERTED.id_reporte VALUES (?, ?, ?, ?)", Integer.class,
                author, input.tipoIncidenteId(), location, description);
        if (id == null) throw new IllegalStateException("No se recibió el identificador del reporte.");
        return find(id, author);
    }

    private boolean coordinate(BigDecimal value, int limit) {
        return value != null && value.abs().compareTo(BigDecimal.valueOf(limit)) <= 0;
    }

    private Reporte mapReport(ResultSet rs) throws SQLException {
        return new Reporte(rs.getInt("id_reporte"), rs.getString("descripcion"),
                rs.getTimestamp("fecha_hora").toLocalDateTime().atOffset(ZoneOffset.UTC),
                rs.getString("estado"), rs.getInt("id_ciudadano"), rs.getInt("id_tipo"),
                rs.getString("tipo_nombre"), rs.getBigDecimal("latitud"), rs.getBigDecimal("longitud"));
    }

    public record Entrada(String descripcion, Integer tipoIncidenteId, Integer ciudadanoId,
                          BigDecimal latitud, BigDecimal longitud) { }
    public record Tipo(int id, String nombre, boolean activo) { }
    public record Reporte(int id, String descripcion, OffsetDateTime fechaRegistroUtc, String estado,
                          int ciudadanoId, int tipoIncidenteId, String tipoIncidenteNombre,
                          BigDecimal latitud, BigDecimal longitud) { }
}
