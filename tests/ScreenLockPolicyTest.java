import net.clevertree.quizgate.core.ScreenLockPolicy;

/**
 * JVM unit tests for screen-off pending lock / interactive-only schedule policy.
 */
public class ScreenLockPolicyTest {
    public static void main(String[] args) {
        Assert.isTrue(ScreenLockPolicy.shouldPendOnScreenOff(true), "armed → pend on off");
        Assert.isTrue(!ScreenLockPolicy.shouldPendOnScreenOff(false), "disarmed → no pend");

        Assert.isTrue(ScreenLockPolicy.shouldLaunchOnWake(true, true, false),
                "armed+pending+no session → launch");
        Assert.isTrue(!ScreenLockPolicy.shouldLaunchOnWake(true, true, true),
                "active hard session → no second launch");
        Assert.isTrue(!ScreenLockPolicy.shouldLaunchOnWake(true, false, false),
                "no pending → no launch");
        Assert.isTrue(!ScreenLockPolicy.shouldLaunchOnWake(false, true, false),
                "disarmed → no launch");

        Assert.isTrue(ScreenLockPolicy.shouldArmSchedule(true, true, false),
                "armed interactive !pending → arm");
        Assert.isTrue(!ScreenLockPolicy.shouldArmSchedule(true, false, false),
                "screen off → no arm");
        Assert.isTrue(!ScreenLockPolicy.shouldArmSchedule(true, true, true),
                "pending → no arm");
        Assert.isTrue(!ScreenLockPolicy.shouldArmSchedule(false, true, false),
                "disarmed → no arm");

        Assert.isTrue(ScreenLockPolicy.shouldSkipFireWhileOff(false), "off → skip fire");
        Assert.isTrue(!ScreenLockPolicy.shouldSkipFireWhileOff(true), "on → allow fire");

        System.out.println("ScreenLockPolicyTest OK");
    }
}
