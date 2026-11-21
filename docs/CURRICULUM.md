# QuizGate curriculum packs

## Feature gap (summary)

| Feature | Pickles (iOS) | Screenwise (Android) | QuizGate |
|---------|---------------|----------------------|----------|
| Education earns time | Minutes jar | Optional math minutes | Minutes jar (Phase 1) |
| Hard app lock | Screen Time | Accessibility blocker | **Hard lock shipped** (Accessibility quiz-confine during active scheduled session; widget/Setup practice unlocked) |
| Web filter / device admin | No | Yes | **No** (not our identity) |
| Grade / age tiers | K–5 | Times-tables adaptive | Pack tiers + age bands |
| No accounts / ads | Yes | Claims free; IAP badge | Forever free, local SoT |
| Curriculum sync | Bundled | Bundled | Signed `.qgpack` + optional sync |
| Curation | App updates | App updates | **SI batch** on pack repo |

North star: [Pickles](https://pickles.to/). Android peer we do not copy as identity: [Screenwise](https://play.google.com/store/apps/details?id=com.makeme.learn.screenwise).

## Naming — SI

We say **SI** (Super Intelligence, i.e. AI) for the software that files, summarizes, and curates — same Clevertree Chat convention. SI curates curriculum packs in batch; parents keep final decisions. Not an open-internet tutor in the child’s profile.

## Pack format (`.qgpack`)

A zip containing:

- `manifest.json` — id, title, ageMin/ageMax, difficultyRank, version, contentSha256, …
- `curriculum.json` — tiers, round rules, earn rules, items[]
- `SIGNATURE` — `UNSIGNED-BUNDLED` for APK assets; ed25519 over sha256 for published packs (Phase 1 accepts bundled + sha match)

Index: `https://quizgate.app/curricula/index.json`

Source tree in this repo: [`curricula/`](../curricula/). Bundled copy: `UnlockQuiz/assets/curricula/basic-math/`.

**basic-math v5:** items carry a progression **`level`** (integer ≥0). Device pref `curriculum_level` (default **0**) includes items with `item.level <=` that value.

| Level | Contents |
|------:|----------|
| **0** | Getting started — all `add_within_10` (**no zeros**) + a few easy `subtract_within_10` |
| **1** | Zero identity (`add_with_zero`: `n+0` / `0+n`) + remaining subtraction |
| **2** | Light `multiply_within_10` |

No color / “which one is red” items (later pack). Division deferred.

Optional per-item `weight` (integer ≥1, default 1): `QuestionPicker` uses weighted random selection so rarer skills stay uncommon as the pool grows. basic-math sets addition `weight: 2` and subtraction/multiplication typically `weight: 1`.

Age **`tier`** (K / 1) remains a separate filter (`active_tier`); levels are the primary “how deep” control.

## Level-up eligibility (soft gates)

Before SI / batch suggests the **next curriculum level**, repo defaults require (all of):

| Gate | Default | Setting key |
|------|--------:|-------------|
| Accuracy of **answered** items (`right / (right+wrong)`) | **≥ 75%** | `level_up_min_accuracy_pct` |
| Absolute correct count | **≥ 5** | `level_up_min_correct` |
| Skips vs answered | **`skipped ≤ right + wrong`** (more skips than rights+wrongs → block) | omit = this rule; optional `level_up_max_skip_pct` = max `skipped/asked` % instead |

Omit/blank in admin → device and SI use these defaults. Per-user overrides live on the tip `settings` object (same blank-means-default pattern as other admin knobs).

**SI guidance:** treat thresholds as the normal bar — adhere by default. The model may still **withhold** a level-up or **advance early** when other evidence clearly warrants it; document the exception in readiness reasons / content hints. Parents remain final authority on what the device runs (`curriculum_level`).

Shared implementations: `LevelUpPolicy` (app) · `data-plane/lib/levelUpPolicy.mjs` (batch).

## Render formats (ask-time)

Catalog items stay **one logical question** (operands + op + correct result). The engine chooses a **render format** when asking — no duplicate rows for True/False vs fill-in vs multi-answer.

| Type | Default allowed formats (code) |
|------|--------------------------------|
| `math` | `equation` · `true_false` · `choice` |
| other | `equation` (fill / multiple-choice as today) |

- **`equation`** — show `1 + 2 = _`; entry is digit pad or multiple-choice per `math_entry` pref.
- **`true_false`** — show a full equation (`1 + 2 = 4`); child taps ✓ / ✗. At ask time the RHS is either the correct result (True) or a small integer offset (False). Own UI — not the digit pad.
- **`choice`** — same blank equation prompt; always show shuffled catalog `choices` (one correct). Ignores digit-pad pref for that ask.

**Skip:** every ask shows a Skip control. It logs `skipped`, then immediately presents another catalog item. After a skip the picker soft-biases toward easier formats (`true_false` / `choice`) and a different op-kind when the pool allows — still randomized so consecutive skips can land on yes/no again (no fixed ladder).

Optional per-item `formats` overrides are reserved for later; basic-math relies on the math defaults above. Stats may log `render_format` on quiz events (token only).

### Roadmap (not implemented)

- **Missing addend / algebra blank** — e.g. `□ + 2 = 3` or `x + 2 = 3` (Pickles-style “fill the blank” on an addend). Same catalog row; new `RenderFormat` + presentation only.
- Compare / inequality cards and other Pickles templates as additional formats when the template engine grows.

## Sync

- Pref `sync_enabled` (default on, Wi‑Fi-only downloads)
- Pref `active_curriculum_id`, `active_tier`
- Offline: bundled basic-math always available

## SI batch

See [`curricula/scripts/`](../curricula/scripts/) — validate packs in CI; optional SI review notes in `review.json` before publish.
