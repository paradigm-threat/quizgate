package net.clevertree.quizgate;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import net.clevertree.quizgate.core.DiagnosticsStore;

import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Hard-gate session helpers: start/end lock session and re-bring QuizActivity
 * when Accessibility detects the child left the quiz.
 */
public final class HardGate {
    /** Shared debounce so a11y window storms do not spawn launch storms. */
    public static final long REINTERCEPT_DEBOUNCE_MS = 750L;

    private static final AtomicLong lastReinterceptAt = new AtomicLong(0L);

    private HardGate() {}

    public static void startSession(Context ctx) {
        if (ctx == null) return;
        new AppPrefs(ctx).setHardSessionActive(true);
        Log.i(AlarmScheduler.TAG, "hard_session start");
    }

    /**
     * Clear the hard-session flag without emitting {@code hard_unlock}.
     * Used for play/practice mode and Soft switch so A/B unlock metrics stay clean.
     */
    public static void clearSessionQuiet(Context ctx) {
        if (ctx == null) return;
        AppPrefs prefs = new AppPrefs(ctx);
        if (!prefs.isHardSessionActive()) return;
        prefs.setHardSessionActive(false);
        Log.i(AlarmScheduler.TAG, "hard_session clear quiet");
    }

    /**
     * Clear hard session and log {@code hard_unlock} when a session was active.
     * @param reason token for diag note (e.g. unlocked, consoling, parent_override)
     */
    public static void endSession(Context ctx, String reason) {
        if (ctx == null) return;
        AppPrefs prefs = new AppPrefs(ctx);
        long now = System.currentTimeMillis();
        // Always clear pending + start grace so wake cannot stack another jail.
        prefs.setPendingScreenLock(false);
        prefs.beginUnlockGrace(now);
        QuizLaunch.cancelNotification(ctx);
        if (!prefs.isHardSessionActive()) return;
        prefs.setHardSessionActive(false);
        String note = reason == null || reason.isEmpty() ? "ended" : reason;
        Diag.schedule(ctx, new DiagnosticsStore.ScheduleEvent(
                now, TimeZone.getDefault(),
                DiagnosticsStore.KIND_HARD_UNLOCK, "hard", "lock",
                prefs.nextAlarmAt(), prefs.getIntervalMs(), prefs.getJitterMs(),
                null, note));
        Log.i(AlarmScheduler.TAG, "hard_session end reason=" + note);
    }

    /** Bring quiz to front during an active hard session (Accessibility reintercept). */
    public static void bringQuizToFront(Context ctx, String trigger) {
        if (ctx == null) return;
        Context app = ctx.getApplicationContext();
        AppPrefs prefs = new AppPrefs(app);
        long now = System.currentTimeMillis();
        // Never reintercept during post-unlock grace (prevents jail→jail).
        if (prefs.inUnlockGrace(now)) return;
        // Race: session may have cleared (unlock / parent / play) between a11y event and here.
        if (!isHardLockActive(app)) return;

        long prev = lastReinterceptAt.get();
        if (now - prev < REINTERCEPT_DEBOUNCE_MS) return;
        if (!lastReinterceptAt.compareAndSet(prev, now)) {
            // Contending caller already advanced the debounce clock.
            if (now - lastReinterceptAt.get() < REINTERCEPT_DEBOUNCE_MS) return;
            lastReinterceptAt.set(now);
        }

        // Re-check after debounce window — unlock may have won the race.
        if (prefs.inUnlockGrace(System.currentTimeMillis())) return;
        if (!isHardLockActive(app)) return;

        String trig = trigger == null || trigger.isEmpty() ? "hard_reintercept" : trigger;
        try {
            Intent quiz = QuizLaunch.quizIntent(app, "interval");
            quiz.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            // Final race check immediately before launch.
            if (prefs.inUnlockGrace(System.currentTimeMillis())) return;
            if (!isHardLockActive(app)) return;
            app.startActivity(quiz);
            Diag.schedule(app, new DiagnosticsStore.ScheduleEvent(
                    System.currentTimeMillis(), TimeZone.getDefault(),
                    DiagnosticsStore.KIND_HARD_REINTERCEPT, trig, "lock",
                    prefs.nextAlarmAt(), prefs.getIntervalMs(), prefs.getJitterMs(),
                    null, "path=a11y"));
            Log.i(AlarmScheduler.TAG, "hard_reintercept trigger=" + trig);
        } catch (Exception e) {
            Log.e(AlarmScheduler.TAG, "hard_reintercept failed", e);
            Diag.nonFatal(app, "hard_reintercept", e);
        }
    }

    public static boolean isOurPackage(Context ctx, String packageName) {
        if (ctx == null || packageName == null) return false;
        return ctx.getPackageName().equals(packageName);
    }

    public static boolean isHardLockActive(Context ctx) {
        return GateMode.isHard(ctx) && new AppPrefs(ctx).isHardSessionActive();
    }

    /** Test / reset hook for debounce clock (unit tests only). */
    static void resetReinterceptClockForTests() {
        lastReinterceptAt.set(0L);
    }
}
