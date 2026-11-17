/**
 * Ephemeral job queue on PVC/local disk — not the Relay tip.
 * Always-on Intake writes here; batch drains and commits tip.
 */
import { randomUUID } from "node:crypto";
import {
  mkdirSync,
  writeFileSync,
  readFileSync,
  existsSync,
  readdirSync,
  renameSync,
  unlinkSync,
} from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));

function queueRoot() {
  return process.env.QUIZGATE_QUEUE_DIR || join(__dirname, "../queue");
}

function binDir(state) {
  const d = join(queueRoot(), state);
  mkdirSync(d, { recursive: true });
  return d;
}

export function enqueue(job) {
  const id = job.id || randomUUID();
  const now = Date.now();
  const record = {
    ...job,
    id,
    state: "pending",
    created_at_ms: job.created_at_ms || now,
    updated_at_ms: now,
  };
  const fp = join(binDir("pending"), `${id}.json`);
  writeFileSync(fp, JSON.stringify(record, null, 2));
  return record;
}

export function listPending(limit = 32) {
  const dir = binDir("pending");
  const names = readdirSync(dir)
    .filter((n) => n.endsWith(".json"))
    .sort();
  const out = [];
  for (const name of names.slice(0, limit)) {
    out.push(JSON.parse(readFileSync(join(dir, name), "utf8")));
  }
  return out;
}

export function leaseJob(id, workerId = "batch") {
  const src = join(binDir("pending"), `${id}.json`);
  if (!existsSync(src)) return null;
  let job;
  try {
    job = JSON.parse(readFileSync(src, "utf8"));
  } catch {
    return null;
  }
  job.state = "leased";
  job.leased_by = workerId;
  job.leased_at_ms = Date.now();
  job.updated_at_ms = job.leased_at_ms;
  const dest = join(binDir("leased"), `${id}.json`);
  try {
    renameSync(src, dest);
    writeFileSync(dest, JSON.stringify(job, null, 2));
  } catch {
    if (!existsSync(src)) return null;
    writeFileSync(dest, JSON.stringify(job, null, 2));
    try {
      unlinkSync(src);
    } catch {
      /* already moved */
    }
  }
  return job;
}

export function completeJob(id, result = {}) {
  const src = join(binDir("leased"), `${id}.json`);
  if (!existsSync(src)) return null;
  const job = JSON.parse(readFileSync(src, "utf8"));
  job.state = "done";
  job.result = result;
  job.updated_at_ms = Date.now();
  const dest = join(binDir("done"), `${id}.json`);
  writeFileSync(dest, JSON.stringify(job, null, 2));
  unlinkSync(src);
  return job;
}

export function pendingCount() {
  return readdirSync(binDir("pending")).filter((n) => n.endsWith(".json")).length;
}

export { queueRoot };
