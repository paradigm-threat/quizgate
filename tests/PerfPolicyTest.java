import net.clevertree.quizgate.core.PerfPolicy;
import net.clevertree.quizgate.core.PerfPolicy.AutoDecision;
import net.clevertree.quizgate.core.PerfPolicy.AutoSignals;

public class PerfPolicyTest {
    public static void main(String[] args) {
        Assert.eq(PerfPolicy.AUTO, PerfPolicy.normalize(null), "null → auto");
        Assert.eq(PerfPolicy.AUTO, PerfPolicy.normalize(""), "empty → auto");
        Assert.eq(PerfPolicy.AUTO, PerfPolicy.normalize("auto"), "auto");
        Assert.eq(PerfPolicy.SLIM, PerfPolicy.normalize("slim"), "slim");
        Assert.eq(PerfPolicy.PERFORMANCE, PerfPolicy.normalize("performance"), "performance");
        Assert.eq(PerfPolicy.PERFORMANCE, PerfPolicy.normalize("balanced"), "balanced→performance");
        Assert.isTrue(PerfPolicy.isAuto("auto"), "isAuto");
        Assert.isTrue(PerfPolicy.isFixedSlim("slim"), "fixed slim");
        Assert.isTrue(PerfPolicy.isFixedPerformance("performance"), "fixed perf");

        Assert.eq(PerfPolicy.SLIM, PerfPolicy.effective("slim", null), "fixed slim ignores signals");
        Assert.eq(PerfPolicy.PERFORMANCE, PerfPolicy.effective("performance", null), "fixed perf");

        AutoSignals healthy = signals(80, true, false, false, false, 192, 4096, 8, false);
        AutoDecision dHealthy = PerfPolicy.decideAuto(healthy, false);
        Assert.isTrue(!dHealthy.preferSlim, "healthy → performance");
        Assert.eq(PerfPolicy.PERFORMANCE, dHealthy.effective, "healthy effective");

        AutoSignals lowBat = signals(12, false, false, false, false, 192, 4096, 8, false);
        AutoDecision dLow = PerfPolicy.decideAuto(lowBat, false);
        Assert.isTrue(dLow.preferSlim, "low battery → slim");
        Assert.isTrue(dLow.reasonCodesCsv().contains("battery_pct_low"), "reason battery_pct_low");

        AutoSignals saver = signals(50, false, true, false, false, 192, 4096, 8, false);
        Assert.isTrue(PerfPolicy.decideAuto(saver, false).preferSlim, "saver → slim");

        AutoSignals weak = signals(90, true, false, false, true, 64, 2048, 4, false);
        Assert.isTrue(PerfPolicy.decideAuto(weak, false).preferSlim, "weak device → slim");

        // Sticky slim until recovery
        AutoSignals recovering = signals(22, false, false, false, false, 192, 4096, 8, false);
        AutoDecision sticky = PerfPolicy.decideAuto(recovering, true);
        Assert.isTrue(sticky.preferSlim, "22% not charging stays sticky");

        AutoSignals recovered = signals(40, true, false, false, false, 192, 4096, 8, false);
        AutoDecision back = PerfPolicy.decideAuto(recovered, true);
        Assert.isTrue(!back.preferSlim, "charging+40% recovers");
        Assert.isTrue(back.reasonCodesCsv().contains("recovered"), "recovered code");

        Assert.eq(PerfPolicy.SLIM, PerfPolicy.effective("auto",
                new AutoSignals(10, false, true, false, false, 192, 4096, 8, false, true)),
                "auto + preferSlim");

        Assert.isTrue(PerfPolicy.fgsStopAfterMs(true) < PerfPolicy.fgsStopAfterMs(false),
                "slim shorter FGS");
        Assert.isTrue(PerfPolicy.keepScheduleKind(true, "arm_blocked"), "slim keeps arm_blocked");
        Assert.isTrue(PerfPolicy.keepScheduleKind(true, "boot_skip"), "slim keeps boot_skip");
        Assert.isTrue(PerfPolicy.keepScheduleKind(true, "perf_mode"), "slim keeps perf_mode");
        Assert.isTrue(PerfPolicy.keepScheduleKind(true, "hard_reintercept"), "slim keeps hard_reintercept");
        Assert.isTrue(PerfPolicy.keepScheduleKind(true, "hard_unlock"), "slim keeps hard_unlock");
        Assert.isTrue(!PerfPolicy.keepScheduleKind(true, "launch_attempt"), "slim drops attempt");

        System.out.println("PerfPolicyTest OK");
    }

    private static AutoSignals signals(int bat, boolean charging, boolean saver, boolean lowBcast,
                                       boolean lowRam, int ramClass, long totalRam, int cpus,
                                       boolean thermal) {
        return new AutoSignals(bat, charging, saver, lowBcast, lowRam, ramClass, totalRam, cpus,
                thermal, false);
    }
}
