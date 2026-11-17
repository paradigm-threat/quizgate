package net.clevertree.quizgate.core;

/**
 * Catalog {@link Question} plus ask-time render choice (equation vs true/false, …).
 * Does not duplicate catalog rows — one logical item, many presentations.
 */
public final class PresentedQuestion {
    public static final String CHOICE_TRUE = "true";
    public static final String CHOICE_FALSE = "false";

    public final Question question;
    public final RenderFormat format;
    /** Symbolic text shown to the child (blank filled for equation; full RHS for T/F). */
    public final String displaySymbolic;
    /** Spoken / prompt line (may differ from catalog for true/false). */
    public final String spoken;
    /**
     * Answer token for this presentation: catalog {@code correct} for equation,
     * or {@link #CHOICE_TRUE}/{@link #CHOICE_FALSE} for true/false.
     */
    public final String correctChoice;
    /** For true/false: whether the displayed equation is mathematically correct. */
    public final boolean statementTrue;

    public PresentedQuestion(Question question, RenderFormat format, String displaySymbolic,
                             String spoken, String correctChoice, boolean statementTrue) {
        this.question = question;
        this.format = format == null ? RenderFormat.EQUATION : format;
        this.displaySymbolic = displaySymbolic == null ? "" : displaySymbolic;
        this.spoken = spoken == null ? "" : spoken;
        this.correctChoice = correctChoice;
        this.statementTrue = statementTrue;
    }

    public String id() {
        return question.id;
    }

    public String type() {
        return question.type;
    }

    public boolean isTrueFalse() {
        return format == RenderFormat.TRUE_FALSE;
    }

    public boolean isCorrect(String choice) {
        if (isTrueFalse()) {
            if (choice == null) return false;
            String c = choice.trim().toLowerCase();
            return correctChoice.equals(c);
        }
        return question.isCorrect(choice);
    }
}
