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
        Assert.isTrue(json.contains("\"schema_version\":2"), "schema");
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

        System.out.println("DiagnosticsStoreTest OK day=" + dayKey
                + " diagBytes=" + diag.approximateSizeBytes());
    }
}
