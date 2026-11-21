package com.example.zonasegura;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SessionStore {
    private static final String PREFS = "auth_session";
    private static final String KEY_ALIAS = "zona_segura_session_key_v1";
    private static final String TOKEN = "token";
    private static final String IV = "iv";
    private static final String BACKEND = "backend";

    static void save(Context context, String token) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove("citizenId").remove("profileName").remove("profileRole")
                .putString(TOKEN, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString(BACKEND, context.getString(R.string.backend_base_url))
                .apply();
    }

    static void remember(Context context,AuthApi.Profile profile){
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putInt("citizenId",profile.citizenId)
                .putString("profileName",profile.name).putString("profileRole",profile.role).apply();
    }
    static int citizenId(Context context){
        if(load(context)==null)return 0;
        SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        return "CIUDADANO".equals(prefs.getString("profileRole",""))?prefs.getInt("citizenId",0):0;
    }
    static AuthApi.Profile offlineProfile(Context context){
        int id=citizenId(context);SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        return id>0?new AuthApi.Profile(200,prefs.getString("profileName","Ciudadano"),"CIUDADANO",id):new AuthApi.Profile(0,null,null);
    }

    static String load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!context.getString(R.string.backend_base_url).equals(prefs.getString(BACKEND, null))) {
            clear(context);
            return null;
        }
        String encrypted = prefs.getString(TOKEN, null);
        String iv = prefs.getString(IV, null);
        if (encrypted == null || iv == null) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(),
                    new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            byte[] clear = cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP));
            return new String(clear, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            clear(context);
            return null;
        }
    }

    static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        SecretKey existing = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        if (existing != null) {
            return existing;
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }

    private SessionStore() { }
}
