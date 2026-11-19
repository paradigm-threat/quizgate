package net.clevertree.quizgate.core;

/**
 * Pure layout math for the quiz column: keep question + answers readable at any
 * viewport size / orientation. Used by {@code QuizPanView} and JVM regression tests.
 *
 * <p>Previously, pan limits were center-based and collapsed to a midpoint when the
 * panel was taller than the screen — that shoved the question off the top in
 * portrait while the stats bar (a sibling view) still looked fine.
 */
public final class QuizLayoutPolicy {

    /** Minimum overlap (px) of content with the viewport on each axis. */
    public static final int DEFAULT_MIN_VISIBLE_PX = 48;

    /** Content width must cover at least this fraction of the viewport. */
    public static final float MIN_WIDTH_FRACTION = 0.92f;

    /** Minimum fraction of the content area that must intersect the viewport. */
    public static final float MIN_OVERLAP_FRACTION = 0.35f;

    public static final float RUBBER = 0.35f;

    private QuizLayoutPolicy() {}

    public static final class PanLimits {
        public final float minX;
        public final float maxX;
        public final float minY;
        public final float maxY;

        public PanLimits(float minX, float maxX, float minY, float maxY) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
        }
    }

    public static final class RectF {
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

        public RectF(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public float width() { return right - left; }
        public float height() { return bottom - top; }
    }

    /**
     * Edge-based pan limits: keep at least {@code minVisiblePx} of the child
     * overlapping the parent on each axis. When the child is larger than the
     * parent, {@code pan = 0} with a top-left anchored child stays valid so the
     * question remains on-screen.
     */
    public static PanLimits panLimits(int parentW, int parentH,
                                      int childLeft, int childTop,
                                      int childRight, int childBottom,
                                      int minVisiblePx) {
        if (parentW <= 0 || parentH <= 0) {
            return new PanLimits(0f, 0f, 0f, 0f);
        }
        int inset = Math.max(1, Math.min(minVisiblePx, Math.min(parentW, parentH) / 2));
        // After translation t: edge' = edge + t. Require overlap ≥ inset.
        float minX = inset - childRight;
        float maxX = parentW - inset - childLeft;
        float minY = inset - childBottom;
        float maxY = parentH - inset - childTop;
        if (minX > maxX) {
            float mid = (minX + maxX) / 2f;
            minX = maxX = mid;
        }
        if (minY > maxY) {
            float mid = (minY + maxY) / 2f;
            minY = maxY = mid;
        }
        return new PanLimits(minX, maxX, minY, maxY);
    }

    public static float[] clampPan(float x, float y, PanLimits lim, boolean soft) {
        if (lim == null) return new float[] { x, y };
        if (!soft) {
            return new float[] {
                    clamp(x, lim.minX, lim.maxX),
                    clamp(y, lim.minY, lim.maxY)
            };
        }
        return new float[] {
                rubber(x, lim.minX, lim.maxX),
                rubber(y, lim.minY, lim.maxY)
        };
    }

    public static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    public static float rubber(float v, float min, float max) {
        if (v < min) return min - (min - v) * RUBBER;
        if (v > max) return max + (v - max) * RUBBER;
        return v;
    }

    /** Usable content width after horizontal padding (both sides combined). */
    public static int contentWidthPx(int parentW, int totalHorizontalPadPx) {
        if (parentW <= 0) return 0;
        int pad = Math.max(0, totalHorizontalPadPx);
        return Math.max(0, parentW - pad);
    }

    public static boolean isWidthReadable(int parentW, int contentW) {
        if (parentW <= 0 || contentW <= 0) return false;
        return contentW >= parentW * MIN_WIDTH_FRACTION;
    }

    /**
     * Initial Y for the quiz panel inside the usable band (viewport minus
     * reserved stats / system edges). Top anchor pins to {@code topReserve};
     * bottom anchor packs above {@code bottomReserve}.
     */
    public static int initialChildTop(boolean anchorBottom, int parentH, int childH,
                                      int topReserve, int bottomReserve) {
        if (parentH <= 0) return 0;
        int top = Math.max(0, topReserve);
        int bottom = Math.max(top, parentH - Math.max(0, bottomReserve));
        if (childH <= 0) return top;
        if (anchorBottom) {
            return Math.max(top, bottom - childH);
        }
        return top;
    }

    /** Horizontal center for a full-width (or nearly full-width) child. */
    public static int initialChildLeft(int parentW, int childW) {
        if (parentW <= 0 || childW <= 0) return 0;
        return Math.max(0, (parentW - childW) / 2);
    }

    public static RectF translatedBounds(int childLeft, int childTop,
                                         int childRight, int childBottom,
                                         float panX, float panY) {
        return new RectF(childLeft + panX, childTop + panY,
                childRight + panX, childBottom + panY);
    }

    public static float overlapArea(RectF a, float viewportW, float viewportH) {
        if (a == null || viewportW <= 0 || viewportH <= 0) return 0f;
        float l = Math.max(0f, a.left);
        float t = Math.max(0f, a.top);
        float r = Math.min(viewportW, a.right);
        float b = Math.min(viewportH, a.bottom);
        if (r <= l || b <= t) return 0f;
        return (r - l) * (b - t);
    }

    /**
     * True when enough of the content intersects the viewport and the top of a
     * tall top-anchored panel has not been shoved off-screen.
     */
    public static boolean isReadable(int parentW, int parentH,
                                     int childLeft, int childTop,
                                     int childRight, int childBottom,
                                     float panX, float panY,
                                     boolean anchorBottom) {
        if (parentW <= 0 || parentH <= 0) return false;
        int cw = childRight - childLeft;
        int ch = childBottom - childTop;
        if (cw <= 0 || ch <= 0) return false;
        if (!isWidthReadable(parentW, cw)) return false;

        RectF bounds = translatedBounds(childLeft, childTop, childRight, childBottom, panX, panY);
        float overlap = overlapArea(bounds, parentW, parentH);
        float contentArea = (float) cw * (float) ch;
        float viewportArea = (float) parentW * (float) parentH;
        float denom = Math.min(contentArea, viewportArea);
        if (denom <= 0f || overlap / denom < MIN_OVERLAP_FRACTION) return false;

        // Tall top-anchored panels: question lives at the top — keep it in view.
        if (!anchorBottom && ch >= parentH) {
            if (bounds.top > DEFAULT_MIN_VISIBLE_PX) return false;
        }
        // Tall bottom-anchored: keep the bottom (answers) reachable.
        if (anchorBottom && ch >= parentH) {
            if (bounds.bottom < parentH - DEFAULT_MIN_VISIBLE_PX) return false;
        }
        return true;
    }

    /**
     * Default pan after layout/orientation change: prefer 0,0 when readable,
     * otherwise clamp into limits (never center-shove a tall panel).
     */
    public static float[] restingPan(int parentW, int parentH,
                                     int childLeft, int childTop,
                                     int childRight, int childBottom,
                                     int minVisiblePx,
                                     boolean anchorBottom) {
        PanLimits lim = panLimits(parentW, parentH, childLeft, childTop,
                childRight, childBottom, minVisiblePx);
        float[] zero = clampPan(0f, 0f, lim, false);
        if (isReadable(parentW, parentH, childLeft, childTop, childRight, childBottom,
                zero[0], zero[1], anchorBottom)) {
            return zero;
        }
        // Prefer pinning the critical edge into view.
        float y = anchorBottom ? lim.maxY : lim.minY;
        return clampPan(zero[0], y, lim, false);
    }
}
