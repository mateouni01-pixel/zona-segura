package com.example.zonasegura;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.json.*;

public final class ReportSyncWorker extends Worker {
    public ReportSyncWorker(@NonNull Context context,@NonNull WorkerParameters parameters){super(context,parameters);}
    static void schedule(Context context,String id){
        OneTimeWorkRequest work=new OneTimeWorkRequest.Builder(ReportSyncWorker.class)
                .addTag("report-sync")
                .setInputData(new Data.Builder().putString("id",id).build())
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build();
        WorkManager.getInstance(context).enqueueUniqueWork("report-sync-"+id,ExistingWorkPolicy.KEEP,work);
    }
    static void resume(Context context,boolean retryErrors){
        int owner=SessionStore.citizenId(context);if(owner<=0)return;
        PendingReports db=PendingReports.get(context);
        for(PendingReports.Item item:db.list(context.getString(R.string.backend_base_url),owner)){
            if(item.state.equals("ERROR")&&!retryErrors)continue;
            if(retryErrors)db.state(item.id,"PENDIENTE","");schedule(context,item.id);
        }
    }
    @NonNull @Override public Result doWork(){
        Context context=getApplicationContext();PendingReports db=PendingReports.get(context);
        String id=getInputData().getString("id");if(id==null)return Result.failure();
        PendingReports.Item item=db.find(id);if(item==null||item.state.equals("SINCRONIZADO")||item.state.equals("ERROR"))return Result.success();
        String token=SessionStore.load(context);
        if(token==null || item.owner!=SessionStore.citizenId(context) || !item.backend.equals(context.getString(R.string.backend_base_url))){
            db.state(id,"ESPERA_SESION","Inicia sesión con la cuenta que creó el reporte para enviarlo.");return Result.success();
        }
        HttpURLConnection connection=null;
        try{
            db.attempt(id);
            connection=(HttpURLConnection)new URL(item.backend+"/api/reportes").openConnection();
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(15000);
            connection.setRequestMethod("POST");connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
            connection.setRequestProperty("Authorization","Bearer "+token);connection.setRequestProperty("Idempotency-Key",id);
            try(OutputStream out=connection.getOutputStream()){out.write(item.payload.getBytes(StandardCharsets.UTF_8));}
            int code=connection.getResponseCode();
            if(code>=200 && code<300){
                try(InputStream input=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1)out.write(buffer,0,n);
                    JSONObject report=new JSONObject(out.toString("UTF-8"));
                    if(report.optInt("id")<=0 || report.optInt("ciudadanoId")!=item.owner)throw new IOException("Respuesta de autor inválida.");
                    db.complete(item,report);return Result.success();
                }
            }
            if(code==401){db.state(id,"ESPERA_SESION","La sesión venció. Vuelve a iniciar sesión para sincronizar.");return Result.success();}
            if(code==408 || code==429 || code>=500){db.state(id,"PENDIENTE","Servidor temporalmente no disponible. Se reintentará.");return Result.retry();}
            db.state(id,"ERROR","El servidor rechazó el envío (HTTP "+code+"). Revisa tu cuenta o el tipo de incidente y reintenta desde Mis reportes.");return Result.success();
        }catch(Exception e){db.state(id,"PENDIENTE","Sin confirmación del servidor. Se reintentará con el mismo identificador.");return Result.retry();}
        finally{if(connection!=null)connection.disconnect();}
    }
}
