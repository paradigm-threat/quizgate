package net.clevertree.quizgate;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.clevertree.quizgate.core.StatsStore;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Shared colorful day cards for StatsActivity and the play-mode stats overlay. */
final class StatsHistoryViews {
    private static final String[] MONTHS = {
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    };

    private StatsHistoryViews() {}

    static void populateDayList(Context ctx, LinearLayout list, List<StatsStore.DayEntry> days, String todayKey) {
        list.removeAllViews();
        for (StatsStore.DayEntry entry : days) {
            list.addView(buildDayCard(ctx, entry, todayKey));
        }
    }

    static View buildDayCard(Context ctx, StatsStore.DayEntry entry, String todayKey) {
        StatsStore.DayStats day = entry.stats;
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(ctx, 20), dp(ctx, 18), dp(ctx, 20), dp(ctx, 18));
        card.setBackgroundResource(cardBackground(day.level()));
        card.setElevation(dp(ctx, 3));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(ctx, 14);
        card.setLayoutParams(cardLp);

        TextView dayLabel = new TextView(ctx);
        dayLabel.setText(formatDayLabel(ctx, entry.dayKey, todayKey));
        dayLabel.setTextColor(color(ctx, R.color.header));
        dayLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        dayLabel.setTypeface(dayLabel.getTypeface(), android.graphics.Typeface.BOLD);
        dayLabel.setGravity(Gravity.CENTER);
        card.addView(dayLabel);

        TextView mood = new TextView(ctx);
        mood.setText(moodLabel(day.level()));
        mood.setTextColor(moodColor(ctx, day.level()));
        mood.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        mood.setTypeface(mood.getTypeface(), android.graphics.Typeface.BOLD);
        mood.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams moodLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        moodLp.topMargin = dp(ctx, 4);
        card.addView(mood, moodLp);

        LinearLayout metrics = new LinearLayout(ctx);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams metricsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        metricsLp.topMargin = dp(ctx, 12);
        card.addView(metrics, metricsLp);

        metrics.addView(metricColumn(ctx,
                String.valueOf(day.questionsAsked),
                ctx.getString(R.string.stats_asked_label),
                color(ctx, R.color.header)));
        metrics.addView(metricColumn(ctx,
                String.valueOf(day.answeredRight),
                ctx.getString(R.string.stats_right_label),
                rightColor(ctx, day.level())));
        metrics.addView(metricColumn(ctx,
                String.valueOf(day.answeredWrong),
                ctx.getString(R.string.stats_wrong_label),
                color(ctx, R.color.stats_mood_trying)));
        metrics.addView(metricColumn(ctx,
                String.valueOf(day.skipped),
                ctx.getString(R.string.stats_skipped_label),
                color(ctx, R.color.header)));
        return card;
    }

    private static LinearLayout metricColumn(Context ctx, String value, String label, int valueColor) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView num = new TextView(ctx);
        num.setText(value);
        num.setTextColor(valueColor);
        num.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        num.setTypeface(num.getTypeface(), android.graphics.Typeface.BOLD);
        num.setGravity(Gravity.CENTER);
        col.addView(num);

        TextView caption = new TextView(ctx);
        caption.setText(label);
        caption.setTextColor(color(ctx, R.color.header));
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        caption.setGravity(Gravity.CENTER);
        col.addView(caption);
        return col;
    }

    private static int cardBackground(StatsStore.Level level) {
        switch (level) {
            case GREAT: return R.drawable.stats_card_great;
            case GOOD: return R.drawable.stats_card_good;
            case KEEP_TRYING: return R.drawable.stats_card_trying;
            case READY:
            default: return R.drawable.stats_card_ready;
        }
    }

    private static int moodLabel(StatsStore.Level level) {
        switch (level) {
            case GREAT: return R.string.stats_mood_great;
            case GOOD: return R.string.stats_mood_good;
            case KEEP_TRYING: return R.string.stats_mood_trying;
            case READY:
            default: return R.string.stats_mood_ready;
        }
    }

    private static int moodColor(Context ctx, StatsStore.Level level) {
        switch (level) {
            case GREAT: return color(ctx, R.color.stats_mood_great);
            case GOOD: return color(ctx, R.color.stats_mood_good);
            case KEEP_TRYING: return color(ctx, R.color.stats_mood_trying);
            case READY:
            default: return color(ctx, R.color.stats_mood_ready);
        }
    }

    private static int rightColor(Context ctx, StatsStore.Level level) {
        switch (level) {
            case GREAT: return color(ctx, R.color.stats_mood_great);
            case GOOD: return color(ctx, R.color.stats_mood_good);
            case KEEP_TRYING: return color(ctx, R.color.stats_mood_trying);
            case READY:
            default: return color(ctx, R.color.stats_mood_great);
        }
    }

    static String formatDayLabel(Context ctx, String dayKey, String todayKey) {
        if (dayKey.equals(todayKey)) return ctx.getString(R.string.stats_day_today);
        String[] parts = dayKey.split("-");
        if (parts.length != 3) return dayKey;
        try {
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);
            if (month < 1 || month > 12) return dayKey;
            Calendar now = Calendar.getInstance();
            if (year == now.get(Calendar.YEAR)) {
                return String.format(Locale.US, "%s %d", MONTHS[month - 1], day);
            }
            return String.format(Locale.US, "%s %d, %d", MONTHS[month - 1], day, year);
        } catch (NumberFormatException e) {
            return dayKey;
        }
    }

    private static int color(Context ctx, int resId) {
        return ctx.getResources().getColor(resId, ctx.getTheme());
    }

    private static int dp(Context ctx, int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
