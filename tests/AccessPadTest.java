import net.clevertree.quizgate.core.AccessPad;
import net.clevertree.quizgate.core.MathPresentation;
import net.clevertree.quizgate.core.PresentedQuestion;
import net.clevertree.quizgate.core.Question;
import net.clevertree.quizgate.core.RenderFormat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class AccessPadTest {
    public static void main(String[] args) {
        testFixedAI();
        testCenteredWindowIncludesAnswer();
        testCenteredClampsAtEdges();
        testRandomLettersIncludeCorrect();
        testAccessFormatsAllowed();
        testAccessTrueFalseLetter();
        testAccessIsCorrectLetter();
        System.out.println("AccessPadTest OK");
    }

    private static void testFixedAI() {
        List<String> g = AccessPad.fixedLettersAI();
        Assert.eq(9, g.size(), "A-I size");
        Assert.eq("A", g.get(0), "first");
        Assert.eq("I", g.get(8), "last");
    }

    private static void testCenteredWindowIncludesAnswer() {
        List<String> g = AccessPad.centeredLetterWindow("L");
        Assert.eq(9, g.size(), "window size");
        Assert.isTrue(g.contains("L"), "contains L");
        // Contiguous ascending
        for (int i = 1; i < g.size(); i++) {
            Assert.isTrue(g.get(i).charAt(0) == g.get(i - 1).charAt(0) + 1, "contiguous");
        }
    }

    private static void testCenteredClampsAtEdges() {
        List<String> a = AccessPad.centeredLetterWindow("A");
        Assert.eq("A", a.get(0), "starts A");
        Assert.eq(9, a.size(), "size");
        List<String> z = AccessPad.centeredLetterWindow("Z");
        Assert.eq("Z", z.get(z.size() - 1), "ends Z");
    }

    private static void testRandomLettersIncludeCorrect() {
        List<String> g = AccessPad.randomLetters("L", new Random(42L));
        Assert.eq(9, g.size(), "random size");
        Assert.isTrue(g.contains("L"), "has L");
        Set<String> uniq = new HashSet<>(g);
        Assert.eq(9, uniq.size(), "unique");
    }

    private static void testAccessFormatsAllowed() {
        Question q = access("fa01", "A, B, C, _", "D", Arrays.asList("D", "C", "E", "B"));
        List<RenderFormat> allowed = RenderFormat.allowedFor(q);
        Assert.eq(3, allowed.size(), "access default formats");
        Assert.isTrue(allowed.contains(RenderFormat.EQUATION), "equation");
        Assert.isTrue(allowed.contains(RenderFormat.TRUE_FALSE), "tf");
        Assert.isTrue(allowed.contains(RenderFormat.CHOICE), "choice");
    }

    private static void testAccessTrueFalseLetter() {
        Question q = access("fa01", "A, B, C, _", "D", Arrays.asList("D", "C", "E", "B"));
        PresentedQuestion t = MathPresentation.trueFalse(q, true, new Random(1L));
        Assert.isTrue(t.displaySymbolic.contains("D"), "true shows D");
        PresentedQuestion f = MathPresentation.trueFalse(q, false, new Random(2L));
        Assert.isTrue(!f.displaySymbolic.endsWith("D") || !f.statementTrue, "false differs");
        Assert.isTrue(!f.statementTrue, "false flag");
    }

    private static void testAccessIsCorrectLetter() {
        Question q = access("fa01", "A, B, C, _", "D", Arrays.asList("D", "C", "E", "B"));
        Assert.isTrue(q.isCorrect("D"), "D");
        Assert.isTrue(q.isCorrect("d"), "d");
        Assert.isTrue(!q.isCorrect("E"), "E wrong");
    }

    private static Question access(String id, String sym, String correct, List<String> choices) {
        return new Question(id, "access", "What comes next?", sym, choices, correct, null);
    }
}
