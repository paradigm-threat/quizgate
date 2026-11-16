package net.clevertree.quizgate;

import android.content.Context;

import net.clevertree.quizgate.core.Catalog;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class CatalogLoader {
    private static Catalog cached;

    private CatalogLoader() {}

    public static synchronized Catalog get(Context ctx) {
        if (cached != null) return cached;
        try {
            InputStream in = ctx.getAssets().open("questions.json");
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            cached = Catalog.fromJson(sb.toString());
            return cached;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load questions.json", e);
        }
    }
}
