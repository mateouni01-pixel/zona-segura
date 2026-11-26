package com.zonasegura.zona_segura_api;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class VerificacionSecurityTests {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private AuthService auth;

    @BeforeEach
    void prepare() {
        context=new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getSystemProperties().put("zona.jwt.secret","AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        context.register(TestConfig.class);context.refresh();
        auth=context.getBean(AuthService.class);
        mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
    }
    @AfterEach void close(){context.close();System.clearProperty("zona.jwt.secret");}

    private String token(String role) {
        when(auth.profile("1")).thenReturn(new AuthService.Profile(1,"Cuenta",List.of(role),0));
        var claims=JwtClaimsSet.builder().issuer("zona-segura-api").audience(List.of("zona-segura-android")).subject("1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).claim("roles",List.of(role)).build();
        return "Bearer "+context.getBean(JwtEncoder.class).encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
    }

    @ParameterizedTest
    @ValueSource(strings={"/api/admin/reportes-verificacion","/api/alcaldia/reportes-verificacion"})
    void bothListRoutesRequireAdministrator(String path)throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization",token("PERSONAL_ALCALDIA"))).andExpect(status().isForbidden());
        mvc.perform(get(path).header("Authorization",token("ADMINISTRADOR"))).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings={"/api/admin/reportes/10/verificacion","/api/alcaldia/reportes/10/verificacion"})
    void bothDecisionRoutesRejectMunicipalRoleAndAcceptAdministrator(String path)throws Exception {
        String body="{\"confirma\":true,\"motivo\":\"Revisión administrativa fundamentada\"}";
        mvc.perform(post(path).contentType("application/json").content(body).header("Authorization",token("PERSONAL_ALCALDIA"))).andExpect(status().isForbidden());
        mvc.perform(post(path).contentType("application/json").content(body).header("Authorization",token("ADMINISTRADOR"))).andExpect(status().isOk());
    }

    @Test
    void tokenDoesNotKeepRevokedAdministratorPermission()throws Exception {
        String previousToken=token("ADMINISTRADOR");
        when(auth.profile("1")).thenReturn(new AuthService.Profile(1,"Cuenta",List.of("PERSONAL_ALCALDIA"),0));
        mvc.perform(get("/api/admin/reportes-verificacion").header("Authorization",previousToken)).andExpect(status().isForbidden());
        mvc.perform(post("/api/alcaldia/reportes/10/verificacion").contentType("application/json")
                .content("{\"confirma\":true,\"motivo\":\"Revisión administrativa fundamentada\"}").header("Authorization",previousToken)).andExpect(status().isForbidden());
    }

    @Configuration @EnableWebMvc @EnableWebSecurity @Import(SecurityConfig.class)
    static class TestConfig {
        @Bean AuthService authService(){return mock(AuthService.class);}
        @Bean VerificacionService verificacionService(){return mock(VerificacionService.class);}
        @Bean VerificacionController verificacionController(VerificacionService service){return new VerificacionController(service);}
        @Bean AdministracionService administracionService(){return mock(AdministracionService.class);}
        @Bean AdminController adminController(AdministracionService service){return new AdminController(service);}
    }

    @Test void accountDeletionRequiresCurrentAdministrator()throws Exception {
        String body="{\"motivo\":\"Cuenta duplicada de prueba\",\"confirmacionId\":42}";
        mvc.perform(delete("/api/admin/usuarios/42").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        for(String role:List.of("CIUDADANO","PERSONAL_ALCALDIA","UNIDAD_OPERATIVA"))
            mvc.perform(delete("/api/admin/usuarios/42").contentType("application/json").content(body).header("Authorization",token(role))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/usuarios/42").contentType("application/json").content(body).header("Authorization",token("ADMINISTRADOR"))).andExpect(status().isNoContent());
        verify(context.getBean(AdministracionService.class)).delete("1",42,new AdministracionService.EliminarUsuario("Cuenta duplicada de prueba",42));
    }
}
