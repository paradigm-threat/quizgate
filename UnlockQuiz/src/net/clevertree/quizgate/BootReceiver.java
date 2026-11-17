package net.clevertree.quizgate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import net.clevertree.quizgate.core.DiagnosticsStore;

import java.util.TimeZone;

/**
 * Reschedule after reboot or clock/timezone changes.
 * AlarmManager alarms do not survive power-off; this re-arms when the device
 * comes back. {@link PackageReplacedReceiver} handles {@link Intent#ACTION_MY_PACKAGE_REPLACED}.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (action == null) return;
        boolean relevant =
                Intent.ACTION_BOOT_COMPLETED.equals(action)
                        || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                        || Intent.ACTION_TIME_CHANGED.equals(action)
                        || Intent.ACTION_TIMEZONE_CHANGED.equals(action);
        if (!relevant) return;

        AppPrefs prefs = new AppPrefs(context);
        if (!prefs.isArmed()) {
            Log.i(AlarmScheduler.TAG, "boot: not armed, skip action=" + action);
            ScheduleAuth.log(context, "boot_skip", action, 0, 0, -1);
            return;
        }
        long now = System.currentTimeMillis();
        try {
            Diag.schedule(context, new DiagnosticsStore.ScheduleEvent(
                    now, TimeZone.getDefault(), DiagnosticsStore.KIND_ARM, "boot",
                    "lock", prefs.nextAlarmAt(), prefs.getIntervalMs(), prefs.getJitterMs(),
                    null, "action=" + action));
        } catch (Throwable t) {
            Log.w(AlarmScheduler.TAG, "boot diag", t);
        }
        AlarmScheduler.armAll(context);
        ScoreWidgetProvider.refreshAll(context);
        Log.i(AlarmScheduler.TAG, "boot: rescheduled action=" + action);
    }
}
