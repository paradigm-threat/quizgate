/**
 * Soft gates for advancing a curriculum level. Repo defaults; tip settings may
 * override. SI should adhere but may exception with a documented reason.
 *
 * accuracy = right / (right + wrong)
 * skips (default) = skipped <= right + wrong
 *   (more skips than answered → not eligible)
 * optional admin level_up_max_skip_pct = max skipped/asked % instead
 */
export const DEFAULT_MIN_ACCURACY_PCT = 75;
export const DEFAULT_MIN_CORRECT = 5;
/** Sentinel: use skipped <= right+wrong (not a percent of asked). */
export const DEFAULT_MAX_SKIP_PCT = -1;

export function parseMinAccuracyPct(raw) {
  const n = Number(raw);
  if (!Number.isFinite(n) || n < 1 || n > 100) return DEFAULT_MIN_ACCURACY_PCT;
  return Math.floor(n);
}

export function parseMinCorrect(raw) {
  const n = Number(raw);
  if (!Number.isFinite(n) || n < 1 || n > 10_000) return DEFAULT_MIN_CORRECT;
  return Math.floor(n);
}

/** <0 → default answered-comparison; 0..100 → max skipped/asked %. */
export function parseMaxSkipPct(raw) {
  const n = Number(raw);
  if (!Number.isFinite(n) || n < 0) return DEFAULT_MAX_SKIP_PCT;
  if (n > 100) return DEFAULT_MAX_SKIP_PCT;
  return Math.floor(n);
}

/**
 * @param {object} counts { asked, right, wrong, skipped }
 * @param {object} [settings] tip settings overrides (blank/missing → defaults)
 */
export function evaluateLevelUp(counts = {}, settings = {}) {
  const asked = Math.max(0, Number(counts.asked) || 0);
  const right = Math.max(0, Number(counts.right) || 0);
  const wrong = Math.max(0, Number(counts.wrong) || 0);
  const skipped = Math.max(0, Number(counts.skipped) || 0);
  const minAccuracyPct = settings.level_up_min_accuracy_pct != null
    ? parseMinAccuracyPct(settings.level_up_min_accuracy_pct)
    : DEFAULT_MIN_ACCURACY_PCT;
  const minCorrect = settings.level_up_min_correct != null
    ? parseMinCorrect(settings.level_up_min_correct)
    : DEFAULT_MIN_CORRECT;
  const maxSkipPct = settings.level_up_max_skip_pct != null
    && settings.level_up_max_skip_pct !== ""
    ? parseMaxSkipPct(settings.level_up_max_skip_pct)
    : DEFAULT_MAX_SKIP_PCT;

  const answered = right + wrong;
  const accuracyPct = answered > 0 ? Math.round((100 * right) / answered) : 0;
  const skipPct = asked > 0 ? Math.round((100 * skipped) / asked) : 0;
  const enoughCorrect = right >= minCorrect;
  const accurateEnough = answered > 0 && accuracyPct >= minAccuracyPct;
  let skipsOk;
  if (maxSkipPct < 0) {
    skipsOk = right + wrong + skipped > 0 && skipped <= right + wrong;
  } else {
    skipsOk = asked > 0 && skipPct <= maxSkipPct;
  }
  const eligible = enoughCorrect && accurateEnough && skipsOk;

  const reasons = [];
  if (!enoughCorrect) reasons.push("below_min_correct");
  if (!accurateEnough) reasons.push(answered === 0 ? "no_answered_samples" : "below_min_accuracy");
  if (!skipsOk) {
    reasons.push(
      right + wrong + skipped === 0
        ? "no_asks"
        : maxSkipPct < 0
          ? "skips_exceed_answered"
          : "skip_rate_too_high",
    );
  }
  if (eligible) reasons.push("level_up_thresholds_met");

  return {
    eligible,
    enough_correct: enoughCorrect,
    accurate_enough: accurateEnough,
    skips_ok: skipsOk,
    observed: { asked, right, wrong, skipped, accuracy_pct: accuracyPct, skip_pct: skipPct },
    thresholds: {
      min_accuracy_pct: minAccuracyPct,
      min_correct: minCorrect,
      max_skip_pct: maxSkipPct,
      skip_rule: maxSkipPct < 0 ? "skipped_le_answered" : "max_skip_pct_of_asked",
    },
    reasons,
    si_guidance:
      "Adhere to thresholds by default; exceptions allowed when other evidence clearly warrants them — document the reason.",
  };
}
