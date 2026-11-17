/**
 * Outbound mail via Gmail SMTP (clevertreellc@gmail.com + App Password).
 * Secrets from env — never commit. See docs/EMAIL.md.
 */
import { createTransport } from "nodemailer";
import { readFileSync, existsSync } from "node:fs";

function loadEnvFile(path) {
  if (!existsSync(path)) return;
  let text;
  try {
    text = readFileSync(path, "utf8");
  } catch {
    // root-only EnvironmentFile may be unreadable to the service user —
    // systemd already injected vars; skip quietly.
    return;
  }
  for (const line of text.split("\n")) {
    const m = line.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$/);
    if (!m) continue;
    const key = m[1];
    let val = m[2].replace(/^["']|["']$/g, "");
    if (process.env[key] == null || process.env[key] === "") {
      process.env[key] = val;
    }
  }
}

/** Load local then server secret files if present. */
export function loadSmtpEnv() {
  const home = process.env.HOME || "";
  if (home) loadEnvFile(`${home}/.config/quizgate/smtp.env`);
  loadEnvFile("/etc/quizgate/smtp.env");
}

export function smtpConfigured() {
  loadSmtpEnv();
  return Boolean(
    process.env.QUIZGATE_SMTP_USER &&
      process.env.QUIZGATE_SMTP_PASS &&
      process.env.QUIZGATE_SMTP_PASS !== "PASTE_APP_PASSWORD_HERE" &&
      process.env.QUIZGATE_SMTP_PASS !== "PASSWORD_NEEDED",
  );
}

function transporter() {
  loadSmtpEnv();
  if (!smtpConfigured()) {
    throw new Error(
      "SMTP not configured — set QUIZGATE_SMTP_* or see docs/EMAIL.md",
    );
  }
  return createTransport({
    host: process.env.QUIZGATE_SMTP_HOST || "smtp.gmail.com",
    port: Number(process.env.QUIZGATE_SMTP_PORT || 587),
    secure: false,
    requireTLS: true,
    auth: {
      user: process.env.QUIZGATE_SMTP_USER,
      pass: process.env.QUIZGATE_SMTP_PASS,
    },
  });
}

/**
 * @param {{ to: string, subject: string, text: string, html?: string }} msg
 */
export async function sendMail(msg) {
  const from =
    process.env.QUIZGATE_SMTP_FROM ||
    `QuizGate <${process.env.QUIZGATE_SMTP_USER}>`;
  const info = await transporter().sendMail({
    from,
    to: msg.to,
    subject: msg.subject,
    text: msg.text,
    html: msg.html,
  });
  return { messageId: info.messageId, accepted: info.accepted };
}

/** 6-digit code for 2FA — caller stores hash + expiry, not this plaintext. */
export function generateOtp() {
  const n = Math.floor(100000 + Math.random() * 900000);
  return String(n);
}

export async function sendTwoFactorEmail(to, code, { uuidHint } = {}) {
  const short = uuidHint
    ? ` for device …${String(uuidHint).slice(-4)}`
    : "";
  const text = [
    `Your QuizGate verification code${short} is: ${code}`,
    "",
    "It expires in 10 minutes. If you did not request this, ignore this email.",
    "",
    "— QuizGate (Clevertree)",
  ].join("\n");
  return sendMail({
    to,
    subject: "QuizGate verification code",
    text,
  });
}
