# Agent notes — QuizGate

Concise orientation for coding agents. Prefer updating existing docs over adding new files.

## Remotes & CI

- **Primary git remote:** GitLab `git@gitlab.com:clevertree/quizgate.git` (`gitlab`). Pull/push here first.
- **GitHub** (`paradigm-threat/quizgate` / `origin` / `github`) is writable again; keep GitLab as source of truth and sync when needed.
- **CI APK:** GitLab CI is **off** (quota — `.gitlab-ci.yml` `workflow: rules: - when: never`). Prefer **GitHub Actions** (`.github/workflows/ci.yml`) for regression + sideload APK. Play **AAB + release keystore** remain human / not in CI.
- **Re-enable GitLab CI:** restore the previous `workflow.rules` `if:` triggers documented at the top of `.gitlab-ci.yml`.
- Site: `site/` → https://quizgate.app (Vercel project `quizgate`, rootDirectory `site`). Deploy with `./scripts/deploy_site.sh` from the **repo root** (never `cd site && vercel` — that fails with Root Directory \"site\" does not exist). Keep only root `.vercel/`; do not add `site/.vercel`.
- Vercel Git: reconnect GitHub Login Connection then `vercel git connect https://github.com/paradigm-threat/quizgate.git --scope clevertrees-projects` for push-to-deploy (GitLab group repos need Pro).
- Never commit secrets, keystores, pairing codes, or device IPs into docs.

## Pull before edit

Other agents land schedule, onboarding, performance, telemetry, and media fixes concurrently. `git pull` (GitLab) before large edits; do not revert unrelated local work.

## Product defaults (verify in code)

| Area | Default / fact |
|------|----------------|
| Schedule | 40±10 min → fire window 30–50 min while interactive; quiet 20:00–07:00; morning 07:00. Minutes jar adds free time on top of that window (never shrinks it). Screen off cancels alarms + sets `pending_screen_lock`; wake opens Hard quiz (no idle RTC wake). Never `KEEP_SCREEN_ON` / `TURN_SCREEN_ON` — power-off stays off; a11y/onStop reintercept only while interactive |
| Re-arm | Boot / `MY_PACKAGE_REPLACED` / `TIME_SET` / wake-skip (grace or !pending) via `armWhileInteractive` only while interactive and not pending; else pend lock |
| Gate mode | Hard-only: Accessibility confine during active scheduled lock; Home/Back never bypass. Widget/Setup `TRIGGER_PLAY` is unlocked practice |
| Never stack locks | One Hard session → one unlock (roundSize 1). After unlock: 120s grace blocks reintercept while the screen stays on; pending cleared; FSI notif cancelled. Intentional screen-off clears grace + always pends; wake opens Hard quiz. ScreenLockGate registers from app start (not only a11y) |
| Grants (`ScheduleAuth`) | exact → notif → battery → **Accessibility** (Arm-critical) → overlay → FSI → usage. Overlay/FSI recommended for launch |
| Performance | Pref `auto` (default) / `performance` / `slim`; Auto → Slim on weak device, low battery, saver |
| Stats | `files/stats/` capped JSONL; day rollups 90d; Intake day-batch on open (async): closed days once, today ≤1×/day; no schedule |
| Time earn | Standalone rapid-fire jar minutes: `TimeEarnPolicy` bands + session cap (default 120m, admin `time_earn_cap_minutes`); play success **Earn more time** when remaining &lt; cap. Hard unlock: ~40% once/local-day offer of the same continue/Quit chrome (`PostUnlockPlayOfferPolicy`); Quit stays on lock restore path; further earns push jar onto the already-armed next alarm |
| Media | Usage access for return-to-app; pre-pause+settle before quiz foreground; resume after unlock; API 31+ hide overlays |
| Quiz UI | Centered in usable band; `QuizUiScale.scaleToFit` keeps chrome inside viewport (shrink if needed, never clip); DayNight colors + `UiContrastTest` (WCAG AA); 2D pan; overlays via MediaGate + OverlayGate |
| Curriculum | bundled `basic-math` v4 (add / subtract / light multiply; division later) |

## Key docs

- [`README.md`](README.md) — build, install, behavior overview
- [`docs/PERFORMANCE.md`](docs/PERFORMANCE.md) — Auto / Performance / Slim
- [`docs/TELEMETRY.md`](docs/TELEMETRY.md) — diagnostics paths + payload (live-debug)
- [`docs/DATA_PLANE.md`](docs/DATA_PLANE.md) — `api.quizgate.app` Intake + `quizgate-users` Relay tip (compile upload flag OFF; runtime opt-in)
- [`docs/PLAY_STORE.md`](docs/PLAY_STORE.md) · [`docs/STORE_PUBLISH.md`](docs/STORE_PUBLISH.md)
- [`docs/CURRICULUM.md`](docs/CURRICULUM.md) · [`docs/WIDGET.md`](docs/WIDGET.md)
- [`docs/PEDAGOGY.md`](docs/PEDAGOGY.md) · [`docs/501C3_PLAN.md`](docs/501C3_PLAN.md) · [`docs/TODO.md`](docs/TODO.md) · [`docs/nonprofit/`](docs/nonprofit/) — educational nonprofit (Form 1023 later)

## Device smoke (manual)

```bash
SERIAL=<id> ./scripts/e2e_phone.sh
# Wireless debugging: pair port ≠ connect port
#   adb pair <ip>:<pairing_port> <code>
#   adb connect <ip>:<connect_port>
```

Dump local stats (debuggable / run-as):

```bash
adb shell run-as net.clevertree.quizgate cat files/stats/diagnostics/schedule.jsonl
```
