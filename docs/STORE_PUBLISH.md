# Store publish checklist — QuizGate

QuizGate is **not** submitted to Google Play or the Apple App Store in the current pass.
This document captures what it takes, plus an honest competitor snapshot.

Marketing / privacy URL (required by both stores later):
https://quizgate.clevertree.net/privacy

---

## Competitors (re-check)

Cannot validate “only one totally free app.” Several live products match the
quiz/math-to-earn-screen-time concept with **no ads and no IAP**:

| App | Platform | Monetization | Notes |
|-----|----------|--------------|--------|
| **Solve2Open** | Android (Play) | Free, no ads, no IAP | Add/sub unlock of selected apps; local; Accessibility; Families Policy |
| **PlugBrain** | Android (Play + F-Droid) | Free forever, OSS, no ads/IAP | Math gate for distraction apps (adult focus); offline |
| **Pickles** | iOS | Free, no ads, no accounts | Kids K–5 math earns minutes; Screen Time APIs |
| **Think2Unlock** | iOS | Free, no ads, no account required | Math/vocab gates; optional parent cloud dashboard |
| **Scrolliosis** | Android (GitHub) | Free OSS | Custom knowledge vault questions |

**Paid / subscription** (same idea, not free): LockdIn, Math Applocker, TutorLock,
Applatch Kids, Kids Focus, BrainKey (monetization unclear), others with trials/IAP.

### QuizGate positioning

- Not “the only free app” — instead: **CleverTree forever-free** (no ads, no IAP,
  no paywall — company policy for all products)
- Differentiates on **reward-based time + progressive curriculum + anonymous AI
  batch curation** (roadmap), soft hourly/morning gate, math+colors now, no
  personal data/photos in product cloud
- Solve2Open remains the closest **simple** free Android peer; Pickles/Think2Unlock
  own free iOS kids space; PlugBrain is free but adult-focus

---

## Google Play

### Account

- One-time **US$25** Play Console fee
- Prefer **organization** account (CleverTree LLC + D-U-N-S) for brand continuity
- Personal accounts created after Nov 2023 need **closed test: ≥12 testers opted in
  continuously for 14 days** before production

### Technical gaps vs current QuizGate APK

- Play requires **AAB** (Android App Bundle), not only a sideload APK — need
  Gradle/`bundletool` pipeline and a **release keystore** (not the debug keystore)
- `targetSdk` must meet current Play minimum (moving target)
- Package `net.clevertree.quizgate` is fine; register Play package name / developer
  verification as required
- **Privacy policy URL** (public HTTPS — host on quizgate.clevertree.net) + in-app link
- **Data safety** form (declare accurately; gallery read is on-device)
- Declare **Contains ads: No**; no IAP
- **Target audience / Families**: if targeting children, comply with Play Families /
  COPPA-style rules. Solve2Open already marks Families Policy — QuizGate should plan
  the same
- Store listing: short/full description, screenshots (phone + tablet), feature graphic
  1024×500 (kit already has), content rating (IARC), contact email
- Sensitive permissions story: exact alarms, notifications, `READ_MEDIA_IMAGES`,
  battery exemption — explain in Console; Accessibility only if we later add hard
  app-intercept (current soft quiz may not need it)

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
  Pickles/Think2Unlock

### Store checklist

- Privacy Nutrition Labels + privacy policy URL
- Age rating questionnaire; kids audience → extra care
- TestFlight → App Review
- Screenshots for required device sizes; export compliance; EU DSA trader status if
  distributing in EU
- Declare no ads / no IAP; forever-free messaging in listing

**Effort estimate:** multi-week to multi-month (new app + entitlement review + Screen
Time UX), not a same-sprint task as Android Play.

---

## Manual QA (not in CI)

`./scripts/e2e_phone.sh` needs a real device/`SERIAL`. Optional follow-up: Android
emulator job in Actions (slow/flaky).
