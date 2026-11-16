package net.clevertree.quizgate.core;

import java.util.ArrayList;
import java.util.List;

public final class QuizSession {
    public static final int MAX_WRONG = 3;

    public enum AnswerResult {
        CORRECT_UNLOCK,
        WRONG_CONTINUE,
        WRONG_CONSOLING
    }

    private final String sessionId;
    private final String trigger;
    private final long startedAt;
    private final QuestionPicker picker;
    private Question current;
    private int missStreak;
    private final List<String> questionsShown = new ArrayList<>();
    private SessionOutcome outcome = SessionOutcome.IN_PROGRESS;
    private long endedAt;
    private boolean ended;

    public QuizSession(String sessionId, String trigger, long startedAt, QuestionPicker picker) {
        this.sessionId = sessionId;
        this.trigger = trigger;
        this.startedAt = startedAt;
        this.picker = picker;
        this.current = picker.first();
        questionsShown.add(current.id);
    }

    public String getSessionId() { return sessionId; }
    public String getTrigger() { return trigger; }
    public long getStartedAt() { return startedAt; }
    public Question getCurrent() { return current; }
    public int getMissStreak() { return missStreak; }
    public SessionOutcome getOutcome() { return outcome; }
    public boolean isEnded() { return ended; }
    public List<String> getQuestionsShown() { return questionsShown; }
    public long getEndedAt() { return endedAt; }
    public long durationMs() { return ended ? endedAt - startedAt : 0; }

    public AnswerResult answer(String choice, long now) {
        ensureOpen();
        if (current.isCorrect(choice)) {
            end(SessionOutcome.CORRECT, now);
            return AnswerResult.CORRECT_UNLOCK;
        }
        missStreak++;
        if (missStreak >= MAX_WRONG) {
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
