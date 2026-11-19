# Time Earn (Rapid-Fire) Feature

Kids playing in **standalone mode** (app opened on its own, not during a lock) can earn extra minutes before the next scheduled screen lock by answering questions quickly. Each correct answer grants minutes scaled by response speed; the pool is capped per session.

## How it works

1. **Visibility threshold** — The "Earn More Time" entry point only appears when the remaining time until the next scheduled lock is **strictly less than the session cap** (default two hours). At or above the cap, the UI is hidden. This ties the feature to the existing schedule clock — no second timer to learn.
2. **Success overlay button** — On a correct answer in standalone mode, the one-second success transition overlay shows a large, high-contrast **Earn More Time** button as the primary tap target in the existing `transition_play_actions` row (alongside Ask Another / Quit / View Stats).
3. **Latency bands** — Response time is bucketed into three categories, never stored as raw milliseconds:
   - **Fast** — under 3 seconds → 5 minutes
   - **Thinking** — 3 to 10 seconds → 2 minutes
   - **Slow** — over 10 seconds → floor amount (default 1 minute). One minute or one hour, same band.
   - **Skipped** — no answer given → 0 minutes, but still recorded for correlation.
4. **Floor** — A slow-but-correct answer always earns at least the floor so a tired kid isn't punished into quitting. The floor is configurable and cannot exceed the cap.
5. **Session cap** — Total minutes earnable per session is capped (default **120 minutes / two hours**). Once exhausted, rewards stop until the next session. The cap is **admin-tunable** via the remote admin panel (`admin_settings` → profile pull), same pattern as interval/jitter/performance preference.
6. **Per-session reset** — Earned minutes zero out with the session/day rollup. They do not stack across days.
7. **Stats payload** — Each attempt records, per question ID:
   - latency **band** (`fast` / `thinking` / `slow` / `skipped`)
   - question **format** (`multiple_choice` / `fill_in_blank` / `true_false` / `digit` / `other`)
   - attempted vs skipped counts

   Raw latencies are never uploaded — only the band enum, keeping the daily batch small and privacy-safe. Bands plus format let you correlate which question formats push kids into the slow bucket.

## Files

- `UnlockQuiz/src/net/clevertree/quizgate/core/TimeEarnPolicy.java` — pure JVM policy (bands, rewards, cap, threshold, stats keys)
- `tests/TimeEarnTest.java` — unit coverage for thresholds, weights, floor, cap clamping, visibility, skips, payload keys
- `scripts/run_unit_tests.sh` — wires `TimeEarnTest` into the CI gate

## UI scope

Standalone mode only for v1. Lock-screen mode is unchanged. Header countdown UI is deferred.
