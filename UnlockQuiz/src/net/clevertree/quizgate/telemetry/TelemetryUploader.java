package net.clevertree.quizgate.telemetry;

import android.content.Context;
import android.util.Log;

import net.clevertree.quizgate.AppPrefs;
import net.clevertree.quizgate.core.BuildFingerprint;
import net.clevertree.quizgate.core.JsonUtil;
import net.clevertree.quizgate.core.StatsStore;
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

    /**
     * Upsert one day rollup ({@code day} + {@code today} counts). Last-write-wins
     * on the server for that uuid+day. Used by on-open day-batch backfill.
     */
    public static boolean uploadDayBatch(Context ctx, String userUuid, String dayKey,
                                         StatsStore.DayStats stats, String curriculumId) {
        return uploadDayBatch(ctx, userUuid, dayKey, stats, curriculumId, null);
    }

    public static boolean uploadDayBatch(Context ctx, String userUuid, String dayKey,
                                         StatsStore.DayStats stats, String curriculumId,
                                         java.util.Map<String, StatsStore.QuestionStats> byQuestion) {
        if (ctx == null) return false;
        boolean runtime = new AppPrefs(ctx).isRemoteUploadEnabled();
        if (!BuildFingerprint.uploadAllowed(runtime)) return false;
        String uuid = userUuid == null ? "" : userUuid.trim();
        String day = StatsStore.canonicalDayKey(dayKey);
        if (uuid.isEmpty() || day.isEmpty()) {
            Log.w(BuildFingerprint.DIAG_LOG_TAG, "day_batch skipped: missing uuid/day");
            return false;
        }
        StatsStore.DayStats d = stats != null ? stats : new StatsStore.DayStats();
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"user_uuid\":\"").append(JsonUtil.esc(uuid)).append('"');
        sb.append(",\"day\":\"").append(JsonUtil.esc(day)).append('"');
        if (curriculumId != null && !curriculumId.isEmpty()) {
            sb.append(",\"curriculum_id\":\"").append(JsonUtil.esc(curriculumId)).append('"');
        }
        sb.append(",\"today\":{");
        sb.append("\"asked\":").append(d.questionsAsked);
        sb.append(",\"right\":").append(d.answeredRight);
        sb.append(",\"wrong\":").append(d.answeredWrong);
        sb.append(",\"skipped\":").append(d.skipped);
        sb.append(",\"asked_lock\":").append(d.askedLock);
        sb.append(",\"right_lock\":").append(d.rightLock);
        sb.append(",\"wrong_lock\":").append(d.wrongLock);
        sb.append(",\"skipped_lock\":").append(d.skippedLock);
        sb.append(",\"asked_play\":").append(d.askedPlay);
        sb.append(",\"right_play\":").append(d.rightPlay);
        sb.append(",\"wrong_play\":").append(d.wrongPlay);
        sb.append(",\"skipped_play\":").append(d.skippedPlay);
        sb.append('}');
        if (byQuestion != null && !byQuestion.isEmpty()) {
            sb.append(",\"by_question\":{");
            boolean first = true;
            int n = 0;
            for (java.util.Map.Entry<String, StatsStore.QuestionStats> e : byQuestion.entrySet()) {
                if (n >= 80) break;
                String qid = e.getKey();
                if (qid == null || qid.isEmpty()) continue;
                StatsStore.QuestionStats q = e.getValue();
                if (q == null) continue;
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(JsonUtil.esc(qid)).append("\":{");
                sb.append("\"shown\":").append(q.shown);
                sb.append(",\"correct\":").append(q.correct);
                sb.append(",\"wrong\":").append(q.wrong);
                sb.append(",\"skipped\":").append(q.skipped);
                sb.append('}');
                n++;
            }
            sb.append('}');
        }
        sb.append('}');
        return postJson(ApiConfig.userSyncUrl(uuid), sb.toString(), "day_batch day=" + day);
    }

    private static boolean postSync(TelemetryPayload payload) {
        String uuid = payload.userUuid;
        if (uuid == null || uuid.isEmpty()) {
            Log.w(BuildFingerprint.DIAG_LOG_TAG, "upload skipped: missing anon uuid");
            return false;
        }
        return postJson(ApiConfig.userSyncUrl(uuid), payload.toJson(), "intake_sync");
    }

    private static boolean postJson(String urlStr, String json, String logLabel) {
        try {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            URL url = new URL(urlStr);
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
                Log.i(BuildFingerprint.DIAG_LOG_TAG, logLabel + " status=" + code + " ok=" + ok);
                return ok;
            } finally {
                conn.disconnect();
            }
        } catch (Throwable t) {
            Log.w(BuildFingerprint.DIAG_LOG_TAG, logLabel + "_failed: " + t.getMessage());
            return false;
        }
    }
}
