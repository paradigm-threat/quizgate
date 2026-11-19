package net.clevertree.quizgate.core;

/**
 * WCAG 2.x relative-luminance contrast (axe / Accessibility Scanner class of
 * checks). Pure math — used by {@link UiContrastCatalog} regression tests so
 * light-on-light / dark-on-dark theme drift fails CI without a device.
 */
public final class Contrast {
    /** Normal text / UI chrome (WCAG AA). */
    public static final double AA_NORMAL = 4.5;
    /** Large text / bold UI (≥18pt / 14pt bold) and non-text UI components (AA). */
    public static final double AA_LARGE = 3.0;
    /** Prefer stronger contrast for kid-facing controls. */
    public static final double KID_UI = 4.5;

    private Contrast() {}

    /** Contrast ratio in [1, 21] for two opaque sRGB colors (ARGB or RGB). */
    public static double ratio(int colorA, int colorB) {
        double l1 = relativeLuminance(colorA);
        double l2 = relativeLuminance(colorB);
        double lighter = Math.max(l1, l2);
        double darker = Math.min(l1, l2);
        return (lighter + 0.05) / (darker + 0.05);
    }

    public static boolean passes(int foreground, int background, double minRatio) {
        return ratio(foreground, background) + 1e-9 >= minRatio;
    }

    /** Parse {@code #RGB}, {@code #RRGGBB}, or {@code #AARRGGBB}. Alpha ignored for luminance. */
    public static int parseColor(String hex) {
        if (hex == null) throw new IllegalArgumentException("null color");
        String h = hex.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (h.length() == 3) {
            char r = h.charAt(0), g = h.charAt(1), b = h.charAt(2);
            h = ("" + r + r + g + g + b + b);
        }
        long v = Long.parseLong(h, 16);
        if (h.length() == 6) return (int) (0xFF000000L | v);
        if (h.length() == 8) return (int) v;
        throw new IllegalArgumentException("bad color: " + hex);
    }

    public static double relativeLuminance(int argb) {
        double r = srgbChannel(((argb >> 16) & 0xFF) / 255.0);
        double g = srgbChannel(((argb >> 8) & 0xFF) / 255.0);
        double b = srgbChannel((argb & 0xFF) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double srgbChannel(double c) {
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
