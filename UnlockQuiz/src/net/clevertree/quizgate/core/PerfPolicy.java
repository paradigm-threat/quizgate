package net.clevertree.quizgate.core;

/**
 * Pure performance-profile policy (no Android deps) for unit tests and shared knobs.
 * Runtime prefs live in {@code AppPrefs}/{@code PerformanceMode}.
 */
public final class PerfPolicy {
    public static final String BALANCED = "balanced";
    public static final String SLIM = "slim";

    public static final long FGS_STOP_BALANCED_MS = 8_000L;
    public static final long FGS_STOP_SLIM_MS = 2_500L;
    public static final int COMPACT_EVERY_BALANCED = 32;
    public static final int COMPACT_EVERY_SLIM = 10_000;

    private PerfPolicy() {}

    public static String normalize(String raw) {
        if (raw != null && SLIM.equalsIgnoreCase(raw.trim())) return SLIM;
        return BALANCED;
    }

    public static boolean isSlim(String mode) {
        return SLIM.equals(normalize(mode));
    }

    public static long fgsStopAfterMs(boolean slim) {
        return slim ? FGS_STOP_SLIM_MS : FGS_STOP_BALANCED_MS;
    }

    public static int compactEvery(boolean slim) {
        return slim ? COMPACT_EVERY_SLIM : COMPACT_EVERY_BALANCED;
    }

    /** Schedule JSONL kinds retained when slim (drops launch_attempt chatter). */
    public static boolean keepScheduleKind(boolean slim, String kind) {
        if (!slim) return true;
        if (kind == null || kind.isEmpty()) return false;
        switch (kind) {
            case "alarm_scheduled":
            case "alarm_fired":
            case "skip_fire":
            case "arm":
            case "bal_blocked":
            case "launch_ok":
            case "quiz_started":
            case "quiz_ended":
                return true;
            default:
                return false;
        }
    }
}
