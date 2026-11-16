package net.clevertree.quizgate;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPrefs {
    private static final String NAME = "unlock_quiz";
    private final SharedPreferences sp;

    public AppPrefs(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public boolean isArmed() {
        return sp.getBoolean("armed", false);
    }

    public void setArmed(boolean v) {
        sp.edit().putBoolean("armed", v).apply();
    }

    public String lastClearedDay() {
        return sp.getString("last_cleared_day", null);
    }

    public void setLastClearedDay(String day) {
        sp.edit().putString("last_cleared_day", day).apply();
    }

    public long getIntervalMs() {
        return sp.getLong("interval_ms", net.clevertree.quizgate.core.Scheduler.DEFAULT_INTERVAL_MS);
    }

    public void setIntervalMs(long ms) {
        sp.edit().putLong("interval_ms", ms).apply();
    }

    public long nextAlarmAt() {
        return sp.getLong("next_alarm_at", 0);
    }

    public void setNextAlarmAt(long ms) {
        sp.edit().putLong("next_alarm_at", ms).apply();
    }

    /** "digit_pad" (default) or "multiple_choice" for math questions. */
    public String getMathEntryMode() {
        return sp.getString("math_entry", "digit_pad");
    }

    public void setMathEntryMode(String mode) {
        sp.edit().putString("math_entry", mode).apply();
    }

    public boolean useDigitPadForMath() {
        return !"multiple_choice".equals(getMathEntryMode());
    }
}
