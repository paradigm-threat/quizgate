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
 *
 * <p>State is process-wide: alarm / FGS / QuizActivity may each call
 * {@link #pauseForQuiz} after media is already paused; a second call must not
 * clear the "was playing" latch or the unlock path would skip {@code MEDIA_PLAY}.
 */
public final class MediaGate {
    private static final String TAG = AlarmScheduler.TAG;

    private static final Object LOCK = new Object();
    private static AudioManager audioManager;
    private static AudioFocusRequest focusRequest;
    private static boolean heldFocus;
    private static boolean shouldResume;
    private static final Handler handler = new Handler(Looper.getMainLooper());

    public void pauseForQuiz(Context ctx) {
        Context app = ctx.getApplicationContext();
        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        AppPrefs prefs = new AppPrefs(app);
        boolean resumeLatch;
        boolean focusHeld;
        synchronized (LOCK) {
            audioManager = am;
            // Latch sticky across duplicate pauses (AlarmReceiver then QuizActivity).
            if (am.isMusicActive() || prefs.wasMediaPlaying()) {
                shouldResume = true;
                prefs.setMediaWasPlaying(true);
            }
            requestExclusiveTransientFocusLocked();
            resumeLatch = shouldResume;
            focusHeld = heldFocus;
        }
        // Explicit pause helps players that only duck on focus loss (YouTube, etc.)
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE);
        // Slim: one key event is enough — skip the delayed duplicate.
        if (PerformanceMode.allowRichMotion(prefs)) {
            handler.postDelayed(() -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE), 180);
        }
        if (PerformanceMode.verboseLog(prefs)) {
            Log.i(TAG, "media pause for quiz wasPlaying=" + resumeLatch + " focus=" + focusHeld);
        }
        try {
            Diag.breadcrumb(app, net.clevertree.quizgate.core.DiagBreadcrumb.builder(
                            System.currentTimeMillis(),
                            net.clevertree.quizgate.core.DiagnosticsStore.KIND_MEDIA)
                    .mediaFocus(focusHeld)
                    .message(resumeLatch ? "was_playing" : "idle")
                    .build());
        } catch (Exception ignored) {}
    }

    public void resumeAfterQuiz() {
        resumeAfterQuiz(null);
    }

    public void resumeAfterQuiz(Context ctx) {
        final Context app = ctx != null ? ctx.getApplicationContext() : null;
        if (app != null) {
            AppPrefs prefs = new AppPrefs(app);
            synchronized (LOCK) {
                if (prefs.wasMediaPlaying()) shouldResume = true;
                if (audioManager == null) {
                    audioManager = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
                }
            }
        }
        handler.postDelayed(() -> {
            boolean play;
            synchronized (LOCK) {
                play = shouldResume;
                if (!play && app != null && new AppPrefs(app).wasMediaPlaying()) {
                    play = true;
                    shouldResume = true;
                }
            }
            try {
                // Abandon focus first so the previous player is eligible to resume
                abandonFocus();
                if (play) {
                    handler.postDelayed(() -> {
                        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY);
                        // YouTube often needs a second PLAY once its activity is frontmost.
                        handler.postDelayed(() -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY), 450);
                        Log.i(TAG, "media resume after quiz");
                    }, 250);
                }
            } finally {
                synchronized (LOCK) {
                    shouldResume = false;
                }
                if (app != null) {
                    new AppPrefs(app).setMediaWasPlaying(false);
                }
            }
        }, 350);
    }

    private void requestExclusiveTransientFocusLocked() {
        if (audioManager == null) return;
        if (heldFocus && focusRequest != null) return;
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(focusChange -> {})
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(true)
                    .build();
            int r = audioManager.requestAudioFocus(focusRequest);
            heldFocus = (r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
            if (!heldFocus) {
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
        synchronized (LOCK) {
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
    }

    private void dispatchMediaKey(int keyCode) {
        AudioManager am;
        synchronized (LOCK) {
            am = audioManager;
        }
        if (am == null) return;
        long now = SystemClock.uptimeMillis();
        try {
            am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
            am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
        } catch (Exception e) {
            Log.e(TAG, "media key " + keyCode, e);
        }
    }
}
