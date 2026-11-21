package com.zonasegura.zona_segura_api;

import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdministracionService service;
    public AdminController(AdministracionService service) { this.service = service; }

    @GetMapping("/acceso")
    public Map<String, String> acceso(@AuthenticationPrincipal Jwt jwt) {
        service.profile(jwt.getSubject());
        return Map.of("estado", "autorizado");
    }

    @GetMapping("/perfil")
    public AuthService.Profile perfil(@AuthenticationPrincipal Jwt jwt) { return service.profile(jwt.getSubject()); }

    @GetMapping("/usuarios")
    public List<AdministracionService.Usuario> usuarios(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required=false) String buscar) { return service.users(jwt.getSubject(), buscar); }

    @GetMapping("/usuarios/{id}")
    public AdministracionService.Usuario usuario(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        return service.user(jwt.getSubject(), id);
    }

    @PutMapping("/usuarios/{id}")
    public AdministracionService.Usuario actualizar(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
            @RequestBody AdministracionService.CambioUsuario input) { return service.update(jwt.getSubject(), id, input); }

    @PostMapping("/usuarios/{id}/levantar-sancion")
    public AdministracionService.Usuario levantar(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
            @RequestBody AdministracionService.Motivo input) { return service.lift(jwt.getSubject(), id, input); }

    @GetMapping("/auditoria")
    public List<AdministracionService.Auditoria> auditoria(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required=false) Integer usuarioId) { return service.audit(jwt.getSubject(), usuarioId); }
}
