package com.nikita.restaurantjournal;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores the user's search settings, not Google's restaurant content. */
final class DiscoverySettings {
    private static final String KEY_ALIAS = "table_notes_places_key";

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("discovery", Context.MODE_PRIVATE);
    }

    static String apiKey(Context context) {
        SharedPreferences p = prefs(context);
        String encrypted = p.getString("key_ciphertext", "");
        if (encrypted.isEmpty()) return "";
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey(),
                    new GCMParameterSpec(128, Base64.decode(p.getString("key_iv", ""), Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return ""; // An invalidated device key requires the user to enter the API key again.
        }
    }

    static boolean hasUnreadableKey(Context context) {
        return !prefs(context).getString("key_ciphertext", "").isEmpty() && apiKey(context).isEmpty();
    }

    static final class KeyStorageException extends Exception {
        KeyStorageException(Exception cause) { super(cause); }
    }

    static void save(Context context, String key, int radiusKm, boolean daily,
                     String privacyUrl, String termsUrl) throws Exception {
        String ciphertext, iv;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey());
            ciphertext=Base64.encodeToString(cipher.doFinal(key.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
            iv=Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP);
        } catch (Exception error) { throw new KeyStorageException(error); }
        SharedPreferences.Editor editor = prefs(context).edit();
        editor.putString("key_ciphertext", ciphertext);
        editor.putString("key_iv", iv);
        editor.putInt("radius_km", radiusKm).putBoolean("daily", daily);
        editor.putString("privacy_url", privacyUrl).putString("terms_url", termsUrl);
        editor.putString("revision", UUID.randomUUID().toString());
        editor.remove("last_success").remove("result_ids").remove("new_ids").remove("last_error");
        if (!editor.commit()) throw new java.io.IOException("Settings could not be saved");
    }

    static void setArea(Context context, double latitude, double longitude) {
        prefs(context).edit().putString("latitude", Double.toString(latitude))
                .putString("longitude", Double.toString(longitude))
                .putLong("area_set", System.currentTimeMillis())
                .putString("revision", UUID.randomUUID().toString())
                .remove("result_ids").remove("new_ids").remove("last_success").remove("last_error").apply();
        DailyDiscoveryJob.schedule(context);
    }

    static boolean hasArea(Context context) {
        return prefs(context).contains("latitude") && prefs(context).contains("longitude");
    }

    static boolean configured(Context context) {
        SharedPreferences p = prefs(context);
        return !apiKey(context).isEmpty() && hasArea(context)
                && !p.getString("privacy_url", "").isEmpty() && !p.getString("terms_url", "").isEmpty();
    }

    static double latitude(Context context) { return Double.parseDouble(prefs(context).getString("latitude", "0")); }
    static double longitude(Context context) { return Double.parseDouble(prefs(context).getString("longitude", "0")); }

    static void clear(Context context) {
        prefs(context).edit().clear().apply();
        DailyDiscoveryJob.schedule(context);
        DiscoveryClient.reset();
    }

    private static SecretKey secretKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) return (SecretKey) store.getKey(KEY_ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
}
