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

**basic-math v4:** addition under 10, expanded subtraction (`subtract_within_10`), and light multiplication (`multiply_within_10`) — no color / “which one is red” items (those return in a later pack with chip-only UI, no text labels). Division is deferred; pack is unfinished until that pass, then SI may mark curricula **proficient**.

**Zero identity (`n + 0` / `0 + n`):** treated as tricky — skill `add_with_zero`, tier **1** only (not Kindergarten). Weight `1`.

Optional per-item `weight` (integer ≥1, default 1): `QuestionPicker` uses weighted random selection so rarer skills stay uncommon as the pool grows. basic-math sets addition `weight: 2` and subtraction/multiplication typically `weight: 1`.

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
