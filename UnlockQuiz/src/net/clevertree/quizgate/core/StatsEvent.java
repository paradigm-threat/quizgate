package net.clevertree.quizgate.core;

/**
 * One quiz event line for {@code events.jsonl}.
 * Prefer question ids + outcome types; choice is kept for local debugging but
 * free-text is scrubbed via {@link TelemetryRedaction}.
 */
public final class StatsEvent {
    public final long ts;
    public final String sessionId;
    public final String type;
    public final String questionId;
    public final String choice;
    public final int missStreak;
    public final long latencyMs;
    public final String curriculumId;
    public final String sessionType;

    public StatsEvent(long ts, String sessionId, String type, String questionId, String choice, int missStreak) {
        this(ts, sessionId, type, questionId, choice, missStreak, -1L, "", "");
    }

    public StatsEvent(long ts, String sessionId, String type, String questionId, String choice,
                      int missStreak, long latencyMs, String curriculumId, String sessionType) {
        this.ts = ts;
        this.sessionId = sessionId == null ? "" : sessionId;
        this.type = type == null ? "" : type;
        this.questionId = questionId == null ? "" : questionId;
        this.choice = choice == null ? "" : choice;
        this.missStreak = missStreak;
        this.latencyMs = latencyMs;
        this.curriculumId = curriculumId == null ? "" : curriculumId;
        this.sessionType = sessionType == null ? "" : sessionType;
    }

    public String toJsonLine() {
        StringBuilder sb = new StringBuilder(128);
        sb.append("{\"ts\":").append(ts);
        sb.append(",\"session_id\":\"").append(JsonUtil.esc(sessionId)).append('"');
        sb.append(",\"type\":\"").append(JsonUtil.esc(type)).append('"');
        sb.append(",\"question_id\":\"").append(JsonUtil.esc(TelemetryRedaction.safeToken(questionId))).append('"');
        String safeChoice = TelemetryRedaction.safeChoiceForUpload(choice);
        sb.append(",\"choice\":\"").append(JsonUtil.esc(safeChoice)).append('"');
        sb.append(",\"miss_streak\":").append(missStreak);
        if (latencyMs >= 0) sb.append(",\"latency_ms\":").append(latencyMs);
        if (!curriculumId.isEmpty()) {
            sb.append(",\"curriculum_id\":\"")
                    .append(JsonUtil.esc(TelemetryRedaction.safeToken(curriculumId))).append('"');
        }
        if (!sessionType.isEmpty()) {
            sb.append(",\"session_type\":\"")
                    .append(JsonUtil.esc(TelemetryRedaction.safeToken(sessionType))).append('"');
        }
        sb.append('}');
        return sb.toString();
    }
}
