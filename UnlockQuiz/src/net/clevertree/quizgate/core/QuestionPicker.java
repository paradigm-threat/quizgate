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
        List<Question> all = catalog.all();
        return all.get(random.nextInt(all.size()));
    }

    public Question nextDifferent(String excludeId) {
        List<Question> pool = new ArrayList<>();
        for (Question q : catalog.all()) {
            if (!q.id.equals(excludeId)) pool.add(q);
        }
        if (pool.isEmpty()) return catalog.byId(excludeId);
        return pool.get(random.nextInt(pool.size()));
    }
}
