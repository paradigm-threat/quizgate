# QuizGate Intake host

Always-on process for **`https://api.quizgate.app`**.

## Live (iad1)

| Piece | Location |
|-------|----------|
| Code | `/opt/quizgate/data-plane` |
| Tip worktree | `/var/quizgate/users` (branch `users`) |
| Queue / cache | `/var/quizgate/queue`, `/var/quizgate/cache` |
| systemd | `quizgate-intake.service` (listen `:8791`) |
| Batch oneshot | `systemctl start quizgate-batch` |
| Caddy site | `/etc/caddy/Caddyfile.quizgate` → `127.0.0.1:8791` |
| TLS ask extra | `/etc/caddy/on-demand-extra.txt` (`api.quizgate.app`) |

Verify:

```bash
curl -sS https://api.quizgate.app/health
# {"ok":true,"service":"quizgate-intake",...,"tip_writer":"quizgate-batch"}
```

## Process (local)

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
# iad1: sudo systemctl start quizgate-batch
```

Publish tip after drain (optional):

```bash
export QUIZGATE_PUBLISH_REMOTES=git://iad1.relaygateway.net:9418/quizgate-users.git
# iad1 unit uses git://127.0.0.1:9418/quizgate-users.git
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

## Peer mesh (ops)

`quizgate-users` is authorized via **relay-master** `catalog/repos/quizgate-users.json` (`e69a36b`, 2026-11-17) → `/api/config` `authorized_repos` / `catalog_repos`. Keep `domains: []` — do **not** put `api.quizgate.app` in `domains[]` (would park HTTP to gateway `:8080` instead of Intake Caddy → `:8791`).
