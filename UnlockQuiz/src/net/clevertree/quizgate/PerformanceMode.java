package net.clevertree.quizgate;

import android.content.Context;

import net.clevertree.quizgate.core.PerfPolicy;

/**
 * Battery / low-end profile. {@link #BALANCED} is the default tablet UX;
 * {@link #SLIM} trims optional work while keeping exact alarms + boot re-arm.
 * Pure policy constants live in {@link PerfPolicy}.
 */
public final class PerformanceMode {
    public static final String BALANCED = PerfPolicy.BALANCED;
    public static final String SLIM = PerfPolicy.SLIM;

    /** FGS hold after FSI send — long enough for BAL handoff. */
    public static final long FGS_STOP_BALANCED_MS = PerfPolicy.FGS_STOP_BALANCED_MS;
    /** Slim: stop sooner once the quiz activity is up. */
    public static final long FGS_STOP_SLIM_MS = PerfPolicy.FGS_STOP_SLIM_MS;

    public static final long SUCCESS_HOLD_BALANCED_MS = 2_000L;
    public static final long SUCCESS_HOLD_SLIM_MS = 700L;

    /** BoundedJsonl periodic compact interval (appends). */
    public static final int COMPACT_EVERY_BALANCED = PerfPolicy.COMPACT_EVERY_BALANCED;
    /** Slim defers mid-session compact; still compact on over-cap + session end. */
    public static final int COMPACT_EVERY_SLIM = PerfPolicy.COMPACT_EVERY_SLIM;

    private PerformanceMode() {}

    public static String normalize(String raw) {
        return PerfPolicy.normalize(raw);
    }

    public static boolean isSlim(Context ctx) {
        return PerfPolicy.isSlim(new AppPrefs(ctx).getPerformanceMode());
    }

    public static boolean isSlim(AppPrefs prefs) {
        return prefs != null && PerfPolicy.isSlim(prefs.getPerformanceMode());
    }

    public static long fgsStopAfterMs(Context ctx) {
        return PerfPolicy.fgsStopAfterMs(isSlim(ctx));
    }

    public static long successHoldMs(AppPrefs prefs) {
        return isSlim(prefs) ? SUCCESS_HOLD_SLIM_MS : SUCCESS_HOLD_BALANCED_MS;
    }

    public static int compactEvery(Context ctx) {
        return PerfPolicy.compactEvery(isSlim(ctx));
    }

    /** Return-to-app via Usage Access — skipped in slim to avoid usage-stats scans. */
    public static boolean allowReturnToApp(AppPrefs prefs) {
        return !isSlim(prefs);
    }

    /** Balloon / confetti / photo-flash party — off in slim. */
    public static boolean allowRichMotion(AppPrefs prefs) {
        return !isSlim(prefs);
    }

    /** Per-answer / media breadcrumbs — off in slim (schedule fire/arm/end stay). */
    public static boolean allowOptionalBreadcrumbs(AppPrefs prefs) {
        return !isSlim(prefs);
    }

    /** Mirror every schedule event into breadcrumbs.jsonl — balanced only. */
    public static boolean mirrorScheduleToBreadcrumbs(AppPrefs prefs) {
        return !isSlim(prefs);
    }

    /** Mid-quiz widget refresh — slim waits for session end. */
    public static boolean refreshWidgetOnAnswer(AppPrefs prefs) {
        return !isSlim(prefs);
    }

    /** Verbose QuizGate logcat lines (Info) for optional paths. */
    public static boolean verboseLog(AppPrefs prefs) {
        return !isSlim(prefs);
    }

    /** Setup / Parents countdown poll while that screen is open. */
    public static long setupCountdownTickMs(AppPrefs prefs) {
        return isSlim(prefs) ? 60_000L : 30_000L;
    }

    /**
     * Schedule JSONL kinds kept in slim. Drops chatter like {@code launch_attempt}
     * while retaining fire / arm / bal_blocked / quiz start+end for triage.
     */
    public static boolean recordScheduleKind(AppPrefs prefs, String kind) {
        return PerfPolicy.keepScheduleKind(isSlim(prefs), kind);
    }
}
