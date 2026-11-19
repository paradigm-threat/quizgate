import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import net.clevertree.quizgate.core.Contrast;
import net.clevertree.quizgate.core.UiContrastCatalog;

/**
 * Axe / Accessibility-Scanner-class regression: WCAG AA contrast for kid-facing
 * color pairs in light and night themes. Fails CI when a theme drifts into
 * light-on-light (the nav/Skip failure mode).
 */
public class UiContrastTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length > 0 ? args[0] : ".");
        Path light = root.resolve("UnlockQuiz/res/values/colors.xml");
        Path night = root.resolve("UnlockQuiz/res/values-night/colors.xml");
        Assert.isTrue(Files.isRegularFile(light), "missing " + light);
        Assert.isTrue(Files.isRegularFile(night), "missing " + night);

        assertTheme("light", Files.readString(light));
        assertTheme("night", Files.readString(night));
        assertMathSmoke();
        System.out.println("UiContrastTest OK pairs="
                + UiContrastCatalog.requiredPairs().size() + " themes=2");
    }

    private static void assertTheme(String label, String xml) {
        Map<String, Integer> colors = UiContrastCatalog.parseColorsXml(xml);
        List<String> fails = UiContrastCatalog.failures(colors);
        Assert.isTrue(fails.isEmpty(),
                label + " contrast failures:\n  - " + String.join("\n  - ", fails));
    }

    private static void assertMathSmoke() {
        // Black on white ≈ 21:1; known WCAG example.
        int black = Contrast.parseColor("#000000");
        int white = Contrast.parseColor("#FFFFFF");
        Assert.isTrue(Contrast.ratio(black, white) > 20.0, "black/white");
        Assert.isTrue(!Contrast.passes(
                        Contrast.parseColor("#FFFFFF"),
                        Contrast.parseColor("#FFF6E8"),
                        Contrast.AA_NORMAL),
                "white-on-cream must fail AA (the bug class we catch)");
    }
}
