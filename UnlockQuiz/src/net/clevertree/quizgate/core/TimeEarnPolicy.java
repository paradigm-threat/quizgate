package net.clevertree.quizgate.core;

import java.util.Locale;
import java.util.Objects;

/**
 * Rapid-fire time-earn (standalone / practice): answer quickly to add minutes to the
 * jar before the next scheduled lock. Speed-weighted rewards, per-session cap
 * (default two hours, admin-tunable), visibility tied to remaining schedule time.
 *
 * Latency is stored only as a {@link Band} — never raw milliseconds — so day-batch /
 * stats payloads stay small and privacy-safe. Format keys match {@link RenderFormat#id}.
 */
public final class TimeEarnPolicy {

    public static final int DEFAULT_CAP_MINUTES = 120;
    public static final int MIN_CAP_MINUTES = 1;
    /** Align with minutes-jar bonus ceiling in AppPrefs (3h). */
    public static final int MAX_CAP_MINUTES = 180;
    public static final int DEFAULT_FLOOR_MINUTES = 1;

    public static final int FAST_THRESHOLD_MS = 3_000;
    public static final int THINKING_THRESHOLD_MS = 10_000;

    public static final int REWARD_FAST_MINUTES = 5;
    public static final int REWARD_THINKING_MINUTES = 2;

    public enum Band {
        FAST, THINKING, SLOW, SKIPPED
    }

    /**
     * Stats / payload format keys — ids align with {@link RenderFormat} where possible.
     * {@code digit} is reserved for a future forced digit-pad mode; {@code other} is fallback.
     */
    public enum Format {
        EQUATION("equation"),
        TRUE_FALSE("true_false"),
        CHOICE("choice"),
        DIGIT("digit"),
        OTHER("other");

        public final String id;

        Format(String id) {
            this.id = id;
        }
    }

    private final int capMinutes;
    private final int floorMinutes;
    private int earnedMinutes;
    private int correctCount;
    private int skipCount;
    private int fastCount;
    private int thinkingCount;
    private int slowCount;

    public TimeEarnPolicy() {
        this(DEFAULT_CAP_MINUTES, DEFAULT_FLOOR_MINUTES);
    }

    public TimeEarnPolicy(int capMinutes, int floorMinutes) {
        this.capMinutes = clampCapMinutes(capMinutes);
        if (floorMinutes < 0) {
            throw new IllegalArgumentException("floorMinutes must be non-negative, got " + floorMinutes);
        }
        if (floorMinutes > this.capMinutes) {
            throw new IllegalArgumentException(
                    "floorMinutes (" + floorMinutes + ") cannot exceed capMinutes (" + this.capMinutes + ")");
        }
        this.floorMinutes = floorMinutes;
        this.earnedMinutes = 0;
    }

    /** Clamp / sanitize admin or prefs values into a usable session cap. */
    public static int clampCapMinutes(int raw) {
        if (raw < MIN_CAP_MINUTES) {
            throw new IllegalArgumentException("capMinutes must be >= " + MIN_CAP_MINUTES + ", got " + raw);
        }
        return Math.min(raw, MAX_CAP_MINUTES);
    }

    /**
     * Parse admin / JSON numeric into a clamped cap. Returns {@link #DEFAULT_CAP_MINUTES}
     * when null/blank/unparseable.
     */
    public static int parseCapMinutes(Object raw) {
        if (raw == null) return DEFAULT_CAP_MINUTES;
        try {
            int v;
            if (raw instanceof Number) {
                v = ((Number) raw).intValue();
            } else {
                String s = String.valueOf(raw).trim();
                if (s.isEmpty()) return DEFAULT_CAP_MINUTES;
                v = Integer.parseInt(s);
            }
            if (v < MIN_CAP_MINUTES) return DEFAULT_CAP_MINUTES;
            return Math.min(v, MAX_CAP_MINUTES);
        } catch (NumberFormatException e) {
            return DEFAULT_CAP_MINUTES;
        }
    }

    public int getCapMinutes() { return capMinutes; }
    public int getFloorMinutes() { return floorMinutes; }
    public int getEarnedMinutes() { return earnedMinutes; }
    public int getRemainingCapMinutes() { return Math.max(0, capMinutes - earnedMinutes); }
    public int getCorrectCount() { return correctCount; }
    public int getSkipCount() { return skipCount; }
    public int getFastCount() { return fastCount; }
    public int getThinkingCount() { return thinkingCount; }
    public int getSlowCount() { return slowCount; }
    public int getAttemptCount() { return correctCount + skipCount; }

    public void resetSession() {
        earnedMinutes = 0;
        correctCount = 0;
        skipCount = 0;
        fastCount = 0;
        thinkingCount = 0;
        slowCount = 0;
    }

    /**
     * Feature UI when there is still schedule time left and that remaining time is
     * strictly under the session cap (so earning can matter before the next lock).
     */
    public static boolean isFeatureVisible(long remainingScheduleMs, int capMinutes) {
        int cap;
        try {
            cap = clampCapMinutes(capMinutes);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (remainingScheduleMs <= 0L) return false;
        long remainingMinutes = remainingScheduleMs / 60_000L;
        return remainingMinutes < cap;
    }

    /** Convenience: visibility from alarm wall-clock vs now. */
    public static boolean isFeatureVisibleAt(long nextAlarmAtMs, long nowMs, int capMinutes) {
        if (nextAlarmAtMs <= 0L) return false;
        return isFeatureVisible(nextAlarmAtMs - nowMs, capMinutes);
    }

    public static Band bandForLatencyMs(long latencyMs) {
        if (latencyMs < 0L) {
            throw new IllegalArgumentException("latencyMs must be non-negative, got " + latencyMs);
        }
        if (latencyMs < FAST_THRESHOLD_MS) return Band.FAST;
        if (latencyMs <= THINKING_THRESHOLD_MS) return Band.THINKING;
        return Band.SLOW;
    }

    public static Band bandForSkip() {
        return Band.SKIPPED;
    }

    /** Map ask-time UI format onto stats keys. */
    public static Format formatFromRender(RenderFormat render) {
        if (render == null) return Format.OTHER;
        switch (render) {
            case EQUATION:
                return Format.EQUATION;
            case TRUE_FALSE:
                return Format.TRUE_FALSE;
            case CHOICE:
                return Format.CHOICE;
            default:
                return Format.OTHER;
        }
    }

    public static Format formatFromId(String raw) {
        if (raw == null || raw.trim().isEmpty()) return Format.OTHER;
        String id = raw.trim().toLowerCase(Locale.ROOT);
        for (Format f : Format.values()) {
            if (f.id.equals(id)) return f;
        }
        // Legacy aliases from the first draft of this feature.
        if ("multiple_choice".equals(id)) return Format.CHOICE;
        if ("fill_in_blank".equals(id) || "fill-in-blank".equals(id)) return Format.EQUATION;
        return Format.OTHER;
    }

    /**
     * Minutes that would be granted for a correct answer in {@code band}, clamped to
     * remaining session cap. Skips always 0.
     */
    public int rewardFor(Band band) {
        Objects.requireNonNull(band, "band");
        if (band == Band.SKIPPED) return 0;
        int reward;
        switch (band) {
            case FAST:
                reward = REWARD_FAST_MINUTES;
                break;
            case THINKING:
                reward = REWARD_THINKING_MINUTES;
                break;
            case SLOW:
                reward = floorMinutes;
                break;
            default:
                reward = 0;
                break;
        }
        return clampReward(reward);
    }

    private int clampReward(int reward) {
        if (reward <= 0) return 0;
        int allowed = getRemainingCapMinutes();
        if (allowed <= 0) return 0;
        return Math.min(reward, allowed);
    }

    /** Correct answer; returns minutes granted (0 if cap exhausted or skip band). */
    public int recordCorrect(Band band) {
        Objects.requireNonNull(band, "band");
        if (band == Band.SKIPPED) {
            recordSkip();
            return 0;
        }
        int granted = rewardFor(band);
        earnedMinutes += granted;
        correctCount++;
        switch (band) {
            case FAST:
                fastCount++;
                break;
            case THINKING:
                thinkingCount++;
                break;
            case SLOW:
                slowCount++;
                break;
            default:
                break;
        }
        return granted;
    }

    /** Correct answer scored from latency ms. */
    public int recordCorrectLatencyMs(long latencyMs) {
        return recordCorrect(bandForLatencyMs(latencyMs));
    }

    public void recordSkip() {
        skipCount++;
    }

    public boolean isCapExhausted() {
        return earnedMinutes >= capMinutes;
    }

    /** Minutes → ms for the existing minutes jar / schedule bonus path. */
    public static long minutesToMs(int minutes) {
        if (minutes <= 0) return 0L;
        return minutes * 60_000L;
    }

    public static String bandKey(Band band) {
        Objects.requireNonNull(band, "band");
        return band.name().toLowerCase(Locale.ROOT);
    }

    public static String formatKey(Format format) {
        Objects.requireNonNull(format, "format");
        return format.id;
    }

    /**
     * Privacy-safe attempt line fields for stats / future day-batch enrichment
     * (no raw latency).
     */
    public static String attemptNote(Band band, Format format) {
        return "band=" + bandKey(band) + ";format=" + formatKey(format);
    }
}
