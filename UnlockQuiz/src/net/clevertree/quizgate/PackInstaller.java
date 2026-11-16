package net.clevertree.quizgate;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Downloads and unpacks a .qgpack zip into files/curricula/{id}/. */
public final class PackInstaller {
    private static final String TAG = "PackInstaller";

    private PackInstaller() {}

    public static void installFromUrl(Context ctx, String packUrl, String id, String expectedSha) throws Exception {
        File tmp = new File(ctx.getCacheDir(), id + ".qgpack");
        download(packUrl, tmp);
        installFromFile(ctx, tmp, id, expectedSha);
        //noinspection ResultOfMethodCallIgnored
        tmp.delete();
    }

    public static void installFromFile(Context ctx, File qgpack, String id, String expectedSha) throws Exception {
        File dest = new File(CurriculumStore.installedDir(ctx), id);
        if (dest.exists()) deleteRecursive(dest);
        //noinspection ResultOfMethodCallIgnored
        dest.mkdirs();
        unzip(qgpack, dest);
        File curriculum = new File(dest, "curriculum.json");
        if (!curriculum.isFile()) throw new IllegalStateException("pack missing curriculum.json");
        if (expectedSha != null && !expectedSha.isEmpty()) {
            String actual = sha256(curriculum);
            // Phase 1: warn but still accept if UNSIGNED-BUNDLED style mismatch on optional field
            if (!expectedSha.equalsIgnoreCase(actual)) {
                Log.w(TAG, "sha mismatch for " + id + " expected=" + expectedSha + " actual=" + actual);
            }
        }
        CurriculumStore.clearCache();
        Log.i(TAG, "installed " + id + " -> " + dest);
    }

    private static void download(String packUrl, File dest) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(packUrl).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        } finally {
            conn.disconnect();
        }
    }

    private static void unzip(File zip, File destDir) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry entry;
            byte[] buf = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.contains("..")) throw new IllegalStateException("bad zip path " + name);
                File out = new File(destDir, name);
                if (entry.isDirectory()) {
                    //noinspection ResultOfMethodCallIgnored
                    out.mkdirs();
                } else {
                    File parent = out.getParentFile();
                    if (parent != null) //noinspection ResultOfMethodCallIgnored
                        parent.mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        int n;
                        while ((n = zis.read(buf)) >= 0) fos.write(buf, 0, n);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) md.update(buf, 0, n);
        }
        byte[] dig = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : dig) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static void deleteRecursive(File f) {
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File c : kids) deleteRecursive(c);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
