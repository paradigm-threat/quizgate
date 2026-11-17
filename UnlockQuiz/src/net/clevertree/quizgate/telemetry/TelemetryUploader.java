package net.clevertree.quizgate.telemetry;

import android.content.Context;
import android.util.Log;

import net.clevertree.quizgate.AppPrefs;
import net.clevertree.quizgate.core.BuildFingerprint;
import net.clevertree.quizgate.core.TelemetryPayload;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * QuizGate Intake client → {@code https://api.quizgate.app}.
 * Allowed when compile flag is on <em>or</em> remote management is enabled in prefs.
 */
public final class TelemetryUploader {
    private TelemetryUploader() {}

    public static boolean maybeUpload(Context ctx, TelemetryPayload payload) {
        if (payload == null) return false;
        boolean runtime = ctx != null && new AppPrefs(ctx).isRemoteUploadEnabled();
        if (!BuildFingerprint.uploadAllowed(runtime)) {
            return false;
        }
        return postSync(payload);
    }

    /** Legacy entry — only uploads when compile-time flag is true. */
    public static boolean maybeUpload(TelemetryPayload payload) {
        if (!BuildFingerprint.TELEMETRY_UPLOAD_ENABLED) {
            return false;
        }
        return postSync(payload);
    }

    private static boolean postSync(TelemetryPayload payload) {
        String uuid = payload.userUuid;
        if (uuid == null || uuid.isEmpty()) {
            Log.w(BuildFingerprint.DIAG_LOG_TAG, "upload skipped: missing anon uuid");
            return false;
        }
        try {
            byte[] body = payload.toJson().getBytes(StandardCharsets.UTF_8);
            URL url = new URL(ApiConfig.userSyncUrl(uuid));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                conn.setConnectTimeout(12_000);
                conn.setReadTimeout(20_000);
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setFixedLengthStreamingMode(body.length);
                OutputStream os = conn.getOutputStream();
                os.write(body);
                os.close();
                int code = conn.getResponseCode();
                boolean ok = code >= 200 && code < 300;
                Log.i(BuildFingerprint.DIAG_LOG_TAG, "intake_sync status=" + code + " ok=" + ok);
                return ok;
            } finally {
                conn.disconnect();
            }
        } catch (Throwable t) {
            Log.w(BuildFingerprint.DIAG_LOG_TAG, "intake_sync_failed: " + t.getMessage());
            return false;
        }
    }
}
