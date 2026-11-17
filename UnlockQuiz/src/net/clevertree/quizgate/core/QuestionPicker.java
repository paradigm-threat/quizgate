package net.clevertree.quizgate.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class QuestionPicker {
    private final Catalog catalog;
    private final Random random;
    private String preferredFirstId;

    public QuestionPicker(Catalog catalog, Random random) {
        this.catalog = catalog;
        this.random = random;
    }

    /** For ADB/dev: force the first question id if present in catalog. */
    public QuestionPicker preferFirst(String id) {
        this.preferredFirstId = id;
        return this;
    }

    public Question first() {
        if (preferredFirstId != null) {
            Question q = catalog.byId(preferredFirstId);
            if (q != null) return q;
        }
        return pickWeighted(catalog.all());
    }

    public Question nextDifferent(String excludeId) {
        List<Question> pool = new ArrayList<>();
        for (Question q : catalog.all()) {
            if (!q.id.equals(excludeId)) pool.add(q);
        }
        if (pool.isEmpty()) return catalog.byId(excludeId);
        return pickWeighted(pool);
    }

    /** Uniform among equal weights; lower {@link Question#weight} stays rarer as the pool grows. */
    Question pickWeighted(List<Question> pool) {
        if (pool == null || pool.isEmpty()) {
            throw new IllegalStateException("empty question pool");
        }
        long total = 0L;
        for (Question q : pool) total += q.weight;
        if (total <= 0L) return pool.get(random.nextInt(pool.size()));
        long ticket = nextLong(total);
        long acc = 0L;
        for (Question q : pool) {
            acc += q.weight;
            if (ticket < acc) return q;
        }
        return pool.get(pool.size() - 1);
    }

    private long nextLong(long bound) {
        if (bound <= 0L) return 0L;
        if (bound <= Integer.MAX_VALUE) return random.nextInt((int) bound);
        // Rare: huge weight sums — fall back to rejection sampling via doubles.
        return (long) (random.nextDouble() * bound);
    }
}
