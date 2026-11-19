# Clevertree 501(c)(3) Approach Plan — QuizGate focus
*Created: 2026-11-19 · Updated: 2026-11-19*

## Scope
- **Primary:** QuizGate as an educational program (kids’ math / curriculum lock that replaces idle screen time with learning). Brand: Clevertree.
- **Entity:** Clevertree LLC (Georgia) today → convert **or** form a new Georgia nonprofit that owns/licenses QuizGate (see Phase 2). The IRS grants exemption to a **qualifying organization**, not to an app name alone.
- **Out of scope for the filing narrative:** MyProblem (do not mention). Treat it as a separate commercial/product effort.
- **Optional / later:** Clevertree Chat as supporting educational infrastructure — fine to keep public, but do not let Chat’s AI framing dominate the QuizGate determination request.

## Note — Repo Visibility
Public docs and source are what examiners can cite. Status after 2026-11-19 visibility pass:
- **Public:** `paradigm-threat/quizgate` (GitHub), `clevertree/quizgate` (GitLab), `paradigm-threat/myproblem`, `paradigm-threat/clevertree-chat`, `paradigm-threat/clevertree-site`
- **Keep private:** auth / credential stores (e.g. `clevertree-chat-auth`)

Track remaining checklist items in [`docs/TODO.md`](TODO.md). When new TODO-style work arrives via PRs or other channels, fold it into that file.

## Phase 1 — Strengthen the Educational Record (Weeks 1–4)
1. Publish a public pedagogy doc for QuizGate: learning objectives per curriculum pack, age bands, how the time-earn mechanic reinforces mastery vs. speed-grinding.
2. Add a "Why this exists" / mission statement page to **quizgate.app** that leads with education and accessibility, not product features.
3. Document the no-tracking, no-ads, no-IAP stance on the public site (already in README — surface it on the site).
4. Gather any usage stats, testimonials from parents/teachers, or pilot feedback. Even informal.
5. *(Optional)* Clevertree Chat essay / mission page — only if Chat is later brought into the same nonprofit umbrella.

## Phase 2 — Entity Structure (Weeks 3–6)
1. Decide: convert Clevertree LLC → nonprofit, or form a new Georgia nonprofit corp that owns/licenses QuizGate.
   - Conversion is cheaper but messier if there's any commercial history.
   - New entity is cleaner if Clevertree LLC has any revenue or contracts (recommended if MyProblem or other commercial work sits in the same LLC).
2. File Articles of Incorporation with Georgia Secretary of State (nonprofit). ~$100 filing fee. Purpose clause must be educational / charitable in IRS-compatible language.
3. Adopt bylaws. Key provisions: educational mission, no private inurement, conflict-of-interest policy, dissolution clause (assets to another 501c3 or government).
4. Appoint at least 3 directors (Georgia requires; IRS strongly prefers independent board, not just founder + family).
5. Assign or license QuizGate IP (code, trademarks, store listings) into the nonprofit if it is a new entity.

## Phase 3 — Federal Filing (Weeks 6–10+)
1. Get EIN from IRS (free, online, instant) for the nonprofit entity.
2. Confirm Form **1023-EZ** eligibility (budget thresholds + organizing document language). If eligible, ~$275; otherwise full Form 1023 (~$600). EZ is faster but still not automatic approval.
3. Narrative: education-first mission, QuizGate activities, donation-only revenue. No MyProblem. Avoid “consumer app / screen-time product” framing.
4. Reference: curriculum docs, mission pages, bylaws, financial projections (donations only, no program fees).

Determination letters often take **months**; plan cashflow accordingly.

## Phase 4 — Georgia State Registration (parallel with Phase 3)
1. Register with Georgia Department of Agriculture (charitable solicitation) if soliciting donations from GA residents — required once you ask for money.
2. Initial registration fee + annual renewal. Check current amounts; they've changed in recent years.

## Risk Flags to Watch
- **Commerciality doctrine:** if QuizGate ever adds paid tiers or ads, the exemption is at risk. Keep it clean.
- **Private inurement:** founder salary must be reasonable and documented (compensation studies, board approval).
- **Brand vs entity:** “Clevertree LLC brand” on a consumer app is fine for marketing; the applicant must be a nonprofit with educational purpose, not a for-profit LLC seeking a halo.
- **MyProblem:** do not describe it as part of the nonprofit purpose.
- Frame donations as grants supporting the educational mission — not pay-what-you-want for software.

## Gaps vs. a complete filing package (plan honesty)
This plan covers the main path. Still add before filing (also listed in `docs/TODO.md`):
- Attorney or experienced EO advisor review of Articles / bylaws / 1023 narrative
- Explicit conflict-of-interest policy
- IP assignment agreement if splitting LLC vs nonprofit
- 1023-EZ eligibility checklist against current IRS instructions
- Bank account + board resolutions once incorporated

## Success Criteria
- 501(c)(3) determination letter received for the QuizGate educational organization
- Donor gifts become tax-deductible
- Georgia charitable solicitation registered (when fundraising)
- Public repos + mission docs live as permanent evidence
