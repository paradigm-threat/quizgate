#!/usr/bin/env node
/**
 * Poll Relay fleet inbound Maildir the same way relay-ops Inbox does.
 *
 * Usage:
 *   node batch/checkInbox.mjs
 *   node batch/checkInbox.mjs --all --body
 *   QUIZGATE_MAIL_MODE=ssh node batch/checkInbox.mjs --quizgate
 *
 * Does not mutate tip. Future: UUID-in-body → admin_settings (see planAdminEmailAction).
 */
import { fileURLToPath } from "node:url";
import {
  checkInbox,
  planAdminEmailAction,
  QUIZGATE_INBOUND_ADDRESS,
} from "../lib/inboundMail.mjs";

function parseArgs(argv) {
  const opts = {
    unreadOnly: true,
    quizgateOnly: false,
    withBody: false,
    plan: false,
  };
  for (const a of argv) {
    if (a === "--all") opts.unreadOnly = false;
    else if (a === "--quizgate") opts.quizgateOnly = true;
    else if (a === "--body") opts.withBody = true;
    else if (a === "--plan") {
      opts.withBody = true;
      opts.plan = true;
    } else if (a === "--help" || a === "-h") {
      console.log(`Usage: node batch/checkInbox.mjs [--all] [--quizgate] [--body] [--plan]
Inbox: ${QUIZGATE_INBOUND_ADDRESS} (Relay OpenSMTPD Maildir on iad1)`);
      process.exit(0);
    }
  }
  return opts;
}

async function main() {
  const opts = parseArgs(process.argv.slice(2));
  const out = checkInbox({
    unreadOnly: opts.unreadOnly,
    quizgateOnly: opts.quizgateOnly,
    withBody: opts.withBody,
  });
  if (opts.plan) {
    out.plans = (out.messages || []).map((m) => ({
      id: m.id,
      subject: m.subject,
      ...planAdminEmailAction(m),
    }));
  }
  console.log(JSON.stringify(out, null, 2));
}

const isMain =
  process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1];
if (isMain) {
  main().catch((e) => {
    console.error(e);
    process.exit(1);
  });
}
