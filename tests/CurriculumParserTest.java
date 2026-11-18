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
        // Formats come from engine defaults — catalog is not duplicated for true/false.
        Assert.eq(3, RenderFormat.allowedFor(m01).size(), "math formats");

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
