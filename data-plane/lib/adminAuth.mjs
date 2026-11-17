/**
 * Ephemeral OTP + session store for admin.quizgate.app (PVC/local disk).
 */
import { createHash, randomBytes, timingSafeEqual } from "node:crypto";
import {
  mkdirSync,
  writeFileSync,
  readFileSync,
  existsSync,
  unlinkSync,
  readdirSync,
} from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { generateOtp, sendTwoFactorEmail, smtpConfigured } from "./mail.mjs";
import { emailMatchesHash, normalizeEmail } from "./emailHash.mjs";
import { isUuid, loadProfile } from "./userStore.mjs";

const __dirname = dirname(fileURLToPath(import.meta.url));
const AUTH_DIR = process.env.QUIZGATE_AUTH_DIR || join(__dirname, "../.data/auth");
const OTP_TTL_MS = 10 * 60 * 1000;
const SESSION_TTL_MS = 12 * 60 * 60 * 1000;

function ensureDir(sub) {
  const d = join(AUTH_DIR, sub);
  mkdirSync(d, { recursive: true });
  return d;
}

function hashOtp(code) {
  return createHash("sha256").update(String(code)).digest("hex");
}

function safeEqualHex(a, b) {
  try {
    const ab = Buffer.from(String(a), "hex");
    const bb = Buffer.from(String(b), "hex");
    if (ab.length !== bb.length) return false;
    return timingSafeEqual(ab, bb);
  } catch {
    return false;
  }
}

export function getAdminEmailHash(uuid) {
  const profile = loadProfile(uuid);
  return profile?.admin?.email_hash || null;
}

export function isHumanAdminEnabled(uuid) {
  const profile = loadProfile(uuid);
  return Boolean(profile?.admin?.human_enabled && profile?.admin?.email_hash);
}

export function isAiRemoteEnabled(uuid) {
  const profile = loadProfile(uuid);
  if (profile?.admin?.ai_enabled === false) return false;
  return Boolean(profile?.admin?.ai_enabled || profile?.remote_ai);
}

/**
 * Start 2FA: UUID + email must match tip hash. Sends email if SMTP configured.
 */
export async function requestOtp(uuid, email) {
  if (!isUuid(uuid)) return { ok: false, error: "invalid_uuid", status: 400 };
  const norm = normalizeEmail(email);
  if (!norm.includes("@")) return { ok: false, error: "invalid_email", status: 400 };

  const stored = getAdminEmailHash(uuid);
  if (!stored) {
    // Anti-enumeration: same generic error
    return { ok: false, error: "auth_failed", status: 401 };
  }
  if (!emailMatchesHash(norm, uuid, stored)) {
    return { ok: false, error: "auth_failed", status: 401 };
  }
  if (!smtpConfigured()) {
    return { ok: false, error: "smtp_not_configured", status: 503 };
  }

  const code = generateOtp();
  const id = randomBytes(16).toString("hex");
  const rec = {
    id,
    uuid: uuid.toLowerCase(),
    email_norm: norm,
    code_hash: hashOtp(code),
    expires_at_ms: Date.now() + OTP_TTL_MS,
    created_at_ms: Date.now(),
  };
  writeFileSync(join(ensureDir("otp"), `${id}.json`), JSON.stringify(rec));
  // Test-only: plaintext last OTP for ops/browser verification (never enable in public docs as default).
  if (process.env.QUIZGATE_OTP_DEBUG === "1") {
    writeFileSync(
      join(ensureDir("otp"), `_last_debug.json`),
      JSON.stringify({
        otp_id: id,
        uuid: rec.uuid,
        code,
        expires_at_ms: rec.expires_at_ms,
        created_at_ms: rec.created_at_ms,
      }),
    );
  }
  await sendTwoFactorEmail(norm, code, { uuidHint: uuid });
  return { ok: true, otp_id: id, expires_in_s: Math.floor(OTP_TTL_MS / 1000) };
}

export function verifyOtp(otpId, code) {
  const fp = join(ensureDir("otp"), `${otpId}.json`);
  if (!existsSync(fp)) return { ok: false, error: "auth_failed", status: 401 };
  const rec = JSON.parse(readFileSync(fp, "utf8"));
  if (Date.now() > rec.expires_at_ms) {
    try {
      unlinkSync(fp);
    } catch {
      /* */
    }
    return { ok: false, error: "otp_expired", status: 401 };
  }
  if (!safeEqualHex(rec.code_hash, hashOtp(code))) {
    return { ok: false, error: "auth_failed", status: 401 };
  }
  try {
    unlinkSync(fp);
  } catch {
    /* */
  }
  const token = randomBytes(32).toString("hex");
  const session = {
    token,
    uuid: rec.uuid,
    email_norm: rec.email_norm,
    expires_at_ms: Date.now() + SESSION_TTL_MS,
    created_at_ms: Date.now(),
  };
  writeFileSync(join(ensureDir("sessions"), `${token}.json`), JSON.stringify(session));
  return {
    ok: true,
    token,
    user_uuid: rec.uuid,
    expires_in_s: Math.floor(SESSION_TTL_MS / 1000),
  };
}

export function sessionFromAuthHeader(header) {
  if (!header || !String(header).startsWith("Bearer ")) return null;
  const token = String(header).slice(7).trim();
  if (!token) return null;
  const fp = join(ensureDir("sessions"), `${token}.json`);
  if (!existsSync(fp)) return null;
  try {
    const session = JSON.parse(readFileSync(fp, "utf8"));
    if (Date.now() > session.expires_at_ms) {
      unlinkSync(fp);
      return null;
    }
    return session;
  } catch {
    return null;
  }
}

export function requireSessionForUuid(header, uuid) {
  const session = sessionFromAuthHeader(header);
  if (!session) return { ok: false, error: "unauthorized", status: 401 };
  if (String(session.uuid).toLowerCase() !== String(uuid).toLowerCase()) {
    return { ok: false, error: "forbidden", status: 403 };
  }
  return { ok: true, session };
}

export function purgeExpiredAuth() {
  for (const sub of ["otp", "sessions"]) {
    const dir = ensureDir(sub);
    for (const name of readdirSync(dir)) {
      if (!name.endsWith(".json")) continue;
      try {
        const rec = JSON.parse(readFileSync(join(dir, name), "utf8"));
        if (rec.expires_at_ms && Date.now() > rec.expires_at_ms) {
          unlinkSync(join(dir, name));
        }
      } catch {
        /* */
      }
    }
  }
}
