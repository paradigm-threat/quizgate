import net.clevertree.quizgate.core.JarSchedulePolicy;
import net.clevertree.quizgate.core.PostUnlockPlayOfferPolicy;

/** Once-per-day post-unlock play offer + jar extend helpers. */
public class PostUnlockPlayOfferTest {
    public static void main(String[] args) {
        testProbabilityConstant();
        testOfferLottery();
        testOncePerDay();
        testRollGuards();
        testJarBonusMs();
        testExtendNextAlarm();
        testMinutesFromBonus();
        System.out.println("PostUnlockPlayOfferTest OK");
    }

    private static void testProbabilityConstant() {
        Assert.eq(0.40d, PostUnlockPlayOfferPolicy.OFFER_PROBABILITY, 1e-9, "40% offer");
    }

    private static void testOfferLottery() {
        Assert.isTrue(PostUnlockPlayOfferPolicy.shouldOffer("2026-11-19", "", 0.0),
                "roll 0 offers");
        Assert.isTrue(PostUnlockPlayOfferPolicy.shouldOffer("2026-11-19", "", 0.399),
                "roll under 0.4 offers");
        Assert.isTrue(!PostUnlockPlayOfferPolicy.shouldOffer("2026-11-19", "", 0.40),
                "roll 0.4 does not offer");
        Assert.isTrue(!PostUnlockPlayOfferPolicy.shouldOffer("2026-11-19", "", 0.99),
                "high roll skips");
    }

    private static void testOncePerDay() {
        Assert.isTrue(!PostUnlockPlayOfferPolicy.shouldOffer("2026-11-19", "2026-11-19", 0.0),
                "already shown today → never");
        Assert.isTrue(PostUnlockPlayOfferPolicy.shouldOffer("2026-11-20", "2026-11-19", 0.1),
                "new day can offer again");
        Assert.isTrue(!PostUnlockPlayOfferPolicy.shouldOffer("", "", 0.1),
                "empty today rejected");
        Assert.isTrue(!PostUnlockPlayOfferPolicy.shouldOffer(null, null, 0.1),
                "null today rejected");
    }

    private static void testRollGuards() {
        Assert.isTrue(!PostUnlockPlayOfferPolicy.shouldOffer("2026-11-19", "", -0.01),
                "negative roll");
        Assert.isTrue(!PostUnlockPlayOfferPolicy.shouldOffer("2026-11-19", "", 1.0),
                "roll 1.0");
        Assert.isTrue(!PostUnlockPlayOfferPolicy.shouldOffer("2026-11-19", "", Double.NaN),
                "NaN roll");
    }

    private static void testJarBonusMs() {
        Assert.eq(0L, JarSchedulePolicy.bonusMsFromMinutes(0), "empty jar");
        Assert.eq(5L * 60_000L, JarSchedulePolicy.bonusMsFromMinutes(5), "5 min");
        Assert.eq(JarSchedulePolicy.JAR_BONUS_CAP_MS,
                JarSchedulePolicy.bonusMsFromMinutes(9999), "cap 3h");
    }

    private static void testExtendNextAlarm() {
        long now = 1_000_000L;
        long next = now + 40L * 60_000L;
        Assert.eq(next + 5L * 60_000L,
                JarSchedulePolicy.extendNextAlarm(next, now, 5L * 60_000L),
                "extend future");
        Assert.eq(0L, JarSchedulePolicy.extendNextAlarm(now - 1, now, 60_000L),
                "past alarm cannot extend");
        Assert.eq(0L, JarSchedulePolicy.extendNextAlarm(0L, now, 60_000L),
                "no alarm");
        Assert.eq(next, JarSchedulePolicy.extendNextAlarm(next, now, 0L),
                "zero bonus leaves next");
    }

    private static void testMinutesFromBonus() {
        Assert.eq(0, JarSchedulePolicy.minutesFromBonusMs(0), "0");
        Assert.eq(5, JarSchedulePolicy.minutesFromBonusMs(5L * 60_000L), "5");
        Assert.eq(0, JarSchedulePolicy.minutesFromBonusMs(59_999L), "floor");
    }
}
