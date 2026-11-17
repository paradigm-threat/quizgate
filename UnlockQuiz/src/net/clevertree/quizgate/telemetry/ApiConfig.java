package net.clevertree.quizgate.telemetry;

/** Public QuizGate API hosts — always *.quizgate.app. */
public final class ApiConfig {
    public static final String API_BASE = "https://api.quizgate.app";

    private ApiConfig() {}

    public static String userProfileUrl(String uuid) {
        return API_BASE + "/v1/users/" + uuid + "/profile";
    }

    public static String userSyncUrl(String uuid) {
        return API_BASE + "/v1/users/" + uuid + "/sync";
    }

    public static String realtimeUrl(String uuid, String kind) {
        return API_BASE + "/v1/users/" + uuid + "/realtime/" + kind;
    }
}
