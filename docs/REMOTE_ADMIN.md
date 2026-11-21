# QuizGate remote management

Public hosts: **`*.quizgate.app` only**.

| Host | Role |
|------|------|
| https://admin.quizgate.app | Parent/human admin UI (UUID + email + 2FA) |
| https://api.quizgate.app | Intake: register, OTP, stats, settings patch |
| App Setup | "Enable remote management" + optional parent email |

## Model

1. Device UUID is a capability token (`AnonUserId`).
2. **Enable remote management** turns on AI sync (runtime upload + profile pull).
3. Optional **parent email** → server stores `sha256(pepper:email:uuid)` only — never plaintext email on the tip.
4. Human login: must know UUID **and** email matching the hash → 6-digit code via `clevertreellc@gmail.com` SMTP → session token.
5. **No user directory / search.** Wrong UUID → generic failure.
6. Stats are read-only in admin. Writable settings (queued `admin_settings` → `quizgate-batch`) are **overrides only**: leave blank in the admin UI to keep the tip/device on **repo defaults**. Keys: curriculum, interval/jitter, performance preference, `time_earn_cap_minutes` (default 120), `stats_day_barrier_hour` (default **5**), `curriculum_level` (default **0**), level-up soft gates `level_up_min_accuracy_pct` (**75**), `level_up_min_correct` (**5**), skips default **`skipped ≤ right+wrong`** (optional `level_up_max_skip_pct`), idle MC hints `hint_mode` / `hint_delay_ms` / `hint_pulse_ms` / `hint_period_ms` / `hint_strong_after_ms` / `hint_blocks_after_ms` / `hint_blocks_enabled`. See [`CURRICULUM.md`](CURRICULUM.md) · [`TIME_EARN.md`](TIME_EARN.md) · [`TELEMETRY.md`](TELEMETRY.md).
7. App periodically pulls `profile.json` when remote upload is enabled; only present settings keys are applied.

## SMTP (2FA)

See [`EMAIL.md`](EMAIL.md). One secret: Gmail **App Password** for `clevertreellc@gmail.com` in:

- Local: `~/.config/quizgate/smtp.env`
- iad1: `/etc/quizgate/smtp.env` + `EnvironmentFile=` on `quizgate-intake`

## API sketch

| Method | Path | Notes |
|--------|------|-------|
| POST | `/v1/users/{uuid}/remote` | `{ ai_enabled, email? }` → queue `remote_register` |
| POST | `/v1/admin/otp` | `{ uuid, email }` → send 2FA |
| POST | `/v1/admin/verify` | `{ otp_id, code }` → Bearer token |
| GET | `/v1/admin/users/{uuid}/stats` | Bearer required |
| PATCH | `/v1/admin/users/{uuid}/profile` | Bearer → `admin_settings` job |
