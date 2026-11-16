package net.clevertree.quizgate.core;

public final class StatsEvent {
    public final long ts;
    public final String sessionId;
    public final String type;
    public final String questionId;
    public final String choice;
    public final int missStreak;

    public StatsEvent(long ts, String sessionId, String type, String questionId, String choice, int missStreak) {
        this.ts = ts;
        this.sessionId = sessionId;
        this.type = type;
        this.questionId = questionId == null ? "" : questionId;
        this.choice = choice == null ? "" : choice;
        this.missStreak = missStreak;
    }

    public String toJsonLine() {
        return "{\"ts\":" + ts
                + ",\"session_id\":\"" + esc(sessionId) + "\""
                + ",\"type\":\"" + esc(type) + "\""
                + ",\"question_id\":\"" + esc(questionId) + "\""
                + ",\"choice\":\"" + esc(choice) + "\""
                + ",\"miss_streak\":" + missStreak
                + "}";
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
