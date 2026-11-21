package com.zonasegura.zona_segura_api;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class TerritorioService {
    private static final double EPSILON=1e-9;
    private final JsonNode geojson;
    private final List<Distrito> districts=new ArrayList<>();
    private record Distrito(int id,List<double[][][]> polygons){}

    public TerritorioService(){
        try(InputStream input=getClass().getResourceAsStream("/distritos-urbanos.json")){
            if(input==null)throw new IllegalStateException("Falta la cobertura distrital.");
            geojson=JsonMapper.builder().build().readTree(input);
            var ids=new HashSet<Integer>();
            for(JsonNode feature:geojson.path("features")){
                int id=feature.path("properties").path("id").asInt();
                JsonNode geometry=feature.path("geometry");
                if(id<1||id>12||!ids.add(id)||!"MultiPolygon".equals(geometry.path("type").asString()))throw new IllegalStateException("Cobertura distrital inválida.");
                List<double[][][]> polygons=new ArrayList<>();
                for(JsonNode poly:geometry.path("coordinates")){
                    if(poly.size()==0)throw new IllegalStateException("Polígono vacío.");
                    double[][][] rings=new double[poly.size()][][];
                    for(int r=0;r<poly.size();r++){
                        JsonNode ring=poly.get(r);
                        if(ring.size()<4)throw new IllegalStateException("Anillo inválido.");
                        rings[r]=new double[ring.size()][2];
                        for(int v=0;v<ring.size();v++){
                            double x=ring.get(v).get(0).asDouble(),y=ring.get(v).get(1).asDouble();
                            if(!Double.isFinite(x)||!Double.isFinite(y)||Math.abs(x)>180||Math.abs(y)>90)throw new IllegalStateException("Coordenada distrital inválida.");
                            rings[r][v]=new double[]{x,y};
                        }
                    }
                    polygons.add(rings);
                }
                districts.add(new Distrito(id,polygons));
            }
            if(ids.size()!=12)throw new IllegalStateException("La cobertura debe contener doce distritos.");
        }catch(Exception exception){throw new IllegalStateException("No se pudo cargar la cobertura distrital.",exception);}
    }

    public JsonNode cobertura(){return geojson;}

    public void require(double lat,double lng){
        if(distrito(lat,lng)==0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "El incidente debe estar dentro de los distritos urbanos 1 al 12 de Santa Cruz de la Sierra.");
    }

    public int distrito(double lat,double lng){
        if(!Double.isFinite(lat)||!Double.isFinite(lng)||Math.abs(lat)>90||Math.abs(lng)>180)return 0;
        for(Distrito district:districts)for(double[][][] polygon:district.polygons()){
            int outer=ring(polygon[0],lng,lat);
            if(outer==0)return district.id();
            if(outer<0)continue;
            boolean hole=false;
            for(int r=1;r<polygon.length;r++){
                int inner=ring(polygon[r],lng,lat);
                if(inner==0)return district.id();
                if(inner>0){hole=true;break;}
            }
            if(!hole)return district.id();
        }
        return 0;
    }

    private int ring(double[][] points,double x,double y){
        boolean inside=false;
        for(int i=0,j=points.length-1;i<points.length;j=i++){
            double ax=points[j][0],ay=points[j][1],bx=points[i][0],by=points[i][1],length=Math.hypot(bx-ax,by-ay);
            if(length>0&&Math.abs((x-ax)*(by-ay)-(y-ay)*(bx-ax))<=EPSILON*length&&x>=Math.min(ax,bx)-EPSILON&&x<=Math.max(ax,bx)+EPSILON&&y>=Math.min(ay,by)-EPSILON&&y<=Math.max(ay,by)+EPSILON)return 0;
            if((ay>y)!=(by>y)&&x<(bx-ax)*(y-ay)/(by-ay)+ax)inside=!inside;
        }
        return inside?1:-1;
    }
}
