# Batch dry-run 2026-11-23

Alena tablet `851f35c3-2432-4e44-81cd-84050a403e50` (device uuid restored; parallel test uuid stays off-device).

## Yesterday `2026-11-22`

**No events / no day rollup** — empty day → ledger `done_empty` (`9a6ba300-…`).

## Last closed day with data `2026-11-21`

| Field | Value |
|-------|-------|
| asked / right / wrong / skipped | 34 / 7 / 0 / 4 |
| answered accuracy | 100% |
| gates | **eligible** |
| SI | **Withhold L0→L1** (thin mastery; skips near gate) |
| tip | stay `curriculum_level: 0`; restore **40±10m** interval (undo mistaken 45m from right/asked) |
| ledger | `ed091bb6-…` tip_commit |

### Per-question (11-21, from events → server `by_question`)

Skips: `a10` (2+4), `a11` (2+5), `a06` (1+6), `m13` (5+4) — now L1 in basic-math v8.  
Shown without answer: `a03` (1+3) ×3. Corrects: `m23`, `a04`, `a18`, `a19`, `m22`, `m27`.

## Drain fix

`progress_sync` no longer auto-bumps `curriculum_level` (hints only: `consider_level:N`). Parents/SI apply standing level. Prevents race with SI withholds.

## Per-question stats (shipped)

- Device: durable `files/stats/questions.jsonl`
- Day-batch upload: optional `by_question` map on Intake PVC
