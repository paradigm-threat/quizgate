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
  return join(batchesRoot(), day, `${uuid}.json`);
}

function normalizeToday(today) {
  if (!today || typeof today !== "object") {
    return { asked: 0, right: 0, wrong: 0, skipped: 0 };
  }
  return {
    asked: Number(today.asked || 0),
    right: Number(today.right || 0),
    wrong: Number(today.wrong || 0),
    skipped: Number(today.skipped || 0),
  };
}

/**
 * Upsert one privacy-safe aggregate per user per UTC day (last-write-wins counts).
 * Does not store recent_quiz / event trails.
 */
export function writeDayBatch(uuid, { today, curriculum_id, day } = {}) {
  if (!isUuid(uuid)) throw new Error("invalid_uuid");
  const dayKey = day || utcDayKey();
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
  const rec = {
    schema: DAY_BATCH_SCHEMA,
    user_uuid: uuid,
    day: dayKey,
    curriculum_id:
      curriculum_id != null && String(curriculum_id)
        ? String(curriculum_id)
        : prev?.curriculum_id || null,
    today: normalizeToday(today != null ? today : prev?.today),
    received_at_ms: now,
    sync_count: Number(prev?.sync_count || 0) + 1,
  };

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
