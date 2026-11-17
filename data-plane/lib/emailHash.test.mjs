import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { hashEmailForUuid, emailMatchesHash, normalizeEmail } from "./emailHash.mjs";

describe("emailHash", () => {
  it("normalizes and matches", () => {
    process.env.QUIZGATE_EMAIL_PEPPER = "test-pepper";
    const uuid = "ed873e38-5899-4b67-9df9-eca502570116";
    const h = hashEmailForUuid("Parent@Example.COM", uuid);
    assert.equal(h.length, 64);
    assert.equal(emailMatchesHash("parent@example.com", uuid, h), true);
    assert.equal(emailMatchesHash("other@example.com", uuid, h), false);
    assert.equal(normalizeEmail(" A@B.C "), "a@b.c");
  });
});
