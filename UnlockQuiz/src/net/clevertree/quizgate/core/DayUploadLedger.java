package net.clevertree.quizgate.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Persistent set of closed calendar days that have been successfully upserted
 * to Intake. One key per line in {@code uploaded_days.txt} under stats/.
 */
public final class DayUploadLedger {
    private final File file;

    public DayUploadLedger(File statsDir) {
        this.file = new File(statsDir, "uploaded_days.txt");
    }

    public File getFile() { return file; }

    public synchronized Set<String> load() throws IOException {
        Set<String> out = new HashSet<>();
        if (!file.exists()) return out;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String day = StatsStore.canonicalDayKey(line.trim());
                if (!day.isEmpty()) out.add(day);
            }
        }
        return out;
    }

    public synchronized void markUploaded(String dayKey) throws IOException {
        String day = StatsStore.canonicalDayKey(dayKey);
        if (day.isEmpty()) return;
        Set<String> cur = load();
        if (!cur.add(day)) return;
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        File tmp = new File(file.getAbsolutePath() + ".tmp");
        try (FileWriter fw = new FileWriter(tmp, false)) {
            for (String d : cur) {
                fw.write(d);
                fw.write('\n');
            }
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
}
