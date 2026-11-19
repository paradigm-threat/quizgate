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
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Local quiz stats. Raw events/sessions are ring-buffered; per-day rollups in
 * {@code days.jsonl} persist historic asked/right/wrong/skipped within a day cap,
 * plus lock vs voluntary ({@code play}) splits for behavioral analysis (no ability gate).
 * Paths: {@code files/stats/} (app-private). Never deleted on package replace.
 */
public final class StatsStore {
    public static final int MAX_EVENT_LINES = 1500;
    public static final long MAX_EVENT_BYTES = 512L * 1024L;
    public static final int MAX_SESSION_LINES = 400;
    public static final long MAX_SESSION_BYTES = 256L * 1024L;
    public static final int MAX_DAY_ROLLUPS = 90;

    /** Scheduled Hard lock quiz (restricts until unlock). */
    public static final String SESSION_LOCK = "lock";
    /** Voluntary / practice use (widget, Parents force, icon) — seeking challenge. */
    public static final String SESSION_PLAY = "play";

    private final File dir;
    private final File eventsFile;
    private final File sessionsFile;
    private final File daysFile;
    private final File contextMarker;
    private final BoundedJsonl eventsLog;
    private final BoundedJsonl sessionsLog;
    private boolean migrated;

    public StatsStore(File dir) {
        this(dir, 32);
    }

    /**
     * @param compactEvery appends before opportunistic JSONL compaction
     *                     (slim profiles use a high value and compact on session end)
     */
    public StatsStore(File dir, int compactEvery) {
        if (!dir.exists()) dir.mkdirs();
        this.dir = dir;
        this.eventsFile = new File(dir, "events.jsonl");
        this.sessionsFile = new File(dir, "sessions.jsonl");
        this.daysFile = new File(dir, "days.jsonl");
        this.contextMarker = new File(dir, ".day_context_v1");
        int every = Math.max(1, compactEvery);
        this.eventsLog = new BoundedJsonl(eventsFile, MAX_EVENT_LINES, MAX_EVENT_BYTES, every);
        this.sessionsLog = new BoundedJsonl(sessionsFile, MAX_SESSION_LINES, MAX_SESSION_BYTES, every);
    }

    public File getDir() { return dir; }
    public File getEventsFile() { return eventsFile; }
    public File getSessionsFile() { return sessionsFile; }
    public File getDaysFile() { return daysFile; }

    /** Normalize to {@link #SESSION_LOCK}, {@link #SESSION_PLAY}, or empty. */
    public static String normalizeSessionType(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return "";
        if (SESSION_LOCK.equals(s) || "hard".equals(s) || "scheduled".equals(s)) {
            return SESSION_LOCK;
        }
        if (SESSION_PLAY.equals(s) || "practice".equals(s) || "voluntary".equals(s)
                || "widget".equals(s)) {
            return SESSION_PLAY;
        }
        return "";
    }

    public synchronized void appendEvent(StatsEvent e) throws IOException {
        appendEvent(e, TimeZone.getDefault());
    }

    public synchronized void appendEvent(StatsEvent e, TimeZone zone) throws IOException {
        eventsLog.append(e.toJsonLine());
        // Demo/seed traffic (adb_/prime_) stays in events but does not inflate day cards.
        if (e.ts > 0 && isDayCountedType(e.type) && !e.demo) {
            upsertDayFromEvent(e.ts, zone == null ? TimeZone.getDefault() : zone,
                    e.type, e.sessionType);
        }
    }

    /** ADB/dev/seed triggers — kept in raw logs, excluded from day rollups. */
    public static boolean isDemoTrigger(String trigger) {
        if (trigger == null || trigger.isEmpty()) return false;
        return trigger.startsWith("adb_") || trigger.startsWith("prime_")
                || "debug".equals(trigger) || "seed".equals(trigger);
    }

    public synchronized void appendSession(QuizSession session) throws IOException {
        boolean demo = isDemoTrigger(session.getTrigger());
        String sessionType = inferSessionTypeFromTrigger(session.getTrigger());
        String line = "{\"session_id\":\"" + JsonUtil.esc(session.getSessionId()) + "\""
                + ",\"trigger\":\"" + JsonUtil.esc(TelemetryRedaction.safeToken(session.getTrigger())) + "\""
                + ",\"session_type\":\"" + JsonUtil.esc(sessionType) + "\""
                + ",\"started_at\":" + session.getStartedAt()
                + ",\"ended_at\":" + session.getEndedAt()
                + ",\"outcome\":\"" + JsonUtil.esc(session.getOutcome().name().toLowerCase(Locale.ROOT)) + "\""
                + ",\"wrong_count\":" + session.getMissStreak()
                + ",\"questions_shown\":" + toJsonArray(session.getQuestionsShown())
                + ",\"duration_ms\":" + session.durationMs()
                + (demo ? ",\"demo\":true" : "")
                + "}";
        sessionsLog.append(line);
    }

    /**
     * Map launch trigger → lock vs play. {@code play} / widget are voluntary;
     * scheduled / wake / interval (and unknown) are lock.
     */
    public static String inferSessionTypeFromTrigger(String trigger) {
        if (trigger == null || trigger.isEmpty()) return SESSION_LOCK;
        String t = trigger.trim().toLowerCase(Locale.ROOT);
        if (SESSION_PLAY.equals(t) || "widget".equals(t)) return SESSION_PLAY;
        return SESSION_LOCK;
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

    private void upsertDayFromEvent(long ts, TimeZone zone, String type, String sessionType)
            throws IOException {
        ensureMigrated(zone);
        String key = dayKey(ts, zone);
        Map<String, DayStats> map = loadDayRollups();
        DayStats d = map.get(key);
        if (d == null) {
            d = new DayStats();
            map.put(key, d);
        }
        applyEvent(d, type, sessionType);
        writeDayRollups(map);
    }

    private void ensureMigrated(TimeZone zone) throws IOException {
        if (migrated) return;
        migrated = true;
        if (!daysFile.exists()) {
            rebuildDaysFromEvents(zone);
            touchContextMarker();
            return;
        }
        // Existing days.jsonl keeps totals; lock/play splits fill in from new events only
        // (do not rebuild from pruned events — that would drop historic rollups).
        if (!contextMarker.exists()) touchContextMarker();
    }

    private void rebuildDaysFromEvents(TimeZone zone) throws IOException {
        Map<String, DayStats> map = new HashMap<>();
        if (eventsFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(eventsFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.contains("\"demo\":true")) continue;
                    long ts = JsonUtil.extractLong(line, "ts");
                    if (ts <= 0) continue;
                    String type = JsonUtil.extract(line, "type");
                    if (!isDayCountedType(type)) continue;
                    String key = dayKey(ts, zone);
                    DayStats d = map.computeIfAbsent(key, k -> new DayStats());
                    applyEvent(d, type, JsonUtil.extract(line, "session_type"));
                }
            }
        }
        writeDayRollups(map);
    }

    private void touchContextMarker() throws IOException {
        FileWriter fw = new FileWriter(contextMarker, false);
        fw.write("v1\n");
        fw.close();
    }

    private Map<String, DayStats> loadDayRollups() throws IOException {
        Map<String, DayStats> map = new HashMap<>();
        if (!daysFile.exists()) return map;
        try (BufferedReader br = new BufferedReader(new FileReader(daysFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String day = canonicalDayKey(JsonUtil.extract(line, "day"));
                if (day.isEmpty()) continue;
                DayStats d = map.get(day);
                if (d == null) d = new DayStats();
                // Prefer higher totals when merging legacy unpadded + padded keys.
                d.questionsAsked = Math.max(d.questionsAsked,
                        (int) Math.max(0, JsonUtil.extractLong(line, "asked")));
                d.answeredRight = Math.max(d.answeredRight,
                        (int) Math.max(0, JsonUtil.extractLong(line, "right")));
                d.answeredWrong = Math.max(d.answeredWrong,
                        (int) Math.max(0, JsonUtil.extractLong(line, "wrong")));
                d.skipped = Math.max(d.skipped,
                        (int) Math.max(0, JsonUtil.extractLong(line, "skipped")));
                d.askedLock = Math.max(d.askedLock,
                        (int) Math.max(0, JsonUtil.extractLong(line, "asked_lock")));
                d.rightLock = Math.max(d.rightLock,
                        (int) Math.max(0, JsonUtil.extractLong(line, "right_lock")));
                d.wrongLock = Math.max(d.wrongLock,
                        (int) Math.max(0, JsonUtil.extractLong(line, "wrong_lock")));
                d.skippedLock = Math.max(d.skippedLock,
                        (int) Math.max(0, JsonUtil.extractLong(line, "skipped_lock")));
                d.askedPlay = Math.max(d.askedPlay,
                        (int) Math.max(0, JsonUtil.extractLong(line, "asked_play")));
                d.rightPlay = Math.max(d.rightPlay,
                        (int) Math.max(0, JsonUtil.extractLong(line, "right_play")));
                d.wrongPlay = Math.max(d.wrongPlay,
                        (int) Math.max(0, JsonUtil.extractLong(line, "wrong_play")));
                d.skippedPlay = Math.max(d.skippedPlay,
                        (int) Math.max(0, JsonUtil.extractLong(line, "skipped_play")));
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
                fw.write(",\"asked_lock\":");
                fw.write(Integer.toString(d.askedLock));
                fw.write(",\"right_lock\":");
                fw.write(Integer.toString(d.rightLock));
                fw.write(",\"wrong_lock\":");
                fw.write(Integer.toString(d.wrongLock));
                fw.write(",\"skipped_lock\":");
                fw.write(Integer.toString(d.skippedLock));
                fw.write(",\"asked_play\":");
                fw.write(Integer.toString(d.askedPlay));
                fw.write(",\"right_play\":");
                fw.write(Integer.toString(d.rightPlay));
                fw.write(",\"wrong_play\":");
                fw.write(Integer.toString(d.wrongPlay));
                fw.write(",\"skipped_play\":");
                fw.write(Integer.toString(d.skippedPlay));
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
        d.askedLock = src.askedLock;
        d.rightLock = src.rightLock;
        d.wrongLock = src.wrongLock;
        d.skippedLock = src.skippedLock;
        d.askedPlay = src.askedPlay;
        d.rightPlay = src.rightPlay;
        d.wrongPlay = src.wrongPlay;
        d.skippedPlay = src.skippedPlay;
        return d;
    }

    private static void applyEvent(DayStats d, String type, String sessionType) {
        String ctx = normalizeSessionType(sessionType);
        if ("shown".equals(type)) {
            d.questionsAsked++;
            if (SESSION_LOCK.equals(ctx)) d.askedLock++;
            else if (SESSION_PLAY.equals(ctx)) d.askedPlay++;
        } else if ("correct".equals(type)) {
            d.answeredRight++;
            if (SESSION_LOCK.equals(ctx)) d.rightLock++;
            else if (SESSION_PLAY.equals(ctx)) d.rightPlay++;
        } else if ("wrong".equals(type)) {
            d.answeredWrong++;
            if (SESSION_LOCK.equals(ctx)) d.wrongLock++;
            else if (SESSION_PLAY.equals(ctx)) d.wrongPlay++;
        } else if ("skipped".equals(type)) {
            d.skipped++;
            if (SESSION_LOCK.equals(ctx)) d.skippedLock++;
            else if (SESSION_PLAY.equals(ctx)) d.skippedPlay++;
        }
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
        Calendar c = Calendar.getInstance(zone == null ? TimeZone.getDefault() : zone);
        c.setTimeInMillis(whenMs);
        return String.format(Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH));
    }

    /** Normalize {@code YYYY-M-D} / {@code YYYY-MM-DD} to zero-padded Intake day keys. */
    public static String canonicalDayKey(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";
        String[] p = s.split("-");
        if (p.length != 3) return s;
        try {
            int y = Integer.parseInt(p[0]);
            int m = Integer.parseInt(p[1]);
            int d = Integer.parseInt(p[2]);
            if (y < 2000 || y > 2100 || m < 1 || m > 12 || d < 1 || d > 31) return s;
            return String.format(Locale.US, "%04d-%02d-%02d", y, m, d);
        } catch (NumberFormatException e) {
            return s;
        }
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
        /** Asked during scheduled lock. */
        public int askedLock;
        public int rightLock;
        public int wrongLock;
        public int skippedLock;
        /** Asked during voluntary / practice play. */
        public int askedPlay;
        public int rightPlay;
        public int wrongPlay;
        public int skippedPlay;

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

        /** True when any lock/play split was recorded. */
        public boolean hasContextSplit() {
            return askedLock + askedPlay + rightLock + rightPlay
                    + wrongLock + wrongPlay + skippedLock + skippedPlay > 0;
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
