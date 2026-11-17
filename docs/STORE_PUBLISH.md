# Store publish checklist — QuizGate

QuizGate is **not** submitted to Google Play or the Apple App Store in the current pass.
This document captures what it takes, plus an honest competitor snapshot.

**Play Console walkthrough + listing copy + upload assets:**
[`docs/PLAY_STORE.md`](PLAY_STORE.md) · [`store/play/`](../store/play/)

Marketing / privacy URL (required by both stores later):
https://quizgate.app/privacy

Clevertree thank-you page (post-download / signup, separate site):
https://clevertree.net/thankyou

App source of truth: **GitLab** `clevertree/quizgate` (GitHub may be suspended). CI APK from GitLab; Play AAB + release keystore remain manual.

---

## Competitors

### North star (same narrative — different platform)

| App | Platform | Monetization | Notes |
|-----|----------|--------------|--------|
| **[Pickles](https://pickles.to/)** | **iOS only** | Free · no ads · no accounts · no tracking | Math earns minutes; locks via Apple Screen Time / FamilyControls; K–5 curriculum. Explicitly **no Android** today (locking would be a separate project). **This is the product QuizGate wants to compete with** — same values, Android focus. |

### Android peers (lesser / different product shape)

| App | Platform | Monetization | Notes |
|-----|----------|--------------|--------|
| **[Screenwise](https://play.google.com/store/apps/details?id=com.makeme.learn.screenwise)** | Android (Play) | Claims free / no ads; Play still shows **In-app purchases**; data-safety lists personal + financial types | Parental-control suite: app blocker, daily limits, web filter + optional math-to-earn minutes. Heavier than Pickles-class “just education.” **Primary Android store competitor to displace on narrative.** |
| **Solve2Open** | Android (Play) | Free, no ads, no IAP | Simple add/sub unlock of selected apps; Accessibility; Families Policy |
| **PlugBrain** | Android (Play + F-Droid) | Free forever, OSS, no ads/IAP | Math gate for distraction apps (**adult** focus); offline |
| **Scrolliosis** | Android (GitHub) | Free OSS | Custom knowledge vault questions |

### Other iOS (same space, not the north star)

| App | Platform | Monetization | Notes |
|-----|----------|--------------|--------|
| **Think2Unlock** | iOS | Free, no ads, no account required | Math/vocab gates; optional parent cloud dashboard |

**Paid / subscription** (same idea, not free): LockdIn, Math Applocker, TutorLock,
Applatch Kids, Kids Focus, BrainKey (monetization unclear), others with trials/IAP.

### QuizGate positioning

- **Compete with Pickles on values** (education earns time; no kids accounts; no tracking; no fees) — **on Android**, where Pickles is absent
- **Not** a Screenwise-style control suite (web filter, device-admin lockdown, IAP-adjacent Play listing) as the product identity
- Clevertree **forever-free** policy (no ads, no IAP, no paywall — all products)
- Roadmap differentiators: progressive curriculum packs + anonymous **SI** (Super Intelligence, i.e. AI) batch curation; soft gate now, harder lock later if Android APIs allow
- Soft half-hour/morning quiz today (basic-math v3: addition + sparse subtraction); gallery/bundled backdrops on-device; optional curriculum sync; Auto/Performance/Slim profiles

---

## Google Play

### Account

- One-time **US$25** Play Console fee
- Prefer **organization** account (Clevertree LLC + D-U-N-S) for brand continuity
- Personal accounts created after Nov 2023 need **closed test: ≥12 testers opted in
  continuously for 14 days** before production

### Technical gaps vs current QuizGate APK

- Play requires **AAB** (Android App Bundle), not only a sideload APK — need
  Gradle/`bundletool` pipeline and a **release keystore** (not the debug keystore)
- `targetSdk` must meet current Play minimum (moving target)
- Package `net.clevertree.quizgate` is fine; register Play package name / developer
  verification as required
- **Privacy policy URL** (public HTTPS — host on quizgate.app) + in-app link
- **Data safety** form (declare accurately; gallery read is on-device)
- Declare **Contains ads: No**; no IAP
- **Target audience / Families**: if targeting children, comply with Play Families /
  COPPA-style rules. Solve2Open already marks Families Policy — QuizGate should plan
  the same
- Store listing: short/full description, screenshots (phone + tablet), feature graphic
  1024×500 (kit already has), content rating (IARC), contact email
- Sensitive permissions story: exact alarms, notifications, battery exemption,
  full-screen intent, overlay, usage stats (return-to-app), optional
  `READ_MEDIA_IMAGES` — explain in Console; Accessibility only if we later add hard
  app-intercept (current soft quiz may not need it)
- Local diagnostics / Play triage: bounded `files/stats/` + `QuizGateDiag` logcat;
  upload OFF — see [`TELEMETRY.md`](TELEMETRY.md)

**Effort estimate:** ~1–2 weeks calendar if org account already exists and 12 testers
can be recruited; longer if waiting out the 14-day closed test on a new personal account.

---

## Apple App Store

### Account

- **Apple Developer Program US$99/year** (individual or organization)

### Product reality

- **No production iOS QuizGate exists today** — CI builds an `ios/QuizGateStub`
  shell only (not an App Store candidate)
- True app locking like Pickles requires **Family Controls / Screen Time APIs** +
  Apple’s **Family Controls distribution entitlement** (manual approval) before
  TestFlight/App Store
- Soft “interval quiz” without shielding other apps is easier to ship but weaker vs
  Pickles — and **Android remains the priority** because Pickles already owns the
  clean iOS narrative

### Store checklist

- Privacy Nutrition Labels + privacy policy URL
- Age rating questionnaire; kids audience → extra care
- TestFlight → App Review
- Screenshots for required device sizes; export compliance; EU DSA trader status if
  distributing in EU
- Declare no ads / no IAP; forever-free messaging in listing

**Effort estimate:** multi-week to multi-month (new app + entitlement review + Screen
Time UX). Defer until Android product matches Pickles-class values in market.

---

## Manual QA (not in CI)

`./scripts/e2e_phone.sh` needs a real device/`SERIAL`. Optional follow-up: Android
emulator job in Actions (slow/flaky).
