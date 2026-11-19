package net.clevertree.quizgate.core;

/**
 * Resolution-aware quiz chrome sizing with a hard fit guarantee: estimated
 * chrome height must stay inside the usable viewport (screen minus stats
 * reserve). Scale may shrink below 1× on short screens; mild enlarge only on
 * roomy tablets. Never grow into clipping. Interactive controls stay at or
 * above {@link #MIN_TOUCH_DP} on supported viewports.
 */
public final class QuizUiScale {

    public static final float REF_SHORTEST_DP = 360f;
    /** Prefer not to go below this when the viewport still has room. */
    public static final float SOFT_MIN_SCALE = 0.72f;
    /** Absolute floor for extreme short screens (fit still wins above this). */
    public static final float HARD_MIN_SCALE = 0.45f;
    /** Alias used by older call sites; equals {@link #SOFT_MIN_SCALE}. */
    public static final float MIN_SCALE = SOFT_MIN_SCALE;
    /** Soft desire cap — never chase large empty space if it risks overflow. */
    public static final float MAX_SCALE = 1.12f;
    /** Side inset so keys/Skip never kiss the screen edge (visible + tappable). */
    public static final float MIN_COLUMN_FRACTION = 0.88f;
    public static final float MAX_COLUMN_FRACTION = 0.92f;
    /** Headroom under stats / system edges; fit always wins over enlarge. */
    public static final float FIT_SLACK = 0.98f;
    /** Minimum tappable control height (dp) after scale. */
    public static final float MIN_TOUCH_DP = 40f;
    /** Usable height (dp) at/above which fit+clickable are both required. */
    public static final float SUPPORTED_USABLE_DP = 300f;

    /** Base control heights (dp) before scale — must match QuizActivity builders. */
    public static final int BASE_KEY_DP = 60;
    public static final int BASE_KEY_COMPACT_DP = 48;
    public static final int BASE_SKIP_DP = 48;
    public static final int BASE_CHOICE_DP = 68;
    public static final int BASE_CHOICE_COMPACT_DP = 56;
    public static final int BASE_TF_DP = 96;
    public static final int BASE_TF_COMPACT_DP = 72;

    private QuizUiScale() {}

    public static float densityOrOne(float density) {
        return density > 0.01f ? density : 1f;
    }

    /** Landscape or short portrait → compact chrome (leaner text, shorter keys). */
    public static boolean useCompactChrome(int widthPx, int heightPx, float density) {
        float d = densityOrOne(density);
        return widthPx > heightPx || (heightPx / d) < 640f;
    }

    /**
     * Desired scale from shortest side (mild). Prefer
     * {@link #scaleToFit} so content never clips.
     */
    public static float scaleFor(int widthPx, int heightPx, float density) {
        float d = densityOrOne(density);
        float wDp = widthPx / d;
        float hDp = heightPx / d;
        float shortest = Math.min(wDp, hDp);
        if (shortest <= 0f) return 1f;
        float raw = shortest / REF_SHORTEST_DP;
        // Map 360→1.0, 800→~1.12 capped — no more 1.85× blow-ups.
        float mild = 1f + (raw - 1f) * 0.25f;
        return clamp(mild, 1f, MAX_SCALE);
    }

    /**
     * Scale that fits the worst-case quiz chrome (digit pad + question + skip)
     * into the usable height. May shrink below 1×; never exceeds
     * {@link #MAX_SCALE}. Fit always wins over the soft floor.
     */
    public static float scaleToFit(int widthPx, int heightPx, float density,
                                   int statsReservePx) {
        boolean compact = useCompactChrome(widthPx, heightPx, density);
        float desire = scaleFor(widthPx, heightPx, density);
        float d = densityOrOne(density);
        float usablePx = Math.max(1f, heightPx - Math.max(0, statsReservePx));
        float usableDp = usablePx / d;
        if (fitsUsableHeight(desire, usableDp, compact)) {
            return desire;
        }
        // Binary search largest scale that fits; allow down to HARD_MIN.
        float lo = HARD_MIN_SCALE;
        float hi = desire;
        float best = HARD_MIN_SCALE;
        for (int i = 0; i < 28; i++) {
            float mid = (lo + hi) * 0.5f;
            if (estimateWorstChromeDp(mid, compact) <= usableDp * FIT_SLACK) {
                best = mid;
                lo = mid;
            } else {
                hi = mid;
            }
        }
        // Prefer soft min when it still fits.
        if (best < SOFT_MIN_SCALE
                && fitsUsableHeight(SOFT_MIN_SCALE, usableDp, compact)) {
            best = SOFT_MIN_SCALE;
        }
        // Fit always wins: if HARD_MIN still overflows, shrink exactly to budget.
        if (!fitsUsableHeight(best, usableDp, compact)) {
            float chrome1 = estimateWorstChromeDp(1f, compact);
            if (chrome1 > 0.01f) {
                best = (usableDp * FIT_SLACK) / Math.max(chrome1, 0.01f);
            }
        }
        // No artificial floor — containment is mandatory; keep scale positive.
        return clamp(best, 0.01f, MAX_SCALE);
    }

    /** Portrait (roomy) worst-case chrome at scale. */
    public static float estimateWorstChromeDp(float scale) {
        return estimateWorstChromeDp(scale, false);
    }

    /**
     * Approximate vertical budget (dp) for the tallest ask path: header +
     * spoken + symbolic + digit answer + 4-row pad + skip + gaps.
     * Compact matches landscape / short-portrait builders in QuizActivity.
     */
    public static float estimateWorstChromeDp(float scale, boolean compact) {
        float s = Math.max(0.01f, scale);
        if (compact) {
            // Lean text/gaps so phone-land keeps keys ≥ MIN_TOUCH_DP while fitting.
            float header = 20f * s;
            float spoken = 22f * s;
            float symbolic = 24f * s;
            float digitAns = 34f * s;
            float keyH = BASE_KEY_COMPACT_DP * s;
            float keyMargin = 1f * s;
            float pad = 4f * (keyH + 2f * keyMargin);
            float skip = BASE_SKIP_DP * s;
            float gaps = 6f * s;
            return header + spoken + symbolic + digitAns + pad + skip + gaps;
        }
        float header = 32f * s;
        float spoken = 36f * s;
        float symbolic = 44f * s;
        float digitAns = 48f * s;
        float keyH = BASE_KEY_DP * s;
        float keyMargin = 4f * s;
        float pad = 4f * (keyH + 2f * keyMargin);
        float skip = BASE_SKIP_DP * s;
        float gaps = 32f * s;
        return header + spoken + symbolic + digitAns + pad + skip + gaps;
    }

    public static boolean fitsUsableHeight(float scale, float usableHeightDp) {
        return fitsUsableHeight(scale, usableHeightDp, false);
    }

    public static boolean fitsUsableHeight(float scale, float usableHeightDp,
                                          boolean compact) {
        if (usableHeightDp <= 0f) return false;
        return estimateWorstChromeDp(scale, compact) <= usableHeightDp * FIT_SLACK;
    }

    /** Control height in dp after scale (pre-density). */
    public static float controlHeightDp(int baseDp, float scale) {
        return baseDp * Math.max(0.01f, scale);
    }

    /**
     * True when digit keys, skip, choice, and T/F controls meet
     * {@link #MIN_TOUCH_DP} at this scale.
     */
    public static boolean controlsFullyClickable(float scale, boolean compact) {
        float key = controlHeightDp(compact ? BASE_KEY_COMPACT_DP : BASE_KEY_DP, scale);
        float skip = controlHeightDp(BASE_SKIP_DP, scale);
        float choice = controlHeightDp(
                compact ? BASE_CHOICE_COMPACT_DP : BASE_CHOICE_DP, scale);
        float tf = controlHeightDp(compact ? BASE_TF_COMPACT_DP : BASE_TF_DP, scale);
        return key >= MIN_TOUCH_DP - 0.01f
                && skip >= MIN_TOUCH_DP - 0.01f
                && choice >= MIN_TOUCH_DP - 0.01f
                && tf >= MIN_TOUCH_DP - 0.01f;
    }

    /**
     * Supported viewport: enough usable height that fit and clickable are both
     * required. Tiny / exotic sizes must still fit (no clip) but may dip under
     * the soft touch floor.
     */
    public static boolean isSupportedViewport(float usableHeightDp) {
        return usableHeightDp >= SUPPORTED_USABLE_DP;
    }

    public static float columnWidthFraction(float scale) {
        float s = clamp(scale, 0.01f, MAX_SCALE);
        float t = (s - HARD_MIN_SCALE) / (MAX_SCALE - HARD_MIN_SCALE);
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        return MIN_COLUMN_FRACTION + t * (MAX_COLUMN_FRACTION - MIN_COLUMN_FRACTION);
    }

    public static int columnWidthPx(int screenWidthPx, float scale) {
        if (screenWidthPx <= 0) return 0;
        float frac = columnWidthFraction(scale);
        return Math.max(1, Math.round(screenWidthPx * frac));
    }

    public static float sp(float baseSp, float scale) {
        return baseSp * clamp(scale, 0.01f, MAX_SCALE);
    }

    public static int scaledDp(int baseDp, float scale, float density) {
        float d = densityOrOne(density);
        float s = clamp(scale, 0.01f, MAX_SCALE);
        return Math.max(1, Math.round(baseDp * s * d));
    }

    public static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
