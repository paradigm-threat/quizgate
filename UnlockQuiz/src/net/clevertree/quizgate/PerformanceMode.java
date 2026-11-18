package net.clevertree.quizgate;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import net.clevertree.quizgate.core.BuildFingerprint;
import net.clevertree.quizgate.core.DiagBreadcrumb;
import net.clevertree.quizgate.core.DiagnosticsStore;
import net.clevertree.quizgate.core.PerfPolicy;
import net.clevertree.quizgate.core.PerfPolicy.AutoDecision;
import net.clevertree.quizgate.core.PerfPolicy.AutoSignals;

/**
 * Performance profile facade. User preference is {@link #PERFORMANCE}, {@link #SLIM},
 * or {@link #AUTO} (default). Effective runtime profile is always performance or slim.
 * Auto re-evaluates on app start and power broadcasts — no busy polling.
 */
public final class PerformanceMode {
    public static final String PERFORMANCE = PerfPolicy.PERFORMANCE;
    public static final String BALANCED = PerfPolicy.BALANCED; // legacy alias
    public static final String SLIM = PerfPolicy.SLIM;
    public static final String AUTO = PerfPolicy.AUTO;

    public static final long FGS_STOP_BALANCED_MS = PerfPolicy.FGS_STOP_PERFORMANCE_MS;
    public static final long FGS_STOP_SLIM_MS = PerfPolicy.FGS_STOP_SLIM_MS;
    public static final long SUCCESS_HOLD_BALANCED_MS = 1_000L;
    public static final long SUCCESS_HOLD_SLIM_MS = 350L;
    public static final int COMPACT_EVERY_BALANCED = PerfPolicy.COMPACT_EVERY_PERFORMANCE;
    public static final int COMPACT_EVERY_SLIM = PerfPolicy.COMPACT_EVERY_SLIM;

    private static volatile AutoSignals cachedSignals;
    private static volatile String cachedEffective;

    private PerformanceMode() {}

    public static String normalize(String raw) {
        return PerfPolicy.normalize(raw);
    }

    /** User-selected preference (performance | slim | auto). */
    public static String preference(Context ctx) {
        return new AppPrefs(ctx).getPerformanceMode();
    }

    public static String preference(AppPrefs prefs) {
        return prefs == null ? AUTO : prefs.getPerformanceMode();
    }

    /**
     * Re-sample device/power signals, resolve effective mode, and if Auto switched
     * performance↔slim, write a durable breadcrumb + QuizGateDiag line.
     */
    public static String reevaluate(Context ctx, String trigger) {
        Context app = ctx.getApplicationContext();
        AppPrefs prefs = new AppPrefs(app);
        String pref = prefs.getPerformanceMode();
        AutoSignals raw = sampleSignals(app, prefs);
        boolean wasSlim = SLIM.equals(prefs.getEffectivePerformanceMode());
        AutoDecision decision;
        if (PerfPolicy.isAuto(pref)) {
            decision = PerfPolicy.decideAuto(raw, wasSlim);
        } else if (PerfPolicy.isFixedSlim(pref)) {
            decision = new AutoDecision(true, new String[]{"fixed_slim"}, SLIM);
        } else {
            decision = new AutoDecision(false, new String[]{"fixed_performance"}, PERFORMANCE);
        }
        AutoSignals signals = new AutoSignals(
                raw.batteryPct, raw.charging, raw.batterySaver, raw.batteryLowBroadcast,
                raw.lowRamDevice, raw.ramClassMb, raw.totalRamMb, raw.cpuCount,
                raw.thermalSevere, decision.preferSlim);
        cachedSignals = signals;
        cachedEffective = decision.effective;

        String prevEffective = prefs.getEffectivePerformanceMode();
        boolean switched = prevEffective != null && !prevEffective.isEmpty()
                && !prevEffective.equals(decision.effective)
                && PerfPolicy.isAuto(pref);

        prefs.setEffectivePerformanceMode(decision.effective);
        prefs.setLastAutoReasons(decision.reasonCodesCsv());
        prefs.setLastAutoSignalsJson(signalsJson(signals));
        if (switched) {
            long now = System.currentTimeMillis();
            prefs.setLastPerfSwitchAt(now);
            prefs.setLastPerfSwitchFrom(prevEffective);
            prefs.setLastPerfSwitchTo(decision.effective);
            logAutoSwitch(app, prefs, prevEffective, decision, signals, trigger, now);
        }
        return decision.effective;
    }

    /** Effective slim? Uses cached reevaluate result when possible. */
    public static boolean isSlim(Context ctx) {
        ensureEvaluated(ctx);
        return SLIM.equals(cachedEffective);
    }

    public static boolean isSlim(AppPrefs prefs) {
        if (prefs == null) return false;
        String eff = prefs.getEffectivePerformanceMode();
        if (eff != null && !eff.isEmpty()) return SLIM.equals(eff);
        // Fallback without Context sampling: fixed slim only; Auto → performance until reevaluate.
        String pref = prefs.getPerformanceMode();
        return PerfPolicy.isFixedSlim(pref);
    }

    public static String effective(Context ctx) {
        ensureEvaluated(ctx);
        return cachedEffective == null ? PERFORMANCE : cachedEffective;
    }

    public static AutoSignals lastSignals(Context ctx) {
        ensureEvaluated(ctx);
        return cachedSignals;
    }

    private static void ensureEvaluated(Context ctx) {
        if (cachedEffective == null || cachedSignals == null) {
            reevaluate(ctx, "lazy");
        }
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

    public static boolean allowReturnToApp(AppPrefs prefs) {
        return !isSlim(prefs);
    }

    public static boolean allowRichMotion(AppPrefs prefs) {
        return !isSlim(prefs);
    }

    public static boolean allowOptionalBreadcrumbs(AppPrefs prefs) {
        return !isSlim(prefs);
    }

    public static boolean mirrorScheduleToBreadcrumbs(AppPrefs prefs) {
        return !isSlim(prefs);
    }

    public static boolean refreshWidgetOnAnswer(AppPrefs prefs) {
        return !isSlim(prefs);
    }

    public static boolean verboseLog(AppPrefs prefs) {
        return !isSlim(prefs);
    }

    public static long setupCountdownTickMs(AppPrefs prefs) {
        return isSlim(prefs) ? 60_000L : 30_000L;
    }

    public static boolean recordScheduleKind(AppPrefs prefs, String kind) {
        return PerfPolicy.keepScheduleKind(isSlim(prefs), kind);
    }

    /** Sample privacy-safe device/power stats (no PII). */
    public static AutoSignals sampleSignals(Context ctx, AppPrefs prefs) {
        int batteryPct = -1;
        boolean charging = false;
        boolean batteryLowBroadcast = prefs != null && prefs.isBatteryLowFlag();
        try {
            Intent battery = ctx.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery != null) {
                int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    batteryPct = Math.round(100f * level / scale);
                }
                int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS,
                        BatteryManager.BATTERY_STATUS_UNKNOWN);
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL;
                int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                if (plugged != 0) charging = true;
            }
        } catch (Exception ignored) {}

        boolean batterySaver = false;
        boolean thermalSevere = false;
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                batterySaver = pm.isPowerSaveMode();
                if (Build.VERSION.SDK_INT >= 29) {
                    int status = pm.getCurrentThermalStatus();
                    thermalSevere = status >= PowerManager.THERMAL_STATUS_SEVERE;
                }
            }
        } catch (Exception ignored) {}

        boolean lowRamDevice = false;
        int ramClassMb = 0;
        long totalRamMb = 0L;
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                lowRamDevice = am.isLowRamDevice();
                ramClassMb = am.getMemoryClass();
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                if (mi.totalMem > 0L) totalRamMb = mi.totalMem / (1024L * 1024L);
            }
        } catch (Exception ignored) {}

        int cpuCount = 0;
        try {
            cpuCount = Runtime.getRuntime().availableProcessors();
        } catch (Exception ignored) {}

        return new AutoSignals(batteryPct, charging, batterySaver, batteryLowBroadcast,
                lowRamDevice, ramClassMb, totalRamMb, cpuCount, thermalSevere, false);
    }

    private static void logAutoSwitch(Context ctx, AppPrefs prefs, String from, AutoDecision d,
                                      AutoSignals s, String trigger, long now) {
        String msg = "from=" + from + " to=" + d.effective
                + " reasons=" + d.reasonCodesCsv()
                + " bat=" + s.batteryPct
                + " charge=" + s.charging
                + " saver=" + s.batterySaver
                + " low_bcast=" + s.batteryLowBroadcast
                + " low_ram=" + s.lowRamDevice
                + " ram_class_mb=" + s.ramClassMb
                + " total_ram_mb=" + s.totalRamMb
                + " cpus=" + s.cpuCount
                + " thermal=" + s.thermalSevere
                + " trigger=" + (trigger == null ? "" : trigger);
        Log.i(BuildFingerprint.DIAG_LOG_TAG, "perf_mode_switch " + msg);
        try {
            // Always durable — even when effective slim (switch itself is the triage event).
            Diag.breadcrumbAlways(ctx, DiagBreadcrumb.builder(now, DiagnosticsStore.KIND_PERF_MODE)
                    .outcome(d.effective)
                    .trigger(trigger == null ? "auto" : trigger)
                    .message(msg)
                    .build());
            Diag.schedule(ctx, new DiagnosticsStore.ScheduleEvent(
                    now, java.util.TimeZone.getDefault(), DiagnosticsStore.KIND_PERF_MODE,
                    trigger == null ? "auto" : trigger, "perf",
                    prefs.nextAlarmAt(), prefs.getIntervalMs(), prefs.getJitterMs(),
                    null, msg));
        } catch (Exception e) {
            Log.w(BuildFingerprint.DIAG_LOG_TAG, "perf_mode_switch log failed", e);
        }
    }

    public static String signalsJson(AutoSignals s) {
        if (s == null) return "{}";
        return "{\"battery_pct\":" + s.batteryPct
                + ",\"charging\":" + s.charging
                + ",\"battery_saver\":" + s.batterySaver
                + ",\"battery_low_broadcast\":" + s.batteryLowBroadcast
                + ",\"low_ram_device\":" + s.lowRamDevice
                + ",\"ram_class_mb\":" + s.ramClassMb
                + ",\"total_ram_mb\":" + s.totalRamMb
                + ",\"cpu_count\":" + s.cpuCount
                + ",\"thermal_severe\":" + s.thermalSevere
                + ",\"prefer_slim\":" + s.preferSlim
                + "}";
    }
}
