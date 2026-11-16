package net.clevertree.quizgate.telemetry;

import android.util.Log;

import net.clevertree.quizgate.core.BuildFingerprint;
import net.clevertree.quizgate.core.TelemetryPayload;

/**
 * Future Clevertree ingest client. Hard-gated by
 * {@link BuildFingerprint#TELEMETRY_UPLOAD_ENABLED} (default false) — no network
 * until a published server endpoint exists.
 */
public final class TelemetryUploader {
    private TelemetryUploader() {}

    /**
     * @return false always while upload is disabled; never throws for network.
     */
    public static boolean maybeUpload(TelemetryPayload payload) {
        if (!BuildFingerprint.TELEMETRY_UPLOAD_ENABLED) {
            return false;
        }
        // Intentionally unimplemented until post-publish ingest is ready.
        Log.w(BuildFingerprint.DIAG_LOG_TAG, "upload enabled but endpoint not configured");
        return false;
    }
}
