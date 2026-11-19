package net.clevertree.quizgate;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import net.clevertree.quizgate.core.DiagnosticsStore;
import net.clevertree.quizgate.core.Scheduler;
import net.clevertree.quizgate.core.ScreenLockPolicy;
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

    /**
     * Cancel interval + morning AlarmManager entries and clear next-alarm pref.
     * Used on screen-off so sleeping devices are never woken by schedule.
     */
    public static void cancelAllAlarms(Context ctx) {
        if (ctx == null) return;
        Context app = ctx.getApplicationContext();
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        cancelOne(am, app, REQ_INTERVAL, AlarmReceiver.ACTION_INTERVAL);
        cancelOne(am, app, REQ_MORNING, AlarmReceiver.ACTION_MORNING);
        new AppPrefs(app).setNextAlarmAt(0L);
        Log.i(TAG, "alarms cancelled (interval+morning)");
    }

    private static void cancelOne(AlarmManager am, Context ctx, int req, String action) {
        if (am == null) return;
        try {
            Intent broadcast = new Intent(ctx, AlarmReceiver.class).setAction(action);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, req, broadcast,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.cancel(pi);
            pi.cancel();
        } catch (Exception e) {
            Log.w(TAG, "cancel alarm action=" + action, e);
        }
        try {
            Intent show = new Intent(ctx, SetupActivity.class);
            show.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent showPi = PendingIntent.getActivity(ctx, req + 50, show,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.cancel(showPi);
            showPi.cancel();
        } catch (Exception ignored) {}
    }

    /**
     * Arm schedule only while the device is interactive and no pending screen lock.
     * No-ops (cancels any leftover wake alarms) when screen is off or pending.
     */
    public static void armWhileInteractive(Context ctx) {
        if (ctx == null) return;
        AppPrefs prefs = new AppPrefs(ctx);
        boolean interactive = ScreenLockGate.isInteractive(ctx);
        if (!ScreenLockPolicy.shouldArmSchedule(
                prefs.isArmed(), interactive, prefs.isPendingScreenLock())) {
            cancelAllAlarms(ctx);
            Log.i(TAG, "armWhileInteractive skip armed=" + prefs.isArmed()
                    + " interactive=" + interactive
                    + " pending=" + prefs.isPendingScreenLock());
            return;
        }
        scheduleArmedWindows(ctx, prefs, "arm_interactive");
    }

    public static void armAll(Context ctx) {
        AppPrefs prefs = new AppPrefs(ctx);
        prefs.setArmed(true);
        boolean interactive = ScreenLockGate.isInteractive(ctx);
        if (!interactive) {
            prefs.setPendingScreenLock(true);
            cancelAllAlarms(ctx);
            long now = new SystemClock().nowMillis();
            try {
                Diag.schedule(ctx, new DiagnosticsStore.ScheduleEvent(
                        now, TimeZone.getDefault(), DiagnosticsStore.KIND_ARM, "arm",
                        "lock", 0L, prefs.getIntervalMs(), prefs.getJitterMs(),
                        null, "arm_screen_off_pending"));
            } catch (Throwable t) {
                Log.w(TAG, "arm diag", t);
            }
            Log.i(TAG, "armed but screen off — pending lock, no wake alarms");
            return;
        }
        prefs.setPendingScreenLock(false);
        scheduleArmedWindows(ctx, prefs, "arm");
    }

    private static void scheduleArmedWindows(Context ctx, AppPrefs prefs, String armReason) {
        long now = new SystemClock().nowMillis();
        Scheduler sched = scheduler(ctx);
        long morningAt = sched.nextMorningAlarm(now);
        scheduleMorning(ctx, morningAt);
        long next = sched.nextAfterUnlock(now);
        scheduleInterval(ctx, next);
        prefs.setNextAlarmAt(next);
        ScheduleAuth.Snapshot auth = ScheduleAuth.snapshot(ctx);
        String authNote = auth.compactNote() + (auth.exactAlarm ? "" : " mode=inexact")
                + " reason=" + armReason;
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

    /**
     * Re-arm the next interval immediately when an alarm fires while the device
     * is on. Survives abandoned / never-started quizzes (Home kill, incomplete
     * session). Power-off gaps are expected — {@link BootReceiver} re-arms on boot.
     * Does <em>not</em> mark the day cleared (morning gate still applies).
     * Skips scheduling (sets pending) if the screen is off.
     */
    public static void scheduleAfterFire(Context ctx) {
        AppPrefs prefs = new AppPrefs(ctx);
        if (!prefs.isArmed()) return;
        if (ScreenLockPolicy.shouldSkipFireWhileOff(ScreenLockGate.isInteractive(ctx))) {
            prefs.setPendingScreenLock(true);
            cancelAllAlarms(ctx);
            Log.i(TAG, "after_fire screen off — pending, no re-arm");
            return;
        }
        if (prefs.isPendingScreenLock()) {
            cancelAllAlarms(ctx);
            Log.i(TAG, "after_fire pending screen lock — no re-arm");
            return;
        }
        long now = new SystemClock().nowMillis();
        prefs.setLastFiredAt(now);
        Scheduler base = scheduler(ctx);
        long next = base.nextAfterUnlock(now);
        scheduleInterval(ctx, next);
        prefs.setNextAlarmAt(next);
        scheduleMorning(ctx, base.nextMorningAlarm(now));
        String authNote = "after_fire " + ScheduleAuth.snapshot(ctx).compactNote();
        try {
            Diag.schedule(ctx, new DiagnosticsStore.ScheduleEvent(
                    now, TimeZone.getDefault(), DiagnosticsStore.KIND_ALARM_SCHEDULED, "interval",
                    "lock", next, prefs.getIntervalMs(), prefs.getJitterMs(), null, authNote));
        } catch (Throwable t) {
            Log.w(TAG, "after_fire diag", t);
        }
        Log.i(TAG, "rescheduled after fire next=" + next + " " + authNote);
    }

    /**
     * Safety net when a lock quiz is destroyed without {@link #scheduleAfterSessionEnd}.
     * No-ops if a future interval is already armed (e.g. from {@link #scheduleAfterFire}).
     */
    public static void ensureNextInterval(Context ctx, String reason) {
        AppPrefs prefs = new AppPrefs(ctx);
        if (!prefs.isArmed()) return;
        if (!ScreenLockPolicy.shouldArmSchedule(
                prefs.isArmed(), ScreenLockGate.isInteractive(ctx), prefs.isPendingScreenLock())) {
            cancelAllAlarms(ctx);
            Log.i(TAG, "ensureNextInterval skip reason=" + reason + " (not interactive or pending)");
            return;
        }
        long now = new SystemClock().nowMillis();
        long next = prefs.nextAlarmAt();
        if (next > now + 30_000L) {
            Log.i(TAG, "ensureNextInterval skip reason=" + reason + " next=" + next);
            return;
        }
        Scheduler base = scheduler(ctx);
        long scheduled = base.nextAfterUnlock(now);
        scheduleInterval(ctx, scheduled);
        prefs.setNextAlarmAt(scheduled);
        scheduleMorning(ctx, base.nextMorningAlarm(now));
        String note = "ensure_" + (reason == null ? "abandon" : reason);
        try {
            Diag.schedule(ctx, new DiagnosticsStore.ScheduleEvent(
                    now, TimeZone.getDefault(), DiagnosticsStore.KIND_ALARM_SCHEDULED, "interval",
                    "lock", scheduled, prefs.getIntervalMs(), prefs.getJitterMs(), null, note));
        } catch (Throwable t) {
            Log.w(TAG, "ensure diag", t);
        }
        Log.i(TAG, "ensureNextInterval reason=" + reason + " next=" + scheduled);
    }

    public static void scheduleAfterSessionEnd(Context ctx) {
        AppPrefs prefs = new AppPrefs(ctx);
        // Unlock / consoling / parent override clears the wake-pending latch.
        // Do NOT re-pend during unlock grace even if interactive flickers — that
        // was stacking a second Hard quiz right after the first unlock.
        long now = new SystemClock().nowMillis();
        prefs.setPendingScreenLock(false);
        if (!prefs.inUnlockGrace(now)) {
            prefs.beginUnlockGrace(now);
        }
        Scheduler base = scheduler(ctx);
        prefs.setLastClearedDay(base.dayKey(now));

        if (!ScreenLockGate.isInteractive(ctx)) {
            // Screen already off after unlock: keep grace, stay pending for *next*
            // real wake only after grace expires (onScreenOff will pend again).
            cancelAllAlarms(ctx);
            Log.i(TAG, "session end while screen off — grace held, no immediate re-pend");
            return;
        }

        // Always keep configured interval±jitter (default 40±10). Banked minutes
        // ADD free time on top — never replace the window with a 2–5 min jar delay.
        long interval = prefs.getIntervalMs();
        long jitterUsed = prefs.getJitterMs();
        long bonusMs = prefs.takeMinutesBonusMs();
        long delayBase = interval + bonusMs;
        Scheduler sched = new Scheduler(TimeZone.getDefault(), delayBase, jitterUsed);
        long next = sched.nextAfterUnlock(now);
        scheduleInterval(ctx, next);
        prefs.setNextAlarmAt(next);
        scheduleMorning(ctx, base.nextMorningAlarm(now));
        ScheduleAuth.Snapshot auth = ScheduleAuth.snapshot(ctx);
        String authNote = "after_session " + auth.compactNote()
                + (auth.exactAlarm ? "" : " mode=inexact")
                + (bonusMs > 0 ? " jarBonusMs=" + bonusMs : "");
        try {
            Diag.schedule(ctx, new DiagnosticsStore.ScheduleEvent(
                    now, TimeZone.getDefault(), DiagnosticsStore.KIND_ALARM_SCHEDULED, "interval",
                    "lock", next, delayBase, jitterUsed, null, authNote));
        } catch (Throwable t) {
            Log.w(TAG, "session diag", t);
        }
        ScheduleAuth.log(ctx, DiagnosticsStore.KIND_PERM, "session_end", next, delayBase, jitterUsed);
        Log.i(TAG, "rescheduled after session next=" + next
                + " delayBase=" + delayBase + " jitter=" + jitterUsed
                + " jarBonusMs=" + bonusMs
                + " sampledWindow=" + Scheduler.formatWindow(delayBase, jitterUsed)
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
        // Broadcast → AlarmReceiver → QuizLaunch. Appear-on-top (SYSTEM_ALERT_WINDOW)
        // is the BAL exemption that lets QuizActivity open over other apps on Android 10+/16.
        // Activity PendingIntents from AlarmManager are still BAL-blocked without overlay.
        Intent broadcast = new Intent(ctx, AlarmReceiver.class).setAction(action);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, req, broadcast,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent show = new Intent(ctx, SetupActivity.class);
        show.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent showPi = PendingIntent.getActivity(ctx, req + 50, show,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(atMs, showPi);
            am.setAlarmClock(info, pi);
            Log.i(TAG, "alarm_clock broadcast action=" + action + " at=" + atMs);
            return;
        } catch (SecurityException se) {
            Log.e(TAG, "setAlarmClock SecurityException — falling back", se);
        } catch (Exception e) {
            Log.e(TAG, "setAlarmClock failed — falling back", e);
        }
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                boolean canExact = am.canScheduleExactAlarms();
                if (canExact) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi);
                    Log.i(TAG, "exact alarm broadcast action=" + action + " at=" + atMs);
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi);
                    Log.w(TAG, "INEXACT fallback action=" + action + " at=" + atMs
                            + " — open exact-alarm settings");
                }
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi);
                Log.i(TAG, "exact alarm broadcast action=" + action + " at=" + atMs);
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
