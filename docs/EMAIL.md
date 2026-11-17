# Outgoing email — `clevertreellc@gmail.com`

QuizGate 2FA and transactional mail send **as** Gmail via SMTP (`smtp.gmail.com:587`), not as `@clevertree.net` (SPF is `-all` on that domain).

**One secret required:** a Google **App Password** for `clevertreellc@gmail.com`. Place it only in the env files below (never git). Without it, Intake returns `smtp_not_configured` for OTP.

Ops note: local `~/.config/quizgate/smtp.env` and iad1 `/etc/quizgate/smtp.env` must stay in sync (including `QUIZGATE_EMAIL_PEPPER` — email hashes are peppered).

## 1. Create a Google App Password (human step)

1. Sign in to Google as **clevertreellc@gmail.com**.
2. Open [Google Account → Security](https://myaccount.google.com/security).
3. Enable **2-Step Verification** if it is not already on.
4. Open [App passwords](https://myaccount.google.com/apppasswords) (search “App passwords” if the link is hidden).
5. Create an app password:
   - App: **Mail**
   - Device: **Other** → name it `quizgate-iad1` (and optionally a second one `quizgate-local`).
6. Copy the **16-character** password (spaces optional). Treat it like a secret — never commit it.

You will paste it into local + server secret files below (not into git).

## 2. Local auth (this machine)

```bash
mkdir -p ~/.config/quizgate
chmod 700 ~/.config/quizgate
cat > ~/.config/quizgate/smtp.env <<'EOF'
QUIZGATE_SMTP_HOST=smtp.gmail.com
QUIZGATE_SMTP_PORT=587
QUIZGATE_SMTP_USER=clevertreellc@gmail.com
QUIZGATE_SMTP_PASS=PASTE_APP_PASSWORD_HERE
QUIZGATE_SMTP_FROM=QuizGate <clevertreellc@gmail.com>
EOF
chmod 600 ~/.config/quizgate/smtp.env
```

Test:

```bash
cd /home/ari/dev/tablet/quizgate/data-plane
set -a && source ~/.config/quizgate/smtp.env && set +a
node scripts/send-test-email.mjs you@example.com
```

## 3. Server auth (iad1 Intake)

On **iad1** (API host `162.209.124.53` / `api.quizgate.app`):

```bash
sudo mkdir -p /etc/quizgate
sudo tee /etc/quizgate/smtp.env >/dev/null <<'EOF'
QUIZGATE_SMTP_HOST=smtp.gmail.com
QUIZGATE_SMTP_PORT=587
QUIZGATE_SMTP_USER=clevertreellc@gmail.com
QUIZGATE_SMTP_PASS=PASTE_APP_PASSWORD_HERE
QUIZGATE_SMTP_FROM=QuizGate <clevertreellc@gmail.com>
EOF
sudo chmod 600 /etc/quizgate/smtp.env
sudo chown root:root /etc/quizgate/smtp.env
```

Point `quizgate-intake.service` (or batch) at that file:

```ini
EnvironmentFile=/etc/quizgate/smtp.env
```

Then `sudo systemctl daemon-reload && sudo systemctl restart quizgate-intake`.

Test from iad1:

```bash
sudo set -a; sudo . /etc/quizgate/smtp.env; set +a
cd /opt/quizgate/data-plane   # or deploy path
node scripts/send-test-email.mjs clevertreellc@gmail.com
```

## 4. App / API usage

- Module: [`data-plane/lib/mail.mjs`](../data-plane/lib/mail.mjs)
- 2FA codes: short-lived, stored hashed server-side; email body never logged.
- Remote admin (planned): parent email is stored as a **hash** with the UUID; plaintext email is only used at login + 2FA send time.

## 5. What not to do

- Do not put App Passwords in git, Vercel env for the static site, or Relay tip.
- Do not send as `@quizgate.app` / `@clevertree.net` until SPF/DKIM for that domain is set up; Gmail From is correct for now.
- Do not reuse the inbound Maildir forward password file for outbound if it still says `PASSWORD_NEEDED`.
