# QuizGate local diagnostics & stats

Privacy-safe on-device logging for Play Console crash/ANR triage and optional Clevertree Intake sync.
**Compile kill-switch stays OFF** (`BuildFingerprint.TELEMETRY_UPLOAD_ENABLED = false`). Runtime **Share anonymous progress** (onboarding + Parents / Setup) opts in via `remote_management`.
Intake is **`https://api.quizgate.app`** only (see [`DATA_PLANE.md`](DATA_PLANE.md)).

**When it uploads:** asynchronously when the app is open (quiz unlock / practice end / Parents). No WorkManager / idle alarms. Scans `days.jsonl` for rollups not yet upserted: **closed days upload once**; **today at most once per local calendar day**. Weeks/months of backlog are fine — accuracy over frequency. Server keeps one file per uuid+day (last-write-wins).

**Live-debug goal:** structured, queryable fields (reason codes + numerics) so Play bugs and eventual uploads are easy to triage — not vague free-text. Prefer switch/session breadcrumbs over high-frequency sampling (especially in Slim).

## Paths (app-private)

| Path | Role |
|------|------|
| `files/stats/events.jsonl` | Recent raw quiz events (ring buffer) |
| `files/stats/sessions.jsonl` | Recent session summaries (ring buffer) |
| `files/stats/days.jsonl` | Durable per-day rollups (asked/right/wrong/skipped) |
| `files/stats/diagnostics/schedule.jsonl` | Alarm / quiz lifecycle timestamps (`alarm_scheduled`, `alarm_fired`, arm, quiz start/end, `perf_mode`, …) |
| `files/stats/diagnostics/breadcrumbs.jsonl` | Triage stream (quiz outcomes, media, perms, Auto switches) |
| `files/stats/diagnostics/exceptions.jsonl` | Non-fatal + uncaught breadcrumbs |

No world-readable external storage. No advertising IDs.

## Retention caps (bounded space)

| Channel | Max lines | Approx byte cap |
|---------|-----------|-----------------|
| events | 1500 | 512 KB |
| sessions | 400 | 256 KB |
| day rollups | **90 days** | tiny (~few KB) |
| schedule | 500 | 256 KB |
| breadcrumbs | 500 | 256 KB |
| exceptions | 100 | 64 KB |

Raw JSONL is append + **periodic compaction** (oldest dropped first). Day rollups keep historic scores after raw events prune. Diagnostics summary includes `stats_bytes` / `diag_bytes`.

## Persistence guarantees

| Event | Stats survive? |
|-------|----------------|
| Process death | Yes (files) |
| App update (`MY_PACKAGE_REPLACED`) | Yes — receiver re-arms only; **never deletes** `files/stats/` |
| Android Auto Backup / device transfer | **Best-effort** — `allowBackup` includes only `files/stats/` (see `res/xml/backup_rules.xml` + `data_extraction_rules.xml`). Requires user backup enabled. |
| `pm clear` / uninstall without restore | **No** — app data wiped. Not fixable without risky shared storage. |

SharedPreferences (including optional resume package) are **excluded** from backup.

## Schema (privacy)

### Quiz event (`events.jsonl`)

`ts`, `session_id`, `type` (`shown`/`correct`/`wrong`/`skipped`/`bypass`/`consoling`), `question_id`, short `choice` (digits/catalog tokens / `true`|`false` only; free-text scrubbed), `miss_streak`, optional `latency_ms`, `curriculum_id`, `session_type` (`play` = voluntary / practice · `lock` = scheduled Hard quiz — **label only**, does not change ability), optional `render_format` (`equation`|`true_false`|`choice` — ask-time UI, not question text).

Day rollups (`days.jsonl`) keep totals plus `asked_lock` / `asked_play` (and right/wrong/skipped splits) so Parents stats and Intake day-batches can tell scheduled vs self-started challenge seeking.

### Schedule (`schedule.jsonl`)

`ts`, `tz`, `tz_offset_min`, `kind`:

- `arm` — scheduling armed (note may include `arm_screen_off_pending` / `arm_interactive`)
- `alarm_scheduled` — next alarm window set (interactive only)
- `alarm_fired` — OS alarm delivered
- `skip_fire` — morning skipped, or `note=screen_off` / `screen_off_pending` when schedule cancelled on sleep
- `launch_attempt` — includes wake-to-lock (`trigger=wake`, `note=wake_lock …`)
- `quiz_started` / `quiz_ended` — session bounds
- `perf_mode` — Auto performance↔slim switch (reason codes + stats in `note`)

Plus `trigger`, `session_type`, `next_alarm_at`, `interval_ms`, `jitter_ms`, optional `other_app` boolean (**never** package name), optional short `note`.

### Upload / snapshot payload (`TelemetryPayload`, schema **v3**)

Built by `TelemetryPayload` / `Diag.buildPayload()`. Includes:

- Anonymous `user_uuid` (device-generated capability UUID)
- Build fingerprint (`versionCode` / `versionName` / git SHA)
- Coarse device model, locale/tz
- Permission booleans, today rollup (`asked`/`right`/`wrong`/`skipped` plus `*_lock` / `*_play` context), recent quiz/schedule/exception lines, storage sizes
- Structured **`performance`** block for live debug / future server ingest:
  - `preference` (`auto`|`performance`|`slim`), `effective` (`performance`|`slim`)
  - `auto_reasons` (CSV reason codes)
  - `last_switch_at` / `last_switch_from` / `last_switch_to`
  - Numerics: `battery_pct`, `charging`, `battery_saver`, `battery_low_broadcast`, `low_ram_device`, `ram_class_mb`, `total_ram_mb`, `cpu_count`, `thermal_severe`

`Diag.syncDayBatchesOnOpen` / `flushAfterInteractiveSession` is the UI entry point. It POSTs lean day batches (`day` + `today` counts including lock/play) via `TelemetryUploader.uploadDayBatch`. A local `files/stats/uploaded_days.txt` ledger remembers closed days already sent.

### Media / overlay breadcrumbs (`kind=media`)

Privacy-safe `msg` tokens (no package names):

| msg | Meaning |
|-----|---------|
| `was_playing` / `idle` | Soft-pause latch at fire |
| `pre_pause_settle_ms=N:idle` / `:timeout_active` | Alarm path waited for playback to stop before quiz UI |
| `overlay_hide_on` / `overlay_hide_unsupported` | `setHideOverlayWindows` availability |
| `resume_play` | Best-effort MEDIA_PLAY after unlock |

### Logcat

Stable tag **`QuizGateDiag`** — process start, uncaught exceptions, and Auto `perf_mode_switch` lines for Play log correlation.

## What we never collect

Names, emails, phones, account IDs, precise location, contacts, Wi‑Fi SSIDs, IP addresses, advertising IDs, question **text**, free-text answers, raw foreground package names in diagnostics (only `other_app: true|false`).

## Enabling upload

1. Intake at `https://api.quizgate.app` + batch writer for `quizgate-users` ([DATA_PLANE.md](DATA_PLANE.md)) — live.
2. Parent opts in: onboarding **Share anonymous progress**, or Parents checkbox (same pref). Optional parent email registers for admin.quizgate.app.
3. On-open async sync POSTs each pending day to `/v1/users/{uuid}/sync` (optional `day` for backfill); Intake upserts `{batches}/{day}/{uuid}.json` (LWW). Tip gets profile/settings only.
4. `RemoteSettingsPull` GETs profile after a successful sync / while opted in.
5. Keep Play Data safety + privacy policy updated before flipping the compile kill-switch or shipping store builds that advertise remote sync.
