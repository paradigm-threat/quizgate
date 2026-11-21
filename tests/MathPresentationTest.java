import net.clevertree.quizgate.core.Catalog;
import net.clevertree.quizgate.core.MathPresentation;
import net.clevertree.quizgate.core.PresentedQuestion;
import net.clevertree.quizgate.core.Question;
import net.clevertree.quizgate.core.QuestionPicker;
import net.clevertree.quizgate.core.RenderFormat;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class MathPresentationTest {
    public static void main(String[] args) {
        testTrueCaseKeepsCorrectRhs();
        testFalseCaseOffsetsRhs();
        testCatalogNotDuplicatedForFormats();
        testAllowedFormatsForMath();
        testChoicePresentation();
        testRandomCoinFlipMixes();
        System.out.println("MathPresentationTest OK");
    }

    private static void testTrueCaseKeepsCorrectRhs() {
        Question q = math("m01", "1 + 1 = _", "2");
        PresentedQuestion p = MathPresentation.trueFalse(q, true, new Random(1L));
        Assert.isTrue(p.statementTrue, "expected a true statement");
        Assert.eq(RenderFormat.TRUE_FALSE, p.format, "format");
        Assert.eq(PresentedQuestion.CHOICE_TRUE, p.correctChoice, "choice true");
        Assert.eq("1 + 1 = 2", p.displaySymbolic, "rhs correct");
        Assert.isTrue(p.isCorrect("true"), "accept true");
        Assert.isTrue(!p.isCorrect("false"), "reject false");
    }

    private static void testFalseCaseOffsetsRhs() {
        Question q = math("m01", "1 + 1 = _", "2");
        Random r = new Random(99L);
        PresentedQuestion p = MathPresentation.trueFalse(q, false, r);
        Assert.isTrue(!p.statementTrue, "false statement");
        Assert.eq(PresentedQuestion.CHOICE_FALSE, p.correctChoice, "choice false");
        Assert.isTrue(p.displaySymbolic.startsWith("1 + 1 = "), "prefix");
        String rhs = p.displaySymbolic.substring("1 + 1 = ".length()).trim();
        int shown = Integer.parseInt(rhs);
        Assert.isTrue(shown != 2, "rhs must differ from correct");
        int delta = Math.abs(shown - 2);
        Assert.isTrue(delta >= 1 && delta <= MathPresentation.MAX_FALSE_OFFSET + 1,
                "small offset delta=" + delta);
        Assert.isTrue(shown >= 0, "non-negative rhs");
        Assert.isTrue(p.isCorrect("false"), "accept false");
        Assert.isTrue(!p.isCorrect("true"), "reject true");

        for (int i = 0; i < 100; i++) {
            int m = MathPresentation.mutateRhs(5, r);
            Assert.isTrue(m != 5, "mutate changes value");
            Assert.isTrue(m >= 0, "non-negative");
            Assert.isTrue(Math.abs(m - 5) <= MathPresentation.MAX_FALSE_OFFSET + 1, "bounded");
        }
    }

    private static void testRandomCoinFlipMixes() {
        Question q = math("m01", "1 + 1 = _", "2");
        Random r = new Random(42L);
        int t = 0, f = 0;
        for (int i = 0; i < 200; i++) {
            PresentedQuestion p = MathPresentation.trueFalse(q, r);
            if (p.statementTrue) t++;
            else f++;
        }
        Assert.isTrue(t > 40 && f > 40, "coin flip mix t=" + t + " f=" + f);
    }

    private static void testCatalogNotDuplicatedForFormats() {
        Question only = math("m01", "1 + 1 = _", "2");
        Catalog catalog = new Catalog(Arrays.asList(only));
        Assert.eq(1, catalog.size(), "single catalog row");

        QuestionPicker picker = new QuestionPicker(catalog, new Random(42L));
        Set<RenderFormat> seen = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            PresentedQuestion p = picker.first();
            Assert.eq("m01", p.id(), "same catalog id");
            Assert.eq(1, catalog.size(), "still one row");
            seen.add(p.format);
        }
        Assert.isTrue(seen.contains(RenderFormat.EQUATION), "saw equation");
        Assert.isTrue(seen.contains(RenderFormat.TRUE_FALSE), "saw true_false");
        Assert.isTrue(seen.contains(RenderFormat.CHOICE), "saw choice");
        Assert.eq(1, catalog.all().size(), "no duplicate items after presents");
    }

    private static void testAllowedFormatsForMath() {
        Question math = math("m01", "1 + 1 = _", "2");
        List<RenderFormat> allowed = RenderFormat.allowedFor(math);
        Assert.eq(3, allowed.size(), "math has three formats");
        Assert.isTrue(allowed.contains(RenderFormat.EQUATION), "equation");
        Assert.isTrue(allowed.contains(RenderFormat.TRUE_FALSE), "true_false");
        Assert.isTrue(allowed.contains(RenderFormat.CHOICE), "choice");

        Question mcOnly = new Question("a01", "math", "spoken", "1 + 1 = _",
                Arrays.asList("1", "2", "3"), "2", null, 1,
                Collections.singletonList(RenderFormat.CHOICE));
        List<RenderFormat> only = RenderFormat.allowedFor(mcOnly);
        Assert.eq(1, only.size(), "per-item override");
        Assert.eq(RenderFormat.CHOICE, only.get(0), "choice only");

        Question color = new Question("c1", "color", "which", "",
                Arrays.asList("red", "blue", "green"), "red", null);
        List<RenderFormat> colorAllowed = RenderFormat.allowedFor(color);
        Assert.eq(1, colorAllowed.size(), "non-math default");
        Assert.eq(RenderFormat.EQUATION, colorAllowed.get(0), "equation only");
    }

    private static void testChoicePresentation() {
        Question q = math("m01", "1 + 1 = _", "2");
        PresentedQuestion p = MathPresentation.choice(q);
        Assert.eq(RenderFormat.CHOICE, p.format, "format");
        Assert.isTrue(p.isChoice(), "isChoice");
        Assert.eq("1 + 1 = _", p.displaySymbolic, "blank symbolic");
        Assert.eq("2", p.correctChoice, "catalog correct");
        Assert.isTrue(p.isCorrect("2"), "accept correct");
        Assert.isTrue(!p.isCorrect("1"), "reject distractor");
        Assert.eq(q, MathPresentation.present(q, RenderFormat.CHOICE, new Random(1L)).question,
                "present routes to choice");
    }

    private static Question math(String id, String symbolic, String correct) {
        return new Question(id, "math", "spoken", symbolic,
                Arrays.asList("1", correct, "3"), correct, null);
    }
}
