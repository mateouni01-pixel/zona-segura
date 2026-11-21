package com.example.zonasegura;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Cobertura académica: polígonos publicados de los distritos urbanos 1 al 12. */
final class TerritorioCobertura {
    private static volatile List<Distrito> districts;
    private static final double EPSILON = 1e-9;
    private static final class Distrito {
        final int id;
        final double[][][][] polygons;
        Distrito(int id, double[][][][] polygons) { this.id=id; this.polygons=polygons; }
    }

    static int distrito(Context context,double lat,double lng) throws IOException {
        if(!Double.isFinite(lat)||!Double.isFinite(lng)||Math.abs(lat)>90||Math.abs(lng)>180)return 0;
        for(Distrito district:load(context))for(double[][][] polygon:district.polygons){
            int outer=ring(polygon[0],lng,lat);
            if(outer==0)return district.id;
            if(outer<0)continue;
            boolean hole=false;
            for(int i=1;i<polygon.length;i++){
                int inner=ring(polygon[i],lng,lat);
                if(inner==0)return district.id;
                if(inner>0){hole=true;break;}
            }
            if(!hole)return district.id;
        }
        return 0;
    }

    private static List<Distrito> load(Context context) throws IOException {
        if(districts!=null)return districts;
        synchronized(TerritorioCobertura.class){
            if(districts!=null)return districts;
            try(InputStream input=context.getAssets().open("distritos-urbanos.json");ByteArrayOutputStream output=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int count;
                while((count=input.read(buffer))!=-1)output.write(buffer,0,count);
                JSONArray features=new JSONObject(output.toString("UTF-8")).getJSONArray("features");
                if(features.length()!=12)throw new IOException("La cobertura debe contener doce distritos.");
                List<Distrito> result=new ArrayList<>();boolean[] found=new boolean[13];
                for(int f=0;f<features.length();f++){
                    JSONObject feature=features.getJSONObject(f),geometry=feature.getJSONObject("geometry");
                    int id=feature.getJSONObject("properties").getInt("id");
                    if(id<1||id>12||found[id]||!"MultiPolygon".equals(geometry.getString("type")))throw new IOException("Cobertura distrital inválida.");
                    found[id]=true;JSONArray source=geometry.getJSONArray("coordinates");
                    double[][][][] polygons=new double[source.length()][][][];
                    for(int p=0;p<source.length();p++){
                        JSONArray polygon=source.getJSONArray(p);
                        if(polygon.length()==0)throw new IOException("Polígono vacío.");
                        polygons[p]=new double[polygon.length()][][];
                        for(int r=0;r<polygon.length();r++){
                            JSONArray ring=polygon.getJSONArray(r);
                            if(ring.length()<4)throw new IOException("Anillo inválido.");
                            polygons[p][r]=new double[ring.length()][2];
                            for(int v=0;v<ring.length();v++){
                                double x=ring.getJSONArray(v).getDouble(0),y=ring.getJSONArray(v).getDouble(1);
                                if(!Double.isFinite(x)||!Double.isFinite(y)||Math.abs(x)>180||Math.abs(y)>90)throw new IOException("Coordenada distrital inválida.");
                                polygons[p][r][v]=new double[]{x,y};
                            }
                        }
                    }
                    result.add(new Distrito(id,polygons));
                }
                districts=result;return result;
            }catch(org.json.JSONException exception){throw new IOException("No se pudo leer la cobertura distrital.",exception);}
        }
    }

    /** -1 fuera, 0 sobre el borde, 1 dentro; coordenadas GeoJSON longitud/latitud. */
    private static int ring(double[][] points,double x,double y){
        boolean inside=false;
        for(int i=0,j=points.length-1;i<points.length;j=i++){
            double ax=points[j][0],ay=points[j][1],bx=points[i][0],by=points[i][1];
            double length=Math.hypot(bx-ax,by-ay);
            if(length>0&&Math.abs((x-ax)*(by-ay)-(y-ay)*(bx-ax))<=EPSILON*length
                &&x>=Math.min(ax,bx)-EPSILON&&x<=Math.max(ax,bx)+EPSILON&&y>=Math.min(ay,by)-EPSILON&&y<=Math.max(ay,by)+EPSILON)return 0;
            if((ay>y)!=(by>y)&&x<(bx-ax)*(y-ay)/(by-ay)+ax)inside=!inside;
        }
        return inside?1:-1;
    }
}
