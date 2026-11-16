package net.clevertree.quizgate;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.clevertree.quizgate.core.Catalog;
import net.clevertree.quizgate.core.DiagBreadcrumb;
import net.clevertree.quizgate.core.DiagnosticsStore;
import net.clevertree.quizgate.core.CurriculumPack;
import net.clevertree.quizgate.core.DigitAnswer;
import net.clevertree.quizgate.core.Question;
import net.clevertree.quizgate.core.QuestionPicker;
import net.clevertree.quizgate.core.QuizSession;
import net.clevertree.quizgate.core.StatsEvent;
import net.clevertree.quizgate.core.StatsStore;
import net.clevertree.quizgate.core.SystemClock;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;
import java.util.UUID;

public class QuizActivity extends Activity {
    public static final String EXTRA_TRIGGER = "trigger";
    /** Bring existing quiz to front without starting a new round (stats Back). */
    public static final String EXTRA_RESUME_ONLY = "resume_only";
    /** Kid opened QuizGate from the launcher — practice mode, not a soft lock. */
    public static final String TRIGGER_PLAY = "play";
    private static final int ERROR_COUNTDOWN_SEC = 3;
    private static final int FAIL_COUNTDOWN_SEC = 6;
    private static final long SUCCESS_HOLD_MS = 2000;
    private static final long FADE_MS = 450;
    private static final long PHOTO_FLASH_MS = 280;
    private static final float BALLOON_INFLATED = 1.10f;
    private static final float BALLOON_SUCCESS_INFLATED = 1.16f;
    private static final long BALLOON_INFLATE_MS = 720;
    private static final long BALLOON_RELAX_MS = 880;
    private static final long BALLOON_PAUSE_MS = 280;
    private static final int[] CONFETTI_COLORS = {
            0xFFE74C3C, 0xFFF1C40F, 0xFF2ECC71, 0xFF3498DB,
            0xFFE87A2E, 0xFF9B59B6, 0xFFFF8FB1, 0xFF1ABC9C
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SystemClock clock = new SystemClock();
    private final Random random = new Random();
    private final MediaGate mediaGate = new MediaGate();
    /** When false (Home leave / play mode), do not yank the child back into the previous app. */
    private boolean restorePreviousApp = true;
    private boolean playMode;
    /** True while historic stats is open — skip Home soft-bypass on leave. */
    private boolean browsingStats;

    private QuizSession session;
    private CurriculumPack activePack;
    private StatsStore stats;
    private Catalog catalog;
    private QuestionPicker picker;
    private boolean finishing;
    private boolean inTransition;
    private boolean mediaResumePending;
    /** Wall clock when current question was shown (for latency_ms). */
    private long questionShownAt;

    private TextView spoken;
    private TextView symbolic;
    private TextView digitAnswerView;
    private View questionBalloon;
    private View balloonTarget;
    private float balloonInflateTo = BALLOON_INFLATED;
    private int balloonCyclesLeft;
    private LinearLayout choices;
    private LinearLayout digitPad;
    private View quizPanel;
    private View quizStatsBar;
    private TextView quizStatsMood;
    private TextView quizStatsAsked;
    private TextView quizStatsRight;
    private boolean balloonRunning;
    private Runnable successEndTick;
    private View transitionOverlay;
    private View transitionScrim;
    private View transitionMessage;
    private FrameLayout confettiLayer;
    private ImageView quizBg;
    private ImageView photoOverlay;
    private ImageView photoOverlayB;
    private TextView transitionTitle;
    private TextView transitionRetryLabel;
    private TextView transitionCountdown;
    private ImageView transitionGateIcon;
    private TextView transitionPlayPrompt;
    private View transitionPlayActions;
    private FrameLayout statsOverlay;
    private AppPrefs prefs;
    private DigitAnswer digitAnswer = new DigitAnswer();
    private String symbolicTemplate = "";
    private Runnable countdownTick;
    private Runnable pulseAnim;
    private Runnable photoFlashTick;
    private boolean photoFlashUseB;
    private List<Uri> devicePhotos = new ArrayList<>();
    private List<Uri> successPhotos = new ArrayList<>();
    private int successPhotoIndex;
    /** False until the current question gets a correct/wrong tap. */
    private boolean currentAnswered;

    private final MapColors colors = new MapColors();
    private TextView quizStatsWrong;
    private TextView quizStatsSkipped;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        setContentView(R.layout.activity_quiz);
        prefs = new AppPrefs(this);
        // Capture previous app if alarm path missed it (e.g. Test quiz / ADB)
        if (prefs.getResumePackage() == null) {
            ForegroundApp.captureNow(this);
        }
        // Pause YouTube / music / game audio while the quiz is up
        mediaGate.pauseForQuiz(this);
        spoken = findViewById(R.id.spoken);
        symbolic = findViewById(R.id.symbolic);
        digitAnswerView = findViewById(R.id.digit_answer);
        questionBalloon = findViewById(R.id.question_balloon);
        choices = findViewById(R.id.choices);
        digitPad = findViewById(R.id.digit_pad);
        quizPanel = findViewById(R.id.quiz_panel);
        quizStatsBar = findViewById(R.id.quiz_stats_bar);
        quizStatsMood = findViewById(R.id.quiz_stats_mood);
        quizStatsAsked = findViewById(R.id.quiz_stats_asked);
        quizStatsRight = findViewById(R.id.quiz_stats_right);
        quizStatsWrong = findViewById(R.id.quiz_stats_wrong);
        quizStatsSkipped = findViewById(R.id.quiz_stats_skipped);
        quizBg = findViewById(R.id.quiz_bg);
        transitionOverlay = findViewById(R.id.transition_overlay);
        transitionScrim = findViewById(R.id.transition_scrim);
        transitionMessage = findViewById(R.id.transition_message);
        confettiLayer = findViewById(R.id.confetti_layer);
        photoOverlay = findViewById(R.id.photo_overlay);
        photoOverlayB = findViewById(R.id.photo_overlay_b);
        devicePhotos = PhotoLibrary.listForQuiz(this, true);
        applyQuizBackground();
        transitionTitle = findViewById(R.id.transition_title);
        transitionRetryLabel = findViewById(R.id.transition_retry_label);
        transitionCountdown = findViewById(R.id.transition_countdown);
        transitionGateIcon = findViewById(R.id.transition_gate_icon);
        transitionPlayPrompt = findViewById(R.id.transition_play_prompt);
        transitionPlayActions = findViewById(R.id.transition_play_actions);
        findViewById(R.id.btn_ask_another).setOnClickListener(v -> askAnotherQuestion());
        findViewById(R.id.btn_quit_play).setOnClickListener(v -> quitPlay());
        findViewById(R.id.btn_play_parents).setOnClickListener(v -> openParentsSetup());
        findViewById(R.id.btn_view_stats).setOnClickListener(v -> openStatsHistory());
        statsOverlay = findViewById(R.id.stats_overlay);

        String trigger = getIntent() != null ? getIntent().getStringExtra(EXTRA_TRIGGER) : "interval";
        if (trigger == null) trigger = "interval";
        QuizLaunch.cancelNotification(this);
        // Resume-only intents (legacy StatsActivity Back) must not start a new round
        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_RESUME_ONLY, false)) {
            playMode = true;
            restorePreviousApp = false;
            return;
        }
        if (!TRIGGER_PLAY.equals(trigger)) {
            Diag.schedule(this, new DiagnosticsStore.ScheduleEvent(
                    clock.nowMillis(), TimeZone.getDefault(), DiagnosticsStore.KIND_LAUNCH_OK,
                    trigger, "lock", prefs.nextAlarmAt(), prefs.getIntervalMs(), prefs.getJitterMs(),
                    prefs.getResumePackage() != null, "path=activity_visible"));
        }
        playMode = TRIGGER_PLAY.equals(trigger);
        if (playMode) {
            restorePreviousApp = false;
        }
        if (getIntent() != null && getIntent().hasExtra("interval_ms")) {
            long ms = getIntent().getLongExtra("interval_ms", 0);
            if (ms > 0) prefs.setIntervalMs(ms);
        }
        if (getIntent() != null && getIntent().hasExtra("jitter_ms")) {
            long ms = getIntent().getLongExtra("jitter_ms", -1);
            if (ms >= 0) prefs.setJitterMs(ms);
        }
        if (getIntent() != null && getIntent().hasExtra("math_entry")) {
            prefs.setMathEntryMode(getIntent().getStringExtra("math_entry"));
        }

        stats = Diag.stats(this);
        activePack = CurriculumStore.getActive(this);
        catalog = CatalogLoader.get(this);
        picker = new QuestionPicker(catalog, random);
        if (getIntent() != null && getIntent().hasExtra("force_qid")) {
            picker.preferFirst(getIntent().getStringExtra("force_qid"));
        }
        startRound(trigger);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent == null) return;
        // StatsActivity Back — restore this instance without restarting a round
        if (intent.getBooleanExtra(EXTRA_RESUME_ONLY, false)) {
            browsingStats = false;
            return;
        }
        String trigger = intent.getStringExtra(EXTRA_TRIGGER);
        if (trigger == null) trigger = TRIGGER_PLAY;
        playMode = TRIGGER_PLAY.equals(trigger);
        if (playMode) restorePreviousApp = false;
        if (finishing || inTransition) return;
        if (session != null && !session.isEnded()) {
            // Soft-replace open play request while mid-question — ignore
            if (!playMode) return;
        }
        if (TRIGGER_PLAY.equals(trigger) && (session == null || session.isEnded())) {
            startRound(TRIGGER_PLAY);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (browsingStats) browsingStats = false;
    }

    private void startRound(String trigger) {
        // ADB / e2e forces single-question rounds for deterministic smoke tests
        int roundSize = (trigger != null && trigger.startsWith("adb_")) ? 1 : activePack.roundSize;
        // Practice from the launcher: one question at a time so success can offer "another"
        if (TRIGGER_PLAY.equals(trigger)) roundSize = 1;
        session = new QuizSession(UUID.randomUUID().toString(), trigger, clock.nowMillis(), picker,
                roundSize, activePack.maxWrong, activePack.consolingUnlock);
        finishing = false;
        inTransition = false;
        currentAnswered = false;
        String sessionType = playMode ? "play" : "lock";
        Diag.schedule(this, new DiagnosticsStore.ScheduleEvent(
                clock.nowMillis(), TimeZone.getDefault(), DiagnosticsStore.KIND_QUIZ_STARTED,
                trigger, sessionType, prefs.nextAlarmAt(), prefs.getIntervalMs(), prefs.getJitterMs(),
                prefs.getResumePackage() != null, ""));
        logEvent("shown", session.getCurrent().id, "", 0);
        bindQuestion(session.getCurrent());
    }

    @Override protected void onStop() {
        super.onStop();
        // After unlock, activity is finishing — resume media once we're in background
        if (mediaResumePending || finishing) {
            mediaResumePending = false;
            mediaGate.resumeAfterQuiz();
        }
    }

    @Override protected void onDestroy() {
        stopBalloonPulse();
        if (mediaResumePending) {
            mediaResumePending = false;
            mediaGate.resumeAfterQuiz();
        }
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        // Navigating to historic stats is not a Home leave
        if (browsingStats) return;
        // Home / Recents: soft bypass — stay where the child went (usually Home)
        if (!finishing && !inTransition && session != null && !session.isEnded()) {
            restorePreviousApp = false;
            finishWithBypass();
        }
    }

    @Override public void onBackPressed() {
        if (statsOverlay != null && statsOverlay.getVisibility() == View.VISIBLE) {
            hideStatsOverlay();
            return;
        }
        if (!finishing && !inTransition && session != null && !session.isEnded()) {
            // Play mode: just leave. Lock mode: soft bypass + restore previous app.
            restorePreviousApp = !playMode;
            finishWithBypass();
        } else if (!inTransition) {
            super.onBackPressed();
        }
    }

    private void bindQuestion(Question q) {
        spoken.setText(q.spoken);
        digitAnswer = new DigitAnswer();
        boolean digitMode = "math".equals(q.type) && prefs.useDigitPadForMath();
        if (q.symbolic != null && !q.symbolic.isEmpty()) {
            symbolicTemplate = q.symbolic;
            symbolic.setVisibility(View.VISIBLE);
            refreshSymbolic();
        } else {
            symbolicTemplate = "";
            symbolic.setVisibility(View.GONE);
        }
        choices.removeAllViews();
        digitPad.removeAllViews();
        if (digitMode) {
            choices.setVisibility(View.GONE);
            digitPad.setVisibility(View.VISIBLE);
            digitAnswerView.setVisibility(View.VISIBLE);
            refreshDigitAnswer();
            buildDigitPad();
        } else {
            digitPad.setVisibility(View.GONE);
            digitAnswerView.setVisibility(View.GONE);
            choices.setVisibility(View.VISIBLE);
            buildMultipleChoice(q);
        }
        quizPanel.setVisibility(View.VISIBLE);
        quizPanel.setAlpha(1f);
        hideTransitionOverlayImmediate();
        currentAnswered = false;
        refreshQuizDayStats();
        startBalloonPulse(questionBalloon, BALLOON_INFLATED, 3);
    }

    private void refreshQuizDayStats() {
        if (quizStatsBar == null || stats == null) return;
        StatsStore.DayStats day;
        try {
            day = stats.dayStats(clock.nowMillis(), TimeZone.getDefault());
        } catch (Exception e) {
            day = new StatsStore.DayStats();
        }
        if (quizStatsAsked != null) {
            quizStatsAsked.setText(day.questionsAsked + " asked");
        }
        quizStatsRight.setText(day.answeredRight + " right");
        if (quizStatsWrong != null) {
            quizStatsWrong.setText(day.answeredWrong + " wrong");
        }
        if (quizStatsSkipped != null) {
            quizStatsSkipped.setText(day.skipped + " skipped");
        }
        switch (day.level()) {
            case GREAT:
                quizStatsBar.setBackgroundResource(R.drawable.stats_card_great);
                quizStatsMood.setText(R.string.stats_mood_great);
                quizStatsMood.setTextColor(color(R.color.stats_mood_great));
                quizStatsRight.setTextColor(color(R.color.stats_mood_great));
                break;
            case GOOD:
                quizStatsBar.setBackgroundResource(R.drawable.stats_card_good);
                quizStatsMood.setText(R.string.stats_mood_good);
                quizStatsMood.setTextColor(color(R.color.stats_mood_good));
                quizStatsRight.setTextColor(color(R.color.stats_mood_good));
                break;
            case KEEP_TRYING:
                quizStatsBar.setBackgroundResource(R.drawable.stats_card_trying);
                quizStatsMood.setText(R.string.stats_mood_trying);
                quizStatsMood.setTextColor(color(R.color.stats_mood_trying));
                quizStatsRight.setTextColor(color(R.color.stats_mood_trying));
                break;
            case READY:
            default:
                quizStatsBar.setBackgroundResource(R.drawable.stats_card_ready);
                quizStatsMood.setText(R.string.stats_mood_ready);
                quizStatsMood.setTextColor(color(R.color.stats_mood_ready));
                quizStatsRight.setTextColor(color(R.color.stats_mood_great));
                break;
        }
    }

    private void buildMultipleChoice(Question q) {
        List<Integer> order = new ArrayList<>();
        order.add(0); order.add(1); order.add(2);
        Collections.shuffle(order, random);
        for (int idx : order) {
            final String choice = q.choices.get(idx);
            Button btn = styleChoiceButton(q.displayLabel(idx), colors.forChoice(q.type, choice));
            btn.setOnClickListener(v -> onChoice(choice));
            choices.addView(btn);
        }
    }

    private void buildDigitPad() {
        // rows: 1 2 3 / 4 5 6 / 7 8 9 / clear 0 enter
        int[][] rows = {
                {1, 2, 3},
                {4, 5, 6},
                {7, 8, 9},
                {-1, 0, -2} // -1 clear, -2 enter
        };
        for (int[] row : rows) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            line.setLayoutParams(lineLp);
            for (int key : row) {
                Button btn = new Button(this);
                btn.setAllCaps(false);
                btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
                btn.setTextColor(Color.parseColor("#1A3A4A"));
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(dp(16));
                bg.setStroke(dp(3), Color.parseColor("#1A3A4A"));
                bg.setColor(Color.WHITE);
                btn.setBackground(bg);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(68), 1f);
                lp.setMargins(dp(6), dp(6), dp(6), dp(6));
                btn.setLayoutParams(lp);
                if (key >= 0) {
                    btn.setText(String.valueOf(key));
                    final int d = key;
                    btn.setOnClickListener(v -> {
                        digitAnswer.press(d);
                        refreshDigitAnswer();
                        refreshSymbolic();
                    });
                } else if (key == -1) {
                    btn.setText("Clear");
                    btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                    btn.setOnClickListener(v -> {
                        digitAnswer.clear();
                        refreshDigitAnswer();
                        refreshSymbolic();
                    });
                } else {
                    btn.setText("OK");
                    bg.setColor(Color.parseColor("#E87A2E"));
                    btn.setTextColor(Color.WHITE);
                    btn.setOnClickListener(v -> submitDigitAnswer());
                }
                line.addView(btn);
            }
            digitPad.addView(line);
        }
    }

    private Button styleChoiceButton(String label, Integer chipColor) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        btn.setTextColor(Color.parseColor("#1A3A4A"));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(3), Color.parseColor("#1A3A4A"));
        bg.setColor(chipColor != null ? chipColor : Color.WHITE);
        btn.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(72));
        lp.setMargins(dp(8), dp(10), dp(8), dp(10));
        btn.setLayoutParams(lp);
        btn.setGravity(Gravity.CENTER);
        return btn;
    }

    private void refreshDigitAnswer() {
        String v = digitAnswer.value();
        digitAnswerView.setText(v.isEmpty() ? "?" : v);
    }

    private void refreshSymbolic() {
        if (symbolicTemplate == null || symbolicTemplate.isEmpty()) return;
        String fill = digitAnswer.isEmpty() ? "_" : digitAnswer.value();
        symbolic.setText(symbolicTemplate.replace("_", fill));
    }

    private void submitDigitAnswer() {
        if (digitAnswer.isEmpty()) return;
        String v = digitAnswer.value();
        Question q = session.getCurrent();
        // Normalize to catalog correct string form
        String choice = v;
        try {
            choice = String.valueOf(Integer.parseInt(v));
        } catch (NumberFormatException ignored) {}
        onChoice(choice);
    }

    private void onChoice(String choice) {
        if (session.isEnded() || finishing || inTransition) return;
        Question q = session.getCurrent();
        QuizSession.AnswerResult result = session.answer(choice, clock.nowMillis());
        currentAnswered = true;
        if (result == QuizSession.AnswerResult.CORRECT_UNLOCK
                || result == QuizSession.AnswerResult.CORRECT_ROUND_DONE) {
            logEvent("correct", q.id, choice, session.getMissStreak());
            int earn = activePack == null ? 0
                    : session.getCorrectInRound() * activePack.earnMinutesPerCorrect;
            if (earn > 0) prefs.addMinutes(earn);
            showSuccessThenFinish();
            return;
        }
        if (result == QuizSession.AnswerResult.CORRECT_CONTINUE) {
            logEvent("correct", q.id, choice, session.getMissStreak());
            inTransition = true;
            handler.postDelayed(() -> {
                inTransition = false;
                currentAnswered = false;
                bindQuestion(session.getCurrent());
                logEvent("shown", session.getCurrent().id, "", session.getMissStreak());
            }, 350);
            return;
        }
        logEvent("wrong", q.id, choice, session.getMissStreak());
        if (result == QuizSession.AnswerResult.WRONG_CONSOLING) {
            showFailTransitionThenFinish();
            return;
        }
        showErrorTransitionThen(session.getCurrent());
    }

    private void showErrorTransitionThen(Question next) {
        inTransition = true;
        stopBalloonPulse();
        applyErrorStyle();
        transitionTitle.setText(R.string.wrong_title);
        preparePhotoBackground(false);
        fadeInTransition(() -> startCountdown(ERROR_COUNTDOWN_SEC, R.string.retry_in, false, () ->
                fadeOutTransition(() -> {
                    inTransition = false;
                    bindQuestion(next);
                    logEvent("shown", next.id, "", session.getMissStreak());
                })));
    }

    private void showFailTransitionThenFinish() {
        inTransition = true;
        stopBalloonPulse();
        logEvent("consoling", "", "", session.getMissStreak());
        applyErrorStyle();
        transitionTitle.setText(R.string.fail_title);
        preparePhotoBackground(false);
        fadeInTransition(() -> {
            if (playMode) {
                showPlayChoiceChrome(R.string.play_fail_prompt);
                return;
            }
            startFailPulse();
            startCountdown(FAIL_COUNTDOWN_SEC, R.string.retry_in, true, () ->
                    fadeOutTransition(this::finishSession));
        });
    }

    private void showSuccessThenFinish() {
        inTransition = true;
        stopBalloonPulse();
        applySuccessStyle();
        transitionTitle.setText(R.string.success_title);
        preparePhotoBackground(true);
        fadeInTransition(() -> {
            startPhotoFlash();
            burstConfetti();
            startBalloonPulse(transitionMessage, BALLOON_SUCCESS_INFLATED, 2);
            if (playMode) {
                showPlayChoiceChrome(R.string.play_success_prompt);
                // Finite party, then idle so taps/dumps aren't blocked by looping anims
                if (successEndTick != null) handler.removeCallbacks(successEndTick);
                successEndTick = () -> {
                    successEndTick = null;
                    stopPhotoFlash();
                    stopBalloonPulse();
                };
                handler.postDelayed(successEndTick, SUCCESS_HOLD_MS);
                return;
            }
            if (successEndTick != null) handler.removeCallbacks(successEndTick);
            successEndTick = () -> {
                successEndTick = null;
                stopPhotoFlash();
                stopBalloonPulse();
                fadeOutTransition(this::finishSession);
            };
            handler.postDelayed(successEndTick, SUCCESS_HOLD_MS);
        });
    }

    private void showPlayChoiceChrome(int promptRes) {
        if (transitionGateIcon != null) transitionGateIcon.setVisibility(View.VISIBLE);
        if (transitionPlayPrompt != null) {
            transitionPlayPrompt.setText(promptRes);
            transitionPlayPrompt.setVisibility(View.VISIBLE);
        }
        if (transitionPlayActions != null) transitionPlayActions.setVisibility(View.VISIBLE);
        transitionRetryLabel.setVisibility(View.GONE);
        transitionCountdown.setVisibility(View.GONE);
        transitionTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
    }

    private void hidePlayChoiceChrome() {
        if (transitionGateIcon != null) transitionGateIcon.setVisibility(View.GONE);
        if (transitionPlayPrompt != null) transitionPlayPrompt.setVisibility(View.GONE);
        if (transitionPlayActions != null) transitionPlayActions.setVisibility(View.GONE);
    }

    private void askAnotherQuestion() {
        if (!playMode || finishing) return;
        stopPhotoFlash();
        stopBalloonPulse();
        stopFailPulse();
        if (successEndTick != null) {
            handler.removeCallbacks(successEndTick);
            successEndTick = null;
        }
        try {
            if (session != null && session.isEnded()) {
                stats.appendSession(session);
            } else if (session != null && !session.isEnded()) {
                // Shouldn't happen on success path, but be safe
                session.bypass(clock.nowMillis());
                stats.appendSession(session);
            }
        } catch (Exception e) {
            Log.e(AlarmScheduler.TAG, "ask another persist", e);
        }
        ScoreWidgetProvider.refreshAll(this);
        hidePlayChoiceChrome();
        fadeOutTransition(() -> {
            inTransition = false;
            startRound(TRIGGER_PLAY);
        });
    }

    private void quitPlay() {
        if (!playMode) return;
        restorePreviousApp = false;
        stopPhotoFlash();
        stopBalloonPulse();
        fadeOutTransition(this::finishSession);
    }

    private void openParentsSetup() {
        Intent i = new Intent(this, SetupActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
        restorePreviousApp = false;
        if (session != null && !session.isEnded()) {
            finishWithBypass();
        } else {
            finishSession();
        }
    }

    private void openStatsHistory() {
        if (!playMode) return;
        showStatsOverlay();
    }

    private void showStatsOverlay() {
        if (statsOverlay == null) return;
        browsingStats = true;
        statsOverlay.removeAllViews();
        View page = getLayoutInflater().inflate(R.layout.activity_stats, statsOverlay, false);
        statsOverlay.addView(page);
        page.findViewById(R.id.stats_back).setOnClickListener(v -> hideStatsOverlay());
        TextView empty = page.findViewById(R.id.stats_empty);
        LinearLayout list = page.findViewById(R.id.stats_day_list);
        List<StatsStore.DayEntry> days;
        try {
            days = stats.allDayStats(TimeZone.getDefault());
        } catch (Exception e) {
            days = Collections.emptyList();
        }
        if (days.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
        } else {
            empty.setVisibility(View.GONE);
            String todayKey = StatsStore.dayKey(clock.nowMillis(), TimeZone.getDefault());
            StatsHistoryViews.populateDayList(this, list, days, todayKey);
        }
        statsOverlay.setVisibility(View.VISIBLE);
    }

    private void hideStatsOverlay() {
        if (statsOverlay == null) return;
        statsOverlay.setVisibility(View.GONE);
        statsOverlay.removeAllViews();
        browsingStats = false;
    }

    private void startBalloonPulse(View target, float inflateTo, int cycles) {
        if (target == null || cycles <= 0) return;
        stopBalloonPulse();
        balloonTarget = target;
        balloonInflateTo = inflateTo;
        balloonCyclesLeft = cycles;
        balloonRunning = true;
        balloonTarget.setScaleX(1f);
        balloonTarget.setScaleY(1f);
        balloonTarget.post(() -> {
            if (!balloonRunning || finishing || balloonTarget == null) return;
            balloonTarget.setPivotX(balloonTarget.getWidth() / 2f);
            balloonTarget.setPivotY(balloonTarget.getHeight() / 2f);
            runBalloonInflate();
        });
    }

    private void runBalloonInflate() {
        if (!balloonRunning || finishing || balloonTarget == null || balloonCyclesLeft <= 0) {
            stopBalloonPulse();
            return;
        }
        balloonCyclesLeft--;
        balloonTarget.animate().cancel();
        // Pump up — ease out like air filling a balloon
        balloonTarget.animate()
                .scaleX(balloonInflateTo)
                .scaleY(balloonInflateTo)
                .setDuration(BALLOON_INFLATE_MS)
                .setInterpolator(new DecelerateInterpolator(1.4f))
                .withEndAction(() -> {
                    if (!balloonRunning || finishing || balloonTarget == null) return;
                    // Soft settle / exhale
                    balloonTarget.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(BALLOON_RELAX_MS)
                            .setInterpolator(new AccelerateDecelerateInterpolator())
                            .withEndAction(() -> {
                                if (!balloonRunning || finishing || balloonTarget == null) return;
                                if (balloonCyclesLeft > 0) {
                                    handler.postDelayed(this::runBalloonInflate, BALLOON_PAUSE_MS);
                                } else {
                                    stopBalloonPulse();
                                }
                            })
                            .start();
                })
                .start();
    }

    private void stopBalloonPulse() {
        balloonRunning = false;
        if (balloonTarget != null) {
            balloonTarget.animate().cancel();
            balloonTarget.setScaleX(1f);
            balloonTarget.setScaleY(1f);
        }
        if (questionBalloon != null && questionBalloon != balloonTarget) {
            questionBalloon.animate().cancel();
            questionBalloon.setScaleX(1f);
            questionBalloon.setScaleY(1f);
        }
        if (transitionMessage != null && transitionMessage != balloonTarget) {
            transitionMessage.animate().cancel();
            transitionMessage.setScaleX(1f);
            transitionMessage.setScaleY(1f);
        }
        balloonTarget = null;
    }

    private void applyErrorStyle() {
        hidePlayChoiceChrome();
        transitionMessage.setBackgroundResource(R.drawable.transition_text_plate);
        transitionScrim.setBackgroundColor(Color.parseColor("#B3000000"));
        transitionTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        transitionTitle.setTextColor(color(R.color.transition_title));
        transitionRetryLabel.setVisibility(View.VISIBLE);
        transitionCountdown.setVisibility(View.VISIBLE);
        transitionRetryLabel.setTextColor(color(R.color.transition_body));
        transitionCountdown.setTextColor(color(R.color.transition_countdown));
        confettiLayer.setVisibility(View.GONE);
        confettiLayer.removeAllViews();
    }

    private void applySuccessStyle() {
        hidePlayChoiceChrome();
        transitionMessage.setBackgroundResource(R.drawable.success_text_plate);
        transitionScrim.setBackgroundColor(color(R.color.success_scrim));
        transitionTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 56);
        transitionTitle.setTextColor(color(R.color.success_title));
        transitionRetryLabel.setVisibility(View.GONE);
        transitionCountdown.setVisibility(View.GONE);
        confettiLayer.setVisibility(View.VISIBLE);
        confettiLayer.removeAllViews();
    }

    private int color(int resId) {
        return getResources().getColor(resId, getTheme());
    }

    private void applyQuizBackground() {
        Uri photo = pickPhoto(true);
        Bitmap bmp = photo != null ? PhotoLibrary.decodeSampled(this, photo, 1600, 1000) : null;
        if (bmp != null) {
            quizBg.setImageBitmap(bmp);
            Log.i(AlarmScheduler.TAG, "quiz backdrop loaded source=" + prefs.getPhotoSource());
        } else {
            quizBg.setImageDrawable(null);
            Log.w(AlarmScheduler.TAG, "quiz backdrop empty — no photos");
        }
    }

    private void preparePhotoBackground(boolean forSuccess) {
        photoOverlayB.setVisibility(View.GONE);
        photoOverlayB.setAlpha(0f);
        photoFlashUseB = false;
        Uri photo = pickPhoto(forSuccess);
        Bitmap bmp = photo != null ? PhotoLibrary.decodeSampled(this, photo, 1280, 800) : null;
        if (bmp != null) {
            photoOverlay.setImageBitmap(bmp);
            photoOverlay.setBackground(null);
            photoOverlay.setVisibility(View.VISIBLE);
            photoOverlay.setScaleX(1f);
            photoOverlay.setScaleY(1f);
            photoOverlay.setAlpha(1f);
        } else {
            photoOverlay.setImageDrawable(null);
            photoOverlay.setBackgroundColor(forSuccess
                    ? Color.parseColor("#2E9B5A")
                    : Color.parseColor("#1A3A4A"));
            photoOverlay.setVisibility(View.VISIBLE);
        }
        transitionScrim.setAlpha(1f);
        transitionMessage.setAlpha(1f);
        transitionMessage.setScaleX(1f);
        transitionMessage.setScaleY(1f);
        transitionCountdown.setScaleX(1f);
        transitionCountdown.setScaleY(1f);
        if (forSuccess) {
            successPhotos = new ArrayList<>(PhotoLibrary.listForQuiz(this, true));
            Collections.shuffle(successPhotos, random);
            successPhotoIndex = 0;
            Log.i(AlarmScheduler.TAG, "success slideshow photos=" + successPhotos.size());
        }
    }

    private void fadeInTransition(Runnable after) {
        quizPanel.animate().cancel();
        transitionOverlay.animate().cancel();
        transitionOverlay.setVisibility(View.VISIBLE);
        transitionOverlay.setAlpha(0f);
        photoOverlay.setScaleX(1f);
        photoOverlay.setScaleY(1f);
        quizPanel.animate().alpha(0.15f).setDuration(FADE_MS).start();
        // Short Ken Burns only — long animations block UIAutomator idle / make dumps flaky
        photoOverlay.setScaleX(1f);
        photoOverlay.setScaleY(1f);
        photoOverlay.animate()
                .scaleX(1.06f)
                .scaleY(1.06f)
                .setDuration(FADE_MS + 150)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
        transitionOverlay.animate()
                .alpha(1f)
                .setDuration(FADE_MS)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    if (after != null) after.run();
                })
                .start();
        // Pop the readable text plate in
        transitionMessage.setScaleX(0.85f);
        transitionMessage.setScaleY(0.85f);
        transitionMessage.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(FADE_MS)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();
    }

    private void fadeOutTransition(Runnable after) {
        stopFailPulse();
        stopPhotoFlash();
        photoOverlay.animate().cancel();
        photoOverlayB.animate().cancel();
        transitionOverlay.animate()
                .alpha(0f)
                .setDuration(FADE_MS)
                .withEndAction(() -> {
                    hideTransitionOverlayImmediate();
                    quizPanel.setAlpha(1f);
                    if (after != null) after.run();
                })
                .start();
        quizPanel.animate().alpha(1f).setDuration(FADE_MS).start();
    }

    private void hideTransitionOverlayImmediate() {
        stopFailPulse();
        stopPhotoFlash();
        if (countdownTick != null) {
            handler.removeCallbacks(countdownTick);
            countdownTick = null;
        }
        transitionOverlay.animate().cancel();
        photoOverlay.animate().cancel();
        photoOverlayB.animate().cancel();
        transitionOverlay.setVisibility(View.GONE);
        transitionOverlay.setAlpha(1f);
        photoOverlay.setScaleX(1f);
        photoOverlay.setScaleY(1f);
        photoOverlayB.setVisibility(View.GONE);
        confettiLayer.setVisibility(View.GONE);
        confettiLayer.removeAllViews();
    }

    private void startCountdown(int seconds, int labelRes, boolean celebratoryPulse, Runnable onDone) {
        if (countdownTick != null) handler.removeCallbacks(countdownTick);
        final int[] left = {seconds};
        transitionRetryLabel.setText(labelRes);
        transitionCountdown.setText(String.valueOf(left[0]));
        bumpCountdown();
        countdownTick = new Runnable() {
            @Override public void run() {
                left[0]--;
                if (left[0] <= 0) {
                    countdownTick = null;
                    if (onDone != null) onDone.run();
                    return;
                }
                transitionCountdown.setText(String.valueOf(left[0]));
                bumpCountdown();
                if (celebratoryPulse) bumpMessagePlate();
                handler.postDelayed(this, 1000);
            }
        };
        handler.postDelayed(countdownTick, 1000);
    }

    private void startPhotoFlash() {
        stopPhotoFlash();
        if (successPhotos.isEmpty()) return;
        photoFlashTick = new Runnable() {
            @Override public void run() {
                if (!inTransition || successPhotos.isEmpty()) return;
                Uri next = successPhotos.get(successPhotoIndex % successPhotos.size());
                successPhotoIndex++;
                Bitmap bmp = PhotoLibrary.decodeSampled(QuizActivity.this, next, 960, 640);
                if (bmp != null) {
                    ImageView incoming = photoFlashUseB ? photoOverlay : photoOverlayB;
                    ImageView outgoing = photoFlashUseB ? photoOverlayB : photoOverlay;
                    incoming.setImageBitmap(bmp);
                    incoming.setVisibility(View.VISIBLE);
                    incoming.setAlpha(0f);
                    incoming.setScaleX(1.04f);
                    incoming.setScaleY(1.04f);
                    incoming.animate().cancel();
                    outgoing.animate().cancel();
                    incoming.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(90).start();
                    outgoing.animate().alpha(0f).setDuration(90).start();
                    photoFlashUseB = !photoFlashUseB;
                    // Tint scrim through bright colors for a party feel
                    int c = CONFETTI_COLORS[successPhotoIndex % CONFETTI_COLORS.length];
                    int scrim = (0x99 << 24) | (c & 0x00FFFFFF);
                    transitionScrim.setBackgroundColor(scrim);
                }
                handler.postDelayed(this, PHOTO_FLASH_MS);
            }
        };
        handler.post(photoFlashTick);
    }

    private void stopPhotoFlash() {
        if (photoFlashTick != null) {
            handler.removeCallbacks(photoFlashTick);
            photoFlashTick = null;
        }
        photoOverlayB.animate().cancel();
        photoOverlayB.setVisibility(View.GONE);
        photoOverlayB.setAlpha(0f);
        photoOverlay.setAlpha(1f);
    }

    private void burstConfetti() {
        confettiLayer.removeAllViews();
        confettiLayer.setVisibility(View.VISIBLE);
        int w = transitionOverlay.getWidth();
        int h = transitionOverlay.getHeight();
        if (w <= 0) w = getResources().getDisplayMetrics().widthPixels;
        if (h <= 0) h = getResources().getDisplayMetrics().heightPixels;
        for (int i = 0; i < 18; i++) {
            View bit = new View(this);
            int size = dp(10 + random.nextInt(14));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
            int startX = random.nextInt(Math.max(1, w));
            lp.leftMargin = startX;
            lp.topMargin = -dp(20);
            bit.setLayoutParams(lp);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(random.nextBoolean()
                    ? GradientDrawable.OVAL
                    : GradientDrawable.RECTANGLE);
            shape.setColor(CONFETTI_COLORS[i % CONFETTI_COLORS.length]);
            shape.setCornerRadius(dp(4));
            bit.setBackground(shape);
            bit.setRotation(random.nextInt(360));
            confettiLayer.addView(bit);
            float endY = h + dp(40);
            float endX = startX + (random.nextInt(240) - 120);
            long dur = 700 + random.nextInt(500);
            bit.animate()
                    .translationY(endY)
                    .translationX(endX - startX)
                    .rotation(bit.getRotation() + 180 + random.nextInt(180))
                    .alpha(0f)
                    .setDuration(dur)
                    .setInterpolator(new AccelerateInterpolator())
                    .start();
        }
    }

    private void bumpCountdown() {
        transitionCountdown.animate().cancel();
        transitionCountdown.setScaleX(0.55f);
        transitionCountdown.setScaleY(0.55f);
        transitionCountdown.setAlpha(0.35f);
        transitionCountdown.setRotation(-8f);
        transitionCountdown.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .rotation(0f)
                .setDuration(360)
                .setInterpolator(new OvershootInterpolator(2.4f))
                .start();
    }

    private void bumpMessagePlate() {
        transitionMessage.animate().cancel();
        transitionMessage.setRotation(0f);
        transitionMessage.animate()
                .scaleX(1.08f)
                .scaleY(1.08f)
                .rotation(2f)
                .setDuration(200)
                .withEndAction(() -> transitionMessage.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .rotation(0f)
                        .setDuration(260)
                        .setInterpolator(new OvershootInterpolator(1.1f))
                        .start())
                .start();
    }

    private void startFailPulse() {
        stopFailPulse();
        // Soft plate bounce on each countdown tick (no continuous animator loop).
        transitionScrim.setAlpha(1f);
        bumpMessagePlate();
    }

    private void stopFailPulse() {
        if (pulseAnim != null) {
            handler.removeCallbacks(pulseAnim);
            pulseAnim = null;
        }
        transitionScrim.animate().cancel();
        transitionScrim.setAlpha(1f);
    }

    private void finishWithBypass() {
        if (finishing) return;
        String qid = session.getCurrent() != null ? session.getCurrent().id : "";
        if (!currentAnswered && !qid.isEmpty()) {
            logEvent("skipped", qid, "", session.getMissStreak());
            currentAnswered = true; // avoid double-count
        }
        session.bypass(clock.nowMillis());
        logEvent("bypass", qid, "", session.getMissStreak());
        finishSession();
    }

    private void finishSession() {
        if (finishing) return;
        finishing = true;
        inTransition = false;
        mediaResumePending = true;
        stopBalloonPulse();
        if (successEndTick != null) {
            handler.removeCallbacks(successEndTick);
            successEndTick = null;
        }
        try {
            if (session != null) stats.appendSession(session);
        } catch (Exception e) {
            Log.e(AlarmScheduler.TAG, "stats session", e);
            Diag.nonFatal(this, "stats_session", e);
        }
        String sessionType = playMode ? "play" : "lock";
        String trigger = session != null ? session.getTrigger() : "";
        String outcome = session != null && session.getOutcome() != null
                ? session.getOutcome().name().toLowerCase() : "";
        Diag.schedule(this, new DiagnosticsStore.ScheduleEvent(
                clock.nowMillis(), TimeZone.getDefault(), DiagnosticsStore.KIND_QUIZ_ENDED,
                trigger, sessionType, prefs.nextAlarmAt(), prefs.getIntervalMs(), prefs.getJitterMs(),
                null, outcome));
        ScoreWidgetProvider.refreshAll(this);
        if (!playMode) {
            AlarmScheduler.scheduleAfterSessionEnd(this);
        }
        // Bring YouTube / game / whatever back before we disappear
        if (restorePreviousApp) {
            try {
                ForegroundApp.restore(this);
            } catch (Exception e) {
                Log.e(AlarmScheduler.TAG, "restore previous app", e);
            }
        } else {
            new AppPrefs(this).setResumePackage(null);
        }
        finish();
    }

    private void logEvent(String type, String qid, String choice, int streak) {
        long now = clock.nowMillis();
        long latency = -1L;
        if ("shown".equals(type)) {
            questionShownAt = now;
        } else if (questionShownAt > 0
                && ("correct".equals(type) || "wrong".equals(type) || "skipped".equals(type))) {
            latency = Math.max(0L, now - questionShownAt);
        }
        String sessionType = playMode ? "play" : "lock";
        String curriculumId = prefs.getActiveCurriculumId();
        try {
            stats.appendEvent(new StatsEvent(now, session.getSessionId(), type, qid, choice, streak,
                    latency, curriculumId, sessionType), TimeZone.getDefault());
        } catch (Exception e) {
            Log.e(AlarmScheduler.TAG, "stats event", e);
            Diag.nonFatal(this, "stats_event", e);
        }
        if ("correct".equals(type) || "wrong".equals(type) || "skipped".equals(type)
                || "bypass".equals(type) || "consoling".equals(type)) {
            Diag.breadcrumb(this, DiagBreadcrumb.builder(now, DiagnosticsStore.KIND_QUIZ)
                    .sessionType(sessionType)
                    .curriculumId(curriculumId)
                    .questionId(qid)
                    .outcome(type)
                    .latencyMs(latency)
                    .missStreak(streak)
                    .trigger(session != null ? session.getTrigger() : "")
                    .build());
        }
        if ("shown".equals(type) || "correct".equals(type) || "wrong".equals(type) || "skipped".equals(type)) {
            refreshQuizDayStats();
            ScoreWidgetProvider.refreshAll(this);
        }
    }

    private Uri pickPhoto(boolean forSuccess) {
        List<Uri> pool = PhotoLibrary.listForQuiz(this, forSuccess);
        if (pool.isEmpty()) {
            pool = devicePhotos;
        } else {
            devicePhotos = pool;
        }
        if (pool.isEmpty()) return null;
        return pool.get(random.nextInt(pool.size()));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** Color chip helpers for color questions. */
    private static final class MapColors {
        Integer forChoice(String type, String choice) {
            if (!"color".equals(type)) return null;
            switch (choice) {
                case "red": return Color.parseColor("#E74C3C");
                case "blue": return Color.parseColor("#3498DB");
                case "green": return Color.parseColor("#27AE60");
                case "yellow": return Color.parseColor("#F1C40F");
                case "orange": return Color.parseColor("#E67E22");
                case "purple": return Color.parseColor("#9B59B6");
                case "brown": return Color.parseColor("#8B5A2B");
                case "pink": return Color.parseColor("#FF8FB1");
                case "black": return Color.parseColor("#2C3E50");
                default: return Color.WHITE;
            }
        }
    }
}
