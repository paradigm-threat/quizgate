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
 * <p>Must work whether the player is in the foreground or already in background
 * (e.g. YouTube Premium "Background play"). State is process-wide: alarm / FGS /
 * QuizActivity may each call {@link #pauseForQuiz}; a second call must not clear
 * the "was playing" latch or the unlock path would skip {@code MEDIA_PLAY}.
 *
 * <p><b>PiP / mini-player:</b> System Picture-in-Picture cannot be force-closed by
 * third-party apps. Pausing <em>before</em> bringing the quiz to the foreground
 * (see {@link #pauseThenSettle}) usually prevents YouTube auto-enter-PiP on task
 * switch. {@link OverlayGate} always reserves a modest bottom-end band for the
 * usual overlay corner; pinned-task detection is diagnostics-only.
 */
public final class MediaGate {
    private static final String TAG = AlarmScheduler.TAG;

    /** Retry offsets — background YouTube often ignores the first MEDIA_PAUSE. */
    private static final long[] PAUSE_RETRY_MS = { 0L, 80L, 200L, 450L, 900L, 1600L };
    /**
     * PLAY after restore: YouTube's activity must be frontmost; Premium BG play
     * and cold-ish task restores often ignore the first one or two keys.
     */
    private static final long[] PLAY_RETRY_MS = { 0L, 350L, 800L, 1400L, 2200L };

    /** Wait for playback to stop before quiz UI so auto-PiP on leave is less likely. */
    private static final long SETTLE_MAX_RICH_MS = 1400L;
    private static final long SETTLE_MAX_SLIM_MS = 500L;
    private static final long SETTLE_POLL_MS = 120L;
    private static final long SETTLE_FIRST_DELAY_MS = 80L;

    private static final Object LOCK = new Object();
    private static AudioManager audioManager;
    private static AudioFocusRequest focusRequest;
    private static boolean heldFocus;
    private static boolean shouldResume;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static int pauseGeneration;
    private static int playGeneration;
    private static int settleGeneration;

    public void pauseForQuiz(Context ctx) {
        Context app = ctx.getApplicationContext();
        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        AppPrefs prefs = new AppPrefs(app);
        boolean resumeLatch;
        boolean focusHeld;
        final int gen;
        synchronized (LOCK) {
            audioManager = am;
            // Latch sticky across duplicate pauses (AlarmReceiver then QuizActivity).
            if (am.isMusicActive() || prefs.wasMediaPlaying()) {
                shouldResume = true;
                prefs.setMediaWasPlaying(true);
            }
            requestExclusiveTransientFocusLocked();
            // If music is still active after focus, keep latching — Premium BG play
            // may duck briefly then keep going until MEDIA_PAUSE lands.
            if (am.isMusicActive()) {
                shouldResume = true;
                prefs.setMediaWasPlaying(true);
            }
            resumeLatch = shouldResume;
            focusHeld = heldFocus;
            pauseGeneration++;
            gen = pauseGeneration;
            // Cancel any in-flight PLAY from a prior session.
            playGeneration++;
        }
        // Immediate pause before the activity finishes drawing — helps collapse
        // YouTube's minimized/PiP audio path as early as AlarmReceiver/FGS.
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE);
        // Burst MEDIA_PAUSE — foreground YT usually stops on the first; background
        // Premium playback often needs several tries after focus is held.
        schedulePauseBurst(gen, prefs);
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

    /**
     * Pause now, then invoke {@code afterSettled} once music is inactive (or after a
     * short timeout). Call this from the alarm / FGS path <em>before</em> starting
     * {@link QuizActivity} so a playing YouTube session is less likely to auto-enter
     * system PiP on the task switch.
     *
     * <p>Posts the FSI / overlay notification should happen before or at the start of
     * the settle window; only the activity bring-up needs to wait.
     */
    public void pauseThenSettle(Context ctx, Runnable afterSettled) {
        if (afterSettled == null) return;
        Context app = ctx.getApplicationContext();
        pauseForQuiz(app);
        AppPrefs prefs = new AppPrefs(app);
        final long maxWait = PerformanceMode.isSlim(prefs) ? SETTLE_MAX_SLIM_MS : SETTLE_MAX_RICH_MS;
        final int settleGen;
        synchronized (LOCK) {
            settleGeneration++;
            settleGen = settleGeneration;
        }
        final long startAt = SystemClock.uptimeMillis();
        final Runnable[] poll = new Runnable[1];
        poll[0] = () -> {
            synchronized (LOCK) {
                if (settleGen != settleGeneration) return;
            }
            AudioManager am;
            synchronized (LOCK) {
                am = audioManager;
            }
            boolean active = am != null && am.isMusicActive();
            long elapsed = SystemClock.uptimeMillis() - startAt;
            if (!active || elapsed >= maxWait) {
                String msg = "pre_pause_settle_ms=" + elapsed
                        + (active ? ":timeout_active" : ":idle");
                Log.i(TAG, "media " + msg);
                try {
                    Diag.breadcrumb(app, net.clevertree.quizgate.core.DiagBreadcrumb.builder(
                                    System.currentTimeMillis(),
                                    net.clevertree.quizgate.core.DiagnosticsStore.KIND_MEDIA)
                            .mediaFocus(heldFocusLocked())
                            .otherApp(prefs.wasMediaPlaying())
                            .message(msg)
                            .build());
                } catch (Exception ignored) {}
                try {
                    afterSettled.run();
                } catch (Exception e) {
                    Log.e(TAG, "post-settle launch", e);
                }
                return;
            }
            // Keep poking pause while we wait — Premium BG play is sticky.
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE);
            handler.postDelayed(poll[0], SETTLE_POLL_MS);
        };
        handler.postDelayed(poll[0], SETTLE_FIRST_DELAY_MS);
    }

    private static boolean heldFocusLocked() {
        synchronized (LOCK) {
            return heldFocus;
        }
    }

    private void schedulePauseBurst(int gen, AppPrefs prefs) {
        boolean rich = PerformanceMode.allowRichMotion(prefs);
        long[] delays = rich ? PAUSE_RETRY_MS : new long[] { 0L, 200L, 600L };
        for (long delay : delays) {
            handler.postDelayed(() -> {
                synchronized (LOCK) {
                    if (gen != pauseGeneration) return;
                }
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE);
                // Re-assert latch if audio is still flowing mid-burst.
                AudioManager am;
                synchronized (LOCK) {
                    am = audioManager;
                }
                if (am != null && am.isMusicActive()) {
                    synchronized (LOCK) {
                        shouldResume = true;
                    }
                    try {
                        prefs.setMediaWasPlaying(true);
                    } catch (Exception ignored) {}
                }
            }, delay);
        }
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
        // Invalidate in-flight pause retries so they don't pause right after we resume.
        final int playGen;
        synchronized (LOCK) {
            pauseGeneration++;
            playGeneration++;
            playGen = playGeneration;
        }
        // Give ForegroundApp.restore a beat to bring YouTube (or similar) forward
        // before we abandon focus + send MEDIA_PLAY.
        handler.postDelayed(() -> {
            boolean play;
            synchronized (LOCK) {
                if (playGen != playGeneration) return;
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
                    for (long delay : PLAY_RETRY_MS) {
                        handler.postDelayed(() -> {
                            synchronized (LOCK) {
                                if (playGen != playGeneration) return;
                            }
                            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY);
                        }, 400 + delay);
                    }
                    Log.i(TAG, "media resume after quiz");
                    try {
                        if (app != null) {
                            Diag.breadcrumb(app, net.clevertree.quizgate.core.DiagBreadcrumb.builder(
                                            System.currentTimeMillis(),
                                            net.clevertree.quizgate.core.DiagnosticsStore.KIND_MEDIA)
                                    .mediaFocus(false)
                                    .message("resume_play")
                                    .build());
                        }
                    } catch (Exception ignored) {}
                }
            } finally {
                // Keep sticky latch until the PLAY burst finishes so a duplicate
                // resumeAfterQuiz(onStop+onDestroy) can still see wasMediaPlaying.
                final boolean clearLatch = play;
                handler.postDelayed(() -> {
                    synchronized (LOCK) {
                        if (playGen == playGeneration) {
                            shouldResume = false;
                        }
                    }
                    if (clearLatch && app != null) {
                        new AppPrefs(app).setMediaWasPlaying(false);
                    }
                }, clearLatch ? 400L + PLAY_RETRY_MS[PLAY_RETRY_MS.length - 1] + 200L : 0L);
                if (!clearLatch && app != null) {
                    new AppPrefs(app).setMediaWasPlaying(false);
                }
            }
        }, 550);
    }

    private void requestExclusiveTransientFocusLocked() {
        if (audioManager == null) return;
        if (heldFocus && focusRequest != null) return;
        try {
            // USAGE_MEDIA: YouTube / ExoPlayer treat this as a real contender and
            // pause more reliably than ASSISTANCE_SONIFICATION (esp. background play).
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
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
            am.dispatchMediaKeyEvent(new KeyEvent(now + 1, now + 1, KeyEvent.ACTION_UP, keyCode, 0));
        } catch (Exception e) {
            Log.e(TAG, "media key " + keyCode, e);
        }
    }
}
