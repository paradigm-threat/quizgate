package net.clevertree.quizgate.core;

/**
 * Accessibility is Arm-critical. Dropping it must not leave a silent dead
 * schedule ({@code alarm_scheduled} with {@code a11y=0}, then only
 * {@code boot_skip}).
 *
 * <p>The user's arm intent stays set. Wake alarms are cancelled and the failure
 * is surfaced while Accessibility is off. When it returns, re-arm only while
 * interactive and not {@code pending_screen_lock} — the same gate as
 * {@link ScreenLockPolicy#shouldArmSchedule}.
 */
public final class A11yArmPolicy {
    private A11yArmPolicy() {}

    /**
     * True when the user wanted the schedule armed. A stored next-alarm with a
     * missing armed flag still counts: {@code apply()} could drop the flag and
     * leave only {@code boot_skip}.
     */
    public static boolean hasArmIntent(boolean armedFlag, long nextAlarmAtMs) {
        return armedFlag || nextAlarmAtMs > 0L;
    }

    /** Armed intent + Accessibility off: drop wake alarms. Do not clear arm intent. */
    public static boolean shouldCancelAlarmsForA11yOff(boolean armIntent, boolean accessibilityEnabled) {
        return armIntent && !accessibilityEnabled;
    }

    /** Same condition as {@link #shouldCancelAlarmsForA11yOff}: show it, do not stay silent. */
    public static boolean shouldSurfaceA11yBlocked(boolean armIntent, boolean accessibilityEnabled) {
        return shouldCancelAlarmsForA11yOff(armIntent, accessibilityEnabled);
    }

    /**
     * Accessibility is back. Re-arm only when the user had armed, the device is
     * interactive, and no screen-lock quiz is waiting.
     */
    public static boolean shouldRearmAfterA11yRestored(
            boolean armIntent,
            boolean accessibilityEnabled,
            boolean interactive,
            boolean pendingScreenLock) {
        return accessibilityEnabled
                && ScreenLockPolicy.shouldArmSchedule(armIntent, interactive, pendingScreenLock);
    }
}
