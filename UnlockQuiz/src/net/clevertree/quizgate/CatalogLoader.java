package net.clevertree.quizgate;

import android.content.Context;

import net.clevertree.quizgate.core.Catalog;
import net.clevertree.quizgate.core.CurriculumPack;

public final class CatalogLoader {
    private static Catalog cached;

    private CatalogLoader() {}

    public static synchronized void clearCache() {
        cached = null;
    }

    public static synchronized Catalog get(Context ctx) {
        if (cached != null) return cached;
        CurriculumPack pack = CurriculumStore.getActive(ctx);
        AppPrefs prefs = new AppPrefs(ctx);
        cached = pack.catalogFiltered(prefs.getActiveTier(), prefs.getCurriculumLevel());
        return cached;
    }
}
