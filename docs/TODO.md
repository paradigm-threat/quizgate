# TODO — QuizGate educational nonprofit (new 501c3)

## How to use this file
Agents and humans: when work arrives from **other sources** (GitHub/GitLab PRs,
branches named `docs/todo`, issues, chat handoffs), **read and update this file**
before implementing. Merge doc-only PR/branch content here, check off completed
items, and add new bullets so nonprofit / visibility / filing work stays in one
place. Do not leave parallel TODO lists that diverge from `docs/TODO.md`.

## Decision
- [x] **New Georgia nonprofit corp**, unrelated to converting Clevertree LLC
- [x] Clevertree LLC remains for-profit; MyProblem stays out of the filing
- [x] Filing focus: QuizGate educational program only
- [x] **Near term:** do everything useful **except** submitting Form 1023 / 1023-EZ
- [x] Public messaging: not a recognized 501(c)(3) yet; that is the direction if the MVP succeeds

## Repo Visibility
- [x] paradigm-threat/quizgate **public** (GitHub)
- [x] clevertree/quizgate **public** (GitLab)
- [x] paradigm-threat/clevertree-chat **public**
- [x] myproblem + clevertree-site confirmed **public**
- [x] Keep secret stores private (`clevertree-chat-auth`, etc.) — policy noted; do not flip auth repos public

## Phase 1 — Now (public intent + educational record)
- [x] Mission / nonprofit-intent copy on quizgate.app (`/` + `/mission`)
- [x] Play listing draft states forever-free + nonprofit direction (`store/play/LISTING_COPY.txt`)
- [x] Pedagogy doc + site page (`docs/PEDAGOGY.md`, `/pedagogy`)
- [x] Soft support path — no fake 501c3 receipts (`/support`)
- [x] Feedback channel for parents/teachers (email + GitHub issues on `/support`)
- [ ] Collect usage stats / testimonials as MVP grows (ongoing after Play)

## Phase 2 — Near term (form entity; still before Form 1023)
In-repo **drafts** ready under [`docs/nonprofit/`](nonprofit/). Humans must file/sign:
- [x] Draft name options, Articles, bylaws, COI, IP license term sheet
- [ ] **Human:** choose legal name + Georgia registered agent ([`nonprofit/HUMAN_NEXT_STEPS.md`](nonprofit/HUMAN_NEXT_STEPS.md))
- [ ] **Human:** file Articles of Incorporation (Georgia nonprofit)
- [ ] **Human:** adopt bylaws + COI; appoint 3+ directors
- [ ] **Human:** sign IP license/assignment (Clevertree LLC → nonprofit)
- [ ] **Human:** open nonprofit bank account; obtain EIN

## Phase 3 — Later (federal exemption)
- [ ] Confirm 1023-EZ eligibility
- [ ] **File** Form 1023-EZ or full 1023 when fundraising is real
- [ ] Financial projections for educational expansion
- [ ] Narrative: QuizGate only

## Phase 4 — When soliciting donations
- [ ] GA charitable solicitation registration
- [ ] Annual renewal reminder

## Risk Watch
- [x] Public copy never claims current 501(c)(3) status
- [x] Forever-free / no ads / no IAP stated on site + Play draft
- [ ] Document arm’s-length LLC↔nonprofit IP when signing
- [x] Donation purpose = curriculum, licenses, guardrails, deployment/access — not Relay geo-vision
