import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  evaluateLevelUp,
  DEFAULT_MIN_ACCURACY_PCT,
  DEFAULT_MIN_CORRECT,
  DEFAULT_MAX_SKIP_PCT,
} from "./levelUpPolicy.mjs";

describe("levelUpPolicy", () => {
  it("defaults and eligible path", () => {
    assert.equal(DEFAULT_MIN_ACCURACY_PCT, 75);
    assert.equal(DEFAULT_MIN_CORRECT, 5);
    assert.equal(DEFAULT_MAX_SKIP_PCT, 25);
    const e = evaluateLevelUp({ asked: 10, right: 8, wrong: 2, skipped: 1 });
    assert.equal(e.eligible, true);
    assert.equal(e.observed.accuracy_pct, 80);
    assert.ok(e.reasons.includes("level_up_thresholds_met"));
  });

  it("fails accuracy / min correct / skips", () => {
    assert.equal(evaluateLevelUp({ asked: 10, right: 5, wrong: 5, skipped: 0 }).eligible, false);
    assert.equal(evaluateLevelUp({ asked: 4, right: 4, wrong: 0, skipped: 0 }).eligible, false);
    assert.equal(evaluateLevelUp({ asked: 10, right: 8, wrong: 0, skipped: 4 }).eligible, false);
  });

  it("admin overrides", () => {
    const e = evaluateLevelUp(
      { asked: 10, right: 6, wrong: 4, skipped: 0 },
      { level_up_min_accuracy_pct: 60, level_up_min_correct: 5 },
    );
    assert.equal(e.eligible, true);
    assert.equal(e.thresholds.min_accuracy_pct, 60);
  });
});
