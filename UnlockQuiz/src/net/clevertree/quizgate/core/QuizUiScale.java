package net.clevertree.quizgate.core;

/**
 * Resolution-aware quiz chrome sizing. Phone (~360dp short side) stays near 1×;
 * tablets scale up so text/buttons fill roughly 60–70% of the viewport instead
 * of looking sparse at ~40%.
 */
public final class QuizUiScale {

    public static final float REF_SHORTEST_DP = 360f;
    public static final float MIN_SCALE = 1.0f;
    public static final float MAX_SCALE = 1.85f;
    /** Target fraction of screen width for the interactive column (choices / pad / skip). */
    public static final float MIN_COLUMN_FRACTION = 0.60f;
    public static final float MAX_COLUMN_FRACTION = 0.72f;

    private QuizUiScale() {}

    public static float densityOrOne(float density) {
        return density > 0.01f ? density : 1f;
    }

    /** Scale from physical pixels + density (shortest side vs 360dp phone). */
    public static float scaleFor(int widthPx, int heightPx, float density) {
        float d = densityOrOne(density);
        float wDp = widthPx / d;
        float hDp = heightPx / d;
        float shortest = Math.min(wDp, hDp);
        if (shortest <= 0f) return MIN_SCALE;
        float raw = shortest / REF_SHORTEST_DP;
        return clamp(raw, MIN_SCALE, MAX_SCALE);
    }

    /**
     * Width of the interactive column as a fraction of the screen.
     * Grows with scale into the 60–72% band.
     */
    public static float columnWidthFraction(float scale) {
        float s = clamp(scale, MIN_SCALE, MAX_SCALE);
        // 1.0 → 0.60, 1.85 → ~0.72
        float t = (s - MIN_SCALE) / (MAX_SCALE - MIN_SCALE);
        return MIN_COLUMN_FRACTION + t * (MAX_COLUMN_FRACTION - MIN_COLUMN_FRACTION);
    }

    public static int columnWidthPx(int screenWidthPx, float scale) {
        if (screenWidthPx <= 0) return 0;
        float frac = columnWidthFraction(scale);
        return Math.max(1, Math.round(screenWidthPx * frac));
    }

    public static float sp(float baseSp, float scale) {
        return baseSp * clamp(scale, MIN_SCALE, MAX_SCALE);
    }

    public static int scaledDp(int baseDp, float scale, float density) {
        float d = densityOrOne(density);
        float s = clamp(scale, MIN_SCALE, MAX_SCALE);
        return Math.max(1, Math.round(baseDp * s * d));
    }

    public static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
