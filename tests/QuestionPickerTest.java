import net.clevertree.quizgate.core.Catalog;
import net.clevertree.quizgate.core.PresentedQuestion;
import net.clevertree.quizgate.core.Question;
import net.clevertree.quizgate.core.QuestionPicker;
import net.clevertree.quizgate.core.RenderFormat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class QuestionPickerTest {
    public static void main(String[] args) {
        Question heavy = q("add", 4);
        Question light = q("sub", 1);
        Catalog catalog = new Catalog(Arrays.asList(heavy, light));
        QuestionPicker picker = new QuestionPicker(catalog, new Random(42L));

        Map<String, Integer> counts = new HashMap<>();
        int n = 2000;
        for (int i = 0; i < n; i++) {
            String id = picker.first().id();
            counts.put(id, counts.getOrDefault(id, 0) + 1);
        }
        int add = counts.getOrDefault("add", 0);
        int sub = counts.getOrDefault("sub", 0);
        Assert.isTrue(add > sub, "heavier weight should win more often add=" + add + " sub=" + sub);
        // Expected ~80% / 20% with weights 4:1 — allow wide band for RNG
        Assert.isTrue(add > n * 0.65, "add share too low: " + add);
        Assert.isTrue(sub < n * 0.35, "sub share too high: " + sub);
        Assert.isTrue(sub > n * 0.05, "sub should still appear: " + sub);

        PresentedQuestion next = picker.nextDifferent("add");
        Assert.eq("sub", next.id(), "only other question");

        // force_qid path stays on equation (ADB / screenshots)
        QuestionPicker forced = new QuestionPicker(catalog, new Random(1L)).preferFirst("add");
        for (int i = 0; i < 20; i++) {
            Assert.eq(RenderFormat.EQUATION, forced.first().format, "preferFirst → equation");
        }

        testSkipFormatBias();
        testSkipOpKindAndForcedFormat();
        testConsecutiveSkipsCanRepeatEasyFormat();
        System.out.println("QuestionPickerTest OK add=" + add + " sub=" + sub);
    }

    private static void testSkipFormatBias() {
        Question add = new Question("a1", "math", "one plus one", "1 + 1 = _",
                Arrays.asList("1", "2", "3"), "2", null, 2);
        Question sub = new Question("s1", "math", "two minus one", "2 - 1 = _",
                Arrays.asList("0", "1", "2"), "1", null, 1);
        Catalog catalog = new Catalog(Arrays.asList(add, sub));
        QuestionPicker picker = new QuestionPicker(catalog, new Random(7L));

        int easy = 0;
        int differentFmt = 0;
        int n = 200;
        RenderFormat prev = RenderFormat.EQUATION;
        for (int i = 0; i < n; i++) {
            PresentedQuestion p = picker.nextAfterSkip(add, prev);
            if (p.format == RenderFormat.TRUE_FALSE || p.format == RenderFormat.CHOICE) easy++;
            if (p.format != prev) differentFmt++;
            prev = p.format;
        }
        Assert.isTrue(easy > n * 0.55, "post-skip should favor easy formats easy=" + easy);
        Assert.isTrue(differentFmt > n * 0.4, "often changes format different=" + differentFmt);
        // Still allows same easy format again (no permanent ladder).
        Assert.isTrue(differentFmt < n, "must allow some format repeats");
    }

    private static void testSkipOpKindAndForcedFormat() {
        Assert.eq("add", QuestionPicker.opKind(math("a", "1 + 2 = _")), "add");
        Assert.eq("subtract", QuestionPicker.opKind(math("s", "5 - 2 = _")), "sub");
        Assert.eq("multiply", QuestionPicker.opKind(math("m", "2 × 3 = _")), "mul");

        Question add = math("a1", "1 + 1 = _");
        Question mul = math("m1", "2 × 2 = _");
        Catalog catalog = new Catalog(Arrays.asList(add, mul));
        QuestionPicker picker = new QuestionPicker(catalog, new Random(11L));
        int differentOp = 0;
        for (int i = 0; i < 100; i++) {
            PresentedQuestion p = picker.nextAfterSkip(add, RenderFormat.EQUATION);
            if ("multiply".equals(QuestionPicker.opKind(p.question))) differentOp++;
        }
        Assert.isTrue(differentOp > 40, "soft prefer other op after skip got=" + differentOp);

        QuestionPicker forced = new QuestionPicker(catalog, new Random(3L))
                .preferFormat(RenderFormat.EQUATION);
        for (int i = 0; i < 30; i++) {
            Assert.eq(RenderFormat.EQUATION,
                    forced.nextAfterSkip(add, RenderFormat.TRUE_FALSE).format,
                    "forced format wins after skip");
        }
    }

    private static void testConsecutiveSkipsCanRepeatEasyFormat() {
        Question a = math("a1", "1 + 1 = _");
        Question b = math("b1", "2 + 2 = _");
        Catalog catalog = new Catalog(Arrays.asList(a, b));
        QuestionPicker picker = new QuestionPicker(catalog, new Random(99L));
        int tfThenTf = 0;
        for (int i = 0; i < 150; i++) {
            PresentedQuestion p1 = picker.nextAfterSkip(a, RenderFormat.EQUATION);
            PresentedQuestion p2 = picker.nextAfterSkip(p1.question, p1.format);
            if (p1.format == RenderFormat.TRUE_FALSE && p2.format == RenderFormat.TRUE_FALSE) {
                tfThenTf++;
            }
        }
        Assert.isTrue(tfThenTf > 0, "consecutive skips may both be yes/no (no fixed ladder)");
    }

    private static Question math(String id, String symbolic) {
        return new Question(id, "math", "spoken " + id, symbolic,
                Arrays.asList("1", "2", "3"), "2", null, 1);
    }

    private static Question q(String id, int weight) {
        List<String> choices = Arrays.asList("1", "2", "3");
        return new Question(id, "math", "spoken " + id, "1+1", choices, "2", null, weight);
    }
}
