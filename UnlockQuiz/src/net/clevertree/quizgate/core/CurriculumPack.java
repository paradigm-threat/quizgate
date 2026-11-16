package net.clevertree.quizgate.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Parsed curriculum.json + manifest metadata. */
public final class CurriculumPack {
    public final String id;
    public final String title;
    public final int ageMin;
    public final int ageMax;
    public final int difficultyRank;
    public final int version;
    public final String contentSha256;
    public final int roundSize;
    public final int earnMinutesPerCorrect;
    public final String wrongBehavior;
    public final boolean consolingUnlock;
    public final int maxWrong;
    public final List<Tier> tiers;
    public final Catalog catalog;
    public final List<ItemMeta> itemMeta;

    public CurriculumPack(String id, String title, int ageMin, int ageMax, int difficultyRank,
                          int version, String contentSha256, int roundSize, int earnMinutesPerCorrect,
                          String wrongBehavior, boolean consolingUnlock, int maxWrong,
                          List<Tier> tiers, Catalog catalog, List<ItemMeta> itemMeta) {
        this.id = id;
        this.title = title;
        this.ageMin = ageMin;
        this.ageMax = ageMax;
        this.difficultyRank = difficultyRank;
        this.version = version;
        this.contentSha256 = contentSha256 == null ? "" : contentSha256;
        this.roundSize = Math.max(1, roundSize);
        this.earnMinutesPerCorrect = Math.max(0, earnMinutesPerCorrect);
        this.wrongBehavior = wrongBehavior == null ? "next" : wrongBehavior;
        this.consolingUnlock = consolingUnlock;
        this.maxWrong = Math.max(1, maxWrong);
        this.tiers = Collections.unmodifiableList(new ArrayList<>(tiers));
        this.catalog = catalog;
        this.itemMeta = Collections.unmodifiableList(new ArrayList<>(itemMeta));
    }

    public Catalog catalogForTier(String tierId) {
        if (tierId == null || tierId.isEmpty() || "all".equals(tierId)) return catalog;
        List<Question> filtered = new ArrayList<>();
        for (int i = 0; i < catalog.size(); i++) {
            Question q = catalog.all().get(i);
            ItemMeta m = itemMeta.get(i);
            if (tierId.equals(m.tier)) filtered.add(q);
        }
        if (filtered.isEmpty()) return catalog;
        return new Catalog(filtered);
    }

    public static final class Tier {
        public final String id;
        public final String label;
        public final int ageMin;
        public final int ageMax;

        public Tier(String id, String label, int ageMin, int ageMax) {
            this.id = id;
            this.label = label;
            this.ageMin = ageMin;
            this.ageMax = ageMax;
        }
    }

    public static final class ItemMeta {
        public final String tier;
        public final String legacyId;

        public ItemMeta(String tier, String legacyId) {
            this.tier = tier == null ? "" : tier;
            this.legacyId = legacyId;
        }
    }
}
