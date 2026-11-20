package com.zonasegura.zona_segura_api;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mapa")
public class MapaController {
    private final MapaService map;
    public MapaController(MapaService map) { this.map = map; }

    @GetMapping("/resumen")
    public MapaService.Resumen summary(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required=false) Double sur, @RequestParam(required=false) Double oeste,
            @RequestParam(required=false) Double norte, @RequestParam(required=false) Double este,
            @RequestParam(defaultValue="7") int dias, @RequestParam(required=false) Integer tipoIncidenteId) {
        return map.summary(jwt.getSubject(), sur, oeste, norte, este, dias, tipoIncidenteId);
    }

    @GetMapping("/zonas")
    public List<MapaService.Zona> zones(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required=false) Double sur, @RequestParam(required=false) Double oeste,
            @RequestParam(required=false) Double norte, @RequestParam(required=false) Double este,
            @RequestParam(defaultValue="7") int dias, @RequestParam(required=false) Integer tipoIncidenteId) {
        return map.summary(jwt.getSubject(), sur, oeste, norte, este, dias, tipoIncidenteId).zonas();
    }
}
