package net.clevertree.quizgate;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import net.clevertree.quizgate.core.StatsStore;
import net.clevertree.quizgate.core.SystemClock;

import java.io.File;
import java.util.TimeZone;

/** Home-screen score tile — tap opens a direct play quiz. */
public class ScoreWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager mgr, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            updateOne(context, mgr, id);
        }
    }

    public static void refreshAll(Context context) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            ComponentName cn = new ComponentName(context, ScoreWidgetProvider.class);
            int[] ids = mgr.getAppWidgetIds(cn);
            if (ids == null || ids.length == 0) return;
            for (int id : ids) updateOne(context, mgr, id);
        } catch (Exception ignored) {}
    }

    private static void updateOne(Context context, AppWidgetManager mgr, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_score);
        StatsStore.DayStats day;
        try {
            StatsStore store = new StatsStore(new File(context.getFilesDir(), "stats"));
            day = store.dayStats(new SystemClock().nowMillis(), TimeZone.getDefault());
        } catch (Exception e) {
            day = new StatsStore.DayStats();
        }
        if (day.questionsAsked == 0 && day.answeredRight == 0) {
            views.setTextViewText(R.id.widget_score, context.getString(R.string.widget_score_ready));
        } else {
            views.setTextViewText(R.id.widget_score,
                    context.getString(R.string.widget_score_line, day.answeredRight, day.questionsAsked));
        }
        views.setTextViewText(R.id.widget_label, context.getString(R.string.widget_score_label));

        Intent play = new Intent(context, QuizActivity.class);
        play.putExtra(QuizActivity.EXTRA_TRIGGER, QuizActivity.TRIGGER_PLAY);
        play.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        PendingIntent pi = PendingIntent.getActivity(context, appWidgetId, play,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pi);
        mgr.updateAppWidget(appWidgetId, views);
    }
}
