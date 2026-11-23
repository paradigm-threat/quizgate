package net.clevertree.quizgate;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import net.clevertree.quizgate.core.A11yArmPolicy;
import net.clevertree.quizgate.core.DiagnosticsStore;

import java.util.TimeZone;

/**
 * Keeps an armed schedule from dying silently when Accessibility drops.
 *
 * <p>Arm intent stays in prefs. Wake alarms are cancelled and a persistent
 * (non-alarm) notification is posted while Accessibility is off. Restoring the
 * service, or the secure setting, re-arms through
 * {@link AlarmScheduler#armWhileInteractive} only while interactive and not
 * pending. Does not turn the screen on.
 */
public final class A11yArm {
    public static final String CHANNEL = "quizgate_schedule_paused";
    public static final int NOTIF_ID = 77;
    private static final long BLOCK_LOG_INTERVAL_MS = 60L * 60L * 1000L;

    private static volatile long lastBlockLogAt;
    private static ContentObserver settingsObserver;

    private A11yArm() {}

    /** Persist arm intent when a next-alarm survived but the armed flag did not. */
    public static void healArmIntent(AppPrefs prefs) {
        if (prefs == null || prefs.isArmed()) return;
        if (A11yArmPolicy.hasArmIntent(false, prefs.nextAlarmAt())) {
            prefs.setArmed(true);
            Log.i(AlarmScheduler.TAG, "healed arm intent from next_alarm_at");
        }
    }

    /**
     * Apply the current Accessibility state: pause + notify, or re-arm when the
     * interactive / not-pending gate allows it.
     */
    public static void reconcile(Context ctx, String reason) {
        if (ctx == null) return;
        Context app = ctx.getApplicationContext();
        AppPrefs prefs = new AppPrefs(app);
        healArmIntent(prefs);
        boolean armed = prefs.isArmed();
        boolean a11y = GateMode.isAccessibilityEnabled(app);
        boolean interactive = ScreenLockGate.isInteractive(app);
        boolean pending = prefs.isPendingScreenLock();
        if (!armed) {
            clearBlocked(app);
            return;
        }
        if (A11yArmPolicy.shouldCancelAlarmsForA11yOff(armed, a11y)) {
            AlarmScheduler.cancelAllAlarms(app);
            showBlocked(app, reason);
            Log.i(AlarmScheduler.TAG, "a11y off — alarms cancelled, arm intent kept reason="
                    + reason);
            return;
        }
        clearBlocked(app);
        if (A11yArmPolicy.shouldRearmAfterA11yRestored(armed, a11y, interactive, pending)) {
            AlarmScheduler.armWhileInteractive(app, reason == null ? "a11y_restored" : reason);
        }
    }

    /** Watch {@code ENABLED_ACCESSIBILITY_SERVICES} for the life of this process. */
    public static void registerSettingsWatch(Context ctx) {
        if (ctx == null || settingsObserver != null) return;
        Context app = ctx.getApplicationContext();
        Uri uri = Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        settingsObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(boolean selfChange) {
                reconcile(app, "a11y_setting");
            }
        };
        try {
            app.getContentResolver().registerContentObserver(uri, false, settingsObserver);
            Log.i(AlarmScheduler.TAG, "a11y settings watch on");
        } catch (Exception e) {
            settingsObserver = null;
            Log.w(AlarmScheduler.TAG, "a11y settings watch failed", e);
        }
    }

    public static void showBlocked(Context ctx, String reason) {
        if (ctx == null) return;
        Context app = ctx.getApplicationContext();
        postPausedNotification(app);
        long now = System.currentTimeMillis();
        if (now - lastBlockLogAt < BLOCK_LOG_INTERVAL_MS) return;
        lastBlockLogAt = now;
        AppPrefs prefs = new AppPrefs(app);
        String note = "a11y_off reason=" + (reason == null ? "" : reason);
        try {
            note = ScheduleAuth.snapshot(app).compactNote() + " " + note;
        } catch (Throwable ignored) {}
        try {
            Diag.schedule(app, new DiagnosticsStore.ScheduleEvent(
                    now, TimeZone.getDefault(), DiagnosticsStore.KIND_ARM_BLOCKED,
                    reason == null ? "a11y" : reason, "lock",
                    0L, prefs.getIntervalMs(), prefs.getJitterMs(), null, note));
        } catch (Throwable t) {
            Log.w(AlarmScheduler.TAG, "arm_blocked diag", t);
        }
        Log.i(AlarmScheduler.TAG, "schedule paused — accessibility off " + note);
    }

    public static void clearBlocked(Context ctx) {
        if (ctx == null) return;
        lastBlockLogAt = 0L;
        try {
            NotificationManager nm = ctx.getApplicationContext()
                    .getSystemService(NotificationManager.class);
            if (nm != null) nm.cancel(NOTIF_ID);
        } catch (Exception e) {
            Log.w(AlarmScheduler.TAG, "clear a11y pause notif", e);
        }
    }

    private static void postPausedNotification(Context app) {
        try {
            ensureChannel(app);
            Intent open = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(app, NOTIF_ID, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            String text = app.getString(R.string.a11y_arm_text);
            Notification.Builder b = new Notification.Builder(app, CHANNEL)
                    .setContentTitle(app.getString(R.string.a11y_arm_title))
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setSmallIcon(R.drawable.ic_stat_quizgate)
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(false)
                    .setCategory(Notification.CATEGORY_STATUS)
                    .setVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager nm = app.getSystemService(NotificationManager.class);
            if (nm != null) nm.notify(NOTIF_ID, b.build());
        } catch (Exception e) {
            Log.w(AlarmScheduler.TAG, "a11y pause notif failed", e);
        }
    }

    private static void ensureChannel(Context app) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = app.getSystemService(NotificationManager.class);
        if (nm == null) return;
        // LOW: ongoing and visible in the shade, but no heads-up. A default/high
        // alarm-style notification can light the screen; power-off must stay off.
        NotificationChannel ch = new NotificationChannel(CHANNEL,
                app.getString(R.string.a11y_arm_channel),
                NotificationManager.IMPORTANCE_LOW);
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }
}
