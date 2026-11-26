package com.example.zonasegura;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Catálogo común a todas las combinaciones; no concede permisos. */
enum InterfazUsuario {
    CIUDADANO("CIUDADANO","Ciudadano","Crea reportes, consulta su seguimiento y explora el mapa de incidentes."),
    ALCALDIA("PERSONAL_ALCALDIA","Personal de Alcaldía","Coordina incidentes, recursos, unidades y órdenes de intervención."),
    ADMINISTRADOR("ADMINISTRADOR","Administrador","Revisa y valida reportes y gestiona la moderación."),
    OPERATIVA("UNIDAD_OPERATIVA","Unidad Operativa","Consulta las órdenes de tu unidad y registra la atención.");

    final String rol;
    final String nombre;
    final String descripcion;
    InterfazUsuario(String rol,String nombre,String descripcion){this.rol=rol;this.nombre=nombre;this.descripcion=descripcion;}
    static InterfazUsuario buscar(String rol){for(var interfaz:values())if(interfaz.rol.equals(rol))return interfaz;return null;}
    static List<InterfazUsuario> disponibles(Collection<String> roles){
        List<InterfazUsuario> result=new ArrayList<>();
        if(roles!=null)for(var interfaz:values())if(roles.contains(interfaz.rol))result.add(interfaz);
        return result;
    }
}
