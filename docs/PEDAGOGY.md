# QuizGate pedagogy

*Public educational record — how QuizGate teaches, who it is for, and how time-earn reinforces mastery without speed-grinding.*

## Purpose
QuizGate replaces stretches of idle tablet time with short, age-fit practice. Learning is the gate to more screen time — not an afterthought bolted onto a surveillance suite. Parents keep every final decision: schedule, curriculum pack, sync on/off, and Hard lock permissions.

## Learners and age bands
| Pack (current) | Age band (manifest) | Focus |
|----------------|---------------------|--------|
| **first-access** | about **4–7** | Pre-math **UI / thinking readiness**: what-comes-next number + letter sequences (~75% / ~25%); digit/letter pad rush-blockers; L0 forward + light reverse; L1 reverse proficiency; L2 patterns + answer-centered pad; L3 randomized pad readiness before basic-math |
| **basic-math** v8 | about **4–7** | Progression **levels**: L0 easiest add (sums ≤5) 4-option MC grid; L1 add-within-10 + harder under-8 adds + easy subtract; L2 zeros + subtract + doubles; L3 light multiply + ×0/×1. Idle MC hints until first correct of the day |

| Pack (planned) | Age band | Focus |
|----------------|----------|--------|
| **first-touch** (not built) | about **1–3** | Picture/audio taps only after UI exists: match-same, body/self, animal sounds, count 1–3, big/small — no reading gate |

**Start kids on first-access, not basic-math**, until they can sit with the quiz UI (choice + true/false + pad) without mashing. Future packs publish their own `ageMin` / `ageMax`. **Levels** gate depth (default **0**); age tiers remain optional.

### Anti-outcome: rush / mash-to-unlock
A day with **high asked**, **~chance-level rights**, **choice-only** (or almost no `equation`), many **fast** latencies, and/or frequent **consoling** unlocks usually means the child is pressing every tile until the three-wrong consoling path opens — not learning. Prefer packs and formats that require composing an answer on the pad, teach format literacy first, and review day-batch **latency bands** + `by_format` remotely. On-device `events.jsonl` already has `latency_ms`; uploads use bands only.

**Level-up bar (default):** at least **75%** correct among answered questions, at least **5** correct, and **not more skips than answered** (`skipped ≤ right + wrong`). Admin may override per device (including an optional skip-%-of-asked cap). SI uses these as soft gates (may exception with a reason). See [`CURRICULUM.md`](CURRICULUM.md).

## Learning objectives (first-access)
1. **Sit and think** — use choice / true-false / pad without rushing every button.
2. **What comes next** — forward and reverse number sequences (single digits); letter sequences A–I then wider.
3. **Pad literacy** — enter the answer on the shared digit/letter pad (up to three tries per ask; outcome-only stats).
4. **Chaos readiness (L3)** — still find the answer when the pad is shuffled / non-adjacent before moving to basic-math.

## Learning objectives (basic-math v8)
By working through scheduled Hard quizzes and optional practice, a child practices:

1. **Easiest addition (level 0)** — sums ≤5 (addends 1–4), **4-option multiple-choice grid only** (no keypad / T/F yet); progressive idle hints while she has no rights yet today. Tuned so first contact is more often solved correctly.
2. **Addition within 10 (level 1)** — larger addends including former under-8 sums 6–7; equation / T/F / choice allowed; more easy subtracts (`n−1`, `n−2`) enter.
3. **Subtraction within 10** — take-away within the same range (expands at level 2).
4. **Zero identity + doubles (level 2)** — `n + 0` / `0 + n`; doubles and near-doubles as tagged skills; remaining subtraction.
5. **Light multiplication within 10 (level 3)** — early facts plus **×0 / ×1** identity.
6. **Multiple representations** — from level 1 up, the same fact can appear as equation blank, true/false, or multiple choice.

Division and broader grade bands are deferred to later packs. Color / “which is red” style items return only with UI that does not rely on text labels alone. Ages **1–3** need a separate picture/audio pack (see [`CURRICULUM.md`](CURRICULUM.md) backlog) — not levels inside basic-math.

## How a session teaches
- **Hard lock (scheduled):** one correct answer unlocks (round size 1). The goal is a short, successful practice burst — not a multi-question jail.
- **Practice / widget:** unlocked; success can offer another question or **Earn more time**.
- **Skip:** allowed; logged; the picker soft-biases toward easier formats after skips so frustration does not trap the child.
- **Consoling unlock:** after repeated misses, the session ends kindly rather than trapping the child forever.
- **Parents:** Accessibility confine applies only during an active Hard session; Home/Back do not bypass that session, but practice and Setup remain free.

## Time-earn and mastery (not speed-grinding)
Minutes in the jar **add free time on top of** the normal schedule window (default about **40±10 minutes**). They never shrink that window.

| Design choice | Why |
|---------------|-----|
| Correct answers earn; skips earn 0 | Rewards engagement with the fact, not avoidance |
| Latency bands (fast / thinking / slow) with a **floor** for slow-but-correct | Faster recall can earn more, but a tired correct answer still counts — no punishment spiral |
| Session cap (default **120 minutes**, admin-tunable) | Prevents grinding endless quizzes for unbounded free time |
| Earn UI only when remaining schedule time is under the cap | Earning matters before the next lock; no bait when it cannot help |
| Occasional post-unlock continue/quit offer (~40%, once per local day) | Invites voluntary extra practice after the lock duty is done |

Details: [`docs/TIME_EARN.md`](TIME_EARN.md).

## Curriculum quality and guardrails
Packs are **signed / versioned** content, not an open generative feed on the child’s device. SI (batch tooling) may suggest curation; **parents decide** what runs. Expansion toward many languages and subjects is intentional and slow: real research, educators, education-oriented licenses, and ongoing human guardrails so material stays age-fit and education-worthy — not controversial topics out of context.

Mission and nonprofit intent: [https://quizgate.app/mission](https://quizgate.app/mission).

## Evidence and feedback
- Local on-device stats (asked / right / wrong / skipped) help families see progress.
- Optional anonymous diagnostics stay opt-in / off by default in current builds (see privacy policy).
- Parents and teachers: [https://quizgate.app/support](https://quizgate.app/support) · GitHub issues on the public repo.

## Related docs
- [`docs/CURRICULUM.md`](CURRICULUM.md) — pack format, sync, SI batch  
- [`docs/TIME_EARN.md`](TIME_EARN.md) — earn bands and caps  
- [`docs/501C3_PLAN.md`](501C3_PLAN.md) — educational nonprofit direction  
