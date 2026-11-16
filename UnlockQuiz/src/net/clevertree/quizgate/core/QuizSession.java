package net.clevertree.quizgate.core;

import java.util.ArrayList;
import java.util.List;

public final class QuizSession {
    public static final int MAX_WRONG = 3;

    public enum AnswerResult {
        CORRECT_CONTINUE,
        CORRECT_ROUND_DONE,
        /** Single-question round unlock (same as CORRECT_ROUND_DONE when roundSize==1). */
        CORRECT_UNLOCK,
        WRONG_CONTINUE,
        WRONG_CONSOLING
    }

    private final String sessionId;
    private final String trigger;
    private final long startedAt;
    private final QuestionPicker picker;
    private final int roundSize;
    private final int maxWrong;
    private final boolean consolingUnlock;
    private Question current;
    private int missStreak;
    private int correctInRound;
    private final List<String> questionsShown = new ArrayList<>();
    private SessionOutcome outcome = SessionOutcome.IN_PROGRESS;
    private long endedAt;
    private boolean ended;

    public QuizSession(String sessionId, String trigger, long startedAt, QuestionPicker picker) {
        this(sessionId, trigger, startedAt, picker, 1, MAX_WRONG, true);
    }

    public QuizSession(String sessionId, String trigger, long startedAt, QuestionPicker picker,
                       int roundSize, int maxWrong, boolean consolingUnlock) {
        this.sessionId = sessionId;
        this.trigger = trigger;
        this.startedAt = startedAt;
        this.picker = picker;
        this.roundSize = Math.max(1, roundSize);
        this.maxWrong = Math.max(1, maxWrong);
        this.consolingUnlock = consolingUnlock;
        this.current = picker.first();
        questionsShown.add(current.id);
    }

    public String getSessionId() { return sessionId; }
    public String getTrigger() { return trigger; }
    public long getStartedAt() { return startedAt; }
    public Question getCurrent() { return current; }
    public int getMissStreak() { return missStreak; }
    public int getCorrectInRound() { return correctInRound; }
    public int getRoundSize() { return roundSize; }
    public SessionOutcome getOutcome() { return outcome; }
    public boolean isEnded() { return ended; }
    public List<String> getQuestionsShown() { return questionsShown; }
    public long getEndedAt() { return endedAt; }
    public long durationMs() { return ended ? endedAt - startedAt : 0; }

    public AnswerResult answer(String choice, long now) {
        ensureOpen();
        if (current.isCorrect(choice)) {
            correctInRound++;
            if (correctInRound >= roundSize) {
                end(SessionOutcome.CORRECT, now);
                return roundSize == 1 ? AnswerResult.CORRECT_UNLOCK : AnswerResult.CORRECT_ROUND_DONE;
            }
            String prev = current.id;
            current = picker.nextDifferent(prev);
            questionsShown.add(current.id);
            return AnswerResult.CORRECT_CONTINUE;
        }
        missStreak++;
        if (consolingUnlock && missStreak >= maxWrong) {
            end(SessionOutcome.CONSOLING_UNLOCK, now);
            return AnswerResult.WRONG_CONSOLING;
        }
        String prev = current.id;
        current = picker.nextDifferent(prev);
        questionsShown.add(current.id);
        return AnswerResult.WRONG_CONTINUE;
    }

    public void bypass(long now) {
        if (ended) return;
        end(SessionOutcome.BYPASS, now);
    }

    private void end(SessionOutcome o, long now) {
        outcome = o;
        endedAt = now;
        ended = true;
    }

    private void ensureOpen() {
        if (ended) throw new IllegalStateException("session already ended: " + outcome);
    }
}
