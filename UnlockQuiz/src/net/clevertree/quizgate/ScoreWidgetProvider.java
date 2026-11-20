package net.clevertree.quizgate;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.RemoteViews;
import android.widget.Toast;

import net.clevertree.quizgate.core.StatsStore;
import net.clevertree.quizgate.core.SystemClock;

import java.util.TimeZone;

/** Home-screen score tile — tap opens a direct play quiz. */
public class ScoreWidgetProvider extends AppWidgetProvider {
    public static final String EXTRA_WIDGET_PINNED = "widget_pinned";

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

    /** Mid-quiz refreshes honor slim mode (session-end callers use {@link #refreshAll}). */
    public static void refreshAllOnAnswer(Context context) {
        if (PerformanceMode.isSlim(context)) return;
        refreshAll(context);
    }

    /** True when at least one QuizGate score widget is hosted on a launcher. */
    public static boolean isPinned(Context context) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            ComponentName cn = new ComponentName(context, ScoreWidgetProvider.class);
            int[] ids = mgr.getAppWidgetIds(cn);
            return ids != null && ids.length > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Ask the launcher to pin the score widget (Android 8+ / Samsung One UI).
     * Returns true when a system pin prompt was shown; false when unsupported
     * (caller should show the manual Widgets-picker path).
     */
    public static boolean requestPin(Activity activity) {
        if (activity == null) return false;
        AppWidgetManager mgr = AppWidgetManager.getInstance(activity);
        if (Build.VERSION.SDK_INT < 26 || !mgr.isRequestPinAppWidgetSupported()) {
            return false;
        }
        ComponentName provider = new ComponentName(activity, ScoreWidgetProvider.class);
        Intent success = new Intent(activity, SetupActivity.class);
        success.putExtra(EXTRA_WIDGET_PINNED, true);
        success.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent successPi = PendingIntent.getActivity(activity, 9101, success,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Bundle extras = new Bundle();
        return mgr.requestPinAppWidget(provider, extras, successPi);
    }

    public static void toastManualAddPath(Context context) {
        Toast.makeText(context, R.string.setup_widget_pin_unsupported, Toast.LENGTH_LONG).show();
    }

    private static void updateOne(Context context, AppWidgetManager mgr, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_score);
        StatsStore.DayStats day;
        try {
            StatsStore store = Diag.stats(context);
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
