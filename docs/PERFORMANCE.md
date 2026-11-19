# QuizGate performance profiles

User preference (Setup / onboarding): **Auto** (default), **Performance**, or **Slim**.
Effective runtime profile is always **Performance** or **Slim**.

- Pref key: `performance_mode=auto|performance|slim` (`balanced` is a legacy alias for `performance`).
- Exact alarms, morning gate, and boot / `MY_PACKAGE_REPLACED` / time-change re-arm are **never** disabled by any profile.
- Telemetry upload stays **OFF**.

## Modes

| Preference | Behavior |
|------------|----------|
| **Auto** (default) | Picks Performance vs Slim from device/power signals. Re-evaluates on app start and power broadcasts (`BATTERY_LOW` / `BATTERY_OKAY` / power-save) — **no polling**. Sticky Slim until recovery (battery ≥25% or charging, saver off, capable device, not thermally severe). |
| **Performance** | Full UX always. Never auto-downgrades. |
| **Slim** | Lean always. |

### Auto → Slim when

Any pressure signal: battery saver on, `ACTION_BATTERY_LOW` sticky flag, battery ≤20%, low-RAM device, app heap memory class ≤96 MB, total RAM ≤3072 MB, CPU count ≤4, or thermal ≥ severe. Reason codes + numeric stats are stored and logged on each Auto switch (`perf_mode` breadcrumb / schedule line + `QuizGateDiag`).

### How to change

Onboarding asks Auto / Performance / Slim (default Auto). Later: **Parents** → Performance mode.

## Cost-center audit

| Cost center | What we found | Performance | Slim |
|-------------|---------------|-------------|------|
| Wake locks | No app-held `PARTIAL_WAKE_LOCK`; schedule uses setAlarmClock only while interactive — screen-off cancels alarms (no idle wake) | interactive exact / setAlarmClock | same |
| Exact alarms | Required for in-use 40±10 reliability | on (cancelled while screen off) | on (cancelled while screen off) |
| Boot / replace / time re-arm | `BootReceiver` + `PackageReplacedReceiver` — interactive only; else `pending_screen_lock` | on | on |
| FGS duration | `QuizTriggerService` after FSI send | ~8 s | ~2.5 s |
| Usage-stats / return-to-app | `ForegroundApp.detect` scans ~60 s of events | on when granted | off (no scan) |
| Media focus | Transient exclusive + pause key | focus + delayed retry pause | focus + single pause |
| Diagnostics I/O | Bounded JSONL under `files/stats/` | full schedule + breadcrumbs + mid-session compact | fire/arm/end (+ `perf_mode`); no optional breadcrumbs; compact on session end |
| Widget refresh | Reads day stats + `updateAppWidget` | mid-answer + session end | session end (and boot/pin) only |
| Animations | Balloon pulse, confetti, photo flash | on | off (short success hold) |
| Setup countdown poll | While Parents screen open | 30 s | 60 s |
| Process start snapshot | `QuizGateApp` logcat summary | on | skipped |
| Telemetry upload | Feature flag | off | off |

Overnight drain with the tablet powered off is **expected** (alarms do not survive power-off; boot re-arms). Do not chase that as a Slim regression.

## Tradeoff table

| Feature | Performance | Slim |
|---------|-------------|------|
| Exact alarm + morning + boot re-arm | Yes | Yes |
| Appear-on-top / FSI launch path | Yes | Yes (reliability first) |
| Return to previous app | Yes (Usage Access) | No |
| Party animations | Yes | No |
| Mid-quiz widget + day-bar refresh | Yes | Session end only |
| Optional breadcrumbs (quiz/media) | Yes | No |
| Schedule dual-write → breadcrumbs | Yes | No (schedule JSONL still kept for fire/arm/`perf_mode`) |
| `launch_attempt` chatter | Logged | Dropped |
| FGS reinforce hold | Longer | Shorter |
| Historic day rollups (90 days) | Same | Same |
| Auto switch logging | N/A (fixed) | N/A (fixed); Auto writes durable `perf_mode` + `QuizGateDiag` |

## Live-debug visibility

Performance fields exist so we can **iterate quickly on live data** (Play triage, eventual server upload): preference, effective mode, Auto reason codes, battery/saver/device heuristics, last switch timestamps. Upload stays OFF; see [`TELEMETRY.md`](TELEMETRY.md). Slim avoids high-frequency sampling — switch + session-boundary logs are enough.

## Implementation map

| Piece | Role |
|-------|------|
| `PerfPolicy` (core) | Pure knobs + Auto decision + schedule-kind filter (unit-tested) |
| `PerformanceMode` | Android facade; samples signals; logs Auto switches |
| `PowerStateReceiver` | Battery / power-save → `reevaluate` |
| `AppPrefs` | `performance_mode`, effective mode, last Auto reasons / switch |
| `Diag` / stores | Compact interval + optional breadcrumb gate; always-on `perf_mode` |
| `QuizTriggerService` | Mode-aware FGS stop + skip reinforce work in Slim |
| `QuizActivity` / `MediaGate` / `ForegroundApp` / `ScoreWidgetProvider` | UX / I/O gates |
| Setup + onboarding | Parent-facing preference |

## Related docs

- [`TELEMETRY.md`](TELEMETRY.md) — local diagnostics bounds; `performance` payload block; upload off
- [`WIDGET.md`](WIDGET.md) — home score widget pin path
