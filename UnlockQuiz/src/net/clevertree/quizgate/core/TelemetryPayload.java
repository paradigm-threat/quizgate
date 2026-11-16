package net.clevertree.quizgate.core;

import java.io.IOException;
import java.util.List;
import java.util.TimeZone;

/**
 * Builds a privacy-safe JSON snapshot for eventual Clevertree upload.
 * Never includes names, emails, package names, IPs, or free-text answers.
 */
public final class TelemetryPayload {
    public final int schemaVersion;
    public final long builtAt;
    public final String gitSha;
    public final int versionCode;
    public final String versionName;
    public final String deviceModel;
    public final int sdkInt;
    public final String release;
    public final String locale;
    public final String tzId;
    public final String curriculumId;
    public final long intervalMs;
    public final long jitterMs;
    public final long nextAlarmAt;
    public final boolean armed;
    public final boolean usageStats;
    public final boolean exactAlarm;
    public final boolean notifications;
    public final boolean photos;
    public final boolean otherAppPresent;
    public final StatsStore.DayStats today;
    public final long statsBytes;
    public final long diagBytes;
    public final List<String> recentQuiz;
    public final List<String> recentSchedule;
    public final List<String> recentExceptions;

    private TelemetryPayload(Builder b) {
        this.schemaVersion = BuildFingerprint.SCHEMA_VERSION;
        this.builtAt = b.builtAt;
        this.gitSha = b.gitSha;
        this.versionCode = b.versionCode;
        this.versionName = b.versionName;
        this.deviceModel = b.deviceModel;
        this.sdkInt = b.sdkInt;
        this.release = b.release;
        this.locale = b.locale;
        this.tzId = b.tzId;
        this.curriculumId = b.curriculumId;
        this.intervalMs = b.intervalMs;
        this.jitterMs = b.jitterMs;
        this.nextAlarmAt = b.nextAlarmAt;
        this.armed = b.armed;
        this.usageStats = b.usageStats;
        this.exactAlarm = b.exactAlarm;
        this.notifications = b.notifications;
        this.photos = b.photos;
        this.otherAppPresent = b.otherAppPresent;
        this.today = b.today;
        this.statsBytes = b.statsBytes;
        this.diagBytes = b.diagBytes;
        this.recentQuiz = b.recentQuiz;
        this.recentSchedule = b.recentSchedule;
        this.recentExceptions = b.recentExceptions;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\"schema_version\":").append(schemaVersion);
        sb.append(",\"built_at\":").append(builtAt);
        sb.append(",\"build\":{");
        sb.append("\"version_code\":").append(versionCode);
        sb.append(",\"version_name\":\"").append(JsonUtil.esc(TelemetryRedaction.safeToken(versionName))).append('"');
        sb.append(",\"git_sha\":\"").append(JsonUtil.esc(TelemetryRedaction.safeToken(gitSha))).append('"');
        sb.append(",\"upload_enabled\":").append(BuildFingerprint.TELEMETRY_UPLOAD_ENABLED);
        sb.append('}');
        sb.append(",\"device\":{");
        sb.append("\"model\":\"").append(JsonUtil.esc(TelemetryRedaction.safeToken(deviceModel))).append('"');
        sb.append(",\"sdk\":").append(sdkInt);
        sb.append(",\"release\":\"").append(JsonUtil.esc(TelemetryRedaction.safeToken(release))).append('"');
        sb.append(",\"locale\":\"").append(JsonUtil.esc(TelemetryRedaction.safeToken(locale))).append('"');
        sb.append(",\"tz\":\"").append(JsonUtil.esc(TelemetryRedaction.safeToken(tzId))).append('"');
        sb.append('}');
        sb.append(",\"prefs\":{");
        sb.append("\"curriculum_id\":\"").append(JsonUtil.esc(TelemetryRedaction.safeToken(curriculumId))).append('"');
        sb.append(",\"interval_ms\":").append(intervalMs);
        sb.append(",\"jitter_ms\":").append(jitterMs);
        sb.append(",\"next_alarm_at\":").append(nextAlarmAt);
        sb.append(",\"armed\":").append(armed);
        sb.append('}');
        sb.append(",\"permissions\":{");
        sb.append("\"usage_stats\":").append(usageStats);
        sb.append(",\"exact_alarm\":").append(exactAlarm);
        sb.append(",\"notifications\":").append(notifications);
        sb.append(",\"photos\":").append(photos);
        sb.append('}');
        sb.append(",\"gate\":{\"other_app\":").append(otherAppPresent).append('}');
        sb.append(",\"storage\":{\"stats_bytes\":").append(statsBytes)
                .append(",\"diag_bytes\":").append(diagBytes).append('}');
        if (today != null) {
            sb.append(",\"today\":{");
            sb.append("\"asked\":").append(today.questionsAsked);
            sb.append(",\"right\":").append(today.answeredRight);
            sb.append(",\"wrong\":").append(today.answeredWrong);
            sb.append(",\"skipped\":").append(today.skipped);
            sb.append('}');
        }
        appendJsonlArray(sb, "recent_quiz", recentQuiz);
        appendJsonlArray(sb, "recent_schedule", recentSchedule);
        appendJsonlArray(sb, "recent_exceptions", recentExceptions);
        sb.append('}');
        return sb.toString();
    }

    private static void appendJsonlArray(StringBuilder sb, String key, List<String> lines) {
        sb.append(",\"").append(key).append("\":[");
        if (lines != null) {
            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) sb.append(',');
                // Lines are already JSON objects
                sb.append(lines.get(i));
            }
        }
        sb.append(']');
    }

    public static Builder builder(long builtAt) {
        return new Builder(builtAt);
    }

    /**
     * Assemble payload from local stores (core-only fields; Android fills device/perms).
     */
    public static TelemetryPayload fromStores(StatsStore stats, DiagnosticsStore diag,
                                              long nowMs, TimeZone zone,
                                              int versionCode, String versionName) throws IOException {
        Builder b = builder(nowMs)
                .gitSha(BuildFingerprint.gitSha())
                .versionCode(versionCode)
                .versionName(versionName)
                .tzId(zone != null ? zone.getID() : TimeZone.getDefault().getID())
                .today(stats.dayStats(nowMs, zone))
                .statsBytes(stats.approximateSizeBytes())
                .diagBytes(diag.approximateSizeBytes())
                .recentQuiz(stats.recentEventLines(DiagnosticsStore.RECENT_QUIZ_DEFAULT))
                .recentSchedule(diag.recentSchedule(40))
                .recentExceptions(diag.recentExceptions(20));
        return b.build();
    }

    public static final class Builder {
        private final long builtAt;
        private String gitSha = "unknown";
        private int versionCode;
        private String versionName = "";
        private String deviceModel = "";
        private int sdkInt;
        private String release = "";
        private String locale = "";
        private String tzId = "";
        private String curriculumId = "";
        private long intervalMs;
        private long jitterMs;
        private long nextAlarmAt;
        private boolean armed;
        private boolean usageStats;
        private boolean exactAlarm;
        private boolean notifications;
        private boolean photos;
        private boolean otherAppPresent;
        private StatsStore.DayStats today;
        private long statsBytes;
        private long diagBytes;
        private List<String> recentQuiz;
        private List<String> recentSchedule;
        private List<String> recentExceptions;

        private Builder(long builtAt) { this.builtAt = builtAt; }

        public Builder gitSha(String v) { this.gitSha = v; return this; }
        public Builder versionCode(int v) { this.versionCode = v; return this; }
        public Builder versionName(String v) { this.versionName = v; return this; }
        public Builder deviceModel(String v) { this.deviceModel = v; return this; }
        public Builder sdkInt(int v) { this.sdkInt = v; return this; }
        public Builder release(String v) { this.release = v; return this; }
        public Builder locale(String v) { this.locale = v; return this; }
        public Builder tzId(String v) { this.tzId = v; return this; }
        public Builder curriculumId(String v) { this.curriculumId = v; return this; }
        public Builder intervalMs(long v) { this.intervalMs = v; return this; }
        public Builder jitterMs(long v) { this.jitterMs = v; return this; }
        public Builder nextAlarmAt(long v) { this.nextAlarmAt = v; return this; }
        public Builder armed(boolean v) { this.armed = v; return this; }
        public Builder usageStats(boolean v) { this.usageStats = v; return this; }
        public Builder exactAlarm(boolean v) { this.exactAlarm = v; return this; }
        public Builder notifications(boolean v) { this.notifications = v; return this; }
        public Builder photos(boolean v) { this.photos = v; return this; }
        public Builder otherAppPresent(boolean v) { this.otherAppPresent = v; return this; }
        public Builder today(StatsStore.DayStats v) { this.today = v; return this; }
        public Builder statsBytes(long v) { this.statsBytes = v; return this; }
        public Builder diagBytes(long v) { this.diagBytes = v; return this; }
        public Builder recentQuiz(List<String> v) { this.recentQuiz = v; return this; }
        public Builder recentSchedule(List<String> v) { this.recentSchedule = v; return this; }
        public Builder recentExceptions(List<String> v) { this.recentExceptions = v; return this; }

        public TelemetryPayload build() { return new TelemetryPayload(this); }
    }
}
