# QuizGate anonymized Relay progress plane

Public hosts are **`*.quizgate.app` only**.

| Host | Role |
|------|------|
| https://quizgate.app | Marketing / curricula (Vercel) |
| https://admin.quizgate.app | Remote admin UI (UUID + email + 2FA) — see [REMOTE_ADMIN.md](REMOTE_ADMIN.md) |
| https://api.quizgate.app | Always-on Intake (cache, enqueue, realtime, admin OTP) |
| `git://iad1.relaygateway.net:9418/quizgate-users.git` | Relay tip (branch `users`) — infra, not a brand host |

## Flow

1. App mints anon UUID (`AnonUserId`) — capability token, no PII.
2. Local stats stay on device ([TELEMETRY.md](TELEMETRY.md)).
3. Upload is off by default. Runtime **remote management** (Setup) or the compile flag enables POSTs of privacy-safe snapshots to  
   `POST /v1/users/{uuid}/sync` on Intake. Optional parent email registers a hash for human admin.
4. Intake **caches + enqueues** (PVC). It does **not** commit the tip.
5. Batch SI drains the queue, writes `users/<uuid>/profile.json|events.jsonl|days.jsonl` as **`quizgate-batch`**, optionally pushes iad1.
6. App GETs `/v1/users/{uuid}/profile` for curriculum / frequency / performance preference.

Realtime photo / ASR / TTS hit `/v1/users/{uuid}/realtime/{kind}` — results return immediately; tip write only if a follow-up job is enqueued and batch accepts.

## Code

| Path | Role |
|------|------|
| [`data-plane/quizgate-users/`](../data-plane/quizgate-users/) | Relay SoT hooks + schema |
| [`data-plane/intake/server.mjs`](../data-plane/intake/server.mjs) | Always-on API |
| [`data-plane/batch/drain.mjs`](../data-plane/batch/drain.mjs) | Sole tip writer |
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
# on iad1:
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
