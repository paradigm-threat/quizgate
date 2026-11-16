package net.clevertree.quizgate;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.clevertree.quizgate.core.StatsStore;
import net.clevertree.quizgate.core.SystemClock;

import java.io.File;
import java.util.List;
import java.util.TimeZone;

/** Historic daily stats — also available as an overlay from play-mode. */
public class StatsActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);
        TextView empty = findViewById(R.id.stats_empty);
        LinearLayout list = findViewById(R.id.stats_day_list);
        findViewById(R.id.stats_back).setOnClickListener(v -> returnToQuiz());

        StatsStore store = new StatsStore(new File(getFilesDir(), "stats"));
        List<StatsStore.DayEntry> days;
        try {
            days = store.allDayStats(TimeZone.getDefault());
        } catch (Exception e) {
            days = java.util.Collections.emptyList();
        }

        if (days.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            return;
        }
        empty.setVisibility(View.GONE);
        String todayKey = StatsStore.dayKey(new SystemClock().nowMillis(), TimeZone.getDefault());
        StatsHistoryViews.populateDayList(this, list, days, todayKey);
    }

    @Override public void onBackPressed() {
        returnToQuiz();
    }

    /**
     * QuizActivity is singleInstance with empty affinity, so a plain finish() can
     * reveal Setup (and its permission dialogs) instead of the play success chrome.
     * Explicitly reorder Quiz to the front, then leave.
     */
    private void returnToQuiz() {
        Intent i = new Intent(this, QuizActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        i.putExtra(QuizActivity.EXTRA_RESUME_ONLY, true);
        startActivity(i);
        finish();
    }
}
