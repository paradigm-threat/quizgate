package net.clevertree.quizgate;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

public class QuizTriggerService extends Service {
    private static final String CHANNEL = "unlock_quiz";
    private static final int NOTIF_ID = 42;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String trigger = intent != null ? intent.getStringExtra(QuizActivity.EXTRA_TRIGGER) : "interval";
        if (trigger == null) trigger = "interval";
        ensureChannel();
        ForegroundApp.captureNow(this);
        try {
            new MediaGate().pauseForQuiz(this);
        } catch (Exception e) {
            Log.e(AlarmScheduler.TAG, "FGS media pause", e);
        }
        Intent quiz = new Intent(this, QuizActivity.class);
        quiz.putExtra(QuizActivity.EXTRA_TRIGGER, trigger);
        quiz.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        PendingIntent pi = PendingIntent.getActivity(this, 0, quiz,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        Notification n = b.setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setSmallIcon(R.drawable.ic_stat_quizgate)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setAutoCancel(true)
                .build();
        startForeground(NOTIF_ID, n);
        try {
            startActivity(quiz);
        } catch (Exception e) {
            Log.e(AlarmScheduler.TAG, "start QuizActivity failed", e);
        }
        // Stop shortly; no relaunch loop
        stopForeground(true);
        stopSelf();
        return START_NOT_STICKY;
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL,
                getString(R.string.notif_channel), NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Toddler unlock quiz triggers");
        ch.setBypassDnd(true);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(ch);
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
