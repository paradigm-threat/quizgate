# quizgate-users

Relay SoT for anonymized QuizGate progress. **UUID = access** — **no user listing**.

This tip is **not** the batch job index. Durable queued/leased/done tracking lives in
the per-app ledger — see [`docs/BATCH_QUEUE.md`](../../docs/BATCH_QUEUE.md).

| Host | Role |
|------|------|
| `https://api.quizgate.app` | Always-on Intake (cache, enqueue, realtime) |
| Batch SI (Cursor Phase 1 / Spot Phase 2) | Permanent tip writer when standing fields change |
| `git://iad1.relaygateway.net:9418/quizgate-users.git` | Relay publish (branch `users`) |
| `quizgate-batch-queue` | Job ledger (separate repo) |

## Layout

```
users/<uuid>/profile.json
```

Text only. Tip holds curriculum / readiness / settings (and future per-curriculum status).  
**Do not** commit day rollups or quiz event trails here — those are ephemeral Intake day batches on the cache PVC (`batches/{day}/{uuid}.json`). Legacy `days.jsonl` / `events.jsonl` may exist historically; new drains no longer write them.  
**Do not** use an empty tip commit to prove a batch ran — mark `done_empty` on the ledger instead.

Binaries live on Intake PVC (`cache://…`), never in git. SI narrative for a drain belongs in **git commit messages** (tip and/or ledger), not standing tip markdown (yet).

## Publish

```bash
git push git://iad1.relaygateway.net:9418/quizgate-users.git users
```

On iad1, batch uses `QUIZGATE_PUBLISH_REMOTES=git://127.0.0.1:9418/quizgate-users.git`.  
`/var/quizgate/users` `origin` must be that URL — **never** `/opt/relay/data/quizgate-users.git`.  
Do **not** `chown` the Relay bare repo to `ubuntu` (breaks index warm).

Service author for tip commits: `quizgate-batch` / `quizgate-batch@quizgate.app`.
