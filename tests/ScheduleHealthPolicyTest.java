import net.clevertree.quizgate.core.DiagnosticsStore;
import net.clevertree.quizgate.core.InteractiveMinutesStore;
import net.clevertree.quizgate.core.ScheduleHealthPolicy;
import net.clevertree.quizgate.core.StatsDayPolicy;
import net.clevertree.quizgate.core.StatsStore;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.TimeZone;

public class ScheduleHealthPolicyTest {
    public static void main(String[] args) throws Exception {
        Assert.eq(ScheduleHealthPolicy.STATUS_OK,
                ScheduleHealthPolicy.fireStatus(true, true, 1, 0, 0, 0),
                "fire ok");
        Assert.eq(ScheduleHealthPolicy.STATUS_OK,
                ScheduleHealthPolicy.fireStatus(true, true, 0, 1, 0, 0),
                "quiz ok");
        Assert.eq(ScheduleHealthPolicy.STATUS_A11Y_OFF,
                ScheduleHealthPolicy.fireStatus(false, true, 0, 0, 0, 3_600_000L),
                "armed without a11y");
        Assert.eq(ScheduleHealthPolicy.STATUS_A11Y_OFF,
                ScheduleHealthPolicy.fireStatus(true, true, 0, 0, 2, 0),
                "arm_blocked");
        Assert.eq(ScheduleHealthPolicy.STATUS_NEVER_ARMED,
                ScheduleHealthPolicy.fireStatus(true, false, 0, 0, 0, 3_600_000L),
                "never armed");
        Assert.eq(ScheduleHealthPolicy.STATUS_IDLE_OR_OFF,
                ScheduleHealthPolicy.fireStatus(true, true, 0, 0, 0, 5 * 60_000L),
                "low interactive");
        Assert.eq(ScheduleHealthPolicy.STATUS_NO_FIRE_WHILE_INTERACTIVE,
                ScheduleHealthPolicy.fireStatus(true, true, 0, 0, 0, 40 * 60_000L),
                "interactive no fire");

        File dir = Files.createTempDirectory("qg-health").toFile();
        DiagnosticsStore diag = new DiagnosticsStore(dir);
        TimeZone utc = TimeZone.getTimeZone("UTC");
        // 12:00 UTC on 2026-11-18 → barrier 5 still same day.
        long ts = java.time.Instant.parse("2026-11-18T12:00:00Z").toEpochMilli();
        String day = StatsStore.dayKey(ts, utc, 5);
        diag.appendScheduleEvent(new DiagnosticsStore.ScheduleEvent(
                ts, utc, DiagnosticsStore.KIND_ARM_BLOCKED, "a11y",
                "lock", 0, 0, -1, null, "a11y=0"));
        diag.appendScheduleEvent(new DiagnosticsStore.ScheduleEvent(
                ts + 1, utc, DiagnosticsStore.KIND_BOOT_SKIP, "boot",
                "lock", 0, 0, -1, null, ""));

        InteractiveMinutesStore im = new InteractiveMinutesStore(dir);
        im.addMs(day, 45L * 60L * 1000L);
        Assert.eq(45L * 60L * 1000L, im.getMs(day), "interactive persist");

        ScheduleHealthPolicy.Snapshot snap = diag.scheduleSnapshotForDay(
                day, utc, 5, false, true, im.getMs(day));
        Assert.eq(1, snap.armBlocked, "arm_blocked count");
        Assert.eq(1, snap.bootSkip, "boot_skip count");
        Assert.eq(ScheduleHealthPolicy.STATUS_A11Y_OFF, snap.fireStatus, "status a11y");
        Assert.eq(45L * 60L * 1000L, snap.interactiveMs, "interactive in snap");

        // formatStatsForDay
        StatsStore stats = new StatsStore(dir);
        stats.setDayBarrierHour(5);
        stats.appendEvent(new net.clevertree.quizgate.core.StatsEvent(
                ts, "s1", "shown", "a01", "1", 0, 100, "basic-math", "lock", false, "choice"), utc);
        stats.appendEvent(new net.clevertree.quizgate.core.StatsEvent(
                ts + 2, "s1", "correct", "a01", "1", 0, 200, "basic-math", "lock", false, "choice"), utc);
        stats.appendEvent(new net.clevertree.quizgate.core.StatsEvent(
                ts + 3, "s1", "shown", "a02", "2", 0, 100, "basic-math", "lock", false, "equation"), utc);
        stats.appendEvent(new net.clevertree.quizgate.core.StatsEvent(
                ts + 4, "s1", "wrong", "a02", "9", 1, 300, "basic-math", "lock", false, "equation"), utc);
        Map<String, StatsStore.QuestionStats> byFmt = stats.formatStatsForDay(day, utc);
        Assert.eq(1, byFmt.get("choice").correct, "choice correct");
        Assert.eq(1, byFmt.get("choice").shown, "choice shown");
        Assert.eq(1, byFmt.get("equation").wrong, "equation wrong");
        Assert.eq(1, byFmt.get("equation").shown, "equation shown");

        long barrier = StatsDayPolicy.nextBarrierMs(ts, utc, 5);
        Assert.isTrue(barrier > ts, "next barrier after noon");
        String after = StatsStore.dayKey(barrier, utc, 5);
        Assert.isTrue(!day.equals(after), "new day at barrier");

        System.out.println("ScheduleHealthPolicyTest OK");
    }
}
