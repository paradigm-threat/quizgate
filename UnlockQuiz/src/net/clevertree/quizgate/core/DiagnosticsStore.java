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
 * {@code skip_fire}, {@code arm}, {@code hard_reintercept}, {@code hard_unlock}.
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
    /** Auto performance↔slim switch (structured reasons in note/msg). */
    public static final String KIND_PERF_MODE = "perf_mode";
    /** Hard gate: Accessibility brought quiz back after leave attempt. */
    public static final String KIND_HARD_REINTERCEPT = "hard_reintercept";
    /** Hard gate: session cleared (unlock / consoling / parent override). */
    public static final String KIND_HARD_UNLOCK = "hard_unlock";

    private final File diagDir;
    private final BoundedJsonl breadcrumbs;
    private final BoundedJsonl schedule;
    private final BoundedJsonl exceptions;
    private final boolean mirrorScheduleToBreadcrumbs;
    private final List<String> memoryBreadcrumbs = new ArrayList<>();
    private final List<String> memoryExceptions = new ArrayList<>();

    public DiagnosticsStore(File statsDir) {
        this(statsDir, 32, true);
    }

    /**
     * @param compactEvery appends before opportunistic compaction
     * @param mirrorScheduleToBreadcrumbs when false (slim), schedule JSONL still
     *        records fires/arms but skips the dual-write into breadcrumbs
     */
    public DiagnosticsStore(File statsDir, int compactEvery, boolean mirrorScheduleToBreadcrumbs) {
        if (!statsDir.exists()) statsDir.mkdirs();
        this.diagDir = new File(statsDir, "diagnostics");
        if (!diagDir.exists()) diagDir.mkdirs();
        int every = Math.max(1, compactEvery);
        this.mirrorScheduleToBreadcrumbs = mirrorScheduleToBreadcrumbs;
        this.breadcrumbs = new BoundedJsonl(new File(diagDir, "breadcrumbs.jsonl"),
                MAX_BREADCRUMBS, MAX_BREADCRUMB_BYTES, every);
        this.schedule = new BoundedJsonl(new File(diagDir, "schedule.jsonl"),
                MAX_SCHEDULE, MAX_SCHEDULE_BYTES, every);
        this.exceptions = new BoundedJsonl(new File(diagDir, "exceptions.jsonl"),
                MAX_EXCEPTIONS, MAX_EXCEPTION_BYTES, every);
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
        if (!mirrorScheduleToBreadcrumbs) return;
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
        /** Soft vs hard; stamped by {@code Diag.schedule} when empty. */
        public String gateMode = "";

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
            StringBuilder sb = new StringBuilder(200);
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
            if (gateMode != null && !gateMode.isEmpty()) {
                sb.append(",\"gate_mode\":\"").append(JsonUtil.esc(TelemetryRedaction.safeToken(gateMode))).append('"');
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

    /**
     * Local Soft vs Hard success rates from recent schedule JSONL (no upload).
     * Soft: launch_ok / alarm_fired (or unlocked quiz_ended vs bypass).
     * Hard: hard_unlock / (hard_unlock + hard_reintercept) when present, else launch_ok / alarm_fired.
     */
    public static final class GateAbStats {
        public final int softFired;
        public final int softLaunchOk;
        public final int softUnlocked;
        public final int softBypass;
        public final int hardFired;
        public final int hardLaunchOk;
        public final int hardUnlock;
        public final int hardReintercept;

        public GateAbStats(int softFired, int softLaunchOk, int softUnlocked, int softBypass,
                           int hardFired, int hardLaunchOk, int hardUnlock, int hardReintercept) {
            this.softFired = softFired;
            this.softLaunchOk = softLaunchOk;
            this.softUnlocked = softUnlocked;
            this.softBypass = softBypass;
            this.hardFired = hardFired;
            this.hardLaunchOk = hardLaunchOk;
            this.hardUnlock = hardUnlock;
            this.hardReintercept = hardReintercept;
        }

        /** Soft success percent, or -1 if insufficient data. */
        public int softSuccessPct() {
            if (softFired > 0) return Math.min(100, Math.round(100f * softLaunchOk / softFired));
            int softEnds = softUnlocked + softBypass;
            if (softEnds > 0) return Math.min(100, Math.round(100f * softUnlocked / softEnds));
            return -1;
        }

        /** Hard success percent, or -1 if insufficient data. Prefer launch_ok / fired. */
        public int hardSuccessPct() {
            if (hardFired > 0) {
                return Math.min(100, Math.round(100f * hardLaunchOk / hardFired));
            }
            // No fires yet: if we saw unlocks, treat as success signal for local A/B.
            if (hardUnlock > 0) return 100;
            return -1;
        }
    }

    public synchronized GateAbStats gateAbStats(int maxLines) throws IOException {
        int softFired = 0, softLaunchOk = 0, softUnlocked = 0, softBypass = 0;
        int hardFired = 0, hardLaunchOk = 0, hardUnlock = 0, hardReintercept = 0;
        int n = Math.max(40, maxLines);
        for (String line : schedule.tail(n)) {
            String kind = JsonUtil.extract(line, "kind");
            String mode = JsonUtil.extract(line, "gate_mode");
            boolean hard = "hard".equalsIgnoreCase(mode);
            boolean soft = "soft".equalsIgnoreCase(mode);
            // Untagged legacy lines count toward soft (pre-dual-gate path).
            if (!hard && !soft) soft = true;
            if (KIND_ALARM_FIRED.equals(kind) || "fire".equals(kind)) {
                if (hard) hardFired++; else softFired++;
            } else if (KIND_LAUNCH_OK.equals(kind)) {
                if (hard) hardLaunchOk++; else softLaunchOk++;
            } else if (KIND_HARD_UNLOCK.equals(kind)) {
                hardUnlock++;
            } else if (KIND_HARD_REINTERCEPT.equals(kind)) {
                hardReintercept++;
            } else if (KIND_QUIZ_ENDED.equals(kind)) {
                String note = JsonUtil.extract(line, "note");
                if ("bypass".equalsIgnoreCase(note)) {
                    if (soft) softBypass++;
                } else if ("unlocked".equalsIgnoreCase(note) || "correct".equalsIgnoreCase(note)
                        || "consoling".equalsIgnoreCase(note)
                        || "consoling_unlock".equalsIgnoreCase(note)) {
                    if (soft) softUnlocked++;
                }
            }
        }
        return new GateAbStats(softFired, softLaunchOk, softUnlocked, softBypass,
                hardFired, hardLaunchOk, hardUnlock, hardReintercept);
    }
}
