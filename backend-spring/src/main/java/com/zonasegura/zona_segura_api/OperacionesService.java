package com.zonasegura.zona_segura_api;

import java.util.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OperacionesService {
    private final JdbcTemplate db;
    private final AuthService auth;
    public OperacionesService(JdbcTemplate db,AuthService auth){this.db=db;this.auth=auth;}
    private ResponseStatusException error(HttpStatus status,String text){return new ResponseStatusException(status,text);}
    private int municipal(String subject){
        var p=auth.profile(subject);
        if(!p.roles().contains("PERSONAL_ALCALDIA") || db.queryForObject("SELECT COUNT(*) FROM dbo.PersonalAlcaldia WHERE id_usuario=?",Integer.class,p.idUsuario())!=1)
            throw error(HttpStatus.FORBIDDEN,"Se requiere personal municipal activo.");
        return p.idUsuario();
    }
    private int unit(String subject){
        var p=auth.profile(subject);
        if(!p.roles().contains("UNIDAD_OPERATIVA"))throw error(HttpStatus.FORBIDDEN,"Se requiere una cuenta operativa.");
        var units=db.query("SELECT u.id_unidad FROM dbo.MiembrosUnidad m JOIN dbo.UnidadesOperativas u ON u.id_unidad=m.id_unidad WHERE m.id_usuario=? AND u.activo=1",(r,n)->r.getInt(1),p.idUsuario());
        if(units.isEmpty())throw error(HttpStatus.FORBIDDEN,"Tu cuenta no tiene una unidad activa asignada.");
        return units.get(0);
    }
    private String text(String value,int min,int max){String t=value==null?"":value.strip();if(t.length()<min||t.length()>max)throw error(HttpStatus.BAD_REQUEST,"Revisa la longitud de los campos obligatorios.");return t;}
    private UUID key(String value){try{return UUID.fromString(value);}catch(Exception e){throw error(HttpStatus.BAD_REQUEST,"solicitudId debe ser UUID.");}}
    public List<Unidad> units(String subject){municipal(subject);return db.query("SELECT id_unidad,nombre,activo FROM dbo.UnidadesOperativas ORDER BY id_unidad",(r,n)->new Unidad(r.getInt(1),r.getString(2),r.getBoolean(3)));}
    public Unidad createUnit(String subject,UnidadEntrada input){
        municipal(subject);String name=text(input.nombre(),1,150);
        int id=db.queryForObject("INSERT dbo.UnidadesOperativas(nombre) OUTPUT INSERTED.id_unidad VALUES(?)",Integer.class,name);
        return new Unidad(id,name,true);
    }
    @Transactional
    public void member(String subject,int id,Miembro input){
        municipal(subject);
        if(db.queryForObject("SELECT COUNT(*) FROM dbo.UnidadesOperativas WHERE id_unidad=? AND activo=1",Integer.class,id)!=1)throw error(HttpStatus.BAD_REQUEST,"Unidad inexistente o inactiva.");
        if(input.usuarioId()==null || db.queryForObject("SELECT COUNT(*) FROM dbo.Usuarios u JOIN dbo.UsuarioRoles ur ON ur.id_usuario=u.id_usuario JOIN dbo.Roles r ON r.id_rol=ur.id_rol WHERE u.id_usuario=? AND u.activo=1 AND r.nombre='UNIDAD_OPERATIVA'",Integer.class,input.usuarioId())!=1)
            throw error(HttpStatus.BAD_REQUEST,"El usuario debe existir y tener el rol UNIDAD_OPERATIVA.");
        db.queryForObject("SELECT id_usuario FROM dbo.Usuarios WITH(UPDLOCK,HOLDLOCK) WHERE id_usuario=?",Integer.class,input.usuarioId());
        if(db.update("UPDATE dbo.MiembrosUnidad SET id_unidad=? WHERE id_usuario=?",id,input.usuarioId())==0)db.update("INSERT dbo.MiembrosUnidad(id_usuario,id_unidad) VALUES(?,?)",input.usuarioId(),id);
    }
    private Recurso resource(ResultSet r)throws SQLException{return new Recurso(r.getInt("id_recurso"),r.getString("nombre"),r.getString("tipo"),r.getInt("id_unidad"),r.getBoolean("disponible"),r.getBoolean("activo"));}
    public List<Recurso> resources(String subject){municipal(subject);return db.query("SELECT * FROM dbo.RecursosPreventivos ORDER BY id_recurso",(r,n)->resource(r));}
    @Transactional
    public Recurso saveResource(String subject,Integer id,RecursoEntrada input){
        municipal(subject);String name=text(input.nombre(),1,150);
        if(!List.of("PATRULLA","CUADRILLA").contains(input.tipo()==null?"":input.tipo())||input.unidadId()==null||input.disponible()==null||input.activo()==null)
            throw error(HttpStatus.BAD_REQUEST,"Indica tipo, unidad y disponibilidad.");
        if(db.queryForObject("SELECT COUNT(*) FROM dbo.UnidadesOperativas WHERE id_unidad=? AND activo=1",Integer.class,input.unidadId())!=1)throw error(HttpStatus.BAD_REQUEST,"Unidad inexistente o inactiva.");
        if(id==null)id=db.queryForObject("INSERT dbo.RecursosPreventivos(nombre,tipo,id_unidad,disponible,activo) OUTPUT INSERTED.id_recurso VALUES(?,?,?,?,?)",Integer.class,name,input.tipo(),input.unidadId(),input.disponible(),input.activo());
        else{
            lockResource(id);
            if(db.queryForObject("SELECT COUNT(*) FROM dbo.OrdenesIntervencion WHERE id_recurso=? AND estado_atencion='ASIGNADA'",Integer.class,id)>0)
                throw error(HttpStatus.CONFLICT,"El recurso tiene una orden activa; no puede modificarse.");
            db.update("UPDATE dbo.RecursosPreventivos SET nombre=?,tipo=?,id_unidad=?,disponible=?,activo=? WHERE id_recurso=?",name,input.tipo(),input.unidadId(),input.disponible(),input.activo(),id);
        }
        return new Recurso(id,name,input.tipo(),input.unidadId(),input.disponible(),input.activo());
    }
    public List<Caso> cases(String subject){
        municipal(subject);
        var available=db.query("SELECT r.* FROM dbo.RecursosPreventivos r JOIN dbo.UnidadesOperativas u ON u.id_unidad=r.id_unidad WHERE r.activo=1 AND r.disponible=1 AND u.activo=1 AND NOT EXISTS(SELECT 1 FROM dbo.OrdenesIntervencion o WHERE o.id_recurso=r.id_recurso AND o.estado_atencion='ASIGNADA')",(r,n)->resource(r));
        return db.query("SELECT r.id_reporte,r.descripcion,r.estado,t.nombre FROM dbo.Reportes r JOIN dbo.TiposIncidente t ON t.id_tipo=r.id_tipo WHERE r.estado IN('VALIDADO','EN_ATENCION','ATENDIDO') ORDER BY r.id_reporte DESC",(r,n)->new Caso(r.getInt(1),r.getString(2),r.getString(3),r.getString(4),available));
    }
    private static final String ORDER_QUERY="SELECT o.*,r.descripcion,r.estado AS reporte_estado,t.nombre AS tipo_incidente,g.latitud,g.longitud,c.nombre AS recurso_nombre,u.nombre AS unidad_nombre,a.id_aviso,a.leido_utc "
        +"FROM dbo.OrdenesIntervencion o JOIN dbo.Reportes r ON r.id_reporte=o.id_reporte JOIN dbo.TiposIncidente t ON t.id_tipo=r.id_tipo JOIN dbo.UbicacionesGPS g ON g.id_ubicacion=r.id_ubicacion "
        +"JOIN dbo.RecursosPreventivos c ON c.id_recurso=o.id_recurso JOIN dbo.UnidadesOperativas u ON u.id_unidad=o.id_unidad LEFT JOIN dbo.AvisosOrden a ON a.id_orden=o.id_orden ";
    private Orden order(ResultSet r)throws SQLException{
        return new Orden(r.getInt("id_orden"),r.getInt("id_reporte"),r.getString("descripcion"),r.getString("reporte_estado"),r.getString("tipo_incidente"),r.getInt("id_recurso"),r.getString("recurso_nombre"),r.getInt("id_unidad"),r.getString("unidad_nombre"),r.getString("tipo_orden"),r.getString("instrucciones"),r.getString("estado_atencion"),r.getString("resultado"),r.getDouble("latitud"),r.getDouble("longitud"),r.getString("fecha_hora"),r.getString("fecha_cierre"),r.getInt("id_aviso"),r.getObject("leido_utc")==null?"EN_BANDEJA":"LEIDA");
    }
    public List<Orden> orders(String subject,boolean operational){
        if(operational)return db.query(ORDER_QUERY+"WHERE o.id_unidad=? ORDER BY o.id_orden DESC",(r,n)->order(r),unit(subject));
        municipal(subject);return db.query(ORDER_QUERY+"ORDER BY o.id_orden DESC",(r,n)->order(r));
    }
    private Orden get(int id){return db.query(ORDER_QUERY+"WHERE o.id_orden=?",(r,n)->order(r),id).get(0);}
    private void lockReport(int id){if(db.query("SELECT id_reporte FROM dbo.Reportes WITH(UPDLOCK,HOLDLOCK) WHERE id_reporte=?",(r,n)->r.getInt(1),id).isEmpty())throw error(HttpStatus.NOT_FOUND,"Reporte inexistente.");}
    private void lockResource(int id){if(db.query("SELECT id_recurso FROM dbo.RecursosPreventivos WITH(UPDLOCK,HOLDLOCK) WHERE id_recurso=?",(r,n)->r.getInt(1),id).isEmpty())throw error(HttpStatus.NOT_FOUND,"Recurso inexistente.");}
    @Transactional
    public Orden assign(String subject,Asignacion input){
        int reviewer=municipal(subject);UUID request=key(input.solicitudId());String instructions=text(input.instrucciones(),10,2000);
        db.queryForObject("SELECT id_usuario FROM dbo.Usuarios WITH(UPDLOCK,HOLDLOCK) WHERE id_usuario=?",Integer.class,reviewer);
        if(input.reporteId()==null||input.recursoId()==null||input.reporteId()<=0||input.recursoId()<=0)throw error(HttpStatus.BAD_REQUEST,"Reporte y recurso obligatorios.");
        lockReport(input.reporteId());
        // Un replay conserva su resultado incluso si la orden ya fue atendida.
        var previous=db.query("SELECT id_orden FROM dbo.OrdenesIntervencion WHERE id_personal=? AND solicitud_id=?",(r,n)->r.getInt(1),reviewer,request.toString());
        if(!previous.isEmpty()){
            var old=get(previous.get(0));
            if(old.reporteId()!=input.reporteId()||old.recursoId()!=input.recursoId()||!old.instrucciones().equals(instructions)||!old.tipoOrden().equals(input.tipoOrden()))throw error(HttpStatus.CONFLICT,"La solicitud ya existe con otros datos.");
            return old;
        }
        if(!"VALIDADO".equals(db.queryForObject("SELECT estado FROM dbo.Reportes WHERE id_reporte=?",String.class,input.reporteId())))throw error(HttpStatus.CONFLICT,"Solo un reporte validado puede recibir una nueva orden.");
        lockResource(input.recursoId());
        var resource=db.query("SELECT * FROM dbo.RecursosPreventivos WHERE id_recurso=?",(r,n)->resource(r),input.recursoId()).get(0);
        boolean maintenance="MANTENIMIENTO".equals(input.tipoOrden());
        if(!List.of("PATRULLAJE","MANTENIMIENTO","DERIVACION").contains(input.tipoOrden()==null?"":input.tipoOrden())||!resource.tipo().equals(maintenance?"CUADRILLA":"PATRULLA"))throw error(HttpStatus.BAD_REQUEST,"El recurso no corresponde al tipo de intervención.");
        if(!resource.activo()||!resource.disponible()||resource.unidadId()<=0||db.queryForObject("SELECT COUNT(*) FROM dbo.UnidadesOperativas WHERE id_unidad=? AND activo=1",Integer.class,resource.unidadId())!=1
                ||db.queryForObject("SELECT COUNT(*) FROM dbo.OrdenesIntervencion WHERE id_recurso=? AND estado_atencion='ASIGNADA'",Integer.class,resource.id())>0)throw error(HttpStatus.CONFLICT,"Recurso no disponible.");
        int id=db.queryForObject("INSERT dbo.OrdenesIntervencion(id_reporte,id_personal,id_recurso,id_unidad,estado_atencion,tipo_orden,instrucciones,solicitud_id) OUTPUT INSERTED.id_orden VALUES(?,?,?,?,'ASIGNADA',?,?,?)",Integer.class,input.reporteId(),reviewer,resource.id(),resource.unidadId(),input.tipoOrden(),instructions,request.toString());
        db.update("UPDATE dbo.RecursosPreventivos SET disponible=0 WHERE id_recurso=?",resource.id());
        db.update("UPDATE dbo.Reportes SET estado='EN_ATENCION' WHERE id_reporte=?",input.reporteId());
        db.update("INSERT dbo.AvisosOrden(id_orden,id_unidad) VALUES(?,?)",id,resource.unidadId());
        return get(id);
    }
    @Transactional
    public Orden close(String subject,int id,Cierre input){
        int unit=unit(subject);int actor=auth.profile(subject).idUsuario();String result=text(input.resultado(),10,2000);
        var ids=db.query("SELECT id_reporte FROM dbo.OrdenesIntervencion WHERE id_orden=? AND id_unidad=?",(r,n)->r.getInt(1),id,unit);
        if(ids.isEmpty())throw error(HttpStatus.NOT_FOUND,"Orden inexistente para tu unidad.");
        lockReport(ids.get(0));var order=get(id);
        if(!order.estadoOrden().equals("ASIGNADA")){
            if(order.estadoOrden().equals("ATENDIDA")&&result.equals(order.resultado()))return order;
            throw error(HttpStatus.CONFLICT,"La orden ya está cerrada.");
        }
        lockResource(order.recursoId());
        if(!order.estado().equals("EN_ATENCION"))throw error(HttpStatus.CONFLICT,"El reporte no está en atención.");
        db.update("UPDATE dbo.OrdenesIntervencion SET estado_atencion='ATENDIDA',resultado=?,fecha_cierre=SYSUTCDATETIME(),id_usuario_cierre=? WHERE id_orden=?",result,actor,id);
        db.update("UPDATE dbo.Reportes SET estado='ATENDIDO' WHERE id_reporte=?",order.reporteId());
        db.update("UPDATE dbo.RecursosPreventivos SET disponible=1 WHERE id_recurso=?",order.recursoId());
        return get(id);
    }
    public void read(String subject,int id){int unit=unit(subject);int actor=auth.profile(subject).idUsuario();
        if(db.update("UPDATE dbo.AvisosOrden SET leido_utc=COALESCE(leido_utc,SYSUTCDATETIME()),id_lector=COALESCE(id_lector,?) WHERE id_aviso=? AND id_unidad=?",actor,id,unit)==0)throw error(HttpStatus.NOT_FOUND,"Aviso inexistente para tu unidad.");
    }
    public record Unidad(int id,String nombre,boolean activo){}
    public record UnidadEntrada(String nombre){}
    public record Miembro(Integer usuarioId){}
    public record Recurso(int id,String nombre,String tipo,int unidadId,boolean disponible,boolean activo){}
    public record RecursoEntrada(String nombre,String tipo,Integer unidadId,Boolean disponible,Boolean activo){}
    public record Caso(int id,String descripcion,String estado,String tipoIncidenteNombre,List<Recurso> recursosDisponibles){}
    public record Asignacion(Integer reporteId,Integer recursoId,String tipoOrden,String instrucciones,String solicitudId){}
    public record Cierre(String resultado){}
    public record Orden(int id,int reporteId,String descripcion,String estado,String tipoIncidenteNombre,int recursoId,String recursoNombre,int unidadId,String unidadNombre,String tipoOrden,String instrucciones,String estadoOrden,String resultado,double latitud,double longitud,String fechaUtc,String cierreUtc,int avisoId,String estadoNotificacion){}
}
