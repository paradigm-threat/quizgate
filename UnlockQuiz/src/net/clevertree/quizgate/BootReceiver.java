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
 * comes back — but only while interactive. If still asleep / pending, set
 * pending screen lock and do not schedule RTC wake alarms.
 * {@link PackageReplacedReceiver} handles {@link Intent#ACTION_MY_PACKAGE_REPLACED}.
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
        boolean interactive = ScreenLockGate.isInteractive(context);
        String note = "action=" + action + " interactive=" + interactive;
        if (!interactive) {
            prefs.setPendingScreenLock(true);
            AlarmScheduler.cancelAllAlarms(context);
            note += " pending_screen_lock";
        }
        try {
            Diag.schedule(context, new DiagnosticsStore.ScheduleEvent(
                    now, TimeZone.getDefault(), DiagnosticsStore.KIND_ARM, "boot",
                    "lock", prefs.nextAlarmAt(), prefs.getIntervalMs(), prefs.getJitterMs(),
                    null, note));
        } catch (Throwable t) {
            Log.w(AlarmScheduler.TAG, "boot diag", t);
        }
        if (interactive && !prefs.isPendingScreenLock()) {
            AlarmScheduler.armWhileInteractive(context);
        } else if (interactive && prefs.isPendingScreenLock()) {
            // Booted / time changed while interactive with a prior pending lock — open quiz.
            ScreenLockGate.onWake(context, "boot_" + action);
        } else {
            Log.i(AlarmScheduler.TAG, "boot: screen off — pending, no wake alarms action=" + action);
        }
        ScoreWidgetProvider.refreshAll(context);
        Log.i(AlarmScheduler.TAG, "boot: handled action=" + action);
    }
}
