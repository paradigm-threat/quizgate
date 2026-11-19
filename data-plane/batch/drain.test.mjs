import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, rmSync, existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { enqueue } from "../lib/queue.mjs";
import { loadProfile } from "../lib/userStore.mjs";
import { drainOnce } from "./drain.mjs";

describe("batch drain", () => {
  it("writes tip profile only from progress_sync (no days/events)", async () => {
    const users = mkdtempSync(join(tmpdir(), "qg-u-"));
    const queue = mkdtempSync(join(tmpdir(), "qg-q-"));
    process.env.QUIZGATE_USERS_ROOT = users;
    process.env.QUIZGATE_QUEUE_DIR = queue;
    const uuid = "ed873e38-5899-4b67-9df9-eca502570116";
    try {
      enqueue({
        type: "progress_sync",
        user_uuid: uuid,
        payload: {
          today: { asked: 40, right: 38, wrong: 2, skipped: 0 },
          recent_quiz: [
            JSON.stringify({ type: "correct", question_id: "m1", ts: Date.now() }),
          ],
          curriculum_id: "basic-math",
          interval_ms: 1_800_000,
          jitter_ms: 900_000,
        },
      });
      const out = await drainOnce(4);
      assert.ok(out.drained >= 1);
      const profile = loadProfile(uuid);
      assert.ok(profile);
      assert.equal(profile.user_uuid, uuid);
      assert.equal(profile.curriculum_id, "basic-math");
      assert.ok(profile.readiness.score >= 0);
      assert.ok(Array.isArray(profile.readiness.reasons));
      const userDir = join(users, "users", uuid);
      assert.ok(existsSync(join(userDir, "profile.json")));
      assert.equal(existsSync(join(userDir, "days.jsonl")), false);
      assert.equal(existsSync(join(userDir, "events.jsonl")), false);
    } finally {
      delete process.env.QUIZGATE_USERS_ROOT;
      delete process.env.QUIZGATE_QUEUE_DIR;
      rmSync(users, { recursive: true, force: true });
      rmSync(queue, { recursive: true, force: true });
    }
  });
});
