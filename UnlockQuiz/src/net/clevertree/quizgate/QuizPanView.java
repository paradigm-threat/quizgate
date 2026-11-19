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

import net.clevertree.quizgate.core.QuizLayoutPolicy;

/**
 * 2D pannable host for the critical quiz column. Children can be dragged
 * anywhere on the page; translation is rubber-banded past the usable region
 * and snapped back on release so answer controls stay reachable.
 *
 * <p>Top/Bottom preference only sets the initial vertical anchor — pan is the
 * primary way to clear overlays. Pan limits are edge-based (see
 * {@link QuizLayoutPolicy}) so tall portrait panels do not shove the question
 * off-screen.
 */
public final class QuizPanView extends FrameLayout {
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
    private int topReservePx;
    private int bottomReservePx;
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
        // No host padding — padding here shrinks the column and was stacking with
        // nested panel pads until portrait looked narrow / obscured.
        setPadding(0, 0, 0, 0);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        minVisiblePx = Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                QuizLayoutPolicy.DEFAULT_MIN_VISIBLE_PX,
                getResources().getDisplayMetrics()));
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

    /**
     * Reserve edges for sibling chrome (stats bar) so the question never lays
     * out underneath it.
     */
    public void setEdgeReservesPx(int topPx, int bottomPx) {
        int t = Math.max(0, topPx);
        int b = Math.max(0, bottomPx);
        if (t == topReservePx && b == bottomReservePx) return;
        topReservePx = t;
        bottomReservePx = b;
        requestLayout();
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
            int childLeft = QuizLayoutPolicy.initialChildLeft(w, cw);
            int childTop = QuizLayoutPolicy.initialChildTop(
                    anchorBottom, h, ch, topReservePx, bottomReservePx);
            child.layout(childLeft, childTop, childLeft + cw, childTop + ch);
        }
        // After size/orientation changes, rest at a readable pan (usually 0,0).
        View content = contentChild();
        if (content != null && w > 0 && h > 0) {
            float[] rest = QuizLayoutPolicy.restingPan(w, h,
                    content.getLeft(), content.getTop(),
                    content.getRight(), content.getBottom(),
                    minVisiblePx, anchorBottom);
            panX = rest[0];
            panY = rest[1];
        }
        applyPan();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(w, h);
        // EXACTLY full width — AT_MOST let nested wrap shrink the column in portrait.
        int childWSpec = MeasureSpec.makeMeasureSpec(Math.max(0, w), MeasureSpec.EXACTLY);
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

    private float[] clamp(float x, float y, boolean soft) {
        View child = contentChild();
        if (child == null || getWidth() == 0 || getHeight() == 0) {
            return new float[] { x, y };
        }
        QuizLayoutPolicy.PanLimits lim = QuizLayoutPolicy.panLimits(
                getWidth(), getHeight(),
                child.getLeft(), child.getTop(),
                child.getRight(), child.getBottom(),
                minVisiblePx);
        return QuizLayoutPolicy.clampPan(x, y, lim, soft);
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
}
