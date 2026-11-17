package net.clevertree.quizgate;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import net.clevertree.quizgate.core.DiagnosticsStore;

import java.util.TimeZone;

/**
 * Foreground-service reinforcement for quiz launch under BAL.
 * Posts the FSI notification via {@link QuizLaunch} and sends the PendingIntent.
 */
public class QuizTriggerService extends Service {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String trigger = intent != null ? intent.getStringExtra(QuizActivity.EXTRA_TRIGGER) : "interval";
        if (trigger == null) trigger = "interval";
        AppPrefs prefs = new AppPrefs(this);
        final String trig = trigger;
        final AppPrefs p = prefs;
        final int sid = startId;
        // Promote to FGS immediately (platform deadline), then settle pause before
        // sending the full-screen PendingIntent so PiP is less likely to spawn mid-send.
        try {
            startForeground(QuizLaunch.NOTIF_ID,
                    QuizLaunch.buildForegroundNotification(this, trig));
        } catch (Exception e) {
            Log.e(AlarmScheduler.TAG, "FGS startForeground", e);
        }

        Runnable sendFsi = () -> {
            long now = System.currentTimeMillis();
            Diag.schedule(QuizTriggerService.this, new DiagnosticsStore.ScheduleEvent(
                    now, TimeZone.getDefault(), DiagnosticsStore.KIND_LAUNCH_ATTEMPT, trig,
                    "lock", p.nextAlarmAt(), p.getIntervalMs(), p.getJitterMs(),
                    null, "path=fgs"));

            boolean ok = QuizLaunch.sendFullScreenFromService(QuizTriggerService.this, trig);
            if (ok) {
                // PendingIntent.send success ≠ QuizActivity started (was mislabeled launch_ok).
                Diag.schedule(QuizTriggerService.this, new DiagnosticsStore.ScheduleEvent(
                        System.currentTimeMillis(), TimeZone.getDefault(),
                        DiagnosticsStore.KIND_LAUNCH_ATTEMPT, trig, "lock",
                        p.nextAlarmAt(), p.getIntervalMs(), p.getJitterMs(),
                        null, "path=fgs_fsi_send"));
                Log.i(AlarmScheduler.TAG, "FGS FSI send ok trigger=" + trig);
            } else {
                Diag.schedule(QuizTriggerService.this, new DiagnosticsStore.ScheduleEvent(
                        System.currentTimeMillis(), TimeZone.getDefault(),
                        DiagnosticsStore.KIND_BAL_BLOCKED, trig, "lock",
                        p.nextAlarmAt(), p.getIntervalMs(), p.getJitterMs(),
                        null, "path=fgs_fsi_send_fail"));
                Log.e(AlarmScheduler.TAG, "FGS FSI send failed trigger=" + trig);
            }

            long stopAfter = PerformanceMode.fgsStopAfterMs(QuizTriggerService.this);
            handler.postDelayed(() -> {
                try {
                    stopForeground(Service.STOP_FOREGROUND_DETACH);
                } catch (Exception ignored) {
                    try { stopForeground(false); } catch (Exception ignored2) {}
                }
                stopSelf(sid);
            }, stopAfter);
        };

        // Balanced: reinforce capture + settle pause before FSI send. Slim: AlarmReceiver
        // already settled; skip extra usage-stats / media wait in FGS.
        if (!PerformanceMode.isSlim(prefs)) {
            ForegroundApp.captureNow(this);
            try {
                new MediaGate().pauseThenSettle(this, sendFsi);
                return START_NOT_STICKY;
            } catch (Exception e) {
                Log.e(AlarmScheduler.TAG, "FGS media pause", e);
            }
        }
        sendFsi.run();
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
