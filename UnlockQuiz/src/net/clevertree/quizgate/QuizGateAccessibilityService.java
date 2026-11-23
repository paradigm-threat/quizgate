package net.clevertree.quizgate;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * Hard-gate confinement + screen off/on delivery:
 * <ul>
 *   <li>While a hard lock session is active, if another package becomes topmost,
 *       bring {@link QuizActivity} back.</li>
 *   <li>Dynamically registers SCREEN_OFF / SCREEN_ON / USER_PRESENT so Armed
 *       devices cancel schedule on sleep and open Hard quiz on wake.</li>
 * </ul>
 * Not a screen reader and not full-device monitoring.
 */
public class QuizGateAccessibilityService extends AccessibilityService {
    private static final String TAG = AlarmScheduler.TAG;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        ScreenLockGate.register(this);
        Log.i(TAG, "a11y connected — screen_lock receiver on");
        // Re-arm if the user had armed and we are interactive with no pending lock.
        A11yArm.reconcile(this, "a11y_connected");
        // Pending lock (grant after sleep) still opens the quiz. Do not also force
        // a second schedule — reconcile already armed when there is nothing pending.
        if (ScreenLockGate.isInteractive(this) && new AppPrefs(this).isPendingScreenLock()) {
            ScreenLockGate.onWake(this, "a11y_connected");
        }
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        // Keep SCREEN_OFF/ON receiver — power-cycle lock must not depend on a11y.
        // If the secure setting is already off, cancel alarms and surface the pause.
        Log.i(TAG, "a11y unbound — screen_lock receiver kept");
        A11yArm.reconcile(this, "a11y_unbind");
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            return;
        }
        // Session may have ended (unlock / parent / Soft switch) — never confine then.
        if (!HardGate.isHardLockActive(this)) return;
        // Screen-off window churn must not relaunch the quiz (that turns the display on).
        if (!ScreenLockGate.isInteractive(this)) return;

        // Prefer event package (works with canRetrieveWindowContent=false).
        String pkg = packageFromEvent(event);
        if (pkg == null || pkg.isEmpty()) return;
        if (HardGate.isOurPackage(this, pkg)) return;
        // Ignore system UI chrome; reintercept when another app becomes top.
        if ("com.android.systemui".equals(pkg)) return;

        Log.i(TAG, "a11y leave pkg=" + pkg + " — reintercept");
        // Debounce + race checks live in HardGate.bringQuizToFront.
        HardGate.bringQuizToFront(this, "a11y_" + type);
    }

    @Override
    public void onInterrupt() {
        // No-op: confine-only service.
    }

    private String packageFromEvent(AccessibilityEvent event) {
        CharSequence pkg = event.getPackageName();
        return pkg == null ? null : pkg.toString();
    }
}
