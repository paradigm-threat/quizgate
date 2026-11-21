import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { enqueue } from "../lib/queue.mjs";
import { loadProfile } from "../lib/userStore.mjs";
import { drainOnce } from "./drain.mjs";
import { hashEmailForUuid } from "../lib/emailHash.mjs";

describe("admin jobs", () => {
  it("remote_register + admin_settings", async () => {
    const users = mkdtempSync(join(tmpdir(), "qg-u-"));
    const queue = mkdtempSync(join(tmpdir(), "qg-q-"));
    process.env.QUIZGATE_USERS_ROOT = users;
    process.env.QUIZGATE_QUEUE_DIR = queue;
    process.env.QUIZGATE_EMAIL_PEPPER = "test-pepper";
    const uuid = "ed873e38-5899-4b67-9df9-eca502570116";
    try {
      const email_hash = hashEmailForUuid("parent@example.com", uuid);
      enqueue({
        type: "remote_register",
        user_uuid: uuid,
        ai_enabled: true,
        human_enabled: true,
        email_hash,
      });
      await drainOnce(4);
      let p = loadProfile(uuid);
      assert.equal(p.admin.human_enabled, true);
      assert.equal(p.admin.email_hash, email_hash);

      enqueue({
        type: "admin_settings",
        user_uuid: uuid,
        actor: "human_admin",
        patch: {
          curriculum_id: "basic-math",
          interval_ms: 2_700_000,
          jitter_ms: 900_000,
          time_earn_cap_minutes: 90,
          stats_day_barrier_hour: 6,
        },
      });
      await drainOnce(4);
      p = loadProfile(uuid);
      assert.equal(p.curriculum_id, "basic-math");
      assert.equal(p.settings.interval_ms, 2_700_000);
      assert.equal(p.settings.time_earn_cap_minutes, 90);
      assert.equal(p.settings.stats_day_barrier_hour, 6);

      enqueue({
        type: "admin_settings",
        user_uuid: uuid,
        actor: "human_admin",
        patch: {
          level_up_min_accuracy_pct: 80,
          level_up_min_correct: 8,
          level_up_max_skip_pct: 20,
          curriculum_level: 0,
        },
      });
      await drainOnce(4);
      p = loadProfile(uuid);
      assert.equal(p.settings.level_up_min_accuracy_pct, 80);
      assert.equal(p.settings.level_up_min_correct, 8);
      assert.equal(p.settings.level_up_max_skip_pct, 20);
      assert.equal(p.settings.curriculum_level, 0);

      // Blank / omitted fields must not wipe prior overrides or invent defaults on tip.
      enqueue({
        type: "admin_settings",
        user_uuid: uuid,
        actor: "human_admin",
        patch: { curriculum_id: "basic-math" },
      });
      await drainOnce(4);
      p = loadProfile(uuid);
      assert.equal(p.settings.time_earn_cap_minutes, 90);
      assert.equal(p.settings.stats_day_barrier_hour, 6);
    } finally {
      delete process.env.QUIZGATE_USERS_ROOT;
      delete process.env.QUIZGATE_QUEUE_DIR;
      rmSync(users, { recursive: true, force: true });
      rmSync(queue, { recursive: true, force: true });
    }
  });
});
