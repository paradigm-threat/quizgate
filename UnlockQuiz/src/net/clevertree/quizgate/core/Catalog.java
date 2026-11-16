package net.clevertree.quizgate.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Minimal JSON parser for our fixed question schema (no external deps). */
public final class Catalog {
    private final List<Question> questions;

    public Catalog(List<Question> questions) {
        this.questions = new ArrayList<>(questions);
        validateAll();
    }

    public List<Question> all() {
        return questions;
    }

    public int size() {
        return questions.size();
    }

    public Question byId(String id) {
        for (Question q : questions) {
            if (q.id.equals(id)) return q;
        }
        return null;
    }

    public void validateAll() {
        Set<String> ids = new HashSet<>();
        for (Question q : questions) {
            q.validate();
            if (!ids.add(q.id)) throw new IllegalStateException("duplicate id " + q.id);
        }
        if (questions.isEmpty()) throw new IllegalStateException("empty catalog");
    }

    public static Catalog fromJson(String json) {
        List<Question> list = new ArrayList<>();
        String body = json.trim();
        if (!body.startsWith("[")) throw new IllegalArgumentException("expected array");
        // Split objects on },{ while tracking — simple scanner
        int i = 1;
        while (i < body.length()) {
            while (i < body.length() && (body.charAt(i) == ',' || Character.isWhitespace(body.charAt(i)) || body.charAt(i) == ']')) {
                if (body.charAt(i) == ']') break;
                i++;
            }
            if (i >= body.length() || body.charAt(i) == ']') break;
            if (body.charAt(i) != '{') throw new IllegalArgumentException("expected object at " + i);
            int start = i;
            int depth = 0;
            for (; i < body.length(); i++) {
                char c = body.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        i++;
                        list.add(parseObject(body.substring(start, i)));
                        break;
                    }
                }
            }
        }
        return new Catalog(list);
    }

    private static Question parseObject(String obj) {
        String id = str(obj, "id");
        String type = str(obj, "type");
        String spoken = str(obj, "spoken");
        String symbolic = strOpt(obj, "symbolic", "");
        String correct = str(obj, "correct");
        List<String> choices = strArray(obj, "choices");
        List<String> labels = null;
        if (obj.contains("\"labels\"")) {
            labels = strArray(obj, "labels");
        }
        return new Question(id, type, spoken, symbolic, choices, correct, labels);
    }

    private static String str(String obj, String key) {
        String v = strOpt(obj, key, null);
        if (v == null) throw new IllegalArgumentException("missing " + key + " in " + obj);
        return v;
    }

    private static String strOpt(String obj, String key, String def) {
        String needle = "\"" + key + "\"";
        int k = obj.indexOf(needle);
        if (k < 0) return def;
        int colon = obj.indexOf(':', k + needle.length());
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

    private static List<String> strArray(String obj, String key) {
        String needle = "\"" + key + "\"";
        int k = obj.indexOf(needle);
        if (k < 0) throw new IllegalArgumentException("missing array " + key);
        int lb = obj.indexOf('[', k);
        int rb = obj.indexOf(']', lb);
        String inner = obj.substring(lb + 1, rb);
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < inner.length()) {
            while (i < inner.length() && inner.charAt(i) != '"') i++;
            if (i >= inner.length()) break;
            i++;
            StringBuilder sb = new StringBuilder();
            while (i < inner.length()) {
                char c = inner.charAt(i++);
                if (c == '\\' && i < inner.length()) {
                    sb.append(inner.charAt(i++));
                    continue;
                }
                if (c == '"') break;
                sb.append(c);
            }
            out.add(sb.toString());
        }
        return out;
    }
}
