package com.zonasegura.zona_segura_api;

import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VerificacionPermisosTests {
    private JdbcTemplate db;
    private AuthService auth;
    private VerificacionService service;
    private String state;
    private int author;
    private int positives;

    @BeforeEach
    void prepare() throws Exception {
        db=mock(JdbcTemplate.class);auth=mock(AuthService.class);service=new VerificacionService(db,auth);
        state="PENDIENTE_VALIDACION";author=2;positives=2;
        when(auth.profile("1")).thenReturn(new AuthService.Profile(1,"Administrador",List.of("ADMINISTRADOR"),0));
        when(db.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(0);
        org.mockito.stubbing.Answer<List<Object>> read=call->{
            var row=mock(ResultSet.class);
            when(row.getInt(1)).thenReturn(10);when(row.getInt("id_reporte")).thenReturn(10);
            when(row.getInt("id_ciudadano")).thenReturn(author);
            when(row.getString("descripcion")).thenReturn("Incidente de prueba");when(row.getString("estado")).thenReturn(state);
            when(row.getString("tipo")).thenReturn("Incidente");when(row.getInt("positivos")).thenReturn(positives);
            when(row.getDouble("latitud")).thenReturn(-17.78);when(row.getDouble("longitud")).thenReturn(-63.18);
            RowMapper<?> mapper=call.getArgument(1);return List.of(mapper.mapRow(row,0));
        };
        when(db.query(anyString(),ArgumentMatchers.<RowMapper<Object>>any(),any(Object[].class))).thenAnswer(read);
        when(db.query(anyString(),ArgumentMatchers.<RowMapper<Object>>any())).thenAnswer(read);
        when(db.update(eq("UPDATE dbo.Reportes SET estado=? WHERE id_reporte=?"),anyString(),eq(10))).thenAnswer(call->{state=call.getArgument(1);return 1;});
    }

    @ParameterizedTest
    @ValueSource(booleans={true,false})
    void administratorWithoutMunicipalProfileCanDecide(boolean confirms) {
        var detail=service.verify("1",10,new VerificacionService.Verificacion(confirms,"Revisión fundamentada de la evidencia"));
        assertEquals(confirms?"VALIDADO":"RECHAZADO",detail.estado());
        verify(db).update(startsWith("INSERT dbo.VerificacionesInstitucionales"),eq(10),eq(1),eq(confirms),anyString());
        verify(db).update(startsWith("INSERT dbo.AuditoriaAdministrativa"),eq(confirms?"REPORTE_VALIDADO":"REPORTE_RECHAZADO"),eq(10),eq(2),eq(1),anyString(),anyString());
        verify(db,never()).queryForObject(contains("PersonalAlcaldia"),eq(Integer.class),any(Object[].class));
    }

    @ParameterizedTest
    @ValueSource(strings={"PERSONAL_ALCALDIA","CIUDADANO","UNIDAD_OPERATIVA"})
    void otherRolesCannotListOrDecide(String role) {
        when(auth.profile("1")).thenReturn(new AuthService.Profile(1,"Cuenta",List.of(role),1));
        assertStatus(HttpStatus.FORBIDDEN,()->service.administrativeReports("1"));
        assertStatus(HttpStatus.FORBIDDEN,()->service.verify("1",10,new VerificacionService.Verificacion(true,"Motivo de revisión válido")));
        verifyNoInteractions(db);
    }

    @Test
    void administratorCanListReportsWithoutMunicipalRole() {
        assertEquals(1,service.administrativeReports("1").size());
        verify(db,never()).queryForObject(contains("PersonalAlcaldia"),eq(Integer.class),any(Object[].class));
    }

    @Test
    void ownReportAndRepeatedDecisionRemainForbidden() {
        author=1;
        assertStatus(HttpStatus.FORBIDDEN,()->service.verify("1",10,new VerificacionService.Verificacion(true,"Motivo de revisión válido")));
        author=2;
        when(db.queryForObject(startsWith("SELECT COUNT(*) FROM dbo.VerificacionesInstitucionales"),eq(Integer.class),eq(10))).thenReturn(1);
        assertStatus(HttpStatus.CONFLICT,()->service.verify("1",10,new VerificacionService.Verificacion(true,"Motivo de revisión válido")));
        verify(db,never()).update(anyString(),any(Object[].class));
    }

    @Test
    void threeCitizenConfirmationsStillValidateAutomatically() {
        when(auth.profile("3")).thenReturn(new AuthService.Profile(3,"Ciudadano",List.of("CIUDADANO"),3));
        when(db.update(startsWith("INSERT dbo.ValidacionesComunitarias"),eq(10),eq(3),eq(true))).thenAnswer(call->{positives++;return 1;});
        when(db.update(startsWith("UPDATE dbo.Reportes SET indice_veracidad"),anyDouble(),anyString(),eq(10))).thenAnswer(call->{state=call.getArgument(2);return 1;});
        var detail=service.vote("3",10,new VerificacionService.Voto(true,-17.78,-63.18));
        assertEquals(3,detail.confirmaciones());assertEquals("VALIDADO",detail.estado());assertEquals("COMUNITARIA",detail.origenValidacion());
        verify(db,never()).update(startsWith("INSERT dbo.VerificacionesInstitucionales"),any(Object[].class));
    }

    private void assertStatus(HttpStatus status,org.junit.jupiter.api.function.Executable action) {
        assertEquals(status,assertThrows(ResponseStatusException.class,action).getStatusCode());
    }
}
