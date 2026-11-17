import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import {
  isUuid,
  emptyProfile,
  commitUserFiles,
  loadProfile,
  SERVICE_ID,
  PermissionError,
} from "./userStore.mjs";

describe("userStore", () => {
  it("validates uuid", () => {
    assert.equal(isUuid("not-a-uuid"), false);
    assert.equal(isUuid("ed873e38-5899-4b67-9df9-eca502570116"), true);
  });

  it("rejects non-batch author", () => {
    const dir = mkdtempSync(join(tmpdir(), "qg-users-"));
    process.env.QUIZGATE_USERS_ROOT = dir;
    try {
      assert.throws(
        () =>
          commitUserFiles(
            "ed873e38-5899-4b67-9df9-eca502570116",
            { "profile.json": "{}\n" },
            "x",
            "anonymous",
          ),
        PermissionError,
      );
    } finally {
      delete process.env.QUIZGATE_USERS_ROOT;
      rmSync(dir, { recursive: true, force: true });
    }
  });

  it("batch commits profile", () => {
    const dir = mkdtempSync(join(tmpdir(), "qg-users-"));
    process.env.QUIZGATE_USERS_ROOT = dir;
    const uuid = "ed873e38-5899-4b67-9df9-eca502570116";
    try {
      const profile = emptyProfile(uuid);
      const ok = commitUserFiles(
        uuid,
        { "profile.json": JSON.stringify(profile, null, 2) + "\n" },
        "test",
        SERVICE_ID,
      );
      assert.equal(ok, true);
      const loaded = loadProfile(uuid);
      assert.equal(loaded.user_uuid, uuid);
      assert.equal(loaded.curriculum_id, "basic-math");
    } finally {
      delete process.env.QUIZGATE_USERS_ROOT;
      rmSync(dir, { recursive: true, force: true });
    }
  });
});
