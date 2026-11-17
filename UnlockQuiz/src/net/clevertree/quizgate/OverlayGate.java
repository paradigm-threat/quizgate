package net.clevertree.quizgate;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
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
 *   <li>Always reserve a modest bottom-end band (stats bar / margins) for the usual
 *       system-overlay / PiP corner. Layout is <em>not</em> gated on detection —
 *       pinned-task probes are diagnostics-only. System PiP cannot be force-closed.</li>
 * </ol>
 *
 * <p>Does not add a new Appear-on-top requirement; {@code SYSTEM_ALERT_WINDOW} remains
 * launch-only (BAL). {@code HIDE_OVERLAY_WINDOWS} is a normal install-time permission.
 */
public final class OverlayGate {
    private static final String TAG = AlarmScheduler.TAG;
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    /**
     * Modest permanent reserve for the usual bottom-end overlay/PiP corner.
     * Floors + small fractions — enough for stats/margins, not enough to crush the pad.
     */
    private static final int CORNER_PAD_BOTTOM_DP = 112;
    private static final int CORNER_PAD_END_DP = 96;
    /** Diagnostics-only rechecks (layout already reserved permanently). */
    private static final long[] PIP_DETECT_RECHECK_MS = { 400L, 900L, 1600L, 2800L };

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
     * Always reserve the usual bottom-end overlay/PiP corner for quiz chrome
     * (stats bar margins + modest panel end inset). Detection is diagnostics-only
     * and never toggles layout. Call after {@code setContentView}.
     */
    public static void applyPipAvoidance(Activity activity, View... contents) {
        if (activity == null || contents == null || contents.length == 0) return;
        Context app = activity.getApplicationContext();
        for (View content : contents) {
            if (content != null) applyCornerReserve(content);
        }
        Boolean pinned = detectForeignPinnedTask();
        boolean mediaRisk = new AppPrefs(app).wasMediaPlaying();
        String detect = pinned == null ? "unknown" : (pinned ? "pinned" : "clear");
        String msg = "corner_reserve_on:detect=" + detect
                + (mediaRisk ? ":media_risk" : "")
                + ":layout=always";
        Log.i(TAG, msg);
        try {
            Diag.breadcrumb(app, net.clevertree.quizgate.core.DiagBreadcrumb.builder(
                            System.currentTimeMillis(),
                            net.clevertree.quizgate.core.DiagnosticsStore.KIND_MEDIA)
                    .otherApp(mediaRisk || Boolean.TRUE.equals(pinned))
                    .message(msg)
                    .build());
        } catch (Exception ignored) {}
        // Recheck detection for diagnostics only — layout stays reserved.
        final int[] idx = { 0 };
        final Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            Boolean again = detectForeignPinnedTask();
            String againDetect = again == null ? "unknown" : (again ? "pinned" : "clear");
            try {
                Diag.breadcrumb(app, net.clevertree.quizgate.core.DiagBreadcrumb.builder(
                                System.currentTimeMillis(),
                                net.clevertree.quizgate.core.DiagnosticsStore.KIND_MEDIA)
                        .otherApp(Boolean.TRUE.equals(again)
                                || new AppPrefs(app).wasMediaPlaying())
                        .message("pip_detect_recheck:detect=" + againDetect)
                        .build());
            } catch (Exception ignored) {}
            if (idx[0] < PIP_DETECT_RECHECK_MS.length) {
                HANDLER.postDelayed(tick[0], PIP_DETECT_RECHECK_MS[idx[0]++]);
            }
        };
        HANDLER.postDelayed(tick[0], PIP_DETECT_RECHECK_MS[0]);
        idx[0] = 1;
    }

    private static boolean pipDetectLogged;

    /**
     * Best-effort: is any task in windowing mode pinned (system PiP)?
     * {@code null} = API / permission unavailable. Third-party apps usually cannot
     * enumerate foreign pinned tasks on modern Android — treat as diagnostics only.
     */
    static Boolean detectForeignPinnedTask() {
        if (Build.VERSION.SDK_INT < 29) return null;
        try {
            Class<?> atmClass = Class.forName("android.app.ActivityTaskManager");
            Object atm = null;
            for (String name : new String[] { "getInstance", "getService" }) {
                try {
                    Method m = atmClass.getDeclaredMethod(name);
                    m.setAccessible(true);
                    atm = m.invoke(null);
                    if (atm != null) break;
                } catch (NoSuchMethodException ignored) {}
            }
            if (atm == null) {
                // ActivityManager.getService() → IActivityManager / IActivityTaskManager
                Method amSvc = Class.forName("android.app.ActivityManager")
                        .getDeclaredMethod("getService");
                amSvc.setAccessible(true);
                atm = amSvc.invoke(null);
            }
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
            if (!pipDetectLogged) {
                pipDetectLogged = true;
                Log.i(TAG, "pip detect unavailable (diag only; corner always reserved)");
            }
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

    /**
     * Permanent bottom-end reserve. Stats bar uses margins only (filled card);
     * quiz panel gets a modest end inset and leaves bottom height to the layout's
     * existing stats clearance so the digit pad is not crushed.
     */
    private static void applyCornerReserve(View content) {
        int left = content.getPaddingLeft();
        int top = content.getPaddingTop();
        int right = content.getPaddingRight();
        int bottom = content.getPaddingBottom();
        Object tag = content.getTag(R.id.quiz_panel);
        // Store baseline paddings + margins once: [pl,pt,pr,pb, ml,mt,mr,mb]
        int[] base = tag instanceof int[] ? (int[]) tag : null;
        ViewGroup.MarginLayoutParams mlp = null;
        if (content.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            mlp = (ViewGroup.MarginLayoutParams) content.getLayoutParams();
        }
        if (base == null) {
            base = new int[] {
                    left, top, right, bottom,
                    mlp != null ? mlp.leftMargin : 0,
                    mlp != null ? mlp.topMargin : 0,
                    mlp != null ? mlp.rightMargin : 0,
                    mlp != null ? mlp.bottomMargin : 0
            };
            content.setTag(R.id.quiz_panel, base);
        }
        boolean marginOnly = content.getId() == R.id.quiz_stats_bar;
        int[] pads = cornerPadsPx(content);
        int extraEnd = pads[0];
        int extraBottom = pads[1];
        boolean rtl = content.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        if (marginOnly) {
            if (mlp != null) {
                int mLeft = base[4] + (rtl ? extraEnd : 0);
                int mRight = base[6] + (rtl ? 0 : extraEnd);
                int mBottom = Math.max(base[7], extraBottom);
                mlp.setMargins(mLeft, base[5], mRight, mBottom);
                content.setLayoutParams(mlp);
            }
            return;
        }
        // Panel: end inset only — bottom clearance stays at layout paddingBottom (stats).
        int newLeft = base[0] + (rtl ? extraEnd : 0);
        int newRight = base[2] + (rtl ? 0 : extraEnd);
        content.setPadding(newLeft, base[1], newRight, base[3]);
    }

    /** @return [endPx, bottomPx] sized for a modest tablet overlay corner. */
    private static int[] cornerPadsPx(View v) {
        android.util.DisplayMetrics dm = v.getResources().getDisplayMetrics();
        // ~10% end / 12% bottom — clears typical mini-player corner without crushing keys.
        int end = Math.max(dp(v, CORNER_PAD_END_DP), Math.round(dm.widthPixels * 0.10f));
        int bottom = Math.max(dp(v, CORNER_PAD_BOTTOM_DP), Math.round(dm.heightPixels * 0.12f));
        return new int[] { end, bottom };
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
