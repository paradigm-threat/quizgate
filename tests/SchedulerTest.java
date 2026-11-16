import net.clevertree.quizgate.core.Scheduler;

import java.util.Calendar;
import java.util.Random;
import java.util.TimeZone;

public class SchedulerTest {
    public static void main(String[] args) {
        TimeZone tz = TimeZone.getTimeZone("America/New_York");
        Scheduler s = new Scheduler(tz, 2L * 60L * 60L * 1000L);

        // 10:00 → next 12:00 (no jitter)
        long t10 = at(tz, 2026, Calendar.NOVEMBER, 15, 10, 0);
        long n10 = s.nextAfterUnlock(t10);
        Assert.eq(at(tz, 2026, Calendar.NOVEMBER, 15, 12, 0), n10, "10:00 + 2h");

        // 18:30 → +2h = 20:30 quiet → next morning 07:00
        long t1830 = at(tz, 2026, Calendar.NOVEMBER, 15, 18, 30);
        long n1830 = s.nextAfterUnlock(t1830);
        Assert.eq(at(tz, 2026, Calendar.NOVEMBER, 16, 7, 0), n1830, "18:30 → morning");

        // 19:00 → 21:00 quiet → morning
        long t19 = at(tz, 2026, Calendar.NOVEMBER, 15, 19, 0);
        Assert.eq(at(tz, 2026, Calendar.NOVEMBER, 16, 7, 0), s.nextAfterUnlock(t19), "19:00 → morning");

        Assert.isTrue(s.isQuietHours(at(tz, 2026, Calendar.NOVEMBER, 15, 20, 0)), "20 quiet");
        Assert.isTrue(s.isQuietHours(at(tz, 2026, Calendar.NOVEMBER, 15, 3, 0)), "3 quiet");
        Assert.isTrue(!s.isQuietHours(at(tz, 2026, Calendar.NOVEMBER, 15, 7, 0)), "7 not quiet");
        Assert.isTrue(!s.isQuietHours(at(tz, 2026, Calendar.NOVEMBER, 15, 19, 59)), "19:59 not quiet");

        Assert.isTrue(s.needsMorningGate(t10, null), "needs morning null");
        Assert.isTrue(s.needsMorningGate(t10, "2026-11-14"), "needs morning other day");
        Assert.isTrue(!s.needsMorningGate(t10, s.dayKey(t10)), "cleared today");

        // Default 60 ± 30 → window 30–90 min
        Assert.eq(Scheduler.DEFAULT_INTERVAL_MS, 60L * 60L * 1000L, "default interval 60m");
        Assert.eq(Scheduler.DEFAULT_JITTER_MS, 30L * 60L * 1000L, "default jitter 30m");
        Scheduler def = new Scheduler(tz, Scheduler.DEFAULT_INTERVAL_MS, Scheduler.DEFAULT_JITTER_MS);
        Assert.eq(30L * 60L * 1000L, def.windowMinMs(), "window min 30m");
        Assert.eq(90L * 60L * 1000L, def.windowMaxMs(), "window max 90m");

        Random fixed = new Random(7L);
        Scheduler jittered = new Scheduler(tz, Scheduler.DEFAULT_INTERVAL_MS, Scheduler.DEFAULT_JITTER_MS, fixed);
        for (int i = 0; i < 40; i++) {
            long delay = jittered.sampleDelayMs();
            Assert.isTrue(delay >= 30L * 60L * 1000L && delay <= 90L * 60L * 1000L,
                    "sample in [30,90] got=" + delay);
        }

        // Daytime next stays inside the jitter window
        Scheduler seeded = new Scheduler(tz, 60L * 60L * 1000L, 30L * 60L * 1000L, new Random(1L));
        long next = seeded.nextAfterUnlock(t10);
        long delay = next - t10;
        Assert.isTrue(delay >= 30L * 60L * 1000L && delay <= 90L * 60L * 1000L,
                "daytime next in window delay=" + delay);

        // Zero jitter stays exact
        Scheduler exact = new Scheduler(tz, 45L * 60L * 1000L, 0L, new Random(99L));
        Assert.eq(45L * 60L * 1000L, exact.sampleDelayMs(), "zero jitter exact");

        Assert.eq("30 min – 90 min",
                Scheduler.formatWindow(Scheduler.DEFAULT_INTERVAL_MS, Scheduler.DEFAULT_JITTER_MS),
                "format window");

        System.out.println("SchedulerTest OK");
    }

    private static long at(TimeZone tz, int y, int month, int day, int h, int min) {
        Calendar c = Calendar.getInstance(tz);
        c.clear();
        c.set(y, month, day, h, min, 0);
        return c.getTimeInMillis();
    }
}
