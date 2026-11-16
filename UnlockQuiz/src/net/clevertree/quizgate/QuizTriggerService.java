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
    private static final long STOP_AFTER_MS = 8_000L;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String trigger = intent != null ? intent.getStringExtra(QuizActivity.EXTRA_TRIGGER) : "interval";
        if (trigger == null) trigger = "interval";
        ForegroundApp.captureNow(this);
        try {
            new MediaGate().pauseForQuiz(this);
        } catch (Exception e) {
            Log.e(AlarmScheduler.TAG, "FGS media pause", e);
        }

        AppPrefs prefs = new AppPrefs(this);
        long now = System.currentTimeMillis();
        Diag.schedule(this, new DiagnosticsStore.ScheduleEvent(
                now, TimeZone.getDefault(), DiagnosticsStore.KIND_LAUNCH_ATTEMPT, trigger,
                "lock", prefs.nextAlarmAt(), prefs.getIntervalMs(), prefs.getJitterMs(),
                null, "path=fgs"));

        boolean ok = QuizLaunch.launchFromService(this, trigger);
        if (ok) {
            Diag.schedule(this, new DiagnosticsStore.ScheduleEvent(
                    System.currentTimeMillis(), TimeZone.getDefault(),
                    DiagnosticsStore.KIND_LAUNCH_OK, trigger, "lock",
                    prefs.nextAlarmAt(), prefs.getIntervalMs(), prefs.getJitterMs(),
                    null, "path=fgs_fsi_send"));
            Log.i(AlarmScheduler.TAG, "FGS FSI send ok trigger=" + trigger);
        } else {
            Diag.schedule(this, new DiagnosticsStore.ScheduleEvent(
                    System.currentTimeMillis(), TimeZone.getDefault(),
                    DiagnosticsStore.KIND_BAL_BLOCKED, trigger, "lock",
                    prefs.nextAlarmAt(), prefs.getIntervalMs(), prefs.getJitterMs(),
                    null, "path=fgs_fsi_send_fail"));
            Log.e(AlarmScheduler.TAG, "FGS FSI send failed trigger=" + trigger);
        }

        final int sid = startId;
        handler.postDelayed(() -> {
            try {
                stopForeground(Service.STOP_FOREGROUND_DETACH);
            } catch (Exception ignored) {
                try { stopForeground(false); } catch (Exception ignored2) {}
            }
            stopSelf(sid);
        }, STOP_AFTER_MS);
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
