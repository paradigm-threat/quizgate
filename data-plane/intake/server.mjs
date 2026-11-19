#!/usr/bin/env node
/**
 * QuizGate always-on Intake — https://api.quizgate.app
 * Caches requests, realtime stubs, enqueue, admin 2FA.
 * Does NOT commit the Relay tip (batch is sole writer).
 */
import { createServer } from "node:http";
import { createHash, randomUUID } from "node:crypto";
import { mkdirSync, writeFileSync, existsSync, readFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { enqueue, pendingCount, listPending } from "../lib/queue.mjs";
import { cacheGet, cacheSet, cacheDedupePost } from "../lib/cache.mjs";
import { writeDayBatch } from "../lib/dayBatch.mjs";
import {
  isUuid,
  loadProfile,
  readEventsTail,
  readDays,
  emptyProfile,
} from "../lib/userStore.mjs";
import { hashEmailForUuid, normalizeEmail } from "../lib/emailHash.mjs";
import {
  requestOtp,
  verifyOtp,
  requireSessionForUuid,
  purgeExpiredAuth,
} from "../lib/adminAuth.mjs";
import { smtpConfigured as mailReady } from "../lib/mail.mjs";

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, "..");
const MEDIA_CACHE = process.env.QUIZGATE_CACHE_DIR || join(ROOT, ".data/media");
const PORT = Number(process.env.PORT || 8791);

mkdirSync(MEDIA_CACHE, { recursive: true });
setInterval(() => {
  try {
    purgeExpiredAuth();
  } catch {
    /* */
  }
}, 60_000).unref?.();

function json(res, status, body, extraHeaders = {}) {
  const payload = typeof body === "string" ? body : JSON.stringify(body);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "access-control-allow-origin": process.env.QUIZGATE_ADMIN_ORIGIN || "https://admin.quizgate.app",
    "access-control-allow-headers": "content-type, authorization",
    "access-control-allow-methods": "GET,POST,PATCH,OPTIONS",
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
    /^\/v1\/users\/([0-9a-fA-F-]{36})(?:\/(profile|events|days|sync|remote|realtime\/[a-z]+))?\/?$/,
  );
  return m ? { uuid: m[1], rest: m[2] || "profile" } : null;
}

async function handleRealtime(kind, buf, contentType) {
  if (kind === "photo" || kind === "vision") {
    const id = randomUUID();
    const ext = /png/i.test(contentType || "") ? ".png" : ".jpg";
    const fp = join(MEDIA_CACHE, `${id}${ext}`);
    writeFileSync(fp, buf);
    return {
      ok: true,
      kind,
      cache_uri: `cache://${id}${ext}`,
      analysis: { labels: ["quiz_worksheet_candidate"], note: "realtime_stub", safe: true },
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
    return { ok: true, kind, audio_uri: null, note: "tts_stub_no_audio" };
  }
  return { ok: false, error: "unknown_realtime_kind" };
}

const server = createServer(async (req, res) => {
  const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
  const path = url.pathname;

  if (req.method === "OPTIONS") {
    return json(res, 204, "");
  }

  if (req.method === "GET" && path === "/health") {
    return json(res, 200, {
      ok: true,
      service: "quizgate-intake",
      host: "api.quizgate.app",
      pending_jobs: pendingCount(),
      tip_writer: "quizgate-batch",
      smtp: mailReady(),
    });
  }

  if (req.method === "GET" && path === "/v1/queue/pending") {
    const secret = process.env.QUIZGATE_OPS_SECRET?.trim();
    if (secret && req.headers["x-ops-secret"] !== secret) {
      return json(res, 401, { error: "unauthorized" });
    }
    return json(res, 200, { jobs: listPending(Number(url.searchParams.get("limit") || 32)) });
  }

  // ---------- Admin auth (no enumeration) ----------
  if (req.method === "POST" && path === "/v1/admin/otp") {
    const raw = await readBody(req);
    let body;
    try {
      body = JSON.parse(raw.toString("utf8") || "{}");
    } catch {
      return json(res, 400, { error: "invalid_json" });
    }
    const out = await requestOtp(body.uuid, body.email);
    return json(res, out.status || (out.ok ? 200 : 400), out);
  }

  if (req.method === "POST" && path === "/v1/admin/verify") {
    const raw = await readBody(req);
    let body;
    try {
      body = JSON.parse(raw.toString("utf8") || "{}");
    } catch {
      return json(res, 400, { error: "invalid_json" });
    }
    const out = verifyOtp(body.otp_id, body.code);
    return json(res, out.status || (out.ok ? 200 : 401), out);
  }

  const adminStats = path.match(/^\/v1\/admin\/users\/([0-9a-fA-F-]{36})\/(stats|profile)$/);
  if (adminStats && isUuid(adminStats[1])) {
    const uuid = adminStats[1];
    const kind = adminStats[2];
    const auth = requireSessionForUuid(req.headers.authorization, uuid);
    if (!auth.ok) return json(res, auth.status, { error: auth.error });

    if (req.method === "GET" && kind === "stats") {
      const profile = loadProfile(uuid) || emptyProfile(uuid);
      return json(res, 200, {
        user_uuid: uuid,
        profile: {
          curriculum_id: profile.curriculum_id,
          readiness: profile.readiness,
          settings: profile.settings,
          admin: {
            ai_enabled: Boolean(profile.admin?.ai_enabled || profile.remote_ai),
            human_enabled: Boolean(profile.admin?.human_enabled),
            has_email: Boolean(profile.admin?.email_hash),
          },
          updated_at_ms: profile.updated_at_ms,
        },
        days: readDays(uuid),
        events: readEventsTail(uuid, 100),
      });
    }
    if (req.method === "GET" && kind === "profile") {
      const profile = loadProfile(uuid) || emptyProfile(uuid);
      return json(res, 200, profile);
    }
    if (req.method === "PATCH" && kind === "profile") {
      const raw = await readBody(req);
      let body;
      try {
        body = JSON.parse(raw.toString("utf8") || "{}");
      } catch {
        return json(res, 400, { error: "invalid_json" });
      }
      const job = enqueue({
        type: "admin_settings",
        user_uuid: uuid,
        actor: "human_admin",
        patch: {
          curriculum_id: body.curriculum_id,
          interval_ms: body.interval_ms,
          jitter_ms: body.jitter_ms,
          performance_preference: body.performance_preference,
          content_hints: body.content_hints,
        },
      });
      return json(res, 202, { ok: true, queued: true, job_id: job.id });
    }
  }

  const u = uuidFromPath(path);
  if (!u || !isUuid(u.uuid)) {
    if (path.startsWith("/v1/")) return json(res, 404, { error: "not_found" });
    return json(res, 404, { error: "not_found", hint: "https://api.quizgate.app/health" });
  }

  // --- GET profile / events / days ---
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

  // --- POST remote: enable AI + optional parent email (hash only on tip via batch) ---
  if (req.method === "POST" && u.rest === "remote") {
    const raw = await readBody(req);
    let payload;
    try {
      payload = JSON.parse(raw.toString("utf8") || "{}");
    } catch {
      return json(res, 400, { error: "invalid_json" });
    }
    const ai = payload.ai_enabled !== false && payload.remote_ai !== false;
    let emailHash = null;
    let human = false;
    if (payload.email) {
      try {
        emailHash = hashEmailForUuid(payload.email, u.uuid);
        human = true;
      } catch {
        return json(res, 400, { error: "invalid_email" });
      }
    }
    const job = enqueue({
      type: "remote_register",
      user_uuid: u.uuid,
      ai_enabled: ai,
      human_enabled: human,
      email_hash: emailHash,
      // never enqueue plaintext email
    });
    return json(res, 202, {
      ok: true,
      queued: true,
      job_id: job.id,
      human_enabled: human,
      ai_enabled: ai,
      admin_url: "https://admin.quizgate.app",
    });
  }

  // --- POST sync ---
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
    const curriculumId =
      payload.curriculumId ||
      payload.curriculum_id ||
      payload.prefs?.curriculum_id ||
      null;
    const today = payload.today || null;
    // Ephemeral day aggregate on cache PVC — never the Relay tip.
    let day_batch = null;
    try {
      day_batch = writeDayBatch(u.uuid, {
        today,
        curriculum_id: curriculumId,
      });
    } catch (err) {
      return json(res, 500, {
        error: "day_batch_write_failed",
        detail: String(err?.message || err),
      });
    }
    const job = enqueue({
      type: "progress_sync",
      user_uuid: u.uuid,
      payload: {
        schema: payload.schemaVersion || payload.schema || 3,
        today,
        recent_quiz: payload.recentQuiz || payload.recent_quiz || [],
        recent_schedule: payload.recentSchedule || payload.recent_schedule || [],
        performance: payload.performance || null,
        curriculum_id: curriculumId,
        interval_ms: payload.intervalMs || payload.interval_ms || null,
        jitter_ms: payload.jitterMs || payload.jitter_ms || null,
        built_at: payload.builtAt || Date.now(),
      },
      skills: ["readiness", "frequency", "curriculum"],
    });
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
      day_batch: {
        day: day_batch.day,
        sync_count: day_batch.sync_count,
      },
    };
    cacheSet("sync", `${u.uuid}:${hash}`, result);
    return json(res, 202, result);
  }

  if (req.method === "POST" && String(u.rest).startsWith("realtime/")) {
    const kind = String(u.rest).slice("realtime/".length);
    const raw = await readBody(req);
    const out = await handleRealtime(kind, raw, req.headers["content-type"]);
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
  console.log(`quizgate-intake listening :${PORT} (api.quizgate.app) smtp=${mailReady()}`);
});
