# 501(c)(3) Approach Plan — New nonprofit for QuizGate
*Created: 2026-11-19 · Updated: 2026-11-19*

## Decision (locked)
**Form a new Georgia nonprofit corporation**, apply for 501(c)(3) for that entity, and keep it **separate from Clevertree LLC**.

- Clevertree LLC stays a for-profit (MyProblem and other commercial work stay there).
- The nonprofit’s purpose is **QuizGate as an educational program** (kids’ math / curriculum that replaces idle screen time with learning).
- Do **not** convert Clevertree LLC. Do **not** put MyProblem in the filing narrative.
- Clevertree Chat is optional/later — not required for this filing.

The IRS exempts the **organization**, not the app name. QuizGate can still be the public face; the applicant is the new nonprofit.

## Relationship to Clevertree LLC
Treat the LLC and nonprofit as arm’s-length:

1. **IP:** Clevertree LLC (or current copyright holders) **assigns or licenses** QuizGate code, name, store listings, and domain rights to the nonprofit in writing (board-approved on both sides if needed).
2. **Money:** No informal mixing of LLC and nonprofit bank accounts. Donations go only to the nonprofit.
3. **Brand:** Prefer a distinct legal name for the nonprofit (e.g. educational foundation / institute). “Clevertree” may appear as a licensed brand later; the determination letter should not read as “Clevertree LLC went nonprofit.”
4. **People:** Founder may serve, but IRS prefers **3+ directors** with independence — not only LLC owners/family.

## Note — Repo Visibility
Public docs and source are what examiners can cite. Status after 2026-11-19 visibility pass:
- **Public:** `paradigm-threat/quizgate` (GitHub), `clevertree/quizgate` (GitLab), `paradigm-threat/myproblem`, `paradigm-threat/clevertree-chat`, `paradigm-threat/clevertree-site`
- **Keep private:** auth / credential stores (e.g. `clevertree-chat-auth`)

Track checklist items in [`docs/TODO.md`](TODO.md). When TODO-style work arrives via PRs or other channels, fold it into that file.

## Phase 1 — Strengthen the Educational Record (Weeks 1–4)
1. Publish a public pedagogy doc for QuizGate: learning objectives per curriculum pack, age bands, how the time-earn mechanic reinforces mastery vs. speed-grinding.
2. Add a "Why this exists" / mission statement page to **quizgate.app** that leads with education and accessibility, not product features.
3. Document the no-tracking, no-ads, no-IAP stance on the public site (already in README — surface it on the site).
4. Gather any usage stats, testimonials from parents/teachers, or pilot feedback. Even informal.

## Phase 2 — Form the Nonprofit (Weeks 3–6)
1. Choose legal name + registered agent (Georgia).
2. File **Articles of Incorporation** (Georgia nonprofit corp). ~$100. Purpose clause must be educational/charitable in IRS-compatible language (and dissolution language that satisfies 501(c)(3)).
3. Adopt **bylaws**: educational mission, no private inurement, conflict-of-interest policy, dissolution clause (assets to another 501c3 or government).
4. Appoint **3+ directors** (independent board preferred).
5. Execute **IP license or assignment** of QuizGate from Clevertree LLC → nonprofit.
6. Open a nonprofit bank account; keep LLC finances separate.

## Phase 3 — Federal Filing (Weeks 6–10+)
1. Get **EIN** for the new nonprofit (free, irs.gov).
2. Confirm Form **1023-EZ** eligibility (budget thresholds + organizing document language). If eligible, ~$275; otherwise full Form 1023 (~$600).
3. Narrative: education-first mission, QuizGate activities, donation-only revenue. No MyProblem. No “we are Clevertree LLC.”
4. Reference: curriculum docs, mission pages, bylaws, financial projections (donations only, no program fees).

Determination letters often take **months**; plan cashflow accordingly.

## Phase 4 — Georgia State Registration (parallel with Phase 3)
1. Register charitable solicitation with Georgia Department of Agriculture if soliciting donations from GA residents.
2. Initial registration fee + annual renewal (verify current amounts).

## Risk Flags
- **Commerciality:** no paid tiers or ads on QuizGate.
- **Private inurement:** any founder compensation must be reasonable, documented, board-approved.
- **Related-party deals:** LLC↔nonprofit IP license must be documented (and fair) so it does not look like the LLC is draining the charity.
- **MyProblem / Clevertree LLC:** out of the nonprofit purpose statement and Form 1023 narrative.
- Frame donations as grants supporting education — not pay-what-you-want for software.

## Gaps before filing
- Attorney or EO advisor review of Articles / bylaws / 1023 narrative
- Conflict-of-interest policy (signed by directors)
- Written IP assignment or license from LLC
- 1023-EZ eligibility checklist vs current IRS instructions
- Bank account + organizing board resolutions

## Success Criteria
- New Georgia nonprofit incorporated (separate from Clevertree LLC)
- 501(c)(3) determination letter received
- Donor gifts tax-deductible to that entity
- Georgia charitable solicitation registered (when fundraising)
- Public QuizGate repos + mission docs as permanent evidence
