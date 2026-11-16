package net.clevertree.quizgate.core;

/**
 * One privacy-safe diagnostic breadcrumb (quiz / schedule / permission / media / error).
 * No question text, no free-text answers, no package names, no PII.
 */
public final class DiagBreadcrumb {
    public final long ts;
    public final String kind;
    public final String sessionType;
    public final String curriculumId;
    public final String questionId;
    public final String outcome;
    public final long latencyMs;
    public final int missStreak;
    public final String trigger;
    public final long nextAlarmAt;
    public final long intervalMs;
    public final long jitterMs;
    public final Boolean usageStatsGranted;
    public final Boolean exactAlarmGranted;
    public final Boolean mediaFocusHeld;
    public final Boolean otherAppPresent;
    public final String message;

    private DiagBreadcrumb(Builder b) {
        this.ts = b.ts;
        this.kind = b.kind == null ? "" : b.kind;
        this.sessionType = b.sessionType == null ? "" : b.sessionType;
        this.curriculumId = b.curriculumId == null ? "" : b.curriculumId;
        this.questionId = b.questionId == null ? "" : b.questionId;
        this.outcome = b.outcome == null ? "" : b.outcome;
        this.latencyMs = b.latencyMs;
        this.missStreak = b.missStreak;
        this.trigger = b.trigger == null ? "" : b.trigger;
        this.nextAlarmAt = b.nextAlarmAt;
        this.intervalMs = b.intervalMs;
        this.jitterMs = b.jitterMs;
        this.usageStatsGranted = b.usageStatsGranted;
        this.exactAlarmGranted = b.exactAlarmGranted;
        this.mediaFocusHeld = b.mediaFocusHeld;
        this.otherAppPresent = b.otherAppPresent;
        this.message = b.message == null ? "" : b.message;
    }

    public String toJsonLine() {
        StringBuilder sb = new StringBuilder(160);
        sb.append("{\"ts\":").append(ts);
        sb.append(",\"kind\":\"").append(JsonUtil.esc(TelemetryRedaction.safeToken(kind))).append('"');
        appendStr(sb, "session_type", sessionType);
        appendStr(sb, "curriculum_id", curriculumId);
        appendStr(sb, "question_id", questionId);
        appendStr(sb, "outcome", outcome);
        if (latencyMs >= 0) sb.append(",\"latency_ms\":").append(latencyMs);
        if (missStreak >= 0) sb.append(",\"miss_streak\":").append(missStreak);
        appendStr(sb, "trigger", trigger);
        if (nextAlarmAt > 0) sb.append(",\"next_alarm_at\":").append(nextAlarmAt);
        if (intervalMs > 0) sb.append(",\"interval_ms\":").append(intervalMs);
        if (jitterMs >= 0 && intervalMs > 0) sb.append(",\"jitter_ms\":").append(jitterMs);
        appendBool(sb, "usage_stats", usageStatsGranted);
        appendBool(sb, "exact_alarm", exactAlarmGranted);
        appendBool(sb, "media_focus", mediaFocusHeld);
        appendBool(sb, "other_app", otherAppPresent);
        if (!message.isEmpty()) {
            sb.append(",\"msg\":\"").append(JsonUtil.esc(TelemetryRedaction.safeMessage(message))).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private static void appendStr(StringBuilder sb, String key, String val) {
        if (val == null || val.isEmpty()) return;
        sb.append(",\"").append(key).append("\":\"")
                .append(JsonUtil.esc(TelemetryRedaction.safeToken(val))).append('"');
    }

    private static void appendBool(StringBuilder sb, String key, Boolean v) {
        if (v == null) return;
        sb.append(",\"").append(key).append("\":").append(v ? "true" : "false");
    }

    public static Builder builder(long ts, String kind) {
        return new Builder(ts, kind);
    }

    public static final class Builder {
        private final long ts;
        private final String kind;
        private String sessionType = "";
        private String curriculumId = "";
        private String questionId = "";
        private String outcome = "";
        private long latencyMs = -1;
        private int missStreak = -1;
        private String trigger = "";
        private long nextAlarmAt = 0;
        private long intervalMs = 0;
        private long jitterMs = -1;
        private Boolean usageStatsGranted;
        private Boolean exactAlarmGranted;
        private Boolean mediaFocusHeld;
        private Boolean otherAppPresent;
        private String message = "";

        private Builder(long ts, String kind) {
            this.ts = ts;
            this.kind = kind;
        }

        public Builder sessionType(String v) { this.sessionType = v; return this; }
        public Builder curriculumId(String v) { this.curriculumId = v; return this; }
        public Builder questionId(String v) { this.questionId = v; return this; }
        public Builder outcome(String v) { this.outcome = v; return this; }
        public Builder latencyMs(long v) { this.latencyMs = v; return this; }
        public Builder missStreak(int v) { this.missStreak = v; return this; }
        public Builder trigger(String v) { this.trigger = v; return this; }
        public Builder nextAlarmAt(long v) { this.nextAlarmAt = v; return this; }
        public Builder intervalMs(long v) { this.intervalMs = v; return this; }
        public Builder jitterMs(long v) { this.jitterMs = v; return this; }
        public Builder usageStats(Boolean v) { this.usageStatsGranted = v; return this; }
        public Builder exactAlarm(Boolean v) { this.exactAlarmGranted = v; return this; }
        public Builder mediaFocus(Boolean v) { this.mediaFocusHeld = v; return this; }
        public Builder otherApp(Boolean v) { this.otherAppPresent = v; return this; }
        public Builder message(String v) { this.message = v; return this; }

        public DiagBreadcrumb build() { return new DiagBreadcrumb(this); }
    }
}
