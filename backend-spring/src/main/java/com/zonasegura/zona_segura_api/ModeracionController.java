package com.zonasegura.zona_segura_api;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class ModeracionController {
    private final AdministracionService service;
    public ModeracionController(AdministracionService service) { this.service = service; }

    @GetMapping("/api/moderacion/reportes")
    public List<AdministracionService.ReporteModeracion> reportes(@AuthenticationPrincipal Jwt jwt) { return service.reports(jwt.getSubject()); }

    @PostMapping("/api/reportes/{id}/rechazo")
    public AdministracionService.ReporteModeracion rechazar(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
            @RequestBody AdministracionService.Motivo input) { return service.reject(jwt.getSubject(), id, input); }

    @PostMapping("/api/reportes/{id}/sancion")
    public AdministracionService.Usuario sancionar(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
            @RequestBody AdministracionService.Motivo input) { return service.sanction(jwt.getSubject(), id, input); }
}
