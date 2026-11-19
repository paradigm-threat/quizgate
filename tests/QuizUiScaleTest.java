import net.clevertree.quizgate.core.QuizUiScale;

/** Regression: tablet scale fills ~60–70% column; phones stay near 1×. */
public class QuizUiScaleTest {
    public static void main(String[] args) {
        // Phone 360×640 @2x density → 360dp short → scale ~1
        float phone = QuizUiScale.scaleFor(720, 1280, 2f);
        Assert.isTrue(phone >= 0.99f && phone <= 1.05f, "phone ~1× got=" + phone);

        // Tablet portrait ~800dp short @2x (1600×2560)
        float tab = QuizUiScale.scaleFor(1600, 2560, 2f);
        Assert.isTrue(tab > 1.4f && tab <= QuizUiScale.MAX_SCALE, "tablet scales up got=" + tab);

        float fracPhone = QuizUiScale.columnWidthFraction(1.0f);
        Assert.eq(QuizUiScale.MIN_COLUMN_FRACTION, fracPhone, 0.001,
                "phone column fraction");
        float fracTab = QuizUiScale.columnWidthFraction(tab);
        Assert.isTrue(fracTab >= 0.60f && fracTab <= 0.72f,
                "tablet column 60–72% got=" + fracTab);

        int col = QuizUiScale.columnWidthPx(1600, tab);
        float used = col / 1600f;
        Assert.isTrue(used >= 0.60f && used <= 0.72f, "column px fraction got=" + used);

        Assert.isTrue(QuizUiScale.sp(28f, 1.5f) > 40f, "sp scales");
        Assert.isTrue(QuizUiScale.scaledDp(72, 1.5f, 2f) > QuizUiScale.scaledDp(72, 1f, 2f),
                "dp scales");

        // Clamp extremes
        Assert.eq(QuizUiScale.MIN_SCALE, QuizUiScale.scaleFor(100, 100, 3f), 0.001, "tiny clamp");
        Assert.eq(QuizUiScale.MAX_SCALE, QuizUiScale.scaleFor(4000, 6000, 1f), 0.001, "huge clamp");

        System.out.println("QuizUiScaleTest OK");
    }
}
