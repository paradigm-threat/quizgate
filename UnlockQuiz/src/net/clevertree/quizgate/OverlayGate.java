package net.clevertree.quizgate;

import android.app.Activity;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/**
 * Best-effort: keep the quiz gate visually clear of floating video / overlays.
 *
 * <ol>
 *   <li>Preferred — pause media <em>before</em> quiz foreground via
 *       {@link MediaGate#pauseThenSettle} so YouTube is less likely to auto-enter
 *       system PiP on the task switch.</li>
 *   <li>{@link Window#setHideOverlayWindows(boolean)} (API 31+) hides other apps'
 *       {@code TYPE_APPLICATION_OVERLAY} windows while we are focused (not system PiP).</li>
 *   <li>Immersive sticky + high-priority fullscreen layout so the quiz owns the display.</li>
 * </ol>
 *
 * <p>Quiz UI is centered; this class does not reserve corners or chase PiP
 * position. System PiP cannot be force-closed — kids can pan the quiz column.
 *
 * <p>Does not add a new Appear-on-top requirement; {@code SYSTEM_ALERT_WINDOW} remains
 * launch-only (BAL). {@code HIDE_OVERLAY_WINDOWS} is a normal install-time permission.
 */
public final class OverlayGate {
    private static final String TAG = AlarmScheduler.TAG;

    private OverlayGate() {}

    /** Call from {@code onCreate} before or right after {@code setContentView}. */
    public static void apply(Activity activity) {
        if (activity == null) return;
        Window w = activity.getWindow();
        if (w == null) return;
        try {
            w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        } catch (Exception e) {
            Log.w(TAG, "overlay gate flags", e);
        }
        hideForeignOverlays(w);
        applyImmersive(w);
        try {
            Diag.breadcrumb(activity.getApplicationContext(),
                    net.clevertree.quizgate.core.DiagBreadcrumb.builder(
                                    System.currentTimeMillis(),
                                    net.clevertree.quizgate.core.DiagnosticsStore.KIND_MEDIA)
                            .message(hideOverlaySupported() ? "overlay_hide_on" : "overlay_hide_unsupported")
                            .build());
        } catch (Exception ignored) {}
    }

    /** Re-assert immersive after focus changes (OEM bars / PiP expand can clear it). */
    public static void onFocus(Activity activity) {
        if (activity == null) return;
        Window w = activity.getWindow();
        if (w == null) return;
        hideForeignOverlays(w);
        applyImmersive(w);
    }

    private static boolean hideOverlaySupported() {
        return Build.VERSION.SDK_INT >= 31;
    }

    private static void hideForeignOverlays(Window w) {
        if (!hideOverlaySupported()) return;
        try {
            w.setHideOverlayWindows(true);
        } catch (SecurityException se) {
            // Permission missing on odd OEM builds — pause path still works.
            Log.w(TAG, "setHideOverlayWindows denied", se);
        } catch (Exception e) {
            Log.w(TAG, "setHideOverlayWindows", e);
        }
    }

    @SuppressWarnings("deprecation")
    private static void applyImmersive(Window w) {
        try {
            View decor = w.getDecorView();
            if (decor == null) return;
            if (Build.VERSION.SDK_INT >= 30) {
                android.view.WindowInsetsController c = w.getInsetsController();
                if (c != null) {
                    c.hide(android.view.WindowInsets.Type.statusBars()
                            | android.view.WindowInsets.Type.navigationBars());
                    c.setSystemBarsBehavior(
                            android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                decor.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN);
            }
        } catch (Exception e) {
            Log.w(TAG, "immersive", e);
        }
    }
}
