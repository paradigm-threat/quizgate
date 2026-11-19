package net.clevertree.quizgate.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Decides which local day rollups still need an Intake upload.
 *
 * <ul>
 *   <li>Closed days ({@code day < today}): upload once, then remember forever.</li>
 *   <li>Today: at most one snapshot per local calendar day (not permanently closed
 *       until the day rolls — so the next open can push the final totals).</li>
 * </ul>
 *
 * No schedule / alarm — callers run this when the app is open.
 */
public final class DayBatchSyncPolicy {

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
        String today = StatsStore.canonicalDayKey(todayKey == null ? "" : todayKey);
        Set<String> uploaded = uploadedClosed == null ? new HashSet<>() : uploadedClosed;
        String snap = todaySnapshotDay == null ? "" : StatsStore.canonicalDayKey(todaySnapshotDay);
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (localDays == null) return out;
        for (String raw : localDays) {
            String day = StatsStore.canonicalDayKey(raw);
            if (day.isEmpty() || !seen.add(day)) continue;
            int cmp = StatsStore.compareDayKeys(day, today);
            if (cmp < 0) {
                if (!uploaded.contains(day)) out.add(day);
            } else if (cmp == 0) {
                if (!day.equals(snap)) out.add(day);
            }
            // Future keys (clock skew) ignored
        }
        out.sort(StatsStore::compareDayKeys);
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
