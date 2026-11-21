package com.zonasegura.zona_segura_api;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PanelController {
    private final JdbcTemplate db;private final AuthService auth;
    public PanelController(JdbcTemplate db,AuthService auth){this.db=db;this.auth=auth;}
    @GetMapping("/api/alcaldia/panel")
    public Resumen panel(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) String desde,@RequestParam(required=false) String hasta,
        @RequestParam(required=false) Integer tipoIncidenteId,@RequestParam(required=false) String estado,@RequestParam(required=false) Double veracidadMinima,
        @RequestParam(required=false) Double sur,@RequestParam(required=false) Double oeste,
        @RequestParam(required=false) Double norte,@RequestParam(required=false) Double este){
        var user=auth.profile(jwt.getSubject());
        if(!user.roles().contains("PERSONAL_ALCALDIA")||db.queryForObject("SELECT COUNT(*) FROM dbo.PersonalAlcaldia WHERE id_usuario=?",Integer.class,user.idUsuario())!=1)throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        LocalDate start,end;try{start=desde==null?LocalDate.now(ZoneOffset.UTC).minusDays(6):LocalDate.parse(desde);end=hasta==null?LocalDate.now(ZoneOffset.UTC):LocalDate.parse(hasta);}catch(Exception e){throw bad();}
        if(start.getYear()<1||end.getYear()>9999||end.equals(LocalDate.of(9999,12,31))||start.isAfter(end)||java.time.temporal.ChronoUnit.DAYS.between(start,end)>365||tipoIncidenteId!=null&&tipoIncidenteId<=0||veracidadMinima!=null&&(!Double.isFinite(veracidadMinima)||veracidadMinima<0||veracidadMinima>1))throw bad();
        if(estado!=null&&!List.of("PENDIENTE_VALIDACION","VALIDADO","RECHAZADO","EN_ATENCION","ATENDIDO").contains(estado))throw bad();
        boolean viewport=sur!=null||oeste!=null||norte!=null||este!=null;
        if(viewport&&(sur==null||oeste==null||norte==null||este==null||!Double.isFinite(sur)||!Double.isFinite(oeste)||!Double.isFinite(norte)||!Double.isFinite(este)
                ||sur < -90||norte > 90||oeste < -180||este > 180||sur>=norte||oeste>=este))throw bad();
        String sql="SELECT r.id_reporte,r.descripcion,r.estado,r.fecha_hora,r.indice_veracidad,t.nombre,g.latitud,g.longitud FROM dbo.Reportes r JOIN dbo.TiposIncidente t ON t.id_tipo=r.id_tipo JOIN dbo.UbicacionesGPS g ON g.id_ubicacion=r.id_ubicacion WHERE r.fecha_hora>=? AND r.fecha_hora<?";
        List<Object> args=new ArrayList<>(List.of(Timestamp.valueOf(start.atStartOfDay()),Timestamp.valueOf(end.plusDays(1).atStartOfDay())));
        if(tipoIncidenteId!=null){sql+=" AND r.id_tipo=?";args.add(tipoIncidenteId);}
        if(estado!=null){sql+=" AND r.estado=?";args.add(estado);}
        if(veracidadMinima!=null){sql+=" AND r.indice_veracidad>=?";args.add(veracidadMinima);}
        if(viewport){
            // Consulta celdas enteras para que el color no cambie al mover unos metros el mapa.
            // El listado y los indicadores se recortan después al rectángulo visible exacto.
            sql+=" AND g.latitud>=? AND g.latitud<? AND g.longitud>=? AND g.longitud<?";
            args.add(Math.floor(sur*200)/200);args.add((Math.floor(norte*200)+1)/200);
            args.add(Math.floor(oeste*200)/200);args.add((Math.floor(este*200)+1)/200);
        }
        var rows=db.query(sql+" ORDER BY r.id_reporte DESC",(r,n)->new Fila(r.getInt(1),r.getString(2),r.getString(3),r.getTimestamp(4).toLocalDateTime().atOffset(ZoneOffset.UTC),r.getDouble(5),r.getString(6),r.getDouble(7),r.getDouble(8)),args.toArray());
        Map<String,List<Fila>> groups=new LinkedHashMap<>();
        for(var row:rows)if(List.of("VALIDADO","EN_ATENCION","ATENDIDO").contains(row.estado())){int x=(int)Math.floor(row.longitud()*200),y=(int)Math.floor(row.latitud()*200);groups.computeIfAbsent(x+":"+y,k->new ArrayList<>()).add(row);}
        List<Zona> zones=new ArrayList<>();for(var entry:groups.entrySet()){String[] xy=entry.getKey().split(":");int x=Integer.parseInt(xy[0]),y=Integer.parseInt(xy[1]),count=entry.getValue().size();double w=x/200.0,s=y/200.0,e=(x+1)/200.0,n=(y+1)/200.0;zones.add(new Zona("zona-"+x+"-"+y,count>=5?"ALTO":count>=3?"MEDIO":"BAJO",count,new double[][]{{w,s},{e,s},{e,n},{w,n},{w,s}}));}
        List<Fila> visible=viewport?rows.stream().filter(r->r.latitud()>=sur&&r.latitud()<=norte&&r.longitud()>=oeste&&r.longitud()<=este).toList():rows;
        var indicators=new Indicadores(visible.size(),count(visible,"PENDIENTE_VALIDACION"),count(visible,"VALIDADO"),count(visible,"EN_ATENCION"),count(visible,"ATENDIDO"),count(visible,"RECHAZADO"),zones.stream().filter(z->z.nivel().equals("ALTO")).count());
        return new Resumen(visible,zones,start.toString(),end.toString(),indicators);
    }
    private long count(List<Fila> rows,String state){return rows.stream().filter(r->r.estado().equals(state)).count();}
    private ResponseStatusException bad(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Revisa fechas (máximo 366 días), estado, categoría, índice entre 0 y 1 y los cuatro límites geográficos WGS84 ordenados.");}
    public record Fila(int id,String descripcion,String estado,OffsetDateTime fechaRegistroUtc,double indiceVeracidad,String tipoIncidenteNombre,double latitud,double longitud){}
    public record Zona(String id,String nivel,int cantidad,double[][] poligono){}
    public record Indicadores(long total,long pendientes,long validados,long enAtencion,long atendidos,long rechazados,long zonasAltas){}
    public record Resumen(List<Fila> reportes,List<Zona> zonas,String desde,String hasta,Indicadores indicadores){}
}
