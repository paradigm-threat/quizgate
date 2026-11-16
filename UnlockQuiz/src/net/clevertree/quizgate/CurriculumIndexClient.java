package net.clevertree.quizgate;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Fetches slim curricula index.json when sync is enabled. */
public final class CurriculumIndexClient {
    public static final String DEFAULT_INDEX_URL =
            "https://quizgate.clevertree.net/curricula/index.json";
    private static final String TAG = "CurriculumIndex";

    private CurriculumIndexClient() {}

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

        public IndexEntry(String id, String title, int ageMin, int ageMax, int difficultyRank,
                          int version, String packUrl, String contentSha256, boolean bundled) {
            this.id = id;
            this.title = title;
            this.ageMin = ageMin;
            this.ageMax = ageMax;
            this.difficultyRank = difficultyRank;
            this.version = version;
            this.packUrl = packUrl;
            this.contentSha256 = contentSha256;
            this.bundled = bundled;
        }
    }

    public static boolean isWifi(Context ctx) {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network net = cm.getActiveNetwork();
        if (net == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(net);
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    public static List<IndexEntry> fetchIndex(Context ctx, String indexUrl) throws Exception {
        URL url = new URL(indexUrl == null || indexUrl.isEmpty() ? DEFAULT_INDEX_URL : indexUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(20000);
        conn.setRequestMethod("GET");
        int code = conn.getResponseCode();
        if (code != 200) throw new IllegalStateException("index HTTP " + code);
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        conn.disconnect();
        return parseIndex(sb.toString());
    }

    public static List<IndexEntry> parseIndex(String json) {
        List<IndexEntry> out = new ArrayList<>();
        String needle = "\"curricula\"";
        int k = json.indexOf(needle);
        if (k < 0) return out;
        int lb = json.indexOf('[', k);
        int depth = 0;
        int end = -1;
        for (int i = lb; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) {
                    end = i;
                    break;
                }
            }
        }
        if (lb < 0 || end < 0) return out;
        String inner = json.substring(lb + 1, end);
        for (String obj : splitObjects(inner)) {
            out.add(new IndexEntry(
                    str(obj, "id"),
                    strOpt(obj, "title", str(obj, "id")),
                    intOpt(obj, "ageMin", 0),
                    intOpt(obj, "ageMax", 99),
                    intOpt(obj, "difficultyRank", 100),
                    intOpt(obj, "version", 1),
                    strOpt(obj, "packUrl", ""),
                    strOpt(obj, "contentSha256", ""),
                    boolOpt(obj, "bundled", false)));
        }
        out.sort((a, b) -> Integer.compare(a.difficultyRank, b.difficultyRank));
        return out;
    }

    public static void syncIfEnabled(Context ctx) {
        AppPrefs prefs = new AppPrefs(ctx);
        if (!prefs.isSyncEnabled()) {
            Log.i(TAG, "sync disabled");
            return;
        }
        if (!isWifi(ctx)) {
            Log.i(TAG, "skip sync — not on Wi-Fi");
            return;
        }
        new Thread(() -> {
            try {
                List<IndexEntry> entries = fetchIndex(ctx, prefs.getCurriculumIndexUrl());
                for (IndexEntry e : entries) {
                    if (e.bundled || e.packUrl == null || e.packUrl.isEmpty()) continue;
                    PackInstaller.installFromUrl(ctx, e.packUrl, e.id, e.contentSha256);
                }
                CurriculumStore.clearCache();
                Log.i(TAG, "sync ok entries=" + entries.size());
            } catch (Exception ex) {
                Log.w(TAG, "sync failed: " + ex.getMessage());
            }
        }, "curriculum-sync").start();
    }

    private static List<String> splitObjects(String arrInner) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < arrInner.length()) {
            while (i < arrInner.length() && arrInner.charAt(i) != '{') i++;
            if (i >= arrInner.length()) break;
            int start = i;
            int depth = 0;
            for (; i < arrInner.length(); i++) {
                char c = arrInner.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        i++;
                        out.add(arrInner.substring(start, i));
                        break;
                    }
                }
            }
        }
        return out;
    }

    private static String str(String obj, String key) {
        String v = strOpt(obj, key, null);
        if (v == null) throw new IllegalArgumentException("missing " + key);
        return v;
    }

    private static String strOpt(String obj, String key, String def) {
        String needle = "\"" + key + "\"";
        int k = obj.indexOf(needle);
        if (k < 0) return def;
        int colon = obj.indexOf(':', k + needle.length());
        int p = colon + 1;
        while (p < obj.length() && Character.isWhitespace(obj.charAt(p))) p++;
        if (p < obj.length() && obj.startsWith("null", p)) return def;
        int q1 = obj.indexOf('"', colon + 1);
        if (q1 < 0) return def;
        StringBuilder sb = new StringBuilder();
        for (int i = q1 + 1; i < obj.length(); i++) {
            char c = obj.charAt(i);
            if (c == '\\' && i + 1 < obj.length()) {
                sb.append(obj.charAt(++i));
                continue;
            }
            if (c == '"') break;
            sb.append(c);
        }
        return sb.toString();
    }

    private static int intOpt(String obj, String key, int def) {
        String needle = "\"" + key + "\"";
        int k = obj.indexOf(needle);
        if (k < 0) return def;
        int colon = obj.indexOf(':', k + needle.length());
        int p = colon + 1;
        while (p < obj.length() && Character.isWhitespace(obj.charAt(p))) p++;
        int end = p;
        while (end < obj.length() && (Character.isDigit(obj.charAt(end)) || obj.charAt(end) == '-')) end++;
        if (end == p) return def;
        try {
            return Integer.parseInt(obj.substring(p, end));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean boolOpt(String obj, String key, boolean def) {
        String needle = "\"" + key + "\"";
        int k = obj.indexOf(needle);
        if (k < 0) return def;
        int colon = obj.indexOf(':', k + needle.length());
        int p = colon + 1;
        while (p < obj.length() && Character.isWhitespace(obj.charAt(p))) p++;
        if (obj.startsWith("true", p)) return true;
        if (obj.startsWith("false", p)) return false;
        return def;
    }
}
