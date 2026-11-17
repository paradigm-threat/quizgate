package net.clevertree.quizgate;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import net.clevertree.quizgate.core.CurriculumPack;
import net.clevertree.quizgate.core.Scheduler;
import net.clevertree.quizgate.core.StatsStore;
import net.clevertree.quizgate.core.SystemClock;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

public class SetupActivity extends Activity {
    private TextView status;
    private TextView minutesJar;
    private TextView nextQuiz;
    private TextView scheduleHealth;
    private View statsCard;
    private TextView statsMood;
    private TextView statsAskedNum;
    private TextView statsRightNum;
    private TextView statsWrongNum;
    private TextView statsSkippedNum;
    private CheckBox syncEnabled;
    private Spinner curriculumSpinner;
    private Spinner tierSpinner;
    private Spinner perfModeSpinner;
    private TextView perfDetail;
    private TextView intervalValue;
    private TextView jitterValue;
    private TextView windowValue;
    private Button addWidget;
    private LinearLayout reqList;
    private View reqCard;
    private final List<CurriculumPack> packList = new ArrayList<>();
    private boolean suppressSpinner;
    private boolean suppressPerfSpinner;
    private boolean skipAuthWarn;
    private final Handler countdownHandler = new Handler(Looper.getMainLooper());
    private final Runnable countdownTick = new Runnable() {
        @Override public void run() {
            refreshCountdown();
            long tick = PerformanceMode.setupCountdownTickMs(new AppPrefs(SetupActivity.this));
            countdownHandler.postDelayed(this, tick);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);
        status = findViewById(R.id.setup_status);
        minutesJar = findViewById(R.id.minutes_jar);
        nextQuiz = findViewById(R.id.setup_next_quiz);
        scheduleHealth = findViewById(R.id.setup_schedule_health);
        statsCard = findViewById(R.id.stats_card);
        statsMood = findViewById(R.id.stats_mood);
        statsAskedNum = findViewById(R.id.stats_asked_num);
        statsRightNum = findViewById(R.id.stats_right_num);
        statsWrongNum = findViewById(R.id.stats_wrong_num);
        statsSkippedNum = findViewById(R.id.stats_skipped_num);
        syncEnabled = findViewById(R.id.sync_enabled);
        curriculumSpinner = findViewById(R.id.curriculum_spinner);
        tierSpinner = findViewById(R.id.tier_spinner);
        perfModeSpinner = findViewById(R.id.perf_mode_spinner);
        perfDetail = findViewById(R.id.setup_perf_detail);
        intervalValue = findViewById(R.id.setup_interval_value);
        jitterValue = findViewById(R.id.setup_jitter_value);
        windowValue = findViewById(R.id.setup_window_value);
        Button arm = findViewById(R.id.btn_arm);
        Button force = findViewById(R.id.btn_force);
        addWidget = findViewById(R.id.btn_add_widget);
        reqList = findViewById(R.id.setup_req_list);
        reqCard = findViewById(R.id.setup_req_card);
        Button syncNow = findViewById(R.id.btn_sync_now);

        AppPrefs prefs = new AppPrefs(this);
        syncEnabled.setChecked(prefs.isSyncEnabled());
        syncEnabled.setOnCheckedChangeListener((b, checked) -> {
            prefs.setSyncEnabled(checked);
            if (checked) CurriculumIndexClient.syncIfEnabled(this);
        });
        bindPerfModeSpinner();
        syncNow.setOnClickListener(v -> {
            if (!prefs.isSyncEnabled()) {
                Toast.makeText(this, "Enable auto-sync first", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "Syncing curricula…", Toast.LENGTH_SHORT).show();
            CurriculumIndexClient.syncIfEnabled(this);
            curriculumSpinner.postDelayed(this::bindCurriculumSpinners, 2500);
        });

        findViewById(R.id.btn_setup_interval_minus).setOnClickListener(v -> bumpInterval(-1));
        findViewById(R.id.btn_setup_interval_plus).setOnClickListener(v -> bumpInterval(1));
        findViewById(R.id.btn_setup_jitter_minus).setOnClickListener(v -> bumpJitter(-1));
        findViewById(R.id.btn_setup_jitter_plus).setOnClickListener(v -> bumpJitter(1));

        arm.setOnClickListener(v -> armNow());
        force.setOnClickListener(v -> {
            Intent i = new Intent(this, QuizActivity.class);
            i.putExtra(QuizActivity.EXTRA_TRIGGER, "adb_force");
            startActivity(i);
        });
        if (addWidget != null) {
            addWidget.setOnClickListener(v -> requestHomeWidget());
        }
        bindCurriculumSpinners();
        applyDevExtras(getIntent());
        refreshScheduleLabels();
        refreshStatus();
        CurriculumIndexClient.syncIfEnabled(this);
    }

    private void bindPerfModeSpinner() {
        if (perfModeSpinner == null) return;
        AppPrefs prefs = new AppPrefs(this);
        // Order: Auto (0), Performance (1), Slim (2)
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.setup_perf_auto));
        labels.add(getString(R.string.setup_perf_performance));
        labels.add(getString(R.string.setup_perf_slim));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels);
        suppressPerfSpinner = true;
        perfModeSpinner.setAdapter(adapter);
        String pref = prefs.getPerformanceMode();
        int sel = 0;
        if (PerformanceMode.PERFORMANCE.equals(pref)) sel = 1;
        else if (PerformanceMode.SLIM.equals(pref)) sel = 2;
        perfModeSpinner.setSelection(sel);
        refreshPerfDetail(pref);
        perfModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressPerfSpinner) return;
                String mode = PerformanceMode.AUTO;
                if (position == 1) mode = PerformanceMode.PERFORMANCE;
                else if (position == 2) mode = PerformanceMode.SLIM;
                AppPrefs p = new AppPrefs(SetupActivity.this);
                if (!mode.equals(p.getPerformanceMode())) {
                    p.setPerformanceMode(mode);
                    PerformanceMode.reevaluate(SetupActivity.this, "setup_pref");
                }
                refreshPerfDetail(mode);
                countdownHandler.removeCallbacks(countdownTick);
                countdownHandler.post(countdownTick);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        suppressPerfSpinner = false;
    }

    private void refreshPerfDetail(String preference) {
        if (perfDetail == null) return;
        String pref = PerformanceMode.normalize(preference);
        if (PerformanceMode.SLIM.equals(pref)) {
            perfDetail.setText(R.string.setup_perf_slim_detail);
        } else if (PerformanceMode.PERFORMANCE.equals(pref)) {
            perfDetail.setText(R.string.setup_perf_performance_detail);
        } else {
            perfDetail.setText(R.string.setup_perf_auto_detail);
        }
    }

    private void bumpInterval(int steps) {
        AppPrefs prefs = new AppPrefs(this);
        prefs.setIntervalMs(Scheduler.bump(prefs.getIntervalMs(), steps));
        onScheduleChanged();
    }

    private void bumpJitter(int steps) {
        AppPrefs prefs = new AppPrefs(this);
        prefs.setJitterMs(Scheduler.bump(prefs.getJitterMs(), steps));
        onScheduleChanged();
    }

    private void onScheduleChanged() {
        refreshScheduleLabels();
        AppPrefs prefs = new AppPrefs(this);
        if (prefs.isArmed()) {
            AlarmScheduler.armAll(this);
            Toast.makeText(this, "Schedule updated", Toast.LENGTH_SHORT).show();
        }
        refreshStatus();
    }

    private void refreshScheduleLabels() {
        AppPrefs prefs = new AppPrefs(this);
        long interval = prefs.getIntervalMs();
        long jitter = prefs.getJitterMs();
        if (intervalValue != null) intervalValue.setText(Scheduler.formatMinutes(interval));
        if (jitterValue != null) jitterValue.setText(Scheduler.formatMinutes(jitter));
        if (windowValue != null) {
            windowValue.setText(getString(R.string.setup_window_label) + ": "
                    + Scheduler.formatWindow(interval, jitter));
        }
    }

    private void bindCurriculumSpinners() {
        AppPrefs prefs = new AppPrefs(this);
        packList.clear();
        packList.addAll(CurriculumStore.listInstalledAndBundled(this));
        List<String> labels = new ArrayList<>();
        int selected = 0;
        for (int i = 0; i < packList.size(); i++) {
            CurriculumPack p = packList.get(i);
            labels.add(p.title + " (ages " + p.ageMin + "–" + p.ageMax + ")");
            if (p.id.equals(prefs.getActiveCurriculumId())) selected = i;
        }
        if (labels.isEmpty()) labels.add("basic-math");
        suppressSpinner = true;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels);
        curriculumSpinner.setAdapter(adapter);
        curriculumSpinner.setSelection(Math.min(selected, Math.max(0, labels.size() - 1)));
        curriculumSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressSpinner || position < 0 || position >= packList.size()) return;
                prefs.setActiveCurriculumId(packList.get(position).id);
                bindTierSpinner(packList.get(position));
                refreshStatus();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        if (!packList.isEmpty()) bindTierSpinner(packList.get(Math.min(selected, packList.size() - 1)));
        suppressSpinner = false;
    }

    private void bindTierSpinner(CurriculumPack pack) {
        AppPrefs prefs = new AppPrefs(this);
        List<String> tiers = new ArrayList<>();
        tiers.add("all");
        for (CurriculumPack.Tier t : pack.tiers) {
            tiers.add(t.id + " — " + t.label);
        }
        int selected = 0;
        String active = prefs.getActiveTier();
        for (int i = 0; i < tiers.size(); i++) {
            String id = i == 0 ? "all" : pack.tiers.get(i - 1).id;
            if (id.equals(active)) selected = i;
        }
        suppressSpinner = true;
        tierSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, tiers));
        tierSpinner.setSelection(selected);
        tierSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressSpinner) return;
                String tierId = position == 0 ? "all" : pack.tiers.get(position - 1).id;
                prefs.setActiveTier(tierId);
                refreshStatus();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        suppressSpinner = false;
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyDevExtras(intent);
        if (intent != null && intent.getBooleanExtra(ScoreWidgetProvider.EXTRA_WIDGET_PINNED, false)) {
            Toast.makeText(this, R.string.setup_widget_pinned, Toast.LENGTH_SHORT).show();
            ScoreWidgetProvider.refreshAll(this);
        }
        refreshStatus();
    }

    private void requestHomeWidget() {
        if (ScoreWidgetProvider.isPinned(this)) {
            Toast.makeText(this, R.string.setup_widget_pinned, Toast.LENGTH_SHORT).show();
            refreshWidgetButton();
            return;
        }
        boolean prompted = ScoreWidgetProvider.requestPin(this);
        if (prompted) {
            Toast.makeText(this, R.string.setup_widget_pin_prompt, Toast.LENGTH_SHORT).show();
        } else {
            ScoreWidgetProvider.toastManualAddPath(this);
        }
    }

    private void applyDevExtras(Intent in) {
        if (in == null) return;
        AppPrefs prefs = new AppPrefs(this);
        if (in.hasExtra("interval_ms")) {
            long ms = in.getLongExtra("interval_ms", 0);
            if (ms > 0) prefs.setIntervalMs(ms);
        } else {
            long cur = prefs.getIntervalMs();
            if (cur == 2L * 60L * 60L * 1000L) {
                prefs.setIntervalMs(Scheduler.DEFAULT_INTERVAL_MS);
            }
        }
        if (in.hasExtra("jitter_ms")) {
            long ms = in.getLongExtra("jitter_ms", -1);
            if (ms >= 0) prefs.setJitterMs(ms);
        }
        if (in.hasExtra("math_entry")) {
            prefs.setMathEntryMode(in.getStringExtra("math_entry"));
        }
        if (in.getBooleanExtra("auto_arm", false)) {
            skipAuthWarn = true;
            armNow();
            skipAuthWarn = false;
        }
        // Smoke: schedule next interval fire in N ms without changing stored 30±15 prefs.
        if (in.hasExtra("next_in_ms")) {
            long delay = in.getLongExtra("next_in_ms", 0);
            if (delay > 0) {
                long at = System.currentTimeMillis() + delay;
                AlarmScheduler.scheduleInterval(this, at);
                prefs.setNextAlarmAt(at);
                android.util.Log.i(AlarmScheduler.TAG, "dev next_in_ms=" + delay + " at=" + at);
            }
        }
        refreshScheduleLabels();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStatus();
        countdownHandler.removeCallbacks(countdownTick);
        countdownHandler.post(countdownTick);
    }

    @Override protected void onPause() {
        countdownHandler.removeCallbacks(countdownTick);
        super.onPause();
    }

    private void armNow() {
        AlarmScheduler.armAll(this);
        refreshStatus();
        ScheduleAuth.Snapshot snap = ScheduleAuth.snapshot(this);
        if (!skipAuthWarn && snap.hasRequiredGap()) {
            showAuthSoftWarn();
            return;
        }
        finishArmSuccess();
    }

    /** Soft warning only — never hard-blocks Arm / schedule. */
    private void showAuthSoftWarn() {
        String missing = ScheduleAuth.missingRequiredSummary(this);
        if (missing.isEmpty()) {
            finishArmSuccess();
            return;
        }
        String message = getString(R.string.setup_auth_warn_body, missing);
        new AlertDialog.Builder(this)
                .setTitle(R.string.setup_auth_warn_title)
                .setMessage(message)
                .setNegativeButton(R.string.setup_auth_warn_back, (d, w) -> {
                    // Stay on checklist and open Settings for the first missing item.
                    if (reqCard != null) reqCard.requestFocus();
                    ScheduleAuth.promptNextGap(this);
                    refreshStatus();
                })
                .setPositiveButton(R.string.setup_auth_warn_proceed, (d, w) -> {
                    // Proceed anyway — do not force remaining validation.
                    Toast.makeText(this, "Armed", Toast.LENGTH_SHORT).show();
                    maybeRequestPhotos();
                    refreshStatus();
                })
                .setCancelable(true)
                .show();
    }

    private void finishArmSuccess() {
        // Prompt the next missing grant (one at a time, cooldown in ScheduleAuth).
        ScheduleAuth.promptNextGap(this);
        maybeRequestPhotos();
        Toast.makeText(this, "Armed", Toast.LENGTH_SHORT).show();
        refreshStatus();
    }

    /** Optional gallery backdrop — never stacks with auth Settings prompts. */
    private void maybeRequestPhotos() {
        ScheduleAuth.Snapshot s = ScheduleAuth.snapshot(this);
        // Don't stack photo prompt over required Settings screens.
        if (s.hasRequiredGap()) return;
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES}, 2);
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 2);
            }
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                     int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && permissions != null) {
            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.POST_NOTIFICATIONS.equals(permissions[i])
                        && (grantResults == null || i >= grantResults.length
                        || grantResults[i] != PackageManager.PERMISSION_GRANTED)) {
                    ScheduleAuth.markNotificationDenied(this);
                }
            }
        }
        refreshStatus();
    }

    private void refreshStatus() {
        AppPrefs p = new AppPrefs(this);
        refreshDayStats();
        refreshCountdown();
        refreshWidgetButton();
        refreshRequirements();
        if (minutesJar != null) {
            minutesJar.setText("Minutes jar: " + p.getMinutesBalance());
        }
        StringBuilder sb = new StringBuilder();
        sb.append(p.isArmed() ? "Armed" : "Not armed");
        sb.append("\nSchedule: ~").append(Scheduler.formatMinutes(p.getIntervalMs()));
        sb.append(" ±").append(Scheduler.formatMinutes(p.getJitterMs()));
        sb.append(" (").append(Scheduler.formatWindow(p.getIntervalMs(), p.getJitterMs())).append(")");
        sb.append("\n+ morning gate");
        sb.append("\nCurriculum: ").append(p.getActiveCurriculumId());
        sb.append(" · tier ").append(p.getActiveTier());
        sb.append("\nSync: ").append(p.isSyncEnabled() ? "on (Wi‑Fi)" : "off");
        sb.append("\nMath entry: ").append(p.getMathEntryMode());
        status.setText(sb.toString());
    }

    private void refreshRequirements() {
        if (reqList == null) return;
        reqList.removeAllViews();
        int pad = dp(12);
        int gap = dp(8);
        for (ScheduleAuth.CheckItem item : ScheduleAuth.checklist(this)) {
            reqList.addView(buildReqRow(item.titleRes, item.detailRes, item.required, item.met,
                    item.met ? null : () -> ScheduleAuth.openGap(this, item.gap)),
                    rowParams(gap));
        }
        boolean widgetPinned = ScoreWidgetProvider.isPinned(this);
        reqList.addView(buildReqRow(R.string.setup_req_widget_title, R.string.setup_req_widget_detail,
                false, widgetPinned, widgetPinned ? null : this::requestHomeWidget),
                rowParams(gap));
        reqList.setPadding(0, 0, 0, pad / 2);
    }

    private LinearLayout.LayoutParams rowParams(int gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = gap;
        return lp;
    }

    private View buildReqRow(int titleRes, int detailRes, boolean required, boolean met,
                             Runnable onTap) {
        int pad = dp(12);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(pad, pad, pad, pad);
        row.setBackgroundResource(R.drawable.setup_req_row);
        if (onTap != null) {
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> onTap.run());
        }

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView mark = new TextView(this);
        mark.setText(met ? "✓" : "!");
        mark.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        mark.setTextColor(getResources().getColor(
                met ? R.color.stats_mood_great : R.color.stats_mood_trying, getTheme()));
        mark.setPadding(0, 0, dp(10), 0);
        top.addView(mark);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText(titleRes);
        title.setTextColor(getResources().getColor(R.color.header, getTheme()));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        textCol.addView(title);

        TextView badge = new TextView(this);
        badge.setText(required ? R.string.setup_req_badge_required : R.string.setup_req_badge_optional);
        badge.setTextColor(getResources().getColor(R.color.symbolic, getTheme()));
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        textCol.addView(badge);

        top.addView(textCol);

        TextView statusTv = new TextView(this);
        statusTv.setText(met ? R.string.setup_req_status_ready : R.string.setup_req_status_needed);
        statusTv.setTextColor(getResources().getColor(
                met ? R.color.stats_mood_great : R.color.stats_mood_trying, getTheme()));
        statusTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        statusTv.setTypeface(statusTv.getTypeface(), android.graphics.Typeface.BOLD);
        top.addView(statusTv);

        row.addView(top);

        TextView detail = new TextView(this);
        detail.setText(detailRes);
        detail.setTextColor(getResources().getColor(R.color.header, getTheme()));
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        detail.setPadding(dp(30), dp(4), 0, 0);
        row.addView(detail);
        return row;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    private void refreshCountdown() {
        if (nextQuiz == null) return;
        AppPrefs p = new AppPrefs(this);
        if (!p.isArmed() || p.nextAlarmAt() <= 0L) {
            nextQuiz.setText(R.string.setup_next_quiz_unarmed);
            refreshScheduleHealth(p);
            return;
        }
        long mins = Scheduler.minutesUntil(p.nextAlarmAt(), System.currentTimeMillis());
        if (mins <= 0L) {
            nextQuiz.setText(R.string.setup_next_quiz_due);
        } else {
            nextQuiz.setText(getString(R.string.setup_next_quiz_armed, mins));
        }
        refreshScheduleHealth(p);
    }

    /** Absolute last-fired / next-alarm timestamps so schedule health is visible without adb. */
    private void refreshScheduleHealth(AppPrefs p) {
        if (scheduleHealth == null) return;
        long last = p.lastFiredAt();
        long next = p.nextAlarmAt();
        String lastLabel = last > 0L
                ? formatClock(last)
                : getString(R.string.setup_schedule_health_never);
        String nextLabel = (p.isArmed() && next > 0L)
                ? formatClock(next)
                : "—";
        scheduleHealth.setText(getString(R.string.setup_schedule_health_line, lastLabel, nextLabel));
    }

    private String formatClock(long ms) {
        return android.text.format.DateFormat.getTimeFormat(this).format(new java.util.Date(ms));
    }

    private void refreshWidgetButton() {
        if (addWidget == null) return;
        boolean pinned = ScoreWidgetProvider.isPinned(this);
        addWidget.setEnabled(!pinned);
        addWidget.setText(pinned ? R.string.setup_widget_pinned : R.string.setup_add_widget);
        addWidget.setAlpha(pinned ? 0.55f : 1f);
    }

    private void refreshDayStats() {
        StatsStore.DayStats day;
        try {
            StatsStore store = new StatsStore(new File(getFilesDir(), "stats"));
            day = store.dayStats(new SystemClock().nowMillis(), TimeZone.getDefault());
        } catch (Exception e) {
            day = new StatsStore.DayStats();
        }
        statsAskedNum.setText(String.valueOf(day.questionsAsked));
        statsRightNum.setText(String.valueOf(day.answeredRight));
        if (statsWrongNum != null) statsWrongNum.setText(String.valueOf(day.answeredWrong));
        if (statsSkippedNum != null) statsSkippedNum.setText(String.valueOf(day.skipped));
        switch (day.level()) {
            case GREAT:
                statsCard.setBackgroundResource(R.drawable.stats_card_great);
                statsMood.setText(R.string.stats_mood_great);
                statsMood.setTextColor(getResources().getColor(R.color.stats_mood_great, getTheme()));
                statsRightNum.setTextColor(getResources().getColor(R.color.stats_mood_great, getTheme()));
                break;
            case GOOD:
                statsCard.setBackgroundResource(R.drawable.stats_card_good);
                statsMood.setText(R.string.stats_mood_good);
                statsMood.setTextColor(getResources().getColor(R.color.stats_mood_good, getTheme()));
                statsRightNum.setTextColor(getResources().getColor(R.color.stats_mood_good, getTheme()));
                break;
            case KEEP_TRYING:
                statsCard.setBackgroundResource(R.drawable.stats_card_trying);
                statsMood.setText(R.string.stats_mood_trying);
                statsMood.setTextColor(getResources().getColor(R.color.stats_mood_trying, getTheme()));
                statsRightNum.setTextColor(getResources().getColor(R.color.stats_mood_trying, getTheme()));
                break;
            case READY:
            default:
                statsCard.setBackgroundResource(R.drawable.stats_card_ready);
                statsMood.setText(R.string.stats_mood_ready);
                statsMood.setTextColor(getResources().getColor(R.color.stats_mood_ready, getTheme()));
                statsRightNum.setTextColor(getResources().getColor(R.color.stats_mood_great, getTheme()));
                break;
        }
    }
}
