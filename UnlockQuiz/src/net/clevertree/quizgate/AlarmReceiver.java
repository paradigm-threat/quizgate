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

        // Never wake / launch a quiz while the screen is off — pend lock instead.
        if (net.clevertree.quizgate.core.ScreenLockPolicy.shouldSkipFireWhileOff(
                ScreenLockGate.isInteractive(context))) {
            prefs.setPendingScreenLock(true);
            AlarmScheduler.cancelAllAlarms(context);
            Diag.schedule(context, new DiagnosticsStore.ScheduleEvent(
                    now, TimeZone.getDefault(), DiagnosticsStore.KIND_SKIP_FIRE, trigger,
                    "lock", 0L, prefs.getIntervalMs(), prefs.getJitterMs(),
                    null, "screen_off"));
            if (!GateMode.isAccessibilityEnabled(context)) {
                A11yArm.showBlocked(context, "alarm_screen_off");
            }
            Log.i(AlarmScheduler.TAG, "alarm skip_fire screen_off trigger=" + trigger);
            return;
        }

        if (ACTION_MORNING.equals(action)) {
            net.clevertree.quizgate.core.Scheduler sched = AlarmScheduler.scheduler(context);
            if (!sched.needsMorningGate(now, prefs.lastClearedDay())) {
                Log.i(AlarmScheduler.TAG, "morning already cleared today, skip quiz");
                if (AlarmScheduler.blockIfA11yOff(context, "morning_cleared")) return;
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
        // Re-arm before launch so an abandoned / never-started quiz cannot kill the chain.
        if (!ACTION_MORNING.equals(action)) {
            AlarmScheduler.scheduleAfterFire(context);
        } else {
            prefs.setLastFiredAt(now);
            if (!AlarmScheduler.blockIfA11yOff(context, "morning")) {
                net.clevertree.quizgate.core.Scheduler sched = AlarmScheduler.scheduler(context);
                AlarmScheduler.scheduleMorning(context, sched.nextMorningAlarm(now));
            }
        }
        ForegroundApp.captureNow(context);
        // Pause while the player is still foreground, settle, then bring quiz up —
        // avoids YouTube auto-enter system PiP on the task switch when possible.
        final PendingResult async = goAsync();
        final String trig = trigger;
        final Context app = context.getApplicationContext();
        try {
            new MediaGate().pauseThenSettle(app, () -> {
                try {
                    QuizLaunch.fromAlarm(app, trig);
                } finally {
                    try { async.finish(); } catch (Exception ignored) {}
                }
            });
        } catch (Exception e) {
            Log.e(AlarmScheduler.TAG, "pre-quiz media pause", e);
            Diag.nonFatal(app, "media_pause", e);
            try {
                QuizLaunch.fromAlarm(app, trig);
            } finally {
                try { async.finish(); } catch (Exception ignored) {}
            }
        }
    }
}
