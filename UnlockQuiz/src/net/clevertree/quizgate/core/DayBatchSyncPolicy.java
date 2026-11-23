package net.clevertree.quizgate.core;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * Decides which local day rollups still need an Intake upload.
 *
 * <ul>
 *   <li>Closed days ({@code day < today}): upload once, then remember forever —
 *       including <em>zero-ask</em> days synthesized when missing from {@code days.jsonl}.</li>
 *   <li>Today: at most one snapshot per local calendar day (not permanently closed
 *       until the day rolls — so the next open can push the final totals).</li>
 * </ul>
 *
 * No schedule / alarm — callers run this when the app is open.
 */
public final class DayBatchSyncPolicy {
    /** Closed barrier days to always consider even with no quiz rollup. */
    public static final int ZERO_DAY_LOOKBACK = 3;

    private DayBatchSyncPolicy() {}

    /**
     * @param localDays        day keys present in {@code days.jsonl} (any order)
     * @param uploadedClosed   days already successfully uploaded as closed
     * @param todayKey         local calendar today ({@link StatsStore#dayKey})
     * @param todaySnapshotDay day key of the last successful <em>today</em> snapshot
     *                         (empty if none this calendar day)
     */
    public static List<String> daysNeedingUpload(
            Collection<String> localDays,
            Set<String> uploadedClosed,
            String todayKey,
            String todaySnapshotDay) {
        return daysNeedingUpload(
                localDays, uploadedClosed, todayKey, todaySnapshotDay,
                ZERO_DAY_LOOKBACK, null);
    }

    /**
     * @param closedLookback number of prior closed days to synthesize if missing
     *                       from {@code localDays} (0 = only keys already present)
     * @param zone           used only to compute lookback calendar dates; may be null
     */
    public static List<String> daysNeedingUpload(
            Collection<String> localDays,
            Set<String> uploadedClosed,
            String todayKey,
            String todaySnapshotDay,
            int closedLookback,
            TimeZone zone) {
        String today = StatsStore.canonicalDayKey(todayKey == null ? "" : todayKey);
        Set<String> uploaded = uploadedClosed == null ? new HashSet<>() : uploadedClosed;
        String snap = todaySnapshotDay == null ? "" : StatsStore.canonicalDayKey(todaySnapshotDay);
        Set<String> keys = new HashSet<>();
        if (localDays != null) {
            for (String raw : localDays) {
                String day = StatsStore.canonicalDayKey(raw);
                if (!day.isEmpty()) keys.add(day);
            }
        }
        if (!today.isEmpty() && closedLookback > 0) {
            for (String d : priorClosedDays(today, closedLookback, zone)) {
                keys.add(d);
            }
        }
        // Always consider today for a live health snapshot when not yet snapped.
        if (!today.isEmpty()) keys.add(today);

        List<String> out = new ArrayList<>();
        for (String day : keys) {
            int cmp = StatsStore.compareDayKeys(day, today);
            if (cmp < 0) {
                if (!uploaded.contains(day)) out.add(day);
            } else if (cmp == 0) {
                if (!day.equals(snap)) out.add(day);
            }
        }
        out.sort(StatsStore::compareDayKeys);
        return out;
    }

    /**
     * Prior closed calendar days before {@code todayKey} (YYYY-MM-DD arithmetic).
     * Does not apply the stats barrier — {@code todayKey} is already a barrier day.
     */
    public static List<String> priorClosedDays(String todayKey, int count, TimeZone zone) {
        List<String> out = new ArrayList<>();
        String today = StatsStore.canonicalDayKey(todayKey == null ? "" : todayKey);
        if (today.isEmpty() || count <= 0) return out;
        TimeZone tz = zone == null ? TimeZone.getTimeZone("UTC") : zone;
        Calendar c = Calendar.getInstance(tz);
        String[] p = today.split("-");
        try {
            c.set(Calendar.YEAR, Integer.parseInt(p[0]));
            c.set(Calendar.MONTH, Integer.parseInt(p[1]) - 1);
            c.set(Calendar.DAY_OF_MONTH, Integer.parseInt(p[2]));
            c.set(Calendar.HOUR_OF_DAY, 12);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
        } catch (Exception e) {
            return out;
        }
        for (int i = 0; i < count; i++) {
            c.add(Calendar.DAY_OF_YEAR, -1);
            out.add(String.format(Locale.US, "%04d-%02d-%02d",
                    c.get(Calendar.YEAR),
                    c.get(Calendar.MONTH) + 1,
                    c.get(Calendar.DAY_OF_MONTH)));
        }
        return out;
    }

    /** After a successful upload of a closed day. */
    public static boolean isClosedDay(String dayKey, String todayKey) {
        String day = StatsStore.canonicalDayKey(dayKey);
        String today = StatsStore.canonicalDayKey(todayKey);
        return !day.isEmpty() && !today.isEmpty()
                && StatsStore.compareDayKeys(day, today) < 0;
    }

    public static String describe(int pendingClosed, boolean includeToday) {
        return String.format(Locale.US, "pending_closed=%d today=%s",
                pendingClosed, includeToday ? "yes" : "no");
    }
}
