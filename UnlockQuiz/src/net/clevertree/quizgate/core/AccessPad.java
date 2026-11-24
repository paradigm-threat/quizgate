package net.clevertree.quizgate.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Glyph lists for the shared digit/letter pad used by {@code access} curriculum items.
 * L0–L1 letters: fixed A–I. L2+: contiguous window centered on the answer. L3: shuffled /
 * non-adjacent distractors.
 */
public final class AccessPad {
    public static final int LETTER_KEYS = 9;
    public static final char LETTER_LO = 'A';
    public static final char LETTER_HI_EARLY = 'I';
    public static final char LETTER_HI = 'Z';

    private AccessPad() {}

    public static boolean isAccessType(Question q) {
        return q != null && "access".equals(q.type);
    }

    public static boolean isLetterToken(String token) {
        if (token == null) return false;
        String t = token.trim();
        return t.length() == 1 && Character.isLetter(t.charAt(0));
    }

    public static boolean isDigitToken(String token) {
        if (token == null) return false;
        String t = token.trim();
        if (t.isEmpty()) return false;
        try {
            Integer.parseInt(t);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Fixed ordered A–I for early letter pads (no zero row). */
    public static List<String> fixedLettersAI() {
        List<String> out = new ArrayList<>(LETTER_KEYS);
        for (char c = LETTER_LO; c <= LETTER_HI_EARLY; c++) {
            out.add(String.valueOf(c));
        }
        return out;
    }

    /** Digits 0–9 in phone-pad order (1–9 then 0). */
    public static List<String> digitsPhoneOrder() {
        List<String> out = new ArrayList<>(10);
        for (int d = 1; d <= 9; d++) out.add(String.valueOf(d));
        out.add("0");
        return out;
    }

    /**
     * Contiguous 9-letter window centered on {@code correct}, clamped to A–Z, ascending.
     */
    public static List<String> centeredLetterWindow(String correct) {
        char ans = normalizeLetter(correct);
        int half = LETTER_KEYS / 2;
        int start = ans - half;
        int end = start + LETTER_KEYS - 1;
        if (start < LETTER_LO) {
            start = LETTER_LO;
            end = start + LETTER_KEYS - 1;
        }
        if (end > LETTER_HI) {
            end = LETTER_HI;
            start = end - LETTER_KEYS + 1;
            if (start < LETTER_LO) start = LETTER_LO;
        }
        List<String> out = new ArrayList<>(LETTER_KEYS);
        for (char c = (char) start; c <= end && out.size() < LETTER_KEYS; c++) {
            out.add(String.valueOf(c));
        }
        return out;
    }

    /**
     * L3 letter chaos: correct plus 8 letters that are mostly non-adjacent, then shuffled.
     */
    public static List<String> randomLetters(String correct, Random random) {
        if (random == null) throw new IllegalArgumentException("random");
        char ans = normalizeLetter(correct);
        List<Character> pool = new ArrayList<>();
        for (char c = LETTER_LO; c <= LETTER_HI; c++) {
            if (c == ans) continue;
            if (Math.abs(c - ans) <= 1) continue; // prefer non-adjacent
            pool.add(c);
        }
        if (pool.size() < LETTER_KEYS - 1) {
            pool.clear();
            for (char c = LETTER_LO; c <= LETTER_HI; c++) {
                if (c != ans) pool.add(c);
            }
        }
        Collections.shuffle(pool, random);
        List<String> out = new ArrayList<>(LETTER_KEYS);
        out.add(String.valueOf(ans));
        for (int i = 0; i < pool.size() && out.size() < LETTER_KEYS; i++) {
            out.add(String.valueOf(pool.get(i)));
        }
        Collections.shuffle(out, random);
        return out;
    }

    /** L3 number chaos: shuffle 0–9 layout. */
    public static List<String> randomDigits(Random random) {
        if (random == null) throw new IllegalArgumentException("random");
        List<String> out = digitsPhoneOrder();
        Collections.shuffle(out, random);
        return out;
    }

    /**
     * Pick glyph list for an access equation ask.
     * @param curriculumLevel device {@code curriculum_level} (max item level included)
     */
    public static List<String> glyphsFor(Question q, int curriculumLevel, Random random) {
        if (q == null) return digitsPhoneOrder();
        String correct = q.correct;
        boolean letter = isLetterToken(correct);
        if (curriculumLevel >= 3) {
            return letter ? randomLetters(correct, random) : randomDigits(random);
        }
        if (letter) {
            if (curriculumLevel >= 2) {
                return centeredLetterWindow(correct);
            }
            return fixedLettersAI();
        }
        return digitsPhoneOrder();
    }

    public static char normalizeLetter(String token) {
        if (!isLetterToken(token)) {
            throw new IllegalArgumentException("letter token required: " + token);
        }
        return Character.toUpperCase(token.trim().charAt(0));
    }

    /** Mutate a letter token by ±1..maxOffset for true/false false statements. */
    public static String mutateLetter(String correct, int maxOffset, Random random) {
        char ans = normalizeLetter(correct);
        int offset = 1 + random.nextInt(Math.max(1, maxOffset));
        boolean neg = random.nextBoolean();
        int shown = neg ? ans - offset : ans + offset;
        if (shown < LETTER_LO) shown = ans + offset;
        if (shown > LETTER_HI) shown = ans - offset;
        if (shown == ans) {
            shown = ans < LETTER_HI ? ans + 1 : ans - 1;
        }
        return String.valueOf((char) shown);
    }

    public static String normalizeToken(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        if (t.isEmpty()) return t;
        if (isLetterToken(t)) return String.valueOf(Character.toUpperCase(t.charAt(0)));
        try {
            return String.valueOf(Integer.parseInt(t));
        } catch (NumberFormatException e) {
            return t.toLowerCase(Locale.US);
        }
    }
}
