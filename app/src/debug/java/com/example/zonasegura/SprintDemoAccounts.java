package com.example.zonasegura;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class SprintDemoAccounts {
    private final SharedPreferences data;
    private final SecureRandom random = new SecureRandom();

    SprintDemoAccounts(Context context) {
        data = context.getSharedPreferences("sprint2-demo-accounts", Context.MODE_PRIVATE);
    }

    String create(String email, String name, String password) {
        if (data.contains(email + ":name")) return null;
        String recovery = String.format(Locale.ROOT, "%08d", random.nextInt(100_000_000));
        data.edit().putString(email + ":name", name)
                .putString(email + ":password", encode(password))
                .putString(email + ":recovery", encode(recovery)).apply();
        return recovery;
    }

    String authenticate(String email, String password) {
        return matches(email + ":password", password) ? data.getString(email + ":name", null) : null;
    }

    boolean recover(String email, String code, String password) {
        if (!matches(email + ":recovery", code)) return false;
        data.edit().putString(email + ":password", encode(password)).apply();
        return true;
    }

    private String encode(String secret) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.encodeToString(salt, Base64.NO_WRAP) + ":" + hash(secret, salt);
    }

    private boolean matches(String key, String secret) {
        String stored = data.getString(key, null);
        if (stored == null) return false;
        String[] parts = stored.split(":", 2);
        String actual = hash(secret, Base64.decode(parts[0], Base64.NO_WRAP));
        return MessageDigest.isEqual(parts[1].getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private String hash(String secret, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), salt, 100_000, 256);
        try {
            byte[] bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        } finally {
            spec.clearPassword();
        }
    }
}
