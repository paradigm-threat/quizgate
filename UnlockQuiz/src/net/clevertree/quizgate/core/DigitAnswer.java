package net.clevertree.quizgate.core;

/** Builds a numeric answer from digit-pad presses (max 2 digits for sums ≤ 10). */
public final class DigitAnswer {
    public static final int MAX_LEN = 2;
    private final StringBuilder buf = new StringBuilder();

    public void press(int digit) {
        if (digit < 0 || digit > 9) return;
        if (buf.length() >= MAX_LEN) return;
        buf.append(digit);
    }

    public void clear() {
        buf.setLength(0);
    }

    public void backspace() {
        if (buf.length() > 0) buf.deleteCharAt(buf.length() - 1);
    }

    public String value() {
        return buf.toString();
    }

    public boolean isEmpty() {
        return buf.length() == 0;
    }

    public boolean matches(String correct) {
        if (isEmpty()) return false;
        // normalize leading zeros: "07" vs "7"
        try {
            return Integer.parseInt(buf.toString()) == Integer.parseInt(correct);
        } catch (NumberFormatException e) {
            return buf.toString().equals(correct);
        }
    }
}
