package com.example.zonasegura;

import android.content.Context;
import android.net.Uri;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

final class CommunityApi {
    static String call(Context context, boolean auth, String path, JSONObject json, Uri photo) throws Exception {
        String base = context.getString(auth ? R.string.api_base_url : R.string.reports_api_url);
        if (base.isEmpty()) throw new IOException("Configura la dirección del servidor.");
        HttpURLConnection connection = (HttpURLConnection)new URL(base + path).openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(15000);
            if (!auth) {
                String token = SessionStore.load(context);
                if (token == null) throw new IOException("Inicia sesión nuevamente.");
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (json != null || photo != null) {
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                if (photo != null) {
                    String mime = context.getContentResolver().getType(photo);
                    if (mime == null || !mime.startsWith("image/")) throw new IOException("Selecciona una imagen.");
                    connection.setRequestProperty("Content-Type", mime);
                    connection.setChunkedStreamingMode(8192);
                    try (InputStream in = context.getContentResolver().openInputStream(photo);
                         OutputStream out = connection.getOutputStream()) {
                        if (in == null) throw new IOException("No se pudo abrir la imagen.");
                        copy(in, out);
                    }
                } else {
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    try (OutputStream out = connection.getOutputStream()) {
                        out.write(json.toString().getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
            int code = connection.getResponseCode();
            if (code == 401 && !auth) {
                SessionStore.clear(context);
                new android.os.Handler(context.getMainLooper()).post(() ->
                        context.startActivity(new android.content.Intent(context, MainActivity.class)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)));
            }
            if (code < 200 || code >= 300) throw new IOException("El servidor respondió HTTP " + code + ".");
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                copy(in, out);
                return out.toString("UTF-8");
            }
        } finally { connection.disconnect(); }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
    }
}
