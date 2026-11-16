package net.clevertree.quizgate.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public final class StatsStore {
    private final File eventsFile;
    private final File sessionsFile;

    public StatsStore(File dir) {
        if (!dir.exists()) dir.mkdirs();
        this.eventsFile = new File(dir, "events.jsonl");
        this.sessionsFile = new File(dir, "sessions.jsonl");
    }

    public synchronized void appendEvent(StatsEvent e) throws IOException {
        append(eventsFile, e.toJsonLine());
    }

    public synchronized void appendSession(QuizSession session) throws IOException {
        String line = "{\"session_id\":\"" + esc(session.getSessionId()) + "\""
                + ",\"trigger\":\"" + esc(session.getTrigger()) + "\""
                + ",\"started_at\":" + session.getStartedAt()
                + ",\"ended_at\":" + session.getEndedAt()
                + ",\"outcome\":\"" + session.getOutcome().name().toLowerCase() + "\""
                + ",\"wrong_count\":" + session.getMissStreak()
                + ",\"questions_shown\":" + toJsonArray(session.getQuestionsShown())
                + ",\"duration_ms\":" + session.durationMs()
                + "}";
        append(sessionsFile, line);
    }

    public File getEventsFile() { return eventsFile; }
    public File getSessionsFile() { return sessionsFile; }

    public Aggregates aggregates() throws IOException {
        Aggregates a = new Aggregates();
        if (!sessionsFile.exists()) return a;
        try (BufferedReader br = new BufferedReader(new FileReader(sessionsFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                a.sessions++;
                if (line.contains("\"outcome\":\"correct\"")) a.correct++;
                else if (line.contains("\"outcome\":\"bypass\"")) a.bypass++;
                else if (line.contains("\"outcome\":\"consoling_unlock\"")) a.consoling++;
            }
        }
        if (eventsFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(eventsFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String qid = extract(line, "question_id");
                    String type = extract(line, "type");
                    if (qid.isEmpty()) continue;
                    int[] pair = a.perQuestion.computeIfAbsent(qid, k -> new int[2]);
                    if ("correct".equals(type)) pair[0]++;
                    else if ("wrong".equals(type)) pair[1]++;
                }
            }
        }
        return a;
    }

    /** Questions shown / correct / wrong for a local calendar day. */
    public DayStats dayStats(long nowMs, TimeZone zone) throws IOException {
        DayStats d = new DayStats();
        if (!eventsFile.exists()) return d;
        String today = dayKey(nowMs, zone);
        try (BufferedReader br = new BufferedReader(new FileReader(eventsFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                long ts = extractLong(line, "ts");
                if (ts <= 0) continue;
                if (!today.equals(dayKey(ts, zone))) continue;
                applyEvent(d, extract(line, "type"));
            }
        }
        return d;
    }

    /**
     * Historic per-day stats from the events log (newest first).
     * Events persist on disk across days — this is the durable history.
     */
    public List<DayEntry> allDayStats(TimeZone zone) throws IOException {
        Map<String, DayStats> byDay = new HashMap<>();
        List<String> order = new ArrayList<>();
        if (eventsFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(eventsFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    long ts = extractLong(line, "ts");
                    if (ts <= 0) continue;
                    String key = dayKey(ts, zone);
                    DayStats d = byDay.get(key);
                    if (d == null) {
                        d = new DayStats();
                        byDay.put(key, d);
                        order.add(key);
                    }
                    applyEvent(d, extract(line, "type"));
                }
            }
        }
        // Newest calendar day first
        order.sort((a, b) -> compareDayKeys(b, a));
        List<DayEntry> out = new ArrayList<>();
        for (String key : order) {
            out.add(new DayEntry(key, byDay.get(key)));
        }
        return out;
    }

    private static void applyEvent(DayStats d, String type) {
        if ("shown".equals(type)) d.questionsAsked++;
        else if ("correct".equals(type)) d.answeredRight++;
        else if ("wrong".equals(type)) d.answeredWrong++;
        else if ("skipped".equals(type)) d.skipped++;
    }

    /** Compare {@code YYYY-M-D} keys chronologically. */
    static int compareDayKeys(String a, String b) {
        String[] pa = a.split("-");
        String[] pb = b.split("-");
        for (int i = 0; i < 3; i++) {
            int ia = Integer.parseInt(pa[i]);
            int ib = Integer.parseInt(pb[i]);
            if (ia != ib) return Integer.compare(ia, ib);
        }
        return 0;
    }

    public static final class DayEntry {
        public final String dayKey;
        public final DayStats stats;

        public DayEntry(String dayKey, DayStats stats) {
            this.dayKey = dayKey;
            this.stats = stats;
        }
    }

    public static String dayKey(long whenMs, TimeZone zone) {
        Calendar c = Calendar.getInstance(zone);
        c.setTimeInMillis(whenMs);
        return c.get(Calendar.YEAR) + "-" + (c.get(Calendar.MONTH) + 1) + "-" + c.get(Calendar.DAY_OF_MONTH);
    }

    public static final class Aggregates {
        public int sessions;
        public int correct;
        public int bypass;
        public int consoling;
        public final Map<String, int[]> perQuestion = new HashMap<>(); // [correct, wrong]

        public double bypassRate() {
            return sessions == 0 ? 0 : (double) bypass / sessions;
        }
    }

    public static final class DayStats {
        public int questionsAsked;
        public int answeredRight;
        public int answeredWrong;
        /** Shown but left unanswered (Home/back/kill before correct or wrong). */
        public int skipped;

        public int answeredTotal() {
            return answeredRight + answeredWrong;
        }

        /** 0–1 success among graded answers; 0 if none yet. */
        public double successRate() {
            int n = answeredTotal();
            return n == 0 ? 0 : (double) answeredRight / n;
        }

        public Level level() {
            if (questionsAsked == 0 && answeredTotal() == 0) return Level.READY;
            double r = successRate();
            if (answeredTotal() == 0) return Level.READY;
            if (r >= 0.75) return Level.GREAT;
            if (r >= 0.45) return Level.GOOD;
            return Level.KEEP_TRYING;
        }
    }

    public enum Level {
        READY,
        GREAT,
        GOOD,
        KEEP_TRYING
    }

    private static void append(File f, String line) throws IOException {
        FileWriter fw = new FileWriter(f, true);
        fw.write(line);
        fw.write("\n");
        fw.close();
    }

    private static String toJsonArray(List<String> ids) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(esc(ids.get(i))).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    private static String extract(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int i = json.indexOf(needle);
        if (i < 0) return "";
        int s = i + needle.length();
        int e = json.indexOf('"', s);
        return e < 0 ? "" : json.substring(s, e);
    }

    private static long extractLong(String json, String key) {
        String needle = "\"" + key + "\":";
        int i = json.indexOf(needle);
        if (i < 0) return -1;
        int s = i + needle.length();
        int e = s;
        while (e < json.length()) {
            char c = json.charAt(e);
            if (c == '-' || (c >= '0' && c <= '9')) e++;
            else break;
        }
        if (e == s) return -1;
        try {
            return Long.parseLong(json.substring(s, e));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public List<String> readAllSessionLines() throws IOException {
        List<String> out = new ArrayList<>();
        if (!sessionsFile.exists()) return out;
        try (BufferedReader br = new BufferedReader(new FileReader(sessionsFile))) {
            String line;
            while ((line = br.readLine()) != null) out.add(line);
        }
        return out;
    }
}
