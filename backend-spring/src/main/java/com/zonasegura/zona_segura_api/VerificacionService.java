package com.zonasegura.zona_segura_api;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VerificacionService {
    public static final int CONFIRMACIONES_REQUERIDAS = 3;
    public static final int RADIO_METROS = 500;
    private final JdbcTemplate jdbc;
    private final AuthService auth;
    private static final String QUERY = "SELECT r.id_reporte,r.id_ciudadano,r.descripcion,r.estado,t.nombre AS tipo,"
            + "u.latitud,u.longitud,"
            + "(SELECT COUNT(*) FROM dbo.ValidacionesComunitarias v WHERE v.id_reporte=r.id_reporte AND v.tipo_voto=1) AS positivos,"
            + "(SELECT COUNT(*) FROM dbo.ValidacionesComunitarias v WHERE v.id_reporte=r.id_reporte AND v.tipo_voto=0) AS negativos,"
            + "(SELECT COUNT(*) FROM dbo.VerificacionesInstitucionales i WHERE i.id_reporte=r.id_reporte) AS institucional "
            + "FROM dbo.Reportes r JOIN dbo.UbicacionesGPS u ON u.id_ubicacion=r.id_ubicacion "
            + "JOIN dbo.TiposIncidente t ON t.id_tipo=r.id_tipo ";

    public VerificacionService(JdbcTemplate jdbc, AuthService auth) { this.jdbc=jdbc;this.auth=auth; }

    private int citizen(String subject) {
        var profile=auth.profile(subject);
        if(!profile.roles().contains("CIUDADANO") || profile.ciudadanoId()<=0)throw error(HttpStatus.FORBIDDEN,"Se requiere una cuenta ciudadana activa.");
        return profile.ciudadanoId();
    }

    private int municipal(String subject) {
        var profile=auth.profile(subject);
        if(!profile.roles().contains("PERSONAL_ALCALDIA") || jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.PersonalAlcaldia WHERE id_usuario=?",Integer.class,profile.idUsuario())!=1)
            throw error(HttpStatus.FORBIDDEN,"Se requiere personal autorizado de Alcaldía.");
        return profile.idUsuario();
    }

    public List<Detalle> nearby(String subject,Double lat,Double lng,int radio) {
        int voter=citizen(subject);
        coordinates(lat,lng);
        if(radio!=RADIO_METROS)throw error(HttpStatus.BAD_REQUEST,"El radio de validación es 500 metros.");
        // Prefiltro geográfico conservador; la distancia final se calcula con Haversine.
        return jdbc.query(QUERY+"WHERE r.estado='PENDIENTE_VALIDACION' AND r.id_ciudadano<>? "
                + "AND u.latitud BETWEEN ? AND ? ORDER BY r.id_reporte DESC",
                (rs,row)->data(rs),voter,lat-0.005,lat+0.005).stream()
                .filter(report->distance(lat,lng,report.lat(),report.lng())<=RADIO_METROS)
                .map(report->detail(report,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.ValidacionesComunitarias WHERE id_reporte=? AND id_ciudadano=?",
                        Integer.class,report.id(),voter)>0,Math.round(distance(lat,lng,report.lat(),report.lng())),false)).toList();
    }

    @Transactional
    public Detalle vote(String subject,int id,Voto input) {
        int voter=citizen(subject);
        if(input==null || input.confirma()==null)throw error(HttpStatus.BAD_REQUEST,"Indica si confirmas el incidente.");
        coordinates(input.latitud(),input.longitud());
        lock(id);
        Datos report=find(id);
        if(report.author()==voter)throw error(HttpStatus.FORBIDDEN,"No puedes votar tu propio reporte.");
        if(!report.state().equals("PENDIENTE_VALIDACION"))throw error(HttpStatus.CONFLICT,"El reporte ya fue resuelto para validación.");
        if(distance(input.latitud(),input.longitud(),report.lat(),report.lng())>RADIO_METROS)
            throw error(HttpStatus.FORBIDDEN,"Debes estar a un máximo de 500 metros del incidente.");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.ValidacionesComunitarias WHERE id_reporte=? AND id_ciudadano=?",Integer.class,id,voter)>0)
            throw error(HttpStatus.CONFLICT,"Ya registraste un voto para este reporte.");
        jdbc.update("INSERT dbo.ValidacionesComunitarias(id_reporte,id_ciudadano,tipo_voto) VALUES(?,?,?)",id,voter,input.confirma());
        Datos updated=find(id);
        double index=(double)updated.positive()/(updated.positive()+updated.negative());
        jdbc.update("UPDATE dbo.Reportes SET indice_veracidad=?,estado=? WHERE id_reporte=?",index,
                updated.positive()>=CONFIRMACIONES_REQUERIDAS?"VALIDADO":"PENDIENTE_VALIDACION",id);
        return detail(find(id),true,null,false);
    }

    public List<Detalle> municipalReports(String subject) {
        municipal(subject);
        return jdbc.query(QUERY+"WHERE r.estado IN ('PENDIENTE_VALIDACION','VALIDADO') "
                + "AND NOT EXISTS(SELECT 1 FROM dbo.VerificacionesInstitucionales i WHERE i.id_reporte=r.id_reporte) ORDER BY r.id_reporte DESC",
                (rs,row)->detail(data(rs),false,null,true));
    }

    @Transactional
    public Detalle verify(String subject,int id,Verificacion input) {
        int reviewer=municipal(subject);
        String reason=input==null || input.motivo()==null?"":input.motivo().strip();
        if(input==null || input.confirma()==null || reason.length()<10 || reason.length()>2000)
            throw error(HttpStatus.BAD_REQUEST,"Registra el resultado y la comprobación realizada, entre 10 y 2000 caracteres.");
        lock(id);
        Datos report=find(id);
        if(report.author()==reviewer)throw error(HttpStatus.FORBIDDEN,"No puedes verificar tu propio reporte.");
        if(!List.of("PENDIENTE_VALIDACION","VALIDADO").contains(report.state()))
            throw error(HttpStatus.CONFLICT,"El reporte ya no admite verificación.");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.VerificacionesInstitucionales WHERE id_reporte=?",Integer.class,id)>0)
            throw error(HttpStatus.CONFLICT,"El reporte ya tiene una verificación institucional.");
        jdbc.update("INSERT dbo.VerificacionesInstitucionales(id_reporte,id_personal,confirma,motivo) VALUES(?,?,?,?)",id,reviewer,input.confirma(),reason);
        jdbc.update("UPDATE dbo.Reportes SET estado=? WHERE id_reporte=?",input.confirma()?"VALIDADO":"RECHAZADO",id);
        return detail(find(id),false,null,true);
    }

    private void lock(int id) {
        if(jdbc.query("SELECT id_reporte FROM dbo.Reportes WITH (UPDLOCK,HOLDLOCK) WHERE id_reporte=?",(rs,row)->rs.getInt(1),id).isEmpty())
            throw error(HttpStatus.NOT_FOUND,"Reporte inexistente.");
    }
    private Datos find(int id) { return jdbc.query(QUERY+"WHERE r.id_reporte=?",(rs,row)->data(rs),id).get(0); }
    private Datos data(ResultSet rs)throws SQLException {
        return new Datos(rs.getInt("id_reporte"),rs.getInt("id_ciudadano"),rs.getString("descripcion"),rs.getString("estado"),rs.getString("tipo"),
                rs.getDouble("latitud"),rs.getDouble("longitud"),rs.getInt("positivos"),rs.getInt("negativos"),rs.getInt("institucional")>0);
    }
    private Detalle detail(Datos r,boolean voted,Long distance,boolean institutional) {
        return new Detalle(r.id(),r.description(),r.state(),r.type(),r.positive(),r.negative(),CONFIRMACIONES_REQUERIDAS,
                r.institutional()?"INSTITUCIONAL":r.positive()>=3?"COMUNITARIA":null,voted,distance,
                institutional?r.lat():null,institutional?r.lng():null);
    }
    static double distance(double lat,double lng,double targetLat,double targetLng) {
        double a=Math.pow(Math.sin(Math.toRadians(targetLat-lat)/2),2)+Math.cos(Math.toRadians(lat))*Math.cos(Math.toRadians(targetLat))*Math.pow(Math.sin(Math.toRadians(targetLng-lng)/2),2);
        return 6371000*2*Math.atan2(Math.sqrt(Math.min(1,a)),Math.sqrt(Math.max(0,1-a)));
    }
    private void coordinates(Double lat,Double lng) {
        if(lat==null || lng==null || !Double.isFinite(lat) || !Double.isFinite(lng) || Math.abs(lat)>90 || Math.abs(lng)>180)
            throw error(HttpStatus.BAD_REQUEST,"Coordenadas obligatorias y dentro de rango.");
    }
    private ResponseStatusException error(HttpStatus code,String message) { return new ResponseStatusException(code,message); }
    private record Datos(int id,int author,String description,String state,String type,double lat,double lng,int positive,int negative,boolean institutional){}
    public record Voto(Boolean confirma,Double latitud,Double longitud){}
    public record Verificacion(Boolean confirma,String motivo){}
    public record Detalle(int id,String descripcion,String estado,String tipoIncidenteNombre,int confirmaciones,int votosNegativos,int confirmacionesRequeridas,
                          String origenValidacion,boolean yaVoto,Long distanciaMetros,Double latitud,Double longitud){}
}
