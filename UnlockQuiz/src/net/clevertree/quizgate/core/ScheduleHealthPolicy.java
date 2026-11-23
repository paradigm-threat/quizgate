package net.clevertree.quizgate.core;

/**
 * Privacy-safe day-batch schedule health. Distinguishes idle tablets from
 * broken schedules without a 24h fire expectation.
 */
public final class ScheduleHealthPolicy {
    /** Below this interactive time, zero fires → {@link #STATUS_IDLE_OR_OFF}. */
    public static final long INTERACTIVE_FLOOR_MS = 30L * 60L * 1000L;

    public static final String STATUS_OK = "ok";
    public static final String STATUS_A11Y_OFF = "a11y_off";
    public static final String STATUS_NEVER_ARMED = "never_armed";
    public static final String STATUS_NO_FIRE_WHILE_INTERACTIVE = "no_fire_while_interactive";
    public static final String STATUS_IDLE_OR_OFF = "idle_or_off";
    public static final String STATUS_UNKNOWN = "unknown";

    private ScheduleHealthPolicy() {}

    /**
     * @param a11y           Accessibility enabled at end of day / upload time (1/0 as bool)
     * @param armed          arm intent present
     * @param alarmFired     schedule.jsonl {@code alarm_fired} count for the day
     * @param quizStarted    schedule.jsonl {@code quiz_started} count
     * @param armBlocked     schedule.jsonl {@code arm_blocked} count
     * @param interactiveMs  accumulated interactive ms for the stats day
     */
    public static String fireStatus(
            boolean a11y,
            boolean armed,
            int alarmFired,
            int quizStarted,
            int armBlocked,
            long interactiveMs) {
        if (alarmFired > 0 || quizStarted > 0) return STATUS_OK;
        if (armBlocked > 0 || (armed && !a11y)) return STATUS_A11Y_OFF;
        if (!armed) return STATUS_NEVER_ARMED;
        long interactive = Math.max(0L, interactiveMs);
        if (interactive >= INTERACTIVE_FLOOR_MS && a11y && armed) {
            return STATUS_NO_FIRE_WHILE_INTERACTIVE;
        }
        if (interactive < INTERACTIVE_FLOOR_MS) return STATUS_IDLE_OR_OFF;
        return STATUS_UNKNOWN;
    }

    /** Lean day-batch schedule object (counts + status). */
    public static final class Snapshot {
        public int a11y;
        public int armed;
        public int alarmFired;
        public int alarmScheduled;
        public int armBlocked;
        public int skipFire;
        public int bootSkip;
        public int quizStarted;
        public long interactiveMs;
        public String fireStatus = STATUS_UNKNOWN;

        public void finalizeStatus() {
            fireStatus = fireStatus(
                    a11y != 0,
                    armed != 0,
                    alarmFired,
                    quizStarted,
                    armBlocked,
                    interactiveMs);
        }
    }
}
