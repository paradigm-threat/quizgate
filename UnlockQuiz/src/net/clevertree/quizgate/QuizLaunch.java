package net.clevertree.quizgate;

import android.app.ActivityOptions;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import net.clevertree.quizgate.core.DiagnosticsStore;

import java.util.TimeZone;

/**
 * BAL-safe quiz launch for Android 10+/15+/Samsung:
 * <ol>
 *   <li>Post HIGH / CATEGORY_ALARM notification with full-screen intent</li>
 *   <li>PendingIntent opts into background activity starts (Android 14+)</li>
 *   <li>Explicitly {@code send()} the FSI PendingIntent after notify — screen-on
 *       OEMs often demote FSI to heads-up without launching</li>
 * </ol>
 */
public final class QuizLaunch {
    public static final String CHANNEL = "unlock_quiz_alarm";
    public static final int NOTIF_ID = 42;

    private QuizLaunch() {}

    /**
     * From {@link AlarmReceiver} after {@link MediaGate#pauseThenSettle}: Soft path uses
     * FSI notification + overlay/FSI bring-up (BAL-safe). Hard path starts a hard session
     * and prefers a direct QuizActivity start (Accessibility confines afterward).
     */
    public static void fromAlarm(Context ctx, String trigger) {
        Context app = ctx.getApplicationContext();
        if (trigger == null || trigger.isEmpty()) trigger = "interval";
        long now = System.currentTimeMillis();
        AppPrefs prefs = new AppPrefs(app);

        if (GateMode.isHard(app)) {
            HardGate.startSession(app);
            ensureChannel(app);
            Intent quiz = quizIntent(app, trigger);
            PendingIntent pi = activityPi(app, quiz);
            Notification n = buildNotification(app, pi);
            NotificationManager nm = app.getSystemService(NotificationManager.class);
            if (nm != null) nm.notify(NOTIF_ID, n);
            log(app, now, DiagnosticsStore.KIND_LAUNCH_ATTEMPT, trigger, prefs, "path=hard_direct");
            try {
                app.startActivity(quiz);
                log(app, System.currentTimeMillis(), DiagnosticsStore.KIND_LAUNCH_ATTEMPT, trigger,
                        prefs, "path=hard_start");
                Log.i(AlarmScheduler.TAG, "QuizLaunch hard direct start ok trigger=" + trigger);
                return;
            } catch (Exception e) {
                Log.w(AlarmScheduler.TAG, "QuizLaunch hard start failed — soft fallback", e);
                // Keep hard session; fall through to overlay/FSI/FGS reinforce.
            }
        }

        ensureChannel(app);
        Intent quiz = quizIntent(app, trigger);
        PendingIntent pi = activityPi(app, quiz);

        Notification n = buildNotification(app, pi);
        NotificationManager nm = app.getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIF_ID, n);
        }
        log(app, now, DiagnosticsStore.KIND_LAUNCH_ATTEMPT, trigger, prefs, "path=fsi_notif");

        // Appear-on-top is the reliable BAL exemption on Android 10+/16 when another
        // app is foreground (FSI often demotes to heads-up; same-UID PI.send is denied).
        boolean overlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(app);
        if (overlay) {
            try {
                app.startActivity(quiz);
                // startActivity returning is not proof the quiz UI ran — QuizActivity
                // logs launch_ok with path=activity_visible when onCreate reaches the quiz.
                log(app, System.currentTimeMillis(), DiagnosticsStore.KIND_LAUNCH_ATTEMPT, trigger, prefs,
                        "path=overlay_direct");
                Log.i(AlarmScheduler.TAG, "QuizLaunch overlay direct start ok trigger=" + trigger);
                return;
            } catch (Exception e) {
                log(app, System.currentTimeMillis(), DiagnosticsStore.KIND_BAL_BLOCKED, trigger, prefs,
                        "path=overlay_throw");
                Log.w(AlarmScheduler.TAG, "QuizLaunch overlay start failed", e);
            }
        } else {
            log(app, System.currentTimeMillis(), DiagnosticsStore.KIND_BAL_BLOCKED, trigger, prefs,
                    "path=need_overlay");
            Log.w(AlarmScheduler.TAG, "QuizLaunch missing Appear on top — FSI/FGS fallback");
        }

        boolean sent = sendFullScreen(app, pi);
        log(app, System.currentTimeMillis(),
                sent ? DiagnosticsStore.KIND_LAUNCH_ATTEMPT : DiagnosticsStore.KIND_BAL_BLOCKED,
                trigger, prefs, sent ? "path=fsi_send" : "path=fsi_send_fail");

        try {
            Intent svc = new Intent(app, QuizTriggerService.class);
            svc.putExtra(QuizActivity.EXTRA_TRIGGER, trigger);
            if (Build.VERSION.SDK_INT >= 26) {
                app.startForegroundService(svc);
            } else {
                app.startService(svc);
            }
            log(app, System.currentTimeMillis(), DiagnosticsStore.KIND_LAUNCH_ATTEMPT, trigger,
                    prefs, "path=fgs");
        } catch (Exception e) {
            Diag.nonFatal(app, "quiz_launch_fgs", e);
            Log.e(AlarmScheduler.TAG, "QuizLaunch FGS reinforce failed", e);
        }
    }

    /** Used by {@link QuizTriggerService} for the foreground notification + send. */
    public static boolean launchFromService(Context ctx, String trigger) {
        ensureChannel(ctx);
        Intent quiz = quizIntent(ctx, trigger);
        PendingIntent pi = activityPi(ctx, quiz);
        Notification n = buildNotification(ctx, pi);
        if (ctx instanceof android.app.Service) {
            ((android.app.Service) ctx).startForeground(NOTIF_ID, n);
        } else {
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null) nm.notify(NOTIF_ID, n);
        }
        return sendFullScreen(ctx, pi);
    }

    /**
     * FSI PendingIntent.send only — caller must already be a started foreground
     * service (see {@link QuizTriggerService}).
     */
    public static boolean sendFullScreenFromService(Context ctx, String trigger) {
        ensureChannel(ctx);
        Intent quiz = quizIntent(ctx, trigger);
        PendingIntent pi = activityPi(ctx, quiz);
        // Refresh the ongoing alarm notification in case channel/text changed.
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(ctx, pi));
        return sendFullScreen(ctx, pi);
    }

    public static Notification buildForegroundNotification(Context ctx, String trigger) {
        ensureChannel(ctx);
        Intent quiz = quizIntent(ctx, trigger);
        return buildNotification(ctx, activityPi(ctx, quiz));
    }

    public static Intent quizIntent(Context ctx, String trigger) {
        Intent quiz = new Intent(ctx, QuizActivity.class);
        quiz.putExtra(QuizActivity.EXTRA_TRIGGER, trigger);
        quiz.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        return quiz;
    }

    public static void cancelNotification(Context ctx) {
        try {
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null) nm.cancel(NOTIF_ID);
        } catch (Exception ignored) {}
    }

    public static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel existing = nm.getNotificationChannel(CHANNEL);
        // Recreate if importance was demoted or alarm attributes are missing.
        if (existing != null
                && existing.getImportance() >= NotificationManager.IMPORTANCE_HIGH
                && existing.canBypassDnd()) {
            return;
        }
        if (existing != null) {
            try {
                nm.deleteNotificationChannel(CHANNEL);
            } catch (SecurityException e) {
                // Samsung/Android: cannot delete a channel while an FGS still uses it.
                // Keep launching with the existing channel rather than aborting the quiz.
                Log.w(AlarmScheduler.TAG, "QuizLaunch keep channel (FGS holds it)", e);
                return;
            }
        }
        NotificationChannel ch = new NotificationChannel(CHANNEL,
                ctx.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Toddler unlock quiz triggers");
        ch.setBypassDnd(true);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ch.enableVibration(true);
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        ch.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, aa);
        nm.createNotificationChannel(ch);
    }

    static boolean sendFullScreen(Context ctx, PendingIntent pi) {
        try {
            Bundle opts = senderStartOptions();
            if (opts != null) {
                pi.send(ctx, 0, null, null, null, null, opts);
            } else {
                pi.send();
            }
            Log.i(AlarmScheduler.TAG, "QuizLaunch FSI PendingIntent.send ok");
            return true;
        } catch (PendingIntent.CanceledException e) {
            Log.e(AlarmScheduler.TAG, "QuizLaunch FSI PendingIntent canceled", e);
            return false;
        } catch (Exception e) {
            Log.e(AlarmScheduler.TAG, "QuizLaunch FSI PendingIntent.send failed", e);
            return false;
        }
    }

    private static Bundle senderStartOptions() {
        if (Build.VERSION.SDK_INT < 34) return null;
        try {
            ActivityOptions opts = ActivityOptions.makeBasic();
            int mode = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED;
            if (Build.VERSION.SDK_INT >= 36) mode = 3; // ALLOW_ALWAYS
            opts.setPendingIntentBackgroundActivityStartMode(mode);
            return opts.toBundle();
        } catch (Throwable t) {
            return backgroundStartOptions();
        }
    }

    private static Notification buildNotification(Context ctx, PendingIntent pi) {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(ctx, CHANNEL);
        } else {
            b = new Notification.Builder(ctx);
        }
        return b.setContentTitle(ctx.getString(R.string.notif_title))
                .setContentText(ctx.getString(R.string.notif_text))
                .setSmallIcon(R.drawable.ic_stat_quizgate)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setOngoing(true)
                .build();
    }

    private static PendingIntent activityPi(Context ctx, Intent quiz) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        Bundle creatorOpts = backgroundStartOptions();
        if (creatorOpts != null && Build.VERSION.SDK_INT >= 34) {
            return PendingIntent.getActivity(ctx, NOTIF_ID, quiz, flags, creatorOpts);
        }
        return PendingIntent.getActivity(ctx, NOTIF_ID, quiz, flags);
    }

    /**
     * Opt PendingIntent into background activity starts (required Android 14+/15 BAL).
     * API 36+ uses ALLOW_ALWAYS so AlarmManager/system-sent PIs can launch while the
     * app is fully backgrounded (ALLOWED alone is insufficient on Android 16).
     */
    public static Bundle backgroundStartOptions() {
        if (Build.VERSION.SDK_INT < 34) return null;
        try {
            ActivityOptions opts = ActivityOptions.makeBasic();
            int mode = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED;
            // ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS = 3 (API 36)
            if (Build.VERSION.SDK_INT >= 36) mode = 3;
            opts.setPendingIntentCreatorBackgroundActivityStartMode(mode);
            return opts.toBundle();
        } catch (Throwable t) {
            Log.w(AlarmScheduler.TAG, "background start options unavailable", t);
            return null;
        }
    }

    private static void log(Context ctx, long ts, String kind, String trigger, AppPrefs prefs,
                            String note) {
        Diag.schedule(ctx, new DiagnosticsStore.ScheduleEvent(
                ts, TimeZone.getDefault(), kind, trigger, "lock",
                prefs.nextAlarmAt(), prefs.getIntervalMs(), prefs.getJitterMs(),
                null, note == null ? "" : note));
        Log.i(AlarmScheduler.TAG, kind + " trigger=" + trigger + " " + note);
    }
}
