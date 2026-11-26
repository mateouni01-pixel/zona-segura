package com.zonasegura.zona_segura_api;

import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

// Opt-in: solo consulta perfiles y prueba bloqueos; todas las transacciones se revierten.
@EnabledIfEnvironmentVariable(named="ZONA_SQLSERVER_CHECKS",matches="true")
class AdministracionSqlServerTests {
    private static final String URL=System.getenv().getOrDefault("ZONA_SQLSERVER_JDBC",
        "jdbc:sqlserver://MateoMS;databaseName=ZonaSegura;integratedSecurity=true;authenticationScheme=NativeAuthentication;encrypt=true;trustServerCertificate=true");
    private JdbcTemplate db;
    private TransactionTemplate tx;
    private AdministracionService service;
    private String actor;

    @BeforeEach void prepare() {
        var source=new DriverManagerDataSource(URL);
        db=new JdbcTemplate(source);
        tx=new TransactionTemplate(new JdbcTransactionManager(source));
        var encoder=new BCryptPasswordEncoder(4);
        service=new AdministracionService(db,new AuthService(db,encoder,null),encoder);
        actor=db.queryForObject("SELECT TOP (1) CONVERT(VARCHAR(20),u.id_usuario) FROM dbo.Usuarios u "
            +"JOIN dbo.UsuarioRoles ur ON ur.id_usuario=u.id_usuario JOIN dbo.Roles r ON r.id_rol=ur.id_rol "
            +"WHERE u.activo=1 AND r.nombre='ADMINISTRADOR' AND NOT EXISTS "
            +"(SELECT 1 FROM dbo.SancionesCuenta s WHERE s.id_usuario=u.id_usuario AND s.levantada_utc IS NULL)",String.class);
    }

    @Test void reproducesOriginalFirstStatementFailure() {
        tx.executeWithoutResult(status->{
            assertEquals(-999,db.queryForObject("DECLARE @r INT; EXEC @r=sys.sp_getapplock "
                +"@Resource=N'zona-segura-admin-global',@LockMode=N'Exclusive',@LockOwner=N'Transaction',@LockTimeout=0; SELECT @r",Integer.class));
            status.setRollbackOnly();
        });
    }

    @Test void allAdministrativeMutationsPassLockAndReachInputValidation() {
        for(int operation=0;operation<6;operation++) {
            final int selected=operation;
            tx.executeWithoutResult(status->{
                var error=assertThrows(ResponseStatusException.class,()->{
                    switch(selected){
                        case 0->service.create(actor,null);
                        case 1->service.update(actor,0,null);
                        case 2->service.reject(actor,0,null);
                        case 3->service.sanction(actor,0,null);
                        case 4->service.lift(actor,0,null);
                        default->service.delete(actor,0,null);
                    }
                });
                assertEquals(HttpStatus.BAD_REQUEST,error.getStatusCode());
                assertEquals("Exclusive",db.queryForObject("SELECT APPLOCK_MODE('public','zona-segura-admin-global','Transaction')",String.class));
                assertEquals(1,db.queryForObject("SELECT @@TRANCOUNT",Integer.class));
                status.setRollbackOnly();
            });
            try(Connection probe=DriverManager.getConnection(URL);var command=probe.createStatement()) {
                probe.setAutoCommit(false);
                try(var rows=command.executeQuery("SELECT TOP (1) id_usuario FROM dbo.Usuarios")){while(rows.next()){} }
                try(var rows=command.executeQuery("DECLARE @r INT; EXEC @r=sys.sp_getapplock "
                    +"@Resource=N'zona-segura-admin-global',@LockMode=N'Exclusive',@LockOwner=N'Transaction',@LockTimeout=0; SELECT @r")) {
                    assertTrue(rows.next());assertEquals(0,rows.getInt(1),"El bloqueo debe liberarse al terminar cada operación");
                }
                probe.rollback();
            } catch(java.sql.SQLException exception){throw new AssertionError(exception);}
        }
    }

    @Test void deletesOnlyTransactionalFixturesAndPreservesAudit() {
        for(String role:java.util.List.of("CIUDADANO","ADMINISTRADOR","PERSONAL_ALCALDIA","UNIDAD_OPERATIVA")) {
            tx.executeWithoutResult(status->{
                var user=service.create(actor,new AdministracionService.NuevoUsuario("Prueba temporal eliminación",
                    "prueba-"+java.util.UUID.randomUUID()+"@example.invalid","ClaveTemporal123",java.util.List.of(role),"Alta temporal para prueba con rollback"));
                service.delete(actor,user.id(),new AdministracionService.EliminarUsuario("Eliminación temporal para prueba con rollback",user.id()));
                assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM dbo.Usuarios WHERE id_usuario=?",Integer.class,user.id()));
                assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM dbo.UsuarioRoles WHERE id_usuario=?",Integer.class,user.id()));
                assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM dbo.AuditoriaAdministrativa WHERE id_usuario_objetivo=?",Integer.class,user.id()));
                assertEquals(HttpStatus.UNAUTHORIZED,assertThrows(ResponseStatusException.class,
                    ()->new AuthService(db,new BCryptPasswordEncoder(4),null).profile(Integer.toString(user.id()))).getStatusCode());
                status.setRollbackOnly();
            });
        }
    }
}
