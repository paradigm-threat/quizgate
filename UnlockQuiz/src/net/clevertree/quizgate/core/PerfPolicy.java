package net.clevertree.quizgate.core;

/**
 * Pure performance-profile policy (no Android deps) for unit tests and shared knobs.
 * User preference: {@link #PERFORMANCE}, {@link #SLIM}, or {@link #AUTO} (default).
 * Effective runtime profile is always {@link #PERFORMANCE} or {@link #SLIM}.
 */
public final class PerfPolicy {
    /** Full UX — fixed; never auto-downgrades. */
    public static final String PERFORMANCE = "performance";
    /** Legacy alias for {@link #PERFORMANCE}. */
    public static final String BALANCED = "balanced";
    /** Lean profile — fixed. */
    public static final String SLIM = "slim";
    /** Runtime heuristics pick performance vs slim. Default for new installs. */
    public static final String AUTO = "auto";

    public static final long FGS_STOP_PERFORMANCE_MS = 8_000L;
    public static final long FGS_STOP_SLIM_MS = 2_500L;
    /** @deprecated use {@link #FGS_STOP_PERFORMANCE_MS} */
    @Deprecated
    public static final long FGS_STOP_BALANCED_MS = FGS_STOP_PERFORMANCE_MS;

    public static final int COMPACT_EVERY_PERFORMANCE = 32;
    public static final int COMPACT_EVERY_SLIM = 10_000;
    /** @deprecated use {@link #COMPACT_EVERY_PERFORMANCE} */
    @Deprecated
    public static final int COMPACT_EVERY_BALANCED = COMPACT_EVERY_PERFORMANCE;

    /** Battery % at or below which Auto prefers slim (sticky until recovery). */
    public static final int AUTO_BATTERY_LOW_PCT = 20;
    /** Battery % at or above which Auto may leave slim (with charging + saver off). */
    public static final int AUTO_BATTERY_RECOVER_PCT = 25;
    /**
     * App heap memory class ≤ this → constrained (ActivityManager.getMemoryClass).
     * Typical tablets are 192–512; only very small heaps count as pressure.
     */
    public static final int AUTO_LOW_RAM_CLASS_MB = 96;
    /** CPU count at or below → weak device. */
    public static final int AUTO_MIN_CPUS_WEAK = 4;
    /** Total RAM MB at or below → weak device. */
    public static final long AUTO_LOW_TOTAL_RAM_MB = 3072L;

    private PerfPolicy() {}

    /** Normalize user preference: performance | slim | auto. */
    public static String normalize(String raw) {
        if (raw == null) return AUTO;
        String t = raw.trim().toLowerCase();
        if (SLIM.equals(t)) return SLIM;
        if (PERFORMANCE.equals(t) || BALANCED.equals(t)) return PERFORMANCE;
        if (AUTO.equals(t)) return AUTO;
        // Unknown / empty → Auto (safe default for new installs and upgrades).
        if (t.isEmpty()) return AUTO;
        return AUTO;
    }

    public static boolean isAuto(String preference) {
        return AUTO.equals(normalize(preference));
    }

    public static boolean isFixedSlim(String preference) {
        return SLIM.equals(normalize(preference));
    }

    public static boolean isFixedPerformance(String preference) {
        return PERFORMANCE.equals(normalize(preference));
    }

    /**
     * Resolve effective profile from preference + Auto signals.
     * Fixed Performance/Slim ignore signals. Auto uses {@link AutoSignals}.
     */
    public static String effective(String preference, AutoSignals signals) {
        String pref = normalize(preference);
        if (SLIM.equals(pref)) return SLIM;
        if (PERFORMANCE.equals(pref)) return PERFORMANCE;
        // Auto
        if (signals != null && signals.preferSlim) return SLIM;
        return PERFORMANCE;
    }

    public static boolean isEffectiveSlim(String preference, AutoSignals signals) {
        return SLIM.equals(effective(preference, signals));
    }

    public static long fgsStopAfterMs(boolean slim) {
        return slim ? FGS_STOP_SLIM_MS : FGS_STOP_PERFORMANCE_MS;
    }

    public static int compactEvery(boolean slim) {
        return slim ? COMPACT_EVERY_SLIM : COMPACT_EVERY_PERFORMANCE;
    }

    /** Schedule JSONL kinds retained when effective slim (drops launch_attempt chatter). */
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
            case "perf_mode":
                return true;
            default:
                return false;
        }
    }

    /**
     * Pure Auto decision from numeric/boolean signals (unit-testable).
     * Prefer slim when any pressure signal is set; recover to performance when
     * device is capable and power is healthy (charging or battery ≥ recover,
     * saver off, not critically thermal).
     */
    public static AutoDecision decideAuto(AutoSignals s, boolean currentlySlim) {
        if (s == null) {
            return new AutoDecision(false, new String[]{"missing_signals"}, PERFORMANCE);
        }
        java.util.ArrayList<String> reasons = new java.util.ArrayList<>();
        if (s.batterySaver) reasons.add("battery_saver");
        if (s.batteryLowBroadcast) reasons.add("battery_low_broadcast");
        if (s.batteryPct >= 0 && s.batteryPct <= AUTO_BATTERY_LOW_PCT) {
            reasons.add("battery_pct_low");
        }
        if (s.lowRamDevice) reasons.add("low_ram_device");
        if (s.ramClassMb > 0 && s.ramClassMb <= AUTO_LOW_RAM_CLASS_MB) {
            reasons.add("ram_class_low");
        }
        if (s.totalRamMb > 0 && s.totalRamMb <= AUTO_LOW_TOTAL_RAM_MB) {
            reasons.add("total_ram_low");
        }
        if (s.cpuCount > 0 && s.cpuCount <= AUTO_MIN_CPUS_WEAK) {
            reasons.add("cpu_count_low");
        }
        if (s.thermalSevere) reasons.add("thermal_severe");

        boolean pressure = !reasons.isEmpty();
        boolean capable = !s.lowRamDevice
                && (s.ramClassMb <= 0 || s.ramClassMb > AUTO_LOW_RAM_CLASS_MB)
                && (s.totalRamMb <= 0 || s.totalRamMb > AUTO_LOW_TOTAL_RAM_MB)
                && (s.cpuCount <= 0 || s.cpuCount > AUTO_MIN_CPUS_WEAK);
        boolean powerOk = !s.batterySaver && !s.batteryLowBroadcast
                && (s.batteryPct < 0 || s.batteryPct >= AUTO_BATTERY_RECOVER_PCT
                || s.charging);
        boolean thermalOk = !s.thermalSevere;

        boolean preferSlim;
        if (pressure) {
            preferSlim = true;
        } else if (currentlySlim) {
            // Sticky slim until recovery conditions clear.
            preferSlim = !(capable && powerOk && thermalOk);
            if (!preferSlim) reasons.add("recovered");
            else reasons.add("sticky_slim");
        } else {
            preferSlim = false;
            reasons.add("healthy");
        }

        String effective = preferSlim ? SLIM : PERFORMANCE;
        return new AutoDecision(preferSlim, reasons.toArray(new String[0]), effective);
    }

    /** Snapshot of device/power signals for Auto (privacy-safe numerics only). */
    public static final class AutoSignals {
        public final int batteryPct;          // -1 unknown
        public final boolean charging;
        public final boolean batterySaver;
        public final boolean batteryLowBroadcast;
        public final boolean lowRamDevice;
        public final int ramClassMb;          // 0 unknown
        public final long totalRamMb;         // 0 unknown
        public final int cpuCount;            // 0 unknown
        public final boolean thermalSevere;
        public final boolean preferSlim;      // filled by decideAuto / sampler

        public AutoSignals(int batteryPct, boolean charging, boolean batterySaver,
                           boolean batteryLowBroadcast, boolean lowRamDevice,
                           int ramClassMb, long totalRamMb, int cpuCount,
                           boolean thermalSevere, boolean preferSlim) {
            this.batteryPct = batteryPct;
            this.charging = charging;
            this.batterySaver = batterySaver;
            this.batteryLowBroadcast = batteryLowBroadcast;
            this.lowRamDevice = lowRamDevice;
            this.ramClassMb = ramClassMb;
            this.totalRamMb = totalRamMb;
            this.cpuCount = cpuCount;
            this.thermalSevere = thermalSevere;
            this.preferSlim = preferSlim;
        }
    }

    public static final class AutoDecision {
        public final boolean preferSlim;
        public final String[] reasonCodes;
        public final String effective;

        public AutoDecision(boolean preferSlim, String[] reasonCodes, String effective) {
            this.preferSlim = preferSlim;
            this.reasonCodes = reasonCodes == null ? new String[0] : reasonCodes;
            this.effective = effective == null ? PERFORMANCE : effective;
        }

        public String reasonCodesCsv() {
            if (reasonCodes.length == 0) return "";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < reasonCodes.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(reasonCodes[i]);
            }
            return sb.toString();
        }
    }
}
