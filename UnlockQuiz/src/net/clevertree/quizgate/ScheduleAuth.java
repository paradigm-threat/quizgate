package net.clevertree.quizgate;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationChannel;
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
import net.clevertree.quizgate.core.GatePolicy;
import net.clevertree.quizgate.core.SystemClock;

import java.io.File;
import java.util.TimeZone;

/**
 * OS authorizations for a fully working QuizGate: exact alarms, notifications
 * (runtime + app toggle + alarm channel), battery exemption, appear-on-top
 * (critical for Soft BAL), Accessibility (critical for Hard confine), full-screen
 * intent, usage stats (return to last app).
 * Checks + single Settings prompt at a time (90s cooldown).
 *
 * <p>Hard mode (recommended): Accessibility confine during an active lock session.
 * Soft mode: overlay/FSI launch; Home/Back = bypass.
 */
public final class ScheduleAuth {
    private static final String TAG = AlarmScheduler.TAG;
    private static final long PROMPT_COOLDOWN_MS = 90_000L;

    /** Soft prompt order: schedule reliability first, then launch-critical overlay. */
    private static final Gap[] PROMPT_ORDER_SOFT = {
            Gap.EXACT_ALARM, Gap.NOTIFICATIONS, Gap.BATTERY,
            Gap.OVERLAY, Gap.FULL_SCREEN_INTENT, Gap.USAGE_STATS
    };

    /** Hard prompt order: Accessibility is the primary confine grant. */
    private static final Gap[] PROMPT_ORDER_HARD = {
            Gap.EXACT_ALARM, Gap.NOTIFICATIONS, Gap.BATTERY,
            Gap.ACCESSIBILITY, Gap.OVERLAY, Gap.FULL_SCREEN_INTENT, Gap.USAGE_STATS
    };

    public enum Gap {
        NONE,
        EXACT_ALARM,
        NOTIFICATIONS,
        BATTERY,
        FULL_SCREEN_INTENT,
        OVERLAY,
        USAGE_STATS,
        ACCESSIBILITY
    }

    public static final class Snapshot {
        public final long ts;
        public final boolean exactAlarm;
        public final boolean batteryUnrestricted;
        /** Runtime POST_NOTIFICATIONS (API 33+) and app-level notification toggle. */
        public final boolean notifications;
        /** Alarm channel exists at HIGH+ importance (when channels apply). */
        public final boolean alarmChannelOk;
        public final boolean fullScreenIntent;
        public final boolean overlay;
        public final boolean usageStats;
        public final boolean accessibility;
        public final boolean hardMode;

        Snapshot(long ts, boolean exactAlarm, boolean batteryUnrestricted,
                 boolean notifications, boolean alarmChannelOk,
                 boolean fullScreenIntent, boolean overlay,
                 boolean usageStats, boolean accessibility, boolean hardMode) {
            this.ts = ts;
            this.exactAlarm = exactAlarm;
            this.batteryUnrestricted = batteryUnrestricted;
            this.notifications = notifications;
            this.alarmChannelOk = alarmChannelOk;
            this.fullScreenIntent = fullScreenIntent;
            this.overlay = overlay;
            this.usageStats = usageStats;
            this.accessibility = accessibility;
            this.hardMode = hardMode;
        }

        public String compactNote() {
            return "exact=" + bit(exactAlarm)
                    + " batt=" + bit(batteryUnrestricted)
                    + " notif=" + bit(notifications)
                    + " ch=" + bit(alarmChannelOk)
                    + " fsi=" + bit(fullScreenIntent)
                    + " overlay=" + bit(overlay)
                    + " a11y=" + bit(accessibility)
                    + " usage=" + bit(usageStats)
                    + " gate=" + (hardMode ? "hard" : "soft");
        }

        public Gap firstGap() {
            return firstRequiredGap();
        }

        /**
         * Gaps needed for quizzes to fire on time and open / stay confined.
         * Order matches onboarding / Setup prompts (one at a time).
         */
        public Gap firstRequiredGap() {
            for (Gap g : promptOrder(hardMode)) {
                if (!granted(g)) return g;
            }
            return Gap.NONE;
        }

        /**
         * Gaps that must be fixed or scheduled quizzes silently fail / never appear
         * (or Hard confine cannot hold). Delegates to {@link GatePolicy}.
         */
        public Gap firstCriticalGap() {
            GatePolicy.CriticalGap g = GatePolicy.firstCriticalGap(
                    hardMode, exactAlarm, notifications, alarmChannelOk,
                    batteryUnrestricted, accessibility, overlay);
            switch (g) {
                case EXACT_ALARM: return Gap.EXACT_ALARM;
                case NOTIFICATIONS: return Gap.NOTIFICATIONS;
                case BATTERY: return Gap.BATTERY;
                case ACCESSIBILITY: return Gap.ACCESSIBILITY;
                case OVERLAY: return Gap.OVERLAY;
                default: return Gap.NONE;
            }
        }

        public boolean hasRequiredGap() {
            return firstRequiredGap() != Gap.NONE;
        }

        public boolean hasCriticalGap() {
            return firstCriticalGap() != Gap.NONE;
        }

        boolean granted(Gap g) {
            switch (g) {
                case EXACT_ALARM: return exactAlarm;
                case NOTIFICATIONS: return notifications && alarmChannelOk;
                case BATTERY: return batteryUnrestricted;
                case FULL_SCREEN_INTENT: return fullScreenIntent;
                case OVERLAY: return overlay;
                case USAGE_STATS: return usageStats;
                case ACCESSIBILITY: return accessibility;
                default: return true;
            }
        }

        private static int bit(boolean v) { return v ? 1 : 0; }
    }

    /** One row in the Setup requirements checklist. */
    public static final class CheckItem {
        public final Gap gap;
        public final boolean required;
        public final boolean met;
        public final int titleRes;
        public final int detailRes;

        CheckItem(Gap gap, boolean required, boolean met, int titleRes, int detailRes) {
            this.gap = gap;
            this.required = required;
            this.met = met;
            this.titleRes = titleRes;
            this.detailRes = detailRes;
        }
    }

    private ScheduleAuth() {}

    static Gap[] promptOrder(boolean hardMode) {
        return hardMode ? PROMPT_ORDER_HARD : PROMPT_ORDER_SOFT;
    }

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
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        boolean notif = true;
        if (Build.VERSION.SDK_INT >= 33) {
            notif = ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        if (notif && nm != null && Build.VERSION.SDK_INT >= 24) {
            notif = nm.areNotificationsEnabled();
        }
        boolean channelOk = true;
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            // Ensure channel exists before judging; create is cheap / idempotent when healthy.
            try {
                QuizLaunch.ensureChannel(ctx);
            } catch (Throwable ignored) {}
            NotificationChannel ch = nm.getNotificationChannel(QuizLaunch.CHANNEL);
            channelOk = ch != null
                    && ch.getImportance() >= NotificationManager.IMPORTANCE_HIGH;
        }
        boolean fsi = true;
        if (Build.VERSION.SDK_INT >= 34) {
            fsi = nm != null && nm.canUseFullScreenIntent();
        }
        boolean overlay = true;
        if (Build.VERSION.SDK_INT >= 23) {
            overlay = Settings.canDrawOverlays(ctx);
        }
        boolean usage = ForegroundApp.hasUsageAccess(ctx);
        boolean a11y = GateMode.isAccessibilityEnabled(ctx);
        boolean hard = GateMode.isHard(ctx);
        return new Snapshot(ts, exact, batt, notif, channelOk, fsi, overlay, usage, a11y, hard);
    }

    public static void log(Context ctx, String kind, String trigger, long nextAlarmAt,
                           long intervalMs, long jitterMs) {
        Snapshot s = snapshot(ctx);
        try {
            DiagnosticsStore diag = store(ctx);
            String note = s.compactNote();
            if (!s.exactAlarm) note = note + " mode=inexact";
            String eventKind = kind == null || kind.isEmpty() ? DiagnosticsStore.KIND_PERM : kind;
            DiagnosticsStore.ScheduleEvent ev = new DiagnosticsStore.ScheduleEvent(
                    s.ts, TimeZone.getDefault(), eventKind, trigger, "",
                    nextAlarmAt, intervalMs, jitterMs, null, note);
            ev.gateMode = new AppPrefs(ctx).getGateMode();
            diag.appendScheduleEvent(ev);
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
            case ACCESSIBILITY:
                toast(activity, activity.getString(R.string.setup_req_a11y_toast));
                openAccessibilitySettings(activity);
                break;
            default:
                break;
        }
        return gap;
    }

    public static void appendStatus(StringBuilder sb, Context ctx) {
        Snapshot s = snapshot(ctx);
        if (Build.VERSION.SDK_INT >= 31) {
            sb.append("\nExact alarms: ").append(s.exactAlarm ? "yes" : "NO — tap row");
        }
        if (Build.VERSION.SDK_INT >= 23) {
            sb.append("\nBattery unrestricted: ").append(s.batteryUnrestricted ? "yes" : "NO — tap row");
        }
        if (Build.VERSION.SDK_INT >= 24) {
            sb.append("\nNotifications: ").append(
                    (s.notifications && s.alarmChannelOk) ? "yes" : "NO — tap row");
        }
        if (s.hardMode) {
            sb.append("\nAccessibility (Hard lock): ")
                    .append(s.accessibility ? "yes" : "NO — tap row");
        }
        if (Build.VERSION.SDK_INT >= 23) {
            sb.append("\nAppear on top (required to open over apps): ")
                    .append(s.overlay ? "yes" : "NO — tap row");
        }
        if (Build.VERSION.SDK_INT >= 34) {
            sb.append("\nFull-screen intent: ").append(s.fullScreenIntent ? "yes" : "NO — tap row");
        }
        sb.append("\nUsage access (return to last app): ")
                .append(s.usageStats ? "yes" : "NO — tap row");
    }

    /**
     * Live checklist for Setup / Parents: schedule + mode-specific confine grants.
     * Home widget is appended by Setup (not a Gap).
     */
    public static java.util.List<CheckItem> checklist(Context ctx) {
        Snapshot s = snapshot(ctx);
        java.util.ArrayList<CheckItem> items = new java.util.ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            items.add(new CheckItem(Gap.EXACT_ALARM, true, s.exactAlarm,
                    R.string.setup_req_exact_title, R.string.setup_req_exact_detail));
        }
        if (Build.VERSION.SDK_INT >= 24) {
            items.add(new CheckItem(Gap.NOTIFICATIONS, true,
                    s.notifications && s.alarmChannelOk,
                    R.string.setup_req_notif_title, R.string.setup_req_notif_detail));
        }
        if (Build.VERSION.SDK_INT >= 23) {
            items.add(new CheckItem(Gap.BATTERY, true, s.batteryUnrestricted,
                    R.string.setup_req_battery_title, R.string.setup_req_battery_detail));
        }
        if (s.hardMode) {
            items.add(new CheckItem(Gap.ACCESSIBILITY, true, s.accessibility,
                    R.string.setup_req_a11y_title, R.string.setup_req_a11y_detail));
        }
        if (Build.VERSION.SDK_INT >= 23) {
            items.add(new CheckItem(Gap.OVERLAY, true, s.overlay,
                    R.string.setup_req_overlay_title, R.string.setup_req_overlay_detail));
        }
        if (Build.VERSION.SDK_INT >= 34) {
            items.add(new CheckItem(Gap.FULL_SCREEN_INTENT, true, s.fullScreenIntent,
                    R.string.setup_req_fsi_title, R.string.setup_req_fsi_detail));
        }
        items.add(new CheckItem(Gap.USAGE_STATS, true, s.usageStats,
                R.string.setup_req_usage_title, R.string.setup_req_usage_detail));
        return items;
    }

    /** Plain-language title for onboarding for a gap (0 if none). */
    public static int onboardTitleRes(Gap gap) {
        if (gap == null) return 0;
        switch (gap) {
            case EXACT_ALARM: return R.string.onboard_perm_exact_title;
            case NOTIFICATIONS: return R.string.onboard_perm_notif_title;
            case BATTERY: return R.string.onboard_perm_battery_title;
            case FULL_SCREEN_INTENT: return R.string.onboard_perm_fsi_title;
            case OVERLAY: return R.string.onboard_perm_overlay_title;
            case USAGE_STATS: return R.string.onboard_perm_usage_title;
            case ACCESSIBILITY: return R.string.onboard_perm_a11y_title;
            default: return 0;
        }
    }

    /** Plain-language why-copy for onboarding for a gap (0 if none). */
    public static int onboardBodyRes(Gap gap) {
        if (gap == null) return 0;
        switch (gap) {
            case EXACT_ALARM: return R.string.onboard_perm_exact_body;
            case NOTIFICATIONS: return R.string.onboard_perm_notif_body;
            case BATTERY: return R.string.onboard_perm_battery_body;
            case FULL_SCREEN_INTENT: return R.string.onboard_perm_fsi_body;
            case OVERLAY: return R.string.onboard_perm_overlay_body;
            case USAGE_STATS: return R.string.onboard_perm_usage_body;
            case ACCESSIBILITY: return R.string.onboard_perm_a11y_body;
            default: return 0;
        }
    }

    /** True when this gap is already granted (or not applicable on this OS). */
    public static boolean isGranted(Context ctx, Gap gap) {
        if (gap == null || gap == Gap.NONE) return true;
        return snapshot(ctx).granted(gap);
    }

    /** True when missing this gap means quizzes will not reliably open / hold. */
    public static boolean isCritical(Gap gap) {
        return isCritical(null, gap);
    }

    public static boolean isCritical(Context ctx, Gap gap) {
        if (gap == Gap.EXACT_ALARM || gap == Gap.NOTIFICATIONS || gap == Gap.BATTERY) return true;
        boolean hard = ctx != null && GateMode.isHard(ctx);
        if (gap == Gap.OVERLAY) {
            // Soft: overlay critical. Hard: still useful for launch but Accessibility is critical.
            // When ctx is null (legacy callers), treat overlay as critical (soft-safe default).
            return ctx == null || GatePolicy.isOverlayCritical(hard);
        }
        if (gap == Gap.ACCESSIBILITY) {
            // When ctx is null, treat a11y as critical (hard-safe / Play-default path).
            return ctx == null || GatePolicy.isAccessibilityCritical(hard);
        }
        return false;
    }

    /**
     * Next unmet gap after {@code after} in prompt order, or NONE.
     * Used by onboarding to walk one permission at a time without Settings spam.
     * Pass NONE/null to get the first unmet gap.
     */
    public static Gap nextGapAfter(Context ctx, Gap after) {
        boolean past = (after == null || after == Gap.NONE);
        boolean hard = GateMode.isHard(ctx);
        for (Gap g : promptOrder(hard)) {
            if (!past) {
                if (g == after) past = true;
                continue;
            }
            // isGranted is true when N/A on this OS (snapshot defaults).
            if (!isGranted(ctx, g)) return g;
        }
        return Gap.NONE;
    }

    /** Parent-friendly bullet list of unmet required items (empty if all set). */
    public static String missingRequiredSummary(Context ctx) {
        StringBuilder sb = new StringBuilder();
        for (CheckItem item : checklist(ctx)) {
            if (!item.required || item.met) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append("• ").append(ctx.getString(item.titleRes));
            if (isCritical(ctx, item.gap)) {
                sb.append(" — ").append(ctx.getString(R.string.setup_req_badge_critical));
            }
        }
        return sb.toString();
    }

    /** Unmet launch-critical items only. */
    public static String missingCriticalSummary(Context ctx) {
        StringBuilder sb = new StringBuilder();
        for (CheckItem item : checklist(ctx)) {
            if (!item.required || item.met || !isCritical(ctx, item.gap)) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append("• ").append(ctx.getString(item.titleRes));
        }
        return sb.toString();
    }

    /** Open Settings / permission UI for a specific gap (or next gap if NONE). */
    public static Gap openGap(Activity activity, Gap gap) {
        if (gap == null || gap == Gap.NONE) return promptNextGap(activity);
        Snapshot s = snapshot(activity);
        switch (gap) {
            case EXACT_ALARM:
                if (s.exactAlarm) return Gap.NONE;
                break;
            case NOTIFICATIONS:
                if (s.granted(Gap.NOTIFICATIONS)) return Gap.NONE;
                break;
            case BATTERY:
                if (s.batteryUnrestricted) return Gap.NONE;
                break;
            case FULL_SCREEN_INTENT:
                if (s.fullScreenIntent) return Gap.NONE;
                break;
            case OVERLAY:
                if (s.overlay) return Gap.NONE;
                break;
            case USAGE_STATS:
                if (s.usageStats) return Gap.NONE;
                break;
            case ACCESSIBILITY:
                if (s.accessibility) return Gap.NONE;
                break;
            default:
                return Gap.NONE;
        }
        AppPrefs prefs = new AppPrefs(activity);
        prefs.setLastAuthPrompt(gap.name(), s.ts);
        switch (gap) {
            case EXACT_ALARM:
                toast(activity, activity.getString(R.string.setup_req_exact_toast));
                openExactAlarmSettings(activity);
                break;
            case NOTIFICATIONS:
                requestNotifications(activity);
                break;
            case BATTERY:
                toast(activity, activity.getString(R.string.setup_req_battery_toast));
                openBatterySettings(activity);
                break;
            case FULL_SCREEN_INTENT:
                toast(activity, activity.getString(R.string.setup_req_fsi_toast));
                openFullScreenIntentSettings(activity);
                break;
            case OVERLAY:
                toast(activity, activity.getString(R.string.setup_req_overlay_toast));
                openOverlaySettings(activity);
                break;
            case USAGE_STATS:
                toast(activity, activity.getString(R.string.setup_req_usage_toast));
                ForegroundApp.openUsageAccessSettings(activity);
                break;
            case ACCESSIBILITY:
                toast(activity, activity.getString(R.string.setup_req_a11y_toast));
                openAccessibilitySettings(activity);
                break;
            default:
                break;
        }
        return gap;
    }

    private static void requestNotifications(Activity activity) {
        Snapshot s = snapshot(activity);
        // Permission may be granted but the app toggle or channel demoted — open settings.
        if (Build.VERSION.SDK_INT >= 33) {
            boolean runtime = activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
            if (!runtime) {
                if (!activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                        && new AppPrefs(activity).wasNotificationDeniedOnce()) {
                    toast(activity, activity.getString(R.string.setup_req_notif_toast));
                    openAppNotificationSettings(activity);
                    return;
                }
                activity.requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
                return;
            }
        }
        if (!s.notifications || !s.alarmChannelOk) {
            toast(activity, activity.getString(R.string.setup_req_notif_toast));
            openAppNotificationSettings(activity);
        }
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

    public static void openAccessibilitySettings(Context ctx) {
        try {
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
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
