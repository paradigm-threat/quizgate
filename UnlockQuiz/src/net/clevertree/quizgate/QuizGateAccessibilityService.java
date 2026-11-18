package net.clevertree.quizgate;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * Hard-gate confinement only: while a hard lock session is active, if another
 * package becomes topmost, bring {@link QuizActivity} back. Not a screen reader
 * and not full-device monitoring — quiz confine during an active lock only.
 */
public class QuizGateAccessibilityService extends AccessibilityService {
    private static final String TAG = AlarmScheduler.TAG;

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
