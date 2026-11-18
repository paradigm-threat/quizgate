import net.clevertree.quizgate.core.DiagBreadcrumb;
import net.clevertree.quizgate.core.DiagnosticsStore;
import net.clevertree.quizgate.core.JsonUtil;
import net.clevertree.quizgate.core.StatsStore;
import net.clevertree.quizgate.core.TelemetryPayload;
import net.clevertree.quizgate.core.TelemetryRedaction;

import java.io.File;
import java.nio.file.Files;
import java.util.TimeZone;

public class DiagnosticsStoreTest {
    public static void main(String[] args) throws Exception {
        File dir = Files.createTempDirectory("qg-diag").toFile();
        DiagnosticsStore diag = new DiagnosticsStore(dir);
        StatsStore stats = new StatsStore(dir);
        TimeZone utc = TimeZone.getTimeZone("UTC");
        long now = 1_700_000_000_000L;

        diag.appendScheduleEvent(new DiagnosticsStore.ScheduleEvent(
                now, utc, DiagnosticsStore.KIND_ALARM_SCHEDULED, "interval",
                "lock", now + 60_000, 1_800_000, 900_000, null, ""));
        diag.appendScheduleEvent(new DiagnosticsStore.ScheduleEvent(
                now + 1, utc, DiagnosticsStore.KIND_ALARM_FIRED, "interval",
                "lock", now + 60_000, 1_800_000, 900_000, true, ""));
        diag.appendScheduleEvent(new DiagnosticsStore.ScheduleEvent(
                now + 2, utc, DiagnosticsStore.KIND_QUIZ_STARTED, "interval",
                "lock", now + 60_000, 0, -1, true, ""));
        diag.appendBreadcrumb(DiagBreadcrumb.builder(now + 3, DiagnosticsStore.KIND_QUIZ)
                .sessionType("lock")
                .curriculumId("basic-math")
                .questionId("m01")
                .outcome("correct")
                .latencyMs(1200)
                .missStreak(0)
                .build());
        diag.appendScheduleEvent(new DiagnosticsStore.ScheduleEvent(
                now + 4, utc, DiagnosticsStore.KIND_QUIZ_ENDED, "interval",
                "lock", now + 60_000, 0, -1, null, "correct"));
        diag.appendException(now + 5, "assert", "boom user@example.com 10.0.0.1",
                "net.clevertree.quizgate.Foo#bar:1");

        Assert.eq(1, diag.fireCountForDay(StatsStore.dayKey(now + 1, utc), utc), "fire count");
        String dayKey = StatsStore.dayKey(now + 1, utc);
        Assert.eq(0, diag.fireCountForDay("1999-1-1", utc), "no fire other day");

        // Cap: overflow schedule lines
        for (int i = 0; i < DiagnosticsStore.MAX_SCHEDULE + 50; i++) {
            diag.appendScheduleEvent(new DiagnosticsStore.ScheduleEvent(
                    now + 100 + i, utc, DiagnosticsStore.KIND_ALARM_FIRED, "interval",
                    "lock", 0, 0, -1, null, "bulk"));
        }
        diag.compact();
        Assert.isTrue(diag.recentSchedule(10_000).size() <= DiagnosticsStore.MAX_SCHEDULE,
                "schedule capped");
        Assert.isTrue(diag.approximateSizeBytes() > 0, "diag size > 0");
        Assert.isTrue(diag.summaryForLogcat().contains("diagBytes="), "summary has size");

        // Redaction
        Assert.eq("[redacted]", TelemetryRedaction.safeToken("a@b.com"), "email token");
        Assert.eq("", TelemetryRedaction.safeChoiceForUpload("hello world!"), "unsafe choice");
        Assert.eq("7", TelemetryRedaction.safeChoiceForUpload("7"), "digit choice");
        Assert.isTrue(TelemetryRedaction.safeMessage("x user@x.com y").contains("[email]"), "msg scrub");

        TelemetryPayload p = TelemetryPayload.fromStores(stats, diag, now, utc, 1, "1.0");
        String json = p.toJson();
        Assert.isTrue(json.contains("\"schema_version\":" + net.clevertree.quizgate.core.BuildFingerprint.SCHEMA_VERSION),
                "schema");
        Assert.isTrue(json.contains("\"performance\":"), "performance block");
        Assert.isTrue(json.contains("\"upload_enabled\":false"), "upload off");
        Assert.isTrue(json.contains("recent_schedule"), "has schedule");
        Assert.isTrue(json.contains(DiagnosticsStore.KIND_ALARM_FIRED)
                || json.contains("alarm_fired"), "has fire kind");
        Assert.isTrue(!json.contains("user@example.com"), "no email in payload");
        Assert.isTrue(!json.contains("10.0.0.1"), "no ip in payload");

        // Schedule line has tz
        String sched = diag.recentSchedule(1).get(0);
        Assert.isTrue(sched.contains("\"tz\""), "tz present");
        Assert.eq(DiagnosticsStore.KIND_ALARM_FIRED, JsonUtil.extract(
                diag.recentSchedule(5).stream()
                        .filter(l -> l.contains(DiagnosticsStore.KIND_ALARM_FIRED))
                        .findFirst().orElse(""), "kind"), "kind extractable");

        // --- Soft vs Hard gate_mode stamp + A/B rates (fresh store) ---
        File abDir = Files.createTempDirectory("qg-gate-ab").toFile();
        DiagnosticsStore ab = new DiagnosticsStore(abDir);
        DiagnosticsStore.ScheduleEvent softFire = new DiagnosticsStore.ScheduleEvent(
                now + 10, utc, DiagnosticsStore.KIND_ALARM_FIRED, "interval",
                "lock", 0, 0, -1, null, "soft_fire");
        softFire.gateMode = "soft";
        ab.appendScheduleEvent(softFire);
        DiagnosticsStore.ScheduleEvent softOk = new DiagnosticsStore.ScheduleEvent(
                now + 11, utc, DiagnosticsStore.KIND_LAUNCH_OK, "interval",
                "lock", 0, 0, -1, null, "soft_ok");
        softOk.gateMode = "soft";
        ab.appendScheduleEvent(softOk);
        DiagnosticsStore.ScheduleEvent hardFire = new DiagnosticsStore.ScheduleEvent(
                now + 12, utc, DiagnosticsStore.KIND_ALARM_FIRED, "interval",
                "lock", 0, 0, -1, null, "hard_fire");
        hardFire.gateMode = "hard";
        ab.appendScheduleEvent(hardFire);
        DiagnosticsStore.ScheduleEvent hardOk = new DiagnosticsStore.ScheduleEvent(
                now + 13, utc, DiagnosticsStore.KIND_LAUNCH_OK, "interval",
                "lock", 0, 0, -1, null, "hard_ok");
        hardOk.gateMode = "hard";
        ab.appendScheduleEvent(hardOk);
        DiagnosticsStore.ScheduleEvent hardRi = new DiagnosticsStore.ScheduleEvent(
                now + 14, utc, DiagnosticsStore.KIND_HARD_REINTERCEPT, "a11y",
                "lock", 0, 0, -1, null, "path=a11y");
        hardRi.gateMode = "hard";
        ab.appendScheduleEvent(hardRi);
        DiagnosticsStore.ScheduleEvent hardUnlock = new DiagnosticsStore.ScheduleEvent(
                now + 15, utc, DiagnosticsStore.KIND_HARD_UNLOCK, "hard",
                "lock", 0, 0, -1, null, "unlocked");
        hardUnlock.gateMode = "hard";
        ab.appendScheduleEvent(hardUnlock);

        String stamped = ab.recentSchedule(10).stream()
                .filter(l -> l.contains(DiagnosticsStore.KIND_HARD_UNLOCK))
                .findFirst().orElse("");
        Assert.isTrue(stamped.contains("\"gate_mode\":\"hard\""), "hard_unlock stamps gate_mode");
        String softLine = ab.recentSchedule(10).stream()
                .filter(l -> l.contains("soft_ok"))
                .findFirst().orElse("");
        Assert.isTrue(softLine.contains("\"gate_mode\":\"soft\""), "soft launch_ok stamps gate_mode");

        DiagnosticsStore.GateAbStats abStats = ab.gateAbStats(40);
        Assert.eq(1, abStats.softFired, "ab softFired");
        Assert.eq(1, abStats.softLaunchOk, "ab softLaunchOk");
        Assert.eq(1, abStats.hardFired, "ab hardFired");
        Assert.eq(1, abStats.hardLaunchOk, "ab hardLaunchOk");
        Assert.eq(1, abStats.hardReintercept, "ab hardReintercept");
        Assert.eq(1, abStats.hardUnlock, "ab hardUnlock");
        Assert.eq(100, abStats.softSuccessPct(), "soft 1/1 → 100%");
        Assert.eq(100, abStats.hardSuccessPct(), "hard 1/1 → 100%");

        // Untagged legacy lines count as soft.
        File legacyDir = Files.createTempDirectory("qg-gate-legacy").toFile();
        DiagnosticsStore legacy = new DiagnosticsStore(legacyDir);
        legacy.appendScheduleEvent(new DiagnosticsStore.ScheduleEvent(
                now + 20, utc, DiagnosticsStore.KIND_ALARM_FIRED, "interval",
                "lock", 0, 0, -1, null, "legacy"));
        DiagnosticsStore.GateAbStats legacyStats = legacy.gateAbStats(20);
        Assert.eq(1, legacyStats.softFired, "untagged → softFired");
        Assert.eq(0, legacyStats.hardFired, "untagged not hard");

        System.out.println("DiagnosticsStoreTest OK day=" + dayKey
                + " diagBytes=" + diag.approximateSizeBytes()
                + " softPct=" + abStats.softSuccessPct()
                + " hardPct=" + abStats.hardSuccessPct());
    }
}
