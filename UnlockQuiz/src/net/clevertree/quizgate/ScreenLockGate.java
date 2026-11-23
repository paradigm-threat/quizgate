package net.clevertree.quizgate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import net.clevertree.quizgate.core.DiagnosticsStore;
import net.clevertree.quizgate.core.InteractiveMinutesStore;
import net.clevertree.quizgate.core.ScreenLockPolicy;
import net.clevertree.quizgate.core.StatsDayPolicy;
import net.clevertree.quizgate.core.StatsStore;

import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Screen-off → pending Hard lock; wake → open quiz.
 *
 * <p>Registered from {@link QuizGateApp} (always) and reinforced from
 * {@link QuizGateAccessibilityService}. Dynamic registration is required —
 * modern Android does not deliver {@code SCREEN_OFF}/{@code SCREEN_ON} to
 * manifest receivers. Do not unregister when Accessibility unbinds.
 *
 * <p>Also accumulates barrier-day {@code interactive_ms} (screen-on time) for
 * day-batch schedule health — no package names.
 */
public final class ScreenLockGate {
    private static final AtomicLong lastWakeAt = new AtomicLong(0L);
    private static final long WAKE_DEBOUNCE_MS = 800L;
    private static final AtomicBoolean receiverRegistered = new AtomicBoolean(false);
    /** Wall-clock when the current interactive stretch began; 0 = not tracking. */
    private static final AtomicLong interactiveSinceMs = new AtomicLong(0L);

    private static BroadcastReceiver screenReceiver;

    private ScreenLockGate() {}

    public static boolean isInteractive(Context ctx) {
        if (ctx == null) return true;
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            return pm == null || pm.isInteractive();
        } catch (Exception e) {
            return true;
        }
    }

    /** Register SCREEN_OFF / SCREEN_ON / USER_PRESENT (idempotent). */
    public static void register(Context ctx) {
        if (ctx == null || receiverRegistered.get()) return;
        Context app = ctx.getApplicationContext();
        if (screenReceiver == null) {
            screenReceiver = new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) {
                    if (intent == null) return;
                    String action = intent.getAction();
                    if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                        onScreenOff(context);
                    } else if (Intent.ACTION_SCREEN_ON.equals(action)
                            || Intent.ACTION_USER_PRESENT.equals(action)) {
                        onWake(context, action);
                    }
                }
            };
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                app.registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                app.registerReceiver(screenReceiver, filter);
            }
            receiverRegistered.set(true);
            Log.i(AlarmScheduler.TAG, "screen_lock receiver registered");
            // Best-effort: start interactive clock if already awake (a11y-independent).
            markInteractiveIfNeeded(app);
        } catch (Exception e) {
            Log.e(AlarmScheduler.TAG, "screen_lock register failed", e);
            Diag.nonFatal(app, "screen_lock_register", e);
        }
    }

    /**
     * Kept for tests / process teardown. Accessibility unbind must <em>not</em>
     * call this — power-cycle lock must keep working without a11y.
     */
    public static void unregister(Context ctx) {
        if (ctx == null || !receiverRegistered.get() || screenReceiver == null) return;
        try {
            flushInteractive(ctx, System.currentTimeMillis());
            ctx.getApplicationContext().unregisterReceiver(screenReceiver);
        } catch (Exception e) {
            Log.w(AlarmScheduler.TAG, "screen_lock unregister", e);
        }
        receiverRegistered.set(false);
    }

    public static void onScreenOff(Context ctx) {
        if (ctx == null) return;
        Context app = ctx.getApplicationContext();
        flushInteractive(app, System.currentTimeMillis());
        AppPrefs prefs = new AppPrefs(app);
        if (!ScreenLockPolicy.shouldPendOnScreenOff(prefs.isArmed())) return;
        long now = System.currentTimeMillis();
        AlarmScheduler.cancelAllAlarms(app);
        // Drop FSI/ongoing quiz notif — CATEGORY_ALARM heads-up can fight power-off on OEMs.
        QuizLaunch.cancelNotification(app);
        // Power-off ends the post-unlock free window — next wake must lock.
        if (ScreenLockPolicy.shouldClearGraceOnScreenOff(true)) {
            prefs.clearUnlockGrace();
        }
        prefs.setPendingScreenLock(true);
        Diag.schedule(app, new DiagnosticsStore.ScheduleEvent(
                now, TimeZone.getDefault(), DiagnosticsStore.KIND_SKIP_FIRE, "screen_off",
                "lock", 0L, prefs.getIntervalMs(), prefs.getJitterMs(),
                null, "screen_off_pending"));
        Log.i(AlarmScheduler.TAG, "screen_off → pending lock (grace cleared), alarms cancelled");
    }

    public static void onWake(Context ctx, String action) {
        if (ctx == null) return;
        Context app = ctx.getApplicationContext();
        markInteractiveIfNeeded(app);
        AppPrefs prefs = new AppPrefs(app);
        long now = System.currentTimeMillis();
        boolean inGrace = prefs.inUnlockGrace(now);
        boolean hardActive = HardGate.isHardLockActive(app);
        boolean pending = prefs.isPendingScreenLock();

        if (ScreenLockPolicy.shouldBringHardOnWake(
                prefs.isArmed(), pending, hardActive, inGrace)) {
            long prev = lastWakeAt.get();
            if (now - prev < WAKE_DEBOUNCE_MS) return;
            if (!lastWakeAt.compareAndSet(prev, now)) return;
            Log.i(AlarmScheduler.TAG, "wake → bring existing Hard quiz action=" + action);
            HardGate.bringQuizToFront(app, "wake_resume");
            return;
        }
        if (!ScreenLockPolicy.shouldLaunchOnWake(
                prefs.isArmed(), pending, hardActive, inGrace)) {
            if (ScreenLockPolicy.shouldRearmScheduleOnWakeSkip(inGrace, pending)) {
                AlarmScheduler.armWhileInteractive(app);
            }
            return;
        }
        long prev = lastWakeAt.get();
        if (now - prev < WAKE_DEBOUNCE_MS) return;
        if (!lastWakeAt.compareAndSet(prev, now)) return;

        String note = "wake_lock action=" + (action == null ? "" : action);
        Diag.schedule(app, new DiagnosticsStore.ScheduleEvent(
                now, TimeZone.getDefault(), DiagnosticsStore.KIND_LAUNCH_ATTEMPT, "wake",
                "lock", prefs.nextAlarmAt(), prefs.getIntervalMs(), prefs.getJitterMs(),
                null, note));
        Log.i(AlarmScheduler.TAG, "wake → Hard quiz " + note);
        try {
            QuizLaunch.fromAlarm(app, "wake");
        } catch (Exception e) {
            Log.e(AlarmScheduler.TAG, "wake quiz launch failed", e);
            Diag.nonFatal(app, "wake_lock", e);
        }
    }

    /** Clear pending after unlock / consoling / parent override, then re-arm if interactive. */
    public static void clearPendingAndRearm(Context ctx) {
        if (ctx == null) return;
        Context app = ctx.getApplicationContext();
        markInteractiveIfNeeded(app);
        AppPrefs prefs = new AppPrefs(app);
        prefs.setPendingScreenLock(false);
        prefs.beginUnlockGrace(System.currentTimeMillis());
        QuizLaunch.cancelNotification(app);
        AlarmScheduler.armWhileInteractive(app);
    }

    /**
     * Flush open interactive stretch into {@code interactive.jsonl} and restart
     * the clock if still interactive. Safe to call from day-batch sync.
     */
    public static void reconcileInteractive(Context ctx) {
        if (ctx == null) return;
        Context app = ctx.getApplicationContext();
        long now = System.currentTimeMillis();
        flushInteractive(app, now);
        if (isInteractive(app)) {
            interactiveSinceMs.compareAndSet(0L, now);
        }
    }

    static void markInteractiveIfNeeded(Context ctx) {
        if (ctx == null) return;
        if (!isInteractive(ctx)) return;
        interactiveSinceMs.compareAndSet(0L, System.currentTimeMillis());
    }

    static void flushInteractive(Context ctx, long nowMs) {
        long start = interactiveSinceMs.getAndSet(0L);
        if (start <= 0L || nowMs <= start || ctx == null) return;
        try {
            AppPrefs prefs = new AppPrefs(ctx);
            int barrier = prefs.getStatsDayBarrierHour();
            TimeZone zone = TimeZone.getDefault();
            InteractiveMinutesStore store = new InteractiveMinutesStore(Diag.statsDir(ctx));
            long cursor = start;
            // Cap one flush at 12h to absorb missed SCREEN_OFF.
            long end = Math.min(nowMs, start + 12L * 60L * 60L * 1000L);
            int guard = 0;
            while (cursor < end && guard++ < 8) {
                String day = StatsStore.dayKey(cursor, zone, barrier);
                long next = StatsDayPolicy.nextBarrierMs(cursor, zone, barrier);
                long chunkEnd = Math.min(end, next);
                long delta = chunkEnd - cursor;
                if (delta > 0L) store.addMs(day, delta);
                cursor = chunkEnd;
            }
        } catch (Exception e) {
            Log.w(AlarmScheduler.TAG, "interactive_ms flush failed", e);
        }
    }

    /** Test hook. */
    static void resetWakeClockForTests() {
        lastWakeAt.set(0L);
        interactiveSinceMs.set(0L);
    }
}
