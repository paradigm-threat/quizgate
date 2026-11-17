# Google Play setup — QuizGate

Step-by-step to create the listing, upload graphics, and start testing.
Copy-paste text lives in [`store/play/LISTING_COPY.txt`](../store/play/LISTING_COPY.txt).
Upload-ready graphics live in [`store/play/upload/`](../store/play/upload/).

---

## Console links

| What | URL |
|------|-----|
| **Play Console (home)** | https://play.google.com/console |
| **Create developer account** | https://play.google.com/console/signup |
| **Developer account help** | https://support.google.com/googleplay/android-developer/answer/6112435 |
| **Create & set up an app** | https://support.google.com/googleplay/android-developer/answer/9859152 |
| **Closed testing / 12 testers rule** | https://support.google.com/googleplay/android-developer/answer/14151465 |
| **Families policy** | https://support.google.com/googleplay/android-developer/answer/9893335 |
| **Data safety** | https://support.google.com/googleplay/android-developer/answer/10787469 |
| **Graphic assets specs** | https://support.google.com/googleplay/android-developer/answer/9866151 |
| Privacy policy (live) | https://quizgate.app/privacy |
| Marketing site | https://quizgate.app |

Package name (must never change once claimed): `net.clevertree.quizgate`

---

## 0. Before you click Create app

1. **Developer account** — one-time **US$25**. Prefer an **organization** account (Clevertree LLC + D‑U‑N‑S) so the brand owns the listing. Personal accounts created after **13 Nov 2023** need a **closed test with ≥12 testers opted in continuously for 14 days** before production access.
2. **Identity / payments** — complete Play Console identity verification and payments profile when prompted.
3. **Release signing** — Play needs an **Android App Bundle (.aab)** signed with a **release keystore**, not the debug APK from CI today. Until AAB + release keystore exist, you can still create the app, fill the listing, and use **internal testing** only after you have a signed AAB (or enroll in Play App Signing and upload a first bundle).
4. **Contact inbox** — use a monitored address (suggested: `support@clevertree.net`). Confirm the mailbox exists before publishing.

---

## 1. Create the app

1. Open https://play.google.com/console → **Create app**
2. App name: **QuizGate**
3. Default language: English (United States) — or your primary locale
4. App or game: **App**
5. Free or paid: **Free**
6. Declarations: accept Play policies / US export laws as applicable
7. Create → you land on the app dashboard

---

## 2. Dashboard checklist (do these before a closed test)

In the left nav, complete every item under **Grow** → **Store presence** and **Policy** → **App content**:

### Store presence → Main store listing

Paste from [`store/play/LISTING_COPY.txt`](../store/play/LISTING_COPY.txt):

- App name  
- Short description  
- Full description  

**Graphics** (files ready under `store/play/upload/`):

| Asset | File | Spec |
|-------|------|------|
| App icon | `icon_512.png` | 512×512 PNG |
| Feature graphic | `feature_graphic_1024x500.jpg` (or `.png`) | 1024×500, **no alpha** |
| Optional richer feature | `feature_graphic_promo_1024x500.jpg` | same size |
| Phone / tablet screenshots | `screenshots/*.jpg` | ≥2 required; 7 tablet frames (1920×1200), generic demo data |

Upload order suggestion for screenshots:

1. `05_quiz_math.jpg` — hero of the product  
2. `06_success_sheet.jpg` — play-mode success / ask-another  
3. `01_onboarding_welcome.jpg`  
4. `03_onboarding_schedule.jpg`  
5. `04_setup.jpg`  
6. `07_stats_history.jpg` — multi-day scores  
7. `02_onboarding_photos.jpg`  

Captured on SM-X210 (Galaxy Tab A9+) after `pm clear` + seeded demo stats (no personal names/packages). Recapture helper: `SERIAL=<id> ./scripts/capture_play_screenshots.sh`. Score widget is **not** auto-pinned — use Setup → **Add home score widget** (or Widgets picker → QuizGate → Today's score), then recapture `08_score_widget.jpg`. See [`WIDGET.md`](WIDGET.md).

> These frames are landscape tablet. For **phone** form-factor slots, capture a phone in portrait (1080×1920+) if Play asks for phone-specific screenshots. Tablet screenshots can fill the **7-inch / 10-inch tablet** slots.

Also set:

- Application category: **Education**
- Contact email + website + privacy policy URL (from listing copy)
- Store listing → save

### App content (Policy)

| Section | Answer summary |
|---------|----------------|
| Privacy policy | `https://quizgate.app/privacy` |
| Ads | **No** |
| Data safety | See draft in `LISTING_COPY.txt` (photos on-device; local stats; optional HTTPS curriculum sync; no ads SDKs). Local diagnostics: [`TELEMETRY.md`](TELEMETRY.md). Future anonymous progress sync (`api.quizgate.app`, UUID only): [`DATA_PLANE.md`](DATA_PLANE.md) — **upload OFF** until Data safety + privacy are updated for production. |
| Target audience | Include children (e.g. 5–8); Families policy applies |
| Content rating | Complete IARC questionnaire (all “no” violence/sex/language/etc. for this app) |
| News / COVID / Financial / Health | Not applicable |
| Government apps | No |
| App access | No special login — leave “all features available without restrictions” |

---

## 3. Testing tracks

Recommended path:

1. **Internal testing** — upload first AAB; add your own Google accounts; verify install from Play  
2. **Closed testing** — add ≥12 testers (email list or Google Group); keep them opted in **14 continuous days** if you need production on a post-2023 personal account  
3. Apply for **Production** when the closed-test bar and policy checklist are green  

Help: https://support.google.com/googleplay/android-developer/answer/9845334

---

## 4. Technical blockers still in this repo

| Item | Status |
|------|--------|
| Sideload **APK** (CI artifact) | Ready — GitLab CI (primary) builds `QuizGate.apk`; GitHub Actions when available |
| Play **AAB** + release keystore | **Not ready** — human Gradle/bundletool + Play App Signing; keep keystore out of git |
| `targetSdk` 34 | Current manifest target |
| Privacy URL live | Yes |
| Icon 512 + feature 1024×500 | Yes (`store/play/upload/`) |
| Screenshots (≥2) | Yes — 7 tablet frames incl. success + stats; add phone portraits before phone-primary launch |
| In-app privacy link | Confirm Setup / about points at privacy URL before review |

Until AAB exists: finish the **listing + App content** now; use CI APK for sideload QA; build the release bundle as the next engineering task.

---

## 5. Local asset map

```
store/play/
  LISTING_COPY.txt          ← paste fields
  upload/
    icon_512.png
    feature_graphic_1024x500.jpg
    feature_graphic_1024x500.png
    feature_graphic_promo_1024x500.jpg
    screenshots/
      01_onboarding_welcome.jpg
      02_onboarding_photos.jpg
      03_onboarding_schedule.jpg
      04_setup.jpg
      05_quiz_math.jpg
      06_success_sheet.jpg
      07_stats_history.jpg

design/android_icon_kit/A_gate_question/   ← source masters (Gate mark = production default)
```

Source kit also has Lock / Open variants under `B_lock_numbers/` and `C_open_gate_success/` if you want alternate store art later.

---

## 6. Permissions story (for Console “sensitive permissions” / review notes)

| Permission | Why |
|------------|-----|
| Exact alarms | Schedule quizzes (30±15 + morning) |
| `POST_NOTIFICATIONS` | Soft-gate reminder / FGS fallback |
| Battery unrestricted | OEM reliability for alarms |
| Full-screen intent | Alarm-style quiz open |
| Appear-on-top / overlay | Launch when background activity starts are blocked |
| `PACKAGE_USAGE_STATS` | Return child to previous app after unlock |
| `READ_MEDIA_IMAGES` | Optional gallery backdrops |

Onboarding / Setup prompts these **one at a time** via `ScheduleAuth` (exact → notif → battery → FSI → overlay → usage). Photos stay optional.

We do **not** currently require Accessibility or Device Admin for the soft gate.

---

## 7. Reviewer notes (paste into “App access” / notes if useful)

```
QuizGate is a parent-configured soft quiz gate. No login.
1. Install → complete or skip onboarding (photos, 30±15 schedule, Auto/Performance/Slim) → Setup → Arm scheduling.
2. Grant exact alarms, notifications, battery unrestricted, full-screen alerts, appear-on-top, and usage access (return to last app); photos optional.
3. Tap “Test quiz now” to open a quiz without waiting for an alarm.
Forever free; no ads; no IAP. Privacy: https://quizgate.app/privacy
```
