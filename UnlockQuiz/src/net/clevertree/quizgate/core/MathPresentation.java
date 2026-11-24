package net.clevertree.quizgate.core;

import java.util.Random;

/**
 * Ask-time presentation for math catalog items — no extra catalog rows.
 * True/false mutates the displayed RHS by a small integer offset when false.
 */
public final class MathPresentation {
    /** Absolute offset range for a false RHS (inclusive): 1..MAX_OFFSET. */
    public static final int MAX_FALSE_OFFSET = 3;

    public static final String TRUE_FALSE_SPOKEN = "Is this correct?";

    private MathPresentation() {}

    public static PresentedQuestion present(Question q, RenderFormat format, Random random) {
        if (q == null) throw new IllegalArgumentException("question");
        if (format == null) format = RenderFormat.EQUATION;
        boolean multiFormat = "math".equals(q.type) || "access".equals(q.type);
        if (format == RenderFormat.TRUE_FALSE && multiFormat) {
            return trueFalse(q, random);
        }
        if (format == RenderFormat.CHOICE && multiFormat) {
            return choice(q);
        }
        return equation(q);
    }

    public static PresentedQuestion equation(Question q) {
        return new PresentedQuestion(
                q,
                RenderFormat.EQUATION,
                q.symbolic,
                q.spoken,
                q.correct,
                true);
    }

    /** Same prompt as equation; UI always shows catalog multi-answer choices. */
    public static PresentedQuestion choice(Question q) {
        return new PresentedQuestion(
                q,
                RenderFormat.CHOICE,
                q.symbolic,
                q.spoken,
                q.correct,
                true);
    }

    public static PresentedQuestion trueFalse(Question q, Random random) {
        if (random == null) throw new IllegalArgumentException("random");
        return trueFalse(q, random.nextBoolean(), random);
    }

    /** Test / forced ask-time path: {@code statementTrue} chooses correct vs mutated RHS. */
    public static PresentedQuestion trueFalse(Question q, boolean statementTrue, Random random) {
        if (random == null) throw new IllegalArgumentException("random");
        String shownToken;
        if (AccessPad.isLetterToken(q.correct)) {
            shownToken = statementTrue
                    ? String.valueOf(AccessPad.normalizeLetter(q.correct))
                    : AccessPad.mutateLetter(q.correct, MAX_FALSE_OFFSET, random);
        } else {
            int correct = parseIntSafe(q.correct);
            int shown = statementTrue ? correct : mutateRhs(correct, random);
            shownToken = String.valueOf(shown);
        }
        String display = fillBlank(q.symbolic, shownToken);
        String choice = statementTrue ? PresentedQuestion.CHOICE_TRUE : PresentedQuestion.CHOICE_FALSE;
        return new PresentedQuestion(
                q,
                RenderFormat.TRUE_FALSE,
                display,
                TRUE_FALSE_SPOKEN,
                choice,
                statementTrue);
    }

    /**
     * Wrong RHS: small nonzero offset (±1..±{@link #MAX_FALSE_OFFSET}), keeping the
     * displayed value non-negative for early-grade packs.
     */
    public static int mutateRhs(int correct, Random random) {
        int offset = 1 + random.nextInt(MAX_FALSE_OFFSET);
        boolean negative = random.nextBoolean();
        int shown = negative ? correct - offset : correct + offset;
        if (shown < 0) {
            shown = correct + offset;
        }
        if (shown == correct) {
            shown = correct + 1;
        }
        return shown;
    }

    static String fillBlank(String symbolic, String value) {
        if (symbolic == null || symbolic.isEmpty()) {
            return value;
        }
        if (symbolic.contains("_")) {
            return symbolic.replace("_", value);
        }
        // Already a full equation — replace trailing result if present, else append.
        int eq = symbolic.lastIndexOf('=');
        if (eq >= 0) {
            return symbolic.substring(0, eq + 1) + " " + value;
        }
        return symbolic + " = " + value;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("math correct must be int: " + s);
        }
    }
}
