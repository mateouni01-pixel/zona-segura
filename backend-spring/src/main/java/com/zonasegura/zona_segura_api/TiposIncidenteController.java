package com.zonasegura.zona_segura_api;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tipos-incidente")
public class TiposIncidenteController {
    private final ReportesService reports;
    public TiposIncidenteController(ReportesService reports) { this.reports = reports; }

    @GetMapping
    public List<ReportesService.Tipo> list(@AuthenticationPrincipal Jwt jwt) {
        return reports.types(jwt.getSubject());
    }

    @GetMapping("/{id:\\d+}")
    public ReportesService.Tipo get(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        return reports.type(jwt.getSubject(), id);
    }
}
