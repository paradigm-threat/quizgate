package net.clevertree.quizgate.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Durable per-stats-day interactive (screen-on) milliseconds under
 * {@code files/stats/interactive.jsonl}. No package names — only time.
 */
public final class InteractiveMinutesStore {
    public static final int MAX_DAY_ROWS = 90;

    private final File file;

    public InteractiveMinutesStore(File statsDir) {
        this.file = new File(statsDir, "interactive.jsonl");
    }

    public File getFile() {
        return file;
    }

    public synchronized long getMs(String dayKey) throws IOException {
        String day = StatsStore.canonicalDayKey(dayKey);
        if (day.isEmpty()) return 0L;
        Map<String, Long> map = load();
        Long v = map.get(day);
        return v == null ? 0L : Math.max(0L, v);
    }

    /** Add {@code deltaMs} to {@code dayKey} (clamped ≥0). */
    public synchronized long addMs(String dayKey, long deltaMs) throws IOException {
        String day = StatsStore.canonicalDayKey(dayKey);
        if (day.isEmpty() || deltaMs <= 0L) return getMs(dayKey);
        Map<String, Long> map = load();
        long cur = map.containsKey(day) ? map.get(day) : 0L;
        long next = cur + deltaMs;
        if (next < 0L) next = 0L;
        // Cap one day at 36h to absorb clock skew without exploding.
        if (next > 36L * 60L * 60L * 1000L) next = 36L * 60L * 60L * 1000L;
        map.put(day, next);
        write(map);
        return next;
    }

    private Map<String, Long> load() throws IOException {
        Map<String, Long> map = new HashMap<>();
        if (!file.exists()) return map;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String day = StatsStore.canonicalDayKey(JsonUtil.extract(line, "day"));
                if (day.isEmpty()) continue;
                long ms = JsonUtil.extractLong(line, "interactive_ms");
                if (ms < 0) ms = 0;
                map.put(day, ms);
            }
        }
        return map;
    }

    private void write(Map<String, Long> map) throws IOException {
        List<String> keys = new ArrayList<>(map.keySet());
        keys.sort(StatsStore::compareDayKeys);
        if (keys.size() > MAX_DAY_ROWS) {
            keys = new ArrayList<>(keys.subList(keys.size() - MAX_DAY_ROWS, keys.size()));
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        File tmp = new File(file.getAbsolutePath() + ".tmp");
        FileWriter fw = new FileWriter(tmp, false);
        try {
            for (String day : keys) {
                Long ms = map.get(day);
                if (ms == null) continue;
                fw.write("{\"day\":\"");
                fw.write(JsonUtil.esc(day));
                fw.write("\",\"interactive_ms\":");
                fw.write(Long.toString(ms));
                fw.write("}\n");
            }
        } finally {
            fw.close();
        }
        if (!tmp.renameTo(file)) {
            try (FileReader fr = new FileReader(tmp); FileWriter out = new FileWriter(file, false)) {
                char[] buf = new char[4096];
                int n;
                while ((n = fr.read(buf)) >= 0) out.write(buf, 0, n);
            }
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    public static String formatHours(long ms) {
        double h = Math.max(0L, ms) / 3_600_000.0;
        return String.format(Locale.US, "%.2f", h);
    }
}
