package net.clevertree.quizgate.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class Question {
    public final String id;
    public final String type;
    public final String spoken;
    public final String symbolic;
    public final List<String> choices;
    public final String correct;
    public final List<String> labels;

    public Question(String id, String type, String spoken, String symbolic,
                    List<String> choices, String correct, List<String> labels) {
        this.id = id;
        this.type = type;
        this.spoken = spoken;
        this.symbolic = symbolic == null ? "" : symbolic;
        this.choices = Collections.unmodifiableList(choices);
        this.correct = correct;
        this.labels = labels == null ? null : Collections.unmodifiableList(labels);
    }

    public boolean isCorrect(String choice) {
        if (correct.equals(choice)) return true;
        // Digit-pad / typed numeric answers: "07" vs "7", "10" vs "10"
        if ("math".equals(type) && choice != null) {
            try {
                return Integer.parseInt(choice) == Integer.parseInt(correct);
            } catch (NumberFormatException ignored) {}
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
        if (choices == null || choices.size() != 3) throw new IllegalStateException(id + " need 3 choices");
        if (correct == null || !choices.contains(correct)) {
            throw new IllegalStateException(id + " correct not in choices: " + correct + " " + choices);
        }
        if ("math".equals(type)) {
            if (symbolic == null || symbolic.isEmpty()) {
                throw new IllegalStateException(id + " math needs symbolic");
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
