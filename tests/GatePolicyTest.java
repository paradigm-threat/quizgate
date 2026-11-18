import net.clevertree.quizgate.core.GatePolicy;
import net.clevertree.quizgate.core.GatePolicy.CriticalGap;

/**
 * JVM unit tests for Hard-only gate policy (no Android framework).
 */
public class GatePolicyTest {
    public static void main(String[] args) {
        Assert.eq(GatePolicy.HARD, GatePolicy.DEFAULT, "default is hard");
        Assert.eq(GatePolicy.HARD, GatePolicy.normalize(null), "null → hard");
        Assert.eq(GatePolicy.HARD, GatePolicy.normalize(""), "empty → hard");
        Assert.eq(GatePolicy.HARD, GatePolicy.normalize("HARD"), "HARD → hard");
        Assert.eq(GatePolicy.HARD, GatePolicy.normalize("weird"), "unknown → hard");
        Assert.eq(GatePolicy.HARD, GatePolicy.normalize("soft"), "soft migrates → hard");
        Assert.eq(GatePolicy.HARD, GatePolicy.normalize("SOFT"), "SOFT migrates → hard");

        Assert.isTrue(GatePolicy.isHard(null), "null isHard");
        Assert.isTrue(GatePolicy.isHard("hard"), "hard isHard");
        Assert.isTrue(GatePolicy.isHard("soft"), "legacy soft still isHard");
        Assert.isTrue(!GatePolicy.isSoft("soft"), "isSoft always false");
        Assert.isTrue(!GatePolicy.isSoft("hard"), "hard !isSoft");

        Assert.isTrue(!GatePolicy.isOverlayCritical(false), "overlay never critical");
        Assert.isTrue(!GatePolicy.isOverlayCritical(true), "overlay never critical (hard)");
        Assert.isTrue(GatePolicy.isAccessibilityCritical(true), "a11y always critical");
        Assert.isTrue(GatePolicy.isAccessibilityCritical(false), "a11y critical even if flag false");

        // Hard-only: Accessibility off blocks; overlay off does not.
        Assert.eq(CriticalGap.ACCESSIBILITY, GatePolicy.firstCriticalGap(
                true, true, true, true, true, false, false),
                "a11y missing is critical");
        Assert.eq(CriticalGap.ACCESSIBILITY, GatePolicy.firstCriticalGap(
                false, true, true, true, true, false, true),
                "a11y missing critical regardless of hardMode flag");
        Assert.eq(CriticalGap.NONE, GatePolicy.firstCriticalGap(
                true, true, true, true, true, true, false),
                "overlay not required for Arm critical");

        // Shared schedule reliability gaps come first.
        Assert.eq(CriticalGap.EXACT_ALARM, GatePolicy.firstCriticalGap(
                true, false, true, true, true, true, true),
                "exact first");
        Assert.eq(CriticalGap.NOTIFICATIONS, GatePolicy.firstCriticalGap(
                true, true, false, true, true, true, true),
                "notif when runtime missing");
        Assert.eq(CriticalGap.NOTIFICATIONS, GatePolicy.firstCriticalGap(
                true, true, true, false, true, true, true),
                "notif when channel demoted");
        Assert.eq(CriticalGap.BATTERY, GatePolicy.firstCriticalGap(
                true, true, true, true, false, true, true),
                "battery before a11y");

        System.out.println("GatePolicyTest OK");
    }
}
