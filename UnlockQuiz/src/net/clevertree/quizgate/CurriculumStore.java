package net.clevertree.quizgate;

import android.content.Context;

import net.clevertree.quizgate.core.CurriculumPack;
import net.clevertree.quizgate.core.CurriculumPackSelect;
import net.clevertree.quizgate.core.CurriculumParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Loads bundled + installed curriculum packs; prefers the higher version. */
public final class CurriculumStore {
    private static CurriculumPack activeCached;
    private static String activeCacheKey;

    private CurriculumStore() {}

    public static File installedDir(Context ctx) {
        return new File(ctx.getFilesDir(), "curricula");
    }

    public static synchronized void clearCache() {
        activeCached = null;
        activeCacheKey = null;
        CatalogLoader.clearCache();
    }

    public static CurriculumPack getActive(Context ctx) {
        AppPrefs prefs = new AppPrefs(ctx);
        String id = prefs.getActiveCurriculumId();
        String key = id + "|" + prefs.getActiveTier() + "|L" + prefs.getCurriculumLevel();
        if (activeCached != null && key.equals(activeCacheKey)) return activeCached;
        CurriculumPack pack = loadById(ctx, id);
        if (pack == null) pack = loadBundled(ctx, "basic-math");
        if (pack == null) throw new RuntimeException("No curriculum available");
        activeCached = pack;
        activeCacheKey = key;
        return pack;
    }

    /**
     * Prefer the newer of installed {@code files/curricula/{id}/} vs APK assets.
     * Tie-break: matching contentSha prefers installed (synced offline copy).
     */
    public static CurriculumPack loadById(Context ctx, String id) {
        if (id == null || id.isEmpty()) id = "basic-math";
        CurriculumPack installed = null;
        File installedDir = new File(installedDir(ctx), id);
        if (new File(installedDir, "curriculum.json").isFile()) {
            try {
                installed = loadFromDir(installedDir);
            } catch (Exception ignored) {
                installed = null;
            }
        }
        CurriculumPack bundled = loadBundled(ctx, id);
        return CurriculumPackSelect.preferNewer(installed, bundled);
    }

    /** Highest local version for {@code id} (installed or bundled). */
    public static int localVersion(Context ctx, String id) {
        CurriculumPack p = loadById(ctx, id);
        return p == null ? 0 : p.version;
    }

    public static String localContentSha(Context ctx, String id) {
        CurriculumPack p = loadById(ctx, id);
        return p == null ? "" : (p.contentSha256 == null ? "" : p.contentSha256);
    }

    /**
     * Version of the unpacked pack under {@code files/curricula/{id}/} only
     * (0 if missing/unreadable). Sync compares the index against this so a
     * current APK still downloads a shared offline copy when packUrl is set.
     */
    public static int installedVersion(Context ctx, String id) {
        CurriculumPack p = loadInstalledOnly(ctx, id);
        return p == null ? 0 : p.version;
    }

    public static String installedContentSha(Context ctx, String id) {
        CurriculumPack p = loadInstalledOnly(ctx, id);
        return p == null ? "" : (p.contentSha256 == null ? "" : p.contentSha256);
    }

    private static CurriculumPack loadInstalledOnly(Context ctx, String id) {
        if (id == null || id.isEmpty()) id = "basic-math";
        File installedDir = new File(installedDir(ctx), id);
        if (!new File(installedDir, "curriculum.json").isFile()) return null;
        try {
            return loadFromDir(installedDir);
        } catch (Exception e) {
            return null;
        }
    }

    public static CurriculumPack loadBundled(Context ctx, String id) {
        try {
            String base = "curricula/" + id + "/";
            String manifest = readAsset(ctx, base + "manifest.json");
            String curriculum = readAsset(ctx, base + "curriculum.json");
            return CurriculumParser.parse(manifest, curriculum);
        } catch (Exception e) {
            return null;
        }
    }

    public static CurriculumPack loadFromDir(File dir) {
        try {
            String manifest = readFile(new File(dir, "manifest.json"));
            String curriculum = readFile(new File(dir, "curriculum.json"));
            return CurriculumParser.parse(manifest, curriculum);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load pack from " + dir, e);
        }
    }

    public static List<CurriculumPack> listInstalledAndBundled(Context ctx) {
        List<CurriculumPack> out = new ArrayList<>();
        CurriculumPack best = loadById(ctx, "basic-math");
        if (best != null) out.add(best);
        File root = installedDir(ctx);
        File[] kids = root.listFiles();
        if (kids != null) {
            for (File d : kids) {
                if (!d.isDirectory()) continue;
                if ("basic-math".equals(d.getName())) continue;
                try {
                    out.add(loadById(ctx, d.getName()));
                } catch (Exception ignored) {}
            }
        }
        out.sort((a, b) -> Integer.compare(a.difficultyRank, b.difficultyRank));
        return out;
    }

    private static String readAsset(Context ctx, String path) throws Exception {
        InputStream in = ctx.getAssets().open(path);
        return readStream(in);
    }

    private static String readFile(File f) throws Exception {
        return readStream(new FileInputStream(f));
    }

    private static String readStream(InputStream in) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }
}
