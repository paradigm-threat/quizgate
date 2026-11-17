package net.clevertree.quizgate.core;

/**
 * App build identity for diagnostics. {@code TELEMETRY_UPLOAD_ENABLED} gates all
 * network upload — default OFF until a published Clevertree ingest exists.
 * Git SHA is optional ({@code GitStamp} generated at APK build time).
 */
public final class BuildFingerprint {
    /** Bumped when TelemetryPayload gains the structured {@code performance} block. */
    public static final int SCHEMA_VERSION = 2;
    public static final boolean TELEMETRY_UPLOAD_ENABLED = false;
    public static final String DIAG_LOG_TAG = "QuizGateDiag";

    private BuildFingerprint() {}

    public static String gitSha() {
        try {
            Class<?> c = Class.forName("net.clevertree.quizgate.GitStamp");
            Object v = c.getField("SHA").get(null);
            if (v instanceof String && !((String) v).isEmpty()) return (String) v;
        } catch (Throwable ignored) {
        }
        return "unknown";
    }

    public static String shortLine(int versionCode, String versionName) {
        String vn = versionName == null || versionName.isEmpty() ? "?" : versionName;
        return "vc=" + versionCode + " vn=" + vn + " sha=" + gitSha()
                + " schema=" + SCHEMA_VERSION
                + " upload=" + TELEMETRY_UPLOAD_ENABLED;
    }
}
