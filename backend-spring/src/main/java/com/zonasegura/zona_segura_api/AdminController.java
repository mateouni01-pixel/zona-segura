package com.zonasegura.zona_segura_api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    @GetMapping("/acceso")
    public Map<String, String> acceso() {
        return Map.of("estado", "autorizado");
    }
}
