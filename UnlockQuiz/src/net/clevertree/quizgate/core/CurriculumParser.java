package net.clevertree.quizgate.core;

import java.util.ArrayList;
import java.util.List;

/** Minimal parsers for manifest.json + curriculum.json (no external deps). */
public final class CurriculumParser {
    private CurriculumParser() {}

    public static CurriculumPack parse(String manifestJson, String curriculumJson) {
        String id = str(manifestJson, "id");
        String title = strOpt(manifestJson, "title", id);
        int ageMin = intOpt(manifestJson, "ageMin", 4);
        int ageMax = intOpt(manifestJson, "ageMax", 7);
        int difficultyRank = intOpt(manifestJson, "difficultyRank", 10);
        int version = intOpt(manifestJson, "version", 1);
        String sha = strOpt(manifestJson, "contentSha256", "");

        int roundSize = intOpt(curriculumJson, "roundSize", 3);
        int earn = intOpt(curriculumJson, "earnMinutesPerCorrect", 2);
        String wrong = strOpt(curriculumJson, "wrongBehavior", "next");
        boolean consoling = boolOpt(curriculumJson, "consolingUnlock", true);
        int maxWrong = intOpt(curriculumJson, "maxWrong", QuizSession.MAX_WRONG);

        List<CurriculumPack.Tier> tiers = parseTiers(curriculumJson);
        List<Question> questions = new ArrayList<>();
        List<CurriculumPack.ItemMeta> meta = new ArrayList<>();
        parseItems(curriculumJson, questions, meta);
        Catalog catalog = new Catalog(questions);
        return new CurriculumPack(id, title, ageMin, ageMax, difficultyRank, version, sha,
                roundSize, earn, wrong, consoling, maxWrong, tiers, catalog, meta);
    }

    private static List<CurriculumPack.Tier> parseTiers(String json) {
        List<CurriculumPack.Tier> out = new ArrayList<>();
        String arr = extractArray(json, "tiers");
        if (arr == null) return out;
        for (String obj : splitObjects(arr)) {
            out.add(new CurriculumPack.Tier(
                    str(obj, "id"),
                    strOpt(obj, "label", str(obj, "id")),
                    intOpt(obj, "ageMin", 0),
                    intOpt(obj, "ageMax", 99)));
        }
        return out;
    }

    private static void parseItems(String json, List<Question> questions, List<CurriculumPack.ItemMeta> meta) {
        String arr = extractArray(json, "items");
        if (arr == null) throw new IllegalArgumentException("missing items");
        for (String obj : splitObjects(arr)) {
            String id = str(obj, "id");
            String legacy = strOpt(obj, "legacyId", null);
            String type = str(obj, "type");
            String spoken = str(obj, "spoken");
            String symbolic = strOpt(obj, "symbolic", "");
            String correct = str(obj, "correct");
            List<String> choices = strArray(obj, "choices");
            List<String> labels = obj.contains("\"labels\"") ? strArray(obj, "labels") : null;
            int weight = intOpt(obj, "weight", Question.DEFAULT_WEIGHT);
            // Catalog byId uses Question.id — prefer legacyId for ADB force_qid compatibility
            String catalogId = legacy != null ? legacy : id;
            int level = intOpt(obj, "level", 0);
            questions.add(new Question(catalogId, type, spoken, symbolic, choices, correct, labels, weight));
            meta.add(new CurriculumPack.ItemMeta(strOpt(obj, "tier", ""), legacy, level));
        }
    }

    private static String extractArray(String json, String key) {
        String needle = "\"" + key + "\"";
        int k = json.indexOf(needle);
        if (k < 0) return null;
        int lb = json.indexOf('[', k);
        if (lb < 0) return null;
        int depth = 0;
        for (int i = lb; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return json.substring(lb + 1, i);
            }
        }
        return null;
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
        // skip whitespace / null
        int p = colon + 1;
        while (p < obj.length() && Character.isWhitespace(obj.charAt(p))) p++;
        if (p < obj.length() && obj.startsWith("null", p)) return def;
        int q1 = obj.indexOf('"', colon + 1);
        if (q1 < 0) return def;
        return readJsonString(obj, q1 + 1, null);
    }

    /**
     * Decode a JSON string body starting after the opening quote; supports JSON unicode escapes.
     * If {@code endOut} is non-null, sets {@code endOut[0]} to the index after the closing quote.
     */
    static String readJsonString(String src, int start, int[] endOut) {
        StringBuilder sb = new StringBuilder();
        int i = start;
        for (; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '"') {
                i++; // past closing quote
                break;
            }
            if (c == '\\' && i + 1 < src.length()) {
                char e = src.charAt(++i);
                if (e == 'u' && i + 4 < src.length()) {
                    String hex = src.substring(i + 1, i + 5);
                    try {
                        sb.append((char) Integer.parseInt(hex, 16));
                        i += 4;
                        continue;
                    } catch (NumberFormatException ignored) {
                        /* fall through — treat as literal */
                    }
                }
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case '"':
                    case '\\':
                    case '/':
                        sb.append(e);
                        break;
                    default:
                        sb.append(e);
                        break;
                }
                continue;
            }
            sb.append(c);
        }
        if (endOut != null && endOut.length > 0) endOut[0] = i;
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

    private static List<String> strArray(String obj, String key) {
        String needle = "\"" + key + "\"";
        int k = obj.indexOf(needle);
        if (k < 0) throw new IllegalArgumentException("missing array " + key);
        int lb = obj.indexOf('[', k);
        int rb = obj.indexOf(']', lb);
        String inner = obj.substring(lb + 1, rb);
        List<String> out = new ArrayList<>();
        int i = 0;
        int[] end = new int[1];
        while (i < inner.length()) {
            while (i < inner.length() && inner.charAt(i) != '"') i++;
            if (i >= inner.length()) break;
            i++;
            out.add(readJsonString(inner, i, end));
            i = end[0];
        }
        return out;
    }
}
