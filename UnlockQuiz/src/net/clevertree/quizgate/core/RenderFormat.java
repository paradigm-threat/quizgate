package net.clevertree.quizgate.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * How a catalog question is shown at ask time.
 * Catalog stays one logical item; formats are chosen by the engine (with optional overrides later).
 */
public enum RenderFormat {
    /** Fill-in for the result: {@code 1 + 2 = _} (digit pad when preferred). */
    EQUATION("equation"),
    /** Full equation with True/False: {@code 1 + 2 = 4} (RHS may be wrong). */
    TRUE_FALSE("true_false"),
    /** Multiple answers shown; exactly one is correct (catalog {@code choices}). */
    CHOICE("choice");

    /** Planned (not implemented): {@code □ + 2 = 3} / {@code x + 2 = 3}. */
    // MISSING_ADDEND("missing_addend");

    public final String id;

    RenderFormat(String id) {
        this.id = id;
    }

    public static RenderFormat fromId(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        for (RenderFormat f : values()) {
            if (f.id.equalsIgnoreCase(raw.trim())) return f;
        }
        return null;
    }

    /**
     * Allowed ask-time formats. Per-item {@link Question#formats} overrides type defaults.
     * Math default: equation + true_false + choice.
     */
    public static List<RenderFormat> allowedFor(Question q) {
        if (q != null && q.formats != null && !q.formats.isEmpty()) {
            return q.formats;
        }
        if (q != null && "math".equals(q.type)) {
            return Arrays.asList(EQUATION, TRUE_FALSE, CHOICE);
        }
        return Collections.singletonList(EQUATION);
    }
}
