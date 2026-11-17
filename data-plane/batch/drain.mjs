#!/usr/bin/env node
/**
 * QuizGate batch SI — sole permanent writer of quizgate-users tip.
 * Drains Intake PVC queue; commits profile/events/days as quizgate-batch.
 */
import { readFileSync, existsSync, mkdirSync, writeFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { listPending, leaseJob, completeJob, pendingCount } from "../lib/queue.mjs";
import {
  SERVICE_ID,
  isUuid,
  loadProfile,
  emptyProfile,
  commitUserFiles,
  publishRemotes,
  readDays,
} from "../lib/userStore.mjs";
import { checkInbox, planAdminEmailAction } from "../lib/inboundMail.mjs";

const __dirname = dirname(fileURLToPath(import.meta.url));

function dayKey(ts = Date.now()) {
  return new Date(ts).toISOString().slice(0, 10);
}

function scoreReadiness(today, recentQuiz) {
  const asked = Number(today?.asked || 0);
  const right = Number(today?.right || 0);
  const rate = asked > 0 ? right / asked : 0;
  const reasons = [];
  let score = rate;
  if (asked >= 20 && rate >= 0.85) {
    reasons.push("high_accuracy_volume");
    score = Math.min(1, score + 0.1);
  } else if (asked < 5) {
    reasons.push("insufficient_samples");
    score = Math.min(score, 0.3);
  } else {
    reasons.push("building_mastery");
  }
  const next =
    asked >= 30 && rate >= 0.9 ? "basic-math-advanced" : null;
  if (next) reasons.push("ready_next_curriculum");
  return { score, next_curriculum_id: next, reasons, recent_n: recentQuiz?.length || 0 };
}

function mergeDayLine(existingLines, today) {
  if (!today || today.asked == null) return existingLines.join("\n") + (existingLines.length ? "\n" : "");
  const key = today.day || dayKey();
  const map = new Map();
  for (const line of existingLines) {
    try {
      const o = JSON.parse(line);
      if (o.day) map.set(o.day, o);
    } catch {
      /* skip */
    }
  }
  map.set(key, {
    day: key,
    asked: today.asked,
    right: today.right,
    wrong: today.wrong,
    skipped: today.skipped,
    updated_at_ms: Date.now(),
  });
  return [...map.values()]
    .sort((a, b) => String(a.day).localeCompare(String(b.day)))
    .map((o) => JSON.stringify(o))
    .join("\n") + "\n";
}

function applyProgressSync(job) {
  const uuid = job.user_uuid;
  if (!isUuid(uuid)) throw new Error("invalid_uuid");
  const p = job.payload || {};
  const profile = loadProfile(uuid) || emptyProfile(uuid);
  const readiness = scoreReadiness(p.today, p.recent_quiz);
  profile.updated_at_ms = Date.now();
  profile.readiness = readiness;
  if (p.curriculum_id) profile.curriculum_id = p.curriculum_id;
  if (readiness.next_curriculum_id) {
    profile.content_hints = [
      ...(profile.content_hints || []).slice(-8),
      `consider:${readiness.next_curriculum_id}`,
    ];
  }
  // Frequency: slow down if struggling, keep default if healthy
  const asked = Number(p.today?.asked || 0);
  const right = Number(p.today?.right || 0);
  const rate = asked > 0 ? right / asked : 1;
  if (asked >= 10 && rate < 0.5) {
    profile.settings.interval_ms = 2_700_000; // 45m
    profile.settings.jitter_ms = 900_000;
    readiness.reasons.push("slow_frequency_low_accuracy");
  } else if (p.interval_ms && p.jitter_ms) {
    profile.settings.interval_ms = Number(p.interval_ms);
    profile.settings.jitter_ms = Number(p.jitter_ms);
  }
  if (p.performance?.preference) {
    profile.settings.performance_preference = p.performance.preference;
  }

  const eventLines = (p.recent_quiz || [])
    .slice(-50)
    .map((line) => {
      if (typeof line === "string") {
        try {
          return JSON.stringify({ ...JSON.parse(line), ingested_at_ms: Date.now() });
        } catch {
          return JSON.stringify({ raw: line, ingested_at_ms: Date.now() });
        }
      }
      return JSON.stringify({ ...line, ingested_at_ms: Date.now() });
    })
    .join("\n");

  const daysBody = mergeDayLine(readDays(uuid), p.today ? { ...p.today, day: dayKey() } : null);

  const writes = {
    "profile.json": JSON.stringify(profile, null, 2) + "\n",
  };
  if (eventLines) {
    const prev = existsSync(
      join(process.env.QUIZGATE_USERS_ROOT || join(__dirname, "../quizgate-users"), "users", uuid, "events.jsonl"),
    )
      ? readFileSync(
          join(
            process.env.QUIZGATE_USERS_ROOT || join(__dirname, "../quizgate-users"),
            "users",
            uuid,
            "events.jsonl",
          ),
          "utf8",
        )
      : "";
    const merged = (prev + (prev.endsWith("\n") || !prev ? "" : "\n") + eventLines + "\n")
      .split("\n")
      .filter(Boolean)
      .slice(-2000)
      .join("\n") + "\n";
    writes["events.jsonl"] = merged;
  }
  if (daysBody.trim()) writes["days.jsonl"] = daysBody;

  commitUserFiles(uuid, writes, `batch: progress_sync ${uuid}`, SERVICE_ID);
  return { readiness, curriculum_id: profile.curriculum_id, settings: profile.settings };
}

function applyIntakeGather(job) {
  /** Lightweight skill: note gather-only; may add content_hints without inventing PII. */
  const uuid = job.user_uuid;
  const profile = loadProfile(uuid) || emptyProfile(uuid);
  profile.updated_at_ms = Date.now();
  profile.content_hints = [
    ...(profile.content_hints || []).slice(-10),
    "intake_gather_ok",
  ];
  commitUserFiles(
    uuid,
    { "profile.json": JSON.stringify(profile, null, 2) + "\n" },
    `batch: intake_gather ${uuid}`,
    SERVICE_ID,
  );
  return { ok: true };
}

function applyContentResearch(job) {
  const uuid = job.user_uuid;
  const profile = loadProfile(uuid) || emptyProfile(uuid);
  profile.updated_at_ms = Date.now();
  profile.content_hints = [
    ...(profile.content_hints || []).slice(-10),
    job.kind === "photo" ? "safe_study_visual" : "safe_study_audio",
    "original_safe_content_stub",
  ];
  commitUserFiles(
    uuid,
    { "profile.json": JSON.stringify(profile, null, 2) + "\n" },
    `batch: content_research ${uuid}`,
    SERVICE_ID,
  );
  return { ok: true, hints: profile.content_hints.slice(-3) };
}

function applyRemoteRegister(job) {
  const uuid = job.user_uuid;
  if (!isUuid(uuid)) throw new Error("invalid_uuid");
  const profile = loadProfile(uuid) || emptyProfile(uuid);
  profile.updated_at_ms = Date.now();
  profile.remote_ai = Boolean(job.ai_enabled);
  profile.admin = {
    ai_enabled: Boolean(job.ai_enabled),
    human_enabled: Boolean(job.human_enabled && job.email_hash),
    email_hash: job.email_hash || profile.admin?.email_hash || null,
    updated_at_ms: Date.now(),
  };
  commitUserFiles(
    uuid,
    { "profile.json": JSON.stringify(profile, null, 2) + "\n" },
    `batch: remote_register ${uuid}`,
    SERVICE_ID,
  );
  return { ok: true, admin: profile.admin };
}

function applyAdminSettings(job) {
  const uuid = job.user_uuid;
  if (!isUuid(uuid)) throw new Error("invalid_uuid");
  const profile = loadProfile(uuid) || emptyProfile(uuid);
  const patch = job.patch || {};
  profile.updated_at_ms = Date.now();
  if (patch.curriculum_id) profile.curriculum_id = String(patch.curriculum_id);
  profile.settings = profile.settings || {};
  if (patch.interval_ms != null) profile.settings.interval_ms = Number(patch.interval_ms);
  if (patch.jitter_ms != null) profile.settings.jitter_ms = Number(patch.jitter_ms);
  if (patch.performance_preference) {
    profile.settings.performance_preference = String(patch.performance_preference);
  }
  if (Array.isArray(patch.content_hints)) {
    profile.content_hints = patch.content_hints.slice(-20);
  }
  profile.admin = {
    ...(profile.admin || {}),
    last_human_patch_at_ms: Date.now(),
    last_actor: job.actor || "human_admin",
  };
  commitUserFiles(
    uuid,
    { "profile.json": JSON.stringify(profile, null, 2) + "\n" },
    `batch: admin_settings ${uuid}`,
    SERVICE_ID,
  );
  return { ok: true, settings: profile.settings, curriculum_id: profile.curriculum_id };
}

export async function drainOnce(limit = 8) {
  const pending = listPending(limit);
  const results = [];
  for (const job of pending) {
    const leased = leaseJob(job.id, SERVICE_ID);
    if (!leased) continue;
    try {
      let result;
      if (leased.type === "progress_sync") result = applyProgressSync(leased);
      else if (leased.type === "intake_gather") result = applyIntakeGather(leased);
      else if (leased.type === "realtime_followup") result = applyContentResearch(leased);
      else if (leased.type === "remote_register") result = applyRemoteRegister(leased);
      else if (leased.type === "admin_settings") result = applyAdminSettings(leased);
      else result = { skipped: true, type: leased.type };
      completeJob(leased.id, result);
      results.push({ id: leased.id, ok: true, result });
    } catch (err) {
      completeJob(leased.id, { ok: false, error: String(err?.message || err) });
      results.push({ id: leased.id, ok: false, error: String(err?.message || err) });
    }
  }
  if (results.some((r) => r.ok) && process.env.QUIZGATE_PUBLISH_REMOTES) {
    try {
      publishRemotes();
    } catch (err) {
      console.error("publish_failed", err?.message || err);
    }
  }
  return { drained: results.length, pending_left: pendingCount(), results };
}

/**
 * Optional inbox poll (same Maildir as relay-ops). Does not apply admin changes yet —
 * only reports UUID-bearing messages for future admin-email-to-batch.
 */
export function pollInboundInbox() {
  if (process.env.QUIZGATE_BATCH_CHECK_INBOX !== "1") return null;
  try {
    const inbox = checkInbox({
      unreadOnly: true,
      quizgateOnly: process.env.QUIZGATE_BATCH_INBOX_QUIZGATE_ONLY !== "0",
      withBody: true,
    });
    const plans = (inbox.messages || []).map((m) => ({
      id: m.id,
      subject: m.subject,
      ...planAdminEmailAction(m),
    }));
    return { ...inbox, plans, applied: false };
  } catch (err) {
    return { ok: false, error: String(err?.message || err), applied: false };
  }
}

async function main() {
  const limit = Number(process.env.QUIZGATE_BATCH_LIMIT || 16);
  const out = await drainOnce(limit);
  const inbox = pollInboundInbox();
  if (inbox) out.inbound_mail = inbox;
  console.log(JSON.stringify(out, null, 2));
  if (process.env.QUIZGATE_BATCH_DECOMMISSION === "1" && out.pending_left === 0) {
    console.log("batch_idle_decommission_signal");
  }
}

const isMain =
  process.argv[1] &&
  fileURLToPath(import.meta.url) === process.argv[1];
if (isMain) {
  main().catch((e) => {
    console.error(e);
    process.exit(1);
  });
}
