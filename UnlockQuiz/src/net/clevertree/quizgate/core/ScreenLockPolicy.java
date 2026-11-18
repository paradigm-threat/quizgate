package net.clevertree.quizgate.core;

/**
 * Pure policy for screen-off → pending lock → wake quiz (no Android framework).
 * Interval schedule runs only while the device is already interactive.
 */
public final class ScreenLockPolicy {
    private ScreenLockPolicy() {}

    /** Armed devices always pend a lock when the screen turns off. */
    public static boolean shouldPendOnScreenOff(boolean armed) {
        return armed;
    }

    /**
     * Wake with a pending lock opens Hard quiz unless a Hard session is already active
     * (e.g. quiz stayed up across screen-off).
     */
    public static boolean shouldLaunchOnWake(boolean armed, boolean pending, boolean hardSessionActive) {
        return armed && pending && !hardSessionActive;
    }

    /** Interval/morning alarms only while armed, interactive, and not pending. */
    public static boolean shouldArmSchedule(boolean armed, boolean interactive, boolean pending) {
        return armed && interactive && !pending;
    }

    /** Alarm fire while screen off: skip launch, keep pending, cancel wake chain. */
    public static boolean shouldSkipFireWhileOff(boolean interactive) {
        return !interactive;
    }
}
