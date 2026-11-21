import net.clevertree.quizgate.core.LevelUpPolicy;

public class LevelUpPolicyTest {
    public static void main(String[] args) {
        Assert.eq(75, LevelUpPolicy.DEFAULT_MIN_ACCURACY_PCT, "acc default");
        Assert.eq(5, LevelUpPolicy.DEFAULT_MIN_CORRECT, "min correct default");
        Assert.eq(-1, LevelUpPolicy.DEFAULT_MAX_SKIP_PCT, "skip default sentinel");

        // Default skip rule: skipped <= right+wrong
        LevelUpPolicy.Eligibility ok = LevelUpPolicy.evaluate(
                10, 8, 2, 1, 75, 5, -1);
        Assert.isTrue(ok.eligible, "8/2 answered 80%, 1 skip ≤ 10 → eligible");
        Assert.eq(80, ok.accuracyPct, "accuracy");

        LevelUpPolicy.Eligibility sim = LevelUpPolicy.evaluate(
                15, 9, 1, 5, 75, 5, -1);
        Assert.isTrue(sim.eligible, "9/1 + 5 skips (5≤10) → eligible");

        LevelUpPolicy.Eligibility tooManySkips = LevelUpPolicy.evaluate(
                15, 5, 0, 10, 75, 5, -1);
        Assert.isTrue(!tooManySkips.eligible, "10 skips > 5 answered → block");
        Assert.isTrue(!tooManySkips.skipsOk, "skipsOk false");

        LevelUpPolicy.Eligibility lowAcc = LevelUpPolicy.evaluate(
                10, 5, 5, 0, 75, 5, -1);
        Assert.isTrue(!lowAcc.eligible, "50% accuracy fails");

        LevelUpPolicy.Eligibility few = LevelUpPolicy.evaluate(
                4, 4, 0, 0, 75, 5, -1);
        Assert.isTrue(!few.eligible, "only 4 correct fails min");

        // Admin pct override still works
        LevelUpPolicy.Eligibility pctFail = LevelUpPolicy.evaluate(
                10, 8, 0, 4, 75, 5, 25);
        Assert.isTrue(!pctFail.eligible, "40% skip fails pct override");

        Assert.eq(75, LevelUpPolicy.parseMinAccuracyPct(-1), "bad → default");
        Assert.eq(5, LevelUpPolicy.parseMinCorrect(0), "bad min → default");
        System.out.println("LevelUpPolicyTest OK");
    }
}
