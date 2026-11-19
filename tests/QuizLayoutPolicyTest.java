import net.clevertree.quizgate.core.QuizLayoutPolicy;

/**
 * Regression: quiz column must stay readable in portrait, landscape, and odd
 * aspect ratios — especially when the panel is taller than the viewport (the
 * old center-based pan clamp shoved the question off-screen).
 */
public class QuizLayoutPolicyTest {

    private static final int MIN_VIS = QuizLayoutPolicy.DEFAULT_MIN_VISIBLE_PX;

    public static void main(String[] args) {
        assertWidthPolicy();
        assertTallPortraitKeepsQuestion();
        assertWideLandscape();
        assertBottomAnchorKeepsAnswers();
        assertZeroPadFullBleed();
        assertRestingPanNeverCentersTallAway();
        assertManyViewports();
        System.out.println("QuizLayoutPolicyTest OK");
    }

    private static void assertWidthPolicy() {
        Assert.isTrue(QuizLayoutPolicy.isWidthReadable(800, 800), "full width ok");
        Assert.isTrue(QuizLayoutPolicy.isWidthReadable(800, 740), "92% ok");
        Assert.isTrue(!QuizLayoutPolicy.isWidthReadable(800, 400), "half width not readable");
        Assert.eq(800, QuizLayoutPolicy.contentWidthPx(800, 0), "zero pad → full");
        Assert.eq(784, QuizLayoutPolicy.contentWidthPx(800, 16), "16px total pad");
    }

    /** Portrait tablet-ish: tall digit-pad panel, top anchor, stats reserve at bottom. */
    private static void assertTallPortraitKeepsQuestion() {
        int w = 800;
        int h = 1280;
        int stats = 72;
        int childH = 1600; // taller than viewport (pad + choices)
        int left = QuizLayoutPolicy.initialChildLeft(w, w);
        int top = QuizLayoutPolicy.initialChildTop(false, h, childH, 0, stats);
        Assert.eq(0, left, "full-bleed left");
        Assert.eq(0, top, "top-anchored at usable top");

        float[] pan = QuizLayoutPolicy.restingPan(w, h, left, top, left + w, top + childH,
                MIN_VIS, false);
        Assert.isTrue(Math.abs(pan[0]) < 0.01f, "resting panX ~0");
        Assert.isTrue(pan[1] <= 0.01f, "must not shove down (was center-clamp bug)");
        Assert.isTrue(QuizLayoutPolicy.isReadable(w, h, left, top, left + w, top + childH,
                pan[0], pan[1], false), "tall portrait readable at rest");
    }

    private static void assertWideLandscape() {
        int w = 1280;
        int h = 800;
        int childH = 600;
        int left = 0;
        int top = QuizLayoutPolicy.initialChildTop(false, h, childH, 0, 64);
        float[] pan = QuizLayoutPolicy.restingPan(w, h, left, top, left + w, top + childH,
                MIN_VIS, false);
        Assert.isTrue(QuizLayoutPolicy.isReadable(w, h, left, top, left + w, top + childH,
                pan[0], pan[1], false), "landscape readable");
    }

    private static void assertBottomAnchorKeepsAnswers() {
        int w = 800;
        int h = 1280;
        int childH = 1600;
        int top = QuizLayoutPolicy.initialChildTop(true, h, childH, 72, 0);
        // Bottom-packed: top = h - childH = negative usable → clamped to topReserve
        Assert.eq(72, top, "bottom anchor packs above top stats reserve when oversized");
        float[] pan = QuizLayoutPolicy.restingPan(w, h, 0, top, w, top + childH, MIN_VIS, true);
        Assert.isTrue(QuizLayoutPolicy.isReadable(w, h, 0, top, w, top + childH,
                pan[0], pan[1], true), "bottom anchor readable");
    }

    private static void assertZeroPadFullBleed() {
        int[] widths = {320, 360, 411, 600, 800, 1024, 1440};
        for (int w : widths) {
            int content = QuizLayoutPolicy.contentWidthPx(w, 0);
            Assert.isTrue(QuizLayoutPolicy.isWidthReadable(w, content),
                    "zero-pad readable w=" + w);
        }
    }

    /**
     * Reproduce the old failure mode: center-style collapse would pick mid Y
     * when child taller than parent — edge limits must keep resting pan readable.
     */
    private static void assertRestingPanNeverCentersTallAway() {
        int w = 600;
        int h = 900;
        int childH = 1400;
        int top = 0;
        QuizLayoutPolicy.PanLimits lim = QuizLayoutPolicy.panLimits(
                w, h, 0, top, w, top + childH, MIN_VIS);
        // Center of panel in parent coords at pan=0 is childH/2 = 700; viewport mid=450.
        // Old code forced pan so center sits in viewport → panY negative large → question gone.
        float[] rest = QuizLayoutPolicy.restingPan(w, h, 0, top, w, top + childH, MIN_VIS, false);
        Assert.isTrue(rest[1] >= lim.minY - 0.01f && rest[1] <= lim.maxY + 0.01f,
                "resting within limits");
        Assert.isTrue(rest[1] <= MIN_VIS,
                "top stays near top of screen, not centered away");
        float questionTop = top + rest[1];
        Assert.isTrue(questionTop <= MIN_VIS, "question top on-screen");
    }

    private static void assertManyViewports() {
        int[][] sizes = {
                {360, 640},   // phone portrait
                {640, 360},   // phone landscape
                {800, 1280},  // tablet portrait
                {1280, 800},  // tablet landscape
                {1024, 1024}, // square
                {320, 480},   // tiny portrait
                {480, 320},   // tiny landscape
                {1440, 2560}, // tall phone
                {2560, 1440}, // wide
                {200, 800},   // extreme narrow
                {800, 200},   // extreme short
        };
        for (int[] wh : sizes) {
            int w = wh[0];
            int h = wh[1];
            for (boolean bottom : new boolean[] {false, true}) {
                for (int childH : new int[] {h / 2, h, h + 400, Math.max(100, h * 2)}) {
                    int stats = Math.min(72, Math.max(24, h / 16));
                    int topReserve = bottom ? stats : 0;
                    int botReserve = bottom ? 0 : stats;
                    int childW = QuizLayoutPolicy.contentWidthPx(w, 0);
                    int left = QuizLayoutPolicy.initialChildLeft(w, childW);
                    int top = QuizLayoutPolicy.initialChildTop(bottom, h, childH, topReserve, botReserve);
                    float[] pan = QuizLayoutPolicy.restingPan(w, h, left, top,
                            left + childW, top + childH, MIN_VIS, bottom);
                    String label = "w=" + w + " h=" + h + " childH=" + childH
                            + " bottom=" + bottom;
                    Assert.isTrue(QuizLayoutPolicy.isWidthReadable(w, childW),
                            "width " + label);
                    // Overlap must be non-zero after resting pan.
                    QuizLayoutPolicy.RectF bounds = QuizLayoutPolicy.translatedBounds(
                            left, top, left + childW, top + childH, pan[0], pan[1]);
                    float overlap = QuizLayoutPolicy.overlapArea(bounds, w, h);
                    Assert.isTrue(overlap > 0f, "overlap>0 " + label);
                    if (childH >= h && !bottom) {
                        Assert.isTrue(bounds.top <= MIN_VIS + 1f,
                                "tall top-anchor question visible " + label
                                        + " top=" + bounds.top);
                    }
                }
            }
        }
    }
}
