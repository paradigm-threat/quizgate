package net.clevertree.quizgate;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
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
    private View statsCard;
    private TextView statsMood;
    private TextView statsAskedNum;
    private TextView statsRightNum;
    private TextView statsWrongNum;
    private TextView statsSkippedNum;
    private CheckBox syncEnabled;
    private Spinner curriculumSpinner;
    private Spinner tierSpinner;
    private TextView intervalValue;
    private TextView jitterValue;
    private TextView windowValue;
    private final List<CurriculumPack> packList = new ArrayList<>();
    private boolean suppressSpinner;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);
        status = findViewById(R.id.setup_status);
        minutesJar = findViewById(R.id.minutes_jar);
        statsCard = findViewById(R.id.stats_card);
        statsMood = findViewById(R.id.stats_mood);
        statsAskedNum = findViewById(R.id.stats_asked_num);
        statsRightNum = findViewById(R.id.stats_right_num);
        statsWrongNum = findViewById(R.id.stats_wrong_num);
        statsSkippedNum = findViewById(R.id.stats_skipped_num);
        syncEnabled = findViewById(R.id.sync_enabled);
        curriculumSpinner = findViewById(R.id.curriculum_spinner);
        tierSpinner = findViewById(R.id.tier_spinner);
        intervalValue = findViewById(R.id.setup_interval_value);
        jitterValue = findViewById(R.id.setup_jitter_value);
        windowValue = findViewById(R.id.setup_window_value);
        Button arm = findViewById(R.id.btn_arm);
        Button force = findViewById(R.id.btn_force);
        Button syncNow = findViewById(R.id.btn_sync_now);

        AppPrefs prefs = new AppPrefs(this);
        syncEnabled.setChecked(prefs.isSyncEnabled());
        syncEnabled.setOnCheckedChangeListener((b, checked) -> {
            prefs.setSyncEnabled(checked);
            if (checked) CurriculumIndexClient.syncIfEnabled(this);
        });
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
        bindCurriculumSpinners();
        applyDevExtras(getIntent());
        refreshScheduleLabels();
        refreshStatus();
        CurriculumIndexClient.syncIfEnabled(this);
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
        refreshStatus();
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
            armNow();
        }
        refreshScheduleLabels();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void armNow() {
        AlarmScheduler.armAll(this);
        requestPerms();
        requestBattery();
        requestUsageAccess();
        Toast.makeText(this, "Armed", Toast.LENGTH_SHORT).show();
        refreshStatus();
    }

    private void requestUsageAccess() {
        if (ForegroundApp.hasUsageAccess(this)) return;
        Toast.makeText(this,
                "Turn on Usage access for QuizGate — so unlock returns to the last app",
                Toast.LENGTH_LONG).show();
        ForegroundApp.openUsageAccessSettings(this);
    }

    private void requestPerms() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES}, 2);
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 2);
            }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = getSystemService(AlarmManager.class);
            if (am != null && !am.canScheduleExactAlarms()) {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception ignored) {}
            }
        }
    }

    private void requestBattery() {
        if (Build.VERSION.SDK_INT < 23) return;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            try {
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                } catch (Exception ignored) {}
            }
        }
    }

    private void refreshStatus() {
        AppPrefs p = new AppPrefs(this);
        refreshDayStats();
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
        if (p.nextAlarmAt() > 0) {
            sb.append("\nNext alarm at: ").append(p.nextAlarmAt());
        }
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = getSystemService(AlarmManager.class);
            boolean exact = am != null && am.canScheduleExactAlarms();
            sb.append("\nExact alarms: ").append(exact ? "yes" : "NO — tap Arm");
        }
        if (Build.VERSION.SDK_INT >= 23) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            boolean ign = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
            sb.append("\nBattery unrestricted: ").append(ign ? "yes" : "NO — tap Arm");
        }
        sb.append("\nUsage access (return to last app): ")
                .append(ForegroundApp.hasUsageAccess(this) ? "yes" : "NO — tap Arm");
        status.setText(sb.toString());
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
