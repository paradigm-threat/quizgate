package net.clevertree.quizgate.core;

/**
 * Soft gates for advancing a curriculum <em>level</em> (not pack id).
 * Repo defaults; admin may override via profile settings. SI should adhere to
 * these thresholds but may withhold or advance with a documented exception.
 *
 * <ul>
 *   <li>{@link #DEFAULT_MIN_ACCURACY_PCT} — right / (right+wrong)</li>
 *   <li>{@link #DEFAULT_MIN_CORRECT} — absolute floor of correct answers</li>
 *   <li>{@link #DEFAULT_MAX_SKIP_PCT} — skipped / asked (too many skips → not ready)</li>
 * </ul>
 */
public final class LevelUpPolicy {
    public static final int DEFAULT_MIN_ACCURACY_PCT = 75;
    public static final int DEFAULT_MIN_CORRECT = 5;
    public static final int DEFAULT_MAX_SKIP_PCT = 25;

    public static final int MIN_ACCURACY_PCT = 1;
    public static final int MAX_ACCURACY_PCT = 100;
    public static final int MIN_CORRECT = 1;
    public static final int MAX_CORRECT = 10_000;
    public static final int MIN_SKIP_PCT = 0;
    public static final int MAX_SKIP_PCT = 100;

    private LevelUpPolicy() {}

    public static int parseMinAccuracyPct(int raw) {
        if (raw < MIN_ACCURACY_PCT || raw > MAX_ACCURACY_PCT) return DEFAULT_MIN_ACCURACY_PCT;
        return raw;
    }

    public static int parseMinCorrect(int raw) {
        if (raw < MIN_CORRECT || raw > MAX_CORRECT) return DEFAULT_MIN_CORRECT;
        return raw;
    }

    public static int parseMaxSkipPct(int raw) {
        if (raw < MIN_SKIP_PCT || raw > MAX_SKIP_PCT) return DEFAULT_MAX_SKIP_PCT;
        return raw;
    }

    /**
     * @param asked   day (or window) questions shown
     * @param right   correct answers
     * @param wrong   incorrect answers
     * @param skipped skips in the same window
     */
    public static Eligibility evaluate(
            int asked, int right, int wrong, int skipped,
            int minAccuracyPct, int minCorrect, int maxSkipPct) {
        int minAcc = parseMinAccuracyPct(minAccuracyPct);
        int minR = parseMinCorrect(minCorrect);
        int maxSkip = parseMaxSkipPct(maxSkipPct);
        int a = Math.max(0, asked);
        int r = Math.max(0, right);
        int w = Math.max(0, wrong);
        int s = Math.max(0, skipped);
        int answered = r + w;
        int accuracyPct = answered > 0 ? (int) Math.round(100.0 * r / (double) answered) : 0;
        int skipPct = a > 0 ? (int) Math.round(100.0 * s / (double) a) : 0;

        boolean enoughCorrect = r >= minR;
        boolean accurateEnough = answered > 0 && accuracyPct >= minAcc;
        boolean skipsOk = a > 0 && skipPct <= maxSkip;
        boolean eligible = enoughCorrect && accurateEnough && skipsOk;

        return new Eligibility(eligible, enoughCorrect, accurateEnough, skipsOk,
                a, r, w, s, accuracyPct, skipPct, minAcc, minR, maxSkip);
    }

    public static final class Eligibility {
        public final boolean eligible;
        public final boolean enoughCorrect;
        public final boolean accurateEnough;
        public final boolean skipsOk;
        public final int asked;
        public final int right;
        public final int wrong;
        public final int skipped;
        public final int accuracyPct;
        public final int skipPct;
        public final int minAccuracyPct;
        public final int minCorrect;
        public final int maxSkipPct;

        public Eligibility(boolean eligible, boolean enoughCorrect, boolean accurateEnough,
                           boolean skipsOk, int asked, int right, int wrong, int skipped,
                           int accuracyPct, int skipPct, int minAccuracyPct, int minCorrect,
                           int maxSkipPct) {
            this.eligible = eligible;
            this.enoughCorrect = enoughCorrect;
            this.accurateEnough = accurateEnough;
            this.skipsOk = skipsOk;
            this.asked = asked;
            this.right = right;
            this.wrong = wrong;
            this.skipped = skipped;
            this.accuracyPct = accuracyPct;
            this.skipPct = skipPct;
            this.minAccuracyPct = minAccuracyPct;
            this.minCorrect = minCorrect;
            this.maxSkipPct = maxSkipPct;
        }
    }
}
