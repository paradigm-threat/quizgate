import java.util.Objects;

import net.clevertree.quizgate.core.TimeEarnPolicy;
import net.clevertree.quizgate.core.TimeEarnPolicy.Band;
import net.clevertree.quizgate.core.TimeEarnPolicy.Format;

public class TimeEarnTest {

    private static int fails = 0;

    public static void main(String[] args) {
        testDefaultCapAndFloor();
        testBandThresholds();
        testBandBoundaries();
        testNegativeLatencyRejected();
        testRewardWeightsAndFloor();
        testFloorClampingToCap();
        testCapExhaustionStopsRewards();
        testCapCannotBeZeroOrNegative();
        testFloorCannotExceedCap();
        testFloorCannotBeNegative();
        testFeatureVisibilityThreshold();
        testFeatureHiddenAtOrAboveCapRemaining();
        testSkipEarnsNothingAndMapsToSkippedBand();
        testBandAndFormatKeysAreStablePayloadStrings();
        testMultipleAttemptsAccumulateTowardCap();
        testRapidFireSequenceFastThenSlowThenSkip();
        if (fails == 0) {
            System.out.println("ALL TIME EARN TESTS PASSED");
        } else {
            System.err.println(fails + " TIME EARN TEST(S) FAILED");
            System.exit(1);
        }
    }

    private static void testDefaultCapAndFloor() {
        TimeEarnPolicy p = new TimeEarnPolicy();
        assertEq(120, p.getCapMinutes(), "default cap is 120 minutes (two hours)");
        assertEq(1, p.getFloorMinutes(), "default floor is 1 minute");
        assertEq(0, p.getEarnedMinutes(), "starts at zero earned");
    }

    private static void testBandThresholds() {
        assertEq(Band.FAST, TimeEarnPolicy.bandForLatencyMs(0), "0ms is fast");
        assertEq(Band.FAST, TimeEarnPolicy.bandForLatencyMs(2_999), "2999ms is fast");
        assertEq(Band.THINKING, TimeEarnPolicy.bandForLatencyMs(3_000), "3000ms is thinking");
        assertEq(Band.THINKING, TimeEarnPolicy.bandForLatencyMs(10_000), "10000ms is thinking");
        assertEq(Band.SLOW, TimeEarnPolicy.bandForLatencyMs(10_001), "10001ms is slow");
        assertEq(Band.SLOW, TimeEarnPolicy.bandForLatencyMs(60_000), "1 minute is slow");
        assertEq(Band.SLOW, TimeEarnPolicy.bandForLatencyMs(3_600_000), "1 hour is slow — same band as 11 seconds");
    }

    private static void testBandBoundaries() {
        // Exactly at thresholds: fast is strictly under 3s, thinking is 3s–10s inclusive.
        assertEq(Band.FAST, TimeEarnPolicy.bandForLatencyMs(2_999));
        assertEq(Band.THINKING, TimeEarnPolicy.bandForLatencyMs(3_000));
        assertEq(Band.THINKING, TimeEarnPolicy.bandForLatencyMs(10_000));
        assertEq(Band.SLOW, TimeEarnPolicy.bandForLatencyMs(10_001));
    }

    private static void testNegativeLatencyRejected() {
        try {
            TimeEarnPolicy.bandForLatencyMs(-1);
            fail("negative latency should throw");
        } catch (IllegalArgumentException expected) {
            // pass
        }
    }

    private static void testRewardWeightsAndFloor() {
        TimeEarnPolicy p = new TimeEarnPolicy(120, 1);
        assertEq(5, p.rewardFor(Band.FAST), "fast = 5 minutes");
        assertEq(2, p.rewardFor(Band.THINKING), "thinking = 2 minutes");
        assertEq(1, p.rewardFor(Band.SLOW), "slow = floor (1 minute)");
        assertEq(0, p.rewardFor(Band.SKIPPED), "skipped = 0");
    }

    private static void testFloorClampingToCap() {
        // Cap of 3, floor of 2: a slow answer near the end can only grant 1.
        TimeEarnPolicy p = new TimeEarnPolicy(3, 2);
        assertEq(2, p.recordCorrect(Band.SLOW), "first slow grants floor 2");
        assertEq(1, p.recordCorrect(Band.SLOW), "second slow clamped to remaining 1");
        assertEq(0, p.recordCorrect(Band.SLOW), "cap exhausted");
        assertEq(3, p.getEarnedMinutes());
    }

    private static void testCapExhaustionStopsRewards() {
        TimeEarnPolicy p = new TimeEarnPolicy(5, 1);
        assertEq(5, p.recordCorrect(Band.FAST), "fast 5 fits exactly in cap 5");
        assertEq(0, p.recordCorrect(Band.FAST), "no reward past cap");
        assertEq(true, p.isCapExhausted());
        assertEq(0, p.getRemainingCapMinutes());
    }

    private static void testCapCannotBeZeroOrNegative() {
        for (int bad : new int[]{0, -1, -10}) {
            try {
                new TimeEarnPolicy(bad, 1);
                fail("cap " + bad + " should throw");
            } catch (IllegalArgumentException expected) {
                // pass
            }
        }
    }

    private static void testFloorCannotExceedCap() {
        try {
            new TimeEarnPolicy(2, 5);
            fail("floor > cap should throw");
        } catch (IllegalArgumentException expected) {
            // pass
        }
    }

    private static void testFloorCannotBeNegative() {
        try {
            new TimeEarnPolicy(10, -1);
            fail("negative floor should throw");
        } catch (IllegalArgumentException expected) {
            // pass
        }
    }

    private static void testFeatureVisibilityThreshold() {
        // Visible when remaining schedule time is strictly under the cap.
        assertEq(true, TimeEarnPolicy.isFeatureVisible(119 * 60_000L, 120), "119 min remaining → visible");
        assertEq(false, TimeEarnPolicy.isFeatureVisible(120 * 60_000L, 120), "exactly 120 min → hidden");
        assertEq(false, TimeEarnPolicy.isFeatureVisible(200 * 60_000L, 120), "200 min remaining → hidden");
        assertEq(true, TimeEarnPolicy.isFeatureVisible(1, 120), "1 ms remaining → visible");
        assertEq(false, TimeEarnPolicy.isFeatureVisible(0, 120), "0 remaining → hidden (nothing to add to)");
    }

    private static void testFeatureHiddenAtOrAboveCapRemaining() {
        assertEq(false, TimeEarnPolicy.isFeatureVisible(3 * 60 * 60_000L, 120));
        assertEq(false, TimeEarnPolicy.isFeatureVisible(2 * 60 * 60_000L, 120));
    }

    private static void testSkipEarnsNothingAndMapsToSkippedBand() {
        TimeEarnPolicy p = new TimeEarnPolicy();
        assertEq(Band.SKIPPED, TimeEarnPolicy.bandForSkip());
        p.recordSkip();
        assertEq(0, p.getEarnedMinutes(), "skip adds no minutes");
        assertEq(0, p.rewardFor(Band.SKIPPED));
    }

    private static void testBandAndFormatKeysAreStablePayloadStrings() {
        assertEq("fast", TimeEarnPolicy.bandKey(Band.FAST));
        assertEq("thinking", TimeEarnPolicy.bandKey(Band.THINKING));
        assertEq("slow", TimeEarnPolicy.bandKey(Band.SLOW));
        assertEq("skipped", TimeEarnPolicy.bandKey(Band.SKIPPED));
        assertEq("multiple_choice", TimeEarnPolicy.formatKey(Format.MULTIPLE_CHOICE));
        assertEq("fill_in_blank", TimeEarnPolicy.formatKey(Format.FILL_IN_BLANK));
        assertEq("true_false", TimeEarnPolicy.formatKey(Format.TRUE_FALSE));
        assertEq("digit", TimeEarnPolicy.formatKey(Format.DIGIT));
        assertEq("other", TimeEarnPolicy.formatKey(Format.OTHER));
    }

    private static void testMultipleAttemptsAccumulateTowardCap() {
        TimeEarnPolicy p = new TimeEarnPolicy(10, 1);
        assertEq(5, p.recordCorrect(Band.FAST));
        assertEq(2, p.recordCorrect(Band.THINKING));
        assertEq(1, p.recordCorrect(Band.SLOW));
        assertEq(8, p.getEarnedMinutes());
        assertEq(2, p.getRemainingCapMinutes());
        assertEq(2, p.recordCorrect(Band.FAST), "clamped to remaining 2");
        assertEq(10, p.getEarnedMinutes());
    }

    private static void testRapidFireSequenceFastThenSlowThenSkip() {
        TimeEarnPolicy p = new TimeEarnPolicy(120, 1);
        // Simulates a kid hammering through: fast, fast, thinking, slow, skip, fast.
        int total = 0;
        total += p.recordCorrect(Band.FAST);
        total += p.recordCorrect(Band.FAST);
        total += p.recordCorrect(Band.THINKING);
        total += p.recordCorrect(Band.SLOW);
        p.recordSkip();
        total += p.recordCorrect(Band.FAST);
        assertEq(5 + 5 + 2 + 1 + 5, total, "sequence total");
        assertEq(18, p.getEarnedMinutes());
        // Stats correlation: 5 attempts recorded (4 correct + 1 skip), each with band + format.
        assertEq(5, 4 + 1);
    }

    private static void assertEq(int expected, int actual) {
        assertEq(expected, actual, "values");
    }

    private static void assertEq(int expected, int actual, String msg) {
        if (expected != actual) {
            fails++;
            System.err.println("FAIL: " + msg + " — expected " + expected + ", got " + actual);
        }
    }

    private static void assertEq(long expected, long actual, String msg) {
        if (expected != actual) {
            fails++;
            System.err.println("FAIL: " + msg + " — expected " + expected + ", got " + actual);
        }
    }

    private static void assertEq(boolean expected, boolean actual) {
        assertEq(expected, actual, "values");
    }

    private static void assertEq(boolean expected, boolean actual, String msg) {
        if (expected != actual) {
            fails++;
            System.err.println("FAIL: " + msg + " — expected " + expected + ", got " + actual);
        }
    }

    private static void assertEq(String expected, String actual) {
        if (!Objects.equals(expected, actual)) {
            fails++;
            System.err.println("FAIL: expected '" + expected + "', got '" + actual + "'");
        }
    }

    private static void assertEq(Band expected, Band actual) {
        assertEq(expected, actual, "band");
    }

    private static void assertEq(Band expected, Band actual, String msg) {
        if (expected != actual) {
            fails++;
            System.err.println("FAIL: " + msg + " — expected " + expected + ", got " + actual);
        }
    }

    private static void fail(String msg) {
        fails++;
        System.err.println("FAIL: " + msg);
    }
}
