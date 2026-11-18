package net.clevertree.quizgate;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;

import java.util.List;

/**
 * Remember the app under the Hard lock and bring it back after unlock so the
 * child does not have to hunt through Recents.
 */
public final class ForegroundApp {
    private static final String TAG = AlarmScheduler.TAG;

    private ForegroundApp() {}

    public static boolean hasUsageAccess(Context ctx) {
        try {
            AppOpsManager ops = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) return false;
            int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), ctx.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    public static void openUsageAccessSettings(Context ctx) {
        try {
            Intent i = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception e) {
            Log.e(TAG, "usage access settings", e);
        }
    }

    /** Package + optional activity class of the last non-QuizGate foreground app. */
    public static final class Detected {
        public final String pkg;
        public final String cls;
        public Detected(String pkg, String cls) {
            this.pkg = pkg;
            this.cls = cls;
        }
    }

    /** Best-effort: last non-QuizGate, non-launcher package moved to foreground. */
    public static String detect(Context ctx) {
        Detected d = detectDetailed(ctx);
        return d == null ? null : d.pkg;
    }

    /**
     * How far back to scan usage events. A short window (e.g. 60s) misses the
     * common case where YouTube (or another player) has been foreground for
     * minutes without a fresh ACTIVITY_RESUMED — {@code am start} to an already
     * top task also emits no new event.
     */
    private static final long DETECT_LOOKBACK_MS = 15L * 60L * 1000L;

    public static Detected detectDetailed(Context ctx) {
        if (!hasUsageAccess(ctx)) {
            Log.w(TAG, "usage access missing — cannot detect previous app");
            return null;
        }
        UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return null;
        long end = System.currentTimeMillis();
        long begin = end - DETECT_LOOKBACK_MS;
        Detected fromEvents = detectFromEvents(ctx, usm, begin, end);
        if (fromEvents != null) {
            Log.i(TAG, "detected previous app=" + fromEvents.pkg
                    + (fromEvents.cls != null ? ("/" + fromEvents.cls) : ""));
            return fromEvents;
        }
        // Fallback: most-recently-used package in the same window (no activity class).
        Detected fromStats = detectFromUsageStats(ctx, usm, begin, end);
        Log.i(TAG, "detected previous app="
                + (fromStats == null ? "null" : fromStats.pkg + " (usage_stats)"));
        return fromStats;
    }

    private static Detected detectFromEvents(Context ctx, UsageStatsManager usm,
                                             long begin, long end) {
        UsageEvents events;
        try {
            events = usm.queryEvents(begin, end);
        } catch (Exception e) {
            Log.e(TAG, "queryEvents", e);
            return null;
        }
        if (events == null) return null;
        UsageEvents.Event ev = new UsageEvents.Event();
        String lastPkg = null;
        String lastCls = null;
        while (events.hasNextEvent()) {
            events.getNextEvent(ev);
            int type = ev.getEventType();
            boolean fg = type == UsageEvents.Event.MOVE_TO_FOREGROUND
                    || (Build.VERSION.SDK_INT >= 29 && type == UsageEvents.Event.ACTIVITY_RESUMED);
            if (!fg) continue;
            String pkg = ev.getPackageName();
            if (pkg == null) continue;
            if (pkg.equals(ctx.getPackageName())) continue;
            if (isLauncherOrSystemUi(ctx, pkg)) continue;
            lastPkg = pkg;
            String cls = ev.getClassName();
            lastCls = (cls != null && !cls.isEmpty()) ? cls : null;
        }
        if (lastPkg == null) return null;
        return new Detected(lastPkg, lastCls);
    }

    private static Detected detectFromUsageStats(Context ctx, UsageStatsManager usm,
                                                 long begin, long end) {
        List<UsageStats> stats;
        try {
            stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, begin, end);
        } catch (Exception e) {
            Log.e(TAG, "queryUsageStats", e);
            return null;
        }
        if (stats == null || stats.isEmpty()) return null;
        UsageStats best = null;
        for (UsageStats s : stats) {
            if (s == null) continue;
            String pkg = s.getPackageName();
            if (pkg == null) continue;
            if (pkg.equals(ctx.getPackageName())) continue;
            if (isLauncherOrSystemUi(ctx, pkg)) continue;
            if (best == null || s.getLastTimeUsed() > best.getLastTimeUsed()) {
                best = s;
            }
        }
        if (best == null || best.getLastTimeUsed() < begin) return null;
        return new Detected(best.getPackageName(), null);
    }

    public static void captureNow(Context ctx) {
        AppPrefs prefs = new AppPrefs(ctx);
        if (!PerformanceMode.allowReturnToApp(prefs)) {
            prefs.setResumePackage(null);
            prefs.setResumeClass(null);
            return;
        }
        Detected d = detectDetailed(ctx);
        if (d == null) {
            prefs.setResumePackage(null);
            prefs.setResumeClass(null);
            return;
        }
        prefs.setResumePackage(d.pkg);
        prefs.setResumeClass(d.cls);
    }

    /**
     * Bring the remembered app to the front (existing task state when possible).
     * @return true if a restore intent was fired
     */
    public static boolean restore(Context ctx) {
        AppPrefs prefs = new AppPrefs(ctx);
        if (!PerformanceMode.allowReturnToApp(prefs)) {
            prefs.setResumePackage(null);
            prefs.setResumeClass(null);
            return false;
        }
        String pkg = prefs.getResumePackage();
        String cls = prefs.getResumeClass();
        prefs.setResumePackage(null);
        prefs.setResumeClass(null);
        if (pkg == null || pkg.isEmpty()) {
            Log.i(TAG, "no resume package stored");
            return false;
        }
        if (pkg.equals(ctx.getPackageName()) || isLauncherOrSystemUi(ctx, pkg)) {
            return false;
        }
        try {
            Intent launch = buildRestoreIntent(ctx, pkg, cls);
            if (launch == null) {
                Log.w(TAG, "no launch intent for " + pkg);
                return false;
            }
            ctx.startActivity(launch);
            Log.i(TAG, "restored previous app=" + pkg
                    + (cls != null ? ("/" + cls) : "")
                    + " via=" + (launch.getComponent() != null
                    ? launch.getComponent().flattenToShortString() : "launcher"));
            return true;
        } catch (Exception e) {
            Log.e(TAG, "restore failed pkg=" + pkg, e);
            return false;
        }
    }

    /**
     * Build a restore intent that works under API 30+ package visibility and for
     * apps whose usage-stats class is a non-exported target (e.g. YouTube
     * {@code MainActivity} vs launcher {@code Shell$HomeActivity}).
     */
    private static Intent buildRestoreIntent(Context ctx, String pkg, String cls) {
        PackageManager pm = ctx.getPackageManager();
        // Prefer the exact activity that was interrupted when it is exported.
        // Usage-stats often reports YouTube's non-exported MainActivity (alias target);
        // starting that throws SecurityException — fall through to the launcher entry.
        if (cls != null && !cls.isEmpty() && isExportedComponent(pm, pkg, cls)) {
            Intent exact = new Intent();
            exact.setComponent(new ComponentName(pkg, cls));
            exact.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if (pm.resolveActivity(exact, 0) != null) {
                return exact;
            }
        }
        // Standard launcher entry — needs <queries> MAIN/LAUNCHER (or package) in the manifest.
        Intent launcher = pm.getLaunchIntentForPackage(pkg);
        if (launcher != null) {
            // Do NOT use RESET_TASK_IF_NEEDED — that can drop YouTube back to Home/root.
            launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            return launcher;
        }
        // Last resort: explicit MAIN/LAUNCHER with package (still requires visibility).
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        main.setPackage(pkg);
        main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (pm.resolveActivity(main, 0) != null) {
            return main;
        }
        return null;
    }

    private static boolean isExportedComponent(PackageManager pm, String pkg, String cls) {
        try {
            android.content.pm.ActivityInfo info =
                    pm.getActivityInfo(new ComponentName(pkg, cls), 0);
            return info != null && info.exported;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isLauncherOrSystemUi(Context ctx, String pkg) {
        if ("com.android.systemui".equals(pkg)) return true;
        PackageManager pm = ctx.getPackageManager();
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        try {
            android.content.pm.ResolveInfo ri = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri != null && ri.activityInfo != null && pkg.equals(ri.activityInfo.packageName)) {
                return true;
            }
        } catch (Exception ignored) {}
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            // Skip pure system packages that are not normal apps (still allow YouTube etc.)
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                    && pkg.startsWith("com.android.")) {
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }
}
