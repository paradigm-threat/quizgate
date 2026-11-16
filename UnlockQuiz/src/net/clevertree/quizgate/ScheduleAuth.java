package net.clevertree.quizgate;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import net.clevertree.quizgate.core.DiagBreadcrumb;
import net.clevertree.quizgate.core.DiagnosticsStore;
import net.clevertree.quizgate.core.SystemClock;

import java.io.File;
import java.util.TimeZone;

/**
 * OS authorizations that keep the quiz schedule firing: exact alarms, battery
 * exemption, notifications (FGS fallback), full-screen intent, usage stats.
 * Checks + optional single Settings prompt (no dialog spam).
 */
public final class ScheduleAuth {
    private static final String TAG = AlarmScheduler.TAG;
    private static final long PROMPT_COOLDOWN_MS = 90_000L;

    public enum Gap {
        NONE,
        EXACT_ALARM,
        NOTIFICATIONS,
        BATTERY,
        FULL_SCREEN_INTENT,
        OVERLAY,
        USAGE_STATS
    }

    public static final class Snapshot {
        public final long ts;
        public final boolean exactAlarm;
        public final boolean batteryUnrestricted;
        public final boolean notifications;
        public final boolean fullScreenIntent;
        public final boolean overlay;
        public final boolean usageStats;

        Snapshot(long ts, boolean exactAlarm, boolean batteryUnrestricted,
                 boolean notifications, boolean fullScreenIntent, boolean overlay,
                 boolean usageStats) {
            this.ts = ts;
            this.exactAlarm = exactAlarm;
            this.batteryUnrestricted = batteryUnrestricted;
            this.notifications = notifications;
            this.fullScreenIntent = fullScreenIntent;
            this.overlay = overlay;
            this.usageStats = usageStats;
        }

        public String compactNote() {
            return "exact=" + bit(exactAlarm)
                    + " batt=" + bit(batteryUnrestricted)
                    + " notif=" + bit(notifications)
                    + " fsi=" + bit(fullScreenIntent)
                    + " overlay=" + bit(overlay)
                    + " usage=" + bit(usageStats);
        }

        public Gap firstGap() {
            if (!exactAlarm) return Gap.EXACT_ALARM;
            if (!notifications) return Gap.NOTIFICATIONS;
            if (!batteryUnrestricted) return Gap.BATTERY;
            if (!fullScreenIntent) return Gap.FULL_SCREEN_INTENT;
            if (!overlay) return Gap.OVERLAY;
            if (!usageStats) return Gap.USAGE_STATS;
            return Gap.NONE;
        }

        private static int bit(boolean v) { return v ? 1 : 0; }
    }

    private ScheduleAuth() {}

    public static DiagnosticsStore store(Context ctx) {
        try {
            return Diag.diagnostics(ctx);
        } catch (Throwable t) {
            return new DiagnosticsStore(new File(ctx.getApplicationContext().getFilesDir(), "stats"));
        }
    }

    public static Snapshot snapshot(Context ctx) {
        long ts = new SystemClock().nowMillis();
        boolean exact = true;
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = ctx.getSystemService(AlarmManager.class);
            exact = am != null && am.canScheduleExactAlarms();
        }
        boolean batt = true;
        if (Build.VERSION.SDK_INT >= 23) {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            batt = pm != null && pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
        }
        boolean notif = true;
        if (Build.VERSION.SDK_INT >= 33) {
            notif = ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        boolean fsi = true;
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            fsi = nm != null && nm.canUseFullScreenIntent();
        }
        boolean overlay = true;
        if (Build.VERSION.SDK_INT >= 23) {
            overlay = Settings.canDrawOverlays(ctx);
        }
        boolean usage = ForegroundApp.hasUsageAccess(ctx);
        return new Snapshot(ts, exact, batt, notif, fsi, overlay, usage);
    }

    public static void log(Context ctx, String kind, String trigger, long nextAlarmAt,
                           long intervalMs, long jitterMs) {
        Snapshot s = snapshot(ctx);
        try {
            DiagnosticsStore diag = store(ctx);
            String note = s.compactNote();
            if (!s.exactAlarm) note = note + " mode=inexact";
            String eventKind = kind == null || kind.isEmpty() ? DiagnosticsStore.KIND_PERM : kind;
            diag.appendScheduleEvent(new DiagnosticsStore.ScheduleEvent(
                    s.ts, TimeZone.getDefault(), eventKind, trigger, "",
                    nextAlarmAt, intervalMs, jitterMs, null, note));
            diag.appendBreadcrumb(DiagBreadcrumb.builder(s.ts, "schedule_auth")
                    .trigger(trigger == null ? "" : trigger)
                    .nextAlarmAt(nextAlarmAt)
                    .intervalMs(intervalMs)
                    .jitterMs(jitterMs)
                    .exactAlarm(s.exactAlarm)
                    .usageStats(s.usageStats)
                    .message(s.compactNote())
                    .build());
        } catch (Throwable e) {
            Log.w(TAG, "schedule auth diag: " + e.getMessage());
        }
        Log.i(TAG, "schedule_auth kind=" + kind + " trigger=" + trigger + " " + s.compactNote());
    }

    public static Gap promptNextGap(Activity activity) {
        Snapshot s = snapshot(activity);
        Gap gap = s.firstGap();
        if (gap == Gap.NONE) return Gap.NONE;

        AppPrefs prefs = new AppPrefs(activity);
        long now = s.ts;
        String lastGap = prefs.getLastAuthPromptGap();
        long lastAt = prefs.getLastAuthPromptAt();
        if (gap.name().equals(lastGap) && now - lastAt < PROMPT_COOLDOWN_MS) {
            Log.i(TAG, "auth prompt cooldown gap=" + gap);
            return gap;
        }
        prefs.setLastAuthPrompt(gap.name(), now);

        switch (gap) {
            case EXACT_ALARM:
                toast(activity, "Allow exact alarms so quizzes fire on time");
                openExactAlarmSettings(activity);
                break;
            case NOTIFICATIONS:
                requestNotifications(activity);
                break;
            case BATTERY:
                toast(activity, "Set battery to Unrestricted so alarms survive Doze");
                openBatterySettings(activity);
                break;
            case FULL_SCREEN_INTENT:
                toast(activity, "Allow full-screen intents for the quiz overlay");
                openFullScreenIntentSettings(activity);
                break;
            case OVERLAY:
                toast(activity, "Allow Appear on top so quizzes can open over other apps");
                openOverlaySettings(activity);
                break;
            case USAGE_STATS:
                toast(activity, "Turn on Usage access — unlock returns to the last app");
                ForegroundApp.openUsageAccessSettings(activity);
                break;
            default:
                break;
        }
        return gap;
    }

    public static void appendStatus(StringBuilder sb, Context ctx) {
        Snapshot s = snapshot(ctx);
        if (Build.VERSION.SDK_INT >= 31) {
            sb.append("\nExact alarms: ").append(s.exactAlarm ? "yes" : "NO — tap Arm");
        }
        if (Build.VERSION.SDK_INT >= 23) {
            sb.append("\nBattery unrestricted: ").append(s.batteryUnrestricted ? "yes" : "NO — tap Arm");
        }
        if (Build.VERSION.SDK_INT >= 33) {
            sb.append("\nNotifications: ").append(s.notifications ? "yes" : "NO — tap Arm");
        }
        if (Build.VERSION.SDK_INT >= 34) {
            sb.append("\nFull-screen intent: ").append(s.fullScreenIntent ? "yes" : "NO — tap Arm");
        }
        if (Build.VERSION.SDK_INT >= 23) {
            sb.append("\nAppear on top: ").append(s.overlay ? "yes" : "NO — tap Arm");
        }
        sb.append("\nUsage access (return to last app): ")
                .append(s.usageStats ? "yes" : "NO — tap Arm");
    }

    private static void requestNotifications(Activity activity) {
        if (Build.VERSION.SDK_INT < 33) return;
        if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (!activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                && new AppPrefs(activity).wasNotificationDeniedOnce()) {
            toast(activity, "Enable notifications for the quiz trigger");
            openAppNotificationSettings(activity);
            return;
        }
        activity.requestPermissions(
                new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
    }

    static void markNotificationDenied(Context ctx) {
        new AppPrefs(ctx).setNotificationDeniedOnce(true);
    }

    private static void openExactAlarmSettings(Context ctx) {
        if (Build.VERSION.SDK_INT < 31) return;
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + ctx.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception e) {
            Log.w(TAG, "exact alarm settings", e);
        }
    }

    private static void openBatterySettings(Context ctx) {
        if (Build.VERSION.SDK_INT < 23) return;
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + ctx.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception e) {
            try {
                Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            } catch (Exception ignored) {}
        }
    }

    private static void openFullScreenIntentSettings(Context ctx) {
        if (Build.VERSION.SDK_INT < 34) return;
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:" + ctx.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception e) {
            openAppNotificationSettings(ctx);
        }
    }

    private static void openOverlaySettings(Context ctx) {
        if (Build.VERSION.SDK_INT < 23) return;
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + ctx.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception e) {
            openAppNotificationSettings(ctx);
        }
    }

    private static void openAppNotificationSettings(Context ctx) {
        try {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, ctx.getPackageName());
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception e) {
            try {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + ctx.getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            } catch (Exception ignored) {}
        }
    }

    private static void toast(Context ctx, String msg) {
        try {
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
        } catch (Exception ignored) {}
    }
}
