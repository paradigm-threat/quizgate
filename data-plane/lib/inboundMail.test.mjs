import { describe, it, before, after } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import {
  checkInbox,
  extractUuids,
  isQuizgateAddressed,
  planAdminEmailAction,
  validateMailId,
  listMessages,
  getMessage,
} from "./inboundMail.mjs";

describe("inboundMail", () => {
  let root;
  const prev = {};

  before(() => {
    root = mkdtempSync(join(tmpdir(), "qg-mail-"));
    for (const f of ["new", "cur", "tmp"]) mkdirSync(join(root, f));
    writeFileSync(
      join(root, "new", "1000.abc.host"),
      [
        "From: parent@example.com",
        "To: batch@quizgate.app",
        "Subject: please set interval for ed873e38-5899-4b67-9df9-eca502570116",
        "",
        "UUID ed873e38-5899-4b67-9df9-eca502570116 — slow quizzes down.",
        "",
      ].join("\r\n"),
    );
    writeFileSync(
      join(root, "new", "1001.def.host"),
      ["From: x@y.z", "To: someone@paradigmthreat.net", "Subject: noise", "", "hi", ""].join(
        "\r\n",
      ),
    );
    prev.MODE = process.env.QUIZGATE_MAIL_MODE;
    prev.DIR = process.env.QUIZGATE_MAIL_MAILDIR;
    process.env.QUIZGATE_MAIL_MODE = "local";
    process.env.QUIZGATE_MAIL_MAILDIR = root;
  });

  after(() => {
    if (prev.MODE === undefined) delete process.env.QUIZGATE_MAIL_MODE;
    else process.env.QUIZGATE_MAIL_MODE = prev.MODE;
    if (prev.DIR === undefined) delete process.env.QUIZGATE_MAIL_MAILDIR;
    else process.env.QUIZGATE_MAIL_MAILDIR = prev.DIR;
    rmSync(root, { recursive: true, force: true });
  });

  it("validates mail ids", () => {
    assert.throws(() => validateMailId("../x"));
    assert.equal(validateMailId("1000.abc.host"), true);
  });

  it("lists unread and filters quizgate", () => {
    const all = listMessages();
    assert.equal(all.length, 2);
    assert.ok(isQuizgateAddressed(all.find((m) => m.id === "1000.abc.host")));
    const qg = checkInbox({ unreadOnly: true, quizgateOnly: true });
    assert.equal(qg.count, 1);
    assert.equal(qg.address, "batch@quizgate.app");
  });

  it("reads body + extracts uuids + plans future admin action", () => {
    const msg = getMessage("1000.abc.host");
    assert.match(msg.body, /slow quizzes/);
    const uuids = extractUuids(msg.subject, msg.body);
    assert.equal(uuids[0], "ed873e38-5899-4b67-9df9-eca502570116");
    const plan = planAdminEmailAction(msg);
    assert.equal(plan.actionable, true);
    assert.equal(plan.user_uuid, uuids[0]);
  });
});
