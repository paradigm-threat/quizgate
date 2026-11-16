package net.clevertree.quizgate;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import net.clevertree.quizgate.core.DiagnosticsStore;
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
        return new Scheduler(TimeZone.getDefault(), prefs.getIntervalMs(), prefs.getJitterMs());
    }

    public static void armAll(Context ctx) {
        AppPrefs prefs = new AppPrefs(ctx);
        prefs.setArmed(true);
        long now = new SystemClock().nowMillis();
        Scheduler sched = scheduler(ctx);
        long morningAt = sched.nextMorningAlarm(now);
        scheduleMorning(ctx, morningAt);
        long next = sched.nextAfterUnlock(now);
        scheduleInterval(ctx, next);
        prefs.setNextAlarmAt(next);
        ScheduleAuth.Snapshot auth = ScheduleAuth.snapshot(ctx);
        String authNote = auth.compactNote() + (auth.exactAlarm ? "" : " mode=inexact");
        try {
            Diag.schedule(ctx, new DiagnosticsStore.ScheduleEvent(
                    now, TimeZone.getDefault(), DiagnosticsStore.KIND_ARM, "arm",
                    "lock", next, prefs.getIntervalMs(), prefs.getJitterMs(),
                    null, authNote));
            Diag.schedule(ctx, new DiagnosticsStore.ScheduleEvent(
                    now, TimeZone.getDefault(), DiagnosticsStore.KIND_ALARM_SCHEDULED, "interval",
                    "lock", next, prefs.getIntervalMs(), prefs.getJitterMs(),
                    null, authNote));
        } catch (Throwable t) {
            Log.w(TAG, "arm diag", t);
        }
        ScheduleAuth.log(ctx, DiagnosticsStore.KIND_PERM, "arm", next,
                prefs.getIntervalMs(), prefs.getJitterMs());
        Log.i(TAG, "armed morning+interval nextInterval=" + next
                + " window=" + Scheduler.formatWindow(prefs.getIntervalMs(), prefs.getJitterMs())
                + " " + authNote);
    }

    public static void scheduleAfterSessionEnd(Context ctx) {
        AppPrefs prefs = new AppPrefs(ctx);
        long now = new SystemClock().nowMillis();
        Scheduler base = scheduler(ctx);
        prefs.setLastClearedDay(base.dayKey(now));

        long delay;
        long jitterUsed;
        if (prefs.getMinutesBalance() > 0) {
            delay = prefs.consumeMinutesForNextInterval(prefs.getIntervalMs());
            jitterUsed = 0L;
        } else {
            delay = prefs.getIntervalMs();
            jitterUsed = prefs.getJitterMs();
        }
        Scheduler sched = new Scheduler(TimeZone.getDefault(), delay, jitterUsed);
        long next = sched.nextAfterUnlock(now);
        scheduleInterval(ctx, next);
        prefs.setNextAlarmAt(next);
        scheduleMorning(ctx, base.nextMorningAlarm(now));
        ScheduleAuth.Snapshot auth = ScheduleAuth.snapshot(ctx);
        String authNote = "after_session " + auth.compactNote()
                + (auth.exactAlarm ? "" : " mode=inexact");
        try {
            Diag.schedule(ctx, new DiagnosticsStore.ScheduleEvent(
                    now, TimeZone.getDefault(), DiagnosticsStore.KIND_ALARM_SCHEDULED, "interval",
                    "lock", next, delay, jitterUsed, null, authNote));
        } catch (Throwable t) {
            Log.w(TAG, "session diag", t);
        }
        ScheduleAuth.log(ctx, DiagnosticsStore.KIND_PERM, "session_end", next, delay, jitterUsed);
        Log.i(TAG, "rescheduled after session next=" + next
                + " delayBase=" + delay + " jitter=" + jitterUsed
                + " sampledWindow=" + Scheduler.formatWindow(delay, jitterUsed)
                + " " + auth.compactNote());
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
                    Log.w(TAG, "INEXACT fallback action=" + action + " at=" + atMs
                            + " — open exact-alarm settings");
                }
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi);
                Log.i(TAG, "exact alarm action=" + action + " at=" + atMs);
            }
        } catch (SecurityException se) {
            Log.e(TAG, "setExact SecurityException — exact alarm revoked", se);
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi);
            } catch (Exception e2) {
                am.set(AlarmManager.RTC_WAKEUP, atMs, pi);
            }
        } catch (Exception e) {
            Log.e(TAG, "setExact failed", e);
            am.set(AlarmManager.RTC_WAKEUP, atMs, pi);
        }
    }
}
