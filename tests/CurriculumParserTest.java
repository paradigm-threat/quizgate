import net.clevertree.quizgate.core.Catalog;
import net.clevertree.quizgate.core.CurriculumPack;
import net.clevertree.quizgate.core.CurriculumParser;
import net.clevertree.quizgate.core.PresentedQuestion;
import net.clevertree.quizgate.core.Question;
import net.clevertree.quizgate.core.QuestionPicker;
import net.clevertree.quizgate.core.QuizSession;
import net.clevertree.quizgate.core.RenderFormat;
import net.clevertree.quizgate.core.SessionOutcome;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

public class CurriculumParserTest {
    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length > 0 ? args[0] : "UnlockQuiz/assets/curricula/basic-math");
        String manifest = Files.readString(root.resolve("manifest.json"));
        String curriculum = Files.readString(root.resolve("curriculum.json"));
        CurriculumPack pack = CurriculumParser.parse(manifest, curriculum);
        Assert.eq("basic-math", pack.id, "pack id");
        Assert.isTrue(pack.catalog.size() >= 20, "enough items");
        Assert.isTrue(pack.roundSize >= 1, "round size");
        Question m01 = pack.catalog.byId("m01");
        Assert.isTrue(m01 != null, "legacy m01");
        Assert.eq("2", m01.correct, "m01 correct");
        // L1+ math keeps engine defaults (equation / T/F / choice).
        Assert.eq(3, RenderFormat.allowedFor(m01).size(), "math formats");

        Question a01 = pack.catalog.byId("a01");
        Assert.isTrue(a01 != null, "L0 a01");
        Assert.eq(4, a01.choices.size(), "L0 four choices");
        Assert.eq(1, RenderFormat.allowedFor(a01).size(), "L0 choice-only");
        Assert.eq(RenderFormat.CHOICE, RenderFormat.allowedFor(a01).get(0), "L0 format");

        Question m40 = pack.catalog.byId("m40");
        Assert.isTrue(m40 != null, "multiply m40");
        Assert.isTrue(m40.symbolic.contains("×"), "multiply uses × not escaped junk: " + m40.symbolic);

        // Minimal parser must decode JSON unicode escapes (regression: showed "u00d7" on device).
        String escapedItem = "{"
                + "\"id\":\"bm.k.esc\",\"legacyId\":\"esc\",\"tier\":\"K\","
                + "\"skills\":[\"multiply_within_10\"],\"type\":\"math\","
                + "\"spoken\":\"two times two\","
                + "\"symbolic\":\"2 \\u00d7 2 = _\","
                + "\"choices\":[\"3\",\"4\",\"5\"],\"correct\":\"4\",\"weight\":1"
                + "}";
        CurriculumPack escaped = CurriculumParser.parse(
                "{\"id\":\"t\",\"title\":\"t\",\"version\":1,\"ageMin\":4,\"ageMax\":7,\"difficultyRank\":1,\"locale\":\"en\"}",
                "{\"id\":\"t\",\"title\":\"t\",\"tiers\":[],\"roundSize\":1,\"earnMinutesPerCorrect\":1,"
                        + "\"wrongBehavior\":\"next\",\"consolingUnlock\":true,\"maxWrong\":3,\"items\":["
                        + escapedItem + "]}");
        Assert.eq("2 × 2 = _", escaped.catalog.byId("esc").symbolic, "unicode escape decode");

        Assert.isTrue(pack.catalogForTier("K").size() > 0, "tier K");

        // Level 0 = add under 8, MC only; prior L0 content is now L1.
        Catalog l0 = pack.catalogFiltered("all", 0);
        Assert.isTrue(l0.size() >= 15, "level 0 pool");
        Assert.isTrue(l0.byId("a01") != null, "L0 has a01");
        Assert.isTrue(l0.byId("m01") == null, "L0 excludes former add-within-10");
        Assert.isTrue(l0.byId("m21") == null, "L0 excludes subtract");
        Assert.isTrue(l0.byId("m16") == null, "L0 excludes 1+0");
        Assert.isTrue(l0.byId("m36") == null, "L0 excludes multiply");
        Catalog l1 = pack.catalogFiltered("all", 1);
        Assert.isTrue(l1.byId("m01") != null, "L1 has add-within-10");
        Assert.isTrue(l1.byId("m21") != null, "L1 has easy subtract");
        Assert.isTrue(l1.byId("m16") == null, "L1 excludes zero identity");
        Assert.isTrue(l1.byId("m36") == null, "L1 excludes multiply");
        Catalog l2 = pack.catalogFiltered("all", 2);
        Assert.isTrue(l2.byId("m16") != null, "L2 includes zero identity");
        Assert.isTrue(l2.byId("m36") == null, "L2 still excludes multiply");
        Catalog l3 = pack.catalogFiltered("all", 3);
        Assert.isTrue(l3.byId("m36") != null, "L3 includes multiply");

        // Round of 3 corrects unlocks
        QuestionPicker picker = new QuestionPicker(pack.catalog, new Random(7L))
                .preferFormat(RenderFormat.EQUATION);
        QuizSession s = new QuizSession("r1", "interval", 1000L, picker, 3, 3, true);
        for (int i = 0; i < 2; i++) {
            PresentedQuestion q = s.getCurrent();
            QuizSession.AnswerResult r = s.answer(q.correctChoice, 1000L + i);
            Assert.eq(QuizSession.AnswerResult.CORRECT_CONTINUE, r, "continue " + i);
        }
        PresentedQuestion q = s.getCurrent();
        QuizSession.AnswerResult done = s.answer(q.correctChoice, 3000L);
        Assert.eq(QuizSession.AnswerResult.CORRECT_ROUND_DONE, done, "round done");
        Assert.eq(SessionOutcome.CORRECT, s.getOutcome(), "outcome");
        Assert.eq(3, s.getCorrectInRound(), "correct count");
        System.out.println("CurriculumParserTest OK");
    }
}
