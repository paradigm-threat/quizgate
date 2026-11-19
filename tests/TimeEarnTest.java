import net.clevertree.quizgate.core.RenderFormat;
import net.clevertree.quizgate.core.TimeEarnPolicy;
import net.clevertree.quizgate.core.TimeEarnPolicy.Band;
import net.clevertree.quizgate.core.TimeEarnPolicy.Format;

/** Regression for rapid-fire time-earn policy (pure JVM). */
public class TimeEarnTest {
    public static void main(String[] args) {
        testDefaultsAndClamp();
        testParseCapMinutes();
        testBandThresholds();
        testNegativeLatencyRejected();
        testRewardWeightsAndFloor();
        testFloorClampingToCap();
        testCapExhaustionStopsRewards();
        testCtorGuards();
        testFeatureVisibility();
        testVisibilityFromAlarmWallClock();
        testSkipAndCounts();
        testFormatMapping();
        testBandAndFormatKeys();
        testAccumulateAndReset();
        testRapidFireSequence();
        testMinutesToMsAndAttemptNote();
        System.out.println("TimeEarnTest OK");
    }

    private static void testDefaultsAndClamp() {
        TimeEarnPolicy p = new TimeEarnPolicy();
        Assert.eq(120, p.getCapMinutes(), "default cap");
        Assert.eq(1, p.getFloorMinutes(), "default floor");
        Assert.eq(0, p.getEarnedMinutes(), "starts zero");
        Assert.eq(180, TimeEarnPolicy.clampCapMinutes(999), "clamp high");
        Assert.eq(1, TimeEarnPolicy.clampCapMinutes(1), "clamp min");
        try {
            TimeEarnPolicy.clampCapMinutes(0);
            throw new AssertionError("cap 0 should throw");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    private static void testParseCapMinutes() {
        Assert.eq(120, TimeEarnPolicy.parseCapMinutes(null), "null → default");
        Assert.eq(120, TimeEarnPolicy.parseCapMinutes(""), "blank → default");
        Assert.eq(120, TimeEarnPolicy.parseCapMinutes("nope"), "bad → default");
        Assert.eq(120, TimeEarnPolicy.parseCapMinutes(0), "zero → default");
        Assert.eq(45, TimeEarnPolicy.parseCapMinutes(45), "int");
        Assert.eq(90, TimeEarnPolicy.parseCapMinutes("90"), "string");
        Assert.eq(180, TimeEarnPolicy.parseCapMinutes(500), "over max → 180");
        Assert.eq(60, TimeEarnPolicy.parseCapMinutes(60.9), "number truncates");
    }

    private static void testBandThresholds() {
        Assert.eq(Band.FAST, TimeEarnPolicy.bandForLatencyMs(0), "0ms fast");
        Assert.eq(Band.FAST, TimeEarnPolicy.bandForLatencyMs(2_999), "2999 fast");
        Assert.eq(Band.THINKING, TimeEarnPolicy.bandForLatencyMs(3_000), "3000 thinking");
        Assert.eq(Band.THINKING, TimeEarnPolicy.bandForLatencyMs(10_000), "10000 thinking");
        Assert.eq(Band.SLOW, TimeEarnPolicy.bandForLatencyMs(10_001), "10001 slow");
        Assert.eq(Band.SLOW, TimeEarnPolicy.bandForLatencyMs(3_600_000), "1h still slow");
        Assert.eq(Band.SKIPPED, TimeEarnPolicy.bandForSkip(), "skip band");
    }

    private static void testNegativeLatencyRejected() {
        try {
            TimeEarnPolicy.bandForLatencyMs(-1);
            throw new AssertionError("negative latency");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    private static void testRewardWeightsAndFloor() {
        TimeEarnPolicy p = new TimeEarnPolicy(120, 1);
        Assert.eq(5, p.rewardFor(Band.FAST), "fast");
        Assert.eq(2, p.rewardFor(Band.THINKING), "thinking");
        Assert.eq(1, p.rewardFor(Band.SLOW), "slow floor");
        Assert.eq(0, p.rewardFor(Band.SKIPPED), "skip");
        TimeEarnPolicy highFloor = new TimeEarnPolicy(120, 3);
        Assert.eq(3, highFloor.rewardFor(Band.SLOW), "custom floor");
    }

    private static void testFloorClampingToCap() {
        TimeEarnPolicy p = new TimeEarnPolicy(3, 2);
        Assert.eq(2, p.recordCorrect(Band.SLOW), "first slow");
        Assert.eq(1, p.recordCorrect(Band.SLOW), "clamped");
        Assert.eq(0, p.recordCorrect(Band.SLOW), "exhausted");
        Assert.eq(3, p.getEarnedMinutes(), "earned");
    }

    private static void testCapExhaustionStopsRewards() {
        TimeEarnPolicy p = new TimeEarnPolicy(5, 1);
        Assert.eq(5, p.recordCorrect(Band.FAST), "exact fill");
        Assert.eq(0, p.recordCorrect(Band.FAST), "past cap");
        Assert.isTrue(p.isCapExhausted(), "exhausted");
        Assert.eq(0, p.getRemainingCapMinutes(), "remaining");
    }

    private static void testCtorGuards() {
        for (int bad : new int[]{0, -1, -10}) {
            try {
                new TimeEarnPolicy(bad, 1);
                throw new AssertionError("cap " + bad);
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
        try {
            new TimeEarnPolicy(2, 5);
            throw new AssertionError("floor > cap");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        try {
            new TimeEarnPolicy(10, -1);
            throw new AssertionError("neg floor");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        // Cap above MAX is clamped; floor must still fit.
        TimeEarnPolicy clamped = new TimeEarnPolicy(999, 10);
        Assert.eq(180, clamped.getCapMinutes(), "ctor clamps cap");
    }

    private static void testFeatureVisibility() {
        Assert.isTrue(TimeEarnPolicy.isFeatureVisible(119 * 60_000L, 120), "119m visible");
        Assert.isTrue(!TimeEarnPolicy.isFeatureVisible(120 * 60_000L, 120), "120m hidden");
        Assert.isTrue(!TimeEarnPolicy.isFeatureVisible(200 * 60_000L, 120), "200m hidden");
        Assert.isTrue(TimeEarnPolicy.isFeatureVisible(1, 120), "1ms visible");
        Assert.isTrue(!TimeEarnPolicy.isFeatureVisible(0, 120), "0 hidden");
        Assert.isTrue(!TimeEarnPolicy.isFeatureVisible(-5, 120), "neg hidden");
        Assert.isTrue(!TimeEarnPolicy.isFeatureVisible(60_000L, 0), "bad cap hidden");
        Assert.isTrue(!TimeEarnPolicy.isFeatureVisible(3 * 60 * 60_000L, 120), "3h hidden");
        Assert.isTrue(!TimeEarnPolicy.isFeatureVisible(2 * 60 * 60_000L, 120), "2h hidden");
    }

    private static void testVisibilityFromAlarmWallClock() {
        long now = 1_000_000L;
        Assert.isTrue(TimeEarnPolicy.isFeatureVisibleAt(now + 30 * 60_000L, now, 120), "30m left");
        Assert.isTrue(!TimeEarnPolicy.isFeatureVisibleAt(now + 200 * 60_000L, now, 120), "200m left");
        Assert.isTrue(!TimeEarnPolicy.isFeatureVisibleAt(0, now, 120), "no alarm");
        Assert.isTrue(!TimeEarnPolicy.isFeatureVisibleAt(now - 1, now, 120), "past alarm");
    }

    private static void testSkipAndCounts() {
        TimeEarnPolicy p = new TimeEarnPolicy();
        p.recordSkip();
        Assert.eq(0, p.getEarnedMinutes(), "skip earns 0");
        Assert.eq(1, p.getSkipCount(), "skip count");
        Assert.eq(0, p.recordCorrect(Band.SKIPPED), "correct+skip band → skip path");
        Assert.eq(2, p.getSkipCount(), "skip via recordCorrect");
        Assert.eq(5, p.recordCorrectLatencyMs(500), "latency fast");
        Assert.eq(1, p.getCorrectCount(), "correct");
        Assert.eq(1, p.getFastCount(), "fast count");
        Assert.eq(3, p.getAttemptCount(), "attempts");
    }

    private static void testFormatMapping() {
        Assert.eq(Format.EQUATION, TimeEarnPolicy.formatFromRender(RenderFormat.EQUATION), "eq");
        Assert.eq(Format.TRUE_FALSE, TimeEarnPolicy.formatFromRender(RenderFormat.TRUE_FALSE), "tf");
        Assert.eq(Format.CHOICE, TimeEarnPolicy.formatFromRender(RenderFormat.CHOICE), "choice");
        Assert.eq(Format.OTHER, TimeEarnPolicy.formatFromRender(null), "null render");
        Assert.eq(Format.CHOICE, TimeEarnPolicy.formatFromId("multiple_choice"), "legacy mc");
        Assert.eq(Format.EQUATION, TimeEarnPolicy.formatFromId("fill_in_blank"), "legacy fib");
        Assert.eq(Format.TRUE_FALSE, TimeEarnPolicy.formatFromId("true_false"), "tf id");
        Assert.eq(Format.OTHER, TimeEarnPolicy.formatFromId("???"), "unknown");
    }

    private static void testBandAndFormatKeys() {
        Assert.eq("fast", TimeEarnPolicy.bandKey(Band.FAST), "fast key");
        Assert.eq("thinking", TimeEarnPolicy.bandKey(Band.THINKING), "thinking key");
        Assert.eq("slow", TimeEarnPolicy.bandKey(Band.SLOW), "slow key");
        Assert.eq("skipped", TimeEarnPolicy.bandKey(Band.SKIPPED), "skipped key");
        Assert.eq("equation", TimeEarnPolicy.formatKey(Format.EQUATION), "equation key");
        Assert.eq("true_false", TimeEarnPolicy.formatKey(Format.TRUE_FALSE), "tf key");
        Assert.eq("choice", TimeEarnPolicy.formatKey(Format.CHOICE), "choice key");
        Assert.eq("digit", TimeEarnPolicy.formatKey(Format.DIGIT), "digit key");
        Assert.eq("other", TimeEarnPolicy.formatKey(Format.OTHER), "other key");
    }

    private static void testAccumulateAndReset() {
        TimeEarnPolicy p = new TimeEarnPolicy(10, 1);
        Assert.eq(5, p.recordCorrect(Band.FAST), "fast");
        Assert.eq(2, p.recordCorrect(Band.THINKING), "thinking");
        Assert.eq(1, p.recordCorrect(Band.SLOW), "slow");
        Assert.eq(8, p.getEarnedMinutes(), "sum");
        Assert.eq(2, p.getRemainingCapMinutes(), "remain");
        Assert.eq(2, p.recordCorrect(Band.FAST), "clamped");
        Assert.eq(10, p.getEarnedMinutes(), "full");
        p.resetSession();
        Assert.eq(0, p.getEarnedMinutes(), "reset earned");
        Assert.eq(0, p.getAttemptCount(), "reset attempts");
        Assert.eq(10, p.getRemainingCapMinutes(), "cap restored");
    }

    private static void testRapidFireSequence() {
        TimeEarnPolicy p = new TimeEarnPolicy(120, 1);
        int total = 0;
        total += p.recordCorrect(Band.FAST);
        total += p.recordCorrect(Band.FAST);
        total += p.recordCorrect(Band.THINKING);
        total += p.recordCorrect(Band.SLOW);
        p.recordSkip();
        total += p.recordCorrect(Band.FAST);
        Assert.eq(18, total, "sequence total");
        Assert.eq(18, p.getEarnedMinutes(), "earned");
        Assert.eq(6, p.getAttemptCount(), "5 correct + 1 skip");
        Assert.eq(5, p.getCorrectCount(), "corrects");
        Assert.eq(3, p.getFastCount(), "fasts");
        Assert.eq(1, p.getThinkingCount(), "thinking");
        Assert.eq(1, p.getSlowCount(), "slow");
        Assert.eq(1, p.getSkipCount(), "skips");
    }

    private static void testMinutesToMsAndAttemptNote() {
        Assert.eq(0L, TimeEarnPolicy.minutesToMs(0), "0 min");
        Assert.eq(0L, TimeEarnPolicy.minutesToMs(-3), "neg min");
        Assert.eq(300_000L, TimeEarnPolicy.minutesToMs(5), "5 min");
        Assert.eq("band=fast;format=choice",
                TimeEarnPolicy.attemptNote(Band.FAST, Format.CHOICE), "note");
    }
}
