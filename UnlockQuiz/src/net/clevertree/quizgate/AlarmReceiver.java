package net.clevertree.quizgate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import net.clevertree.quizgate.core.DiagnosticsStore;

import java.util.TimeZone;

/**
 * AlarmManager target (setAlarmClock broadcast). Hands off to {@link QuizLaunch}
 * which uses Appear-on-top for BAL-safe QuizActivity starts, plus FSI notification.
 * {@link AlarmGateActivity} remains available as an alternate trampoline.
 */
public class AlarmReceiver extends BroadcastReceiver {
    public static final String ACTION_INTERVAL = "net.clevertree.quizgate.ACTION_INTERVAL";
    public static final String ACTION_MORNING = "net.clevertree.quizgate.ACTION_MORNING";
    public static final String ACTION_DEBUG_FIRE = "net.clevertree.quizgate.ACTION_DEBUG_FIRE";

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        String trigger;
        if (ACTION_MORNING.equals(action)) trigger = "morning";
        else if (ACTION_DEBUG_FIRE.equals(action)) trigger = "adb_force";
        else trigger = "interval";
        long now = System.currentTimeMillis();
        AppPrefs prefs = new AppPrefs(context);
        Log.i(AlarmScheduler.TAG, "alarm broadcast trigger=" + trigger + " action=" + action);

        if (ACTION_MORNING.equals(action)) {
            net.clevertree.quizgate.core.Scheduler sched = AlarmScheduler.scheduler(context);
            if (!sched.needsMorningGate(now, prefs.lastClearedDay())) {
                Log.i(AlarmScheduler.TAG, "morning already cleared today, skip quiz");
                long nextMorning = sched.nextMorningAlarm(now);
                AlarmScheduler.scheduleMorning(context, nextMorning);
                Diag.schedule(context, new DiagnosticsStore.ScheduleEvent(
                        now, TimeZone.getDefault(), DiagnosticsStore.KIND_SKIP_FIRE, trigger,
                        "lock", nextMorning, prefs.getIntervalMs(), prefs.getJitterMs(),
                        null, "morning_already_cleared"));
                return;
            }
        }
        Diag.schedule(context, new DiagnosticsStore.ScheduleEvent(
                now, TimeZone.getDefault(), DiagnosticsStore.KIND_ALARM_FIRED, trigger,
                "lock", prefs.nextAlarmAt(), prefs.getIntervalMs(), prefs.getJitterMs(),
                null, "path=broadcast"));
        ForegroundApp.captureNow(context);
        try {
            new MediaGate().pauseForQuiz(context);
        } catch (Exception e) {
            Log.e(AlarmScheduler.TAG, "pre-quiz media pause", e);
            Diag.nonFatal(context, "media_pause", e);
        }
        QuizLaunch.fromAlarm(context, trigger);
    }
}
