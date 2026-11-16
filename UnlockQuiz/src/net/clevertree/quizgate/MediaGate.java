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
 * Soft-pause whatever is playing (YouTube, music, etc.) when the quiz appears,
 * then try to resume after unlock. Uses audio focus + media key events — no
 * Accessibility Service required. Resume is best-effort (apps may ignore play).
 */
public final class MediaGate {
    private static final String TAG = AlarmScheduler.TAG;

    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private boolean heldFocus;
    private boolean shouldResume;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public void pauseForQuiz(Context ctx) {
        audioManager = (AudioManager) ctx.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) return;
        shouldResume = audioManager.isMusicActive();
        requestTransientFocus();
        // Explicit pause helps players that duck instead of stopping on focus loss
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE);
        Log.i(TAG, "media pause for quiz wasPlaying=" + shouldResume);
    }

    public void resumeAfterQuiz() {
        handler.postDelayed(() -> {
            try {
                if (shouldResume) {
                    dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY);
                    Log.i(TAG, "media resume after quiz");
                }
            } finally {
                abandonFocus();
                shouldResume = false;
            }
        }, 450);
    }

    private void requestTransientFocus() {
        if (audioManager == null) return;
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(focusChange -> {})
                    .setAcceptsDelayedFocusGain(false)
                    .build();
            int r = audioManager.requestAudioFocus(focusRequest);
            heldFocus = (r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
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
