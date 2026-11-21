package com.example.zonasegura;
import android.content.Context;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

final class ApiClient {
    static String call(Context context,String method,String path,JSONObject body)throws Exception{
        String token=SessionStore.load(context);if(token==null)throw new IOException("Inicia sesión nuevamente.");
        String base=context.getString(R.string.backend_base_url);if(base.isEmpty())throw new IOException("Configura el servidor.");
        HttpURLConnection connection=(HttpURLConnection)new URL(base+path).openConnection();
        try{
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(15000);
            connection.setRequestMethod(method);connection.setRequestProperty("Authorization","Bearer "+token);
            if(body!=null){connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream out=connection.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int code=connection.getResponseCode();InputStream stream=code>=200&&code<300?connection.getInputStream():connection.getErrorStream();
            String text="";
            if(stream!=null)try(InputStream input=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>2000000)throw new IOException("Respuesta demasiado grande.");}text=out.toString("UTF-8");
            }
            if(code<200||code>=300){String reason="El servidor respondió HTTP "+code+".";
                try{JSONObject error=new JSONObject(text);reason=error.optString("detalle",error.optString("detail",reason));}catch(Exception ignored){}
                if(code==401)reason="La sesión venció. Cierra sesión e inicia nuevamente.";
                throw new IOException(reason);
            }
            return text;
        }finally{connection.disconnect();}
    }
}
