import net.clevertree.quizgate.core.PerfPolicy;

public class PerfPolicyTest {
    public static void main(String[] args) {
        Assert.eq(PerfPolicy.BALANCED, PerfPolicy.normalize(null), "null → balanced");
        Assert.eq(PerfPolicy.BALANCED, PerfPolicy.normalize(""), "empty → balanced");
        Assert.eq(PerfPolicy.SLIM, PerfPolicy.normalize("slim"), "slim");
        Assert.eq(PerfPolicy.SLIM, PerfPolicy.normalize("SLIM"), "SLIM case");
        Assert.isTrue(PerfPolicy.isSlim("slim"), "isSlim");
        Assert.isTrue(!PerfPolicy.isSlim("balanced"), "not slim");
        Assert.isTrue(PerfPolicy.fgsStopAfterMs(true) < PerfPolicy.fgsStopAfterMs(false),
                "slim shorter FGS");
        Assert.isTrue(PerfPolicy.compactEvery(true) > PerfPolicy.compactEvery(false),
                "slim defers compact");
        Assert.isTrue(PerfPolicy.keepScheduleKind(true, "alarm_fired"), "slim keeps fire");
        Assert.isTrue(PerfPolicy.keepScheduleKind(true, "arm"), "slim keeps arm");
        Assert.isTrue(!PerfPolicy.keepScheduleKind(true, "launch_attempt"), "slim drops attempt");
        Assert.isTrue(PerfPolicy.keepScheduleKind(false, "launch_attempt"), "balanced keeps attempt");
        System.out.println("PerfPolicyTest OK");
    }
}
