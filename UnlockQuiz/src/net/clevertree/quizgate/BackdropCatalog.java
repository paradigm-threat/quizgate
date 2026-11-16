package net.clevertree.quizgate;

import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Bundled kid-friendly success/fail transition images under assets/backdrop_catalog/. */
public final class BackdropCatalog {
    public static final String ASSET_SCHEME = "asset";
    private static final String TAG = AlarmScheduler.TAG;
    private static final String ROOT = "backdrop_catalog/";

    private BackdropCatalog() {}

    public static Uri assetUri(String relativePath) {
        return Uri.parse(ASSET_SCHEME + ":///" + ROOT + relativePath);
    }

    public static List<Uri> listSuccess(Context ctx) {
        return listMood(ctx, "success");
    }

    public static List<Uri> listFail(Context ctx) {
        return listMood(ctx, "fail");
    }

    public static List<Uri> listAll(Context ctx) {
        List<Uri> out = new ArrayList<>();
        out.addAll(listSuccess(ctx));
        out.addAll(listFail(ctx));
        return out;
    }

    private static List<Uri> listMood(Context ctx, String mood) {
        List<Uri> out = new ArrayList<>();
        try {
            String json = readAsset(ctx, ROOT + "manifest.json");
            String arr = extractArray(json, mood);
            if (arr != null) {
                for (String path : strLiterals(arr)) {
                    if (assetExists(ctx, ROOT + path)) out.add(assetUri(path));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "backdrop manifest read failed", e);
        }
        if (out.isEmpty()) {
            // Fallback: list directory
            try {
                String[] files = ctx.getAssets().list(ROOT + mood);
                if (files != null) {
                    for (String f : files) {
                        String lower = f.toLowerCase();
                        if (lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".jpg")) {
                            out.add(assetUri(mood + "/" + f));
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "backdrop dir list failed", e);
            }
        }
        Log.i(TAG, "bundled backdrop " + mood + "=" + out.size());
        return out;
    }

    private static boolean assetExists(Context ctx, String path) {
        try (InputStream in = ctx.getAssets().open(path)) {
            return in != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static String readAsset(Context ctx, String path) throws Exception {
        AssetManager am = ctx.getAssets();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(am.open(path), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }

    private static String extractArray(String json, String key) {
        String needle = "\"" + key + "\"";
        int k = json.indexOf(needle);
        if (k < 0) return null;
        int lb = json.indexOf('[', k);
        if (lb < 0) return null;
        int depth = 0;
        for (int i = lb; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return json.substring(lb + 1, i);
            }
        }
        return null;
    }

    private static List<String> strLiterals(String inner) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < inner.length()) {
            while (i < inner.length() && inner.charAt(i) != '"') i++;
            if (i >= inner.length()) break;
            i++;
            StringBuilder sb = new StringBuilder();
            while (i < inner.length()) {
                char c = inner.charAt(i++);
                if (c == '\\' && i < inner.length()) {
                    sb.append(inner.charAt(i++));
                    continue;
                }
                if (c == '"') break;
                sb.append(c);
            }
            out.add(sb.toString());
        }
        return out;
    }
}
