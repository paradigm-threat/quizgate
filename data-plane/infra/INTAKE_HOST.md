# QuizGate Intake host

Always-on process for **`https://api.quizgate.app`**.

## Process

```bash
cd data-plane
export PORT=8791
export QUIZGATE_USERS_ROOT=./quizgate-users
export QUIZGATE_QUEUE_DIR=./queue
export QUIZGATE_CACHE_DIR=./.data/cache
node intake/server.mjs
```

Reverse-proxy TLS → `127.0.0.1:8791` with Host `api.quizgate.app`.

## Batch wake

When `pending` queue depth/age warrants:

```bash
node batch/drain.mjs
# Spot: set QUIZGATE_BATCH_DECOMMISSION=1 when idle
```

Publish tip after drain (optional):

```bash
export QUIZGATE_PUBLISH_REMOTES=git://iad1.relaygateway.net:9418/quizgate-users.git
```

## Env

| Var | Purpose |
|-----|---------|
| `PORT` | Listen port (default 8791) |
| `QUIZGATE_USERS_ROOT` | Checkout of tip repo |
| `QUIZGATE_QUEUE_DIR` | Ephemeral job PVC |
| `QUIZGATE_CACHE_DIR` | Request + media cache |
| `QUIZGATE_OPS_SECRET` | Protect `/v1/queue/pending` |
| `QUIZGATE_PUBLISH_REMOTES` | Comma-separated git:// remotes for batch |

## Sole tip writer

**`quizgate-batch`** only. Intake never runs `git commit` on `users/`.
