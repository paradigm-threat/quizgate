import net.clevertree.quizgate.core.QuizUiScale;

/**
 * Regression: scale may enlarge mildly, but worst-case chrome must always fit
 * the usable viewport — no clipping. On supported sizes, every interactive
 * control (digit keys, Skip, choice, T/F) stays fully visible and ≥
 * {@link QuizUiScale#MIN_TOUCH_DP} so it remains clickable.
 */
public class QuizUiScaleTest {
    public static void main(String[] args) {
        assertMildDesire();
        assertFitNeverClips();
        assertControlsVisibleAndClickable();
        assertShortLandscapeShrinks();
        assertTabletPortraitComfortable();
        assertColumnNearFullBleed();
        System.out.println("QuizUiScaleTest OK");
    }

    private static void assertMildDesire() {
        float phone = QuizUiScale.scaleFor(720, 1280, 2f);
        Assert.isTrue(phone >= 0.99f && phone <= 1.05f, "phone desire ~1× got=" + phone);

        float tab = QuizUiScale.scaleFor(1600, 2560, 2f);
        Assert.isTrue(tab <= QuizUiScale.MAX_SCALE + 0.001f, "tablet capped got=" + tab);
        Assert.isTrue(tab < 1.2f, "no more 1.85× blow-up got=" + tab);
    }

    private static void assertFitNeverClips() {
        int[][] sizes = {
                {720, 1280},   // phone port
                {1280, 720},   // phone land
                {1600, 2560},  // tablet port
                {2560, 1600},  // tablet land
                {1200, 1920},  // SM_X210 port
                {1920, 1200},  // SM_X210 land
                {800, 480},    // short land
                {480, 800},
                {2000, 1200},
                {600, 400},
        };
        float[] densities = {1.5f, 2f, 2.75f, 3f};
        for (int[] wh : sizes) {
            for (float dens : densities) {
                int statsPx = Math.round(56f * dens);
                boolean compact = QuizUiScale.useCompactChrome(wh[0], wh[1], dens);
                float s = QuizUiScale.scaleToFit(wh[0], wh[1], dens, statsPx);
                float usableDp = (wh[1] - statsPx) / dens;
                float chrome = QuizUiScale.estimateWorstChromeDp(s, compact);
                Assert.isTrue(chrome <= usableDp + 0.5f,
                        "no clip w=" + wh[0] + " h=" + wh[1] + " dens=" + dens
                                + " scale=" + s
                                + " chrome=" + chrome
                                + " usable=" + usableDp);
                Assert.isTrue(s > 0f && s <= QuizUiScale.MAX_SCALE + 0.001f,
                        "scale bounds got=" + s);
            }
        }
    }

    /**
     * Supported viewports: after scaleToFit, chrome fits AND every control
     * height stays ≥ MIN_TOUCH_DP (fully visible + clickable).
     */
    private static void assertControlsVisibleAndClickable() {
        int[][] sizes = {
                {720, 1280},
                {1280, 720},
                {1600, 2560},
                {2560, 1600},
                {1200, 1920}, // device under test
                {1920, 1200},
                {1080, 1920},
                {1920, 1080},
        };
        float[] densities = {1.5f, 2f, 2.75f, 3f};
        for (int[] wh : sizes) {
            for (float dens : densities) {
                int statsPx = Math.round(56f * dens);
                float usableDp = (wh[1] - statsPx) / dens;
                if (!QuizUiScale.isSupportedViewport(usableDp)) continue;

                boolean compact = QuizUiScale.useCompactChrome(wh[0], wh[1], dens);
                float s = QuizUiScale.scaleToFit(wh[0], wh[1], dens, statsPx);
                float chrome = QuizUiScale.estimateWorstChromeDp(s, compact);

                Assert.isTrue(QuizUiScale.fitsUsableHeight(s, usableDp, compact),
                        "visible fit w=" + wh[0] + " h=" + wh[1] + " dens=" + dens
                                + " scale=" + s + " chrome=" + chrome
                                + " usable=" + usableDp);
                Assert.isTrue(QuizUiScale.controlsFullyClickable(s, compact),
                        "clickable w=" + wh[0] + " h=" + wh[1] + " dens=" + dens
                                + " scale=" + s
                                + " key=" + QuizUiScale.controlHeightDp(
                                compact ? QuizUiScale.BASE_KEY_COMPACT_DP
                                        : QuizUiScale.BASE_KEY_DP, s)
                                + " skip=" + QuizUiScale.controlHeightDp(
                                QuizUiScale.BASE_SKIP_DP, s));
            }
        }
    }

    private static void assertShortLandscapeShrinks() {
        float s = QuizUiScale.scaleToFit(1920, 800, 2f, 120);
        Assert.isTrue(s < 1.0f, "short land shrinks got=" + s);
        float usableDp = (800 - 120) / 2f;
        Assert.isTrue(QuizUiScale.fitsUsableHeight(s, usableDp, true), "short land fits");
        if (QuizUiScale.isSupportedViewport(usableDp)) {
            Assert.isTrue(QuizUiScale.controlsFullyClickable(s, true),
                    "short land clickable scale=" + s);
        }
    }

    /** SM_X210 portrait must stay comfortable (near 1×, all controls large). */
    private static void assertTabletPortraitComfortable() {
        float dens = 1.5f; // 240dpi
        int statsPx = Math.round(56f * dens);
        float s = QuizUiScale.scaleToFit(1200, 1920, dens, statsPx);
        Assert.isTrue(s >= 0.95f && s <= QuizUiScale.MAX_SCALE + 0.001f,
                "tablet port scale comfortable got=" + s);
        Assert.isTrue(QuizUiScale.controlsFullyClickable(s, false),
                "tablet port clickable");
        float key = QuizUiScale.controlHeightDp(QuizUiScale.BASE_KEY_DP, s);
        Assert.isTrue(key >= 48f, "tablet keys generous got=" + key);
    }

    private static void assertColumnNearFullBleed() {
        float f1 = QuizUiScale.columnWidthFraction(1f);
        Assert.isTrue(f1 >= 0.88f && f1 <= 0.92f, "column inset got=" + f1);
        int col = QuizUiScale.columnWidthPx(1000, 1f);
        Assert.isTrue(col >= 880 && col <= 920, "column px got=" + col);
        // Side inset ≥ 4% each side so rounded keys aren't edge-clipped.
        Assert.isTrue(col <= 920, "keep side margins");
    }
}
