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

/**
 * Skippable first-run intro. Chooses gallery vs bundled backdrop catalog.
 */
public class OnboardingActivity extends Activity {
    private static final int REQ_PHOTOS = 41;

    private AppPrefs prefs;
    private TextView title;
    private TextView body;
    private TextView skip;
    private Button next;
    private LinearLayout photoActions;
    private int step; // 0 welcome, 1 photos, 2 done

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new AppPrefs(this);
        if (prefs.isOnboardingDone()) {
            goSetup();
            return;
        }
        setContentView(R.layout.activity_onboarding);
        title = findViewById(R.id.onboard_title);
        body = findViewById(R.id.onboard_body);
        skip = findViewById(R.id.onboard_skip);
        next = findViewById(R.id.btn_onboard_next);
        photoActions = findViewById(R.id.onboard_photo_actions);
        Button useGallery = findViewById(R.id.btn_use_gallery);
        Button useDefaults = findViewById(R.id.btn_use_defaults);

        skip.setOnClickListener(v -> finishOnboarding(PhotoLibrary.SOURCE_BUNDLED));
        next.setOnClickListener(v -> onNext());
        useGallery.setOnClickListener(v -> requestGalleryThenFinish());
        useDefaults.setOnClickListener(v -> finishOnboarding(PhotoLibrary.SOURCE_BUNDLED));

        step = 0;
        showStep();
    }

    private void onNext() {
        if (step == 0) {
            step = 1;
            showStep();
        } else if (step == 1) {
            // Continue without explicit choice → defaults
            finishOnboarding(PhotoLibrary.SOURCE_BUNDLED);
        } else {
            finishOnboarding(prefs.getPhotoSource());
        }
    }

    private void showStep() {
        photoActions.setVisibility(View.GONE);
        next.setVisibility(View.VISIBLE);
        if (step == 0) {
            title.setText(R.string.onboard_welcome_title);
            body.setText(R.string.onboard_welcome_body);
            next.setText(R.string.onboard_continue);
        } else if (step == 1) {
            title.setText(R.string.onboard_photos_title);
            body.setText(R.string.onboard_photos_body);
            photoActions.setVisibility(View.VISIBLE);
            next.setText(R.string.onboard_use_defaults);
        } else {
            title.setText(R.string.onboard_done_title);
            body.setText(R.string.onboard_done_body);
            next.setText(R.string.onboard_continue);
        }
    }

    private void requestGalleryThenFinish() {
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
            finishOnboarding(PhotoLibrary.SOURCE_BUNDLED);
        } else {
            Toast.makeText(this, "Using " + n + " device photos", Toast.LENGTH_SHORT).show();
            finishOnboarding(PhotoLibrary.SOURCE_GALLERY);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PHOTOS) return;
        boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (ok) {
            applyGalleryChoice();
        } else {
            Toast.makeText(this, "Permission declined — using defaults", Toast.LENGTH_SHORT).show();
            finishOnboarding(PhotoLibrary.SOURCE_BUNDLED);
        }
    }

    private void finishOnboarding(String photoSource) {
        prefs.setPhotoSource(photoSource);
        prefs.setOnboardingDone(true);
        goSetup();
    }

    private void goSetup() {
        Intent i = new Intent(this, SetupActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        // Preserve ADB extras (auto_arm, interval_ms, …)
        if (getIntent() != null && getIntent().getExtras() != null) {
            i.putExtras(getIntent());
        }
        startActivity(i);
        finish();
    }
}
