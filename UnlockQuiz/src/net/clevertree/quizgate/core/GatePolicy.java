package net.clevertree.quizgate.core;

/**
 * Pure Soft/Hard gate policy (JVM-testable). Android wrappers live in
 * {@code GateMode} / {@code ScheduleAuth}; keep confinement logic here so unit
 * tests do not need the framework.
 *
 * <p>Default / recommended: {@link #HARD} (Accessibility confine). Soft remains
 * the gentler Home-bypass A/B path.
 */
public final class GatePolicy {
    public static final String SOFT = "soft";
    public static final String HARD = "hard";
    /** Product default — real Play-declarable lock. */
    public static final String DEFAULT = HARD;

    /** Arm-blocking gaps that must be fixed before quizzes can open / hold. */
    public enum CriticalGap {
        NONE,
        EXACT_ALARM,
        NOTIFICATIONS,
        BATTERY,
        ACCESSIBILITY,
        OVERLAY
    }

    private GatePolicy() {}

    public static String normalize(String mode) {
        if (SOFT.equalsIgnoreCase(mode)) return SOFT;
        return HARD;
    }

    public static boolean isHard(String mode) {
        return HARD.equals(normalize(mode));
    }

    public static boolean isSoft(String mode) {
        return !isHard(mode);
    }

    /** Soft: overlay is launch-critical. Hard: Accessibility is confine-critical. */
    public static boolean isOverlayCritical(boolean hardMode) {
        return !hardMode;
    }

    public static boolean isAccessibilityCritical(boolean hardMode) {
        return hardMode;
    }

    /**
     * First Arm hard-block gap for the given grants.
     * Shared by Soft and Hard: exact → notif → battery, then mode-critical grant.
     */
    public static CriticalGap firstCriticalGap(
            boolean hardMode,
            boolean exactAlarm,
            boolean notifications,
            boolean alarmChannelOk,
            boolean batteryUnrestricted,
            boolean accessibility,
            boolean overlay) {
        if (!exactAlarm) return CriticalGap.EXACT_ALARM;
        if (!notifications || !alarmChannelOk) return CriticalGap.NOTIFICATIONS;
        if (!batteryUnrestricted) return CriticalGap.BATTERY;
        if (hardMode) {
            if (!accessibility) return CriticalGap.ACCESSIBILITY;
        } else {
            if (!overlay) return CriticalGap.OVERLAY;
        }
        return CriticalGap.NONE;
    }
}
