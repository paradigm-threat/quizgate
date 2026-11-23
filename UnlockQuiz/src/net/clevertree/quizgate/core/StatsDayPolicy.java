package net.clevertree.quizgate.core;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Stats "day" boundary: local wall time before the barrier hour belongs to the
 * <em>previous</em> calendar date. Default {@link #DEFAULT_BARRIER_HOUR} (05:00)
 * so late-night use rolls into yesterday until morning. Admin-tunable via profile
 * {@code settings.stats_day_barrier_hour}; omit/blank → this default.
 */
public final class StatsDayPolicy {
    /** Repo / client default — 5 AM local. */
    public static final int DEFAULT_BARRIER_HOUR = 5;
    public static final int MIN_HOUR = 0;
    public static final int MAX_HOUR = 23;

    private StatsDayPolicy() {}

    /**
     * Clamp to 0–23; invalid values fall back to {@link #DEFAULT_BARRIER_HOUR}.
     * Midnight barrier ({@code 0}) is the classic calendar-day split.
     */
    public static int parseBarrierHour(int raw) {
        if (raw < MIN_HOUR || raw > MAX_HOUR) return DEFAULT_BARRIER_HOUR;
        return raw;
    }

    /**
     * Local stats day key {@code YYYY-MM-DD} using {@code barrierHour}: times with
     * {@code HOUR_OF_DAY < barrier} map to the previous calendar day.
     */
    public static String dayKey(long whenMs, TimeZone zone, int barrierHour) {
        int barrier = parseBarrierHour(barrierHour);
        TimeZone tz = zone == null ? TimeZone.getDefault() : zone;
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(whenMs);
        if (c.get(Calendar.HOUR_OF_DAY) < barrier) {
            c.add(Calendar.DAY_OF_YEAR, -1);
        }
        return String.format(Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH));
    }

    /**
     * Instant of the next stats-day barrier strictly after {@code whenMs}
     * (local wall clock at {@code barrierHour}:00:00).
     */
    public static long nextBarrierMs(long whenMs, TimeZone zone, int barrierHour) {
        int barrier = parseBarrierHour(barrierHour);
        TimeZone tz = zone == null ? TimeZone.getDefault() : zone;
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(whenMs);
        c.set(Calendar.HOUR_OF_DAY, barrier);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= whenMs) {
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        return c.getTimeInMillis();
    }
}
