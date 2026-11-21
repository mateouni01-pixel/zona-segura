package com.zonasegura.zona_segura_api;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.ResponseEntity;

@RestController
public class OperacionesController {
    private final OperacionesService service;
    public OperacionesController(OperacionesService service){this.service=service;}
    @GetMapping("/api/unidades") public List<OperacionesService.Unidad> units(@AuthenticationPrincipal Jwt jwt){return service.units(jwt.getSubject());}
    @PostMapping("/api/unidades") public ResponseEntity<?> unit(@AuthenticationPrincipal Jwt jwt,@RequestBody OperacionesService.UnidadEntrada input){return ResponseEntity.status(201).body(service.createUnit(jwt.getSubject(),input));}
    @PostMapping("/api/unidades/{id}/miembros") public void member(@AuthenticationPrincipal Jwt jwt,@PathVariable int id,@RequestBody OperacionesService.Miembro input){service.member(jwt.getSubject(),id,input);}
    @GetMapping("/api/recursos") public List<OperacionesService.Recurso> resources(@AuthenticationPrincipal Jwt jwt){return service.resources(jwt.getSubject());}
    @PostMapping("/api/recursos") public ResponseEntity<?> resource(@AuthenticationPrincipal Jwt jwt,@RequestBody OperacionesService.RecursoEntrada input){return ResponseEntity.status(201).body(service.saveResource(jwt.getSubject(),null,input));}
    @PutMapping("/api/recursos/{id}") public OperacionesService.Recurso update(@AuthenticationPrincipal Jwt jwt,@PathVariable int id,@RequestBody OperacionesService.RecursoEntrada input){return service.saveResource(jwt.getSubject(),id,input);}
    @GetMapping("/api/intervenciones") public List<OperacionesService.Caso> cases(@AuthenticationPrincipal Jwt jwt){return service.cases(jwt.getSubject());}
    @GetMapping("/api/intervenciones/ordenes") public List<OperacionesService.Orden> orders(@AuthenticationPrincipal Jwt jwt){return service.orders(jwt.getSubject(),false);}
    @PostMapping("/api/intervenciones") public ResponseEntity<?> assign(@AuthenticationPrincipal Jwt jwt,@RequestBody OperacionesService.Asignacion input){return ResponseEntity.status(201).body(service.assign(jwt.getSubject(),input));}
    @GetMapping("/api/operativa/ordenes") public List<OperacionesService.Orden> inbox(@AuthenticationPrincipal Jwt jwt){return service.orders(jwt.getSubject(),true);}
    @PostMapping("/api/operativa/ordenes/{id}/atender") public OperacionesService.Orden close(@AuthenticationPrincipal Jwt jwt,@PathVariable int id,@RequestBody OperacionesService.Cierre input){return service.close(jwt.getSubject(),id,input);}
    @PostMapping("/api/operativa/avisos/{id}/leer") public void read(@AuthenticationPrincipal Jwt jwt,@PathVariable int id){service.read(jwt.getSubject(),id);}
}
