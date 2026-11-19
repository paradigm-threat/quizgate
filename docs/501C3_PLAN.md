# 501(c)(3) Approach Plan — New nonprofit for QuizGate
*Created: 2026-11-19 · Updated: 2026-11-19*

## Decision (locked)
**Form a new Georgia nonprofit corporation**, apply for 501(c)(3) for that entity, and keep it **separate from Clevertree LLC**.

- Clevertree LLC stays a for-profit (MyProblem and other commercial work stay there). LLC + nonprofit side by side is fine; co-mingling money/brand/board is not.
- **QuizGate is the technique** (short age-fit practice unlocks screen time). The nonprofit’s purpose is that educational program — not Clevertree’s other products.
- **Working legal name:** QuizGate Educational Foundation, Inc. (final name only at Georgia filing). Avoid Clevertree-echo names and bare product “Inc.” names — see [`nonprofit/NAME_OPTIONS.md`](nonprofit/NAME_OPTIONS.md).
- Do **not** convert Clevertree LLC. Do **not** put MyProblem in the filing narrative.

## Timing strategy (near term vs Form 1023)
**Do now / in the near future — everything except the full IRS Form 1023 (or 1023-EZ) filing:**

| Do now | Hold until MVP traction + fundraising readiness |
|--------|--------------------------------------------------|
| Public mission / nonprofit **intent** on quizgate.app + Play listing copy | Submit Form **1023 / 1023-EZ** |
| Pedagogy / educational-record docs | Expect determination letter (often months) |
| Choose nonprofit legal name; line up 3+ directors | GA charitable solicitation (when asking for money) |
| Draft Articles / bylaws; incorporate when directors ready | Large public fundraising campaigns |
| Draft IP license/assignment from Clevertree LLC → nonprofit | |
| EIN for the new corp once incorporated | |
| Keep forever-free / no-ads stance | |

**Why wait on Form 1023:** Play Store MVP and store approval do not require exemption. Filing has fees and compliance; little cash benefit until donations/grants. Public **intent** + real product still strengthen a later application.

Public messaging (site, Play, README): we are **not a recognized 501(c)(3) yet**. Direction is a dedicated educational nonprofit **if the MVP succeeds**. Until then Clevertree LLC ships the free app; donations (when enabled) are aimed at the educational expansion below — not at selling the product.

## What donations would fund (purpose narrative)
Not a Relay-style “node in every place” network. Global **use** means:

1. **Hardware & deployment** so families and partners can actually run QuizGate (devices, install support, practical rollout — not commercial SaaS fees).
2. **Access infrastructure** — modest regional servers / CDN for curriculum and updates where needed for good access (not geo-political Relay vision).
3. **Curriculum at world scale** — every language and education-worthy subject band that families need. That cannot be “AI dump only”; it needs **real research**, educators, and time.
4. **Legal & licensing** — education-oriented licenses and rights so packs stay lawful.
5. **Guardrails** — continual process, with people worldwide, so content stays within educational norms: no spillover into controversial topics out of context, and nothing that would not be deemed education-worthy for the age band.

That continual worldwide curriculum + compliance work is why donations matter later; the MVP itself stays forever free with no ads and no IAP.

## Relationship to Clevertree LLC
1. **IP:** Written assign or license of QuizGate code, name, store listings, domains to the nonprofit when it exists.
2. **Money:** Separate books; donations only to the nonprofit once recognized / registered to solicit.
3. **Brand:** Nonprofit legal name centers QuizGate + education (Foundation/Institute), not Clevertree / “Clever Treat.” Clevertree may appear as incubator/licensee narrative.
4. **People:** Prefer independent board majority over “LLC owners only.”
5. **Human checklist:** all filing/sign steps live only in [`nonprofit/HUMAN_NEXT_STEPS.md`](nonprofit/HUMAN_NEXT_STEPS.md).

## Note — Repo Visibility
- **Public:** `paradigm-threat/quizgate`, `clevertree/quizgate`, `myproblem`, `clevertree-chat`, `clevertree-site`
- **Keep private:** credential stores (e.g. `clevertree-chat-auth`)

Checklist: [`docs/TODO.md`](TODO.md). Fold PR/handoff TODOs into that file.

## Phase 1 — Educational record + public intent (now) — **done in-repo**
1. ~~Mission / nonprofit intent on quizgate.app~~ → `/mission` (technique + Foundation working name + LLC coexistence)
2. ~~Play listing draft~~ → `store/play/LISTING_COPY.txt`
3. ~~Pedagogy~~ → `docs/PEDAGOGY.md` + `/pedagogy`
4. ~~Forever-free / no ads / no IAP on site~~
5. ~~Soft support path (no fake receipts)~~ → `/support`
6. ~~Name framing / rejected Clevertree-echo names~~ → `nonprofit/NAME_OPTIONS.md`

## Phase 2 — Form nonprofit when ready (near term, before Form 1023)
**Drafts** in [`docs/nonprofit/`](nonprofit/). **Humans only** — see [`nonprofit/HUMAN_NEXT_STEPS.md`](nonprofit/HUMAN_NEXT_STEPS.md) (name check, agent, Articles, board/COI, IP sign, EIN, bank).

## Phase 3 — Federal exemption (later)
1. Confirm 1023-EZ eligibility.
2. File Form 1023-EZ (~$275) or full 1023 (~$600) when fundraising is real.
3. Narrative: QuizGate education + donation uses above; no MyProblem; not “Clevertree LLC converted.”

## Phase 4 — Georgia solicitation (when asking for money)
Register with GA Dept. of Agriculture; renew annually.

## Risk Flags
- Commerciality: no paid tiers / ads on QuizGate.
- Private inurement; arm’s-length LLC↔nonprofit IP deals.
- Do not claim current 501(c)(3) status before the determination letter.
- Frame future donations as grants for educational mission, not pay-what-you-want software.

## Success Criteria
- Public intent + MVP educational record live **before** Form 1023
- New Georgia nonprofit incorporated (separate from Clevertree LLC)
- 501(c)(3) determination letter when we file
- Donor gifts tax-deductible; GA solicitation when fundraising
