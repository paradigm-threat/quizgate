package net.clevertree.quizgate.core;

/** Minimal JSON helpers shared by stats + diagnostics (no Android deps). */
public final class JsonUtil {
    private JsonUtil() {}

    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static String extract(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int i = json.indexOf(needle);
        if (i < 0) return "";
        int s = i + needle.length();
        int e = json.indexOf('"', s);
        return e < 0 ? "" : json.substring(s, e);
    }

    public static long extractLong(String json, String key) {
        String needle = "\"" + key + "\":";
        int i = json.indexOf(needle);
        if (i < 0) return -1;
        int s = i + needle.length();
        while (s < json.length() && json.charAt(s) == ' ') s++;
        int e = s;
        if (e < json.length() && json.charAt(e) == '-') e++;
        while (e < json.length()) {
            char c = json.charAt(e);
            if (c >= '0' && c <= '9') e++;
            else break;
        }
        if (e == s || (e == s + 1 && json.charAt(s) == '-')) return -1;
        try {
            return Long.parseLong(json.substring(s, e));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    public static boolean extractBool(String json, String key, boolean def) {
        String needle = "\"" + key + "\":";
        int i = json.indexOf(needle);
        if (i < 0) return def;
        int s = i + needle.length();
        while (s < json.length() && json.charAt(s) == ' ') s++;
        if (json.startsWith("true", s)) return true;
        if (json.startsWith("false", s)) return false;
        return def;
    }
}
