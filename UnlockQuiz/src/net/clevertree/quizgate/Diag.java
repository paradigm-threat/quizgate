package net.clevertree.quizgate;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import net.clevertree.quizgate.core.AnonUserId;
import net.clevertree.quizgate.core.BuildFingerprint;
import net.clevertree.quizgate.core.DayBatchSyncPolicy;
import net.clevertree.quizgate.core.DayUploadLedger;
import net.clevertree.quizgate.core.DiagBreadcrumb;
import net.clevertree.quizgate.core.DiagnosticsStore;
import net.clevertree.quizgate.core.StatsStore;
import net.clevertree.quizgate.core.TelemetryPayload;
import net.clevertree.quizgate.core.TelemetryRedaction;
import net.clevertree.quizgate.telemetry.RemoteSettingsPull;
import net.clevertree.quizgate.telemetry.TelemetryUploader;

import java.io.File;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * App-facing diagnostics helper: local breadcrumbs, schedule events, crash summary.
 * Never logs package names or PII. Day-batch upload requires remote-management
 * opt-in (or the compile flag) and runs asynchronously on open / session end —
 * no scheduled jobs. Closed days upload once; today at most once per day.
 */
public final class Diag {
    private Diag() {}

    public static File statsDir(Context ctx) {
        return new File(ctx.getApplicationContext().getFilesDir(), "stats");
    }

    public static StatsStore stats(Context ctx) {
        StatsStore store = new StatsStore(statsDir(ctx), PerformanceMode.compactEvery(ctx));
        store.setDayBarrierHour(new AppPrefs(ctx).getStatsDayBarrierHour());
        return store;
    }

    public static DiagnosticsStore diagnostics(Context ctx) {
        AppPrefs prefs = new AppPrefs(ctx);
        return new DiagnosticsStore(statsDir(ctx), PerformanceMode.compactEvery(ctx),
                PerformanceMode.mirrorScheduleToBreadcrumbs(prefs));
    }

    public static void breadcrumb(Context ctx, DiagBreadcrumb b) {
        try {
            if (!PerformanceMode.allowOptionalBreadcrumbs(new AppPrefs(ctx))) return;
            diagnostics(ctx).appendBreadcrumb(b);
        } catch (Exception e) {
            Log.w(BuildFingerprint.DIAG_LOG_TAG, "breadcrumb failed", e);
        }
    }

    /**
     * Always-on breadcrumb for triage-critical events (e.g. Auto perf↔slim switch).
     * Not gated by slim optional-diag suppression.
     */
    public static void breadcrumbAlways(Context ctx, DiagBreadcrumb b) {
        try {
            diagnostics(ctx).appendBreadcrumb(b);
        } catch (Exception e) {
            Log.w(BuildFingerprint.DIAG_LOG_TAG, "breadcrumbAlways failed", e);
        }
    }

    public static void schedule(Context ctx, DiagnosticsStore.ScheduleEvent e) {
        try {
            if (e != null && !PerformanceMode.recordScheduleKind(new AppPrefs(ctx), e.kind)) {
                return;
            }
            if (e != null && (e.gateMode == null || e.gateMode.isEmpty())) {
                e.gateMode = new AppPrefs(ctx).getGateMode();
            }
            diagnostics(ctx).appendScheduleEvent(e);
        } catch (Exception e2) {
            Log.w(BuildFingerprint.DIAG_LOG_TAG, "schedule event failed", e2);
        }
    }

    /** Compact stats + diagnostics rings (slim: call on session end only). */
    public static void compactLocal(Context ctx) {
        try {
            stats(ctx).compact();
        } catch (Exception e) {
            Log.w(BuildFingerprint.DIAG_LOG_TAG, "stats compact failed", e);
        }
        try {
            diagnostics(ctx).compact();
        } catch (Exception e) {
            Log.w(BuildFingerprint.DIAG_LOG_TAG, "diag compact failed", e);
        }
    }

    public static void nonFatal(Context ctx, String kind, Throwable t) {
        String msg = t == null ? "" : t.getMessage();
        String frame = "";
        if (t != null && t.getStackTrace() != null && t.getStackTrace().length > 0) {
            StackTraceElement el = t.getStackTrace()[0];
            frame = el.getClassName() + "#" + el.getMethodName() + ":" + el.getLineNumber();
        }
        try {
            diagnostics(ctx).appendException(System.currentTimeMillis(), kind, msg, frame);
        } catch (Exception e) {
            Log.w(BuildFingerprint.DIAG_LOG_TAG, "exception log failed", e);
        }
        Log.w(BuildFingerprint.DIAG_LOG_TAG, kind + ": " + TelemetryRedaction.safeMessage(msg));
    }

    public static void logcatSnapshot(Context ctx) {
        try {
            DiagnosticsStore d = diagnostics(ctx);
            StatsStore s = stats(ctx);
            int vc = versionCode(ctx);
            String vn = versionName(ctx);
            Log.i(BuildFingerprint.DIAG_LOG_TAG, BuildFingerprint.shortLine(vc, vn)
                    + " statsBytes=" + s.approximateSizeBytes()
                    + " " + d.summaryForLogcat());
        } catch (Exception e) {
            Log.w(BuildFingerprint.DIAG_LOG_TAG, "snapshot failed", e);
        }
    }

    /** @deprecated Prefer {@link #syncDayBatchesOnOpen}; kept for call-site compatibility. */
    @Deprecated
    public static final long REMOTE_FLUSH_MIN_GAP_MS = 20_000L;

    private static final AtomicBoolean DAY_BATCH_SYNC_RUNNING = new AtomicBoolean(false);

    /**
     * Build full diagnostic payload + optional legacy sync. Day rollups use
     * {@link #syncDayBatchesOnOpen} instead (accurate per-day upserts).
     */
    public static void flushLocalOnly(Context ctx) {
        try {
            AppPrefs prefs = new AppPrefs(ctx);
            if (prefs.isRemoteUploadEnabled() || BuildFingerprint.TELEMETRY_UPLOAD_ENABLED) {
                RemoteSettingsPull.maybePull(ctx);
            }
            prefs.setLastRemoteFlushAt(System.currentTimeMillis());
        } catch (Exception e) {
            Log.w(BuildFingerprint.DIAG_LOG_TAG, "flush failed", e);
        }
    }

    /**
     * On-open / post-session: asynchronously upload any local day rollups that
     * have not yet been upserted to Intake. No WorkManager / alarms.
     * Closed days upload once; today at most once per local calendar day.
     * Opt-in via remote management (or compile flag).
     */
    public static void flushAfterInteractiveSession(Context ctx) {
        syncDayBatchesOnOpen(ctx);
    }

    /** Alias — fire-and-forget day-batch backfill when the UI is interactive. */
    public static void syncDayBatchesOnOpen(Context ctx) {
        if (ctx == null) return;
        final Context app = ctx.getApplicationContext();
        // Shared curriculum pull (independent of Intake opt-in); throttled.
        CurriculumIndexClient.syncIfEnabled(app, false);
        AppPrefs prefs = new AppPrefs(app);
        if (!prefs.isRemoteUploadEnabled()) return;
        if (!DAY_BATCH_SYNC_RUNNING.compareAndSet(false, true)) {
            Log.i(BuildFingerprint.DIAG_LOG_TAG, "day_batch_sync_busy");
            return;
        }
        new Thread(() -> {
            try {
                runDayBatchSync(app);
            } finally {
                DAY_BATCH_SYNC_RUNNING.set(false);
            }
        }, "qg-day-batch-sync").start();
    }

    private static void runDayBatchSync(Context app) {
        try {
            AppPrefs prefs = new AppPrefs(app);
            StatsStore store = stats(app);
            TimeZone zone = TimeZone.getDefault();
            long now = System.currentTimeMillis();
            String today = StatsStore.dayKey(now, zone, prefs.getStatsDayBarrierHour());
            java.util.List<StatsStore.DayEntry> entries = store.allDayStats(zone);
            java.util.List<String> localKeys = new java.util.ArrayList<>();
            java.util.Map<String, StatsStore.DayStats> byDay = new java.util.HashMap<>();
            for (StatsStore.DayEntry e : entries) {
                String k = StatsStore.canonicalDayKey(e.dayKey);
                localKeys.add(k);
                byDay.put(k, e.stats);
            }
            DayUploadLedger ledger = new DayUploadLedger(store.getDir());
            java.util.Set<String> uploaded = ledger.load();
            String todaySnap = prefs.getTodayBatchSnapshotDay();
            java.util.List<String> need = DayBatchSyncPolicy.daysNeedingUpload(
                    localKeys, uploaded, today, todaySnap);
            Log.i(BuildFingerprint.DIAG_LOG_TAG, "day_batch_sync need=" + need.size()
                    + " today=" + today);
            if (need.isEmpty()) {
                RemoteSettingsPull.maybePull(app);
                return;
            }
            String uuid = AnonUserId.getOrCreate(app);
            String curriculum = prefs.getActiveCurriculumId();
            int ok = 0;
            for (String day : need) {
                StatsStore.DayStats rollup = byDay.get(day);
                if (rollup == null) rollup = new StatsStore.DayStats();
                boolean uploadedOk = TelemetryUploader.uploadDayBatch(
                        app, uuid, day, rollup, curriculum);
                if (!uploadedOk) {
                    Log.w(BuildFingerprint.DIAG_LOG_TAG, "day_batch_sync_stop day=" + day);
                    break;
                }
                ok++;
                if (DayBatchSyncPolicy.isClosedDay(day, today)) {
                    ledger.markUploaded(day);
                } else if (day.equals(today)) {
                    prefs.setTodayBatchSnapshotDay(today);
                }
            }
            prefs.setLastRemoteFlushAt(System.currentTimeMillis());
            Log.i(BuildFingerprint.DIAG_LOG_TAG, "day_batch_sync_done ok=" + ok
                    + "/" + need.size());
            RemoteSettingsPull.maybePull(app);
        } catch (Exception e) {
            Log.w(BuildFingerprint.DIAG_LOG_TAG, "day_batch_sync_failed", e);
        }
    }

    public static TelemetryPayload buildPayload(Context ctx) throws Exception {
        Context app = ctx.getApplicationContext();
        AppPrefs prefs = new AppPrefs(app);
        StatsStore stats = stats(app);
        DiagnosticsStore diag = diagnostics(app);
        long now = System.currentTimeMillis();
        TimeZone zone = TimeZone.getDefault();
        boolean exact = true;
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
            exact = am != null && am.canScheduleExactAlarms();
        }
        boolean notif = true;
        if (Build.VERSION.SDK_INT >= 33) {
            notif = app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        boolean photos;
        if (Build.VERSION.SDK_INT >= 33) {
            photos = app.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            photos = app.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        String resume = prefs.getResumePackage();
        PerformanceMode.reevaluate(app, "telemetry");
        net.clevertree.quizgate.core.PerfPolicy.AutoSignals sig = PerformanceMode.lastSignals(app);
        return TelemetryPayload.builder(now)
                .userUuid(AnonUserId.getOrCreate(app))
                .gitSha(BuildFingerprint.gitSha())
                .versionCode(versionCode(app))
                .versionName(versionName(app))
                .deviceModel(TelemetryRedaction.coarseModel(Build.MANUFACTURER, Build.MODEL))
                .sdkInt(Build.VERSION.SDK_INT)
                .release(Build.VERSION.RELEASE)
                .locale(Locale.getDefault().toLanguageTag())
                .tzId(zone.getID())
                .curriculumId(prefs.getActiveCurriculumId())
                .intervalMs(prefs.getIntervalMs())
                .jitterMs(prefs.getJitterMs())
                .nextAlarmAt(prefs.nextAlarmAt())
                .armed(prefs.isArmed())
                .usageStats(ForegroundApp.hasUsageAccess(app))
                .exactAlarm(exact)
                .notifications(notif)
                .photos(photos)
                .otherAppPresent(resume != null && !resume.isEmpty())
                .today(stats.dayStats(now, zone))
                .statsBytes(stats.approximateSizeBytes())
                .diagBytes(diag.approximateSizeBytes())
                .recentQuiz(stats.recentEventLines(DiagnosticsStore.RECENT_QUIZ_DEFAULT))
                .recentSchedule(diag.recentSchedule(40))
                .recentExceptions(diag.recentExceptions(20))
                .perfPreference(prefs.getPerformanceMode())
                .perfEffective(PerformanceMode.effective(app))
                .perfAutoReasons(prefs.getLastAutoReasons())
                .perfSwitchAt(prefs.getLastPerfSwitchAt())
                .perfSwitchFrom(prefs.getLastPerfSwitchFrom())
                .perfSwitchTo(prefs.getLastPerfSwitchTo())
                .perfBatteryPct(sig == null ? -1 : sig.batteryPct)
                .perfCharging(sig != null && sig.charging)
                .perfBatterySaver(sig != null && sig.batterySaver)
                .perfBatteryLowBroadcast(sig != null && sig.batteryLowBroadcast)
                .perfLowRamDevice(sig != null && sig.lowRamDevice)
                .perfRamClassMb(sig == null ? 0 : sig.ramClassMb)
                .perfTotalRamMb(sig == null ? 0L : sig.totalRamMb)
                .perfCpuCount(sig == null ? 0 : sig.cpuCount)
                .perfThermalSevere(sig != null && sig.thermalSevere)
                .build();
    }

    public static int versionCode(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= 28) return (int) pi.getLongVersionCode();
            //noinspection deprecation
            return pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    public static String versionName(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionName == null ? "" : pi.versionName;
        } catch (Exception e) {
            return "";
        }
    }
}
