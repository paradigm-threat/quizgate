import net.clevertree.quizgate.core.AnswerHintPolicy;

public class AnswerHintPolicyTest {
    public static void main(String[] args) {
        Assert.eq(AnswerHintPolicy.MODE_UNTIL_FIRST_CORRECT,
                AnswerHintPolicy.normalizeMode(null), "default mode");
        Assert.eq(AnswerHintPolicy.MODE_OFF, AnswerHintPolicy.normalizeMode("OFF"), "off");
        Assert.eq(AnswerHintPolicy.MODE_ALWAYS, AnswerHintPolicy.normalizeMode("always"), "always");

        AnswerHintPolicy.Config off = AnswerHintPolicy.resolve(
                AnswerHintPolicy.MODE_OFF, 0, true, 3000, 500, 3000, 12000, 18000, true);
        Assert.isTrue(!off.enabled, "mode off");

        AnswerHintPolicy.Config afterRight = AnswerHintPolicy.resolve(
                AnswerHintPolicy.MODE_UNTIL_FIRST_CORRECT, 1, true,
                3000, 500, 3000, 12000, 18000, true);
        Assert.isTrue(!afterRight.enabled, "until_first with rights");

        AnswerHintPolicy.Config ok = AnswerHintPolicy.resolve(
                AnswerHintPolicy.MODE_UNTIL_FIRST_CORRECT, 0, true,
                3000, 500, 3000, 12000, 18000, true);
        Assert.isTrue(ok.enabled, "enabled");
        Assert.isTrue(ok.softRepeat, "soft repeat");
        Assert.isTrue(ok.strongEnabled, "strong");
        Assert.isTrue(ok.blocksEnabled, "blocks");
        Assert.eq(3000, ok.delayMs, "delay");
        Assert.eq(12000, ok.strongAfterMs, "strong after");

        AnswerHintPolicy.Config slim = AnswerHintPolicy.resolve(
                AnswerHintPolicy.MODE_ALWAYS, 5, false,
                3000, 500, 3000, 12000, 18000, true);
        Assert.isTrue(slim.enabled, "always even with rights");
        Assert.isTrue(!slim.softRepeat, "slim no soft repeat");
        Assert.isTrue(!slim.strongEnabled, "slim no strong");
        Assert.isTrue(!slim.blocksEnabled, "slim no blocks");

        int[] add = AnswerHintPolicy.parseAddends("5 + 2 = _");
        Assert.isTrue(add != null && add[0] == 5 && add[1] == 2, "parse addends");
        Assert.isTrue(AnswerHintPolicy.parseAddends("2 - 1 = _") == null, "no subtract");

        Assert.eq(AnswerHintPolicy.DEFAULT_DELAY_MS,
                AnswerHintPolicy.clampMs(-1, AnswerHintPolicy.DEFAULT_DELAY_MS), "clamp");
        System.out.println("AnswerHintPolicyTest OK");
    }
}
