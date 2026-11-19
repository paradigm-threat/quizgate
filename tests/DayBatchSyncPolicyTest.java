import net.clevertree.quizgate.core.DayBatchSyncPolicy;
import net.clevertree.quizgate.core.DayUploadLedger;
import net.clevertree.quizgate.core.StatsStore;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DayBatchSyncPolicyTest {
    public static void main(String[] args) throws Exception {
        String today = "2026-11-19";
        Set<String> uploaded = new HashSet<>();
        List<String> local = Arrays.asList(
                "2026-11-18", "2026-11-19", "2026-11-01", "2026-10-30");

        List<String> need = DayBatchSyncPolicy.daysNeedingUpload(local, uploaded, today, "");
        Assert.eq(4, need.size(), "all pending first open");
        Assert.eq("2026-10-30", need.get(0), "oldest first");
        Assert.eq("2026-11-19", need.get(3), "today last");

        uploaded.add("2026-10-30");
        uploaded.add("2026-11-01");
        uploaded.add("2026-11-18");
        need = DayBatchSyncPolicy.daysNeedingUpload(local, uploaded, today, "");
        Assert.eq(1, need.size(), "only today left");
        Assert.eq(today, need.get(0), "today");

        need = DayBatchSyncPolicy.daysNeedingUpload(local, uploaded, today, today);
        Assert.eq(0, need.size(), "today already snapshotted");

        // Next day: yesterday must upload even if it had a today-snapshot
        String next = "2026-11-20";
        need = DayBatchSyncPolicy.daysNeedingUpload(local, uploaded, next, "2026-11-19");
        Assert.isTrue(need.contains("2026-11-19"), "closed yesterday needs final upload");
        Assert.isTrue(!need.contains("2026-11-18"), "already in ledger");

        Assert.isTrue(DayBatchSyncPolicy.isClosedDay("2026-11-18", today), "closed");
        Assert.isTrue(!DayBatchSyncPolicy.isClosedDay(today, today), "today not closed");

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
