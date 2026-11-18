package net.clevertree.quizgate.core;

/**
 * Pure policy for screen-off → pending lock → wake quiz (no Android framework).
 * Interval schedule runs only while the device is already interactive.
 *
 * <p>Invariant: never stack Hard lock sessions. After unlock, a grace window
 * blocks wake/reintercept relaunch so the child is not jail→jail.
 */
public final class ScreenLockPolicy {
    /** Post-unlock window where wake/reintercept must not open a new Hard quiz. */
    public static final long UNLOCK_GRACE_MS = 120_000L;

    private ScreenLockPolicy() {}

    /** Armed devices always pend a lock when the screen turns off. */
    public static boolean shouldPendOnScreenOff(boolean armed) {
        return armed;
    }

    /**
     * Wake with a pending lock opens Hard quiz unless a Hard session is already active
     * (e.g. quiz stayed up across screen-off) or unlock grace is still active.
     */
    public static boolean shouldLaunchOnWake(boolean armed, boolean pending, boolean hardSessionActive) {
        return shouldLaunchOnWake(armed, pending, hardSessionActive, false);
    }

    public static boolean shouldLaunchOnWake(boolean armed, boolean pending,
                                            boolean hardSessionActive, boolean inUnlockGrace) {
        return armed && pending && !hardSessionActive && !inUnlockGrace;
    }

    /** Interval/morning alarms only while armed, interactive, and not pending. */
    public static boolean shouldArmSchedule(boolean armed, boolean interactive, boolean pending) {
        return armed && interactive && !pending;
    }

    /**
     * When wake skips launching a quiz, restore the interactive schedule if screen-off
     * may have cancelled alarms (unlock grace, or no pending lock).
     * Do not re-arm while a real pending wake-lock is waiting outside grace.
     */
    public static boolean shouldRearmScheduleOnWakeSkip(boolean inUnlockGrace, boolean pending) {
        return inUnlockGrace || !pending;
    }

    /** Alarm fire while screen off: skip launch, keep pending, cancel wake chain. */
    public static boolean shouldSkipFireWhileOff(boolean interactive) {
        return !interactive;
    }

    /** True when {@code nowMs} is still inside the post-unlock grace window. */
    public static boolean inUnlockGrace(long nowMs, long graceUntilMs) {
        return graceUntilMs > 0L && nowMs < graceUntilMs;
    }

    /** Lock/wake/interval sessions unlock on one correct answer (never multi-jail rounds). */
    public static int lockRoundSize() {
        return 1;
    }
}
