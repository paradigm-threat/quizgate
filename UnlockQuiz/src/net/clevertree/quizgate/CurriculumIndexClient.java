package net.clevertree.quizgate;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

import net.clevertree.quizgate.core.CurriculumIndexParse;
import net.clevertree.quizgate.core.CurriculumPackSelect;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fetches slim curricula index.json and installs newer shared packs into
 * {@code files/curricula/{id}/} for offline use. Bundled APK packs are the
 * fallback; {@code bundled:true} in the index still allows updates when
 * {@code packUrl} is present and the index version/sha is newer.
 */
public final class CurriculumIndexClient {
    public static final String DEFAULT_INDEX_URL =
            "https://quizgate.app/curricula/index.json";
    /** Default throttle between background open-path syncs. */
    public static final long DEFAULT_SYNC_INTERVAL_MS = 6L * 60L * 60L * 1000L;
    private static final String TAG = "CurriculumIndex";
    private static final AtomicBoolean SYNC_RUNNING = new AtomicBoolean(false);

    private CurriculumIndexClient() {}

    /** Adapter over {@link CurriculumIndexParse.Entry} for existing call sites. */
    public static final class IndexEntry {
        public final String id;
        public final String title;
        public final int ageMin;
        public final int ageMax;
        public final int difficultyRank;
        public final int version;
        public final String packUrl;
        public final String contentSha256;
        public final boolean bundled;

        public IndexEntry(CurriculumIndexParse.Entry e) {
            this.id = e.id;
            this.title = e.title;
            this.ageMin = e.ageMin;
            this.ageMax = e.ageMax;
            this.difficultyRank = e.difficultyRank;
            this.version = e.version;
            this.packUrl = e.packUrl;
            this.contentSha256 = e.contentSha256;
            this.bundled = e.bundled;
        }
    }

    public static boolean hasNetwork(Context ctx) {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network net = cm.getActiveNetwork();
        if (net == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(net);
        return caps != null && (
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    /** @deprecated Use {@link #hasNetwork}. */
    public static boolean isWifi(Context ctx) {
        return hasNetwork(ctx);
    }

    public static List<IndexEntry> fetchIndex(Context ctx, String indexUrl) throws Exception {
        java.net.URL url = new java.net.URL(
                indexUrl == null || indexUrl.isEmpty() ? DEFAULT_INDEX_URL : indexUrl);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(20000);
        conn.setRequestMethod("GET");
        int code = conn.getResponseCode();
        if (code != 200) throw new IllegalStateException("index HTTP " + code);
        java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        conn.disconnect();
        return parseIndex(sb.toString());
    }

    public static List<IndexEntry> parseIndex(String json) {
        List<IndexEntry> out = new ArrayList<>();
        for (CurriculumIndexParse.Entry e : CurriculumIndexParse.parse(json)) {
            out.add(new IndexEntry(e));
        }
        return out;
    }

    public static boolean needsInstall(Context ctx, IndexEntry e) {
        if (e == null) return false;
        // Compare index to installed dir only — APK bundled is fallback, not
        // a reason to skip downloading the shared offline copy.
        return CurriculumPackSelect.needsInstall(
                e.version, e.contentSha256,
                CurriculumStore.installedVersion(ctx, e.id),
                CurriculumStore.installedContentSha(ctx, e.id),
                e.packUrl);
    }

    /** Setup / Sync now: ignore throttle. */
    public static void syncIfEnabled(Context ctx) {
        syncIfEnabled(ctx, true);
    }

    /**
     * @param force when false, skip if last sync was within
     *              {@link #DEFAULT_SYNC_INTERVAL_MS} (open-path throttle).
     */
    public static void syncIfEnabled(Context ctx, boolean force) {
        if (ctx == null) return;
        final Context app = ctx.getApplicationContext();
        AppPrefs prefs = new AppPrefs(app);
        if (!prefs.isSyncEnabled()) {
            Log.i(TAG, "sync disabled");
            return;
        }
        if (!hasNetwork(app)) {
            Log.i(TAG, "skip sync — no network");
            return;
        }
        if (!force) {
            long last = prefs.getLastCurriculumSyncAt();
            if (last > 0 && System.currentTimeMillis() - last < DEFAULT_SYNC_INTERVAL_MS) {
                Log.i(TAG, "skip sync — throttled");
                return;
            }
        }
        if (!SYNC_RUNNING.compareAndSet(false, true)) {
            Log.i(TAG, "sync busy");
            return;
        }
        new Thread(() -> {
            try {
                runSync(app);
            } finally {
                SYNC_RUNNING.set(false);
            }
        }, "curriculum-sync").start();
    }

    private static void runSync(Context app) {
        AppPrefs prefs = new AppPrefs(app);
        try {
            List<IndexEntry> entries = fetchIndex(app, prefs.getCurriculumIndexUrl());
            int installed = 0;
            for (IndexEntry e : entries) {
                // bundled:true = also in APK; still update when packUrl is newer.
                if (!needsInstall(app, e)) {
                    Log.i(TAG, "up-to-date id=" + e.id + " v=" + e.version);
                    continue;
                }
                Log.i(TAG, "install id=" + e.id + " v=" + e.version + " url=" + e.packUrl);
                PackInstaller.installFromUrl(app, e.packUrl, e.id, e.contentSha256);
                installed++;
            }
            CurriculumStore.clearCache();
            prefs.setLastCurriculumSyncAt(System.currentTimeMillis());
            Log.i(TAG, "sync ok entries=" + entries.size() + " installed=" + installed);
        } catch (Exception ex) {
            Log.w(TAG, "sync failed: " + ex.getMessage());
        }
    }
}
