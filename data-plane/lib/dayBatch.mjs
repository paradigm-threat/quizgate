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

function nonNegLong(v) {
  const n = Number(v);
  if (!Number.isFinite(n) || n < 0) return 0;
  return Math.min(Math.floor(n), Number.MAX_SAFE_INTEGER);
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
const MAX_BY_FORMAT = 8;

const FIRE_STATUS = new Set([
  "ok",
  "a11y_off",
  "never_armed",
  "no_fire_while_interactive",
  "idle_or_off",
  "unknown",
]);

function normalizeCountBlock(v) {
  if (!v || typeof v !== "object") return null;
  return {
    shown: nonNegInt(v.shown),
    correct: nonNegInt(v.correct),
    wrong: nonNegInt(v.wrong),
    skipped: nonNegInt(v.skipped),
  };
}

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
    const block = normalizeCountBlock(v);
    if (!block) continue;
    out[id] = block;
    n++;
  }
  return n > 0 ? out : null;
}

/** Day-level ask-format map: equation|true_false|choice|unknown → counts. */
export function normalizeByFormat(raw) {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) return null;
  const out = {};
  let n = 0;
  for (const [fmt, v] of Object.entries(raw)) {
    if (n >= MAX_BY_FORMAT) break;
    const id = String(fmt || "")
      .trim()
      .slice(0, 32);
    if (!id || /[\\/\0"]/.test(id)) continue;
    const block = normalizeCountBlock(v);
    if (!block) continue;
    out[id] = block;
    n++;
  }
  return n > 0 ? out : null;
}

export function normalizeSchedule(raw) {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) return null;
  let status = String(raw.fire_status || raw.fireStatus || "unknown")
    .trim()
    .slice(0, 48);
  if (!FIRE_STATUS.has(status)) status = "unknown";
  return {
    a11y: nonNegInt(raw.a11y) > 0 ? 1 : 0,
    armed: nonNegInt(raw.armed) > 0 ? 1 : 0,
    alarm_fired: nonNegInt(raw.alarm_fired ?? raw.alarmFired),
    alarm_scheduled: nonNegInt(raw.alarm_scheduled ?? raw.alarmScheduled),
    arm_blocked: nonNegInt(raw.arm_blocked ?? raw.armBlocked),
    skip_fire: nonNegInt(raw.skip_fire ?? raw.skipFire),
    boot_skip: nonNegInt(raw.boot_skip ?? raw.bootSkip),
    quiz_started: nonNegInt(raw.quiz_started ?? raw.quizStarted),
    interactive_ms: nonNegLong(raw.interactive_ms ?? raw.interactiveMs),
    fire_status: status,
  };
}

/** Privacy-safe latency bands (correct/wrong only). No raw ms. */
export function normalizeLatency(raw) {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) return null;
  const fast = nonNegInt(raw.fast_lt_3s ?? raw.fastLt3s);
  const mid = nonNegInt(raw.mid_3_10s ?? raw.mid3To10s);
  const slow = nonNegInt(raw.slow_ge_10s ?? raw.slowGe10s);
  let n = nonNegInt(raw.n);
  if (n === 0) n = fast + mid + slow;
  if (n === 0 && fast === 0 && mid === 0 && slow === 0) return null;
  return {
    fast_lt_3s: fast,
    mid_3_10s: mid,
    slow_ge_10s: slow,
    n,
  };
}

/** Omit / null from Intake → keep previous; explicit object replaces. */
function keepOrReplace(nextRaw, prevVal, normalize) {
  if (nextRaw == null) return prevVal ?? null;
  return normalize(nextRaw);
}

export function writeDayBatch(
  uuid,
  { today, curriculum_id, day, by_question, by_format, schedule, latency } = {}
) {
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
  const byQ = keepOrReplace(
    by_question,
    normalizeByQuestion(prev?.by_question),
    normalizeByQuestion
  );
  const byFmt = keepOrReplace(
    by_format,
    normalizeByFormat(prev?.by_format),
    normalizeByFormat
  );
  const sched = keepOrReplace(
    schedule,
    normalizeSchedule(prev?.schedule),
    normalizeSchedule
  );
  const lat = keepOrReplace(latency, normalizeLatency(prev?.latency), normalizeLatency);
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
  if (byFmt) rec.by_format = byFmt;
  if (sched) rec.schedule = sched;
  if (lat) rec.latency = lat;

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
