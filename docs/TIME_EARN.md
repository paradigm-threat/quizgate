# Time Earn (Rapid-Fire) Feature

Kids playing in **standalone / practice mode** (app opened on its own, not during a Hard lock) can earn extra minutes for the **minutes jar** before the next scheduled screen lock by answering quickly. Each correct answer grants minutes scaled by response speed; the pool is capped per session. Jar minutes still **add on top of** the normal 40±10 window (never shrink it).

Policy core is pure JVM: [`TimeEarnPolicy`](../UnlockQuiz/src/net/clevertree/quizgate/core/TimeEarnPolicy.java). Play success overlay wires **Earn more time** in [`QuizActivity`](../UnlockQuiz/src/net/clevertree/quizgate/QuizActivity.java).

## How it works

1. **Visibility threshold** — The "Earn More Time" entry point only appears when remaining time until the next scheduled lock is **strictly less than the session cap** (default two hours) and still **&gt; 0**. At or above the cap (or with no upcoming alarm), the UI stays hidden.
2. **Success overlay button** — On a correct answer in standalone / play mode, the success transition offers **Earn more time** (primary) alongside Ask Another / Quit / View Stats. Tapping it grants jar minutes for that answer’s latency band, then asks another. Once active, further corrects in the same play visit auto-grant until the session cap is exhausted.
3. **Latency bands** — Response time is bucketed; raw milliseconds are never stored:
   - **Fast** — under 3 seconds → **5** minutes
   - **Thinking** — 3 to 10 seconds (inclusive) → **2** minutes
   - **Slow** — over 10 seconds → floor (default **1** minute). One minute or one hour, same band.
   - **Skipped** — no answer → **0** minutes (counted for correlation).
4. **Floor** — Slow-but-correct still earns the floor so a tired kid isn’t punished into quitting. Floor cannot exceed the session cap.
5. **Session cap** — Default **120** minutes (max **180**, min **1**). Admin-tunable via `settings.time_earn_cap_minutes` on the tip profile (Intake `admin_settings` → batch → pull). Once exhausted, rewards stop until `resetSession()`.
6. **Minutes jar** — Granted minutes feed `AppPrefs.addMinutes` / `takeMinutesBonusMs` (existing schedule bonus path).
7. **Stats** — Attempts record band + format keys aligned with `RenderFormat` (`equation` / `true_false` / `choice` / `digit` / `other`). No raw latency in uploads.

## Admin / profile

| Setting | Default | Range |
|---------|---------|-------|
| `settings.time_earn_cap_minutes` | 120 | 1–180 |
| `settings.stats_day_barrier_hour` | 5 | 0–23 (omit → device default 5 AM stats day) |

Written by human admin PATCH → queue → `quizgate-batch`; applied on device by `RemoteSettingsPull`.

## UI scope

Standalone / practice: success overlay offers **Earn more time** / Ask another / **Quit** (Quit is the largest button).

**Post-unlock offer (Hard):** After a scheduled lock unlock, teardown (grace + `scheduleAfterSessionEnd`) still runs at the same moment as before. Separately, a once-per-local-day **40%** lottery (`PostUnlockPlayOfferPolicy` / pref `post_unlock_play_offer_day`) may show the same play-choice chrome instead of auto-finishing. Quit uses the normal lock restore/finish path. Choosing Earn / Ask another continues as play; new jar minutes call `AlarmScheduler.applyJarBonusToNextAlarm` so they extend the already-armed next lock (up to the session cap, default two hours remaining-visibility rule unchanged). If the offer is shown once, later Hard unlocks that day skip it.

## Files

- `UnlockQuiz/.../core/TimeEarnPolicy.java` — bands, rewards, cap, visibility, format map, session counters
- `UnlockQuiz/.../core/PostUnlockPlayOfferPolicy.java` — once/day + 40% lottery
- `UnlockQuiz/.../core/JarSchedulePolicy.java` — extend next-alarm math
- `UnlockQuiz/.../AppPrefs.java` — `get/setTimeEarnCapMinutes`, `post_unlock_play_offer_day`
- `tests/TimeEarnTest.java`, `tests/PostUnlockPlayOfferTest.java` — regression via `scripts/run_unit_tests.sh`
- Tip schema: `data-plane/quizgate-users/schema/profile.v1.json`
