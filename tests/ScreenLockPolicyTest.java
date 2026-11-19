import net.clevertree.quizgate.core.ScreenLockPolicy;

/**
 * JVM unit tests for screen-off pending lock / interactive-only schedule /
 * never-stack unlock grace policy.
 */
public class ScreenLockPolicyTest {
    public static void main(String[] args) {
        Assert.isTrue(ScreenLockPolicy.shouldPendOnScreenOff(true), "armed → pend on off");
        Assert.isTrue(!ScreenLockPolicy.shouldPendOnScreenOff(false), "disarmed → no pend");
        Assert.isTrue(ScreenLockPolicy.shouldClearGraceOnScreenOff(true),
                "armed screen-off clears grace so wake locks");
        Assert.isTrue(!ScreenLockPolicy.shouldClearGraceOnScreenOff(false),
                "disarmed screen-off leaves grace alone");

        Assert.isTrue(ScreenLockPolicy.shouldLaunchOnWake(true, true, false, false),
                "armed+pending+no session → launch");
        Assert.isTrue(!ScreenLockPolicy.shouldLaunchOnWake(true, true, true, false),
                "active hard session → no second launch");
        Assert.isTrue(!ScreenLockPolicy.shouldLaunchOnWake(true, false, false, false),
                "no pending → no launch");
        Assert.isTrue(!ScreenLockPolicy.shouldLaunchOnWake(false, true, false, false),
                "disarmed → no launch");
        Assert.isTrue(!ScreenLockPolicy.shouldLaunchOnWake(true, true, false, true),
                "unlock grace → no wake launch (never stack)");

        Assert.isTrue(ScreenLockPolicy.shouldArmSchedule(true, true, false),
                "armed interactive !pending → arm");
        Assert.isTrue(!ScreenLockPolicy.shouldArmSchedule(true, false, false),
                "screen off → no arm");
        Assert.isTrue(!ScreenLockPolicy.shouldArmSchedule(true, true, true),
                "pending → no arm");
        Assert.isTrue(!ScreenLockPolicy.shouldArmSchedule(false, true, false),
                "disarmed → no arm");

        Assert.isTrue(ScreenLockPolicy.shouldRearmScheduleOnWakeSkip(true, false),
                "grace + !pending → rearm after cancelled alarms");
        Assert.isTrue(ScreenLockPolicy.shouldRearmScheduleOnWakeSkip(true, true),
                "grace + pending cleared path → rearm");
        Assert.isTrue(ScreenLockPolicy.shouldRearmScheduleOnWakeSkip(false, false),
                "!pending wake skip → rearm");
        Assert.isTrue(!ScreenLockPolicy.shouldRearmScheduleOnWakeSkip(false, true),
                "pending outside grace → wait for wake launch, no rearm");

        Assert.isTrue(ScreenLockPolicy.shouldSkipFireWhileOff(false), "off → skip fire");
        Assert.isTrue(!ScreenLockPolicy.shouldSkipFireWhileOff(true), "on → allow fire");

        // Reintercept must never run while the display is off (would wake the tablet).
        Assert.isTrue(ScreenLockPolicy.shouldReintercept(true, true, false),
                "hard+interactive → reintercept");
        Assert.isTrue(!ScreenLockPolicy.shouldReintercept(true, false, false),
                "screen off → no reintercept");
        Assert.isTrue(!ScreenLockPolicy.shouldReintercept(true, true, true),
                "grace → no reintercept");
        Assert.isTrue(!ScreenLockPolicy.shouldReintercept(false, true, false),
                "no hard session → no reintercept");

        Assert.isTrue(ScreenLockPolicy.shouldBringHardOnWake(true, true, true, false),
                "wake resume existing hard");
        Assert.isTrue(!ScreenLockPolicy.shouldBringHardOnWake(true, true, false, false),
                "no hard → fresh launch path, not bring");
        Assert.isTrue(!ScreenLockPolicy.shouldBringHardOnWake(true, true, true, true),
                "grace → no bring");

        Assert.eq(1, ScreenLockPolicy.lockRoundSize(), "lock roundSize is 1");
        Assert.eq(120_000L, ScreenLockPolicy.UNLOCK_GRACE_MS, "grace 120s");

        long now = 1_000_000L;
        Assert.isTrue(ScreenLockPolicy.inUnlockGrace(now, now + 1), "inside grace");
        Assert.isTrue(!ScreenLockPolicy.inUnlockGrace(now, now), "at expiry not in grace");
        Assert.isTrue(!ScreenLockPolicy.inUnlockGrace(now, 0L), "no grace");
        Assert.isTrue(!ScreenLockPolicy.inUnlockGrace(now + 200_000L, now + 120_000L),
                "after grace");

        // Legacy 3-arg overload still blocks when hard active / not pending
        Assert.isTrue(ScreenLockPolicy.shouldLaunchOnWake(true, true, false),
                "3-arg launch ok");
        Assert.isTrue(!ScreenLockPolicy.shouldLaunchOnWake(true, true, true),
                "3-arg hard active blocks");

        System.out.println("ScreenLockPolicyTest OK");
    }
}
