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
    assert.equal(DEFAULT_MAX_SKIP_PCT, -1);
    const e = evaluateLevelUp({ asked: 10, right: 8, wrong: 2, skipped: 1 });
    assert.equal(e.eligible, true);
    assert.equal(e.thresholds.skip_rule, "skipped_le_answered");
  });

  it("sim student 9/1/5 eligible under answered skip rule", () => {
    const e = evaluateLevelUp({ asked: 15, right: 9, wrong: 1, skipped: 5 });
    assert.equal(e.eligible, true);
  });

  it("blocks when skips exceed answered", () => {
    const e = evaluateLevelUp({ asked: 15, right: 5, wrong: 0, skipped: 10 });
    assert.equal(e.eligible, false);
    assert.ok(e.reasons.includes("skips_exceed_answered"));
  });

  it("fails accuracy / min correct", () => {
    assert.equal(evaluateLevelUp({ asked: 10, right: 5, wrong: 5, skipped: 0 }).eligible, false);
    assert.equal(evaluateLevelUp({ asked: 4, right: 4, wrong: 0, skipped: 0 }).eligible, false);
  });

  it("admin pct override", () => {
    const e = evaluateLevelUp(
      { asked: 10, right: 8, wrong: 0, skipped: 4 },
      { level_up_max_skip_pct: 25 },
    );
    assert.equal(e.eligible, false);
    assert.equal(e.thresholds.skip_rule, "max_skip_pct_of_asked");
  });
});
