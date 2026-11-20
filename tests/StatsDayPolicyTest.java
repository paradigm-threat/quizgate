import net.clevertree.quizgate.core.StatsDayPolicy;
import net.clevertree.quizgate.core.StatsStore;

import java.util.Calendar;
import java.util.TimeZone;

public class StatsDayPolicyTest {
    public static void main(String[] args) {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        Assert.eq(5, StatsDayPolicy.DEFAULT_BARRIER_HOUR, "default 5");
        Assert.eq(5, StatsDayPolicy.parseBarrierHour(-1), "bad → default");
        Assert.eq(5, StatsDayPolicy.parseBarrierHour(99), "bad → default");
        Assert.eq(0, StatsDayPolicy.parseBarrierHour(0), "midnight ok");
        Assert.eq(23, StatsDayPolicy.parseBarrierHour(23), "23 ok");

        long nov19_0430 = at(utc, 2026, Calendar.NOVEMBER, 19, 4, 30);
        long nov19_0500 = at(utc, 2026, Calendar.NOVEMBER, 19, 5, 0);
        long nov19_1200 = at(utc, 2026, Calendar.NOVEMBER, 19, 12, 0);

        Assert.eq("2026-11-18", StatsDayPolicy.dayKey(nov19_0430, utc, 5), "04:30 → prior day");
        Assert.eq("2026-11-19", StatsDayPolicy.dayKey(nov19_0500, utc, 5), "05:00 → same day");
        Assert.eq("2026-11-19", StatsDayPolicy.dayKey(nov19_1200, utc, 5), "noon → same day");
        Assert.eq("2026-11-19", StatsDayPolicy.dayKey(nov19_0430, utc, 0), "barrier 0 = calendar");
        Assert.eq("2026-11-19", StatsStore.dayKey(nov19_1200, utc), "StatsStore default barrier");
        Assert.eq("2026-11-18", StatsStore.dayKey(nov19_0430, utc), "StatsStore 04:30 default");

        System.out.println("StatsDayPolicyTest OK");
    }

    private static long at(TimeZone tz, int y, int month, int day, int hour, int min) {
        Calendar c = Calendar.getInstance(tz);
        c.clear();
        c.set(y, month, day, hour, min, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
