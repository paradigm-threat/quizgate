# QuizGate durable batch queue (ledger)

Per-app SoT for **job identity, lease, and completion** — separate from the tip
[`quizgate-users`](../data-plane/quizgate-users/README.md) (UUID = access, **no listing**).

Relay-wide procedure stays drain → commit queue → merge
([`batch-phases.md`](batch-phases.md) · MyProblem
[`cursor-batch-playbook.md`](../../../myproblem/docs/cursor-batch-playbook.md)).
This doc is the QuizGate **operational ledger**.

## Why a separate repo

| Concern | Tip (`quizgate-users`) | Ledger (`quizgate-batch-queue`) |
|---------|------------------------|----------------------------------|
| List unprocessed jobs | Forbidden (no user listing) | Required |
| Prove a `job_id` was handled | Empty tip commit is wrong | State transition commit |
| Mesh / multi-worker sync | Capability paths only | Append-only index + job files |
| AI narrative | Prefer **git commit message** | Same — not standing tip markdown |

Do **not** use “process twice and ignore” as the primary dedupe story — that hides
lost jobs. Dedupe is **same `job_id` already `done` / `done_empty` / `failed`**.

## Layout (working name `quizgate-batch-queue`)

```
quizgate-batch-queue/
  README.md
  ledger/jobs/<job_id>.json     # envelope + state
  ledger/index.jsonl            # append-only id/state/ts for sync
```

Local dry-run root: `data-plane/.data/quizgate-batch-queue/` (git repo).
Env: `QUIZGATE_BATCH_QUEUE_ROOT`.

### Job envelope fields

| Field | Meaning |
|-------|---------|
| `id` | Stable job UUID (prefer Intake `job_id`) |
| `kind` | `day_batch` / `progress_sync` / … |
| `user_uuid` | Tip capability UUID |
| `day` | Stats day `YYYY-MM-DD` (device barrier day when known) |
| `created_at_ms` | Enqueue time |
| `state` | `queued` → `leased` → `done` \| `done_empty` \| `failed` |
| `leased_by` / `leased_at_ms` | Worker claim |
| `result` | `empty` \| `tip_commit` \| `failed` |
| `tip_commit` | Tip sha or `null` |
| `payload_ref` | PVC path or embedded lean counts |
| `notes` | Short machine tokens; **full SI prose in the ledger git commit message** |

### States

| Event | State | Tip write |
|-------|-------|-----------|
| Intake accepts sync | `queued` | none |
| Worker leases | `leased` | none |
| No standing tip change | `done_empty` | **no tip commit** (proof = ledger) |
| Standing tip change | `done` + `tip_commit` | local tip commit → **validate pass** → push |
| Crash after lease | TTL → requeue `queued` | none |
| Duplicate `job_id` | ignore | none |

## Two-phase Cursor (Phase 1)

1. **Run A (drain):** lease job → read day aggregates (PVC or payload) → apply
   level-up soft gates ([`CURRICULUM.md`](CURRICULUM.md)) → propose tip commit
   **only if** standing fields change → ledger commit with full notes in
   **commit message**. Do **not** push tip remotes.
2. **Run B (validate / merge):** recheck rules (no illicit level bump, no PII,
   job id present) → push tip only if a non-empty sound tip commit exists →
   finalize ledger / Intake PVC job.

## Hot path vs durable path

- **Now:** Intake upserts PVC day batches (`DATA_PLANE.md`); durable ledger
  stores envelopes + states. Cursor/Spot claim via ledger.
- **Next:** embed lean aggregates in the job JSON so workers need not mount PVC.
  Keep PVC until that path is proven.

## Mesh intent

Each app owns its queue ledger so workers worldwide can list and claim that app’s
jobs without scanning other products’ tips. Relay still uses one **procedure**;
QuizGate uses this **repo** as the QuizGate slice of durable queue SoT.

## Dry-run checklist

See end of a dry-run’s ledger commit message for the job id and level-up decision.
Success: job processed in ledger; thresholds evaluated; no curriculum bump unless
gates + SI exception say so; never use an empty tip commit as proof of work.
