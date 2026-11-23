import net.clevertree.quizgate.core.DayBatchSyncPolicy;
import net.clevertree.quizgate.core.DayUploadLedger;
import net.clevertree.quizgate.core.StatsStore;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

public class DayBatchSyncPolicyTest {
    public static void main(String[] args) throws Exception {
        String today = "2026-11-19";
        Set<String> uploaded = new HashSet<>();
        List<String> local = Arrays.asList(
                "2026-11-18", "2026-11-19", "2026-11-01", "2026-10-30");
        TimeZone utc = TimeZone.getTimeZone("UTC");

        // Default lookback synthesizes prior closed days missing from days.jsonl.
        List<String> need = DayBatchSyncPolicy.daysNeedingUpload(
                local, uploaded, today, "", DayBatchSyncPolicy.ZERO_DAY_LOOKBACK, utc);
        Assert.isTrue(need.contains("2026-10-30"), "has oldest local");
        Assert.isTrue(need.contains("2026-11-01"), "has mid local");
        Assert.isTrue(need.contains("2026-11-18"), "has yesterday local");
        Assert.isTrue(need.contains("2026-11-17"), "zero-day lookback -2");
        Assert.isTrue(need.contains("2026-11-16"), "zero-day lookback -3");
        Assert.isTrue(need.contains(today), "includes today");
        Assert.eq("2026-10-30", need.get(0), "oldest first");

        // Explicit lookback=0 preserves legacy “only local keys” behaviour.
        need = DayBatchSyncPolicy.daysNeedingUpload(
                local, uploaded, today, "", 0, utc);
        Assert.eq(4, need.size(), "lookback0 all pending");
        Assert.eq("2026-10-30", need.get(0), "oldest first");
        Assert.eq("2026-11-19", need.get(3), "today last");

        uploaded.add("2026-10-30");
        uploaded.add("2026-11-01");
        uploaded.add("2026-11-18");
        uploaded.add("2026-11-17");
        uploaded.add("2026-11-16");
        need = DayBatchSyncPolicy.daysNeedingUpload(
                local, uploaded, today, "", DayBatchSyncPolicy.ZERO_DAY_LOOKBACK, utc);
        Assert.eq(1, need.size(), "only today left");
        Assert.eq(today, need.get(0), "today");

        need = DayBatchSyncPolicy.daysNeedingUpload(
                local, uploaded, today, today, DayBatchSyncPolicy.ZERO_DAY_LOOKBACK, utc);
        Assert.eq(0, need.size(), "today already snapshotted");

        // Next day: yesterday must upload even if it had a today-snapshot
        String next = "2026-11-20";
        need = DayBatchSyncPolicy.daysNeedingUpload(
                local, uploaded, next, "2026-11-19", DayBatchSyncPolicy.ZERO_DAY_LOOKBACK, utc);
        Assert.isTrue(need.contains("2026-11-19"), "closed yesterday needs final upload");
        Assert.isTrue(!need.contains("2026-11-18"), "already in ledger");

        // Empty local rollups: still upload synthetic closed days.
        need = DayBatchSyncPolicy.daysNeedingUpload(
                Collections.emptyList(), new HashSet<>(), today, "",
                DayBatchSyncPolicy.ZERO_DAY_LOOKBACK, utc);
        Assert.isTrue(need.contains("2026-11-18"), "missing closed yesterday");
        Assert.isTrue(need.contains("2026-11-17"), "missing closed -2");
        Assert.isTrue(need.contains("2026-11-16"), "missing closed -3");
        Assert.isTrue(need.contains(today), "today health snapshot");

        Assert.isTrue(DayBatchSyncPolicy.isClosedDay("2026-11-18", today), "closed");
        Assert.isTrue(!DayBatchSyncPolicy.isClosedDay(today, today), "today not closed");

        List<String> prior = DayBatchSyncPolicy.priorClosedDays(today, 3, utc);
        Assert.eq(3, prior.size(), "prior count");
        Assert.eq("2026-11-18", prior.get(0), "yesterday first");

        // Ledger round-trip
        File dir = File.createTempFile("qg-ledger", "dir");
        //noinspection ResultOfMethodCallIgnored
        dir.delete();
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        DayUploadLedger ledger = new DayUploadLedger(dir);
        ledger.markUploaded("2026-11-5");
        ledger.markUploaded("2026-11-05"); // canonical merge
        Set<String> got = ledger.load();
        Assert.isTrue(got.contains("2026-11-05"), "padded key");
        Assert.eq(1, got.size(), "deduped");

        Assert.eq("2026-11-05", StatsStore.canonicalDayKey("2026-11-5"), "canonical");
        System.out.println("DayBatchSyncPolicyTest OK");
    }
}
