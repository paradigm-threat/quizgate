package net.clevertree.quizgate.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kid/parent-facing color pairs that must stay readable in light and night
 * themes. Mirrors what axe / Accessibility Scanner flag for text contrast.
 *
 * <p>Surfaces that live in drawables are duplicated here as named keys so the
 * catalog stays the single regression checklist — keep drawables in sync via
 * {@code @color/…} references.
 */
public final class UiContrastCatalog {
    private static final Pattern COLOR = Pattern.compile(
            "<color\\s+name=\"([^\"]+)\">\\s*(#[0-9A-Fa-f]{3,8})\\s*</color>");

    private UiContrastCatalog() {}

    public static final class Pair {
        public final String fgName;
        public final String bgName;
        public final double minRatio;
        public final String role;

        public Pair(String fgName, String bgName, double minRatio, String role) {
            this.fgName = fgName;
            this.bgName = bgName;
            this.minRatio = minRatio;
            this.role = role;
        }
    }

    /** Critical pairs checked for every theme map. */
    public static List<Pair> requiredPairs() {
        List<Pair> out = new ArrayList<>();
        // Primary reading
        out.add(new Pair("header", "bg", Contrast.AA_NORMAL, "body on page"));
        out.add(new Pair("body_muted", "bg", Contrast.AA_NORMAL, "muted copy on page"));
        out.add(new Pair("body_muted", "surface_onboard_panel", Contrast.AA_NORMAL, "onboard body"));
        out.add(new Pair("header", "surface_onboard_panel", Contrast.AA_NORMAL, "onboard title"));
        out.add(new Pair("symbolic", "bg", Contrast.AA_NORMAL, "equation on page"));
        out.add(new Pair("accent_dark", "bg", Contrast.AA_NORMAL, "digit answer on page"));
        // Controls kids tap
        out.add(new Pair("header", "skip_fill", Contrast.KID_UI, "Skip label"));
        out.add(new Pair("header", "button", Contrast.KID_UI, "digit key label"));
        out.add(new Pair("button_stroke", "button", Contrast.AA_LARGE, "digit key outline"));
        out.add(new Pair("on_accent", "accent", Contrast.KID_UI, "OK / primary CTA"));
        out.add(new Pair("on_accent", "btn_secondary_fill", Contrast.KID_UI, "secondary CTA"));
        out.add(new Pair("on_accent", "success", Contrast.KID_UI, "success CTA"));
        // Stats chrome
        out.add(new Pair("header", "surface_stats_ready", Contrast.AA_NORMAL, "stats on ready card"));
        out.add(new Pair("stats_mood_great", "surface_stats_great", Contrast.AA_NORMAL, "right count"));
        out.add(new Pair("stats_mood_trying", "surface_stats_trying", Contrast.AA_NORMAL, "wrong count"));
        out.add(new Pair("stats_mood_ready", "surface_stats_ready", Contrast.AA_LARGE, "mood label"));
        // Setup permission rows
        out.add(new Pair("header", "surface_setup_row", Contrast.AA_NORMAL, "setup row title"));
        out.add(new Pair("header", "surface_setup_card", Contrast.AA_NORMAL, "setup card title"));
        // System bars (icons are drawn by OEM; bar vs page still needs separation)
        out.add(new Pair("button_stroke", "system_bar", Contrast.AA_LARGE, "bar edge vs icons cue"));
        return Collections.unmodifiableList(out);
    }

    public static Map<String, Integer> parseColorsXml(String xml) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (xml == null) return map;
        Matcher m = COLOR.matcher(xml);
        while (m.find()) {
            map.put(m.group(1), Contrast.parseColor(m.group(2)));
        }
        return map;
    }

    public static List<String> failures(Map<String, Integer> colors) {
        List<String> fails = new ArrayList<>();
        for (Pair p : requiredPairs()) {
            Integer fg = colors.get(p.fgName);
            Integer bg = colors.get(p.bgName);
            if (fg == null || bg == null) {
                fails.add(String.format(Locale.US,
                        "missing color %s or %s (%s)", p.fgName, p.bgName, p.role));
                continue;
            }
            double r = Contrast.ratio(fg, bg);
            if (r + 1e-9 < p.minRatio) {
                fails.add(String.format(Locale.US,
                        "%s on %s = %.2f:1 < %.1f:1 (%s)",
                        p.fgName, p.bgName, r, p.minRatio, p.role));
            }
        }
        return fails;
    }
}
