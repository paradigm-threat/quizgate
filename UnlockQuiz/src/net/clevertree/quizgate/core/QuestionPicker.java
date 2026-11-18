package net.clevertree.quizgate.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class QuestionPicker {
    private final Catalog catalog;
    private final Random random;
    private String preferredFirstId;
    /** When set, always use this format (ADB / screenshots / tests). */
    private RenderFormat forcedFormat;

    public QuestionPicker(Catalog catalog, Random random) {
        this.catalog = catalog;
        this.random = random;
    }

    /** For ADB/dev: force the first question id if present in catalog. */
    public QuestionPicker preferFirst(String id) {
        this.preferredFirstId = id;
        return this;
    }

    /** Force render format (e.g. {@link RenderFormat#EQUATION} for digit-pad e2e). */
    public QuestionPicker preferFormat(RenderFormat format) {
        this.forcedFormat = format;
        return this;
    }

    public PresentedQuestion first() {
        Question q;
        if (preferredFirstId != null) {
            q = catalog.byId(preferredFirstId);
            if (q == null) q = pickWeighted(catalog.all());
        } else {
            q = pickWeighted(catalog.all());
        }
        return present(q);
    }

    public PresentedQuestion nextDifferent(String excludeId) {
        List<Question> pool = new ArrayList<>();
        for (Question q : catalog.all()) {
            if (!q.id.equals(excludeId)) pool.add(q);
        }
        Question q = pool.isEmpty() ? catalog.byId(excludeId) : pickWeighted(pool);
        return present(q);
    }

    /**
     * After a child skip: different catalog item when possible, soft bias toward
     * easier ask formats ({@link RenderFormat#TRUE_FALSE}/{@link RenderFormat#CHOICE})
     * and a different op-kind — never a fixed sequence.
     */
    public PresentedQuestion nextAfterSkip(Question previous, RenderFormat previousFormat) {
        String excludeId = previous == null ? "" : previous.id;
        String prevOp = opKind(previous);
        List<Question> pool = new ArrayList<>();
        List<Question> differentOp = new ArrayList<>();
        for (Question q : catalog.all()) {
            if (q.id.equals(excludeId)) continue;
            pool.add(q);
            if (!opKind(q).equals(prevOp)) differentOp.add(q);
        }
        List<Question> use = pool;
        if (!differentOp.isEmpty() && random.nextInt(100) < 65) {
            use = differentOp;
        }
        Question q = use.isEmpty()
                ? (previous != null ? previous : pickWeighted(catalog.all()))
                : pickWeighted(use);
        RenderFormat format = chooseFormatAfterSkip(q, previousFormat);
        return MathPresentation.present(q, format, random);
    }

    PresentedQuestion present(Question q) {
        RenderFormat format = chooseFormat(q);
        return MathPresentation.present(q, format, random);
    }

    private RenderFormat chooseFormat(Question q) {
        if (forcedFormat != null) return forcedFormat;
        // Deterministic ADB / force_qid smoke: stay on equation + digit/MC entry.
        if (preferredFirstId != null) return RenderFormat.EQUATION;
        List<RenderFormat> allowed = RenderFormat.allowedFor(q);
        if (allowed.isEmpty()) return RenderFormat.EQUATION;
        if (allowed.size() == 1) return allowed.get(0);
        return allowed.get(random.nextInt(allowed.size()));
    }

    /**
     * Soft post-skip format mix (approx): prefer easy + different, sometimes easy
     * again, sometimes any different, rarely full random including previous.
     */
    RenderFormat chooseFormatAfterSkip(Question q, RenderFormat previousFormat) {
        if (forcedFormat != null) return forcedFormat;
        if (preferredFirstId != null) return RenderFormat.EQUATION;
        List<RenderFormat> allowed = new ArrayList<>(RenderFormat.allowedFor(q));
        if (allowed.isEmpty()) return RenderFormat.EQUATION;
        if (allowed.size() == 1) return allowed.get(0);

        List<RenderFormat> easy = new ArrayList<>();
        List<RenderFormat> easyDifferent = new ArrayList<>();
        List<RenderFormat> anyDifferent = new ArrayList<>();
        for (RenderFormat f : allowed) {
            boolean easyFmt = f == RenderFormat.TRUE_FALSE || f == RenderFormat.CHOICE;
            if (easyFmt) easy.add(f);
            if (f != previousFormat) anyDifferent.add(f);
            if (easyFmt && f != previousFormat) easyDifferent.add(f);
        }
        int roll = random.nextInt(100);
        if (roll < 55 && !easyDifferent.isEmpty()) {
            return easyDifferent.get(random.nextInt(easyDifferent.size()));
        }
        if (roll < 75 && !easy.isEmpty()) {
            return easy.get(random.nextInt(easy.size()));
        }
        if (roll < 90 && !anyDifferent.isEmpty()) {
            return anyDifferent.get(random.nextInt(anyDifferent.size()));
        }
        return allowed.get(random.nextInt(allowed.size()));
    }

    /** Coarse math family from symbolic text — add / subtract / multiply / other. */
    public static String opKind(Question q) {
        if (q == null || q.symbolic == null) return "other";
        String s = q.symbolic;
        if (s.indexOf('×') >= 0 || s.contains(" x ") || s.contains(" X ")) return "multiply";
        if (s.indexOf('-') >= 0) return "subtract";
        if (s.indexOf('+') >= 0) return "add";
        return "other";
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
