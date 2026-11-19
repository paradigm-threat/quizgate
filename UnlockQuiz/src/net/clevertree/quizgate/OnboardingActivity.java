package net.clevertree.quizgate;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
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
import net.clevertree.quizgate.telemetry.RemoteRegister;

/**
 * Skippable first-run intro: welcome → photos → schedule → performance →
 * lock explainer → anonymous progress share → permissions (one ScheduleAuth
 * gap at a time) → Setup. Quiz UI is always centered (no placement step).
 */
public class OnboardingActivity extends Activity {
    private static final int REQ_PHOTOS = 41;
    private static final int STEP_WELCOME = 0;
    private static final int STEP_PHOTOS = 1;
    private static final int STEP_SCHEDULE = 2;
    private static final int STEP_PERF = 3;
    private static final int STEP_GATE = 4;
    private static final int STEP_REMOTE = 5;
    private static final int STEP_PERMISSIONS = 6;
    private static final int STEP_DONE = 7;

    private AppPrefs prefs;
    private TextView title;
    private TextView body;
    private TextView skip;
    private Button next;
    private Button permAllow;
    private Button permLater;
    private LinearLayout photoActions;
    private LinearLayout scheduleActions;
    private LinearLayout perfActions;
    private LinearLayout gateActions;
    private LinearLayout remoteActions;
    private LinearLayout permActions;
    private TextView intervalValue;
    private TextView jitterValue;
    private TextView windowValue;
    private int step;
    private String pendingPhotoSource = PhotoLibrary.SOURCE_BUNDLED;
    private String draftPerfMode = PerformanceMode.AUTO;
    private long draftIntervalMs = Scheduler.DEFAULT_INTERVAL_MS;
    private long draftJitterMs = Scheduler.DEFAULT_JITTER_MS;
    /** Parent opted into anonymous Intake progress share (default off). */
    private boolean draftRemoteShare = false;
    /** Gap currently shown on the permissions step (NONE when all granted). */
    private ScheduleAuth.Gap currentPermGap = ScheduleAuth.Gap.NONE;

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
        permAllow = findViewById(R.id.btn_onboard_perm_allow);
        permLater = findViewById(R.id.btn_onboard_perm_later);
        photoActions = findViewById(R.id.onboard_photo_actions);
        scheduleActions = findViewById(R.id.onboard_schedule_actions);
        perfActions = findViewById(R.id.onboard_perf_actions);
        gateActions = findViewById(R.id.onboard_gate_actions);
        remoteActions = findViewById(R.id.onboard_remote_actions);
        permActions = findViewById(R.id.onboard_perm_actions);
        intervalValue = findViewById(R.id.onboard_interval_value);
        jitterValue = findViewById(R.id.onboard_jitter_value);
        windowValue = findViewById(R.id.onboard_window_value);
        Button useGallery = findViewById(R.id.btn_use_gallery);
        Button useDefaults = findViewById(R.id.btn_use_defaults);

        draftIntervalMs = prefs.getIntervalMs();
        draftJitterMs = prefs.getJitterMs();
        draftPerfMode = prefs.getPerformanceMode();

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
        View btnAuto = findViewById(R.id.btn_onboard_perf_auto);
        View btnPerf = findViewById(R.id.btn_onboard_perf_performance);
        View btnSlim = findViewById(R.id.btn_onboard_perf_slim);
        if (btnAuto != null) {
            btnAuto.setOnClickListener(v -> choosePerfAndContinue(PerformanceMode.AUTO));
        }
        if (btnPerf != null) {
            btnPerf.setOnClickListener(v -> choosePerfAndContinue(PerformanceMode.PERFORMANCE));
        }
        if (btnSlim != null) {
            btnSlim.setOnClickListener(v -> choosePerfAndContinue(PerformanceMode.SLIM));
        }
        View btnRemoteShare = findViewById(R.id.btn_onboard_remote_share);
        View btnRemoteLocal = findViewById(R.id.btn_onboard_remote_local);
        if (btnRemoteShare != null) {
            btnRemoteShare.setOnClickListener(v -> chooseRemoteAndContinue(true));
        }
        if (btnRemoteLocal != null) {
            btnRemoteLocal.setOnClickListener(v -> chooseRemoteAndContinue(false));
        }
        if (permAllow != null) {
            permAllow.setOnClickListener(v -> allowCurrentPermission());
        }
        if (permLater != null) {
            permLater.setOnClickListener(v -> skipCurrentPermission());
        }

        step = STEP_WELCOME;
        showStep();
    }

    @Override protected void onResume() {
        super.onResume();
        if (step == STEP_PERMISSIONS) {
            // Returning from Settings: advance past grants, keep one prompt at a time.
            advancePermissionIfGranted();
            showStep();
        }
    }

    private void onNext() {
        if (step == STEP_WELCOME) {
            step = STEP_PHOTOS;
            showStep();
        } else if (step == STEP_PHOTOS) {
            pendingPhotoSource = PhotoLibrary.SOURCE_BUNDLED;
            goSchedule();
        } else if (step == STEP_SCHEDULE) {
            goPerf();
        } else if (step == STEP_PERF) {
            // Continue keeps Auto if they didn't tap a button (default).
            choosePerfAndContinue(draftPerfMode);
        } else if (step == STEP_GATE) {
            chooseGateAndContinue();
        } else if (step == STEP_REMOTE) {
            // Continue without tapping a button keeps progress local.
            chooseRemoteAndContinue(false);
        } else if (step == STEP_DONE) {
            finishOnboarding(pendingPhotoSource, false);
        } else {
            finishOnboarding(pendingPhotoSource, false);
        }
    }

    private void goSchedule() {
        step = STEP_SCHEDULE;
        showStep();
    }

    private void goPerf() {
        step = STEP_PERF;
        showStep();
    }

    private void choosePerfAndContinue(String mode) {
        draftPerfMode = PerformanceMode.normalize(mode);
        goGate();
    }

    private void goGate() {
        step = STEP_GATE;
        showStep();
    }

    private void chooseGateAndContinue() {
        // Hard-only: persist early so permission walk includes Accessibility.
        prefs.setGateMode(GateMode.HARD);
        goRemote();
    }

    private void goRemote() {
        step = STEP_REMOTE;
        showStep();
    }

    private void chooseRemoteAndContinue(boolean share) {
        draftRemoteShare = share;
        goPermissions();
    }

    private void goPermissions() {
        currentPermGap = ScheduleAuth.nextGapAfter(this, ScheduleAuth.Gap.NONE);
        if (currentPermGap == ScheduleAuth.Gap.NONE) {
            step = STEP_DONE;
        } else {
            step = STEP_PERMISSIONS;
        }
        showStep();
    }

    private void advancePermissionIfGranted() {
        if (currentPermGap == ScheduleAuth.Gap.NONE) {
            currentPermGap = ScheduleAuth.nextGapAfter(this, ScheduleAuth.Gap.NONE);
        } else if (ScheduleAuth.isGranted(this, currentPermGap)) {
            currentPermGap = ScheduleAuth.nextGapAfter(this, currentPermGap);
        } else {
            // Still missing — keep showing the same gap (do not auto-open Settings).
            return;
        }
        if (currentPermGap == ScheduleAuth.Gap.NONE) {
            step = STEP_DONE;
        }
    }

    private void allowCurrentPermission() {
        if (currentPermGap == null || currentPermGap == ScheduleAuth.Gap.NONE) {
            step = STEP_DONE;
            showStep();
            return;
        }
        // openGap respects cooldown and opens exactly one Settings / runtime prompt.
        ScheduleAuth.openGap(this, currentPermGap);
    }

    private void skipCurrentPermission() {
        if (ScheduleAuth.isCritical(this, currentPermGap)) {
            Toast.makeText(this, R.string.onboard_perm_critical_skip_toast, Toast.LENGTH_LONG).show();
        }
        // Move to the next unmet gap without opening Settings again.
        currentPermGap = ScheduleAuth.nextGapAfter(this, currentPermGap);
        if (currentPermGap == ScheduleAuth.Gap.NONE) {
            step = STEP_DONE;
        }
        showStep();
    }

    private void showStep() {
        photoActions.setVisibility(View.GONE);
        scheduleActions.setVisibility(View.GONE);
        if (perfActions != null) perfActions.setVisibility(View.GONE);
        if (gateActions != null) gateActions.setVisibility(View.GONE);
        if (remoteActions != null) remoteActions.setVisibility(View.GONE);
        if (permActions != null) permActions.setVisibility(View.GONE);
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
        } else if (step == STEP_PERF) {
            title.setText(R.string.onboard_perf_title);
            body.setText(R.string.onboard_perf_body);
            if (perfActions != null) perfActions.setVisibility(View.VISIBLE);
            next.setVisibility(View.GONE);
        } else if (step == STEP_GATE) {
            title.setText(R.string.onboard_gate_title);
            body.setText(R.string.onboard_gate_body);
            if (gateActions != null) gateActions.setVisibility(View.GONE);
            next.setVisibility(View.VISIBLE);
            next.setText(R.string.onboard_continue);
        } else if (step == STEP_REMOTE) {
            title.setText(R.string.onboard_remote_title);
            body.setText(R.string.onboard_remote_body);
            if (remoteActions != null) remoteActions.setVisibility(View.VISIBLE);
            next.setVisibility(View.GONE);
        } else if (step == STEP_PERMISSIONS) {
            showPermissionStep();
        } else if (step == STEP_DONE) {
            title.setText(R.string.onboard_done_title);
            String missing = ScheduleAuth.missingRequiredSummary(this);
            if (!missing.isEmpty()) {
                body.setText(getString(R.string.onboard_done_body_missing, missing));
            } else {
                body.setText(R.string.onboard_done_body);
            }
            next.setText(R.string.onboard_continue);
        }
    }

    private void showPermissionStep() {
        if (currentPermGap == null || currentPermGap == ScheduleAuth.Gap.NONE) {
            step = STEP_DONE;
            showStep();
            return;
        }
        int titleRes = ScheduleAuth.onboardTitleRes(currentPermGap);
        int bodyRes = ScheduleAuth.onboardBodyRes(currentPermGap);
        if (titleRes != 0) title.setText(titleRes);
        if (bodyRes != 0) body.setText(bodyRes);
        next.setVisibility(View.GONE);
        if (permActions != null) {
            permActions.setVisibility(View.VISIBLE);
            if (permAllow != null) {
                permAllow.setText(ScheduleAuth.isCritical(this, currentPermGap)
                        ? R.string.onboard_perm_allow_required
                        : R.string.onboard_perm_allow);
            }
            if (permLater != null) {
                permLater.setText(ScheduleAuth.isCritical(this, currentPermGap)
                        ? R.string.onboard_perm_later_critical
                        : R.string.onboard_perm_later);
            }
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
        if (requestCode == 1) {
            // Notification runtime prompt from ScheduleAuth during permissions step.
            if (permissions != null) {
                for (int i = 0; i < permissions.length; i++) {
                    if (Manifest.permission.POST_NOTIFICATIONS.equals(permissions[i])
                            && (grantResults == null || i >= grantResults.length
                            || grantResults[i] != PackageManager.PERMISSION_GRANTED)) {
                        ScheduleAuth.markNotificationDenied(this);
                    }
                }
            }
            if (step == STEP_PERMISSIONS) {
                advancePermissionIfGranted();
                showStep();
            }
            return;
        }
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
            prefs.setPerformanceMode(PerformanceMode.AUTO);
            prefs.setGateMode(GateMode.HARD);
            // Skip leaves progress local unless they already chose Share.
        } else {
            prefs.setIntervalMs(draftIntervalMs);
            prefs.setJitterMs(draftJitterMs);
            prefs.setPerformanceMode(draftPerfMode);
            prefs.setGateMode(GateMode.HARD);
        }
        prefs.setRemoteManagementEnabled(draftRemoteShare);
        try {
            PerformanceMode.reevaluate(this, "onboarding");
        } catch (Exception ignored) {}
        prefs.setOnboardingDone(true);
        if (draftRemoteShare) {
            final Context app = getApplicationContext();
            new Thread(() -> {
                RemoteRegister.register(app, true, null);
                Diag.flushLocalOnly(app);
            }, "qg-onboard-remote").start();
        }
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
