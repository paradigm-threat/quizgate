import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, rmSync, existsSync, mkdirSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { execFileSync } from "node:child_process";
import { enqueue } from "../lib/queue.mjs";
import { loadProfile, emptyProfile, SERVICE_ID, SERVICE_EMAIL } from "../lib/userStore.mjs";
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
      assert.equal(out.results[0].result.tip_commit, true);
      const profile = loadProfile(uuid);
      assert.ok(profile);
      assert.equal(profile.user_uuid, uuid);
      assert.equal(profile.curriculum_id, "basic-math");
      // Soft gates hint next level; tip curriculum_level stays parent/SI-owned.
      assert.equal(profile.settings.curriculum_level == null ? 0 : profile.settings.curriculum_level, 0);
      assert.ok((profile.content_hints || []).some((h) => String(h).startsWith("consider_level:")));
      assert.equal(profile.readiness.next_level, 1);
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

  it("done_empty when tip exists and no standing change", async () => {
    const users = mkdtempSync(join(tmpdir(), "qg-u-"));
    const queue = mkdtempSync(join(tmpdir(), "qg-q-"));
    process.env.QUIZGATE_USERS_ROOT = users;
    process.env.QUIZGATE_QUEUE_DIR = queue;
    const uuid = "851f35c3-2432-4e44-81cd-84050a403e50";
    try {
      execFileSync("git", ["init", "-b", "users"], { cwd: users });
      mkdirSync(join(users, "users", uuid), { recursive: true });
      const seed = emptyProfile(uuid);
      seed.settings.curriculum_level = 0;
      // Already on slow interval so low-accuracy day does not change settings.
      seed.settings.interval_ms = 2_700_000;
      seed.settings.jitter_ms = 900_000;
      writeFileSync(
        join(users, "users", uuid, "profile.json"),
        JSON.stringify(seed, null, 2) + "\n",
      );
      execFileSync("git", ["add", "-A"], { cwd: users });
      execFileSync(
        "git",
        [
          "-c", `user.name=${SERVICE_ID}`,
          "-c", `user.email=${SERVICE_EMAIL}`,
          "commit", "-m", "seed",
        ],
        { cwd: users },
      );
      const headBefore = execFileSync("git", ["rev-parse", "HEAD"], {
        cwd: users,
        encoding: "utf8",
      }).trim();
      enqueue({
        type: "progress_sync",
        user_uuid: uuid,
        payload: {
          today: { asked: 32, right: 7, wrong: 8, skipped: 3 },
          curriculum_id: "basic-math",
        },
      });
      const out = await drainOnce(4);
      assert.equal(out.results[0].result.done_empty, true);
      assert.equal(out.results[0].result.tip_commit, false);
      const headAfter = execFileSync("git", ["rev-parse", "HEAD"], {
        cwd: users,
        encoding: "utf8",
      }).trim();
      assert.equal(headAfter, headBefore);
      assert.equal(loadProfile(uuid).settings.curriculum_level, 0);
    } finally {
      delete process.env.QUIZGATE_USERS_ROOT;
      delete process.env.QUIZGATE_QUEUE_DIR;
      rmSync(users, { recursive: true, force: true });
      rmSync(queue, { recursive: true, force: true });
    }
  });
});
