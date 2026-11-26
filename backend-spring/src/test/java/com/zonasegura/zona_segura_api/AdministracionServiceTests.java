package com.zonasegura.zona_segura_api;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdministracionServiceTests {
    private JdbcTemplate db;
    private AuthService auth;
    private AdministracionService service;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(4);
    private List<String> storedRoles;

    @BeforeEach
    void prepare() throws Exception {
        db = mock(JdbcTemplate.class);
        auth = mock(AuthService.class);
        service = new AdministracionService(db, auth, passwords);
        storedRoles = List.of("ADMINISTRADOR");
        when(auth.profile("1")).thenReturn(new AuthService.Profile(1, "Administrador", List.of("ADMINISTRADOR"), 0));
        when(db.queryForObject(startsWith("DECLARE @result"), eq(Integer.class))).thenReturn(0);
        when(db.queryForObject(startsWith("INSERT INTO dbo.Usuarios"), eq(Integer.class), anyString(), anyString(), anyString())).thenReturn(42);
        when(db.update(startsWith("INSERT dbo.UsuarioRoles"), eq(42), anyString())).thenReturn(1);
        when(db.query(anyString(), ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class))).thenAnswer(call -> {
            String sql = call.getArgument(0);
            RowMapper<?> mapper = call.getArgument(1);
            List<Object> rows = new ArrayList<>();
            if (sql.contains("JOIN dbo.Roles")) {
                for (String role : storedRoles) {
                    ResultSet row = mock(ResultSet.class);
                    when(row.getString(1)).thenReturn(role);
                    rows.add(mapper.mapRow(row, rows.size()));
                }
            } else {
                ResultSet row = mock(ResultSet.class);
                when(row.getInt(1)).thenReturn(sql.contains("WITH (UPDLOCK") ? 1 : 42);
                when(row.getString(2)).thenReturn("Nueva cuenta");
                when(row.getString(3)).thenReturn("nuevo@example.com");
                when(row.getBoolean(4)).thenReturn(true);
                rows.add(mapper.mapRow(row, 0));
            }
            return rows;
        });
    }

    private AdministracionService.NuevoUsuario input(List<String> roles, String password) {
        return new AdministracionService.NuevoUsuario(" Nueva cuenta ", " NUEVO@example.com ", password, roles, "Alta autorizada por administración");
    }

    @Test
    void startsTransactionWithProfileBeforeRequestingLockAndRechecksPermissions() {
        service.create("1", input(storedRoles, "ClaveSegura123"));
        var order = inOrder(auth, db);
        order.verify(auth).profile("1");
        order.verify(db).queryForObject(startsWith("DECLARE @result"), eq(Integer.class));
        order.verify(db).query(startsWith("SELECT id_usuario FROM dbo.Usuarios WITH"), ArgumentMatchers.<RowMapper<Object>>any(), eq(1));
        order.verify(auth).profile("1");
        order.verify(db).queryForObject(startsWith("INSERT INTO dbo.Usuarios"), eq(Integer.class), anyString(), anyString(), anyString());
    }

    @Test
    void rechecksRevokedAdministratorAfterAcquiringLock() {
        when(auth.profile("1")).thenReturn(new AuthService.Profile(1,"Administrador",storedRoles,0),
                new AuthService.Profile(1,"Cuenta",List.of("CIUDADANO"),1));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.create("1",input(storedRoles,"ClaveSegura123")));
        assertNoAccountWritten();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1,-2,-3})
    void reportsContentionOnlyForLockConflicts(int result) {
        when(db.queryForObject(startsWith("DECLARE @result"),eq(Integer.class))).thenReturn(result);
        assertStatus(HttpStatus.CONFLICT, () -> service.create("1",input(storedRoles,"ClaveSegura123")));
        assertNoAccountWritten();
    }

    @ParameterizedTest
    @ValueSource(ints = {-999})
    @NullSource
    void technicalLockErrorsAreNotReportedAsAnotherOperation(Integer result) {
        when(db.queryForObject(startsWith("DECLARE @result"),eq(Integer.class))).thenReturn(result);
        assertStatus(HttpStatus.INTERNAL_SERVER_ERROR, () -> service.create("1",input(storedRoles,"ClaveSegura123")));
        assertNoAccountWritten();
    }

    @Test
    void acceptsLockGrantedAfterWaiting() {
        when(db.queryForObject(startsWith("DECLARE @result"),eq(Integer.class))).thenReturn(1);
        assertEquals(42,service.create("1",input(storedRoles,"ClaveSegura123")).id());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRADOR", "CIUDADANO", "PERSONAL_ALCALDIA", "UNIDAD_OPERATIVA"})
    void administratorCreatesEachSupportedRole(String role) {
        storedRoles = List.of(role);
        var user = service.create("1", input(List.of(role), "ClaveSegura123"));
        assertEquals(42, user.id());
        assertEquals(List.of(role), user.roles());
        assertTrue(user.activo());
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(db).queryForObject(startsWith("INSERT INTO dbo.Usuarios"), eq(Integer.class), eq("Nueva cuenta"), eq("nuevo@example.com"), hash.capture());
        assertNotEquals("ClaveSegura123", hash.getValue());
        assertTrue(passwords.matches("ClaveSegura123", hash.getValue()));
        verify(db).update(startsWith("INSERT dbo.UsuarioRoles"), eq(42), eq(role));
        verify(db).update(startsWith("INSERT dbo.AuditoriaAdministrativa"), eq("USUARIO_CREADO"), eq(42), eq(42), eq(1), anyString(), ArgumentMatchers.<String>argThat(detail -> !detail.contains("ClaveSegura123")));
        if (role.equals("ADMINISTRADOR")) verify(db).update(contains("INSERT dbo.Administradores"), eq(42), eq(42));
        if (role.equals("PERSONAL_ALCALDIA")) verify(db).update(contains("INSERT dbo.PersonalAlcaldia"), eq(42), eq(42));
        if (role.equals("CIUDADANO")) verify(db).update(contains("INSERT dbo.Ciudadanos"), eq(42), eq(42));
    }

    @Test
    void supportsMultipleRolesAndNormalizesNames() {
        storedRoles = List.of("ADMINISTRADOR", "PERSONAL_ALCALDIA");
        assertEquals(storedRoles, service.create("1", input(List.of(" administrador ", "personal_alcaldia"), "ClaveSegura123")).roles());
        verify(db).update(startsWith("INSERT dbo.UsuarioRoles"), eq(42), eq("ADMINISTRADOR"));
        verify(db).update(startsWith("INSERT dbo.UsuarioRoles"), eq(42), eq("PERSONAL_ALCALDIA"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CIUDADANO", "PERSONAL_ALCALDIA", "UNIDAD_OPERATIVA"})
    void nonAdministratorsCannotCreateAdministrators(String role) {
        when(auth.profile("1")).thenReturn(new AuthService.Profile(1, "Cuenta", List.of(role), 1));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.create("1", input(List.of("ADMINISTRADOR"), "ClaveSegura123")));
        verify(db, never()).queryForObject(startsWith("DECLARE @result"), eq(Integer.class));
        assertNoAccountWritten();
    }

    @Test
    void revokedOrInactiveAccountCannotCreateUsers() {
        when(auth.profile("1")).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.create("1", input(List.of("ADMINISTRADOR"), "ClaveSegura123")));
        assertNoAccountWritten();
    }

    @Test
    void rejectsEmptyUnknownDuplicateAndNullRoles() {
        for (List<String> roles : Arrays.asList(List.<String>of(), List.of("SUPERADMIN"), List.of("ADMINISTRADOR", "administrador"), Arrays.asList((String)null), null)) {
            assertStatus(HttpStatus.BAD_REQUEST, () -> service.create("1", input(roles, "ClaveSegura123")));
        }
        assertNoAccountWritten();
    }

    @ParameterizedTest
    @ValueSource(strings = {"corta", "                                                                          ", "😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀"})
    void rejectsShortOrOver72BytePasswords(String password) {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create("1", input(List.of("ADMINISTRADOR"), password)));
        assertNoAccountWritten();
    }

    @Test
    void validatesIdentityAndAuditReason() {
        for (var invalid : List.of(
                new AdministracionService.NuevoUsuario(" ", "nuevo@example.com", "ClaveSegura123", storedRoles, "Motivo válido de creación"),
                new AdministracionService.NuevoUsuario("Nombre", "correo-invalido", "ClaveSegura123", storedRoles, "Motivo válido de creación"),
                new AdministracionService.NuevoUsuario("Nombre", "nuevo@example.com", "ClaveSegura123", storedRoles, "corto"))) {
            assertStatus(HttpStatus.BAD_REQUEST, () -> service.create("1", invalid));
        }
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create("1", null));
        assertNoAccountWritten();
    }

    @Test
    void duplicateEmailReturnsConflictWithoutAssigningRoles() {
        when(db.queryForObject(startsWith("INSERT INTO dbo.Usuarios"), eq(Integer.class), anyString(), anyString(), anyString())).thenThrow(new DuplicateKeyException("duplicate"));
        assertStatus(HttpStatus.CONFLICT, () -> service.create("1", input(storedRoles, "ClaveSegura123")));
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void missingRoleDefinitionFailsInsteadOfReportingSuccess() {
        when(db.update(startsWith("INSERT dbo.UsuarioRoles"), eq(42), anyString())).thenReturn(0);
        assertStatus(HttpStatus.CONFLICT, () -> service.create("1", input(storedRoles, "ClaveSegura123")));
        verify(db, never()).update(startsWith("INSERT dbo.AuditoriaAdministrativa"), any(Object[].class));
    }

    @Test
    void publicRegistrationStillOnlyGrantsCitizenRole() {
        when(db.queryForObject(contains("SELECT id_rol FROM dbo.Roles"), eq(Integer.class))).thenReturn(3);
        var publicRegistration = new AuthService(db, passwords, null);
        assertEquals(42, publicRegistration.register("Ciudadano", "ciudadano@example.com", "ClaveSegura123"));
        verify(db).update("INSERT INTO dbo.UsuarioRoles (id_usuario, id_rol) VALUES (?, ?)", 42, 3);
        verify(db).update("INSERT INTO dbo.Ciudadanos (id_usuario) VALUES (?)", 42);
        verify(db, never()).update(contains("Administradores"), any(Object[].class));
    }

    private void assertNoAccountWritten() {
        verify(db, never()).queryForObject(startsWith("INSERT INTO dbo.Usuarios"), eq(Integer.class), anyString(), anyString(), anyString());
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @Test void deletesUnusedAccountAndAuditsBeforeRemovingIt() {
        storedRoles=List.of("CIUDADANO");
        when(db.update("DELETE FROM dbo.Usuarios WHERE id_usuario=?",42)).thenReturn(1);
        service.delete("1",42,new AdministracionService.EliminarUsuario("Cuenta duplicada de prueba",42));
        var order=inOrder(db);
        order.verify(db).update(startsWith("INSERT dbo.AuditoriaAdministrativa"),eq("USUARIO_ELIMINADO"),eq(42),eq(42),eq(1),anyString(),anyString());
        order.verify(db).update("DELETE FROM dbo.UsuarioRoles WHERE id_usuario=?",42);
        order.verify(db).update("DELETE FROM dbo.Usuarios WHERE id_usuario=?",42);
    }

    @Test void cannotDeleteOwnAccount() {
        assertStatus(HttpStatus.CONFLICT,()->service.delete("1",1,new AdministracionService.EliminarUsuario("Motivo válido de eliminación",1)));
        assertNoAccountWritten();
    }

    @Test void cannotDeleteLastAdministrator() {
        assertStatus(HttpStatus.CONFLICT,()->service.delete("1",42,new AdministracionService.EliminarUsuario("Motivo válido de eliminación",42)));
        assertNoAccountWritten();
    }

    @Test void linkedReportsPreventDeletionBeforeAnyWrite() {
        storedRoles=List.of("CIUDADANO");
        when(db.queryForObject("SELECT COUNT(*) FROM dbo.Reportes WHERE id_ciudadano=?",Integer.class,42)).thenReturn(1);
        assertStatus(HttpStatus.CONFLICT,()->service.delete("1",42,new AdministracionService.EliminarUsuario("Motivo válido de eliminación",42)));
        assertNoAccountWritten();
    }

    @Test void deletionRequiresMatchingConfirmationAndReason() {
        for(var input:List.of(new AdministracionService.EliminarUsuario("Motivo válido de eliminación",null),
                new AdministracionService.EliminarUsuario("Motivo válido de eliminación",43),new AdministracionService.EliminarUsuario("corto",42))) {
            assertStatus(HttpStatus.BAD_REQUEST,()->service.delete("1",42,input));
        }
        assertStatus(HttpStatus.BAD_REQUEST,()->service.delete("1",42,null));
        assertNoAccountWritten();
    }

    @Test void citizenCannotDeleteAccounts() {
        when(auth.profile("1")).thenReturn(new AuthService.Profile(1,"Cuenta",List.of("CIUDADANO"),1));
        assertStatus(HttpStatus.FORBIDDEN,()->service.delete("1",42,new AdministracionService.EliminarUsuario("Motivo válido de eliminación",42)));
        assertNoAccountWritten();
    }

    private void assertStatus(HttpStatus status, org.junit.jupiter.api.function.Executable action) {
        assertEquals(status, assertThrows(ResponseStatusException.class, action).getStatusCode());
    }
}
