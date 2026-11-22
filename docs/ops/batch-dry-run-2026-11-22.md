# Batch dry-run 2026-11-22

Cursor Phase-1 dry-run for **yesterday** tablet stats (`2026-11-21`).

## Job — tablet (tip create @ L0, SI withhold level bump)

| Field | Value |
|-------|-------|
| **job_id** | `f15725fc-668c-4b73-85e5-b683b7469c7b` |
| **day** | `2026-11-21` |
| **uuid** | `41f0fc0e-9e10-47a5-abb7-aab5f13553c0` (current device; new vs prior tip `851f35c3-…`) |
| **curriculum** | `basic-math` level **0** (stay) |
| **observed** | asked 34 · right 7 · wrong 0 · skipped 4 · answered accuracy **100%** · skip 12% of asked |
| **gates** | **eligible** — ≥5 correct, ≥75% answered accuracy, `skipped ≤ right+wrong` (4 ≤ 7) |
| **SI** | **Withhold L0→L1** — thin mastery sample, skips near gate, recent weak days, L1 adds subtract |
| **tip** | create `a59f4d504d7d8c6c4ac872eff7a1f7eedad1d80b` · `curriculum_level: 0` |
| **ledger** | `done` + tip_commit (local dry-run root) |

### Why not advance (despite thresholds)

1. Only **7** correct (barely above min 5).
2. **4** skips vs **7** answered — passes the rule but fragile.
3. **0** wrongs with multiple skips can mean avoidance, not mastery.
4. Context: `2026-11-19` ~59% and `2026-11-20` ~47% answered accuracy (same tablet, older uuid).
5. Many lock sessions were shown-only / abandoned; limited item variety (e.g. `m23` twice).

### Recommendation

**Do not move to L1 yet.** Keep **basic-math L0** another day or two. Re-evaluate when a day clears the same gates with **more correct volume** (aim well above 5) and skips comfortably under answered.

Skip default: `skipped ≤ right+wrong` (`LevelUpPolicy` / `CURRICULUM.md`).
