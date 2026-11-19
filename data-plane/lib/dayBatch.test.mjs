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

  it("utcDayKey is YYYY-MM-DD", () => {
    assert.match(utcDayKey(Date.parse("2026-11-18T23:00:00Z")), /^\d{4}-\d{2}-\d{2}$/);
  });
});
