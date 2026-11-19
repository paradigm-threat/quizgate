package net.clevertree.quizgate.core;

import java.util.Calendar;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Quiet hours 20:00–07:00 local: no interval alarms.
 * Morning gate at 07:00.
 * Soft interval = base interval ± jitter (default 40m ± 10m → next fire in 30–50m).
 */
public final class Scheduler {
    public static final int QUIET_START_HOUR = 20;
    public static final int MORNING_HOUR = 7;
    public static final long DEFAULT_INTERVAL_MS = 40L * 60L * 1000L;
    public static final long DEFAULT_JITTER_MS = 10L * 60L * 1000L;
    public static final long MIN_DELAY_MS = 5L * 60L * 1000L;
    public static final long MIN_INTERVAL_MS = 15L * 60L * 1000L;
    public static final long MAX_INTERVAL_MS = 3L * 60L * 60L * 1000L;
    public static final long MAX_JITTER_MS = 90L * 60L * 1000L;
    public static final long STEP_MS = 15L * 60L * 1000L;

    private final TimeZone zone;
    private final long intervalMs;
    private final long jitterMs;
    private final Random random;

    public Scheduler(TimeZone zone, long intervalMs) {
        this(zone, intervalMs, 0L, null);
    }

    public Scheduler(TimeZone zone, long intervalMs, long jitterMs) {
        this(zone, intervalMs, jitterMs, null);
    }

    public Scheduler(TimeZone zone, long intervalMs, long jitterMs, Random random) {
        this.zone = zone;
        // Do not clamp here — minutes-jar delays may be shorter than MIN_INTERVAL_MS.
        this.intervalMs = Math.max(0L, intervalMs);
        this.jitterMs = Math.max(0L, jitterMs);
        this.random = random;
    }

    public Scheduler(TimeZone zone) {
        this(zone, DEFAULT_INTERVAL_MS, DEFAULT_JITTER_MS, null);
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public long getJitterMs() {
        return jitterMs;
    }

    public boolean isQuietHours(long whenMs) {
        Calendar c = cal(whenMs);
        int h = c.get(Calendar.HOUR_OF_DAY);
        return h >= QUIET_START_HOUR || h < MORNING_HOUR;
    }

    /** Next fire after an unlock/bypass/consoling at `fromMs`, with interval ± jitter. */
    public long nextAfterUnlock(long fromMs) {
        long candidate = fromMs + sampleDelayMs();
        if (!isQuietHours(candidate)) {
            return candidate;
        }
        // Land in quiet → schedule next morning 07:00
        return nextMorningAtOrAfter(fromMs);
    }

    /**
     * Uniform delay in [interval − jitter, interval + jitter], floored at {@link #MIN_DELAY_MS}.
     * With jitter 0 this is exactly {@code intervalMs}.
     */
    public long sampleDelayMs() {
        long min = Math.max(MIN_DELAY_MS, intervalMs - jitterMs);
        long max = Math.max(min, intervalMs + jitterMs);
        if (max == min) return min;
        long span = max - min;
        long roll = nextLong(span + 1);
        return min + roll;
    }

    public long windowMinMs() {
        return Math.max(MIN_DELAY_MS, intervalMs - jitterMs);
    }

    public long windowMaxMs() {
        return Math.max(windowMinMs(), intervalMs + jitterMs);
    }

    /** Next 07:00 strictly after fromMs if already past today's morning, else today's 07:00 if still before. */
    public long nextMorningAtOrAfter(long fromMs) {
        Calendar c = cal(fromMs);
        c.set(Calendar.HOUR_OF_DAY, MORNING_HOUR);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= fromMs) {
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        return c.getTimeInMillis();
    }

    /** Morning alarm for calendar day of `now`, or next day if already past 07:00. */
    public long nextMorningAlarm(long nowMs) {
        return nextMorningAtOrAfter(nowMs);
    }

    public boolean needsMorningGate(long nowMs, String lastClearedDayKey) {
        String today = dayKey(nowMs);
        return lastClearedDayKey == null || !today.equals(lastClearedDayKey);
    }

    public String dayKey(long whenMs) {
        Calendar c = cal(whenMs);
        return c.get(Calendar.YEAR) + "-" + (c.get(Calendar.MONTH) + 1) + "-" + c.get(Calendar.DAY_OF_MONTH);
    }

    public static long clampInterval(long ms) {
        if (ms < MIN_INTERVAL_MS) return MIN_INTERVAL_MS;
        if (ms > MAX_INTERVAL_MS) return MAX_INTERVAL_MS;
        return ms;
    }

    public static long clampJitter(long ms) {
        if (ms < 0) return 0;
        if (ms > MAX_JITTER_MS) return MAX_JITTER_MS;
        return ms;
    }

    public static long bump(long ms, int steps) {
        return ms + steps * STEP_MS;
    }

    public static String formatMinutes(long ms) {
        long mins = Math.max(0, ms / 60_000L);
        return mins + " min";
    }

    /**
     * Whole minutes until {@code nextAtMs}, rounded up.
     * Returns {@code -1} when unset, {@code 0} when due or past.
     */
    public static long minutesUntil(long nextAtMs, long nowMs) {
        if (nextAtMs <= 0L) return -1L;
        long rem = nextAtMs - nowMs;
        if (rem <= 0L) return 0L;
        return (rem + 59_999L) / 60_000L;
    }

    public static String formatWindow(long intervalMs, long jitterMs) {
        Scheduler s = new Scheduler(TimeZone.getDefault(), intervalMs, jitterMs, null);
        return formatMinutes(s.windowMinMs()) + " – " + formatMinutes(s.windowMaxMs());
    }

    private long nextLong(long boundExclusive) {
        if (boundExclusive <= 1) return 0;
        if (boundExclusive > Integer.MAX_VALUE) {
            // Defensive: interval windows are minutes-scale and fit in int.
            boundExclusive = Integer.MAX_VALUE;
        }
        if (random != null) {
            return random.nextInt((int) boundExclusive);
        }
        return ThreadLocalRandom.current().nextInt((int) boundExclusive);
    }

    private Calendar cal(long ms) {
        Calendar c = Calendar.getInstance(zone);
        c.setTimeInMillis(ms);
        return c;
    }
}
