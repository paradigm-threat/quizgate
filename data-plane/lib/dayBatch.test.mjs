import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, rmSync, existsSync, readdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import {
  writeDayBatch,
  readDayBatch,
  utcDayKey,
  DAY_BATCH_SCHEMA,
  dayBatchPath,
  normalizeToday,
  normalizeByFormat,
  normalizeSchedule,
  assertDayKey,
} from "./dayBatch.mjs";

describe("dayBatch", () => {
  it("upserts one file per uuid per UTC day and bumps sync_count", () => {
    const root = mkdtempSync(join(tmpdir(), "qg-batches-"));
    process.env.QUIZGATE_BATCH_DIR = root;
    const uuid = "851f35c3-2432-4e44-81cd-84050a403e50";
    const day = "2026-11-18";
    try {
      const a = writeDayBatch(uuid, {
        day,
        curriculum_id: "basic-math",
        today: { asked: 10, right: 9, wrong: 1, skipped: 0 },
      });
      assert.equal(a.schema, DAY_BATCH_SCHEMA);
      assert.equal(a.day, day);
      assert.equal(a.sync_count, 1);
      assert.equal(a.today.asked, 10);
      assert.ok(existsSync(dayBatchPath(uuid, day)));

      const b = writeDayBatch(uuid, {
        day,
        curriculum_id: "basic-math",
        today: { asked: 12, right: 10, wrong: 1, skipped: 1 },
      });
      assert.equal(b.sync_count, 2);
      assert.equal(b.today.asked, 12);
      assert.equal(b.today.skipped, 1);

      const loaded = readDayBatch(uuid, day);
      assert.equal(loaded.sync_count, 2);
      assert.equal(loaded.user_uuid, uuid);
      // Only one file under that day folder
      assert.deepEqual(readdirSync(join(root, day)), [`${uuid}.json`]);
      // No recent_quiz / event trails
      assert.equal(loaded.recent_quiz, undefined);
      assert.equal(loaded.events, undefined);
    } finally {
      delete process.env.QUIZGATE_BATCH_DIR;
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("rejects invalid uuid", () => {
    assert.throws(() => writeDayBatch("not-a-uuid", { today: {} }), /invalid_uuid/);
  });

  it("rejects path-like day keys and clamps bad counts", () => {
    const root = mkdtempSync(join(tmpdir(), "qg-batches-bad-"));
    process.env.QUIZGATE_BATCH_DIR = root;
    const uuid = "851f35c3-2432-4e44-81cd-84050a403e50";
    try {
      assert.throws(
        () => writeDayBatch(uuid, { day: "../etc", today: {} }),
        /invalid_day/
      );
      const rec = writeDayBatch(uuid, {
        day: "2026-11-18",
        today: { asked: "nope", right: -3, wrong: 1.9, skipped: Infinity },
      });
      assert.deepEqual(rec.today, { asked: 0, right: 0, wrong: 1, skipped: 0 });
    } finally {
      delete process.env.QUIZGATE_BATCH_DIR;
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("keeps lock/play splits when sent and omits them otherwise", () => {
    const root = mkdtempSync(join(tmpdir(), "qg-batches-split-"));
    process.env.QUIZGATE_BATCH_DIR = root;
    const uuid = "851f35c3-2432-4e44-81cd-84050a403e50";
    try {
      const rec = writeDayBatch(uuid, {
        day: "2026-11-18",
        today: {
          asked: 52, right: 24, wrong: 2, skipped: 8,
          asked_lock: 5, right_lock: 1, wrong_lock: 1, skipped_lock: 0,
          asked_play: 0, right_play: 0, wrong_play: 0, skipped_play: "x",
          recent_quiz: [{ q: "1+1" }],
        },
      });
      assert.deepEqual(rec.today, {
        asked: 52, right: 24, wrong: 2, skipped: 8,
        asked_lock: 5, asked_play: 0,
        right_lock: 1, right_play: 0,
        wrong_lock: 1, wrong_play: 0,
        skipped_lock: 0, skipped_play: 0,
      });
      assert.deepEqual(normalizeToday({ asked: 3 }), {
        asked: 3, right: 0, wrong: 0, skipped: 0,
      });
    } finally {
      delete process.env.QUIZGATE_BATCH_DIR;
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("stores by_question map when provided", () => {
    const root = mkdtempSync(join(tmpdir(), "qg-batches-bq-"));
    process.env.QUIZGATE_BATCH_DIR = root;
    const uuid = "851f35c3-2432-4e44-81cd-84050a403e50";
    try {
      const rec = writeDayBatch(uuid, {
        day: "2026-11-21",
        today: { asked: 4, right: 1, wrong: 0, skipped: 1 },
        by_question: {
          a10: { shown: 2, correct: 0, wrong: 0, skipped: 1 },
          a04: { shown: 1, correct: 1, wrong: 0, skipped: 0 },
        },
      });
      assert.equal(rec.by_question.a10.skipped, 1);
      assert.equal(rec.by_question.a04.correct, 1);
      assert.equal(readDayBatch(uuid, "2026-11-21").by_question.a10.shown, 2);
    } finally {
      delete process.env.QUIZGATE_BATCH_DIR;
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("stores by_format and schedule health; omit keeps prior", () => {
    const root = mkdtempSync(join(tmpdir(), "qg-batches-fmt-"));
    process.env.QUIZGATE_BATCH_DIR = root;
    const uuid = "851f35c3-2432-4e44-81cd-84050a403e50";
    try {
      const a = writeDayBatch(uuid, {
        day: "2026-11-22",
        today: { asked: 0, right: 0, wrong: 0, skipped: 0 },
        by_format: {
          choice: { shown: 3, correct: 2, wrong: 0, skipped: 1 },
          equation: { shown: 1, correct: 0, wrong: 1, skipped: 0 },
        },
        schedule: {
          a11y: 0,
          armed: 1,
          alarm_fired: 0,
          arm_blocked: 1,
          boot_skip: 1,
          interactive_ms: 0,
          fire_status: "a11y_off",
        },
      });
      assert.equal(a.by_format.choice.correct, 2);
      assert.equal(a.schedule.fire_status, "a11y_off");
      assert.equal(a.schedule.arm_blocked, 1);
      assert.deepEqual(normalizeByFormat({ choice: { shown: 1 } }).choice, {
        shown: 1, correct: 0, wrong: 0, skipped: 0,
      });
      assert.equal(normalizeSchedule({ fire_status: "nope" }).fire_status, "unknown");

      const b = writeDayBatch(uuid, {
        day: "2026-11-22",
        today: { asked: 0, right: 0, wrong: 0, skipped: 0 },
        // omit by_format / schedule → keep prior
      });
      assert.equal(b.sync_count, 2);
      assert.equal(b.by_format.choice.shown, 3);
      assert.equal(b.schedule.fire_status, "a11y_off");
      assert.equal(b.schedule.interactive_ms, 0);
    } finally {
      delete process.env.QUIZGATE_BATCH_DIR;
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("explicit day writes that day's file; omitted day uses UTC today", () => {
    const root = mkdtempSync(join(tmpdir(), "qg-batches-day-"));
    process.env.QUIZGATE_BATCH_DIR = root;
    const uuid = "851f35c3-2432-4e44-81cd-84050a403e50";
    try {
      const back = writeDayBatch(uuid, { day: "2026-11-18", today: { asked: 52 } });
      const now = writeDayBatch(uuid, { today: { asked: 2 } });
      assert.equal(back.day, "2026-11-18");
      assert.equal(now.day, utcDayKey());
      assert.equal(readDayBatch(uuid, "2026-11-18").today.asked, 52);
      assert.throws(() => assertDayKey("2026-13-01"), /invalid_day/);
      assert.throws(() => assertDayKey("2026-02-30"), /invalid_day/);
    } finally {
      delete process.env.QUIZGATE_BATCH_DIR;
      rmSync(root, { recursive: true, force: true });
    }
  });

  it("utcDayKey is YYYY-MM-DD", () => {
    assert.match(utcDayKey(Date.parse("2026-11-18T23:00:00Z")), /^\d{4}-\d{2}-\d{2}$/);
  });
});
