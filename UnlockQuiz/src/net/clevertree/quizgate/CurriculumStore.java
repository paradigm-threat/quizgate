package net.clevertree.quizgate;

import android.content.Context;

import net.clevertree.quizgate.core.CurriculumPack;
import net.clevertree.quizgate.core.CurriculumParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Loads bundled + installed curriculum packs. */
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
        String key = id + "|" + prefs.getActiveTier();
        if (activeCached != null && key.equals(activeCacheKey)) return activeCached;
        CurriculumPack pack = loadById(ctx, id);
        if (pack == null) pack = loadBundled(ctx, "basic-math");
        if (pack == null) throw new RuntimeException("No curriculum available");
        activeCached = pack;
        activeCacheKey = key;
        return pack;
    }

    public static CurriculumPack loadById(Context ctx, String id) {
        if (id == null || id.isEmpty()) id = "basic-math";
        File installed = new File(installedDir(ctx), id);
        if (new File(installed, "curriculum.json").isFile()) {
            return loadFromDir(installed);
        }
        return loadBundled(ctx, id);
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
        CurriculumPack bundled = loadBundled(ctx, "basic-math");
        if (bundled != null) out.add(bundled);
        File root = installedDir(ctx);
        File[] kids = root.listFiles();
        if (kids != null) {
            for (File d : kids) {
                if (!d.isDirectory()) continue;
                if ("basic-math".equals(d.getName()) && bundled != null) continue;
                try {
                    out.add(loadFromDir(d));
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
