package com.zonasegura.zona_segura_api;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdministracionService {
    private static final Logger LOG = LoggerFactory.getLogger(AdministracionService.class);
    private static final Set<String> ROLES = Set.of("CIUDADANO", "PERSONAL_ALCALDIA", "ADMINISTRADOR", "UNIDAD_OPERATIVA");
    private static final String USERS = "SELECT u.id_usuario,u.nombre,u.correo,u.activo,"
            + "CASE WHEN EXISTS(SELECT 1 FROM dbo.SancionesCuenta s WHERE s.id_usuario=u.id_usuario AND s.levantada_utc IS NULL) THEN 1 ELSE 0 END AS sancionado FROM dbo.Usuarios u ";
    private static final String REPORTS = "SELECT r.id_reporte,t.nombre,r.descripcion,r.estado,r.indice_veracidad,r.id_ciudadano,"
            + "(SELECT COUNT(*) FROM dbo.ValidacionesComunitarias v WHERE v.id_reporte=r.id_reporte AND v.tipo_voto=0) AS negativos,"
            + "CASE WHEN EXISTS(SELECT 1 FROM dbo.SancionesCuenta s WHERE s.id_usuario=r.id_ciudadano AND s.levantada_utc IS NULL) THEN 1 ELSE 0 END AS sancionado,"
            + "CASE WHEN EXISTS(SELECT 1 FROM dbo.VerificacionesInstitucionales i WHERE i.id_reporte=r.id_reporte) THEN 1 ELSE 0 END AS institucional,"
            + "CASE WHEN EXISTS(SELECT 1 FROM dbo.SancionesCuenta s WHERE s.id_reporte=r.id_reporte) THEN 1 ELSE 0 END AS sancion_previa "
            + "FROM dbo.Reportes r JOIN dbo.TiposIncidente t ON t.id_tipo=r.id_tipo ";
    private final JdbcTemplate db;
    private final AuthService auth;
    private final PasswordEncoder passwords;

    public AdministracionService(JdbcTemplate db, AuthService auth, PasswordEncoder passwords) {
        this.db = db; this.auth = auth; this.passwords = passwords;
    }

    public AuthService.Profile profile(String subject) {
        var actor = auth.profile(subject);
        if (!actor.roles().contains("ADMINISTRADOR")) throw error(HttpStatus.FORBIDDEN, "Se requiere un administrador activo.");
        return actor;
    }

    public List<Usuario> users(String subject, String buscar) {
        profile(subject);
        String search = buscar == null ? "" : buscar.strip();
        if (search.length() > 100) throw error(HttpStatus.BAD_REQUEST, "La búsqueda admite hasta 100 caracteres.");
        // Directorio limitado a 200 coincidencias. La consulta individual no depende de este límite.
        String sql = USERS.replace("SELECT u.id_usuario", "SELECT TOP (200) u.id_usuario");
        List<UsuarioBase> rows;
        if (search.isEmpty()) rows = db.query(sql + "ORDER BY u.id_usuario DESC", this::userBase);
        else rows = db.query(sql + "WHERE u.nombre LIKE ? ESCAPE '~' OR u.correo LIKE ? ESCAPE '~' OR CONVERT(NVARCHAR(20),u.id_usuario)=? ORDER BY u.id_usuario DESC",
                this::userBase, "%" + escapeLike(search) + "%", "%" + escapeLike(search) + "%", search);
        List<Usuario> result = new ArrayList<>();
        for (UsuarioBase row : rows) result.add(withRoles(row));
        return result;
    }

    public Usuario user(String subject, int id) { profile(subject); return findUser(id); }

    @Transactional
    public Usuario create(String subject, NuevoUsuario input) {
        int actor = mutation(subject);
        if (input == null) throw error(HttpStatus.BAD_REQUEST, "Indica los datos de la cuenta.");
        String reason = reason(input.motivo());
        String email = AuthService.validateRegistration(input.nombre(), input.correo(), input.password());
        LinkedHashSet<String> roles = normalizedRoles(input.roles());
        Integer id;
        try {
            id = db.queryForObject("INSERT INTO dbo.Usuarios(nombre,correo,password_hash,activo) OUTPUT INSERTED.id_usuario VALUES(?,?,?,1)",
                    Integer.class, input.nombre().trim(), email, passwords.encode(input.password()));
        } catch (DuplicateKeyException exception) {
            throw error(HttpStatus.CONFLICT, "El correo ya está registrado.");
        }
        if (id == null) throw error(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo crear la cuenta.");
        assignRoles(id, roles);
        appendAudit("USUARIO_CREADO", id, id, actor, reason, "Cuenta activa; roles " + roles);
        return findUser(id);
    }

    @Transactional
    public Usuario update(String subject, int id, CambioUsuario input) {
        int actor = mutation(subject);
        String reason = reason(input == null ? null : input.motivo());
        if (input == null || input.activo() == null) throw error(HttpStatus.BAD_REQUEST, "Indica el estado de acceso.");
        LinkedHashSet<String> desired = normalizedRoles(input.roles());
        lockUser(id);
        Usuario before = findUser(id);
        if (actor == id && (!input.activo() || !desired.contains("ADMINISTRADOR")))
            throw error(HttpStatus.CONFLICT, "No puedes desactivar tu cuenta ni retirar tu propio permiso de administrador.");
        if (before.sancionado() && input.activo()) throw error(HttpStatus.CONFLICT, "Primero levanta la sanción mediante la acción correspondiente.");
        if (before.activo() && before.roles().contains("ADMINISTRADOR") && (!input.activo() || !desired.contains("ADMINISTRADOR"))) protectLastAdmin();
        assignRoles(id, desired);
        db.update("UPDATE dbo.Usuarios SET activo=? WHERE id_usuario=?", input.activo(), id);
        Usuario after = findUser(id);
        appendAudit("USUARIO_ACTUALIZADO", id, id, actor, reason,
                "Roles " + before.roles() + " → " + after.roles() + "; acceso " + before.activo() + " → " + after.activo());
        return after;
    }

    private LinkedHashSet<String> normalizedRoles(List<String> roles) {
        if (roles == null || roles.isEmpty() || roles.size() > ROLES.size())
            throw error(HttpStatus.BAD_REQUEST, "Selecciona entre uno y cuatro roles válidos.");
        LinkedHashSet<String> desired = new LinkedHashSet<>();
        for (String role : roles) {
            if (role == null || !ROLES.contains(role.strip().toUpperCase(Locale.ROOT)) || !desired.add(role.strip().toUpperCase(Locale.ROOT)))
                throw error(HttpStatus.BAD_REQUEST, "Los roles deben ser válidos y no repetirse.");
        }
        return desired;
    }

    @Transactional
    public void delete(String subject, int id, EliminarUsuario input) {
        int actor = mutation(subject);
        String reason = reason(input == null ? null : input.motivo());
        if (input.confirmacionId() == null || input.confirmacionId() != id)
            throw error(HttpStatus.BAD_REQUEST, "Escribe el ID de la cuenta para confirmar su eliminación.");
        if (actor == id) throw error(HttpStatus.CONFLICT, "No puedes eliminar tu propia cuenta administrativa.");
        lockUser(id);
        Usuario before = findUser(id);
        if (before.activo() && before.roles().contains("ADMINISTRADOR")) protectLastAdmin();
        for (String sql : List.of(
                "SELECT COUNT(*) FROM dbo.Reportes WHERE id_ciudadano=?",
                "SELECT COUNT(*) FROM dbo.ValidacionesComunitarias WHERE id_ciudadano=?",
                "SELECT COUNT(*) FROM dbo.HistorialReputacion WHERE id_ciudadano=?",
                "SELECT COUNT(*) FROM dbo.VerificacionesInstitucionales WHERE id_personal=?",
                "SELECT COUNT(*) FROM dbo.OrdenesIntervencion WHERE id_personal=? OR id_usuario_cierre=?",
                "SELECT COUNT(*) FROM dbo.AvisosOrden WHERE id_lector=?",
                "SELECT COUNT(*) FROM dbo.DecisionesModeracion WHERE id_responsable=?",
                "SELECT COUNT(*) FROM dbo.SancionesCuenta WHERE id_usuario=? OR id_responsable=? OR id_responsable_levantamiento=?",
                "SELECT COUNT(*) FROM dbo.AuditoriaAdministrativa WHERE id_responsable=?")) {
            Object[] parameters = new Object[(int)sql.chars().filter(ch -> ch == '?').count()];
            java.util.Arrays.fill(parameters, id);
            if (count(sql, parameters) > 0)
                throw error(HttpStatus.CONFLICT, "Esta cuenta tiene reportes, decisiones u operaciones vinculadas y no se puede borrar sin perder historial. Puedes dejarla inactiva.");
        }
        appendAudit("USUARIO_ELIMINADO", id, id, actor, reason, "Eliminación definitiva de la cuenta #" + id + "; roles previos " + before.roles());
        db.update("DELETE FROM dbo.MiembrosUnidad WHERE id_usuario=?", id);
        db.update("DELETE FROM dbo.UsuarioRoles WHERE id_usuario=?", id);
        db.update("DELETE FROM dbo.Ciudadanos WHERE id_usuario=?", id);
        db.update("DELETE FROM dbo.PersonalAlcaldia WHERE id_usuario=?", id);
        db.update("DELETE FROM dbo.Administradores WHERE id_usuario=?", id);
        if (db.update("DELETE FROM dbo.Usuarios WHERE id_usuario=?", id) != 1)
            throw error(HttpStatus.CONFLICT, "La cuenta cambió. Actualiza el directorio antes de reintentar.");
    }

    private void assignRoles(int id, Set<String> desired) {
        for (String role : desired) {
            if (role.equals("CIUDADANO")) db.update("IF NOT EXISTS(SELECT 1 FROM dbo.Ciudadanos WHERE id_usuario=?) INSERT dbo.Ciudadanos(id_usuario) VALUES(?)", id, id);
            if (role.equals("PERSONAL_ALCALDIA")) db.update("IF NOT EXISTS(SELECT 1 FROM dbo.PersonalAlcaldia WHERE id_usuario=?) INSERT dbo.PersonalAlcaldia(id_usuario,cargo,rol_acceso) VALUES(?,N'Personal municipal',N'GESTION')", id, id);
            if (role.equals("ADMINISTRADOR")) db.update("IF NOT EXISTS(SELECT 1 FROM dbo.Administradores WHERE id_usuario=?) INSERT dbo.Administradores(id_usuario,nivel_acceso) VALUES(?,N'GESTION')", id, id);
        }
        // Las especializaciones históricas se conservan; los permisos vigentes dependen de UsuarioRoles.
        db.update("DELETE FROM dbo.UsuarioRoles WHERE id_usuario=?", id);
        for (String role : desired) {
            int affected = db.update("INSERT dbo.UsuarioRoles(id_usuario,id_rol) SELECT ?,id_rol FROM dbo.Roles WHERE nombre=?", id, role);
            if (affected != 1) throw error(HttpStatus.CONFLICT, "Falta la definición de un rol. Instala las actualizaciones de la base.");
        }
    }

    public List<ReporteModeracion> reports(String subject) {
        profile(subject);
        return db.query(REPORTS + "WHERE r.estado IN ('PENDIENTE_VALIDACION','VALIDADO','RECHAZADO') ORDER BY negativos DESC,r.id_reporte DESC", this::report);
    }

    @Transactional
    public ReporteModeracion reject(String subject, int id, Motivo input) {
        int actor = mutation(subject);
        String reason = reason(input == null ? null : input.motivo());
        int author = author(id);
        lockUser(author);
        lockReport(id);
        ReporteModeracion before = findReport(id);
        if (before.autorId() != author) throw error(HttpStatus.CONFLICT, "El reporte cambió. Actualiza la bandeja.");
        if (author == actor) throw error(HttpStatus.FORBIDDEN, "No puedes moderar tu propio reporte.");
        if (!List.of("PENDIENTE_VALIDACION", "VALIDADO").contains(before.estado()))
            throw error(HttpStatus.CONFLICT, "El reporte ya no admite rechazo por moderación.");
        if (before.verificadoInstitucionalmente()) throw error(HttpStatus.CONFLICT, "No se sobrescribe una comprobación institucional.");
        if (count("SELECT COUNT(*) FROM dbo.DecisionesModeracion WHERE id_reporte=?", id) > 0)
            throw error(HttpStatus.CONFLICT, "El reporte ya tiene una decisión de moderación.");
        db.update("INSERT dbo.DecisionesModeracion(id_reporte,id_responsable,motivo,estado_anterior) VALUES(?,?,?,?)", id, actor, reason, before.estado());
        db.update("UPDATE dbo.Reportes SET estado='RECHAZADO' WHERE id_reporte=?", id);
        appendAudit("REPORTE_RECHAZADO", id, author, actor, reason, "Reporte #" + id + "; estado " + before.estado() + " → RECHAZADO; votos conservados");
        return findReport(id);
    }

    @Transactional
    public Usuario sanction(String subject, int id, Motivo input) {
        int actor = mutation(subject);
        String reason = reason(input == null ? null : input.motivo());
        int author = author(id);
        lockUser(author);
        lockReport(id);
        ReporteModeracion report = findReport(id);
        if (report.autorId() != author) throw error(HttpStatus.CONFLICT, "El reporte cambió. Actualiza la bandeja.");
        if (actor == author) throw error(HttpStatus.FORBIDDEN, "No puedes sancionar tu propia cuenta.");
        if (!report.estado().equals("RECHAZADO")) throw error(HttpStatus.CONFLICT, "Solo se sanciona por un reporte previamente rechazado tras revisión.");
        Usuario before = findUser(author);
        if (before.sancionado()) throw error(HttpStatus.CONFLICT, "La cuenta ya tiene una sanción vigente.");
        if (report.sancionPrevia()) throw error(HttpStatus.CONFLICT, "Este reporte ya fue utilizado para una sanción; no se sanciona dos veces el mismo hecho.");
        if (before.activo() && before.roles().contains("ADMINISTRADOR")) protectLastAdmin();
        db.update("INSERT dbo.SancionesCuenta(id_usuario,id_reporte,id_responsable,motivo,activo_anterior) VALUES(?,?,?,?,?)", author, id, actor, reason, before.activo());
        db.update("UPDATE dbo.Usuarios SET activo=0 WHERE id_usuario=?", author);
        appendAudit("CUENTA_SUSPENDIDA", author, author, actor, reason, "Reporte #" + id + "; acceso " + before.activo() + " → false");
        return findUser(author);
    }

    @Transactional
    public Usuario lift(String subject, int id, Motivo input) {
        int actor = mutation(subject);
        String reason = reason(input == null ? null : input.motivo());
        lockUser(id);
        if (actor == id) throw error(HttpStatus.FORBIDDEN, "No puedes levantar una sanción propia.");
        var sanctions = db.query("SELECT id_sancion,activo_anterior FROM dbo.SancionesCuenta WITH (UPDLOCK,HOLDLOCK) WHERE id_usuario=? AND levantada_utc IS NULL",
                (rs, row) -> new Sancion(rs.getInt(1), rs.getBoolean(2)), id);
        if (sanctions.isEmpty()) throw error(HttpStatus.CONFLICT, "La cuenta no tiene una sanción vigente.");
        Sancion sanction = sanctions.get(0);
        db.update("UPDATE dbo.SancionesCuenta SET levantada_utc=SYSUTCDATETIME(),id_responsable_levantamiento=?,motivo_levantamiento=? WHERE id_sancion=?", actor, reason, sanction.id());
        db.update("UPDATE dbo.Usuarios SET activo=? WHERE id_usuario=?", sanction.activoAnterior(), id);
        appendAudit("SANCION_LEVANTADA", id, id, actor, reason,
                "Sanción #" + sanction.id() + "; acceso restaurado al valor previo: " + sanction.activoAnterior());
        return findUser(id);
    }

    public List<Auditoria> audit(String subject, Integer usuarioId) {
        profile(subject);
        if (usuarioId != null && usuarioId <= 0) throw error(HttpStatus.BAD_REQUEST, "Usuario inválido.");
        String sql = "SELECT TOP (500) id_auditoria,accion,objetivo_id,motivo,id_responsable,fecha_utc,detalle FROM dbo.AuditoriaAdministrativa ";
        if (usuarioId != null) return db.query(sql + "WHERE id_usuario_objetivo=? ORDER BY id_auditoria DESC", this::auditRow, usuarioId);
        return db.query(sql + "ORDER BY id_auditoria DESC", this::auditRow);
    }

    private int mutation(String subject) {
        // JDBC usa transacciones implicitas: la consulta del perfil inicia la transaccion
        // real en SQL Server antes de pedir un applock con propietario Transaction.
        int actor = profile(subject).idUsuario();
        Integer result = db.queryForObject("DECLARE @result INT; EXEC @result=sys.sp_getapplock @Resource=N'zona-segura-admin-global',@LockMode=N'Exclusive',@LockOwner=N'Transaction',@LockTimeout=10000; SELECT @result", Integer.class);
        if (result != null && (result == -1 || result == -2 || result == -3))
            throw error(HttpStatus.CONFLICT, "Otra operación administrativa está en curso. Reintenta.");
        if (result == null || result < 0) {
            LOG.error("No se pudo adquirir el bloqueo administrativo de SQL Server. Resultado: {}", result);
            throw error(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo iniciar la operación administrativa. Revisa el registro de la API.");
        }
        lockUser(actor);
        // Los permisos pueden cambiar mientras se espera el bloqueo global.
        profile(subject);
        return actor;
    }

    private void protectLastAdmin() {
        if (count("SELECT COUNT(*) FROM dbo.Usuarios u JOIN dbo.UsuarioRoles ur ON ur.id_usuario=u.id_usuario JOIN dbo.Roles r ON r.id_rol=ur.id_rol WHERE u.activo=1 AND r.nombre='ADMINISTRADOR' AND NOT EXISTS(SELECT 1 FROM dbo.SancionesCuenta s WHERE s.id_usuario=u.id_usuario AND s.levantada_utc IS NULL)") <= 1)
            throw error(HttpStatus.CONFLICT, "Debe conservarse al menos un administrador activo.");
    }

    private void lockUser(int id) {
        if (id <= 0) throw error(HttpStatus.BAD_REQUEST, "Usuario inválido.");
        if (db.query("SELECT id_usuario FROM dbo.Usuarios WITH (UPDLOCK,HOLDLOCK) WHERE id_usuario=?", (rs, row) -> rs.getInt(1), id).isEmpty())
            throw error(HttpStatus.NOT_FOUND, "Usuario inexistente.");
    }

    private void lockReport(int id) {
        if (db.query("SELECT id_reporte FROM dbo.Reportes WITH (UPDLOCK,HOLDLOCK) WHERE id_reporte=?", (rs, row) -> rs.getInt(1), id).isEmpty())
            throw error(HttpStatus.NOT_FOUND, "Reporte inexistente.");
    }

    private int author(int id) {
        if (id <= 0) throw error(HttpStatus.BAD_REQUEST, "Reporte inválido.");
        var authors = db.query("SELECT id_ciudadano FROM dbo.Reportes WHERE id_reporte=?", (rs, row) -> rs.getInt(1), id);
        if (authors.isEmpty()) throw error(HttpStatus.NOT_FOUND, "Reporte inexistente.");
        return authors.get(0);
    }

    private Usuario findUser(int id) {
        if (id <= 0) throw error(HttpStatus.BAD_REQUEST, "Usuario inválido.");
        var rows = db.query(USERS + "WHERE u.id_usuario=?", this::userBase, id);
        if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND, "Usuario inexistente.");
        return withRoles(rows.get(0));
    }

    private UsuarioBase userBase(ResultSet rs, int row) throws SQLException { return new UsuarioBase(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getBoolean(5)); }

    private Usuario withRoles(UsuarioBase row) {
        List<String> roles = db.query("SELECT r.nombre FROM dbo.UsuarioRoles ur JOIN dbo.Roles r ON r.id_rol=ur.id_rol WHERE ur.id_usuario=? ORDER BY r.nombre", (rs, index) -> rs.getString(1), row.id());
        return new Usuario(row.id(), row.nombre(), row.correo(), row.activo(), roles, row.sancionado());
    }

    private ReporteModeracion report(ResultSet rs, int row) throws SQLException {
        return new ReporteModeracion(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getDouble(5), rs.getInt("negativos"), rs.getInt("id_ciudadano"), rs.getBoolean("sancionado"), rs.getBoolean("institucional"), rs.getBoolean("sancion_previa"));
    }

    private ReporteModeracion findReport(int id) {
        var reports = db.query(REPORTS + "WHERE r.id_reporte=?", this::report, id);
        if (reports.isEmpty()) throw error(HttpStatus.NOT_FOUND, "Reporte inexistente.");
        return reports.get(0);
    }

    private void appendAudit(String action, int target, int user, int actor, String reason, String detail) {
        db.update("INSERT dbo.AuditoriaAdministrativa(accion,objetivo_id,id_usuario_objetivo,id_responsable,motivo,detalle) VALUES(?,?,?,?,?,?)", action, target, user, actor, reason, detail);
    }

    private Auditoria auditRow(ResultSet rs, int row) throws SQLException {
        OffsetDateTime at = rs.getTimestamp(6).toLocalDateTime().atOffset(ZoneOffset.UTC);
        return new Auditoria(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getInt(5), at, rs.getString(7));
    }

    private int count(String sql, Object... args) { Integer value = db.queryForObject(sql, Integer.class, args); return value == null ? 0 : value; }
    private String reason(String input) { String value = input == null ? "" : input.strip(); if (value.length() < 10 || value.length() > 2000) throw error(HttpStatus.BAD_REQUEST, "Escribe un motivo de 10 a 2000 caracteres."); return value; }
    private String escapeLike(String value) { return value.replace("~", "~~").replace("%", "~%").replace("_", "~_").replace("[", "~["); }
    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }

    private record UsuarioBase(int id, String nombre, String correo, boolean activo, boolean sancionado) { }
    private record Sancion(int id, boolean activoAnterior) { }
    public record Usuario(int id, String nombre, String correo, boolean activo, List<String> roles, boolean sancionado) { }
    public record CambioUsuario(List<String> roles, Boolean activo, String motivo) { }
    public record EliminarUsuario(String motivo, Integer confirmacionId) { }
    public record NuevoUsuario(String nombre, String correo, String password, List<String> roles, String motivo) { }
    public record Motivo(String motivo) { }
    public record ReporteModeracion(int id, String tipoIncidenteNombre, String descripcion, String estado, double indiceVeracidad, int votosNegativos, int autorId, boolean sancionado, boolean verificadoInstitucionalmente, boolean sancionPrevia) { }
    public record Auditoria(int id, String accion, int objetivoId, String motivo, int responsableId, OffsetDateTime fechaUtc, String detalle) { }
}
