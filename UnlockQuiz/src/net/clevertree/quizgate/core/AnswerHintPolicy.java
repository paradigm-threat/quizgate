package net.clevertree.quizgate.core;

/**
 * Progressive idle hints for multiple-choice asks. Repo defaults; tip/admin may
 * override. Soft pulse → strong reveal → optional one-shot count-blocks.
 *
 * Gate: {@link #MODE_UNTIL_FIRST_CORRECT} only while today's right count is 0.
 */
public final class AnswerHintPolicy {
    public static final String MODE_OFF = "off";
    public static final String MODE_UNTIL_FIRST_CORRECT = "until_first_correct";
    public static final String MODE_ALWAYS = "always";

    public static final int DEFAULT_DELAY_MS = 3_000;
    public static final int DEFAULT_PULSE_MS = 500;
    public static final int DEFAULT_PERIOD_MS = 3_000;
    public static final int DEFAULT_STRONG_AFTER_MS = 12_000;
    public static final int DEFAULT_BLOCKS_AFTER_MS = 18_000;
    public static final boolean DEFAULT_BLOCKS_ENABLED = true;

    public static final int MIN_MS = 0;
    public static final int MAX_MS = 120_000;

    private AnswerHintPolicy() {}

    public static String normalizeMode(String raw) {
        if (raw == null) return MODE_UNTIL_FIRST_CORRECT;
        String s = raw.trim().toLowerCase();
        if (MODE_OFF.equals(s) || MODE_ALWAYS.equals(s) || MODE_UNTIL_FIRST_CORRECT.equals(s)) {
            return s;
        }
        return MODE_UNTIL_FIRST_CORRECT;
    }

    public static int clampMs(int raw, int def) {
        if (raw < MIN_MS || raw > MAX_MS) return def;
        return raw;
    }

    /**
     * @param todayRight today's correct count (stats day)
     * @param richMotion false on Slim / reduced motion — soft pulse only (at most)
     */
    public static Config resolve(String mode, int todayRight, boolean richMotion,
                                 int delayMs, int pulseMs, int periodMs,
                                 int strongAfterMs, int blocksAfterMs, boolean blocksEnabled) {
        String m = normalizeMode(mode);
        boolean gatedOff = MODE_OFF.equals(m)
                || (MODE_UNTIL_FIRST_CORRECT.equals(m) && todayRight > 0);
        if (gatedOff) {
            return Config.disabled();
        }
        int delay = clampMs(delayMs, DEFAULT_DELAY_MS);
        int pulse = clampMs(pulseMs, DEFAULT_PULSE_MS);
        if (pulse < 100) pulse = DEFAULT_PULSE_MS;
        int period = clampMs(periodMs, DEFAULT_PERIOD_MS);
        if (period < 500) period = DEFAULT_PERIOD_MS;
        int strong = clampMs(strongAfterMs, DEFAULT_STRONG_AFTER_MS);
        int blocks = clampMs(blocksAfterMs, DEFAULT_BLOCKS_AFTER_MS);
        boolean blocksOn = blocksEnabled && richMotion && blocks > 0;
        boolean strongOn = richMotion && strong > 0;
        // Slim: at most one soft pulse cycle (delay then pulse, no repeat / strong / blocks).
        boolean softRepeat = richMotion;
        return new Config(true, softRepeat, strongOn, blocksOn, delay, pulse, period, strong, blocks);
    }

    public static final class Config {
        public final boolean enabled;
        public final boolean softRepeat;
        public final boolean strongEnabled;
        public final boolean blocksEnabled;
        public final int delayMs;
        public final int pulseMs;
        public final int periodMs;
        public final int strongAfterMs;
        public final int blocksAfterMs;

        public Config(boolean enabled, boolean softRepeat, boolean strongEnabled,
                      boolean blocksEnabled, int delayMs, int pulseMs, int periodMs,
                      int strongAfterMs, int blocksAfterMs) {
            this.enabled = enabled;
            this.softRepeat = softRepeat;
            this.strongEnabled = strongEnabled;
            this.blocksEnabled = blocksEnabled;
            this.delayMs = delayMs;
            this.pulseMs = pulseMs;
            this.periodMs = periodMs;
            this.strongAfterMs = strongAfterMs;
            this.blocksAfterMs = blocksAfterMs;
        }

        public static Config disabled() {
            return new Config(false, false, false, false, 0, 0, 0, 0, 0);
        }
    }

    /** Parse {@code a + b = _} style math symbolics for count-blocks. */
    public static int[] parseAddends(String symbolic) {
        if (symbolic == null) return null;
        String s = symbolic.trim();
        // "5 + 2 = _" or "5 + 2 = 7"
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^(\\d+)\\s*\\+\\s*(\\d+)\\s*=")
                .matcher(s);
        if (!m.find()) return null;
        try {
            int a = Integer.parseInt(m.group(1));
            int b = Integer.parseInt(m.group(2));
            if (a < 0 || b < 0 || a > 12 || b > 12) return null;
            return new int[] { a, b };
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
