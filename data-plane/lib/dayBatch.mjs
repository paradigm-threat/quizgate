/**
 * Ephemeral day-batch aggregates for SI — live on cache PVC, never the Relay tip.
 * Path: {QUIZGATE_BATCH_DIR|QUIZGATE_CACHE_DIR/batches}/{YYYY-MM-DD}/{uuid}.json
 */
import { mkdirSync, writeFileSync, readFileSync, existsSync, renameSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { isUuid } from "./userStore.mjs";

const __dirname = dirname(fileURLToPath(import.meta.url));
const DEFAULT_CACHE = join(__dirname, "../.data/cache");

export const DAY_BATCH_SCHEMA = "quizgate.day_batch.v1";

export function batchesRoot() {
  if (process.env.QUIZGATE_BATCH_DIR) return process.env.QUIZGATE_BATCH_DIR;
  const cache = process.env.QUIZGATE_CACHE_DIR || DEFAULT_CACHE;
  return join(cache, "batches");
}

/** UTC calendar day YYYY-MM-DD */
export function utcDayKey(ts = Date.now()) {
  return new Date(ts).toISOString().slice(0, 10);
}

export function dayBatchPath(uuid, day = utcDayKey()) {
  return join(batchesRoot(), assertDayKey(day), `${uuid}.json`);
}

const DAY_RE = /^\d{4}-\d{2}-\d{2}$/;

/** Reject path traversal / non-calendar day keys. */
export function assertDayKey(day) {
  const ts = typeof day === "string" && DAY_RE.test(day) ? Date.parse(`${day}T00:00:00Z`) : NaN;
  if (!Number.isFinite(ts) || utcDayKey(ts) !== day) {
    throw new Error("invalid_day");
  }
  return day;
}

function nonNegInt(v) {
  const n = Number(v);
  if (!Number.isFinite(n) || n < 0) return 0;
  return Math.min(Math.floor(n), 1_000_000);
}

const SPLIT_KEYS = ["asked", "right", "wrong", "skipped"].flatMap((k) => [
  `${k}_lock`,
  `${k}_play`,
]);

export function normalizeToday(today) {
  if (!today || typeof today !== "object") {
    return { asked: 0, right: 0, wrong: 0, skipped: 0 };
  }
  const out = {
    asked: nonNegInt(today.asked),
    right: nonNegInt(today.right),
    wrong: nonNegInt(today.wrong),
    skipped: nonNegInt(today.skipped),
  };
  // Lock vs practice splits are only stored when the client sends them.
  for (const k of SPLIT_KEYS) {
    if (today[k] != null) out[k] = nonNegInt(today[k]);
  }
  return out;
}

function normalizeCurriculumId(id, fallback = null) {
  if (id == null) return fallback;
  const s = String(id).trim().slice(0, 128);
  if (!s || /[\\/\0]/.test(s)) return fallback;
  return s;
}

/**
 * Upsert one privacy-safe aggregate per user per day (last-write-wins counts).
 * `day` defaults to the UTC receive day; clients may pass their local day for backfill.
 * Does not store recent_quiz / event trails.
 */
/** Cap per-day question map size (qid → counts). */
const MAX_BY_QUESTION = 80;

export function normalizeByQuestion(raw) {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) return null;
  const out = {};
  let n = 0;
  for (const [qid, v] of Object.entries(raw)) {
    if (n >= MAX_BY_QUESTION) break;
    const id = String(qid || "")
      .trim()
      .slice(0, 64);
    if (!id || /[\\/\0"]/.test(id)) continue;
    if (!v || typeof v !== "object") continue;
    out[id] = {
      shown: nonNegInt(v.shown),
      correct: nonNegInt(v.correct),
      wrong: nonNegInt(v.wrong),
      skipped: nonNegInt(v.skipped),
    };
    n++;
  }
  return n > 0 ? out : null;
}

export function writeDayBatch(uuid, { today, curriculum_id, day, by_question } = {}) {
  if (!isUuid(uuid)) throw new Error("invalid_uuid");
  const dayKey = assertDayKey(day || utcDayKey());
  const fp = dayBatchPath(uuid, dayKey);
  mkdirSync(dirname(fp), { recursive: true });

  let prev = null;
  if (existsSync(fp)) {
    try {
      prev = JSON.parse(readFileSync(fp, "utf8"));
    } catch {
      prev = null;
    }
  }

  const now = Date.now();
  const byQ =
    normalizeByQuestion(by_question) ||
    (by_question === undefined ? normalizeByQuestion(prev?.by_question) : null);
  const rec = {
    schema: DAY_BATCH_SCHEMA,
    user_uuid: uuid,
    day: dayKey,
    curriculum_id: normalizeCurriculumId(
      curriculum_id,
      prev?.curriculum_id || null
    ),
    today: normalizeToday(today != null ? today : prev?.today),
    received_at_ms: now,
    sync_count: nonNegInt(prev?.sync_count) + 1,
  };
  if (byQ) rec.by_question = byQ;

  const tmp = `${fp}.${process.pid}.${now}.tmp`;
  writeFileSync(tmp, JSON.stringify(rec, null, 2) + "\n");
  renameSync(tmp, fp);
  return rec;
}

export function readDayBatch(uuid, day = utcDayKey()) {
  if (!isUuid(uuid)) return null;
  const fp = dayBatchPath(uuid, day);
  if (!existsSync(fp)) return null;
  try {
    return JSON.parse(readFileSync(fp, "utf8"));
  } catch {
    return null;
  }
}
