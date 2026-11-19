package net.clevertree.quizgate.core;

import java.util.Locale;
import java.util.Objects;

/**
 * Rapid-fire time-earn feature: kids answer questions in standalone mode to earn
 * extra minutes before the next scheduled lock. Speed-weighted rewards, a per-session
 * cap (default two hours, admin-tunable), and a visibility threshold tied to the
 * schedule so the feature only surfaces when less than the cap remains.
 *
 * Latency is recorded as one of three bands per attempt — not raw milliseconds —
 * so the daily stats payload stays small and privacy-safe.
 */
public final class TimeEarnPolicy {

    public static final int DEFAULT_CAP_MINUTES = 120; // two hours
    public static final int DEFAULT_FLOOR_MINUTES = 1;  // slow-but-correct still earns a token

    public static final int FAST_THRESHOLD_MS = 3_000;   // under 3s → fast
    public static final int THINKING_THRESHOLD_MS = 10_000; // 3–10s → thinking; over 10s or skip → slow

    public enum Band {
        FAST, THINKING, SLOW, SKIPPED
    }

    public enum Format {
        MULTIPLE_CHOICE, FILL_IN_BLANK, TRUE_FALSE, DIGIT, OTHER
    }

    private final int capMinutes;
    private final int floorMinutes;
    private int earnedMinutes;

    public TimeEarnPolicy() {
        this(DEFAULT_CAP_MINUTES, DEFAULT_FLOOR_MINUTES);
    }

    public TimeEarnPolicy(int capMinutes, int floorMinutes) {
        if (capMinutes <= 0) {
            throw new IllegalArgumentException("capMinutes must be positive, got " + capMinutes);
        }
        if (floorMinutes < 0) {
            throw new IllegalArgumentException("floorMinutes must be non-negative, got " + floorMinutes);
        }
        if (floorMinutes > capMinutes) {
            throw new IllegalArgumentException(
                    "floorMinutes (" + floorMinutes + ") cannot exceed capMinutes (" + capMinutes + ")");
        }
        this.capMinutes = capMinutes;
        this.floorMinutes = floorMinutes;
        this.earnedMinutes = 0;
    }

    public int getCapMinutes() { return capMinutes; }
    public int getFloorMinutes() { return floorMinutes; }
    public int getEarnedMinutes() { return earnedMinutes; }
    public int getRemainingCapMinutes() { return capMinutes - earnedMinutes; }

    /**
     * True when the feature UI should be shown: remaining schedule time is under the
     * cap, and there is still some time left to extend (zero/negative → hidden).
     */
    public static boolean isFeatureVisible(long remainingScheduleMs, int capMinutes) {
        if (capMinutes <= 0 || remainingScheduleMs <= 0) return false;
        long remainingMinutes = remainingScheduleMs / 60_000L;
        return remainingMinutes < capMinutes;
    }

    public static Band bandForLatencyMs(long latencyMs) {
        if (latencyMs < 0) {
            throw new IllegalArgumentException("latencyMs must be non-negative, got " + latencyMs);
        }
        if (latencyMs < FAST_THRESHOLD_MS) return Band.FAST;
        if (latencyMs <= THINKING_THRESHOLD_MS) return Band.THINKING;
        return Band.SLOW;
    }

    public static Band bandForSkip() {
        return Band.SKIPPED;
    }

    /**
     * Minutes earned for a correct answer in the given band, clamped so the session
     * never exceeds the cap. Slow-but-correct answers still earn the floor.
     */
    public int rewardFor(Band band) {
        Objects.requireNonNull(band, "band");
        if (band == Band.SKIPPED) return 0;
        int reward = switch (band) {
            case FAST -> 5;
            case THINKING -> 2;
            case SLOW -> floorMinutes;
            case SKIPPED -> 0;
        };
        return clampReward(reward);
    }

    private int clampReward(int reward) {
        int allowed = getRemainingCapMinutes();
        if (allowed <= 0) return 0;
        return Math.min(reward, allowed);
    }

    /** Attempt a correct answer; returns minutes actually granted (0 if cap exhausted). */
    public int recordCorrect(Band band) {
        int granted = rewardFor(band);
        earnedMinutes += granted;
        return granted;
    }

    public void recordSkip() {
        // Skips earn nothing; band is recorded in stats for correlation.
    }

    public boolean isCapExhausted() {
        return earnedMinutes >= capMinutes;
    }

    /** Stable string for the daily stats payload. */
    public static String bandKey(Band band) {
        return band.name().toLowerCase(Locale.ROOT);
    }

    public static String formatKey(Format format) {
        return format.name().toLowerCase(Locale.ROOT);
    }
}
