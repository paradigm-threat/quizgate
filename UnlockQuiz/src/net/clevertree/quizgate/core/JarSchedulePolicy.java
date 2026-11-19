package net.clevertree.quizgate.core;

/**
 * Pure helpers for applying minutes-jar bonus onto an already-scheduled next
 * interval alarm (post-unlock continue / earn path).
 */
public final class JarSchedulePolicy {
    /** Cap jar payout at 3 hours (matches {@code AppPrefs.takeMinutesBonusMs}). */
    public static final long JAR_BONUS_CAP_MS = 3L * 60L * 60L * 1000L;

    private JarSchedulePolicy() {}

    /** Minutes balance → bonus ms, capped. Zero when empty. */
    public static long bonusMsFromMinutes(int minutesBalance) {
        if (minutesBalance <= 0) return 0L;
        long fromJar = (long) minutesBalance * 60_000L;
        return Math.min(fromJar, JAR_BONUS_CAP_MS);
    }

    /**
     * Extend a future next-alarm by {@code bonusMs}.
     *
     * @return new next-alarm ms, or {@code 0} when it cannot be applied (caller
     *         should keep/refund the jar for a later unlock reschedule)
     */
    public static long extendNextAlarm(long nextAlarmAtMs, long nowMs, long bonusMs) {
        if (bonusMs <= 0L) return nextAlarmAtMs > nowMs ? nextAlarmAtMs : 0L;
        if (nextAlarmAtMs <= nowMs) return 0L;
        long extended = nextAlarmAtMs + bonusMs;
        if (extended <= nextAlarmAtMs) return 0L; // overflow guard
        return extended;
    }

    /** Whole minutes represented by a bonus ms value (floor). */
    public static int minutesFromBonusMs(long bonusMs) {
        if (bonusMs <= 0L) return 0;
        long mins = bonusMs / 60_000L;
        if (mins > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) mins;
    }
}
