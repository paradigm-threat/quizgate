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
        return net.clevertree.quizgate.core.Scheduler.clampInterval(
                sp.getLong("interval_ms", net.clevertree.quizgate.core.Scheduler.DEFAULT_INTERVAL_MS));
    }

    public void setIntervalMs(long ms) {
        sp.edit().putLong("interval_ms", net.clevertree.quizgate.core.Scheduler.clampInterval(ms)).apply();
    }

    /** Randomness around {@link #getIntervalMs()} — default 15 minutes (±). */
    public long getJitterMs() {
        return net.clevertree.quizgate.core.Scheduler.clampJitter(
                sp.getLong("jitter_ms", net.clevertree.quizgate.core.Scheduler.DEFAULT_JITTER_MS));
    }

    public void setJitterMs(long ms) {
        sp.edit().putLong("jitter_ms", net.clevertree.quizgate.core.Scheduler.clampJitter(ms)).apply();
    }

    public long nextAlarmAt() {
        return sp.getLong("next_alarm_at", 0);
    }

    public void setNextAlarmAt(long ms) {
        sp.edit().putLong("next_alarm_at", ms).apply();
    }

    /** Wall-clock ms of the last interval/morning alarm fire (0 = never). */
    public long lastFiredAt() {
        return sp.getLong("last_fired_at", 0L);
    }

    public void setLastFiredAt(long ms) {
        sp.edit().putLong("last_fired_at", Math.max(0L, ms)).apply();
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

    public boolean isSyncEnabled() {
        return sp.getBoolean("sync_enabled", true);
    }

    public void setSyncEnabled(boolean v) {
        sp.edit().putBoolean("sync_enabled", v).apply();
    }

    public String getActiveCurriculumId() {
        return sp.getString("active_curriculum_id", "basic-math");
    }

    public void setActiveCurriculumId(String id) {
        sp.edit().putString("active_curriculum_id", id == null ? "basic-math" : id).apply();
        CurriculumStore.clearCache();
    }

    public String getActiveTier() {
        return sp.getString("active_tier", "all");
    }

    public void setActiveTier(String tier) {
        sp.edit().putString("active_tier", tier == null ? "all" : tier).apply();
        CurriculumStore.clearCache();
    }

    public String getCurriculumIndexUrl() {
        return sp.getString("curriculum_index_url", CurriculumIndexClient.DEFAULT_INDEX_URL);
    }

    public int getMinutesBalance() {
        return sp.getInt("minutes_balance", 0);
    }

    public void setMinutesBalance(int minutes) {
        sp.edit().putInt("minutes_balance", Math.max(0, minutes)).apply();
    }

    public void addMinutes(int delta) {
        if (delta <= 0) return;
        setMinutesBalance(getMinutesBalance() + delta);
    }

    /** Consume banked minutes for the next free stretch; returns delay ms to use. */
    public long consumeMinutesForNextInterval(long fallbackIntervalMs) {
        int bal = getMinutesBalance();
        if (bal <= 0) return fallbackIntervalMs;
        setMinutesBalance(0);
        long fromJar = bal * 60_000L;
        // Cap at 3 hours so a huge jar does not skip mornings entirely
        long cap = 3L * 60L * 60L * 1000L;
        return Math.min(fromJar, cap);
    }

    public boolean isOnboardingDone() {
        return sp.getBoolean("onboarding_done", false);
    }

    public void setOnboardingDone(boolean v) {
        sp.edit().putBoolean("onboarding_done", v).apply();
    }

    /** {@link PhotoLibrary#SOURCE_GALLERY} or {@link PhotoLibrary#SOURCE_BUNDLED}. */
    public String getPhotoSource() {
        return sp.getString("photo_source", PhotoLibrary.SOURCE_BUNDLED);
    }

    public void setPhotoSource(String source) {
        if (!PhotoLibrary.SOURCE_GALLERY.equals(source) && !PhotoLibrary.SOURCE_BUNDLED.equals(source)) {
            source = PhotoLibrary.SOURCE_BUNDLED;
        }
        sp.edit().putString("photo_source", source).apply();
    }

    /** Package to bring back after a soft-gate unlock (YouTube, game, …). */
    public String getResumePackage() {
        return sp.getString("resume_package", null);
    }

    public void setResumePackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            sp.edit().remove("resume_package").apply();
        } else {
            sp.edit().putString("resume_package", pkg).apply();
        }
    }

    /** Last ScheduleAuth gap prompted (name) — used to avoid Settings spam. */
    public String getLastAuthPromptGap() {
        return sp.getString("auth_prompt_gap", "");
    }

    public long getLastAuthPromptAt() {
        return sp.getLong("auth_prompt_at", 0L);
    }

    public void setLastAuthPrompt(String gap, long atMs) {
        sp.edit().putString("auth_prompt_gap", gap == null ? "" : gap)
                .putLong("auth_prompt_at", atMs)
                .apply();
    }

    public boolean wasNotificationDeniedOnce() {
        return sp.getBoolean("notif_denied_once", false);
    }

    public void setNotificationDeniedOnce(boolean v) {
        sp.edit().putBoolean("notif_denied_once", v).apply();
    }

    /**
     * User preference: {@link PerformanceMode#PERFORMANCE}, {@link PerformanceMode#SLIM},
     * or {@link PerformanceMode#AUTO} (default). Never disables exact alarms / boot re-arm.
     */
    public String getPerformanceMode() {
        return PerformanceMode.normalize(sp.getString("performance_mode", PerformanceMode.AUTO));
    }

    public void setPerformanceMode(String mode) {
        sp.edit().putString("performance_mode", PerformanceMode.normalize(mode)).apply();
    }

    /** Last resolved effective profile: performance | slim. */
    public String getEffectivePerformanceMode() {
        String v = sp.getString("perf_effective", "");
        if (PerformanceMode.SLIM.equals(v)) return PerformanceMode.SLIM;
        if (PerformanceMode.PERFORMANCE.equals(v) || PerformanceMode.BALANCED.equals(v)) {
            return PerformanceMode.PERFORMANCE;
        }
        return "";
    }

    public void setEffectivePerformanceMode(String effective) {
        String e = PerformanceMode.SLIM.equals(effective)
                ? PerformanceMode.SLIM : PerformanceMode.PERFORMANCE;
        sp.edit().putString("perf_effective", e).apply();
    }

    public String getLastAutoReasons() {
        return sp.getString("perf_auto_reasons", "");
    }

    public void setLastAutoReasons(String csv) {
        sp.edit().putString("perf_auto_reasons", csv == null ? "" : csv).apply();
    }

    public String getLastAutoSignalsJson() {
        return sp.getString("perf_auto_signals", "{}");
    }

    public void setLastAutoSignalsJson(String json) {
        sp.edit().putString("perf_auto_signals", json == null || json.isEmpty() ? "{}" : json).apply();
    }

    public long getLastPerfSwitchAt() {
        return sp.getLong("perf_switch_at", 0L);
    }

    public void setLastPerfSwitchAt(long ms) {
        sp.edit().putLong("perf_switch_at", Math.max(0L, ms)).apply();
    }

    public String getLastPerfSwitchFrom() {
        return sp.getString("perf_switch_from", "");
    }

    public void setLastPerfSwitchFrom(String from) {
        sp.edit().putString("perf_switch_from", from == null ? "" : from).apply();
    }

    public String getLastPerfSwitchTo() {
        return sp.getString("perf_switch_to", "");
    }

    public void setLastPerfSwitchTo(String to) {
        sp.edit().putString("perf_switch_to", to == null ? "" : to).apply();
    }

    /** Sticky flag from {@link android.content.Intent#ACTION_BATTERY_LOW}. */
    public boolean isBatteryLowFlag() {
        return sp.getBoolean("battery_low_flag", false);
    }

    public void setBatteryLowFlag(boolean v) {
        sp.edit().putBoolean("battery_low_flag", v).apply();
    }

    public boolean isSlimPerformance() {
        return PerformanceMode.isSlim(this);
    }
}
