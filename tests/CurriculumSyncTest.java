import net.clevertree.quizgate.core.Catalog;
import net.clevertree.quizgate.core.CurriculumIndexParse;
import net.clevertree.quizgate.core.CurriculumPack;
import net.clevertree.quizgate.core.CurriculumPackSelect;
import net.clevertree.quizgate.core.Question;

import java.util.Collections;
import java.util.List;

/** Index parse + prefer-newer / needsInstall (JVM, no Android). */
public class CurriculumSyncTest {
    public static void main(String[] args) {
        assertParseIndex();
        assertPreferNewer();
        assertNeedsInstall();
        System.out.println("CurriculumSyncTest OK");
    }

    private static void assertParseIndex() {
        String json = "{"
                + "\"updatedAt\":\"2026-11-21T00:00:00Z\","
                + "\"curricula\":[{"
                + "\"id\":\"basic-math\",\"title\":\"Basic Math\","
                + "\"ageMin\":4,\"ageMax\":7,\"difficultyRank\":10,"
                + "\"version\":7,\"packUrl\":\"https://quizgate.app/curricula/packs/basic-math-v7.qgpack\","
                + "\"contentSha256\":\"abc\",\"bundled\":true"
                + "}]}";
        List<CurriculumIndexParse.Entry> entries = CurriculumIndexParse.parse(json);
        Assert.eq(1, entries.size(), "one entry");
        CurriculumIndexParse.Entry e = entries.get(0);
        Assert.eq("basic-math", e.id, "id");
        Assert.eq(7, e.version, "version");
        Assert.isTrue(e.bundled, "bundled marker");
        Assert.isTrue(e.packUrl.contains("basic-math-v7.qgpack"), "packUrl");
        Assert.eq("abc", e.contentSha256, "sha");
    }

    private static void assertPreferNewer() {
        CurriculumPack v6 = pack(6, "sha6");
        CurriculumPack v7 = pack(7, "sha7");
        Assert.eq(7, CurriculumPackSelect.preferNewer(v6, v7).version, "bundled newer");
        Assert.eq(7, CurriculumPackSelect.preferNewer(v7, v6).version, "installed newer");
        Assert.isTrue(CurriculumPackSelect.preferNewer(v7, pack(7, "sha7")) == v7, "tie prefers first");
        Assert.isTrue(CurriculumPackSelect.preferNewer(v7, null) == v7, "null b");
        Assert.isTrue(CurriculumPackSelect.preferNewer(null, v6) == v6, "null a");
    }

    private static void assertNeedsInstall() {
        // bundled packs with packUrl must still be eligible when newer
        Assert.isTrue(CurriculumPackSelect.needsInstall(
                7, "newsha", 6, "old", "https://quizgate.app/p.qgpack"), "version bump");
        Assert.isTrue(!CurriculumPackSelect.needsInstall(
                7, "sha", 7, "sha", "https://quizgate.app/p.qgpack"), "same version+sha");
        Assert.isTrue(CurriculumPackSelect.needsInstall(
                7, "new", 7, "old", "https://quizgate.app/p.qgpack"), "sha change");
        Assert.isTrue(!CurriculumPackSelect.needsInstall(
                7, "sha", 8, "sha", "https://quizgate.app/p.qgpack"), "local newer");
        Assert.isTrue(!CurriculumPackSelect.needsInstall(
                7, "sha", 6, "old", ""), "no packUrl");
        Assert.isTrue(!CurriculumPackSelect.needsInstall(
                7, "sha", 6, "old", null), "null packUrl");
        // No installed copy yet (localVersion 0) → still pull even if APK has it
        Assert.isTrue(CurriculumPackSelect.needsInstall(
                7, "sha", 0, "", "https://quizgate.app/p.qgpack"), "first install");
    }

    private static CurriculumPack pack(int version, String sha) {
        Question q = new Question("t1", "math", "one plus one", "1 + 1 = _",
                java.util.Arrays.asList("1", "2", "3"), "2", null);
        Catalog cat = new Catalog(Collections.singletonList(q));
        return new CurriculumPack(
                "basic-math", "Basic Math", 4, 7, 10, version, sha,
                1, 2, "next", true, 3, Collections.emptyList(), cat, Collections.emptyList());
    }
}
