package net.clevertree.quizgate;

import android.app.Application;
import android.util.Log;

import net.clevertree.quizgate.core.BuildFingerprint;

/**
 * Process-wide diagnostics: uncaught exception breadcrumbs with stable logcat tag
 * {@link BuildFingerprint#DIAG_LOG_TAG} for Play Console / logcat correlation.
 * Does not clear stats on upgrade.
 */
public class QuizGateApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler prior = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                Diag.nonFatal(QuizGateApp.this, "uncaught", throwable);
                Diag.logcatSnapshot(QuizGateApp.this);
                Log.e(BuildFingerprint.DIAG_LOG_TAG, "uncaught in " + thread.getName(), throwable);
            } catch (Throwable ignored) {
            }
            if (prior != null) prior.uncaughtException(thread, throwable);
            else System.exit(2);
        });
        // Slim: skip process-start snapshot I/O; uncaught handler still snapshots.
        if (!PerformanceMode.isSlim(this)) {
            Diag.logcatSnapshot(this);
        }
    }
}
