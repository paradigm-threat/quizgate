import net.clevertree.quizgate.core.GatePolicy;
import net.clevertree.quizgate.core.GatePolicy.CriticalGap;

/**
 * JVM unit tests for Soft/Hard gate policy (no Android framework).
 */
public class GatePolicyTest {
    public static void main(String[] args) {
        Assert.eq(GatePolicy.HARD, GatePolicy.DEFAULT, "default is hard");
        Assert.eq(GatePolicy.HARD, GatePolicy.normalize(null), "null → hard");
        Assert.eq(GatePolicy.HARD, GatePolicy.normalize(""), "empty → hard");
        Assert.eq(GatePolicy.HARD, GatePolicy.normalize("HARD"), "HARD → hard");
        Assert.eq(GatePolicy.HARD, GatePolicy.normalize("weird"), "unknown → hard");
        Assert.eq(GatePolicy.SOFT, GatePolicy.normalize("soft"), "soft");
        Assert.eq(GatePolicy.SOFT, GatePolicy.normalize("SOFT"), "SOFT → soft");

        Assert.isTrue(GatePolicy.isHard(null), "null isHard");
        Assert.isTrue(GatePolicy.isHard("hard"), "hard isHard");
        Assert.isTrue(!GatePolicy.isHard("soft"), "soft !isHard");
        Assert.isTrue(GatePolicy.isSoft("soft"), "soft isSoft");
        Assert.isTrue(!GatePolicy.isSoft("hard"), "hard !isSoft");

        Assert.isTrue(GatePolicy.isOverlayCritical(false), "soft overlay critical");
        Assert.isTrue(!GatePolicy.isOverlayCritical(true), "hard overlay not critical");
        Assert.isTrue(GatePolicy.isAccessibilityCritical(true), "hard a11y critical");
        Assert.isTrue(!GatePolicy.isAccessibilityCritical(false), "soft a11y not critical");

        // Soft: Accessibility off must NOT block Arm; overlay off must.
        Assert.eq(CriticalGap.OVERLAY, GatePolicy.firstCriticalGap(
                false, true, true, true, true, false, false),
                "soft: overlay missing is critical even if a11y off");
        Assert.eq(CriticalGap.NONE, GatePolicy.firstCriticalGap(
                false, true, true, true, true, false, true),
                "soft: a11y not required when overlay ok");

        // Hard: Accessibility off blocks; overlay off does not (after a11y ok).
        Assert.eq(CriticalGap.ACCESSIBILITY, GatePolicy.firstCriticalGap(
                true, true, true, true, true, false, false),
                "hard: a11y missing is critical");
        Assert.eq(CriticalGap.NONE, GatePolicy.firstCriticalGap(
                true, true, true, true, true, true, false),
                "hard: overlay not required for Arm critical");

        // Shared schedule reliability gaps come first.
        Assert.eq(CriticalGap.EXACT_ALARM, GatePolicy.firstCriticalGap(
                true, false, true, true, true, true, true),
                "exact first");
        Assert.eq(CriticalGap.NOTIFICATIONS, GatePolicy.firstCriticalGap(
                false, true, false, true, true, true, true),
                "notif when runtime missing");
        Assert.eq(CriticalGap.NOTIFICATIONS, GatePolicy.firstCriticalGap(
                false, true, true, false, true, true, true),
                "notif when channel demoted");
        Assert.eq(CriticalGap.BATTERY, GatePolicy.firstCriticalGap(
                true, true, true, true, false, true, true),
                "battery before mode grant");

        System.out.println("GatePolicyTest OK");
    }
}
