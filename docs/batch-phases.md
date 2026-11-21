# Batch phases (QuizGate)

Relay-wide product SoT: [`../../../myproblem/docs/batch-phases.md`](../../../myproblem/docs/batch-phases.md) — **same SI procedure** as MyProblem and Clevertree (drain → commit queue → merge phase → remotes). Phase 1 Cursor / Phase 2 Spot only change the runner.

**Tip lens:** a QuizGate tip is an **anonymous user** account (`users/<uuid>/` on the tip remote) — progress over time, PII removed from standing SoT. Clevertree may bind a room to that same UUID. The tip is **UUID = access** and must **not** list users or act as a job index.

**Durable queue (QuizGate-only):** [`BATCH_QUEUE.md`](BATCH_QUEUE.md) — per-app ledger repo (`quizgate-batch-queue`) tracks `job_id` queued → leased → done / done_empty / failed. Proof of processing is a **ledger commit** (SI notes in the **commit message**), not an empty tip commit. Intake still caches day aggregates on PVC; it also enqueues job envelopes into the ledger path as that lands.

**Two-phase Cursor:** Run A drains and proposes (local tip commit only if standing fields change; always update ledger). Run B validates rules and authorizes tip push. Playbook: [`cursor-batch-playbook.md`](../../../myproblem/docs/cursor-batch-playbook.md).

**Jobs:** day-batch / profile / readiness kinds. Every job is a potential tip create/update **or** a deliberate `done_empty` when level-up / standing gates say nothing to write. Level-up soft gates: [`CURRICULUM.md`](CURRICULUM.md). Shared SI tools when the job and rules say so. Uploads: **Bunny** via per-repo upload-source.

Data-plane notes: [`DATA_PLANE.md`](DATA_PLANE.md).
