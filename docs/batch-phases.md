# Batch phases (QuizGate)

Relay-wide product SoT: [`../../../myproblem/docs/batch-phases.md`](../../../myproblem/docs/batch-phases.md).

**Tip lens:** a QuizGate tip is a whole **user** (`users/<uuid>/`) tracking progress over time (may split later). Clevertree may bind a room to that same UUID.

Day-batch / profile jobs are **kinds** on the **shared batch queue**. Phase 1: **Cursor** inventories all user tips then drains ([`cursor-batch-playbook.md`](../../../myproblem/docs/cursor-batch-playbook.md)). Phase 2: Spot. Uploads: **Bunny** via per-repo upload-source config.
