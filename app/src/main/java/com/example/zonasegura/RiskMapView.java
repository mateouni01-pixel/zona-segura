package com.example.zonasegura;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import org.json.*;
import java.util.ArrayList;
import java.util.List;

/** Draws published geographic polygons on a local projection centered on Santa Cruz. */
final class RiskMapView extends View {
    private final List<Path> paths = new ArrayList<>();
    private final List<Integer> colors = new ArrayList<>();
    private final RectF bounds = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean thermal = true;
    RiskMapView(Context context, JSONArray zones) {
        super(context);
        StringBuilder description = new StringBuilder("Zonas de riesgo");
        for (int i=0; i<zones.length(); i++) {
            JSONObject zone=zones.optJSONObject(i);
            if(zone==null) continue;
            JSONArray ring=zone.optJSONArray("poligono");
            if(ring==null || ring.length()<3) continue;
            Path path=new Path();
            boolean valid=true;
            for(int j=0;j<ring.length();j++){
                JSONArray point=ring.optJSONArray(j);
                if(point==null){valid=false;break;}
                double lon=point.optDouble(0), lat=point.optDouble(1);
                if(!Double.isFinite(lon)||!Double.isFinite(lat)||Math.abs(lon)>180||Math.abs(lat)>90){valid=false;break;}
                float x=(float)((lon+63.18)*106000),y=(float)((-17.78-lat)*111000);
                if(j==0)path.moveTo(x,y);else path.lineTo(x,y);
            }
            if(!valid)continue;
            path.close();
            RectF rect=new RectF();path.computeBounds(rect,true);
            if(rect.isEmpty())continue;
            String level=zone.optString("nivel");
            int color;
            switch(level){case "ALTO":color=0xffaf283a;break;case "MEDIO":color=0xffa46608;break;case "BAJO":color=0xff15805f;break;default:continue;}
            bounds.union(rect);paths.add(path);colors.add(color);
            description.append(". ").append(zone.optString("nombre")).append(", ").append(level);
        }
        setContentDescription(description);
    }
    void toggle(){thermal=!thermal;invalidate();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.drawColor(0xffe9f0ed);
        if(paths.isEmpty()){paint.setColor(0xff243d37);paint.setTextSize(32);canvas.drawText("Sin polígonos válidos",20,50,paint);return;}
        Matrix project=new Matrix();
        project.setRectToRect(bounds,new RectF(16,16,getWidth()-16,getHeight()-16),Matrix.ScaleToFit.CENTER);
        for(int i=0;i<paths.size();i++){
            Path screen=new Path();paths.get(i).transform(project,screen);
            RectF rect=new RectF();screen.computeBounds(rect,true);
            int color=colors.get(i);
            paint.setStyle(Paint.Style.FILL);paint.setColor(color);
            if(thermal)paint.setShader(new RadialGradient(rect.centerX(),rect.centerY(),
                    Math.max(1,Math.max(rect.width(),rect.height())/2),color,(color&0xffffff)|0x44000000,Shader.TileMode.CLAMP));
            else paint.setAlpha(48);
            canvas.drawPath(screen,paint);paint.setShader(null);
            paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);canvas.drawPath(screen,paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }
}
