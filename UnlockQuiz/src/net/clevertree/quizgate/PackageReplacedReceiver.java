package net.clevertree.quizgate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import net.clevertree.quizgate.core.BuildFingerprint;
import net.clevertree.quizgate.core.DiagnosticsStore;

import java.util.TimeZone;

/**
 * App update (MY_PACKAGE_REPLACED): re-arm alarms if needed. Never deletes stats
 * or diagnostics — files under {@code files/stats/} survive package replace.
 */
public class PackageReplacedReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        Log.i(BuildFingerprint.DIAG_LOG_TAG, "package replaced — preserving stats, re-arm if needed");
        AppPrefs prefs = new AppPrefs(context);
        long now = System.currentTimeMillis();
        try {
            Diag.schedule(context, new DiagnosticsStore.ScheduleEvent(
                    now, TimeZone.getDefault(), DiagnosticsStore.KIND_ARM, "package_replaced",
                    "", prefs.nextAlarmAt(), prefs.getIntervalMs(), prefs.getJitterMs(),
                    null, "upgrade_preserve_stats"));
        } catch (Throwable t) {
            Log.w(AlarmScheduler.TAG, "package replaced diag", t);
        }
        if (prefs.isArmed()) {
            // Re-arm only while interactive; otherwise pend lock (no idle wake).
            if (ScreenLockGate.isInteractive(context) && !prefs.isPendingScreenLock()) {
                AlarmScheduler.armWhileInteractive(context);
            } else {
                prefs.setPendingScreenLock(true);
                AlarmScheduler.cancelAllAlarms(context);
            }
        }
        ScoreWidgetProvider.refreshAll(context);
        try {
            Diag.logcatSnapshot(context);
        } catch (Throwable ignored) {}
        ScheduleAuth.log(context, DiagnosticsStore.KIND_PERM, "package_replaced",
                prefs.nextAlarmAt(), prefs.getIntervalMs(), prefs.getJitterMs());
        A11yArm.reconcile(context, "package_replaced");
    }
}
