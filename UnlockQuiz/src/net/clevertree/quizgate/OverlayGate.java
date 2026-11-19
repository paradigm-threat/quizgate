package net.clevertree.quizgate;

import android.app.Activity;
import android.content.res.Configuration;
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
 * <p><b>Sleep is sacred:</b> never set {@code FLAG_KEEP_SCREEN_ON} or
 * {@code FLAG_TURN_SCREEN_ON}. Power-off must leave the tablet off; Hard quiz
 * opens only after the user turns the screen back on ({@link ScreenLockGate}).
 *
 * <p>System navigation / status bar colors follow the DayNight theme so Home/Back
 * stay visible when transient bars peek over light or dark quiz chrome.
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
            // Show over keyguard after a user wake — but never force or keep the display on.
            w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
            w.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            if (Build.VERSION.SDK_INT >= 27) {
                activity.setShowWhenLocked(true);
                activity.setTurnScreenOn(false);
            }
        } catch (Exception e) {
            Log.w(TAG, "overlay gate flags", e);
        }
        applySystemBarContrast(activity, w);
        hideForeignOverlays(w);
        applyImmersive(activity, w);
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
        // Defend against OEM/window merges re-adding wake flags.
        try {
            w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
            if (Build.VERSION.SDK_INT >= 27) {
                activity.setTurnScreenOn(false);
            }
        } catch (Exception ignored) {}
        applySystemBarContrast(activity, w);
        hideForeignOverlays(w);
        applyImmersive(activity, w);
    }

    /** Status / nav bar color + light/dark icons from the active DayNight theme. */
    private static void applySystemBarContrast(Activity activity, Window w) {
        try {
            int bar = activity.getResources().getColor(R.color.system_bar, activity.getTheme());
            w.setStatusBarColor(bar);
            w.setNavigationBarColor(bar);
            boolean night = isNightUi(activity);
            if (Build.VERSION.SDK_INT >= 30) {
                android.view.WindowInsetsController c = w.getInsetsController();
                if (c != null) {
                    int light = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                    c.setSystemBarsAppearance(night ? 0 : light, light);
                }
            } else if (Build.VERSION.SDK_INT >= 26) {
                View decor = w.getDecorView();
                int flags = decor.getSystemUiVisibility();
                if (night) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                    if (Build.VERSION.SDK_INT >= 27) {
                        flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                    }
                } else {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                    if (Build.VERSION.SDK_INT >= 27) {
                        flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                    }
                }
                decor.setSystemUiVisibility(flags);
            }
        } catch (Exception e) {
            Log.w(TAG, "system bar contrast", e);
        }
    }

    static boolean isNightUi(Activity activity) {
        if (activity == null) return false;
        int night = activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    private static boolean hideOverlaySupported() {
        return Build.VERSION.SDK_INT >= 31;
    }

    private static void hideForeignOverlays(Window w) {
        if (!hideOverlaySupported()) return;
        try {
            w.setHideOverlayWindows(true);
        } catch (SecurityException se) {
            Log.w(TAG, "setHideOverlayWindows denied", se);
        } catch (Exception e) {
            Log.w(TAG, "setHideOverlayWindows", e);
        }
    }

    @SuppressWarnings("deprecation")
    private static void applyImmersive(Activity activity, Window w) {
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
                    // Re-apply icon contrast after hide (OEMs reset appearance).
                    boolean night = isNightUi(activity);
                    int light = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                    c.setSystemBarsAppearance(night ? 0 : light, light);
                }
            } else {
                int flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN;
                if (!isNightUi(activity)) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                    if (Build.VERSION.SDK_INT >= 27) {
                        flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                    }
                }
                decor.setSystemUiVisibility(flags);
            }
        } catch (Exception e) {
            Log.w(TAG, "immersive", e);
        }
    }
}
