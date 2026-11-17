package net.clevertree.quizgate.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * Append-only JSONL with periodic compaction: line cap + byte cap, oldest first.
 * Writes are atomic on compact (temp + rename). Safe for app-private storage.
 */
public final class BoundedJsonl {
    private final File file;
    private final int maxLines;
    private final long maxBytes;
    private final int compactEvery;
    private int appendsSinceCompact;

    public BoundedJsonl(File file, int maxLines, long maxBytes) {
        this(file, maxLines, maxBytes, 32);
    }

    public BoundedJsonl(File file, int maxLines, long maxBytes, int compactEvery) {
        this.file = file;
        this.maxLines = Math.max(1, maxLines);
        this.maxBytes = Math.max(1024L, maxBytes);
        this.compactEvery = Math.max(1, compactEvery);
    }

    /** How many appends between opportunistic compact passes. */
    public int compactEvery() {
        return compactEvery;
    }

    public File file() { return file; }

    public synchronized void append(String line) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        FileWriter fw = new FileWriter(file, true);
        try {
            fw.write(line);
            fw.write('\n');
        } finally {
            fw.close();
        }
        appendsSinceCompact++;
        if (appendsSinceCompact >= compactEvery || overCap()) {
            compact();
        }
    }

    public synchronized boolean overCap() {
        return file.exists() && file.length() > maxBytes;
    }

    public synchronized void compact() throws IOException {
        appendsSinceCompact = 0;
        if (!file.exists()) return;
        List<String> all = readAll();
        if (all.isEmpty()) return;
        List<String> keep = selectNewestFitting(all, maxLines, maxBytes);
        if (keep.size() == all.size() && file.length() <= maxBytes) return;
        atomicRewrite(keep);
    }

    public synchronized List<String> readAll() throws IOException {
        List<String> out = new ArrayList<>();
        if (!file.exists()) return out;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.isEmpty()) out.add(line);
            }
        }
        return out;
    }

    public synchronized List<String> tail(int n) throws IOException {
        List<String> all = readAll();
        if (n <= 0 || all.isEmpty()) return new ArrayList<>();
        if (all.size() <= n) return all;
        return new ArrayList<>(all.subList(all.size() - n, all.size()));
    }

    public synchronized long sizeBytes() {
        return file.exists() ? file.length() : 0L;
    }

    public synchronized int lineCount() throws IOException {
        return readAll().size();
    }

    public static List<String> selectNewestFitting(List<String> all, int maxLines, long maxBytes) {
        int start = Math.max(0, all.size() - maxLines);
        while (start < all.size()) {
            long bytes = 0;
            for (int i = start; i < all.size(); i++) {
                bytes += all.get(i).length() + 1L;
                if (bytes > maxBytes) break;
            }
            if (bytes <= maxBytes) break;
            start++;
        }
        if (start == 0) return all;
        return new ArrayList<>(all.subList(start, all.size()));
    }

    private void atomicRewrite(List<String> lines) throws IOException {
        File tmp = new File(file.getAbsolutePath() + ".tmp");
        FileWriter fw = new FileWriter(tmp, false);
        try {
            for (String l : lines) {
                fw.write(l);
                fw.write('\n');
            }
        } finally {
            fw.close();
        }
        if (!tmp.renameTo(file)) {
            copyFile(tmp, file);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    private static void copyFile(File from, File to) throws IOException {
        try (FileChannel in = new FileInputStream(from).getChannel();
             FileChannel out = new FileOutputStream(to, false).getChannel()) {
            out.transferFrom(in, 0, in.size());
        }
    }
}
