# QuizGate anonymized Relay progress plane

Public hosts are **`*.quizgate.app` only**.

| Host | Role |
|------|------|
| https://quizgate.app | Marketing / curricula (Vercel) |
| https://api.quizgate.app | Always-on Intake (cache, enqueue, realtime) |
| `git://iad1.relaygateway.net:9418/quizgate-users.git` | Relay tip (branch `users`) — infra, not a brand host |

## Flow

1. App mints anon UUID (`AnonUserId`) — capability token, no PII.
2. Local stats stay on device ([TELEMETRY.md](TELEMETRY.md)).
3. When `TELEMETRY_UPLOAD_ENABLED` is true, app POSTs privacy-safe snapshots to  
   `POST /v1/users/{uuid}/sync` on Intake.
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
| [`data-plane/infra/DNS.md`](../data-plane/infra/DNS.md) | DNS / TLS |
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
