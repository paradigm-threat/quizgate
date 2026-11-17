package net.clevertree.quizgate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import net.clevertree.quizgate.core.BuildFingerprint;

/**
 * Re-evaluates Auto performance mode on power-related OS broadcasts.
 * No polling — only BATTERY_LOW/OKAY and power-save changes.
 */
public class PowerStateReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        AppPrefs prefs = new AppPrefs(context);
        if (Intent.ACTION_BATTERY_LOW.equals(action)) {
            prefs.setBatteryLowFlag(true);
        } else if (Intent.ACTION_BATTERY_OKAY.equals(action)) {
            prefs.setBatteryLowFlag(false);
        } else if (PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(action)) {
            // Flag read live in sampleSignals; just trigger reevaluate.
        } else {
            return;
        }
        try {
            String eff = PerformanceMode.reevaluate(context, action);
            Log.i(BuildFingerprint.DIAG_LOG_TAG,
                    "power_state action=" + action + " effective=" + eff
                            + " pref=" + prefs.getPerformanceMode());
        } catch (Exception e) {
            Log.w(BuildFingerprint.DIAG_LOG_TAG, "power_state reevaluate failed", e);
        }
    }
}
