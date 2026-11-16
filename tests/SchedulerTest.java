import net.clevertree.quizgate.core.Scheduler;

import java.util.Calendar;
import java.util.TimeZone;

public class SchedulerTest {
    public static void main(String[] args) {
        TimeZone tz = TimeZone.getTimeZone("America/New_York");
        Scheduler s = new Scheduler(tz, 2L * 60L * 60L * 1000L);

        // 10:00 → next 12:00
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

        System.out.println("SchedulerTest OK");
    }

    private static long at(TimeZone tz, int y, int month, int day, int h, int min) {
        Calendar c = Calendar.getInstance(tz);
        c.clear();
        c.set(y, month, day, h, min, 0);
        return c.getTimeInMillis();
    }
}
