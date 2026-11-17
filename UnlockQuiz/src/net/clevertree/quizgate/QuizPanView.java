package net.clevertree.quizgate;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/**
 * 2D pannable host for the critical quiz column. Children can be dragged
 * anywhere on the page; translation is rubber-banded past the usable region
 * and snapped back on release so answer controls stay reachable.
 *
 * <p>Top/Bottom preference only sets the initial vertical anchor — pan is the
 * primary way to clear overlays.
 */
public final class QuizPanView extends FrameLayout {
    /**
     * Keep the panel center at least this far inside the viewport so answer
     * controls cannot sit fully off an edge.
     */
    private static final int CENTER_INSET_DP = 96;
    /** Soft overscroll past the hard clamp while dragging. */
    private static final float RUBBER = 0.35f;
    private static final long SNAP_MS = 240L;

    private final int touchSlop;
    private final int minVisiblePx;
    private final DecelerateInterpolator snapInterp = new DecelerateInterpolator();

    private float panX;
    private float panY;
    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    private boolean dragging;
    private boolean anchorBottom;
    private VelocityTracker velocityTracker;
    private ValueAnimator snapAnim;

    public QuizPanView(Context context) {
        this(context, null);
    }

    public QuizPanView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public QuizPanView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setClipChildren(false);
        setClipToPadding(false);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        minVisiblePx = Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, CENTER_INSET_DP, getResources().getDisplayMetrics()));
    }

    /** Prefer packing the panel toward the bottom (false = top, default). */
    public void setAnchorBottom(boolean bottom) {
        if (anchorBottom == bottom) return;
        anchorBottom = bottom;
        requestLayout();
        resetPan(false);
    }

    public boolean isAnchorBottom() {
        return anchorBottom;
    }

    /** Clear pan offset and re-anchor (optionally animated). */
    public void resetPan(boolean animate) {
        cancelSnap();
        if (!animate || (panX == 0f && panY == 0f)) {
            panX = 0f;
            panY = 0f;
            applyPan();
            return;
        }
        animateTo(0f, 0f);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int w = right - left;
        final int h = bottom - top;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int cw = child.getMeasuredWidth();
            int ch = child.getMeasuredHeight();
            int childLeft = (w - cw) / 2;
            int childTop = anchorBottom
                    ? Math.max(0, h - ch - getPaddingBottom())
                    : getPaddingTop();
            // Leave a little room for the stats bar when bottom-anchored.
            if (anchorBottom) {
                childTop = Math.max(0, childTop - dp(72));
            } else {
                childTop = Math.min(childTop + dp(8), Math.max(0, h - ch));
            }
            child.layout(childLeft, childTop, childLeft + cw, childTop + ch);
        }
        // Re-clamp after size/orientation changes so content stays recoverable.
        float[] clamped = clamp(panX, panY, false);
        panX = clamped[0];
        panY = clamped[1];
        applyPan();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(w, h);
        int childWSpec = MeasureSpec.makeMeasureSpec(Math.max(0, w - getPaddingLeft() - getPaddingRight()),
                MeasureSpec.AT_MOST);
        int childHSpec = MeasureSpec.makeMeasureSpec(Math.max(0, h), MeasureSpec.AT_MOST);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            child.measure(childWSpec, childHSpec);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelSnap();
                downX = lastX = ev.getX();
                downY = lastY = ev.getY();
                dragging = false;
                ensureVelocity(ev);
                break;
            case MotionEvent.ACTION_MOVE: {
                ensureVelocity(ev);
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;
                if (!dragging && Math.hypot(dx, dy) > touchSlop) {
                    dragging = true;
                    lastX = ev.getX();
                    lastY = ev.getY();
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                recycleVelocity();
                dragging = false;
                break;
            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        ensureVelocity(ev);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelSnap();
                downX = lastX = ev.getX();
                downY = lastY = ev.getY();
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float x = ev.getX();
                float y = ev.getY();
                float dx = x - lastX;
                float dy = y - lastY;
                if (!dragging && Math.hypot(x - downX, y - downY) > touchSlop) {
                    dragging = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (dragging) {
                    float[] soft = clamp(panX + dx, panY + dy, true);
                    panX = soft[0];
                    panY = soft[1];
                    applyPan();
                    lastX = x;
                    lastY = y;
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (dragging) {
                    float vx = 0f;
                    float vy = 0f;
                    if (velocityTracker != null) {
                        velocityTracker.computeCurrentVelocity(1000);
                        vx = velocityTracker.getXVelocity();
                        vy = velocityTracker.getYVelocity();
                    }
                    // Light fling projection, then snap into the hard usable region.
                    float projectedX = panX + vx / 8f;
                    float projectedY = panY + vy / 8f;
                    float[] hard = clamp(projectedX, projectedY, false);
                    animateTo(hard[0], hard[1]);
                } else {
                    float[] hard = clamp(panX, panY, false);
                    if (hard[0] != panX || hard[1] != panY) {
                        animateTo(hard[0], hard[1]);
                    }
                }
                dragging = false;
                recycleVelocity();
                return true;
            }
            default:
                return dragging;
        }
    }

    private void applyPan() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            child.setTranslationX(panX);
            child.setTranslationY(panY);
        }
    }

    /**
     * @param soft when true, allow rubber-band past the hard usable region while dragging
     */
    private float[] clamp(float x, float y, boolean soft) {
        View child = contentChild();
        if (child == null || getWidth() == 0 || getHeight() == 0) {
            return new float[] { x, y };
        }
        int parentW = getWidth();
        int parentH = getHeight();
        // Clamp so the panel's center stays inside the viewport (with inset).
        // Full-width panels would otherwise allow "45% visible" while the
        // centered answer keys sit completely off-screen.
        float cx = (child.getLeft() + child.getRight()) * 0.5f;
        float cy = (child.getTop() + child.getBottom()) * 0.5f;
        float insetX = Math.min(minVisiblePx, parentW * 0.5f);
        float insetY = Math.min(minVisiblePx, parentH * 0.5f);
        float minX = insetX - cx;
        float maxX = parentW - insetX - cx;
        float minY = insetY - cy;
        float maxY = parentH - insetY - cy;
        if (minX > maxX) {
            float mid = (minX + maxX) / 2f;
            minX = maxX = mid;
        }
        if (minY > maxY) {
            float mid = (minY + maxY) / 2f;
            minY = maxY = mid;
        }

        if (!soft) {
            return new float[] {
                    Math.max(minX, Math.min(maxX, x)),
                    Math.max(minY, Math.min(maxY, y))
            };
        }

        // Rubber-band: ease past the edge so the child never fully vanishes mid-drag.
        float softX = rubber(x, minX, maxX);
        float softY = rubber(y, minY, maxY);
        return new float[] { softX, softY };
    }

    private static float rubber(float v, float min, float max) {
        if (v < min) {
            float over = min - v;
            return min - over * RUBBER;
        }
        if (v > max) {
            float over = v - max;
            return max + over * RUBBER;
        }
        return v;
    }

    private void animateTo(float targetX, float targetY) {
        cancelSnap();
        final float fromX = panX;
        final float fromY = panY;
        if (fromX == targetX && fromY == targetY) {
            panX = targetX;
            panY = targetY;
            applyPan();
            return;
        }
        snapAnim = ValueAnimator.ofFloat(0f, 1f);
        snapAnim.setDuration(SNAP_MS);
        snapAnim.setInterpolator(snapInterp);
        snapAnim.addUpdateListener(a -> {
            float f = (Float) a.getAnimatedValue();
            panX = fromX + (targetX - fromX) * f;
            panY = fromY + (targetY - fromY) * f;
            applyPan();
        });
        snapAnim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                panX = targetX;
                panY = targetY;
                applyPan();
                snapAnim = null;
            }
        });
        snapAnim.start();
    }

    private void cancelSnap() {
        if (snapAnim != null) {
            snapAnim.cancel();
            snapAnim = null;
        }
    }

    private View contentChild() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) return child;
        }
        return null;
    }

    private void ensureVelocity(MotionEvent ev) {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(ev);
    }

    private void recycleVelocity() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    private int dp(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()));
    }
}
