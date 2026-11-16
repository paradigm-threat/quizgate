package net.clevertree.quizgate;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import net.clevertree.quizgate.core.Scheduler;

/**
 * Skippable first-run intro: welcome → photos → schedule → Setup.
 */
public class OnboardingActivity extends Activity {
    private static final int REQ_PHOTOS = 41;
    private static final int STEP_WELCOME = 0;
    private static final int STEP_PHOTOS = 1;
    private static final int STEP_SCHEDULE = 2;

    private AppPrefs prefs;
    private TextView title;
    private TextView body;
    private TextView skip;
    private Button next;
    private LinearLayout photoActions;
    private LinearLayout scheduleActions;
    private TextView intervalValue;
    private TextView jitterValue;
    private TextView windowValue;
    private int step;
    private String pendingPhotoSource = PhotoLibrary.SOURCE_BUNDLED;
    private long draftIntervalMs = Scheduler.DEFAULT_INTERVAL_MS;
    private long draftJitterMs = Scheduler.DEFAULT_JITTER_MS;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new AppPrefs(this);
        if (prefs.isOnboardingDone()) {
            // Opening the app icon → practice quiz (not Setup)
            goPlayQuiz();
            return;
        }
        setContentView(R.layout.activity_onboarding);
        title = findViewById(R.id.onboard_title);
        body = findViewById(R.id.onboard_body);
        skip = findViewById(R.id.onboard_skip);
        next = findViewById(R.id.btn_onboard_next);
        photoActions = findViewById(R.id.onboard_photo_actions);
        scheduleActions = findViewById(R.id.onboard_schedule_actions);
        intervalValue = findViewById(R.id.onboard_interval_value);
        jitterValue = findViewById(R.id.onboard_jitter_value);
        windowValue = findViewById(R.id.onboard_window_value);
        Button useGallery = findViewById(R.id.btn_use_gallery);
        Button useDefaults = findViewById(R.id.btn_use_defaults);

        draftIntervalMs = prefs.getIntervalMs();
        draftJitterMs = prefs.getJitterMs();

        skip.setOnClickListener(v -> finishOnboarding(pendingPhotoSource, true));
        next.setOnClickListener(v -> onNext());
        useGallery.setOnClickListener(v -> requestGalleryThenAdvance());
        useDefaults.setOnClickListener(v -> {
            pendingPhotoSource = PhotoLibrary.SOURCE_BUNDLED;
            goSchedule();
        });
        findViewById(R.id.btn_interval_minus).setOnClickListener(v -> bumpInterval(-1));
        findViewById(R.id.btn_interval_plus).setOnClickListener(v -> bumpInterval(1));
        findViewById(R.id.btn_jitter_minus).setOnClickListener(v -> bumpJitter(-1));
        findViewById(R.id.btn_jitter_plus).setOnClickListener(v -> bumpJitter(1));

        step = STEP_WELCOME;
        showStep();
    }

    private void onNext() {
        if (step == STEP_WELCOME) {
            step = STEP_PHOTOS;
            showStep();
        } else if (step == STEP_PHOTOS) {
            pendingPhotoSource = PhotoLibrary.SOURCE_BUNDLED;
            goSchedule();
        } else if (step == STEP_SCHEDULE) {
            finishOnboarding(pendingPhotoSource, false);
        } else {
            finishOnboarding(pendingPhotoSource, false);
        }
    }

    private void goSchedule() {
        step = STEP_SCHEDULE;
        showStep();
    }

    private void showStep() {
        photoActions.setVisibility(View.GONE);
        scheduleActions.setVisibility(View.GONE);
        next.setVisibility(View.VISIBLE);
        if (step == STEP_WELCOME) {
            title.setText(R.string.onboard_welcome_title);
            body.setText(R.string.onboard_welcome_body);
            next.setText(R.string.onboard_continue);
        } else if (step == STEP_PHOTOS) {
            title.setText(R.string.onboard_photos_title);
            body.setText(R.string.onboard_photos_body);
            photoActions.setVisibility(View.VISIBLE);
            next.setText(R.string.onboard_use_defaults);
        } else if (step == STEP_SCHEDULE) {
            title.setText(R.string.onboard_schedule_title);
            body.setText(R.string.onboard_schedule_body);
            scheduleActions.setVisibility(View.VISIBLE);
            next.setText(R.string.onboard_continue);
            refreshScheduleLabels();
        }
    }

    private void bumpInterval(int steps) {
        draftIntervalMs = Scheduler.clampInterval(Scheduler.bump(draftIntervalMs, steps));
        refreshScheduleLabels();
    }

    private void bumpJitter(int steps) {
        draftJitterMs = Scheduler.clampJitter(Scheduler.bump(draftJitterMs, steps));
        refreshScheduleLabels();
    }

    private void refreshScheduleLabels() {
        intervalValue.setText(Scheduler.formatMinutes(draftIntervalMs));
        jitterValue.setText(Scheduler.formatMinutes(draftJitterMs));
        windowValue.setText(getString(R.string.onboard_window_label) + ": "
                + Scheduler.formatWindow(draftIntervalMs, draftJitterMs));
    }

    private void requestGalleryThenAdvance() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES}, REQ_PHOTOS);
                return;
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_PHOTOS);
                return;
            }
        }
        applyGalleryChoice();
    }

    private void applyGalleryChoice() {
        int n = PhotoLibrary.listGallery(this).size();
        if (n == 0) {
            Toast.makeText(this, "No photos found — using defaults", Toast.LENGTH_SHORT).show();
            pendingPhotoSource = PhotoLibrary.SOURCE_BUNDLED;
        } else {
            Toast.makeText(this, "Using " + n + " device photos", Toast.LENGTH_SHORT).show();
            pendingPhotoSource = PhotoLibrary.SOURCE_GALLERY;
        }
        goSchedule();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PHOTOS) return;
        boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (ok) {
            applyGalleryChoice();
        } else {
            Toast.makeText(this, "Permission declined — using defaults", Toast.LENGTH_SHORT).show();
            pendingPhotoSource = PhotoLibrary.SOURCE_BUNDLED;
            goSchedule();
        }
    }

    private void finishOnboarding(String photoSource, boolean useDefaultSchedule) {
        prefs.setPhotoSource(photoSource);
        if (useDefaultSchedule) {
            prefs.setIntervalMs(Scheduler.DEFAULT_INTERVAL_MS);
            prefs.setJitterMs(Scheduler.DEFAULT_JITTER_MS);
        } else {
            prefs.setIntervalMs(draftIntervalMs);
            prefs.setJitterMs(draftJitterMs);
        }
        prefs.setOnboardingDone(true);
        // First-run: parents arm on Setup; later launches open play quiz
        goSetup();
    }

    private void goSetup() {
        Intent i = new Intent(this, SetupActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        // Preserve ADB extras (auto_arm, interval_ms, jitter_ms, …)
        if (getIntent() != null && getIntent().getExtras() != null) {
            i.putExtras(getIntent());
        }
        startActivity(i);
        finish();
    }

    private void goPlayQuiz() {
        Intent i = new Intent(this, QuizActivity.class);
        i.putExtra(QuizActivity.EXTRA_TRIGGER, QuizActivity.TRIGGER_PLAY);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        if (getIntent() != null && getIntent().getExtras() != null) {
            i.putExtras(getIntent());
            // Ensure play trigger wins over any stale extras
            i.putExtra(QuizActivity.EXTRA_TRIGGER, QuizActivity.TRIGGER_PLAY);
        }
        startActivity(i);
        finish();
    }
}
