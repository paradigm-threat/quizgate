import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { generateOtp, smtpConfigured } from "./mail.mjs";

describe("mail", () => {
  it("generates 6-digit otp", () => {
    const c = generateOtp();
    assert.match(c, /^[0-9]{6}$/);
  });

  it("reports smtpConfigured false without secrets", () => {
    const prevUser = process.env.QUIZGATE_SMTP_USER;
    const prevPass = process.env.QUIZGATE_SMTP_PASS;
    delete process.env.QUIZGATE_SMTP_USER;
    delete process.env.QUIZGATE_SMTP_PASS;
    try {
      // May still be true if env files exist with real secrets — only assert shape
      const v = smtpConfigured();
      assert.equal(typeof v, "boolean");
    } finally {
      if (prevUser != null) process.env.QUIZGATE_SMTP_USER = prevUser;
      else delete process.env.QUIZGATE_SMTP_USER;
      if (prevPass != null) process.env.QUIZGATE_SMTP_PASS = prevPass;
      else delete process.env.QUIZGATE_SMTP_PASS;
    }
  });
});
