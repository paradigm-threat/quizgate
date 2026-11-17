package net.clevertree.quizgate;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

import net.clevertree.quizgate.core.DiagnosticsStore;

import java.util.TimeZone;

/**
 * AlarmManager target activity. System-sent activity PendingIntents from
 * {@code setAlarmClock} are BAL-allowed; a same-UID {@code PendingIntent.send()}
 * from a receiver is not (Android 15+). This trampoline runs privileged, then
 * starts {@link QuizActivity} while it holds a visible window.
 */
public class AlarmGateActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        Intent in = getIntent();
        String action = in != null ? in.getAction() : null;
        String trigger;
        if (AlarmReceiver.ACTION_MORNING.equals(action)) trigger = "morning";
        else if (AlarmReceiver.ACTION_DEBUG_FIRE.equals(action)) trigger = "adb_force";
        else trigger = "interval";

        long now = System.currentTimeMillis();
        AppPrefs prefs = new AppPrefs(this);
        Log.i(AlarmScheduler.TAG, "alarm gate trigger=" + trigger + " action=" + action);

        if (AlarmReceiver.ACTION_MORNING.equals(action)) {
            net.clevertree.quizgate.core.Scheduler sched = AlarmScheduler.scheduler(this);
            if (!sched.needsMorningGate(now, prefs.lastClearedDay())) {
                Log.i(AlarmScheduler.TAG, "morning already cleared today, skip quiz");
                long nextMorning = sched.nextMorningAlarm(now);
                AlarmScheduler.scheduleMorning(this, nextMorning);
                Diag.schedule(this, new DiagnosticsStore.ScheduleEvent(
                        now, TimeZone.getDefault(), DiagnosticsStore.KIND_SKIP_FIRE, trigger,
                        "lock", nextMorning, prefs.getIntervalMs(), prefs.getJitterMs(),
                        null, "morning_already_cleared"));
                finish();
                return;
            }
        }

        Diag.schedule(this, new DiagnosticsStore.ScheduleEvent(
                now, TimeZone.getDefault(), DiagnosticsStore.KIND_ALARM_FIRED, trigger,
                "lock", prefs.nextAlarmAt(), prefs.getIntervalMs(), prefs.getJitterMs(),
                null, "path=alarm_gate"));
        if (!AlarmReceiver.ACTION_MORNING.equals(action)) {
            AlarmScheduler.scheduleAfterFire(this);
        } else {
            prefs.setLastFiredAt(now);
            net.clevertree.quizgate.core.Scheduler sched = AlarmScheduler.scheduler(this);
            AlarmScheduler.scheduleMorning(this, sched.nextMorningAlarm(now));
        }
        ForegroundApp.captureNow(this);
        try {
            new MediaGate().pauseForQuiz(this);
        } catch (Exception e) {
            Log.e(AlarmScheduler.TAG, "pre-quiz media pause", e);
            Diag.nonFatal(this, "media_pause", e);
        }

        Intent quiz = QuizLaunch.quizIntent(this, trigger);
        Diag.schedule(this, new DiagnosticsStore.ScheduleEvent(
                System.currentTimeMillis(), TimeZone.getDefault(),
                DiagnosticsStore.KIND_LAUNCH_ATTEMPT, trigger, "lock",
                prefs.nextAlarmAt(), prefs.getIntervalMs(), prefs.getJitterMs(),
                null, "path=alarm_gate"));
        try {
            startActivity(quiz);
            Diag.schedule(this, new DiagnosticsStore.ScheduleEvent(
                    System.currentTimeMillis(), TimeZone.getDefault(),
                    DiagnosticsStore.KIND_LAUNCH_ATTEMPT, trigger, "lock",
                    prefs.nextAlarmAt(), prefs.getIntervalMs(), prefs.getJitterMs(),
                    null, "path=alarm_gate"));
            Log.i(AlarmScheduler.TAG, "alarm gate started QuizActivity trigger=" + trigger);
            finish();
            return;
        } catch (Exception e) {
            Log.e(AlarmScheduler.TAG, "alarm gate startActivity failed — QuizLaunch", e);
        }
        // Overlay + FSI path when BAL blocked the trampoline→quiz handoff.
        QuizLaunch.fromAlarm(this, trigger);
        finish();
    }
}
