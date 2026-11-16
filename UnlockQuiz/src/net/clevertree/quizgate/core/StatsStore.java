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

/**
 * Local quiz stats. Raw events/sessions are ring-buffered; per-day rollups in
 * {@code days.jsonl} persist historic asked/right/wrong/skipped within a day cap.
 * Paths: {@code files/stats/} (app-private). Never deleted on package replace.
 */
public final class StatsStore {
    public static final int MAX_EVENT_LINES = 1500;
    public static final long MAX_EVENT_BYTES = 512L * 1024L;
    public static final int MAX_SESSION_LINES = 400;
    public static final long MAX_SESSION_BYTES = 256L * 1024L;
    public static final int MAX_DAY_ROLLUPS = 90;

    private final File dir;
    private final File eventsFile;
    private final File sessionsFile;
    private final File daysFile;
    private final BoundedJsonl eventsLog;
    private final BoundedJsonl sessionsLog;
    private boolean migrated;

    public StatsStore(File dir) {
        if (!dir.exists()) dir.mkdirs();
        this.dir = dir;
        this.eventsFile = new File(dir, "events.jsonl");
        this.sessionsFile = new File(dir, "sessions.jsonl");
        this.daysFile = new File(dir, "days.jsonl");
        this.eventsLog = new BoundedJsonl(eventsFile, MAX_EVENT_LINES, MAX_EVENT_BYTES);
        this.sessionsLog = new BoundedJsonl(sessionsFile, MAX_SESSION_LINES, MAX_SESSION_BYTES);
    }

    public File getDir() { return dir; }
    public File getEventsFile() { return eventsFile; }
    public File getSessionsFile() { return sessionsFile; }
    public File getDaysFile() { return daysFile; }

    public synchronized void appendEvent(StatsEvent e) throws IOException {
        appendEvent(e, TimeZone.getDefault());
    }

    public synchronized void appendEvent(StatsEvent e, TimeZone zone) throws IOException {
        eventsLog.append(e.toJsonLine());
        if (e.ts > 0 && isDayCountedType(e.type)) {
            upsertDayFromEvent(e.ts, zone == null ? TimeZone.getDefault() : zone, e.type);
        }
    }

    public synchronized void appendSession(QuizSession session) throws IOException {
        String line = "{\"session_id\":\"" + JsonUtil.esc(session.getSessionId()) + "\""
                + ",\"trigger\":\"" + JsonUtil.esc(TelemetryRedaction.safeToken(session.getTrigger())) + "\""
                + ",\"started_at\":" + session.getStartedAt()
                + ",\"ended_at\":" + session.getEndedAt()
                + ",\"outcome\":\"" + JsonUtil.esc(session.getOutcome().name().toLowerCase()) + "\""
                + ",\"wrong_count\":" + session.getMissStreak()
                + ",\"questions_shown\":" + toJsonArray(session.getQuestionsShown())
                + ",\"duration_ms\":" + session.durationMs()
                + "}";
        sessionsLog.append(line);
    }

    public synchronized void compact() throws IOException {
        eventsLog.compact();
        sessionsLog.compact();
        pruneDayRollups(MAX_DAY_ROLLUPS);
    }

    public synchronized long approximateSizeBytes() {
        return sizeOf(eventsFile) + sizeOf(sessionsFile) + sizeOf(daysFile);
    }

    public Aggregates aggregates() throws IOException {
        Aggregates a = new Aggregates();
        if (sessionsFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(sessionsFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    a.sessions++;
                    if (line.contains("\"outcome\":\"correct\"")) a.correct++;
                    else if (line.contains("\"outcome\":\"bypass\"")) a.bypass++;
                    else if (line.contains("\"outcome\":\"consoling_unlock\"")) a.consoling++;
                }
            }
        }
        if (eventsFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(eventsFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String qid = JsonUtil.extract(line, "question_id");
                    String type = JsonUtil.extract(line, "type");
                    if (qid.isEmpty()) continue;
                    int[] pair = a.perQuestion.computeIfAbsent(qid, k -> new int[2]);
                    if ("correct".equals(type)) pair[0]++;
                    else if ("wrong".equals(type)) pair[1]++;
                }
            }
        }
        return a;
    }

    public DayStats dayStats(long nowMs, TimeZone zone) throws IOException {
        ensureMigrated(zone);
        String today = dayKey(nowMs, zone);
        Map<String, DayStats> rollups = loadDayRollups();
        DayStats d = rollups.get(today);
        if (d == null) d = new DayStats();
        else d = copyDay(d);
        return d;
    }

    public List<DayEntry> allDayStats(TimeZone zone) throws IOException {
        ensureMigrated(zone);
        Map<String, DayStats> byDay = loadDayRollups();
        List<String> order = new ArrayList<>(byDay.keySet());
        order.sort((a, b) -> compareDayKeys(b, a));
        List<DayEntry> out = new ArrayList<>();
        for (String key : order) out.add(new DayEntry(key, byDay.get(key)));
        return out;
    }

    private static boolean isDayCountedType(String type) {
        return "shown".equals(type) || "correct".equals(type)
                || "wrong".equals(type) || "skipped".equals(type);
    }

    private void upsertDayFromEvent(long ts, TimeZone zone, String type) throws IOException {
        ensureMigrated(zone);
        String key = dayKey(ts, zone);
        Map<String, DayStats> map = loadDayRollups();
        DayStats d = map.get(key);
        if (d == null) {
            d = new DayStats();
            map.put(key, d);
        }
        applyEvent(d, type);
        writeDayRollups(map);
    }

    private void ensureMigrated(TimeZone zone) throws IOException {
        if (migrated) return;
        migrated = true;
        if (daysFile.exists()) return;
        Map<String, DayStats> map = new HashMap<>();
        if (eventsFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(eventsFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    long ts = JsonUtil.extractLong(line, "ts");
                    if (ts <= 0) continue;
                    String key = dayKey(ts, zone);
                    DayStats d = map.computeIfAbsent(key, k -> new DayStats());
                    applyEvent(d, JsonUtil.extract(line, "type"));
                }
            }
        }
        if (!map.isEmpty()) writeDayRollups(map);
        else {
            FileWriter fw = new FileWriter(daysFile, false);
            fw.close();
        }
    }

    private Map<String, DayStats> loadDayRollups() throws IOException {
        Map<String, DayStats> map = new HashMap<>();
        if (!daysFile.exists()) return map;
        try (BufferedReader br = new BufferedReader(new FileReader(daysFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String day = JsonUtil.extract(line, "day");
                if (day.isEmpty()) continue;
                DayStats d = new DayStats();
                d.questionsAsked = (int) Math.max(0, JsonUtil.extractLong(line, "asked"));
                d.answeredRight = (int) Math.max(0, JsonUtil.extractLong(line, "right"));
                d.answeredWrong = (int) Math.max(0, JsonUtil.extractLong(line, "wrong"));
                d.skipped = (int) Math.max(0, JsonUtil.extractLong(line, "skipped"));
                map.put(day, d);
            }
        }
        return map;
    }

    private void writeDayRollups(Map<String, DayStats> map) throws IOException {
        List<String> keys = new ArrayList<>(map.keySet());
        keys.sort(StatsStore::compareDayKeys);
        if (keys.size() > MAX_DAY_ROLLUPS) {
            keys = new ArrayList<>(keys.subList(keys.size() - MAX_DAY_ROLLUPS, keys.size()));
        }
        File tmp = new File(daysFile.getAbsolutePath() + ".tmp");
        FileWriter fw = new FileWriter(tmp, false);
        try {
            for (String key : keys) {
                DayStats d = map.get(key);
                if (d == null) continue;
                fw.write("{\"day\":\"");
                fw.write(JsonUtil.esc(key));
                fw.write("\",\"asked\":");
                fw.write(Integer.toString(d.questionsAsked));
                fw.write(",\"right\":");
                fw.write(Integer.toString(d.answeredRight));
                fw.write(",\"wrong\":");
                fw.write(Integer.toString(d.answeredWrong));
                fw.write(",\"skipped\":");
                fw.write(Integer.toString(d.skipped));
                fw.write("}\n");
            }
        } finally {
            fw.close();
        }
        if (!tmp.renameTo(daysFile)) {
            try (FileReader fr = new FileReader(tmp); FileWriter out = new FileWriter(daysFile, false)) {
                char[] buf = new char[4096];
                int n;
                while ((n = fr.read(buf)) >= 0) out.write(buf, 0, n);
            }
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    private void pruneDayRollups(int maxDays) throws IOException {
        Map<String, DayStats> map = loadDayRollups();
        if (map.size() <= maxDays) return;
        writeDayRollups(map);
    }

    private static DayStats copyDay(DayStats src) {
        DayStats d = new DayStats();
        d.questionsAsked = src.questionsAsked;
        d.answeredRight = src.answeredRight;
        d.answeredWrong = src.answeredWrong;
        d.skipped = src.skipped;
        return d;
    }

    private static void applyEvent(DayStats d, String type) {
        if ("shown".equals(type)) d.questionsAsked++;
        else if ("correct".equals(type)) d.answeredRight++;
        else if ("wrong".equals(type)) d.answeredWrong++;
        else if ("skipped".equals(type)) d.skipped++;
    }

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
        public final Map<String, int[]> perQuestion = new HashMap<>();
        public double bypassRate() {
            return sessions == 0 ? 0 : (double) bypass / sessions;
        }
    }

    public static final class DayStats {
        public int questionsAsked;
        public int answeredRight;
        public int answeredWrong;
        public int skipped;
        public int answeredTotal() { return answeredRight + answeredWrong; }
        public double successRate() {
            int n = answeredTotal();
            return n == 0 ? 0 : (double) answeredRight / n;
        }
        public Level level() {
            if (questionsAsked == 0 && answeredTotal() == 0) return Level.READY;
            if (answeredTotal() == 0) return Level.READY;
            double r = successRate();
            if (r >= 0.75) return Level.GREAT;
            if (r >= 0.45) return Level.GOOD;
            return Level.KEEP_TRYING;
        }
    }

    public enum Level { READY, GREAT, GOOD, KEEP_TRYING }

    private static String toJsonArray(List<String> ids) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(JsonUtil.esc(TelemetryRedaction.safeToken(ids.get(i)))).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    private static long sizeOf(File f) {
        return f != null && f.exists() ? f.length() : 0L;
    }

    public List<String> readAllSessionLines() throws IOException {
        return sessionsLog.readAll();
    }

    public List<String> recentEventLines(int n) throws IOException {
        return eventsLog.tail(n);
    }
}
