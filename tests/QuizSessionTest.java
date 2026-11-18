import net.clevertree.quizgate.core.Catalog;
import net.clevertree.quizgate.core.PresentedQuestion;
import net.clevertree.quizgate.core.QuestionPicker;
import net.clevertree.quizgate.core.QuizSession;
import net.clevertree.quizgate.core.RenderFormat;
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
        testSkipDoesNotRaiseMissStreak(c);
        testSkipThenWrongsStillConsoling(c);
        testMultipleSkipsStayOpen(c);
        System.out.println("QuizSessionTest OK");
    }

    private static QuestionPicker picker(Catalog c, long seed) {
        // Equation so answers use catalog numeric correct (not true/false tokens).
        return new QuestionPicker(c, new Random(seed)).preferFormat(RenderFormat.EQUATION);
    }

    private static void testCorrect(Catalog c) {
        QuizSession s = new QuizSession("s1", "adb_force", 1000L, picker(c, 1L));
        PresentedQuestion q = s.getCurrent();
        QuizSession.AnswerResult r = s.answer(q.correctChoice, 2000L);
        Assert.eq(QuizSession.AnswerResult.CORRECT_UNLOCK, r, "correct unlock");
        Assert.eq(SessionOutcome.CORRECT, s.getOutcome(), "outcome correct");
        Assert.isTrue(s.isEnded(), "ended");
    }

    private static void testThreeWrong(Catalog c) {
        QuizSession s = new QuizSession("s2", "interval", 1000L, picker(c, 2L));
        for (int i = 0; i < 2; i++) {
            PresentedQuestion q = s.getCurrent();
            String wrong = wrongChoice(q);
            QuizSession.AnswerResult r = s.answer(wrong, 1000L + i);
            Assert.eq(QuizSession.AnswerResult.WRONG_CONTINUE, r, "wrong continue " + i);
            Assert.eq(SessionOutcome.IN_PROGRESS, s.getOutcome(), "still open");
        }
        PresentedQuestion q = s.getCurrent();
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
        String first = s.getCurrent().id();
        s.answer(wrongChoice(s.getCurrent()), 1100L);
        Assert.isTrue(!first.equals(s.getCurrent().id()) || c.size() == 1, "different after wrong");
    }

    private static void testSkipDoesNotRaiseMissStreak(Catalog c) {
        // Unforced formats so skip path uses nextAfterSkip.
        QuestionPicker free = new QuestionPicker(c, new Random(9L));
        QuizSession s = new QuizSession("s5", "play", 1000L, free);
        String first = s.getCurrent().id();
        Assert.eq(0, s.getMissStreak(), "start streak");
        PresentedQuestion next = s.skip();
        Assert.eq(0, s.getMissStreak(), "skip is not a miss");
        Assert.eq(SessionOutcome.IN_PROGRESS, s.getOutcome(), "still open after skip");
        Assert.isTrue(next != null, "next after skip");
        Assert.isTrue(!first.equals(next.id()) || c.size() == 1, "different id after skip");
        Assert.isTrue(s.getQuestionsShown().size() >= 2, "shown list grew");
    }

    /** Skips must not burn consoling budget — three wrongs still unlock. */
    private static void testSkipThenWrongsStillConsoling(Catalog c) {
        QuizSession s = new QuizSession("s6", "interval", 1000L, picker(c, 21L));
        s.skip();
        s.skip();
        Assert.eq(0, s.getMissStreak(), "skips leave streak at 0");
        for (int i = 0; i < 2; i++) {
            Assert.eq(QuizSession.AnswerResult.WRONG_CONTINUE,
                    s.answer(wrongChoice(s.getCurrent()), 2000L + i), "wrong " + i);
        }
        Assert.eq(QuizSession.AnswerResult.WRONG_CONSOLING,
                s.answer(wrongChoice(s.getCurrent()), 3000L), "consoling after 3 wrongs");
        Assert.eq(3, s.getMissStreak(), "streak counts wrongs only");
        Assert.eq(SessionOutcome.CONSOLING_UNLOCK, s.getOutcome(), "consoling");
    }

    private static void testMultipleSkipsStayOpen(Catalog c) {
        QuestionPicker free = new QuestionPicker(c, new Random(33L));
        QuizSession s = new QuizSession("s7", "play", 1000L, free);
        for (int i = 0; i < 5; i++) {
            String prev = s.getCurrent().id();
            PresentedQuestion n = s.skip();
            Assert.eq(SessionOutcome.IN_PROGRESS, s.getOutcome(), "open after skip " + i);
            Assert.eq(0, s.getMissStreak(), "streak still 0");
            Assert.isTrue(!prev.equals(n.id()) || c.size() == 1, "id change " + i);
        }
        Assert.isTrue(s.getQuestionsShown().size() >= 6, "all skips recorded in shown");
    }

    private static String wrongChoice(PresentedQuestion q) {
        if (q.isTrueFalse()) {
            return q.statementTrue ? PresentedQuestion.CHOICE_FALSE : PresentedQuestion.CHOICE_TRUE;
        }
        for (String ch : q.question.choices) {
            if (!q.isCorrect(ch)) return ch;
        }
        throw new IllegalStateException("no wrong");
    }
}
