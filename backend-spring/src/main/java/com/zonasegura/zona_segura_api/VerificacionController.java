package com.zonasegura.zona_segura_api;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class VerificacionController {
    private final VerificacionService service;
    public VerificacionController(VerificacionService service){this.service=service;}
    @GetMapping("/api/reportes/cercanos")
    public List<VerificacionService.Detalle> nearby(@AuthenticationPrincipal Jwt jwt,@RequestParam Double lat,@RequestParam Double lng,@RequestParam(defaultValue="500") int radio){
        return service.nearby(jwt.getSubject(),lat,lng,radio);
    }
    @PostMapping("/api/reportes/{id}/votos")
    public VerificacionService.Detalle vote(@AuthenticationPrincipal Jwt jwt,@PathVariable int id,@RequestBody VerificacionService.Voto input){
        return service.vote(jwt.getSubject(),id,input);
    }
    @GetMapping({"/api/admin/reportes-verificacion", "/api/alcaldia/reportes-verificacion"})
    public List<VerificacionService.Detalle> reports(@AuthenticationPrincipal Jwt jwt){return service.administrativeReports(jwt.getSubject());}
    @PostMapping({"/api/admin/reportes/{id}/verificacion", "/api/alcaldia/reportes/{id}/verificacion"})
    public VerificacionService.Detalle verify(@AuthenticationPrincipal Jwt jwt,@PathVariable int id,@RequestBody VerificacionService.Verificacion input){
        return service.verify(jwt.getSubject(),id,input);
    }
}
