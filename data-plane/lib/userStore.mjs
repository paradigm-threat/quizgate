/**
 * quizgate-users tip store — text-only commits as quizgate-batch.
 * Intake must NOT call commitProfile / appendEvents; batch is sole writer.
 */
import { randomUUID } from "node:crypto";
import {
  mkdirSync,
  readFileSync,
  writeFileSync,
  existsSync,
  appendFileSync,
  readdirSync,
} from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { execFileSync } from "node:child_process";

const __dirname = dirname(fileURLToPath(import.meta.url));
const DEFAULT_ROOT = join(__dirname, "../quizgate-users");

export const SERVICE_ID = "quizgate-batch";
export const SERVICE_EMAIL = "quizgate-batch@quizgate.app";
export const BRANCH = "users";

const BINARY_RE =
  /\.(bin|wav|mp3|m4a|aac|jpg|jpeg|png|gif|webp|mp4|mov|pdf|zip)$/i;

export function usersRoot() {
  return process.env.QUIZGATE_USERS_ROOT || DEFAULT_ROOT;
}

function ensureRepo() {
  const root = usersRoot();
  if (!existsSync(join(root, ".git"))) {
    mkdirSync(join(root, "users"), { recursive: true });
    execFileSync("git", ["init", "-b", "main"], { cwd: root });
    writeFileSync(join(root, "users", ".gitkeep"), "");
    execFileSync("git", ["add", "-A"], { cwd: root });
    gitCommit(root, "chore: init quizgate-users");
    try {
      execFileSync("git", ["branch", BRANCH], { cwd: root });
    } catch {
      /* exists */
    }
  }
  return root;
}

function git(root, args) {
  return execFileSync("git", args, {
    cwd: root,
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
  });
}

function gitCommit(root, message) {
  execFileSync(
    "git",
    [
      "-c",
      `user.name=${SERVICE_ID}`,
      "-c",
      `user.email=${SERVICE_EMAIL}`,
      "commit",
      "-m",
      message,
    ],
    { cwd: root, stdio: ["ignore", "pipe", "pipe"] },
  );
}

function checkoutUsers(root) {
  const branch = git(root, ["rev-parse", "--abbrev-ref", "HEAD"]).trim();
  if (branch !== BRANCH) {
    try {
      git(root, ["checkout", BRANCH]);
    } catch {
      git(root, ["checkout", "-b", BRANCH]);
    }
  }
}

function userDir(uuid) {
  return join(ensureRepo(), "users", uuid);
}

export function isUuid(id) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
    String(id || ""),
  );
}

export function emptyProfile(uuid) {
  const now = Date.now();
  return {
    schema: "quizgate.user.profile.v1",
    user_uuid: uuid,
    updated_at_ms: now,
    curriculum_id: "basic-math",
    readiness: { score: 0, next_curriculum_id: null, reasons: [] },
    settings: {
      interval_ms: 2_400_000, // 40m
      jitter_ms: 600_000, // ±10m → 30–50m window
      performance_preference: "auto",
      time_earn_cap_minutes: 120,
    },
    content_hints: [],
    remote_ai: false,
    admin: {
      ai_enabled: false,
      human_enabled: false,
      email_hash: null,
    },
  };
}

export function loadProfile(uuid) {
  if (!isUuid(uuid)) return null;
  const p = join(userDir(uuid), "profile.json");
  if (!existsSync(p)) return null;
  return JSON.parse(readFileSync(p, "utf8"));
}

export function readEventsTail(uuid, maxLines = 200) {
  const p = join(userDir(uuid), "events.jsonl");
  if (!existsSync(p)) return [];
  const lines = readFileSync(p, "utf8").split("\n").filter(Boolean);
  return lines.slice(-maxLines);
}

export function readDays(uuid) {
  const p = join(userDir(uuid), "days.jsonl");
  if (!existsSync(p)) return [];
  return readFileSync(p, "utf8").split("\n").filter(Boolean);
}

/**
 * Sole tip writer entry — author must be quizgate-batch.
 */
export function commitUserFiles(uuid, writes, message, author = SERVICE_ID) {
  if (author !== SERVICE_ID) {
    throw new PermissionError("only quizgate-batch may commit tip files");
  }
  if (!isUuid(uuid)) throw new Error("invalid_uuid");
  const root = ensureRepo();
  checkoutUsers(root);
  const dir = userDir(uuid);
  mkdirSync(dir, { recursive: true });
  for (const [rel, content] of Object.entries(writes)) {
    if (BINARY_RE.test(rel) || (typeof content === "string" && content.includes("\0"))) {
      throw new Error("binaries_rejected");
    }
    const dest = join(dir, rel);
    mkdirSync(dirname(dest), { recursive: true });
    writeFileSync(dest, content);
  }
  git(root, ["add", "-A", `users/${uuid}`]);
  const status = git(root, ["status", "--porcelain"]).trim();
  if (!status) return false;
  gitCommit(root, message || `batch: update ${uuid}`);
  const an = git(root, ["log", "-1", "--format=%an"]).trim();
  if (an !== SERVICE_ID) {
    throw new Error("commit_author_mismatch");
  }
  return true;
}

export function publishRemotes(remotes) {
  const root = ensureRepo();
  checkoutUsers(root);
  const list =
    remotes ||
    String(process.env.QUIZGATE_PUBLISH_REMOTES || "")
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);
  for (const remote of list) {
    execFileSync("git", ["push", remote, BRANCH], {
      cwd: root,
      stdio: ["ignore", "pipe", "pipe"],
    });
  }
}

export function appendLocalJsonl(uuid, fileName, lineObj) {
  /** Queue-side helper for tests — does not commit. */
  const dir = userDir(uuid);
  mkdirSync(dir, { recursive: true });
  appendFileSync(join(dir, fileName), JSON.stringify(lineObj) + "\n");
}

export function listLocalUuidsOpsOnly() {
  const root = join(ensureRepo(), "users");
  if (!existsSync(root)) return [];
  return readdirSync(root).filter(isUuid);
}

class PermissionError extends Error {
  constructor(msg) {
    super(msg);
    this.name = "PermissionError";
  }
}

export { PermissionError, randomUUID };
