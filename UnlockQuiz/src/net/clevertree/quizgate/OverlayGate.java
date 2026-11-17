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
 *   <li>Always lift critical quiz controls (digit pad / answers / T/F) above the usual
 *       bottom-end overlay/PiP band via symmetric bottom inset. Horizontal padding stays
 *       balanced so the main column stays centered. Stats may sit in that corner.
 *       Layout is <em>not</em> gated on detection — pinned-task probes are diagnostics-only.
 *       System PiP cannot be force-closed.</li>
 * </ol>
 *
 * <p>Does not add a new Appear-on-top requirement; {@code SYSTEM_ALERT_WINDOW} remains
 * launch-only (BAL). {@code HIDE_OVERLAY_WINDOWS} is a normal install-time permission.
 */
public final class OverlayGate {
    private static final String TAG = AlarmScheduler.TAG;
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    /**
     * Permanent bottom clearance so critical controls sit above the usual overlay/PiP
     * band. Landscape needs a taller reserve (short side); portrait uses a lighter floor.
     * No end inset — that shoved the whole column off-center.
     */
    private static final int CORNER_PAD_BOTTOM_PORTRAIT_DP = 120;
    /** Landscape PiP is short-side tall — leave ~40% so keys sit above the band. */
    private static final int CORNER_PAD_BOTTOM_LANDSCAPE_DP = 280;
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
     * Lift critical quiz panel content above the usual bottom-end overlay/PiP band.
     * Pass the quiz panel only — stats stay in the corner (non-critical). Detection is
     * diagnostics-only and never toggles layout. Call after {@code setContentView}.
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
                + ":layout=bottom_center";
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
     * Permanent bottom clearance for critical controls. Stats are skipped (may sit under
     * PiP). Horizontal padding stays at layout baseline so the column remains centered.
     */
    private static void applyCornerReserve(View content) {
        if (content.getId() == R.id.quiz_stats_bar) {
            // Non-critical chrome — leave in the bottom-end corner.
            return;
        }
        int left = content.getPaddingLeft();
        int top = content.getPaddingTop();
        int right = content.getPaddingRight();
        int bottom = content.getPaddingBottom();
        Object tag = content.getTag(R.id.quiz_panel);
        // Store baseline paddings once: [pl,pt,pr,pb]
        int[] base = tag instanceof int[] ? (int[]) tag : null;
        if (base == null || base.length < 4) {
            base = new int[] { left, top, right, bottom };
            content.setTag(R.id.quiz_panel, base);
        }
        int extraBottom = cornerBottomPx(content);
        // Symmetric L/R — only raise the floor so pad/answers clear the overlay band.
        content.setPadding(base[0], base[1], base[2], Math.max(base[3], extraBottom));
    }

    /** Bottom px sized to clear a typical tablet PiP / mini-player band. */
    private static int cornerBottomPx(View v) {
        android.util.DisplayMetrics dm = v.getResources().getDisplayMetrics();
        boolean landscape = dm.widthPixels > dm.heightPixels;
        int shortSide = Math.min(dm.widthPixels, dm.heightPixels);
        // Landscape uses height (short side): measured YouTube PiP tops ~37–40% up.
        // Portrait: lighter band — pad already sits well above typical PiP.
        float frac = landscape ? 0.42f : 0.16f;
        int floorDp = landscape ? CORNER_PAD_BOTTOM_LANDSCAPE_DP : CORNER_PAD_BOTTOM_PORTRAIT_DP;
        int base = Math.max(dp(v, floorDp), Math.round(shortSide * frac));
        // Small extra gap so key bottoms are not flush with the PiP top edge.
        return base + (landscape ? dp(v, 12) : 0);
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
