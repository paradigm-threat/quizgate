# QuizGate

Clevertree soft quiz gate for young kids: every half hour (±15m → next fire in **15–45 min**) + morning gate (07:00; quiet hours 20:00–07:00), math digit pad, local/bundled photo transitions, unlock after 3 misses or bypass (Home). Today’s stats on the home screen (right / wrong / skipped). No MacroDroid. **Forever free — no ads, no IAP, no subscription.**

**Positioning:** compete with [Pickles](https://pickles.to/) (math for screen time · no accounts · no tracking · no fees) on **Android**, where Pickles is unavailable. Play peers like [Screenwise](https://play.google.com/store/apps/details?id=com.makeme.learn.screenwise) are heavier parental-control suites — not the same education-first narrative. Curriculum packs sync optionally; **SI** (Super Intelligence, i.e. AI) batch-curates packs — parents keep final decisions. See [`docs/CURRICULUM.md`](docs/CURRICULUM.md).

Package: `net.clevertree.quizgate`

- Site: https://quizgate.app  
- Privacy: https://quizgate.app/privacy  
- GitLab (**primary**): https://gitlab.com/clevertree/quizgate  
- GitHub (secondary / Actions CI): https://github.com/paradigm-threat/quizgate  
- Clevertree thank-you: https://clevertree.net/thankyou  
- Vercel team: **clevertrees-projects** · project **quizgate** · root directory `site` · production branch `main` · domain `quizgate.app`

**Team note — remotes:** push/pull **GitLab** first. Site deploys historically expect GitHub → Vercel; if GitHub is unavailable, treat GitLab as source of truth for the app and sync site separately.

Photos are loaded from the device gallery at runtime (MediaStore). No personal photos are bundled in the APK or repo.

## Build

```bash
./scripts/run_regression.sh   # packs + SI heuristics + unit tests
./scripts/build_apk.sh
```

**CI/CD**
- **GitLab CI**: **disabled** (compute quota). `.gitlab-ci.yml` has `workflow: rules: - when: never`. Re-enable notes are in that file / `AGENTS.md`.
- **GitHub Actions** (current CI): `regression` → `android-apk` (uploads `QuizGate.apk`) + `ios-stub` on `main` / PRs
- Device e2e (`SERIAL=<id> ./scripts/e2e_phone.sh`) stays **manual**
- Play **AAB + release keystore** are still human steps — CI ships sideload APK only

The **iOS stub** under `ios/QuizGateStub/` is an empty branded shell for CI only — **not** an App Store / Screen Time product.

## Marketing site

Static site in `site/` (seeded from the Website Favicon Themes Kit). Local preview:

```bash
python3 -m http.server 8000 --directory site
```

Play Store setup + listing copy: [`docs/PLAY_STORE.md`](docs/PLAY_STORE.md) · [`store/play/`](store/play/).  
Store publish notes + competitor table: [`docs/STORE_PUBLISH.md`](docs/STORE_PUBLISH.md).  
Performance Auto / Performance / Slim: [`docs/PERFORMANCE.md`](docs/PERFORMANCE.md).  
Local diagnostics (upload OFF): [`docs/TELEMETRY.md`](docs/TELEMETRY.md).  
Anonymized Relay progress plane (`api.quizgate.app`): [`docs/DATA_PLANE.md`](docs/DATA_PLANE.md).  
Remote admin (`admin.quizgate.app`): [`docs/REMOTE_ADMIN.md`](docs/REMOTE_ADMIN.md).  
Email (outbound Gmail 2FA + inbound `batch@quizgate.app` Maildir): [`docs/EMAIL.md`](docs/EMAIL.md).  
Agent orientation: [`AGENTS.md`](AGENTS.md).

## Install / test

First launch opens skippable onboarding: photos → schedule (default 30±15) → performance (**Auto** / Performance / Slim) → grants one-at-a-time (`ScheduleAuth`: exact alarm, notifications, battery unrestricted, full-screen intent, appear-on-top, usage access) → Setup. Drop ChatGPT exports into `UnlockQuiz/assets/backdrop_catalog/{success,fail}/` using the filenames in that folder’s README.

**Fully working** soft gate needs those grants (photos optional). Setup shows a requirements checklist and prompts the next missing gap with a cooldown.

```bash
SERIAL=<device> ./scripts/e2e_phone.sh
# or
adb -s <device> install -r build/apk/QuizGate.apk
adb -s <device> shell am start -n net.clevertree.quizgate/.OnboardingActivity
# Setup directly (skips intro if already done):
adb -s <device> shell am start -n net.clevertree.quizgate/.SetupActivity
```

### Wireless debugging (optional)

Pairing port ≠ connect port. On the tablet: Developer options → Wireless debugging → Pair with pairing code, then:

```bash
adb pair <ip>:<pairing_port> <code>
adb connect <ip>:<connect_port>
adb devices -l
```

Samsung/ColorOS may drop wireless debugging on Wi‑Fi roam — prefer USB or re-pair; do not put pairing codes or device IPs in the repo.

Force quiz:

```bash
adb shell am start -n net.clevertree.quizgate/.QuizActivity --es trigger adb_force
```

Short interval (2 min) then arm:

```bash
adb shell am start -n net.clevertree.quizgate/.SetupActivity --el interval_ms 120000 --ez auto_arm true
```

Fire quiz via alarm receiver (no MacroDroid):

```bash
adb shell am broadcast -a net.clevertree.quizgate.ACTION_DEBUG_FIRE -n net.clevertree.quizgate/.AlarmReceiver
```

On OPPO/ColorOS: open the app once, accept **battery optimization exemption**, notifications, and **photos** permission so exact interval alarms and gallery transitions work.

Dump stats (debuggable / run-as):

```bash
adb shell run-as net.clevertree.quizgate cat files/stats/sessions.jsonl
adb shell run-as net.clevertree.quizgate cat files/stats/events.jsonl
adb shell run-as net.clevertree.quizgate cat files/stats/diagnostics/schedule.jsonl
```

Logcat triage tag: **`QuizGateDiag`**. Network upload of diagnostics stays **OFF** until a published Clevertree ingest exists.

## Behavior

- Schedule: default **30±15 min**; morning alarm; re-arm on boot, app update (`MY_PACKAGE_REPLACED`), and time/timezone change
- Header: “Please answer this question”
- Curriculum: bundled **basic-math v3** — addition under 10 + sparse subtraction (~5 items / ~20% pool); math asks randomly as fill-in (`equation`) or Pickles-style **True/False** (same catalog rows — see [`docs/CURRICULUM.md`](docs/CURRICULUM.md))
- Math: spoken + `2 + 5 = _` (digit pad by default)
- Wrong → fade + “Retry in” countdown → new question
- 3 wrong → fail overlay + 6s countdown → unlock
- Leave without answering → bypass (no reopen), logged as skipped
- YouTube / music: soft-pause **before** the quiz is brought to the foreground (exclusive audio focus + media-pause burst + short settle so auto-PiP is less likely); resume after unlock (best-effort). On API 31+ the quiz window uses `setHideOverlayWindows` for foreign `TYPE_APPLICATION_OVERLAY` windows. System PiP cannot be force-closed — quiz chrome always reserves a modest bottom-end band (stats/margins) for that corner; pinned-task detection is diagnostics-only. Expanding PiP leaves the quiz and counts as bypass. **Usage access** required to return to the prior app after the session
- Success / error transitions use random images from the device gallery when permitted
- Performance: **Auto** (default) uses full UX when healthy; switches to **Slim** on weak device, low battery, or battery saver — see [`docs/PERFORMANCE.md`](docs/PERFORMANCE.md)
