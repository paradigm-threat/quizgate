import net.clevertree.quizgate.core.A11yArmPolicy;
import net.clevertree.quizgate.core.ScreenLockPolicy;

/**
 * JVM policy: re-arm after Accessibility returns, and surface (not silence)
 * an armed schedule while Accessibility is off.
 */
public class A11yArmPolicyTest {
    public static void main(String[] args) {
        Assert.isTrue(A11yArmPolicy.hasArmIntent(true, 0L), "armed flag is intent");
        Assert.isTrue(A11yArmPolicy.hasArmIntent(false, 1L),
                "future or stale next alarm still counts as intent");
        Assert.isTrue(!A11yArmPolicy.hasArmIntent(false, 0L), "never armed");

        Assert.isTrue(A11yArmPolicy.shouldRearmAfterA11yRestored(true, true, true, false),
                "armed + a11y + interactive + !pending → rearm");
        Assert.isTrue(!A11yArmPolicy.shouldRearmAfterA11yRestored(true, false, true, false),
                "a11y still off → no rearm");
        Assert.isTrue(!A11yArmPolicy.shouldRearmAfterA11yRestored(false, true, true, false),
                "never armed → no rearm");
        Assert.isTrue(!A11yArmPolicy.shouldRearmAfterA11yRestored(true, true, false, false),
                "screen off → no rearm");
        Assert.isTrue(!A11yArmPolicy.shouldRearmAfterA11yRestored(true, true, true, true),
                "pending screen lock → no rearm");
        Assert.isTrue(
                A11yArmPolicy.shouldRearmAfterA11yRestored(true, true, true, false)
                        == ScreenLockPolicy.shouldArmSchedule(true, true, false),
                "rearm uses the same interactive gate as armWhileInteractive");

        Assert.isTrue(A11yArmPolicy.shouldCancelAlarmsForA11yOff(true, false),
                "armed + a11y off → cancel alarms");
        Assert.isTrue(!A11yArmPolicy.shouldCancelAlarmsForA11yOff(true, true),
                "a11y on → keep alarms");
        Assert.isTrue(!A11yArmPolicy.shouldCancelAlarmsForA11yOff(false, false),
                "not armed → do not treat as an a11y disarm");
        Assert.isTrue(A11yArmPolicy.shouldCancelAlarmsForA11yOff(true, false),
                "cancel even if the screen is off — caller still keeps arm intent");

        Assert.isTrue(A11yArmPolicy.shouldSurfaceA11yBlocked(true, false),
                "surface when armed and a11y off");
        Assert.isTrue(!A11yArmPolicy.shouldSurfaceA11yBlocked(false, false),
                "boot_skip stays quiet when the user never armed");
        Assert.isTrue(!A11yArmPolicy.shouldSurfaceA11yBlocked(true, true),
                "no pause notice when Accessibility is on");
        Assert.isTrue(
                A11yArmPolicy.shouldSurfaceA11yBlocked(true, false)
                        == A11yArmPolicy.shouldCancelAlarmsForA11yOff(true, false),
                "surface and cancel travel together");

        System.out.println("A11yArmPolicyTest OK");
    }
}
