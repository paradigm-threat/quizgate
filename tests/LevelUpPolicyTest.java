import net.clevertree.quizgate.core.LevelUpPolicy;

public class LevelUpPolicyTest {
    public static void main(String[] args) {
        Assert.eq(75, LevelUpPolicy.DEFAULT_MIN_ACCURACY_PCT, "acc default");
        Assert.eq(5, LevelUpPolicy.DEFAULT_MIN_CORRECT, "min correct default");
        Assert.eq(25, LevelUpPolicy.DEFAULT_MAX_SKIP_PCT, "skip default");

        LevelUpPolicy.Eligibility ok = LevelUpPolicy.evaluate(
                10, 8, 2, 1, 75, 5, 25);
        Assert.isTrue(ok.eligible, "8/10 answered 80%, 1 skip → eligible");
        Assert.eq(80, ok.accuracyPct, "accuracy");
        Assert.eq(10, ok.skipPct, "skip pct");

        LevelUpPolicy.Eligibility lowAcc = LevelUpPolicy.evaluate(
                10, 5, 5, 0, 75, 5, 25);
        Assert.isTrue(!lowAcc.eligible, "50% accuracy fails");
        Assert.isTrue(!lowAcc.accurateEnough, "accurateEnough false");

        LevelUpPolicy.Eligibility few = LevelUpPolicy.evaluate(
                4, 4, 0, 0, 75, 5, 25);
        Assert.isTrue(!few.eligible, "only 4 correct fails min");
        Assert.isTrue(!few.enoughCorrect, "enoughCorrect false");

        LevelUpPolicy.Eligibility skips = LevelUpPolicy.evaluate(
                10, 8, 0, 4, 75, 5, 25);
        Assert.isTrue(!skips.eligible, "40% skip fails");
        Assert.isTrue(!skips.skipsOk, "skipsOk false");

        Assert.eq(75, LevelUpPolicy.parseMinAccuracyPct(-1), "bad → default");
        Assert.eq(5, LevelUpPolicy.parseMinCorrect(0), "bad min → default");
        System.out.println("LevelUpPolicyTest OK");
    }
}
