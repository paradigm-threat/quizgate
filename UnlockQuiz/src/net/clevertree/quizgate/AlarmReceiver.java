package net.clevertree.quizgate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

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
        Log.i(AlarmScheduler.TAG, "alarm fired trigger=" + trigger + " action=" + action);
        if (ACTION_MORNING.equals(action)) {
            AppPrefs prefs = new AppPrefs(context);
            net.clevertree.quizgate.core.Scheduler sched = AlarmScheduler.scheduler(context);
            long now = System.currentTimeMillis();
            if (!sched.needsMorningGate(now, prefs.lastClearedDay())) {
                Log.i(AlarmScheduler.TAG, "morning already cleared today, skip quiz");
                AlarmScheduler.scheduleMorning(context, sched.nextMorningAlarm(now));
                return;
            }
        }
        // Capture + pause BEFORE showing the quiz so YouTube/audio stop immediately
        ForegroundApp.captureNow(context);
        try {
            new MediaGate().pauseForQuiz(context);
        } catch (Exception e) {
            Log.e(AlarmScheduler.TAG, "pre-quiz media pause", e);
        }
        // Prefer direct activity start (AlarmManager temp allowlist); FGS as backup
        try {
            Intent quiz = new Intent(context, QuizActivity.class);
            quiz.putExtra(QuizActivity.EXTRA_TRIGGER, trigger);
            quiz.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
            context.startActivity(quiz);
            Log.i(AlarmScheduler.TAG, "started QuizActivity from receiver");
        } catch (Exception e) {
            Log.e(AlarmScheduler.TAG, "direct start failed, trying FGS", e);
            Intent svc = new Intent(context, QuizTriggerService.class);
            svc.putExtra(QuizActivity.EXTRA_TRIGGER, trigger);
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(svc);
            } else {
                context.startService(svc);
            }
        }
    }
}
