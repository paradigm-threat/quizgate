package net.clevertree.quizgate.core;

import java.util.ArrayList;
import java.util.List;

/** Parses curricula index.json (no Android). */
public final class CurriculumIndexParse {
    private CurriculumIndexParse() {}

    public static final class Entry {
        public final String id;
        public final String title;
        public final int ageMin;
        public final int ageMax;
        public final int difficultyRank;
        public final int version;
        public final String packUrl;
        public final String contentSha256;
        public final boolean bundled;

        public Entry(String id, String title, int ageMin, int ageMax, int difficultyRank,
                     int version, String packUrl, String contentSha256, boolean bundled) {
            this.id = id;
            this.title = title;
            this.ageMin = ageMin;
            this.ageMax = ageMax;
            this.difficultyRank = difficultyRank;
            this.version = version;
            this.packUrl = packUrl;
            this.contentSha256 = contentSha256;
            this.bundled = bundled;
        }
    }

    public static List<Entry> parse(String json) {
        List<Entry> out = new ArrayList<>();
        if (json == null) return out;
        String needle = "\"curricula\"";
        int k = json.indexOf(needle);
        if (k < 0) return out;
        int lb = json.indexOf('[', k);
        int depth = 0;
        int end = -1;
        for (int i = lb; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) {
                    end = i;
                    break;
                }
            }
        }
        if (lb < 0 || end < 0) return out;
        String inner = json.substring(lb + 1, end);
        for (String obj : splitObjects(inner)) {
            out.add(new Entry(
                    str(obj, "id"),
                    strOpt(obj, "title", str(obj, "id")),
                    intOpt(obj, "ageMin", 0),
                    intOpt(obj, "ageMax", 99),
                    intOpt(obj, "difficultyRank", 100),
                    intOpt(obj, "version", 1),
                    strOpt(obj, "packUrl", ""),
                    strOpt(obj, "contentSha256", ""),
                    boolOpt(obj, "bundled", false)));
        }
        out.sort((a, b) -> Integer.compare(a.difficultyRank, b.difficultyRank));
        return out;
    }

    private static List<String> splitObjects(String arrInner) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < arrInner.length()) {
            while (i < arrInner.length() && arrInner.charAt(i) != '{') i++;
            if (i >= arrInner.length()) break;
            int start = i;
            int depth = 0;
            for (; i < arrInner.length(); i++) {
                char c = arrInner.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        i++;
                        out.add(arrInner.substring(start, i));
                        break;
                    }
                }
            }
        }
        return out;
    }

    private static String str(String obj, String key) {
        String v = strOpt(obj, key, null);
        if (v == null) throw new IllegalArgumentException("missing " + key);
        return v;
    }

    private static String strOpt(String obj, String key, String def) {
        String needle = "\"" + key + "\"";
        int k = obj.indexOf(needle);
        if (k < 0) return def;
        int colon = obj.indexOf(':', k + needle.length());
        int p = colon + 1;
        while (p < obj.length() && Character.isWhitespace(obj.charAt(p))) p++;
        if (p < obj.length() && obj.startsWith("null", p)) return def;
        int q1 = obj.indexOf('"', colon + 1);
        if (q1 < 0) return def;
        StringBuilder sb = new StringBuilder();
        for (int i = q1 + 1; i < obj.length(); i++) {
            char c = obj.charAt(i);
            if (c == '\\' && i + 1 < obj.length()) {
                sb.append(obj.charAt(++i));
                continue;
            }
            if (c == '"') break;
            sb.append(c);
        }
        return sb.toString();
    }

    private static int intOpt(String obj, String key, int def) {
        String needle = "\"" + key + "\"";
        int k = obj.indexOf(needle);
        if (k < 0) return def;
        int colon = obj.indexOf(':', k + needle.length());
        int p = colon + 1;
        while (p < obj.length() && Character.isWhitespace(obj.charAt(p))) p++;
        int end = p;
        while (end < obj.length() && (Character.isDigit(obj.charAt(end)) || obj.charAt(end) == '-')) end++;
        if (end == p) return def;
        try {
            return Integer.parseInt(obj.substring(p, end));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean boolOpt(String obj, String key, boolean def) {
        String needle = "\"" + key + "\"";
        int k = obj.indexOf(needle);
        if (k < 0) return def;
        int colon = obj.indexOf(':', k + needle.length());
        int p = colon + 1;
        while (p < obj.length() && Character.isWhitespace(obj.charAt(p))) p++;
        if (obj.startsWith("true", p)) return true;
        if (obj.startsWith("false", p)) return false;
        return def;
    }
}
