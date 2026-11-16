package net.clevertree.quizgate;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

/**
 * Soft-pause whatever is playing (YouTube, music, games with audio) when the quiz
 * appears, then resume after unlock. Uses exclusive transient audio focus + media
 * key events — no Accessibility Service required. Resume is best-effort.
 */
public final class MediaGate {
    private static final String TAG = AlarmScheduler.TAG;

    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private boolean heldFocus;
    private boolean shouldResume;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public void pauseForQuiz(Context ctx) {
        Context app = ctx.getApplicationContext();
        audioManager = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) return;
        shouldResume = audioManager.isMusicActive();
        requestExclusiveTransientFocus();
        // Explicit pause helps players that only duck on focus loss (YouTube, etc.)
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE);
        handler.postDelayed(() -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE), 180);
        Log.i(TAG, "media pause for quiz wasPlaying=" + shouldResume + " focus=" + heldFocus);
        try {
            Diag.breadcrumb(app, net.clevertree.quizgate.core.DiagBreadcrumb.builder(
                            System.currentTimeMillis(),
                            net.clevertree.quizgate.core.DiagnosticsStore.KIND_MEDIA)
                    .mediaFocus(heldFocus)
                    .message(shouldResume ? "was_playing" : "idle")
                    .build());
        } catch (Exception ignored) {}
    }

    public void resumeAfterQuiz() {
        handler.postDelayed(() -> {
            try {
                // Abandon focus first so the previous player is eligible to resume
                abandonFocus();
                if (shouldResume) {
                    handler.postDelayed(() -> {
                        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY);
                        Log.i(TAG, "media resume after quiz");
                    }, 200);
                }
            } finally {
                shouldResume = false;
            }
        }, 350);
    }

    private void requestExclusiveTransientFocus() {
        if (audioManager == null) return;
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            // EXCLUSIVE: tell players (incl. YouTube) to pause, not merely duck
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(focusChange -> {})
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(true)
                    .build();
            int r = audioManager.requestAudioFocus(focusRequest);
            heldFocus = (r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
            if (!heldFocus) {
                // Fallback if exclusive is denied
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(attrs)
                        .setOnAudioFocusChangeListener(focusChange -> {})
                        .setAcceptsDelayedFocusGain(false)
                        .build();
                r = audioManager.requestAudioFocus(focusRequest);
                heldFocus = (r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
            }
        } catch (Exception e) {
            Log.e(TAG, "audio focus request failed", e);
        }
    }

    private void abandonFocus() {
        if (audioManager == null || !heldFocus) return;
        try {
            if (focusRequest != null) {
                audioManager.abandonAudioFocusRequest(focusRequest);
            }
        } catch (Exception e) {
            Log.e(TAG, "abandon focus failed", e);
        }
        heldFocus = false;
        focusRequest = null;
    }

    private void dispatchMediaKey(int keyCode) {
        if (audioManager == null) return;
        long now = SystemClock.uptimeMillis();
        try {
            audioManager.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
            audioManager.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
        } catch (Exception e) {
            Log.e(TAG, "media key " + keyCode, e);
        }
    }
}
