package com.zonasegura.zona_segura_api;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/reportes")
public class ReportesController {
    private final ReportesService reports;
    public ReportesController(ReportesService reports) { this.reports = reports; }

    @GetMapping
    public List<ReportesService.Reporte> list(@AuthenticationPrincipal Jwt jwt) {
        return reports.list(jwt.getSubject());
    }

    @GetMapping("/{id:\\d+}")
    public ReportesService.Reporte get(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        return reports.get(jwt.getSubject(), id);
    }

    @PostMapping
    public ResponseEntity<ReportesService.Reporte> create(@AuthenticationPrincipal Jwt jwt,
                                                         @RequestHeader(value="Idempotency-Key",required=false) String key,
                                                         @RequestBody ReportesService.Entrada input) {
        var report = reports.create(jwt.getSubject(), input,key);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(report.id()).toUri();
        return ResponseEntity.created(location).body(report);
    }
}
