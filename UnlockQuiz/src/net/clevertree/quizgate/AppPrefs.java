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

    /** Randomness around {@link #getIntervalMs()} — default 10 minutes (±). */
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

    /**
     * Active curriculum progression level (default 0 = getting started).
     * Items with {@code level <=} this value are eligible.
     */
    public int getCurriculumLevel() {
        return Math.max(0, sp.getInt("curriculum_level", 0));
    }

    public void setCurriculumLevel(int level) {
        sp.edit().putInt("curriculum_level", Math.max(0, level)).apply();
        CurriculumStore.clearCache();
    }

    /** Level-up soft gate: min accuracy % of answered (right/(right+wrong)). */
    public int getLevelUpMinAccuracyPct() {
        return net.clevertree.quizgate.core.LevelUpPolicy.parseMinAccuracyPct(
                sp.getInt("level_up_min_accuracy_pct",
                        net.clevertree.quizgate.core.LevelUpPolicy.DEFAULT_MIN_ACCURACY_PCT));
    }

    public void setLevelUpMinAccuracyPct(int pct) {
        sp.edit().putInt("level_up_min_accuracy_pct",
                net.clevertree.quizgate.core.LevelUpPolicy.parseMinAccuracyPct(pct)).apply();
    }

    public int getLevelUpMinCorrect() {
        return net.clevertree.quizgate.core.LevelUpPolicy.parseMinCorrect(
                sp.getInt("level_up_min_correct",
                        net.clevertree.quizgate.core.LevelUpPolicy.DEFAULT_MIN_CORRECT));
    }

    public void setLevelUpMinCorrect(int n) {
        sp.edit().putInt("level_up_min_correct",
                net.clevertree.quizgate.core.LevelUpPolicy.parseMinCorrect(n)).apply();
    }

    /**
     * Skip gate for level-up. Default {@code LevelUpPolicy.DEFAULT_MAX_SKIP_PCT} (−1)
     * means skipped ≤ right+wrong; non-negative = max skipped/asked %.
     */
    public int getLevelUpMaxSkipPct() {
        return net.clevertree.quizgate.core.LevelUpPolicy.parseMaxSkipPct(
                sp.getInt("level_up_max_skip_pct",
                        net.clevertree.quizgate.core.LevelUpPolicy.DEFAULT_MAX_SKIP_PCT));
    }

    public void setLevelUpMaxSkipPct(int pct) {
        sp.edit().putInt("level_up_max_skip_pct",
                net.clevertree.quizgate.core.LevelUpPolicy.parseMaxSkipPct(pct)).apply();
    }

    /** Idle MC hint mode: off / until_first_correct / always. */
    public String getHintMode() {
        return net.clevertree.quizgate.core.AnswerHintPolicy.normalizeMode(
                sp.getString("hint_mode",
                        net.clevertree.quizgate.core.AnswerHintPolicy.MODE_UNTIL_FIRST_CORRECT));
    }

    public void setHintMode(String mode) {
        sp.edit().putString("hint_mode",
                net.clevertree.quizgate.core.AnswerHintPolicy.normalizeMode(mode)).apply();
    }

    public int getHintDelayMs() {
        return net.clevertree.quizgate.core.AnswerHintPolicy.clampMs(
                sp.getInt("hint_delay_ms",
                        net.clevertree.quizgate.core.AnswerHintPolicy.DEFAULT_DELAY_MS),
                net.clevertree.quizgate.core.AnswerHintPolicy.DEFAULT_DELAY_MS);
    }

    public void setHintDelayMs(int ms) {
        sp.edit().putInt("hint_delay_ms",
                net.clevertree.quizgate.core.AnswerHintPolicy.clampMs(ms,
                        net.clevertree.quizgate.core.AnswerHintPolicy.DEFAULT_DELAY_MS)).apply();
    }

    public int getHintPulseMs() {
        return net.clevertree.quizgate.core.AnswerHintPolicy.clampMs(
                sp.getInt("hint_pulse_ms",
                        net.clevertree.quizgate.core.AnswerHintPolicy.DEFAULT_PULSE_MS),
                net.clevertree.quizgate.core.AnswerHintPolicy.DEFAULT_PULSE_MS);
    }

    public void setHintPulseMs(int ms) {
        sp.edit().putInt("hint_pulse_ms",
                net.clevertree.quizgate.core.AnswerHintPolicy.clampMs(ms,
                        net.clevertree.quizgate.core.AnswerHintPolicy.DEFAULT_PULSE_MS)).apply();
    }

    public int getHintPeriodMs() {
        return net.clevertree.quizgate.core.AnswerHintPolicy.clampMs(
                sp.getInt("hint_period_ms",
                        net.clevertree.quizgate.core.AnswerHintPolicy.DEFAULT_PERIOD_MS),
                net.clevertree.quizgate.core.AnswerHintPolicy.DEFAULT_PERIOD_MS);
    }

    public void setHintPeriodMs(int ms) {
        sp.edit().putInt("hint_period_ms",
                net.clevertree.quizgate.core.AnswerHintPolicy.clampMs(ms,
                        net.clevertree.quizgate.core.AnswerHintPolicy.DEFAULT_PERIOD_MS)).apply();
    }

    public int getHintStrongAfterMs() {
        return net.clevertree.quizgate.core.AnswerHintPolicy.clampMs(
                sp.getInt("hint_strong_after_ms",
                        net.clevertree.quizgate.core.AnswerHintPolicy.DEFAULT_STRONG_AFTER_MS),
                net.clevertree.quizgate.core.AnswerHintPolicy.DEFAULT_STRONG_AFTER_MS);
    }

    public void setHintStrongAfterMs(int ms) {
        sp.edit().putInt("hint_strong_after_ms",
                net.clevertree.quizgate.core.AnswerHintPolicy.clampMs(ms,
                        net.clevertree.quizgate.core.AnswerHintPolicy.DEFAULT_STRONG_AFTER_MS)).apply();
    }

    public int getHintBlocksAfterMs() {
        return net.clevertree.quizgate.core.AnswerHintPolicy.clampMs(
                sp.getInt("hint_blocks_after_ms",
                        net.clevertree.quizgate.core.AnswerHintPolicy.DEFAULT_BLOCKS_AFTER_MS),
                net.clevertree.quizgate.core.AnswerHintPolicy.DEFAULT_BLOCKS_AFTER_MS);
    }

    public void setHintBlocksAfterMs(int ms) {
        sp.edit().putInt("hint_blocks_after_ms",
                net.clevertree.quizgate.core.AnswerHintPolicy.clampMs(ms,
                        net.clevertree.quizgate.core.AnswerHintPolicy.DEFAULT_BLOCKS_AFTER_MS)).apply();
    }

    public boolean isHintBlocksEnabled() {
        return sp.getBoolean("hint_blocks_enabled",
                net.clevertree.quizgate.core.AnswerHintPolicy.DEFAULT_BLOCKS_ENABLED);
    }

    public void setHintBlocksEnabled(boolean v) {
        sp.edit().putBoolean("hint_blocks_enabled", v).apply();
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

    /**
     * Per-session rapid-fire time-earn cap (minutes). Admin-tunable via profile
     * {@code settings.time_earn_cap_minutes}; clamped in {@link net.clevertree.quizgate.core.TimeEarnPolicy}.
     */
    public int getTimeEarnCapMinutes() {
        int raw = sp.getInt("time_earn_cap_minutes",
                net.clevertree.quizgate.core.TimeEarnPolicy.DEFAULT_CAP_MINUTES);
        return net.clevertree.quizgate.core.TimeEarnPolicy.parseCapMinutes(raw);
    }

    public void setTimeEarnCapMinutes(int minutes) {
        sp.edit().putInt("time_earn_cap_minutes",
                net.clevertree.quizgate.core.TimeEarnPolicy.parseCapMinutes(minutes)).apply();
    }

    /**
     * Local hour when the stats day rolls (default 5). Admin
     * {@code settings.stats_day_barrier_hour}; omit → repo default.
     */
    public int getStatsDayBarrierHour() {
        int raw = sp.getInt("stats_day_barrier_hour",
                net.clevertree.quizgate.core.StatsDayPolicy.DEFAULT_BARRIER_HOUR);
        return net.clevertree.quizgate.core.StatsDayPolicy.parseBarrierHour(raw);
    }

    public void setStatsDayBarrierHour(int hour) {
        sp.edit().putInt("stats_day_barrier_hour",
                net.clevertree.quizgate.core.StatsDayPolicy.parseBarrierHour(hour)).apply();
    }

    /**
     * Legacy helper: previously <em>replaced</em> the schedule with jar minutes (too short).
     * Prefer {@link #takeMinutesBonusMs()} — jar time adds on top of 40±10, never shrinks it.
     */
    public long consumeMinutesForNextInterval(long fallbackIntervalMs) {
        long bonus = takeMinutesBonusMs();
        if (bonus <= 0L) return fallbackIntervalMs;
        return fallbackIntervalMs + bonus;
    }

    /**
     * Clear the minutes jar and return earned free-time ms to add on top of the
     * normal interval±jitter window. Caps at 3 hours. Returns 0 when empty.
     */
    public long takeMinutesBonusMs() {
        int bal = getMinutesBalance();
        if (bal <= 0) return 0L;
        setMinutesBalance(0);
        long fromJar = bal * 60_000L;
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

    /**
     * Activity class of the remembered app (optional). Helps restore the video /
     * game screen instead of the package launcher root.
     */
    public String getResumeClass() {
        return sp.getString("resume_class", null);
    }

    public void setResumeClass(String cls) {
        if (cls == null || cls.isEmpty()) {
            sp.edit().remove("resume_class").apply();
        } else {
            sp.edit().putString("resume_class", cls).apply();
        }
    }

    /** Sticky latch: media was active when the quiz soft-paused it. */
    public boolean wasMediaPlaying() {
        return sp.getBoolean("media_was_playing", false);
    }

    public void setMediaWasPlaying(boolean v) {
        sp.edit().putBoolean("media_was_playing", v).apply();
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
     * Lock mode is always Hard (Accessibility confine). Legacy {@code soft} prefs
     * normalize to {@link GateMode#HARD} on read/write.
     */
    public String getGateMode() {
        String raw = sp.getString("gate_mode", GateMode.HARD);
        String mode = GateMode.normalize(raw);
        // Migrate soft → hard in place so Setup/diag never see Soft again.
        if (raw != null && !GateMode.HARD.equals(raw) && !mode.equals(raw)) {
            sp.edit().putString("gate_mode", mode).commit();
        }
        return mode;
    }

    public void setGateMode(String mode) {
        // commit(): gate mode must survive immediate force-stop / process death (adb smoke, OEM kills).
        sp.edit().putString("gate_mode", GateMode.HARD).commit();
    }

    /** Active Hard-gate lock session — Accessibility reopens quiz while true. */
    public boolean isHardSessionActive() {
        return sp.getBoolean("hard_session_active", false);
    }

    public void setHardSessionActive(boolean active) {
        // commit(): AccessibilityService / leave-hint reintercept must see the flag immediately.
        sp.edit().putBoolean("hard_session_active", active).commit();
    }

    /**
     * Screen turned off while Armed — next wake must show Hard quiz.
     * Interval/morning alarms stay cancelled until unlock clears this.
     */
    public boolean isPendingScreenLock() {
        return sp.getBoolean("pending_screen_lock", false);
    }

    public void setPendingScreenLock(boolean pending) {
        // commit(): Accessibility screen-off/on and BootReceiver must see this immediately.
        sp.edit().putBoolean("pending_screen_lock", pending).commit();
    }

    /**
     * Epoch millis until which wake/reintercept must not open a new Hard quiz
     * (prevents jail→jail right after unlock).
     */
    public long getUnlockGraceUntilMs() {
        return sp.getLong("unlock_grace_until_ms", 0L);
    }

    public void setUnlockGraceUntilMs(long untilMs) {
        sp.edit().putLong("unlock_grace_until_ms", Math.max(0L, untilMs)).commit();
    }

    /** Mark unlock grace from now for {@link net.clevertree.quizgate.core.ScreenLockPolicy#UNLOCK_GRACE_MS}. */
    public void beginUnlockGrace(long nowMs) {
        setUnlockGraceUntilMs(nowMs + net.clevertree.quizgate.core.ScreenLockPolicy.UNLOCK_GRACE_MS);
    }

    /** Screen-off ends the free period — next wake may lock. */
    public void clearUnlockGrace() {
        setUnlockGraceUntilMs(0L);
    }

    public boolean inUnlockGrace(long nowMs) {
        return net.clevertree.quizgate.core.ScreenLockPolicy.inUnlockGrace(nowMs, getUnlockGraceUntilMs());
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

    /** Last successful remote profile pull from api.quizgate.app (0 = never). */
    public long getLastRemoteProfileAt() {
        return sp.getLong("last_remote_profile_at", 0L);
    }

    public void setLastRemoteProfileAt(long ms) {
        sp.edit().putLong("last_remote_profile_at", Math.max(0L, ms)).apply();
    }

    /**
     * Parent opted into remote management (AI sync and/or human admin).
     * Gates runtime upload to api.quizgate.app when compile flag is off.
     */
    public boolean isRemoteManagementEnabled() {
        return sp.getBoolean("remote_management", false);
    }

    public void setRemoteManagementEnabled(boolean v) {
        sp.edit().putBoolean("remote_management", v).apply();
    }

    public boolean isRemoteAiEnabled() {
        return sp.getBoolean("remote_ai", true);
    }

    public void setRemoteAiEnabled(boolean v) {
        sp.edit().putBoolean("remote_ai", v).apply();
    }

    /** True when progress may sync to Intake (compile flag or remote management). */
    public boolean isRemoteUploadEnabled() {
        return net.clevertree.quizgate.core.BuildFingerprint.uploadAllowed(
                isRemoteManagementEnabled());
    }

    /**
     * Optional parent email kept only so Setup can re-register hash.
     * Prefer not to rely on this for identity — server stores hash only.
     */
    public String getParentEmail() {
        return sp.getString("parent_email", "");
    }

    public void setParentEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            sp.edit().remove("parent_email").apply();
        } else {
            sp.edit().putString("parent_email", email.trim()).apply();
        }
    }

    public long getLastRemoteRegisterAt() {
        return sp.getLong("last_remote_register_at", 0L);
    }

    public void setLastRemoteRegisterAt(long ms) {
        sp.edit().putLong("last_remote_register_at", Math.max(0L, ms)).apply();
    }

    /** Last interactive Intake progress flush (ms). Legacy throttle; day-batch sync uses ledger. */
    public long getLastRemoteFlushAt() {
        return sp.getLong("last_remote_flush_at", 0L);
    }

    public void setLastRemoteFlushAt(long ms) {
        sp.edit().putLong("last_remote_flush_at", Math.max(0L, ms)).apply();
    }

    /**
     * Local day key ({@code YYYY-MM-DD}) of the last successful <em>today</em>
     * day-batch snapshot. Cleared automatically when the calendar day advances.
     */
    public String getTodayBatchSnapshotDay() {
        return sp.getString("today_batch_snapshot_day", "");
    }

    public void setTodayBatchSnapshotDay(String dayKey) {
        String d = net.clevertree.quizgate.core.StatsStore.canonicalDayKey(dayKey);
        if (d.isEmpty()) {
            sp.edit().remove("today_batch_snapshot_day").apply();
        } else {
            sp.edit().putString("today_batch_snapshot_day", d).apply();
        }
    }

    /**
     * Local day key when the post-unlock play-choice offer was last shown.
     * Empty until shown; once set to today, further Hard unlocks skip the lottery.
     */
    public String getPostUnlockPlayOfferDay() {
        return sp.getString("post_unlock_play_offer_day", "");
    }

    public void setPostUnlockPlayOfferDay(String dayKey) {
        String d = net.clevertree.quizgate.core.StatsStore.canonicalDayKey(dayKey);
        if (d.isEmpty()) {
            sp.edit().remove("post_unlock_play_offer_day").apply();
        } else {
            sp.edit().putString("post_unlock_play_offer_day", d).apply();
        }
    }
}
