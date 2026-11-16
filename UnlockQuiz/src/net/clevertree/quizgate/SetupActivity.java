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
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import net.clevertree.quizgate.core.Scheduler;
import net.clevertree.quizgate.core.StatsStore;
import net.clevertree.quizgate.core.SystemClock;

import java.io.File;
import java.util.TimeZone;

public class SetupActivity extends Activity {
    private TextView status;
    private View statsCard;
    private TextView statsMood;
    private TextView statsAskedNum;
    private TextView statsRightNum;
    private TextView statsWrongNum;
    private TextView statsSkippedNum;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);
        status = findViewById(R.id.setup_status);
        statsCard = findViewById(R.id.stats_card);
        statsMood = findViewById(R.id.stats_mood);
        statsAskedNum = findViewById(R.id.stats_asked_num);
        statsRightNum = findViewById(R.id.stats_right_num);
        statsWrongNum = findViewById(R.id.stats_wrong_num);
        statsSkippedNum = findViewById(R.id.stats_skipped_num);
        Button arm = findViewById(R.id.btn_arm);
        Button force = findViewById(R.id.btn_force);
        arm.setOnClickListener(v -> {
            // Product default: 1 hour (ADB may override via intent extras first)
            new AppPrefs(this).setIntervalMs(Scheduler.DEFAULT_INTERVAL_MS);
            armNow();
        });
        force.setOnClickListener(v -> {
            Intent i = new Intent(this, QuizActivity.class);
            i.putExtra(QuizActivity.EXTRA_TRIGGER, "adb_force");
            startActivity(i);
        });
        applyDevExtras(getIntent());
        refreshStatus();
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
            // Migrate old 2h installs to 1h product default
            long cur = prefs.getIntervalMs();
            if (cur == 2L * 60L * 60L * 1000L) {
                prefs.setIntervalMs(Scheduler.DEFAULT_INTERVAL_MS);
            }
        }
        if (in.hasExtra("math_entry")) {
            prefs.setMathEntryMode(in.getStringExtra("math_entry"));
        }
        if (in.getBooleanExtra("auto_arm", false)) {
            armNow();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void armNow() {
        // Schedule first so ADB auto_arm works even if a permission sheet opens after.
        AlarmScheduler.armAll(this);
        requestPerms();
        requestBattery();
        Toast.makeText(this, "Armed", Toast.LENGTH_SHORT).show();
        refreshStatus();
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
        StringBuilder sb = new StringBuilder();
        sb.append(p.isArmed() ? "Armed" : "Not armed");
        long mins = Math.max(1, p.getIntervalMs() / 60000L);
        sb.append("\nEvery ").append(mins).append(" min + morning");
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
