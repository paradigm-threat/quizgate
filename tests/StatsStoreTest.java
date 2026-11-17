import net.clevertree.quizgate.core.Catalog;
import net.clevertree.quizgate.core.QuestionPicker;
import net.clevertree.quizgate.core.QuizSession;
import net.clevertree.quizgate.core.RenderFormat;
import net.clevertree.quizgate.core.StatsEvent;
import net.clevertree.quizgate.core.StatsStore;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;
import java.util.TimeZone;

public class StatsStoreTest {
    public static void main(String[] args) throws Exception {
        Catalog c = Catalog.fromJson(new String(Files.readAllBytes(Paths.get(args[0]))));
        File dir = Files.createTempDirectory("unlock-stats").toFile();
        StatsStore store = new StatsStore(dir);
        TimeZone utc = TimeZone.getTimeZone("UTC");

        QuestionPicker picker = new QuestionPicker(c, new Random(7)).preferFormat(RenderFormat.EQUATION);
        QuizSession s = new QuizSession("sessA", "adb_force", 1000L, picker);
        store.appendEvent(new StatsEvent(1000, "sessA", "shown", s.getCurrent().id(), "", 0), utc);
        String correct = s.getCurrent().correctChoice;
        store.appendEvent(new StatsEvent(1100, "sessA", "correct", s.getCurrent().id(), correct, 0), utc);
        s.answer(correct, 1100L);
        store.appendSession(s);

        QuizSession s2 = new QuizSession("sessB", "interval", 2000L,
                new QuestionPicker(c, new Random(8)).preferFormat(RenderFormat.EQUATION));
        s2.bypass(2100L);
        store.appendEvent(new StatsEvent(2100, "sessB", "bypass", s2.getCurrent().id(), "", 0), utc);
        store.appendSession(s2);

        StatsStore.Aggregates a = store.aggregates();
        Assert.eq(2, a.sessions, "sessions");
        Assert.eq(1, a.correct, "correct");
        Assert.eq(1, a.bypass, "bypass");
        Assert.eq(0.5, a.bypassRate(), 0.001, "bypass rate");

        long now = System.currentTimeMillis();
        store.appendEvent(new StatsEvent(now, "d1", "shown", "m01", "", 0), utc);
        store.appendEvent(new StatsEvent(now + 1, "d1", "wrong", "m01", "9", 1), utc);
        store.appendEvent(new StatsEvent(now + 2, "d1", "shown", "m02", "", 1), utc);
        store.appendEvent(new StatsEvent(now + 3, "d1", "correct", "m02", "3", 0), utc);
        store.appendEvent(new StatsEvent(now + 4, "d1", "shown", "m03", "", 0), utc);
        store.appendEvent(new StatsEvent(now + 5, "d1", "skipped", "m03", "", 0), utc);
        StatsStore.DayStats day = store.dayStats(now, utc);
        Assert.eq(3, day.questionsAsked, "day asked");
        Assert.eq(1, day.answeredRight, "day right");
        Assert.eq(1, day.answeredWrong, "day wrong");
        Assert.eq(1, day.skipped, "day skipped");
        Assert.eq(StatsStore.Level.GOOD, day.level(), "day level ~50%");

        // Historic days survive in days.jsonl even after raw event prune
        long older = now - 3L * 24L * 60L * 60L * 1000L;
        store.appendEvent(new StatsEvent(older, "old", "shown", "m01", "", 0), utc);
        store.appendEvent(new StatsEvent(older + 1, "old", "correct", "m01", "2", 0), utc);
        java.util.List<StatsStore.DayEntry> hist = store.allDayStats(utc);
        Assert.isTrue(hist.size() >= 2, "historic days >= 2");
        Assert.eq(StatsStore.dayKey(now, utc), hist.get(0).dayKey, "newest day first");
        boolean foundOld = false;
        for (StatsStore.DayEntry e : hist) {
            if (e.dayKey.equals(StatsStore.dayKey(older, utc))) {
                foundOld = true;
                Assert.eq(1, e.stats.answeredRight, "old day right");
                Assert.eq(1, e.stats.questionsAsked, "old day asked");
            }
        }
        Assert.isTrue(foundOld, "older day retained");

        // Day rollups persist after compacting raw events
        store.compact();
        Assert.isTrue(store.getDaysFile().exists(), "days.jsonl exists");
        StatsStore.DayStats dayAfter = store.dayStats(now, utc);
        Assert.eq(3, dayAfter.questionsAsked, "rollup asked after compact");
        Assert.isTrue(store.approximateSizeBytes() >= 0, "size non-negative");

        System.out.println("StatsStoreTest OK dir=" + dir.getAbsolutePath());
    }
}
