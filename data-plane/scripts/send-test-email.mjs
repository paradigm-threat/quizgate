#!/usr/bin/env node
/**
 * Send a test message via clevertreellc@gmail.com SMTP.
 * Usage: node scripts/send-test-email.mjs recipient@example.com
 */
import { sendMail, smtpConfigured, loadSmtpEnv } from "../lib/mail.mjs";

const to = process.argv[2];
if (!to || !to.includes("@")) {
  console.error("Usage: node scripts/send-test-email.mjs <to@email>");
  process.exit(2);
}

loadSmtpEnv();
if (!smtpConfigured()) {
  console.error(
    "SMTP not configured. Create ~/.config/quizgate/smtp.env or /etc/quizgate/smtp.env — see docs/EMAIL.md",
  );
  process.exit(1);
}

const result = await sendMail({
  to,
  subject: "QuizGate SMTP test",
  text: [
    "This is a QuizGate outgoing-mail test.",
    `Time: ${new Date().toISOString()}`,
    "",
    "If you received this, Gmail App Password SMTP is working.",
  ].join("\n"),
});

console.log(JSON.stringify({ ok: true, to, ...result }, null, 2));
