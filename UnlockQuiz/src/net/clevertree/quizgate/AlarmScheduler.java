package net.clevertree.quizgate;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import net.clevertree.quizgate.core.Scheduler;
import net.clevertree.quizgate.core.SystemClock;

import java.util.TimeZone;

public final class AlarmScheduler {
    public static final String TAG = "QuizGate";
    public static final int REQ_INTERVAL = 1001;
    public static final int REQ_MORNING = 1002;

    private AlarmScheduler() {}

    public static Scheduler scheduler(Context ctx) {
        AppPrefs prefs = new AppPrefs(ctx);
        return new Scheduler(TimeZone.getDefault(), prefs.getIntervalMs());
    }

    public static void armAll(Context ctx) {
        AppPrefs prefs = new AppPrefs(ctx);
        prefs.setArmed(true);
        long now = new SystemClock().nowMillis();
        Scheduler sched = scheduler(ctx);
        scheduleMorning(ctx, sched.nextMorningAlarm(now));
        long next = sched.nextAfterUnlock(now);
        scheduleInterval(ctx, next);
        prefs.setNextAlarmAt(next);
        Log.i(TAG, "armed morning+interval nextInterval=" + next);
    }

    public static void scheduleAfterSessionEnd(Context ctx) {
        AppPrefs prefs = new AppPrefs(ctx);
        long now = new SystemClock().nowMillis();
        Scheduler sched = scheduler(ctx);
        prefs.setLastClearedDay(sched.dayKey(now));
        long next = sched.nextAfterUnlock(now);
        scheduleInterval(ctx, next);
        prefs.setNextAlarmAt(next);
        // Keep morning alarm refreshed
        scheduleMorning(ctx, sched.nextMorningAlarm(now));
        Log.i(TAG, "rescheduled after session next=" + next);
    }

    public static void scheduleInterval(Context ctx, long atMs) {
        setExact(ctx, atMs, REQ_INTERVAL, AlarmReceiver.ACTION_INTERVAL);
    }

    public static void scheduleMorning(Context ctx, long atMs) {
        setExact(ctx, atMs, REQ_MORNING, AlarmReceiver.ACTION_MORNING);
    }

    private static void setExact(Context ctx, long atMs, int req, String action) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ctx, AlarmReceiver.class).setAction(action);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, req, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                boolean canExact = am.canScheduleExactAlarms();
                if (canExact) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi);
                    Log.i(TAG, "exact alarm action=" + action + " at=" + atMs);
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi);
                    Log.w(TAG, "INEXACT fallback action=" + action + " at=" + atMs);
                }
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi);
                Log.i(TAG, "exact alarm action=" + action + " at=" + atMs);
            }
        } catch (Exception e) {
            Log.e(TAG, "setExact failed", e);
            am.set(AlarmManager.RTC_WAKEUP, atMs, pi);
        }
    }
}
