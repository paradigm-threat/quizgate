# QuizGate local diagnostics & stats

Privacy-safe on-device logging for Play Console crash/ANR triage and (later) Clevertree upload.
**Network upload is OFF** (`BuildFingerprint.TELEMETRY_UPLOAD_ENABLED = false`).
When enabled, Intake is **`https://api.quizgate.app`** only (see [`DATA_PLANE.md`](DATA_PLANE.md)).

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

`ts`, `session_id`, `type` (`shown`/`correct`/`wrong`/`skipped`/`bypass`/`consoling`), `question_id`, short `choice` (digits/catalog tokens only; free-text scrubbed), `miss_streak`, optional `latency_ms`, `curriculum_id`, `session_type` (`play`|`lock`).

### Schedule (`schedule.jsonl`)

`ts`, `tz`, `tz_offset_min`, `kind`:

- `arm` — scheduling armed
- `alarm_scheduled` — next alarm window set
- `alarm_fired` — OS alarm delivered
- `skip_fire` — morning skipped (already cleared)
- `quiz_started` / `quiz_ended` — session bounds
- `perf_mode` — Auto performance↔slim switch (reason codes + stats in `note`)

Plus `trigger`, `session_type`, `next_alarm_at`, `interval_ms`, `jitter_ms`, optional `other_app` boolean (**never** package name), optional short `note`.

### Upload / snapshot payload (`TelemetryPayload`, schema **v3**)

Built by `TelemetryPayload` / `Diag.buildPayload()`. Includes:

- Anonymous `user_uuid` (device-generated capability UUID)
- Build fingerprint (`versionCode` / `versionName` / git SHA)
- Coarse device model, locale/tz
- Permission booleans, today rollup, recent quiz/schedule/exception lines, storage sizes
- Structured **`performance`** block for live debug / future server ingest:
  - `preference` (`auto`|`performance`|`slim`), `effective` (`performance`|`slim`)
  - `auto_reasons` (CSV reason codes)
  - `last_switch_at` / `last_switch_from` / `last_switch_to`
  - Numerics: `battery_pct`, `charging`, `battery_saver`, `battery_low_broadcast`, `low_ram_device`, `ram_class_mb`, `total_ram_mb`, `cpu_count`, `thermal_severe`

`TelemetryUploader.maybeUpload()` is a no-op while the flag is false. Fields stay in the local snapshot shape so enabling upload later needs no schema redesign.

### Logcat

Stable tag **`QuizGateDiag`** — process start, uncaught exceptions, and Auto `perf_mode_switch` lines for Play log correlation.

## What we never collect

Names, emails, phones, account IDs, precise location, contacts, Wi‑Fi SSIDs, IP addresses, advertising IDs, question **text**, free-text answers, raw foreground package names in diagnostics (only `other_app: true|false`).

## Enabling upload later

1. Run Intake at `https://api.quizgate.app` + batch writer for `quizgate-users` ([DATA_PLANE.md](DATA_PLANE.md)).
2. Flip `BuildFingerprint.TELEMETRY_UPLOAD_ENABLED` to `true` (and/or a remote/prefs gate).
3. `TelemetryUploader` POSTs JSON to `/v1/users/{uuid}/sync`; `RemoteSettingsPull` GETs profile.
4. Update Play Data safety + privacy policy before shipping.
