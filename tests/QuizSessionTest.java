import net.clevertree.quizgate.core.Catalog;
import net.clevertree.quizgate.core.Question;
import net.clevertree.quizgate.core.QuestionPicker;
import net.clevertree.quizgate.core.QuizSession;
import net.clevertree.quizgate.core.SessionOutcome;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;

public class QuizSessionTest {
    public static void main(String[] args) throws Exception {
        Catalog c = Catalog.fromJson(new String(Files.readAllBytes(Paths.get(args[0]))));
        testCorrect(c);
        testThreeWrong(c);
        testBypass(c);
        testNoRepeatAfterWrong(c);
        System.out.println("QuizSessionTest OK");
    }

    private static QuestionPicker picker(Catalog c, long seed) {
        return new QuestionPicker(c, new Random(seed));
    }

    private static void testCorrect(Catalog c) {
        QuizSession s = new QuizSession("s1", "adb_force", 1000L, picker(c, 1L));
        Question q = s.getCurrent();
        QuizSession.AnswerResult r = s.answer(q.correct, 2000L);
        Assert.eq(QuizSession.AnswerResult.CORRECT_UNLOCK, r, "correct unlock");
        Assert.eq(SessionOutcome.CORRECT, s.getOutcome(), "outcome correct");
        Assert.isTrue(s.isEnded(), "ended");
    }

    private static void testThreeWrong(Catalog c) {
        QuizSession s = new QuizSession("s2", "interval", 1000L, picker(c, 2L));
        for (int i = 0; i < 2; i++) {
            Question q = s.getCurrent();
            String wrong = wrongChoice(q);
            QuizSession.AnswerResult r = s.answer(wrong, 1000L + i);
            Assert.eq(QuizSession.AnswerResult.WRONG_CONTINUE, r, "wrong continue " + i);
            Assert.eq(SessionOutcome.IN_PROGRESS, s.getOutcome(), "still open");
        }
        Question q = s.getCurrent();
        QuizSession.AnswerResult r = s.answer(wrongChoice(q), 3000L);
        Assert.eq(QuizSession.AnswerResult.WRONG_CONSOLING, r, "consoling");
        Assert.eq(SessionOutcome.CONSOLING_UNLOCK, s.getOutcome(), "consoling outcome");
        Assert.eq(3, s.getMissStreak(), "streak 3");
    }

    private static void testBypass(Catalog c) {
        QuizSession s = new QuizSession("s3", "morning", 1000L, picker(c, 3L));
        s.bypass(1500L);
        Assert.eq(SessionOutcome.BYPASS, s.getOutcome(), "bypass");
        Assert.isTrue(s.isEnded(), "ended bypass");
    }

    private static void testNoRepeatAfterWrong(Catalog c) {
        QuizSession s = new QuizSession("s4", "interval", 1000L, picker(c, 42L));
        String first = s.getCurrent().id;
        s.answer(wrongChoice(s.getCurrent()), 1100L);
        Assert.isTrue(!first.equals(s.getCurrent().id) || c.size() == 1, "different after wrong");
    }

    private static String wrongChoice(Question q) {
        for (String ch : q.choices) {
            if (!q.isCorrect(ch)) return ch;
        }
        throw new IllegalStateException("no wrong");
    }
}
