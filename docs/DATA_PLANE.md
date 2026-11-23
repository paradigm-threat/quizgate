# QuizGate anonymized Relay progress plane

Public hosts are **`*.quizgate.app` only**.

**Batch phases:** [`batch-phases.md`](batch-phases.md) · durable ledger [`BATCH_QUEUE.md`](BATCH_QUEUE.md) · Relay SoT [`../../../myproblem/docs/batch-phases.md`](../../../myproblem/docs/batch-phases.md) — same SI drain → commit queue → merge as MyProblem/Clevertree. Day-batch / profile jobs are kinds on the multi-site procedure; QuizGate’s **durable job index** is the per-app ledger (not the tip). Phase 1: **Cursor** (two passes: drain propose → validate push); Intake stays cache+enqueue. Tip = anonymous `users/<uuid>/`. Uploads: **Bunny** via per-repo upload-source config.

| Host / SoT | Role |
|------|------|
| https://quizgate.app | Marketing / curricula (Vercel) |
| https://admin.quizgate.app | Remote admin UI (UUID + email + 2FA) — see [REMOTE_ADMIN.md](REMOTE_ADMIN.md) |
| https://api.quizgate.app | Always-on Intake (cache, enqueue, realtime, admin OTP) |
| `git://iad1.relaygateway.net:9418/quizgate-users.git` | Relay **tip** (branch `users`) — profile only; no job listing |
| `quizgate-batch-queue` | Durable **job ledger** (queued / leased / done_empty / done) — see [`BATCH_QUEUE.md`](BATCH_QUEUE.md) |

## Flow

1. App mints anon UUID (`AnonUserId`) — capability token, no PII.
2. Local stats stay on device ([TELEMETRY.md](TELEMETRY.md)).
3. Upload is off by default. Runtime **Share anonymous progress** (onboarding + Parents/Setup `remote_management`) or the compile flag enables async on-open day-batch POSTs to  
   `POST /v1/users/{uuid}/sync` — no scheduled jobs. The client backfills any local days not yet uploaded (closed days once; today ≤1× per local day). Optional parent email registers a hash for human admin.
4. Intake **caches + enqueues** (PVC). It does **not** commit the tip. On each sync it also upserts an ephemeral **day batch** under the cache PVC:
   `{QUIZGATE_CACHE_DIR}/batches/{YYYY-MM-DD}/{uuid}.json` (override root with `QUIZGATE_BATCH_DIR`) — aggregates (`asked`/`right`/`wrong`/`skipped`, plus `*_lock` / `*_play` splits) and optional `by_question` map (per-qid shown/correct/wrong/skipped for that day, capped). No raw event trails.
   - **Unique per uuid + day:** exactly one file per `(uuid, day)`. Re-syncs atomically overwrite the counts (last write wins) and bump `sync_count`; no duplicate files.
   - **Day key:** optional body `day` (`YYYY-MM-DD`, e.g. the device's local day for backfill). When omitted, Intake uses the **UTC receive day**. Malformed `day` → `400 invalid_day`.
   - Identical bodies within 30s are deduped by the request cache (`deduped: true`, no `sync_count` bump).
5. Batch SI drains jobs (PVC pending + durable ledger). It writes tip **`users/<uuid>/profile.json` only** when standing fields change (`quizgate-batch` author). Readiness includes **level-up** soft gates (`levelUpPolicy.mjs`: default ≥75% answered accuracy, ≥5 correct, **skips ≤ right+wrong**) — SI should adhere but may document exceptions in the **git commit message**. If thresholds fail **or** the tip already reflects the same standing decision, mark ledger **`done_empty`** — do **not** push a no-op tip commit as proof. Phase 1: **Cursor** two-phase (propose locally → validate → push); `node batch/drain.mjs` / systemd remain parity targets for Spot. Tip never gets `days.jsonl` / `events.jsonl`.
6. App GETs `/v1/users/{uuid}/profile` for curriculum / frequency / performance preference.

Realtime photo / ASR / TTS hit `/v1/users/{uuid}/realtime/{kind}` — results return immediately; tip write only if a follow-up job is enqueued and batch accepts.

## Code

| Path | Role |
|------|------|
| [`data-plane/quizgate-users/`](../data-plane/quizgate-users/) | Tip hooks + schema (not the job ledger) |
| [`docs/BATCH_QUEUE.md`](BATCH_QUEUE.md) | Durable per-app job ledger design |
| [`data-plane/.data/quizgate-batch-queue/`](../data-plane/.data/quizgate-batch-queue/) | Local dry-run ledger git root (`QUIZGATE_BATCH_QUEUE_ROOT`) |
| [`data-plane/intake/server.mjs`](../data-plane/intake/server.mjs) | Always-on API |
| [`data-plane/batch/drain.mjs`](../data-plane/batch/drain.mjs) | Tip writer (`profile.json`) when standing change |
| [`data-plane/lib/dayBatch.mjs`](../data-plane/lib/dayBatch.mjs) | Intake day-batch upsert on cache PVC |
| [`data-plane/lib/levelUpPolicy.mjs`](../data-plane/lib/levelUpPolicy.mjs) | Level-up soft gates |
| [`data-plane/batch/checkInbox.mjs`](../data-plane/batch/checkInbox.mjs) | Poll Relay Maildir (same as fleet Inbox) |
| [`data-plane/lib/inboundMail.mjs`](../data-plane/lib/inboundMail.mjs) | Shared Maildir reader wrapper |
| [`data-plane/infra/DNS.md`](../data-plane/infra/DNS.md) | DNS / TLS / MX |
| [`docs/EMAIL.md`](EMAIL.md) | Outbound Gmail vs inbound `batch@quizgate.app` |
| [`UnlockQuiz/.../telemetry/`](../UnlockQuiz/src/net/clevertree/quizgate/telemetry/) | APK client |

## Local

```bash
cd data-plane
node --test
PORT=8791 node intake/server.mjs
# elsewhere:
node batch/drain.mjs
```

## Production verify

```bash
dig api.quizgate.app +short
curl -sS https://api.quizgate.app/health
UUID=xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx
curl -sS -X POST "https://api.quizgate.app/v1/users/$UUID/sync" \
  -H 'content-type: application/json' \
  -d "{\"user_uuid\":\"$UUID\",\"today\":{\"asked\":10,\"right\":9,\"wrong\":1,\"skipped\":0},\"curriculumId\":\"basic-math\"}"
# backfill a past local day (same uuid+day file is overwritten, sync_count++):
curl -sS -X POST "https://api.quizgate.app/v1/users/$UUID/sync" \
  -H 'content-type: application/json' \
  -d "{\"user_uuid\":\"$UUID\",\"day\":\"2026-11-18\",\"today\":{\"asked\":52,\"right\":24,\"wrong\":2,\"skipped\":8,\"asked_lock\":5}}"
# on iad1:
ls /var/quizgate/cache/batches/2026-11-18/
sudo systemctl start quizgate-batch
curl -sS "https://api.quizgate.app/v1/users/$UUID/profile"
```

Upload remains **OFF** in the APK (`TELEMETRY_UPLOAD_ENABLED = false`) until privacy + Play Data safety are updated for production.

## Inbound mail (batch)

Relay OpenSMTPD on iad1 accepts **`batch@quizgate.app`** (catch-all) into the shared fleet Maildir. Batch polls with the same helper as relay-ops Inbox:

```bash
cd data-plane
npm run inbox -- --quizgate --body --plan
# optional on drain:
QUIZGATE_BATCH_CHECK_INBOX=1 node batch/drain.mjs
```

Outbound 2FA stays on Gmail SMTP — see [EMAIL.md](EMAIL.md). Future: UUID-in-thread admin email → same decisions as admin UI / API (hook only today).
