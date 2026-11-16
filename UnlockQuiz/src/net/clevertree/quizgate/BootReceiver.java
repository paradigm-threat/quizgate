package net.clevertree.quizgate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        AppPrefs prefs = new AppPrefs(context);
        if (!prefs.isArmed()) {
            Log.i(AlarmScheduler.TAG, "boot: not armed, skip");
            return;
        }
        AlarmScheduler.armAll(context);
        Log.i(AlarmScheduler.TAG, "boot: rescheduled");
    }
}
