package net.clevertree.quizgate;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.lang.reflect.Method;
import java.util.List;

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
 *   <li>Degraded UX — when a pinned (PiP) task is still detected (or media was active
 *       at fire), pad quiz content away from the usual bottom-end PiP corner so taps
 *       stay usable. System PiP cannot be force-closed by third-party apps.</li>
 * </ol>
 *
 * <p>Does not add a new Appear-on-top requirement; {@code SYSTEM_ALERT_WINDOW} remains
 * launch-only (BAL). {@code HIDE_OVERLAY_WINDOWS} is a normal install-time permission.
 */
public final class OverlayGate {
    private static final String TAG = AlarmScheduler.TAG;
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    /** Typical system PiP footprint on tablets — keep controls clear of bottom-end. */
    private static final int PIP_PAD_BOTTOM_DP = 168;
    private static final int PIP_PAD_END_DP = 152;
    private static final long[] PIP_RECHECK_MS = { 400L, 900L, 1600L, 2800L };

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

    /**
     * Detect foreign PiP / media risk and pad quiz content out of the usual PiP corner.
     * Call after {@code setContentView}. Safe to call multiple times; schedules light
     * rechecks while the activity is alive.
     */
    public static void applyPipAvoidance(Activity activity, View content) {
        if (activity == null || content == null) return;
        Context app = activity.getApplicationContext();
        AppPrefs prefs = new AppPrefs(app);
        Boolean pinned = detectForeignPinnedTask();
        boolean mediaRisk = prefs.wasMediaPlaying();
        boolean pad = mediaRisk || Boolean.TRUE.equals(pinned);
        applyPipPadding(content, pad);
        String detect = pinned == null ? "unknown" : (pinned ? "pinned" : "clear");
        String msg = pad
                ? ("pip_pad_on:detect=" + detect + (mediaRisk ? ":media_risk" : ""))
                : ("pip_pad_off:detect=" + detect);
        Log.i(TAG, msg);
        try {
            Diag.breadcrumb(app, net.clevertree.quizgate.core.DiagBreadcrumb.builder(
                            System.currentTimeMillis(),
                            net.clevertree.quizgate.core.DiagnosticsStore.KIND_MEDIA)
                    .otherApp(mediaRisk || Boolean.TRUE.equals(pinned))
                    .message(msg)
                    .build());
        } catch (Exception ignored) {}
        // Recheck after pause bursts — pinned task may dismiss once playback stops.
        final int[] idx = { 0 };
        final Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            Boolean again = detectForeignPinnedTask();
            boolean stillRisk = prefs.wasMediaPlaying() && Boolean.TRUE.equals(again);
            boolean needPad = Boolean.TRUE.equals(again) || stillRisk;
            // Once clear and media latch was the only reason, drop pad for full UI.
            if (!needPad && Boolean.FALSE.equals(again)) {
                applyPipPadding(content, false);
                try {
                    Diag.breadcrumb(app, net.clevertree.quizgate.core.DiagBreadcrumb.builder(
                                    System.currentTimeMillis(),
                                    net.clevertree.quizgate.core.DiagnosticsStore.KIND_MEDIA)
                            .otherApp(false)
                            .message("pip_pad_cleared")
                            .build());
                } catch (Exception ignored) {}
                return;
            }
            if (needPad) applyPipPadding(content, true);
            if (idx[0] < PIP_RECHECK_MS.length) {
                HANDLER.postDelayed(tick[0], PIP_RECHECK_MS[idx[0]++]);
            }
        };
        HANDLER.postDelayed(tick[0], PIP_RECHECK_MS[0]);
        idx[0] = 1;
    }

    /**
     * Best-effort: is any task in windowing mode pinned (system PiP)?
     * {@code null} = API / permission unavailable (do not treat as clear).
     */
    static Boolean detectForeignPinnedTask() {
        if (Build.VERSION.SDK_INT < 29) return null;
        try {
            Class<?> atmClass = Class.forName("android.app.ActivityTaskManager");
            Object atm = atmClass.getMethod("getInstance").invoke(null);
            if (atm == null) return null;
            Method getTasks = null;
            for (String name : new String[] { "getAllRootTaskInfos", "getAllStackInfos" }) {
                try {
                    getTasks = atm.getClass().getMethod(name);
                    break;
                } catch (NoSuchMethodException ignored) {}
            }
            if (getTasks == null) return null;
            Object infos = getTasks.invoke(atm);
            if (!(infos instanceof List)) return null;
            for (Object info : (List<?>) infos) {
                if (info == null) continue;
                Integer mode = readWindowingMode(info);
                // WINDOWING_MODE_PINNED = 2
                if (mode != null && mode == 2) return true;
            }
            return false;
        } catch (SecurityException se) {
            return null;
        } catch (Throwable t) {
            Log.w(TAG, "pip detect unavailable", t);
            return null;
        }
    }

    private static Integer readWindowingMode(Object taskInfo) {
        try {
            return (Integer) taskInfo.getClass().getField("windowingMode").get(taskInfo);
        } catch (Throwable ignored) {}
        try {
            Method m = taskInfo.getClass().getMethod("getWindowingMode");
            Object v = m.invoke(taskInfo);
            if (v instanceof Integer) return (Integer) v;
        } catch (Throwable ignored) {}
        try {
            Object config = taskInfo.getClass().getField("configuration").get(taskInfo);
            if (config != null) {
                Object winConfig = config.getClass().getField("windowConfiguration").get(config);
                if (winConfig != null) {
                    Method m = winConfig.getClass().getMethod("getWindowingMode");
                    Object v = m.invoke(winConfig);
                    if (v instanceof Integer) return (Integer) v;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void applyPipPadding(View content, boolean on) {
        int left = content.getPaddingLeft();
        int top = content.getPaddingTop();
        int right = content.getPaddingRight();
        int bottom = content.getPaddingBottom();
        Object tag = content.getTag(R.id.quiz_panel);
        // Store baseline paddings once (use quiz_panel id as stable key bucket via tag).
        int[] base = tag instanceof int[] ? (int[]) tag : null;
        if (base == null) {
            base = new int[] { left, top, right, bottom };
            content.setTag(R.id.quiz_panel, base);
        }
        if (!on) {
            content.setPadding(base[0], base[1], base[2], base[3]);
            return;
        }
        int extraBottom = dp(content, PIP_PAD_BOTTOM_DP);
        int extraEnd = dp(content, PIP_PAD_END_DP);
        boolean rtl = content.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int newLeft = base[0] + (rtl ? extraEnd : 0);
        int newRight = base[2] + (rtl ? 0 : extraEnd);
        int newBottom = Math.max(base[3], extraBottom);
        content.setPadding(newLeft, base[1], newRight, newBottom);
    }

    private static int dp(View v, int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, v.getResources().getDisplayMetrics()));
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
