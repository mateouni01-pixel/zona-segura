package com.example.zonasegura;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class AuthApi {
    static final class Profile {
        final int status;
        final String name;
        final String role;

        Profile(int status, String name, String role) {
            this.status = status;
            this.name = name;
            this.role = role;
        }
    }

    static final class MunicipalProfile {
        final int status;
        final String name;
        final String cargo;
        final String accessRole;

        MunicipalProfile(int status, String name, String cargo, String accessRole) {
            this.status = status;
            this.name = name;
            this.cargo = cargo;
            this.accessRole = accessRole;
        }
    }

    static final class Result {
        final int status;
        final String token;
        final String role;

        Result(int status, String token, String role) {
            this.status = status;
            this.token = token;
            this.role = role;
        }
    }

    static Result login(String baseUrl, String email, String password) throws IOException {
        if (baseUrl == null || baseUrl.isEmpty()) {
            return new Result(-1, null, null);
        }
        HttpURLConnection connection = (HttpURLConnection)
                new URL(baseUrl + "/api/auth/login").openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setDoOutput(true);
        try {
            JSONObject body = new JSONObject();
            body.put("correo", email);
            body.put("password", password);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            if (status != 200) {
                return new Result(status, null, null);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    bytes.write(buffer, 0, count);
                }
            }
            String json = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            JSONObject response = new JSONObject(json);
            JSONArray roles = response.optJSONArray("roles");
            String role = roles != null && roles.length() > 0 ? roles.optString(0) : "";
            String token = response.optString("token", "");
            if (token.isEmpty()) {
                return new Result(500, null, null);
            }
            return new Result(200, token, role);
        } catch (org.json.JSONException exception) {
            throw new IOException("Respuesta inválida del servidor.", exception);
        } finally {
            connection.disconnect();
        }
    }

    static Profile profile(String baseUrl, String token) throws IOException {
        if (baseUrl == null || baseUrl.isEmpty()) {
            return new Profile(-1, null, null);
        }
        HttpURLConnection connection = (HttpURLConnection)
                new URL(baseUrl + "/api/auth/me").openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        try {
            int status = connection.getResponseCode();
            if (status != 200) {
                return new Profile(status, null, null);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    bytes.write(buffer, 0, count);
                }
            }
            JSONObject response = new JSONObject(
                    new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            JSONArray roles = response.optJSONArray("roles");
            String role = roles != null && roles.length() > 0 ? roles.optString(0) : "";
            if (roles != null) {
                for (int i = 0; i < roles.length(); i++) {
                    if ("PERSONAL_ALCALDIA".equals(roles.optString(i))) {
                        role = "PERSONAL_ALCALDIA";
                        break;
                    }
                }
            }
            return new Profile(200, response.optString("nombre", ""), role);
        } catch (org.json.JSONException exception) {
            throw new IOException("Respuesta inválida del servidor.", exception);
        } finally {
            connection.disconnect();
        }
    }

    static MunicipalProfile municipalProfile(String baseUrl, String token) throws IOException {
        if (baseUrl == null || baseUrl.isEmpty()) {
            return new MunicipalProfile(-1, null, null, null);
        }
        HttpURLConnection connection = (HttpURLConnection)
                new URL(baseUrl + "/api/alcaldia/perfil").openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        try {
            int status = connection.getResponseCode();
            if (status != 200) {
                return new MunicipalProfile(status, null, null, null);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    bytes.write(buffer, 0, count);
                }
            }
            JSONObject response = new JSONObject(
                    new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            return new MunicipalProfile(200, response.optString("nombre", ""),
                    response.optString("cargo", ""), response.optString("rolAcceso", ""));
        } catch (org.json.JSONException exception) {
            throw new IOException("Respuesta inválida del servidor.", exception);
        } finally {
            connection.disconnect();
        }
    }

    private AuthApi() { }
}
