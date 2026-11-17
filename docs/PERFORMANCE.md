# QuizGate performance & battery profiles

Default: **Balanced** — full tablet UX. Optional: **Lean / slim** for low-end devices or parents who want less background work.

Schedule defaults stay **30±15 minutes**. Exact alarms and boot / package-replace re-arm are **never** disabled by either profile. Telemetry upload stays **OFF**.

## How to enable Lean mode

1. Open QuizGate → **Parents** (or Setup).
2. Under **Performance**, choose **Lean / low-end**.
3. Leave **Balanced (recommended)** for typical tablets (Galaxy Tab A9+ class and newer).

The choice is stored in app prefs (`performance_mode=balanced|slim`) and takes effect on the next quiz / Setup countdown tick.

## Cost-center audit

| Cost center | What we found | Balanced | Slim |
|-------------|---------------|----------|------|
| Wake locks | No app-held `PARTIAL_WAKE_LOCK`; wakes come from AlarmManager | exact / setAlarmClock | same (required) |
| Exact alarms | Required for 30±15 reliability | on | on |
| Boot / replace re-arm | `BootReceiver` + `PackageReplacedReceiver` | on | on |
| FGS duration | `QuizTriggerService` after FSI send | ~8 s | ~2.5 s |
| Usage-stats / return-to-app | `ForegroundApp.detect` scans ~60 s of events | on when granted | off (no scan) |
| Media focus | Transient exclusive + pause key | focus + delayed retry pause | focus + single pause |
| Diagnostics I/O | Bounded JSONL under `files/stats/` | full schedule + breadcrumbs + mid-session compact | fire/arm/end only; no optional breadcrumbs; compact on session end |
| Widget refresh | Reads day stats + `updateAppWidget` | mid-answer + session end | session end (and boot/pin) only |
| Animations | Balloon pulse, confetti, photo flash | on | off (short success hold) |
| Setup countdown poll | While Parents screen open | 30 s | 60 s |
| Process start snapshot | `QuizGateApp` logcat summary | on | skipped |
| Telemetry upload | Feature flag | off | off |

Overnight drain with the tablet powered off is **expected** (alarms do not survive power-off; boot re-arms). Do not chase that as a lean-mode regression.

## Tradeoff table

| Feature | Balanced (default) | Lean / slim |
|---------|--------------------|-------------|
| Exact alarm + morning + boot re-arm | Yes | Yes |
| Appear-on-top / FSI launch path | Yes | Yes (reliability first) |
| Return to previous app | Yes (Usage Access) | No |
| Party animations | Yes | No |
| Mid-quiz widget + day-bar refresh | Yes | Session end only |
| Optional breadcrumbs (quiz/media) | Yes | No |
| Schedule dual-write → breadcrumbs | Yes | No (schedule JSONL still kept for fire/arm) |
| `launch_attempt` chatter | Logged | Dropped |
| FGS reinforce hold | Longer | Shorter |
| Historic day rollups (90 days) | Same | Same |
| Notification-only launch | Not used | Not used (would miss quizzes) |

## Implementation map

| Piece | Role |
|-------|------|
| `PerfPolicy` (core) | Pure knobs + schedule-kind filter (unit-tested) |
| `PerformanceMode` | Android facade over prefs |
| `AppPrefs.get/setPerformanceMode` | Persistence |
| `Diag` / stores | Compact interval + optional breadcrumb gate |
| `QuizTriggerService` | Mode-aware FGS stop + skip reinforce work in slim |
| `QuizActivity` / `MediaGate` / `ForegroundApp` / `ScoreWidgetProvider` | UX / I/O gates |
| Setup → Performance spinner | Parent-facing toggle |

## Related docs

- [`TELEMETRY.md`](TELEMETRY.md) — local diagnostics bounds; upload off
- [`WIDGET.md`](WIDGET.md) — home score widget pin path
