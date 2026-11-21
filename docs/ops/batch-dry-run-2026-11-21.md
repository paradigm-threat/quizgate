# Batch dry-run 2026-11-21

Local Cursor Phase-1 dry-run of day_batch processing (two jobs).

## Job 1 — tablet (done_empty, no tip commit)

- **job_id:** `4ce89f95-7148-4a83-a2c6-463e615802a0`
- **day:** `2026-11-20` · **uuid:** `851f35c3-2432-4e44-81cd-84050a403e50`
- **level-up:** not eligible (~47% answered accuracy &lt; 75%)
- **tip:** UUID already on tip; **no tip commit** (no standing decision)
- **ledger:** `done_empty`

## Job 2 — simulated student (tip create + level bump)

- **job_id:** `afa5f46a-dd84-4647-ae65-1d0253f82dd5`
- **day:** `2026-11-21` · **uuid:** `28d9f334-63b2-483a-9308-22f945d2db35` (not on a device)
- **observed:** 9 right / 1 wrong / 5 skipped
- **level-up:** eligible (90% accuracy, skips ≤ answered) → `curriculum_level` 0→1
- **tip:** create commit `67fd13e44ea9e0b3f593f7c4c2cbc88510db032f`
- **ledger:** `done` + tip_commit

Skip default used: `skipped ≤ right+wrong` (see `LevelUpPolicy` / `CURRICULUM.md`).

Full SI narrative is in the ledger git commit messages.
