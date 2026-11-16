package net.clevertree.quizgate.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * Local privacy-safe diagnostics under {@code files/stats/diagnostics/}.
 * Ring-buffered JSONL (line + byte caps). No network I/O.
 *
 * <p>Schedule kinds: {@code alarm_scheduled}, {@code alarm_fired}, {@code launch_attempt},
 * {@code bal_blocked}, {@code launch_ok}, {@code quiz_started}, {@code quiz_ended},
 * {@code skip_fire}, {@code arm}.
 */
public final class DiagnosticsStore {
    public static final int MAX_BREADCRUMBS = 500;
    public static final long MAX_BREADCRUMB_BYTES = 256L * 1024L;
    public static final int MAX_SCHEDULE = 500;
    public static final long MAX_SCHEDULE_BYTES = 256L * 1024L;
    public static final int MAX_EXCEPTIONS = 100;
    public static final long MAX_EXCEPTION_BYTES = 64L * 1024L;
    public static final int RECENT_QUIZ_DEFAULT = 40;

    public static final String KIND_ALARM_SCHEDULED = "alarm_scheduled";
    public static final String KIND_ALARM_FIRED = "alarm_fired";
    public static final String KIND_LAUNCH_ATTEMPT = "launch_attempt";
    public static final String KIND_BAL_BLOCKED = "bal_blocked";
    public static final String KIND_LAUNCH_OK = "launch_ok";
    public static final String KIND_QUIZ_STARTED = "quiz_started";
    public static final String KIND_QUIZ_ENDED = "quiz_ended";
    public static final String KIND_SKIP_FIRE = "skip_fire";
    public static final String KIND_ARM = "arm";
    public static final String KIND_QUIZ = "quiz";
    public static final String KIND_PERM = "perm";
    public static final String KIND_MEDIA = "media";

    private final File diagDir;
    private final BoundedJsonl breadcrumbs;
    private final BoundedJsonl schedule;
    private final BoundedJsonl exceptions;
    private final List<String> memoryBreadcrumbs = new ArrayList<>();
    private final List<String> memoryExceptions = new ArrayList<>();

    public DiagnosticsStore(File statsDir) {
        if (!statsDir.exists()) statsDir.mkdirs();
        this.diagDir = new File(statsDir, "diagnostics");
        if (!diagDir.exists()) diagDir.mkdirs();
        this.breadcrumbs = new BoundedJsonl(new File(diagDir, "breadcrumbs.jsonl"),
                MAX_BREADCRUMBS, MAX_BREADCRUMB_BYTES);
        this.schedule = new BoundedJsonl(new File(diagDir, "schedule.jsonl"),
                MAX_SCHEDULE, MAX_SCHEDULE_BYTES);
        this.exceptions = new BoundedJsonl(new File(diagDir, "exceptions.jsonl"),
                MAX_EXCEPTIONS, MAX_EXCEPTION_BYTES);
    }

    public File getDiagDir() { return diagDir; }
    public File getBreadcrumbsFile() { return breadcrumbs.file(); }
    public File getScheduleFile() { return schedule.file(); }
    public File getExceptionsFile() { return exceptions.file(); }

    public synchronized void appendBreadcrumb(DiagBreadcrumb b) throws IOException {
        String line = b.toJsonLine();
        breadcrumbs.append(line);
        pushMemory(memoryBreadcrumbs, line, 48);
    }

    public synchronized void appendScheduleEvent(ScheduleEvent e) throws IOException {
        schedule.append(e.toJsonLine());
        DiagBreadcrumb.Builder b = DiagBreadcrumb.builder(e.ts, e.kind)
                .trigger(e.trigger)
                .sessionType(e.sessionType)
                .nextAlarmAt(e.nextAlarmAt)
                .intervalMs(e.intervalMs)
                .jitterMs(e.jitterMs)
                .message(e.note);
        if (e.otherAppPresent != null) b.otherApp(e.otherAppPresent);
        appendBreadcrumb(b.build());
    }

    public synchronized void appendException(long ts, String kind, String message, String topFrame)
            throws IOException {
        String line = "{\"ts\":" + ts
                + ",\"kind\":\"" + JsonUtil.esc(TelemetryRedaction.safeToken(kind)) + "\""
                + ",\"msg\":\"" + JsonUtil.esc(TelemetryRedaction.safeMessage(message)) + "\""
                + ",\"frame\":\"" + JsonUtil.esc(TelemetryRedaction.safeMessage(topFrame)) + "\""
                + "}";
        exceptions.append(line);
        pushMemory(memoryExceptions, line, 16);
    }

    public synchronized void compact() throws IOException {
        breadcrumbs.compact();
        schedule.compact();
        exceptions.compact();
    }

    public synchronized long approximateSizeBytes() {
        return breadcrumbs.sizeBytes() + schedule.sizeBytes() + exceptions.sizeBytes();
    }

    public synchronized List<String> recentBreadcrumbs(int n) throws IOException {
        return breadcrumbs.tail(n);
    }

    public synchronized List<String> recentSchedule(int n) throws IOException {
        return schedule.tail(n);
    }

    public synchronized List<String> recentExceptions(int n) throws IOException {
        return exceptions.tail(n);
    }

    public synchronized String summaryForLogcat() {
        StringBuilder sb = new StringBuilder(160);
        sb.append("diagBytes=").append(approximateSizeBytes());
        sb.append(" bc=").append(memoryBreadcrumbs.size());
        sb.append(" ex=").append(memoryExceptions.size());
        if (!memoryBreadcrumbs.isEmpty()) {
            String last = memoryBreadcrumbs.get(memoryBreadcrumbs.size() - 1);
            String kind = JsonUtil.extract(last, "kind");
            long ts = JsonUtil.extractLong(last, "ts");
            sb.append(" last=").append(kind).append('@').append(ts);
        }
        if (!memoryExceptions.isEmpty()) {
            String last = memoryExceptions.get(memoryExceptions.size() - 1);
            sb.append(" lastEx=").append(JsonUtil.extract(last, "kind"));
        }
        return sb.toString();
    }

    public int fireCountForDay(String dayKey, TimeZone zone) throws IOException {
        if (dayKey == null || dayKey.isEmpty()) return 0;
        int n = 0;
        for (String line : schedule.readAll()) {
            String kind = JsonUtil.extract(line, "kind");
            if (!KIND_ALARM_FIRED.equals(kind) && !"fire".equals(kind)) continue;
            long ts = JsonUtil.extractLong(line, "ts");
            if (ts <= 0) continue;
            if (dayKey.equals(StatsStore.dayKey(ts, zone))) n++;
        }
        return n;
    }

    private static void pushMemory(List<String> ring, String line, int cap) {
        ring.add(line);
        while (ring.size() > cap) ring.remove(0);
    }

    public static final class ScheduleEvent {
        public final long ts;
        public final String tzId;
        public final int tzOffsetMinutes;
        public final String kind;
        public final String trigger;
        public final String sessionType;
        public final long nextAlarmAt;
        public final long intervalMs;
        public final long jitterMs;
        public final Boolean otherAppPresent;
        public final String note;

        public ScheduleEvent(long ts, TimeZone zone, String kind, String trigger,
                             String sessionType, long nextAlarmAt, long intervalMs, long jitterMs,
                             Boolean otherAppPresent, String note) {
            this.ts = ts;
            TimeZone z = zone == null ? TimeZone.getDefault() : zone;
            this.tzId = z.getID() == null ? "unknown" : z.getID();
            this.tzOffsetMinutes = z.getOffset(ts) / 60_000;
            this.kind = kind == null ? "" : kind;
            this.trigger = trigger == null ? "" : trigger;
            this.sessionType = sessionType == null ? "" : sessionType;
            this.nextAlarmAt = nextAlarmAt;
            this.intervalMs = intervalMs;
            this.jitterMs = jitterMs;
            this.otherAppPresent = otherAppPresent;
            this.note = note == null ? "" : note;
        }

        public String toJsonLine() {
            StringBuilder sb = new StringBuilder(180);
            sb.append("{\"ts\":").append(ts);
            sb.append(",\"tz\":\"").append(JsonUtil.esc(TelemetryRedaction.safeToken(tzId))).append('"');
            sb.append(",\"tz_offset_min\":").append(tzOffsetMinutes);
            sb.append(",\"kind\":\"").append(JsonUtil.esc(TelemetryRedaction.safeToken(kind))).append('"');
            if (!trigger.isEmpty()) {
                sb.append(",\"trigger\":\"").append(JsonUtil.esc(TelemetryRedaction.safeToken(trigger))).append('"');
            }
            if (!sessionType.isEmpty()) {
                sb.append(",\"session_type\":\"").append(JsonUtil.esc(TelemetryRedaction.safeToken(sessionType))).append('"');
            }
            if (nextAlarmAt > 0) sb.append(",\"next_alarm_at\":").append(nextAlarmAt);
            if (intervalMs > 0) sb.append(",\"interval_ms\":").append(intervalMs);
            if (jitterMs >= 0 && (intervalMs > 0
                    || KIND_ALARM_SCHEDULED.equals(kind) || KIND_ARM.equals(kind))) {
                sb.append(",\"jitter_ms\":").append(jitterMs);
            }
            if (otherAppPresent != null) {
                sb.append(",\"other_app\":").append(otherAppPresent ? "true" : "false");
            }
            if (!note.isEmpty()) {
                sb.append(",\"note\":\"").append(JsonUtil.esc(TelemetryRedaction.safeMessage(note))).append('"');
            }
            sb.append('}');
            return sb.toString();
        }
    }
}
