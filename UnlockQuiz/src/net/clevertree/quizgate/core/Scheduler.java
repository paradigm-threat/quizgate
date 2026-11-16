package net.clevertree.quizgate.core;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Quiet hours 20:00–07:00 local: no interval alarms.
 * Morning gate at 07:00. Interval = +intervalMs after unlock (default 1h).
 */
public final class Scheduler {
    public static final int QUIET_START_HOUR = 20;
    public static final int MORNING_HOUR = 7;
    public static final long DEFAULT_INTERVAL_MS = 1L * 60L * 60L * 1000L;

    private final TimeZone zone;
    private final long intervalMs;

    public Scheduler(TimeZone zone, long intervalMs) {
        this.zone = zone;
        this.intervalMs = intervalMs;
    }

    public Scheduler(TimeZone zone) {
        this(zone, DEFAULT_INTERVAL_MS);
    }

    public boolean isQuietHours(long whenMs) {
        Calendar c = cal(whenMs);
        int h = c.get(Calendar.HOUR_OF_DAY);
        return h >= QUIET_START_HOUR || h < MORNING_HOUR;
    }

    /** Next fire after an unlock/bypass/consoling at `fromMs`. */
    public long nextAfterUnlock(long fromMs) {
        long candidate = fromMs + intervalMs;
        if (!isQuietHours(candidate)) {
            return candidate;
        }
        // Land in quiet → schedule next morning 07:00
        return nextMorningAtOrAfter(fromMs);
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

    private Calendar cal(long ms) {
        Calendar c = Calendar.getInstance(zone);
        c.setTimeInMillis(ms);
        return c;
    }
}
