package net.clevertree.quizgate.telemetry;

import android.content.Context;
import android.util.Log;

import net.clevertree.quizgate.AppPrefs;
import net.clevertree.quizgate.core.AnonUserId;
import net.clevertree.quizgate.core.BuildFingerprint;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Pulls {@code profile.json} decisions from Intake (after batch wrote tip).
 * Safe no-op when upload flag is off or network fails.
 */
public final class RemoteSettingsPull {
    private RemoteSettingsPull() {}

    public static boolean maybePull(Context ctx) {
        if (!BuildFingerprint.TELEMETRY_UPLOAD_ENABLED) return false;
        if (ctx == null) return false;
        String uuid = AnonUserId.getOrCreate(ctx);
        try {
            URL url = new URL(ApiConfig.userProfileUrl(uuid));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                conn.setConnectTimeout(12_000);
                conn.setReadTimeout(20_000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    Log.w(BuildFingerprint.DIAG_LOG_TAG, "profile_pull status=" + code);
                    return false;
                }
                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                applyProfile(ctx, new JSONObject(sb.toString()));
                return true;
            } finally {
                conn.disconnect();
            }
        } catch (Throwable t) {
            Log.w(BuildFingerprint.DIAG_LOG_TAG, "profile_pull_failed: " + t.getMessage());
            return false;
        }
    }

    static void applyProfile(Context ctx, JSONObject profile) {
        if (profile == null) return;
        AppPrefs prefs = new AppPrefs(ctx);
        String curriculum = profile.optString("curriculum_id", null);
        if (curriculum != null && !curriculum.isEmpty()) {
            prefs.setActiveCurriculumId(curriculum);
        }
        JSONObject settings = profile.optJSONObject("settings");
        if (settings != null) {
            if (settings.has("interval_ms")) {
                prefs.setIntervalMs(settings.optLong("interval_ms", prefs.getIntervalMs()));
            }
            if (settings.has("jitter_ms")) {
                prefs.setJitterMs(settings.optLong("jitter_ms", prefs.getJitterMs()));
            }
            String perf = settings.optString("performance_preference", null);
            if (perf != null && !perf.isEmpty()) {
                prefs.setPerformanceMode(perf);
            }
        }
        prefs.setLastRemoteProfileAt(System.currentTimeMillis());
        Log.i(BuildFingerprint.DIAG_LOG_TAG, "profile_pull_applied curriculum="
                + prefs.getActiveCurriculumId());
    }
}
