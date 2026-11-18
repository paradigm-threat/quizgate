package net.clevertree.quizgate.core;

/**
 * Hard-only gate policy (JVM-testable). Android wrappers live in
 * {@code GateMode} / {@code ScheduleAuth}; keep confinement logic here so unit
 * tests do not need the framework.
 *
 * <p>Product lock is always {@link #HARD} (Accessibility confine). Legacy
 * {@code soft} prefs normalize to Hard. Overlay remains a launch helper, not an
 * Arm-critical Soft product path.
 */
public final class GatePolicy {
    /** @deprecated Soft product mode removed; normalize maps soft → {@link #HARD}. */
    @Deprecated
    public static final String SOFT = "soft";
    public static final String HARD = "hard";
    /** Product default — Accessibility confine during active lock. */
    public static final String DEFAULT = HARD;

    /** Arm-blocking gaps that must be fixed before quizzes can open / hold. */
    public enum CriticalGap {
        NONE,
        EXACT_ALARM,
        NOTIFICATIONS,
        BATTERY,
        ACCESSIBILITY,
        /** @deprecated Soft-only; never returned by {@link #firstCriticalGap}. */
        @Deprecated
        OVERLAY
    }

    private GatePolicy() {}

    /** Always Hard — legacy {@code soft} migrates to Hard. */
    public static String normalize(String mode) {
        return HARD;
    }

    public static boolean isHard(String mode) {
        return true;
    }

    /** Soft product mode removed. */
    public static boolean isSoft(String mode) {
        return false;
    }

    /** Overlay is recommended for BAL launch, never Arm-critical. */
    public static boolean isOverlayCritical(boolean hardMode) {
        return false;
    }

    /** Accessibility is always Arm-critical for the Hard-only lock. */
    public static boolean isAccessibilityCritical(boolean hardMode) {
        return true;
    }

    /**
     * First Arm hard-block gap: exact → notif → battery → Accessibility.
     * Overlay is never critical (launch helper only).
     *
     * @param hardMode ignored (always Hard); kept for call-site compatibility
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
        if (!accessibility) return CriticalGap.ACCESSIBILITY;
        return CriticalGap.NONE;
    }
}
