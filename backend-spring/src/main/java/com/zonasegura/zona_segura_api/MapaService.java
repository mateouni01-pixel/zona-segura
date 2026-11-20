package com.zonasegura.zona_segura_api;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MapaService {
    private final JdbcTemplate jdbc;
    private final AuthService auth;
    public MapaService(JdbcTemplate jdbc, AuthService auth) { this.jdbc = jdbc; this.auth = auth; }

    public Resumen summary(String subject, Double sur, Double oeste, Double norte, Double este,
                           int dias, Integer tipoIncidenteId) {
        var profile = auth.profile(subject);
        if (profile.roles().stream().noneMatch(role -> List.of("CIUDADANO", "PERSONAL_ALCALDIA", "ADMINISTRADOR").contains(role)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        int supplied = (sur == null ? 0 : 1) + (oeste == null ? 0 : 1) + (norte == null ? 0 : 1) + (este == null ? 0 : 1);
        if (supplied != 0 && supplied != 4) throw invalidBounds();
        double south = sur == null ? -17.95 : sur, west = oeste == null ? -63.35 : oeste;
        double north = norte == null ? -17.55 : norte, east = este == null ? -62.95 : este;
        if (!Double.isFinite(south) || !Double.isFinite(north) || !Double.isFinite(west) || !Double.isFinite(east)
                || south < -85 || north > 85 || west < -180 || east > 180
                || south >= north || west >= east || north-south > 2 || east-west > 2
                || dias < 1 || dias > 90 || tipoIncidenteId != null && tipoIncidenteId <= 0)
            throw invalidBounds();

        // Consultar celdas completas mantiene estable su conteo al desplazar el mapa.
        double minLat = Math.floor(south*200)/200, maxLat = Math.ceil(north*200)/200;
        double minLon = Math.floor(west*200)/200, maxLon = Math.ceil(east*200)/200;
        Instant now = Instant.now(), since = now.minusSeconds(dias*86400L);
        String sql = "SELECT CAST(FLOOR(u.longitud*200) AS int) AS x, CAST(FLOOR(u.latitud*200) AS int) AS y, COUNT(*) AS cantidad "
                + "FROM dbo.Reportes r JOIN dbo.UbicacionesGPS u ON u.id_ubicacion=r.id_ubicacion "
                + "WHERE r.estado IN ('VALIDADO','EN_ATENCION','ATENDIDO') AND r.fecha_hora>=? AND r.fecha_hora<=? "
                + "AND u.latitud>=? AND u.latitud<? AND u.longitud>=? AND u.longitud<? ";
        List<Object> parameters = new ArrayList<>(List.of(
                Timestamp.valueOf(since.atOffset(ZoneOffset.UTC).toLocalDateTime()),
                Timestamp.valueOf(now.atOffset(ZoneOffset.UTC).toLocalDateTime()), minLat, maxLat, minLon, maxLon));
        if (tipoIncidenteId != null) { sql += "AND r.id_tipo=? "; parameters.add(tipoIncidenteId); }
        sql += "GROUP BY FLOOR(u.longitud*200), FLOOR(u.latitud*200) ORDER BY x, y";
        var zones = jdbc.query(sql, (rs,row) -> zone(rs.getInt("x"), rs.getInt("y"), rs.getInt("cantidad")), parameters.toArray());
        return new Resumen(zones, zones.stream().mapToInt(Zona::cantidad).sum(), dias, since, now,
                0.005, new Umbrales(3, 5));
    }

    private Zona zone(int x, int y, int count) {
        double west=x/200.0, south=y/200.0, east=(x+1)/200.0, north=(y+1)/200.0;
        return new Zona("zona-"+x+"-"+y, "Área de incidentes confirmados", count>=5 ? "ALTO" : count>=3 ? "MEDIO" : "BAJO", count,
                new double[][]{{west,south},{east,south},{east,north},{west,north},{west,south}});
    }
    private ResponseStatusException invalidBounds() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revisa los límites del área, el tipo y el período (1 a 90 días).");
    }
    public record Umbrales(int medio, int alto) { }
    public record Zona(String id, String nombre, String nivel, int cantidad, double[][] poligono) { }
    public record Resumen(List<Zona> zonas, int totalReportes, int dias, Instant desdeUtc, Instant actualizadoUtc,
                          double tamanoCeldaGrados, Umbrales umbrales) { }
}
