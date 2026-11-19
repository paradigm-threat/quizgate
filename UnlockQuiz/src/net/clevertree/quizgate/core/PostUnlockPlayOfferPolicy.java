package net.clevertree.quizgate.core;

/**
 * Once-per-local-day lottery for showing play-choice chrome (Quit / Earn /
 * Ask another) after a Hard lock unlock — without changing when unlock
 * teardown (grace + reschedule) runs.
 *
 * <p>Each Hard success may roll until the offer has been shown once that day.
 * Probability is intentionally high enough that morning/first-use often sees it.
 */
public final class PostUnlockPlayOfferPolicy {
    /** Chance to show the continue/quit chrome on an eligible Hard unlock. */
    public static final double OFFER_PROBABILITY = 0.40d;

    private PostUnlockPlayOfferPolicy() {}

    /**
     * @param todayKey     local calendar day ({@link StatsStore#dayKey})
     * @param lastOfferDay day key when the offer was last shown (empty if never)
     * @param roll         uniform random in {@code [0, 1)} from the caller
     * @return true when this unlock should show play chrome instead of auto-exit
     */
    public static boolean shouldOffer(String todayKey, String lastOfferDay, double roll) {
        String today = StatsStore.canonicalDayKey(todayKey == null ? "" : todayKey);
        if (today.isEmpty()) return false;
        String last = StatsStore.canonicalDayKey(lastOfferDay == null ? "" : lastOfferDay);
        if (!last.isEmpty() && today.equals(last)) return false;
        if (Double.isNaN(roll) || roll < 0.0d || roll >= 1.0d) return false;
        return roll < OFFER_PROBABILITY;
    }
}
