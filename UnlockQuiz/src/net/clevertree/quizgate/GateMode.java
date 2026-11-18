package net.clevertree.quizgate;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;

import net.clevertree.quizgate.core.GatePolicy;

import java.util.List;

/**
 * Hard (recommended: Accessibility confine until unlock) vs Soft (overlay/FSI launch,
 * Home = bypass). Shared schedule + quiz UI; separate confinement and grants.
 * Pure normalize / critical-gap rules live in {@link GatePolicy}.
 */
public final class GateMode {
    public static final String SOFT = GatePolicy.SOFT;
    public static final String HARD = GatePolicy.HARD;

    private GateMode() {}

    public static String normalize(String mode) {
        return GatePolicy.normalize(mode);
    }

    public static boolean isHard(Context ctx) {
        return GatePolicy.isHard(new AppPrefs(ctx).getGateMode());
    }

    public static boolean isHard(String mode) {
        return GatePolicy.isHard(mode);
    }

    public static boolean isSoft(Context ctx) {
        return !isHard(ctx);
    }

    /** True when our Accessibility service is enabled for this package. */
    public static boolean isAccessibilityEnabled(Context ctx) {
        if (ctx == null) return false;
        try {
            AccessibilityManager am =
                    (AccessibilityManager) ctx.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am == null) return false;
            List<AccessibilityServiceInfo> enabled =
                    am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            if (enabled == null) return false;
            String expect = ctx.getPackageName() + "/" + QuizGateAccessibilityService.class.getName();
            String expectShort = ctx.getPackageName() + "/.QuizGateAccessibilityService";
            for (AccessibilityServiceInfo info : enabled) {
                if (info == null || info.getId() == null) continue;
                String id = info.getId();
                if (expect.equals(id) || expectShort.equals(id)
                        || id.endsWith("/" + QuizGateAccessibilityService.class.getName())
                        || id.endsWith("/.QuizGateAccessibilityService")) {
                    return true;
                }
            }
            // Fallback: Settings secure string (some OEMs lag the AM list).
            String setting = Settings.Secure.getString(
                    ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (TextUtils.isEmpty(setting)) return false;
            TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
            splitter.setString(setting);
            while (splitter.hasNext()) {
                String id = splitter.next();
                if (expect.equals(id) || expectShort.equals(id)
                        || id.endsWith("/QuizGateAccessibilityService")
                        || id.endsWith("/.QuizGateAccessibilityService")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
