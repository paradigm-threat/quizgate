import net.clevertree.quizgate.core.QuizLayoutPolicy;

/**
 * Regression: quiz column stays centered and readable in portrait, landscape,
 * and odd aspect ratios — never top/bottom parent skew.
 */
public class QuizLayoutPolicyTest {

    private static final int MIN_VIS = QuizLayoutPolicy.DEFAULT_MIN_VISIBLE_PX;

    public static void main(String[] args) {
        assertWidthPolicy();
        assertCenterWhenFits();
        assertTallPinsTop();
        assertWideLandscape();
        assertZeroPadFullBleed();
        assertRestingPanTall();
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

    private static void assertCenterWhenFits() {
        int w = 800;
        int h = 1280;
        int stats = 72;
        int childH = 400;
        int left = QuizLayoutPolicy.initialChildLeft(w, w);
        int top = QuizLayoutPolicy.initialChildTop(h, childH, 0, stats);
        Assert.eq(0, left, "full-bleed left");
        int usable = h - stats;
        int expected = (usable - childH) / 2;
        Assert.eq(expected, top, "centered in usable band");
        float[] pan = QuizLayoutPolicy.restingPan(w, h, left, top, left + w, top + childH, MIN_VIS);
        Assert.isTrue(Math.abs(pan[0]) < 0.01f && Math.abs(pan[1]) < 0.01f, "resting pan ~0");
        Assert.isTrue(QuizLayoutPolicy.isReadable(w, h, left, top, left + w, top + childH,
                pan[0], pan[1]), "centered panel readable");
    }

    private static void assertTallPinsTop() {
        int w = 800;
        int h = 1280;
        int stats = 72;
        int childH = 1600;
        int top = QuizLayoutPolicy.initialChildTop(h, childH, 0, stats);
        Assert.eq(0, top, "tall panel pins to top of usable band");
        float[] pan = QuizLayoutPolicy.restingPan(w, h, 0, top, w, top + childH, MIN_VIS);
        Assert.isTrue(pan[1] <= 0.01f, "must not shove question off-screen");
        Assert.isTrue(QuizLayoutPolicy.isReadable(w, h, 0, top, w, top + childH,
                pan[0], pan[1]), "tall portrait readable");
    }

    private static void assertWideLandscape() {
        int w = 1280;
        int h = 800;
        int childH = 500;
        int top = QuizLayoutPolicy.initialChildTop(h, childH, 0, 64);
        int usable = h - 64;
        Assert.eq((usable - childH) / 2, top, "landscape centered");
        float[] pan = QuizLayoutPolicy.restingPan(w, h, 0, top, w, top + childH, MIN_VIS);
        Assert.isTrue(QuizLayoutPolicy.isReadable(w, h, 0, top, w, top + childH,
                pan[0], pan[1]), "landscape readable");
    }

    private static void assertZeroPadFullBleed() {
        int[] widths = {320, 360, 411, 600, 800, 1024, 1440};
        for (int w : widths) {
            int content = QuizLayoutPolicy.contentWidthPx(w, 0);
            Assert.isTrue(QuizLayoutPolicy.isWidthReadable(w, content),
                    "zero-pad readable w=" + w);
        }
    }

    private static void assertRestingPanTall() {
        int w = 600;
        int h = 900;
        int childH = 1400;
        int top = 0;
        float[] rest = QuizLayoutPolicy.restingPan(w, h, 0, top, w, top + childH, MIN_VIS);
        float questionTop = top + rest[1];
        Assert.isTrue(questionTop <= MIN_VIS, "question top on-screen");
    }

    private static void assertManyViewports() {
        int[][] sizes = {
                {360, 640},
                {640, 360},
                {800, 1280},
                {1280, 800},
                {1024, 1024},
                {320, 480},
                {480, 320},
                {1440, 2560},
                {2560, 1440},
                {200, 800},
                {800, 200},
        };
        for (int[] wh : sizes) {
            int w = wh[0];
            int h = wh[1];
            for (int childH : new int[] {Math.max(80, h / 3), h / 2, h, h + 400, Math.max(100, h * 2)}) {
                int stats = Math.min(72, Math.max(24, h / 16));
                int childW = QuizLayoutPolicy.contentWidthPx(w, 0);
                int left = QuizLayoutPolicy.initialChildLeft(w, childW);
                int top = QuizLayoutPolicy.initialChildTop(h, childH, 0, stats);
                float[] pan = QuizLayoutPolicy.restingPan(w, h, left, top,
                        left + childW, top + childH, MIN_VIS);
                String label = "w=" + w + " h=" + h + " childH=" + childH;
                Assert.isTrue(QuizLayoutPolicy.isWidthReadable(w, childW), "width " + label);
                QuizLayoutPolicy.RectF bounds = QuizLayoutPolicy.translatedBounds(
                        left, top, left + childW, top + childH, pan[0], pan[1]);
                float overlap = QuizLayoutPolicy.overlapArea(bounds, w, h);
                Assert.isTrue(overlap > 0f, "overlap>0 " + label);
                int usable = h - stats;
                if (childH < usable) {
                    int expected = (usable - childH) / 2;
                    Assert.eq(expected, top, "centered " + label);
                } else {
                    Assert.eq(0, top, "tall pins top " + label);
                    Assert.isTrue(bounds.top <= MIN_VIS + 1f,
                            "tall question visible " + label + " top=" + bounds.top);
                }
            }
        }
    }
}
