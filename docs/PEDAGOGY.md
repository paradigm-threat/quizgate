# QuizGate pedagogy

*Public educational record — how QuizGate teaches, who it is for, and how time-earn reinforces mastery without speed-grinding.*

## Purpose
QuizGate replaces stretches of idle tablet time with short, age-fit practice. Learning is the gate to more screen time — not an afterthought bolted onto a surveillance suite. Parents keep every final decision: schedule, curriculum pack, sync on/off, and Hard lock permissions.

## Learners and age bands
| Pack (current) | Age band (manifest) | Focus |
|----------------|---------------------|--------|
| **basic-math** v7 | about **4–7** | Progression **levels**: L0 add-under-8 4-option MC grid; L1 add-within-10 + easy subtract; L2 zero-identity + more subtract; L3 light multiply. Idle MC hints until first correct of the day |

Future packs will publish their own `ageMin` / `ageMax` and learning objectives the same way. **Levels** gate how deep the pool goes (default device level **0**); age tiers remain a separate optional filter.

**Level-up bar (default):** at least **75%** correct among answered questions, at least **5** correct, and **not more skips than answered** (`skipped ≤ right + wrong`). Admin may override per device (including an optional skip-%-of-asked cap). SI uses these as soft gates (may exception with a reason). See [`CURRICULUM.md`](CURRICULUM.md).

## Learning objectives (basic-math v5)
By working through scheduled Hard quizzes and optional practice, a child practices:

1. **Addition under 8 (level 0)** — tiny addends (sums ≤7), **4-option multiple-choice grid only** (no keypad / T/F yet); progressive idle hints while she has no rights yet today.
2. **Addition within 10 (level 1)** — larger addends; equation / T/F / choice allowed; a few easy subtracts enter.
3. **Subtraction within 10** — take-away within the same range (expands at level 2).
4. **Zero identity (level 2)** — `n + 0` / `0 + n` as a distinct skill (`add_with_zero`).
5. **Light multiplication within 10 (level 3)** — early facts (small factors).
6. **Multiple representations** — from level 1 up, the same fact can appear as equation blank, true/false, or multiple choice.

Division and broader grade bands are deferred to later packs. Color / “which is red” style items return only with UI that does not rely on text labels alone.

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
