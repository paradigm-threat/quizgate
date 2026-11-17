/**
 * Parent email is never stored in plaintext on the tip.
 * hash = sha256(pepper + ":" + normalize(email) + ":" + uuid)
 */
import { createHash, timingSafeEqual } from "node:crypto";

export function normalizeEmail(email) {
  return String(email || "")
    .trim()
    .toLowerCase();
}

export function emailPepper() {
  return (
    process.env.QUIZGATE_EMAIL_PEPPER ||
    process.env.QUIZGATE_OPS_SECRET ||
    "quizgate-dev-pepper-change-me"
  );
}

export function hashEmailForUuid(email, uuid) {
  const norm = normalizeEmail(email);
  if (!norm || !norm.includes("@")) throw new Error("invalid_email");
  return createHash("sha256")
    .update(`${emailPepper()}:${norm}:${String(uuid).toLowerCase()}`)
    .digest("hex");
}

export function emailMatchesHash(email, uuid, storedHash) {
  if (!storedHash) return false;
  try {
    const a = Buffer.from(hashEmailForUuid(email, uuid), "hex");
    const b = Buffer.from(String(storedHash), "hex");
    if (a.length !== b.length) return false;
    return timingSafeEqual(a, b);
  } catch {
    return false;
  }
}
