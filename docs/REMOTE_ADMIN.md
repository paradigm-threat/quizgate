# QuizGate remote management

Public hosts: **`*.quizgate.app` only**.

| Host | Role |
|------|------|
| https://admin.quizgate.app | Parent/human admin UI (UUID + email + 2FA) |
| https://api.quizgate.app | Intake: register, OTP, stats, settings patch |
| App Setup | “Enable remote management” + optional parent email |

## Model

1. Device UUID is a capability token (`AnonUserId`).
2. **Enable remote management** turns on AI sync (runtime upload + profile pull).
3. Optional **parent email** → server stores `sha256(pepper:email:uuid)` only — never plaintext email on the tip.
4. Human login: must know UUID **and** email matching the hash → 6-digit code via `clevertreellc@gmail.com` SMTP → session token.
5. **No user directory / search.** Wrong UUID → generic failure.
6. Stats are read-only in admin; curriculum / interval / jitter / performance preference are writable (queued `admin_settings` → `quizgate-batch`).
7. App periodically pulls `profile.json` when remote upload is enabled.

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
