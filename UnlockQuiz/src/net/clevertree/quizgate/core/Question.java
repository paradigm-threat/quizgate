package net.clevertree.quizgate.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class Question {
    public static final int DEFAULT_WEIGHT = 1;

    public final String id;
    public final String type;
    public final String spoken;
    public final String symbolic;
    public final List<String> choices;
    public final String correct;
    public final List<String> labels;
    /** Relative pick weight (≥1). Lower = rarer as the pool grows. */
    public final int weight;
    /**
     * Optional ask-time format override ({@code equation}/{@code true_false}/{@code choice}).
     * {@code null} → type defaults in {@link RenderFormat#allowedFor(Question)}.
     */
    public final List<RenderFormat> formats;

    public Question(String id, String type, String spoken, String symbolic,
                    List<String> choices, String correct, List<String> labels) {
        this(id, type, spoken, symbolic, choices, correct, labels, DEFAULT_WEIGHT, null);
    }

    public Question(String id, String type, String spoken, String symbolic,
                    List<String> choices, String correct, List<String> labels, int weight) {
        this(id, type, spoken, symbolic, choices, correct, labels, weight, null);
    }

    public Question(String id, String type, String spoken, String symbolic,
                    List<String> choices, String correct, List<String> labels, int weight,
                    List<RenderFormat> formats) {
        this.id = id;
        this.type = type;
        this.spoken = spoken;
        this.symbolic = symbolic == null ? "" : symbolic;
        this.choices = Collections.unmodifiableList(choices);
        this.correct = correct;
        this.labels = labels == null ? null : Collections.unmodifiableList(labels);
        this.weight = Math.max(1, weight);
        if (formats == null || formats.isEmpty()) {
            this.formats = null;
        } else {
            this.formats = Collections.unmodifiableList(new ArrayList<>(formats));
        }
    }

    public boolean isCorrect(String choice) {
        if (correct.equals(choice)) return true;
        if (choice == null) return false;
        // Digit-pad / typed numeric answers: "07" vs "7", "10" vs "10"
        if (("math".equals(type) || "access".equals(type))) {
            try {
                return Integer.parseInt(choice) == Integer.parseInt(correct);
            } catch (NumberFormatException ignored) {}
            if ("access".equals(type) && choice.length() == 1 && correct.length() == 1) {
                return Character.toUpperCase(choice.charAt(0))
                        == Character.toUpperCase(correct.charAt(0));
            }
        }
        return false;
    }

    public String displayLabel(int index) {
        if (labels != null && index >= 0 && index < labels.size()) {
            return labels.get(index);
        }
        return choices.get(index);
    }

    public void validate() {
        if (id == null || id.isEmpty()) throw new IllegalStateException("empty id");
        if (spoken == null || spoken.isEmpty()) throw new IllegalStateException(id + " empty spoken");
        if (choices == null || (choices.size() != 3 && choices.size() != 4)) {
            throw new IllegalStateException(id + " need 3 or 4 choices");
        }
        if (correct == null || !choices.contains(correct)) {
            throw new IllegalStateException(id + " correct not in choices: " + correct + " " + choices);
        }
        if (weight < 1) throw new IllegalStateException(id + " weight must be ≥1");
        if ("math".equals(type) || "access".equals(type)) {
            if (symbolic == null || symbolic.isEmpty()) {
                throw new IllegalStateException(id + " " + type + " needs symbolic");
            }
        }
        if (labels != null && labels.size() != choices.size()) {
            throw new IllegalStateException(id + " labels size mismatch");
        }
    }

    @Override public String toString() {
        return id + ":" + spoken + Arrays.toString(choices.toArray());
    }
}
