package net.clevertree.quizgate.telemetry;

import android.content.Context;
import android.util.Log;

import net.clevertree.quizgate.AppPrefs;
import net.clevertree.quizgate.core.AnonUserId;
import net.clevertree.quizgate.core.BuildFingerprint;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Registers remote AI / human admin (email → server-side hash only). */
public final class RemoteRegister {
    private RemoteRegister() {}

    public static boolean register(Context ctx, boolean aiEnabled, String parentEmail) {
        if (ctx == null) return false;
        String uuid = AnonUserId.getOrCreate(ctx);
        try {
            JSONObject body = new JSONObject();
            body.put("ai_enabled", aiEnabled);
            body.put("remote_ai", aiEnabled);
            if (parentEmail != null && parentEmail.contains("@")) {
                body.put("email", parentEmail.trim());
            }
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            URL url = new URL(ApiConfig.API_BASE + "/v1/users/" + uuid + "/remote");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                conn.setConnectTimeout(12_000);
                conn.setReadTimeout(20_000);
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setFixedLengthStreamingMode(bytes.length);
                OutputStream os = conn.getOutputStream();
                os.write(bytes);
                os.close();
                int code = conn.getResponseCode();
                boolean ok = code >= 200 && code < 300;
                Log.i(BuildFingerprint.DIAG_LOG_TAG, "remote_register status=" + code);
                if (ok) {
                    AppPrefs prefs = new AppPrefs(ctx);
                    prefs.setRemoteManagementEnabled(true);
                    prefs.setRemoteAiEnabled(aiEnabled);
                    if (parentEmail != null && parentEmail.contains("@")) {
                        prefs.setParentEmail(parentEmail.trim());
                    }
                    prefs.setLastRemoteRegisterAt(System.currentTimeMillis());
                }
                return ok;
            } finally {
                conn.disconnect();
            }
        } catch (Throwable t) {
            Log.w(BuildFingerprint.DIAG_LOG_TAG, "remote_register_failed: " + t.getMessage());
            return false;
        }
    }
}
