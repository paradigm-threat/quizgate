/**
 * QuizGate inbound mail — same Maildir backend as relay-ops Inbox.
 *
 * Canonical helper: relay-gateway/scripts/maildir_inbox.py
 * Vendored copy:    data-plane/lib/maildir_inbox.py (keep in sync)
 *
 * Access:
 *   - local: QUIZGATE_MAIL_MODE=local (or auto when Maildir is readable)
 *   - ssh:   QUIZGATE_MAIL_MODE=ssh (fleet key → iad1; same as relay-ops)
 *
 * Inbox address (once MX is live): batch@quizgate.app (catch-all *@quizgate.app)
 */
import { spawnSync } from "node:child_process";
import { accessSync, constants, existsSync, readFileSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));

export const DEFAULT_MAIL_HOST = "iad1.relaygateway.net";
export const DEFAULT_MAILDIR = "/home/arl/Maildir";
export const QUIZGATE_INBOUND_ADDRESS = "batch@quizgate.app";
export const QUIZGATE_INBOUND_DOMAINS = ["quizgate.app", "mail.quizgate.app"];

/** UUID v4-ish token used as capability in admin email threads (future filter). */
export const UUID_IN_TEXT_RE =
  /\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\b/i;

function helperPath() {
  const env = process.env.QUIZGATE_MAIL_HELPER?.trim();
  if (env && existsSync(env)) return env;
  const vendored = join(__dirname, "maildir_inbox.py");
  if (existsSync(vendored)) return vendored;
  const relay = resolve(
    __dirname,
    "../../../../relay/relay-gateway/scripts/maildir_inbox.py",
  );
  if (existsSync(relay)) return relay;
  throw new Error("maildir_inbox.py not found (set QUIZGATE_MAIL_HELPER)");
}

function mailHost() {
  return process.env.QUIZGATE_MAIL_HOST?.trim() || DEFAULT_MAIL_HOST;
}

function maildir() {
  return process.env.QUIZGATE_MAIL_MAILDIR?.trim() || DEFAULT_MAILDIR;
}

function sshIdentity() {
  return (
    process.env.QUIZGATE_MAIL_SSH_IDENTITY?.trim() ||
    process.env.RELAY_OPS_SSH_IDENTITY?.trim() ||
    join(homedir(), ".ssh/id_ed25519_relay_gateway")
  );
}

function sshUser() {
  return process.env.QUIZGATE_MAIL_SSH_USER?.trim() || "ubuntu";
}

function canReadMaildir(root) {
  try {
    accessSync(root, constants.R_OK);
    return existsSync(join(root, "new")) || existsSync(join(root, "cur"));
  } catch {
    return false;
  }
}

export function resolveMailMode() {
  const mode = (process.env.QUIZGATE_MAIL_MODE || "auto").trim().toLowerCase();
  if (mode === "local" || mode === "ssh") return mode;
  return canReadMaildir(maildir()) ? "local" : "ssh";
}

function parseJsonOrThrow(stdout, label) {
  const text = String(stdout || "").trim();
  if (!text) throw new Error(`${label}: empty stdout`);
  try {
    return JSON.parse(text);
  } catch {
    throw new Error(`${label}: bad JSON: ${text.slice(0, 200)}`);
  }
}

function shellQuote(s) {
  return `'${String(s).replace(/'/g, `'\"'\"'`)}'`;
}

export function validateMailId(id) {
  if (!id || id.length > 200) throw new Error("invalid mail id");
  if (id.includes("/") || id.includes("\\") || id.includes("..") || id.includes("\0")) {
    throw new Error("invalid mail id");
  }
  if (![...id].every((c) => /[A-Za-z0-9.\-_:]/.test(c))) {
    throw new Error("invalid mail id");
  }
  return true;
}

function runLocal(cmd, id) {
  const args = [helperPath(), cmd, maildir()];
  if (id) args.push(id);
  const r = spawnSync("python3", args, { encoding: "utf8", maxBuffer: 8 * 1024 * 1024 });
  if (r.status !== 0) {
    const err = (r.stderr || r.stdout || "").trim() || `exit ${r.status}`;
    const e = new Error(`local mail ${cmd}: ${err}`);
    e.code = r.status;
    throw e;
  }
  return parseJsonOrThrow(r.stdout, `local ${cmd}`);
}

function runSsh(cmd, id) {
  if (id) validateMailId(id);
  const enc = Buffer.from(readFileSync(helperPath())).toString("base64");
  let remote =
    `sudo python3 -c 'import base64,sys; code=base64.standard_b64decode(sys.argv[1]); ` +
    `sys.argv=["maildir_inbox.py"]+sys.argv[2:]; exec(code.decode())' ` +
    `${shellQuote(enc)} ${shellQuote(cmd)} ${shellQuote(maildir())}`;
  if (id) remote += ` ${shellQuote(id)}`;
  const host = `${sshUser()}@${mailHost()}`;
  const args = [
    "-o",
    "BatchMode=yes",
    "-o",
    "StrictHostKeyChecking=accept-new",
    "-i",
    sshIdentity(),
    host,
    remote,
  ];
  const r = spawnSync("ssh", args, { encoding: "utf8", maxBuffer: 8 * 1024 * 1024 });
  if (r.status !== 0) {
    const err = (r.stderr || r.stdout || "").trim() || `exit ${r.status}`;
    const e = new Error(`ssh mail ${cmd}: ${err}`);
    e.code = r.status;
    throw e;
  }
  return parseJsonOrThrow(r.stdout, `ssh ${cmd}`);
}

function run(cmd, id) {
  return resolveMailMode() === "local" ? runLocal(cmd, id) : runSsh(cmd, id);
}

export function listMessages() {
  return run("list");
}

export function getMessage(id) {
  return run("get", id);
}

export function markRead(id) {
  return run("mark-read", id);
}

/** True if To/subject looks like QuizGate inbound. */
export function isQuizgateAddressed(item) {
  const hay = `${item?.to || ""} ${item?.subject || ""}`.toLowerCase();
  return QUIZGATE_INBOUND_DOMAINS.some((d) => hay.includes(d.toLowerCase()));
}

export function extractUuids(...texts) {
  const found = new Set();
  for (const t of texts) {
    if (!t) continue;
    const re = new RegExp(UUID_IN_TEXT_RE.source, "gi");
    let m;
    while ((m = re.exec(String(t)))) found.add(m[0].toLowerCase());
  }
  return [...found];
}

/**
 * List messages for QuizGate batch polling.
 * @param {{ unreadOnly?: boolean, quizgateOnly?: boolean, withBody?: boolean }} [opts]
 */
export function checkInbox(opts = {}) {
  const unreadOnly = opts.unreadOnly !== false;
  const quizgateOnly = opts.quizgateOnly === true;
  const withBody = opts.withBody === true;
  let items = listMessages();
  if (unreadOnly) items = items.filter((m) => m.unread);
  if (quizgateOnly) items = items.filter(isQuizgateAddressed);
  if (!withBody) {
    return {
      mode: resolveMailMode(),
      maildir: maildir(),
      address: QUIZGATE_INBOUND_ADDRESS,
      count: items.length,
      messages: items,
    };
  }
  const messages = items.map((m) => {
    const full = getMessage(m.id);
    const uuids = extractUuids(full.subject, full.body, full.to, m.to);
    return { ...full, uuids };
  });
  return {
    mode: resolveMailMode(),
    maildir: maildir(),
    address: QUIZGATE_INBOUND_ADDRESS,
    count: messages.length,
    messages,
  };
}

/** Future: turn UUID-bearing admin mail into admin_settings / API decisions. */
export function planAdminEmailAction(message) {
  const uuids = extractUuids(message?.subject, message?.body, message?.to);
  if (!uuids.length) {
    return { actionable: false, reason: "no_uuid_in_thread" };
  }
  return {
    actionable: true,
    reason: "uuid_present",
    user_uuid: uuids[0],
    uuids,
    // Not implemented: parse commands → same decisions as human admin / Intake API.
    next: "enqueue_admin_settings_or_intake_admin_api",
  };
}
