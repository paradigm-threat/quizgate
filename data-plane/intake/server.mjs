#!/usr/bin/env node
/**
 * QuizGate always-on Intake — https://api.quizgate.app
 * Caches requests, handles realtime stubs, enqueues jobs.
 * Does NOT commit the Relay tip (batch is sole writer).
 */
import { createServer } from "node:http";
import { createHash, randomUUID } from "node:crypto";
import { mkdirSync, writeFileSync, existsSync, readFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { enqueue, pendingCount, listPending } from "../lib/queue.mjs";
import { cacheGet, cacheSet, cacheDedupePost } from "../lib/cache.mjs";
import { isUuid, loadProfile, readEventsTail, readDays, emptyProfile } from "../lib/userStore.mjs";

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, "..");
const MEDIA_CACHE = process.env.QUIZGATE_CACHE_DIR || join(ROOT, ".data/media");
const PORT = Number(process.env.PORT || 8791);

mkdirSync(MEDIA_CACHE, { recursive: true });

function json(res, status, body, extraHeaders = {}) {
  const payload = typeof body === "string" ? body : JSON.stringify(body);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    ...extraHeaders,
  });
  res.end(payload);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on("data", (c) => chunks.push(c));
    req.on("end", () => resolve(Buffer.concat(chunks)));
    req.on("error", reject);
  });
}

function uuidFromPath(urlPath) {
  const m = String(urlPath).match(
    /^\/v1\/users\/([0-9a-fA-F-]{36})(?:\/(profile|events|days|sync|realtime\/[a-z]+))?\/?$/,
  );
  return m ? { uuid: m[1], rest: m[2] || "profile" } : null;
}

async function handleRealtime(kind, buf, contentType) {
  /** Lightweight stubs — replace with SI backends; never tip-commit. */
  if (kind === "photo" || kind === "vision") {
    const id = randomUUID();
    const ext = /png/i.test(contentType || "") ? ".png" : ".jpg";
    const fp = join(MEDIA_CACHE, `${id}${ext}`);
    writeFileSync(fp, buf);
    return {
      ok: true,
      kind,
      cache_uri: `cache://${id}${ext}`,
      analysis: {
        labels: ["quiz_worksheet_candidate"],
        note: "realtime_stub",
        safe: true,
      },
    };
  }
  if (kind === "transcribe" || kind === "asr") {
    return {
      ok: true,
      kind: "transcribe",
      text: buf.length ? "[realtime stub transcript]" : "",
      model: "stub",
    };
  }
  if (kind === "tts" || kind === "ttv") {
    return {
      ok: true,
      kind,
      audio_uri: null,
      note: "tts_stub_no_audio",
    };
  }
  return { ok: false, error: "unknown_realtime_kind" };
}

const server = createServer(async (req, res) => {
  const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
  const path = url.pathname;

  if (req.method === "GET" && path === "/health") {
    return json(res, 200, {
      ok: true,
      service: "quizgate-intake",
      host: "api.quizgate.app",
      pending_jobs: pendingCount(),
      tip_writer: "quizgate-batch",
    });
  }

  if (req.method === "GET" && path === "/v1/queue/pending") {
    const secret = process.env.QUIZGATE_OPS_SECRET?.trim();
    if (secret && req.headers["x-ops-secret"] !== secret) {
      return json(res, 401, { error: "unauthorized" });
    }
    return json(res, 200, { jobs: listPending(Number(url.searchParams.get("limit") || 32)) });
  }

  const u = uuidFromPath(path);
  if (!u || !isUuid(u.uuid)) {
    if (path.startsWith("/v1/")) return json(res, 404, { error: "not_found" });
    return json(res, 404, { error: "not_found", hint: "https://api.quizgate.app/health" });
  }

  // --- GET profile / events / days (cached tip reads) ---
  if (req.method === "GET") {
    const cacheKey = `${u.uuid}:${u.rest}`;
    const cached = cacheGet("get", cacheKey, 15_000);
    if (cached) return json(res, 200, cached, { "x-quizgate-cache": "hit" });

    if (u.rest === "profile") {
      const profile = loadProfile(u.uuid) || emptyProfile(u.uuid);
      cacheSet("get", cacheKey, profile);
      return json(res, 200, profile, { "x-quizgate-cache": "miss" });
    }
    if (u.rest === "events") {
      const body = { user_uuid: u.uuid, lines: readEventsTail(u.uuid) };
      cacheSet("get", cacheKey, body);
      return json(res, 200, body);
    }
    if (u.rest === "days") {
      const body = { user_uuid: u.uuid, lines: readDays(u.uuid) };
      cacheSet("get", cacheKey, body);
      return json(res, 200, body);
    }
  }

  // --- POST sync: enqueue progress (no tip write) ---
  if (req.method === "POST" && u.rest === "sync") {
    const raw = await readBody(req);
    const hash = createHash("sha256").update(raw).digest("hex");
    const dedupe = cacheDedupePost("sync", `${u.uuid}:${hash}`, 30_000);
    if (dedupe.duplicate) {
      return json(res, 200, { ...dedupe.value, deduped: true });
    }
    let payload;
    try {
      payload = JSON.parse(raw.toString("utf8") || "{}");
    } catch {
      return json(res, 400, { error: "invalid_json" });
    }
    if (payload.user_uuid && payload.user_uuid !== u.uuid) {
      return json(res, 400, { error: "uuid_mismatch" });
    }
    const job = enqueue({
      type: "progress_sync",
      user_uuid: u.uuid,
      payload: {
        schema: payload.schemaVersion || payload.schema || 2,
        today: payload.today || null,
        recent_quiz: payload.recentQuiz || payload.recent_quiz || [],
        recent_schedule: payload.recentSchedule || payload.recent_schedule || [],
        performance: payload.performance || null,
        curriculum_id: payload.curriculumId || payload.curriculum_id || null,
        interval_ms: payload.intervalMs || payload.interval_ms || null,
        jitter_ms: payload.jitterMs || payload.jitter_ms || null,
        built_at: payload.builtAt || Date.now(),
      },
      skills: ["readiness", "frequency", "curriculum"],
    });
    // Also enqueue lightweight intake skill ticket for gathering context
    enqueue({
      type: "intake_gather",
      user_uuid: u.uuid,
      parent_job: job.id,
      skills: ["ticket_gather"],
      note: "lightweight_intake",
    });
    const result = {
      ok: true,
      queued: true,
      job_id: job.id,
      pending_jobs: pendingCount(),
    };
    cacheSet("sync", `${u.uuid}:${hash}`, result);
    return json(res, 202, result);
  }

  // --- POST realtime ---
  if (req.method === "POST" && String(u.rest).startsWith("realtime/")) {
    const kind = String(u.rest).slice("realtime/".length);
    const raw = await readBody(req);
    const out = await handleRealtime(kind, raw, req.headers["content-type"]);
    // Optional: enqueue research/content job when client asks
    if (url.searchParams.get("enqueue") === "1" && out.ok) {
      const job = enqueue({
        type: "realtime_followup",
        user_uuid: u.uuid,
        kind,
        cache_uri: out.cache_uri || null,
        skills: ["content", "research"],
      });
      out.job_id = job.id;
    }
    return json(res, out.ok ? 200 : 400, out);
  }

  return json(res, 405, { error: "method_not_allowed" });
});

server.listen(PORT, () => {
  console.log(`quizgate-intake listening :${PORT} (api.quizgate.app)`);
});
