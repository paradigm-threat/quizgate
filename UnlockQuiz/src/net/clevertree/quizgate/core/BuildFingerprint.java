package net.clevertree.quizgate.core;

/**
 * App build identity for diagnostics. {@code TELEMETRY_UPLOAD_ENABLED} gates all
 * network upload — default OFF until a published {@code api.quizgate.app} Intake
 * is reviewed for Play Data safety / privacy.
 * Git SHA is optional ({@code GitStamp} generated at APK build time).
 */
public final class BuildFingerprint {
    /** Bumped when TelemetryPayload includes anon {@code user_uuid} for Intake. */
    public static final int SCHEMA_VERSION = 3;
    /**
     * Compile-time kill switch. Runtime remote management uses
     * {@link net.clevertree.quizgate.AppPrefs#isRemoteUploadEnabled()} which can
     * enable sync while this remains false for store builds until privacy review.
     * When true, upload is always allowed; when false, prefs must opt in.
     */
    public static final boolean TELEMETRY_UPLOAD_ENABLED = false;
    public static final String DIAG_LOG_TAG = "QuizGateDiag";
    public static final String API_BASE = "https://api.quizgate.app";
    public static final String ADMIN_URL = "https://admin.quizgate.app";

    private BuildFingerprint() {}

    /** True if compile flag or runtime remote-management opt-in. */
    public static boolean uploadAllowed(boolean runtimeRemoteEnabled) {
        return TELEMETRY_UPLOAD_ENABLED || runtimeRemoteEnabled;
    }

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
