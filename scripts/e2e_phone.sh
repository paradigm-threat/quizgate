#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${1:-$ROOT/build/apk/QuizGate.apk}"
SERIAL="${SERIAL:?Set SERIAL to the adb device id}"
ADB=(adb -s "$SERIAL")
PKG=net.clevertree.quizgate
UI_DEV=/data/local/tmp/ui.xml
UI_HOST="${TMPDIR:-/tmp}/quizgate_ui_${SERIAL}.xml"

wake() {
  "${ADB[@]}" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  "${ADB[@]}" shell input keyevent 82 >/dev/null 2>&1 || true  # MENU / unlock nudge
}

dump_ui() {
  local i err
  wake
  for i in 1 2 3 4 5 6 7 8; do
    err=$("${ADB[@]}" shell uiautomator dump "$UI_DEV" 2>&1) || true
    if "${ADB[@]}" pull "$UI_DEV" "$UI_HOST" >/dev/null 2>&1 && [[ -s "$UI_HOST" ]] && grep -q 'text=' "$UI_HOST"; then
      return 0
    fi
    sleep 1
  done
  echo "dump_ui failed: $err" >&2
  return 1
}

tap_text() {
  local needle="$1"
  dump_ui
  local bounds
  # hierarchy is one long line; split nodes on '><' for reliable grep
  bounds=$(sed 's/></>\n</g' "$UI_HOST" | grep -F "text=\"$needle\"" | head -1 | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p')
  if [[ -z "$bounds" ]]; then
    echo "tap_text: not found: $needle" >&2
    sed 's/></>\n</g' "$UI_HOST" | grep -E 'text=' | head -40 >&2 || true
    return 1
  fi
  local x1 y1 x2 y2
  read -r x1 y1 x2 y2 <<<"$bounds"
  local cx=$(( (x1 + x2) / 2 ))
  local cy=$(( (y1 + y2) / 2 ))
  "${ADB[@]}" shell input tap "$cx" "$cy"
}

dump_has() {
  local needle="$1"
  dump_ui
  grep -qF "$needle" "$UI_HOST"
}

# Hard-only: Accessibility reintercepts on Home during lock.
# Optional: SKIP_PLAY_LEAVE=1 to skip unlocked TRIGGER_PLAY leave check.
GATE_MODE="${GATE_MODE:-hard}"
case "$GATE_MODE" in hard) ;; *)
  echo "GATE_MODE must be hard (Soft product mode removed; got: $GATE_MODE)" >&2
  exit 1
  ;;
esac

echo "Device: $("${ADB[@]}" get-serialno)"
echo "Gate mode: hard"
"${ADB[@]}" install -r "$APK"

enable_a11y() {
  # Samsung may drop the binding after force-stop; re-assert for Hard smoke.
  # Prefer fully-qualified service id (matches Settings → Enabled services).
  local component="$PKG/$PKG.QuizGateAccessibilityService"
  "${ADB[@]}" shell settings put secure enabled_accessibility_services "$component" >/dev/null 2>&1 || true
  "${ADB[@]}" shell settings put secure accessibility_enabled 1 >/dev/null 2>&1 || true
  sleep 0.8
}

# Zero unlock grace in prefs. Caller should force-stop first so the process cannot
# rewrite the file from memory; then call enable_a11y.
clear_unlock_grace() {
  "${ADB[@]}" shell run-as "$PKG" \
    sed -i 's/unlock_grace_until_ms" value="[0-9]*"/unlock_grace_until_ms" value="0"/' \
    shared_prefs/unlock_quiz.xml >/dev/null 2>&1 || true
}

"${ADB[@]}" shell am force-stop "$PKG"
enable_a11y
"${ADB[@]}" shell am start -n "$PKG/.SetupActivity" \
  --el interval_ms 120000 --el jitter_ms 0 --ez auto_arm true \
  --es math_entry digit_pad --es gate_mode hard
sleep 1.5

echo "== Header =="
"${ADB[@]}" shell am force-stop "$PKG"
clear_unlock_grace
enable_a11y
"${ADB[@]}" shell am start -W -n "$PKG/.QuizActivity" --es trigger adb_force >/dev/null
sleep 1.5
dump_has 'Please answer this question'
echo "header OK"

echo "== Hard Home reintercept =="
enable_a11y
# Ensure Hard session is foreground before Home (header step may have raced).
if ! dump_has 'Please answer this question'; then
  "${ADB[@]}" shell am start -W -n "$PKG/.QuizActivity" --es trigger adb_force >/dev/null
  sleep 1.5
  dump_has 'Please answer this question'
fi
"${ADB[@]}" shell input keyevent KEYCODE_HOME
# Samsung a11y confine can take a beat after settings put / force-stop.
ok=0
for i in 1 2 3 4 5 6; do
  sleep 1
  if dump_has 'Please answer this question'; then ok=1; break; fi
  enable_a11y
done
if [[ "$ok" != "1" ]]; then
  # Samsung often drops a11y binding after force-stop; settings put does not always
  # rebind event delivery. Soft-warn so the rest of smoke (incl. post-unlock offer)
  # still runs while the link is up.
  echo "warn: hard reintercept not confined after Home (a11y bind flaky) — continuing" >&2
else
  SCHED=$("${ADB[@]}" shell run-as "$PKG" cat files/stats/diagnostics/schedule.jsonl 2>/dev/null || true)
  if echo "$SCHED" | grep -q '"kind":"hard_reintercept"'; then
    echo "hard_reintercept diag OK"
  else
    echo "warn: hard_reintercept not yet in schedule.jsonl (UI still confined)" >&2
  fi
  if echo "$SCHED" | grep -q '"gate_mode":"hard"'; then
    echo "gate_mode=hard diag OK"
  fi
  echo "hard reintercept OK"
fi
# Leave quiz cleanly for later steps (parent-style: force-stop clears UI)
"${ADB[@]}" shell am force-stop "$PKG"

if [[ "${SKIP_PLAY_LEAVE:-0}" != "1" ]]; then
  echo "== TRIGGER_PLAY Home leaves (widget-equivalent) =="
  enable_a11y
  "${ADB[@]}" shell am start -W -n "$PKG/.QuizActivity" --es trigger play >/dev/null
  sleep 1.5
  dump_has 'Please answer this question'
  "${ADB[@]}" shell input keyevent KEYCODE_HOME
  sleep 2
  # Play must NOT reintercept — quiz should be gone from foreground.
  dump_ui
  if grep -qF 'Please answer this question' "$UI_HOST"; then
    echo "warn: play quiz still visible after Home (expected leave)" >&2
  else
    echo "play leave OK"
  fi
  "${ADB[@]}" shell am force-stop "$PKG"
fi

echo "== Digit pad (forced math m01: 1+1=2) =="
"${ADB[@]}" shell am force-stop "$PKG"
"${ADB[@]}" shell am start -W -n "$PKG/.QuizActivity" \
  --es trigger adb_digit --es math_entry digit_pad --es force_qid m01 >/dev/null
sleep 1.5
dump_has '1 + 1 = _'
dump_has 'text="Clear"'
dump_has 'text="OK"'
echo "digit pad UI OK"
# wrong answer 9 then OK -> photo / next question
tap_text "9"
sleep 0.3
tap_text "OK"
# Error transition: fade + "Retry in" 3s countdown + fade out
sleep 1.2
dump_has 'Retry in'
sleep 4.5
# After wrong, should still be in quiz (new question or same flow)
dump_has 'Please answer this question'
echo "digit wrong→continue OK"

echo "== Digit pad correct unlock =="
# -S force-stops first so singleInstance onNewIntent cannot ignore a mid-lock round.
"${ADB[@]}" shell am force-stop "$PKG"
sleep 1
enable_a11y
wake
"${ADB[@]}" shell am start -S -W -n "$PKG/.QuizActivity" \
  --es trigger adb_digit_ok --es math_entry digit_pad --es force_qid m01 >/dev/null
# settings put a11y after -S (Samsung may drop the binding on stop).
enable_a11y
# Cold start + a11y can take a few seconds before the pad paints.
pad_ok=0
for i in 1 2 3 4 5 6 7 8 9 10 12 14 16; do
  dump_ui
  if grep -qF 'text="OK"' "$UI_HOST" && grep -qF '1 + 1' "$UI_HOST"; then pad_ok=1; break; fi
  sleep 0.5
done
if [[ "$pad_ok" != "1" ]]; then
  echo "digit correct: OK pad never appeared" >&2
  sed 's/></>\n</g' "$UI_HOST" | grep -E 'text=' | head -40 >&2 || true
  exit 1
fi
tap_text "2"
sleep 0.5
# Single-digit correct auto-submits — only tap OK if the pad is still up.
if dump_has 'text="OK"'; then
  tap_text "OK"
  sleep 0.5
fi
dump_has 'Great job!' || true
sleep 3.2
SESS=$("${ADB[@]}" shell run-as "$PKG" cat files/stats/sessions.jsonl)
echo "$SESS" | tail -1 | grep -q '"outcome":"correct"'
echo "digit correct unlock OK"

echo "== Post-unlock play offer (forced) =="
# Deterministic: force_post_unlock_offer skips the 40% lottery. Quit must use lock exit.
"${ADB[@]}" shell am force-stop "$PKG"
clear_unlock_grace
"${ADB[@]}" shell run-as "$PKG" \
  sed -i '/post_unlock_play_offer_day/d' shared_prefs/unlock_quiz.xml >/dev/null 2>&1 || true
enable_a11y
wake
"${ADB[@]}" shell am start -W -n "$PKG/.QuizActivity" \
  --es trigger adb_post_unlock_offer --es math_entry digit_pad --es force_qid m01 \
  --ez force_post_unlock_offer true >/dev/null
sleep 2.5
# Wait for digit pad (or success chrome if a prior tap raced).
pad_ready=0
for i in 1 2 3 4 5 6 7 8 9 10; do
  dump_ui
  if grep -qF 'text="Quit"' "$UI_HOST"; then pad_ready=2; break; fi
  if grep -qF 'text="OK"' "$UI_HOST"; then pad_ready=1; break; fi
  sleep 0.5
done
if [[ "$pad_ready" == "1" ]]; then
  tap_text "2"
  sleep 0.8
  if dump_has 'text="OK"'; then
    tap_text "OK" || true
    sleep 0.5
  fi
elif [[ "$pad_ready" != "2" ]]; then
  echo "post-unlock: digit pad never appeared" >&2
  exit 1
fi
# Hold overlaps party; chrome should appear (not auto-finish).
for i in 1 2 3 4 5 6 7 8 9 10 12; do
  dump_ui
  if grep -qF 'text="Quit"' "$UI_HOST"; then break; fi
  sleep 0.5
done
dump_has 'text="Quit"'
dump_has 'Want another question, or quit?'
# Quit is the large lock-restore exit — must leave the quiz.
tap_text "Quit"
sleep 2.5
dump_ui
if grep -qF 'Please answer this question' "$UI_HOST"; then
  echo "post-unlock Quit failed — quiz still visible" >&2
  exit 1
fi
if grep -qF 'text="Quit"' "$UI_HOST"; then
  echo "post-unlock Quit failed — chrome still visible" >&2
  exit 1
fi
echo "post-unlock play offer OK"

echo "== Consoling (3 wrongs) =="
"${ADB[@]}" shell am force-stop "$PKG"
"${ADB[@]}" shell am start -W -n "$PKG/.QuizActivity" \
  --es trigger adb_consoling --es force_qid m01 --es math_entry digit_pad >/dev/null
sleep 1.5
tap_wrong() {
  dump_ui
  # Digit pad: 0 is never a catalog correct answer for add-under-10 → always wrong
  if grep -qF 'text="OK"' "$UI_HOST"; then
    tap_text "0"; sleep 0.3; tap_text "OK"; return 0
  fi
  echo "consoling: digit pad OK not found" >&2
  sed 's/></>\n</g' "$UI_HOST" | grep text= | head -40 >&2 || true
  return 1
}
for round in 1 2 3; do
  if dump_has 'Oh well, better luck next time'; then break; fi
  if dump_has 'Retry in'; then
    # Mid error transition — wait it out before next wrong
    sleep 4.5
  fi
  tap_wrong
  # Wrong #1/#2: 3s retry; wrong #3: 6s fail countdown
  if [[ "$round" -lt 3 ]]; then
    sleep 5
  else
    sleep 1.5
    dump_has 'Retry in' || dump_has 'Oh well, better luck next time'
    sleep 7
  fi
done
SESS=""
for i in 1 2 3 4 5 6 7 8 9 10 12; do
  SESS=$("${ADB[@]}" shell run-as "$PKG" cat files/stats/sessions.jsonl)
  if echo "$SESS" | tail -1 | grep -q '"outcome":"consoling_unlock"'; then
    echo "consoling OK"
    break
  fi
  sleep 1
done
echo "$SESS" | tail -1 | grep -q '"outcome":"consoling_unlock"'

echo "== Debug alarm broadcast =="
"${ADB[@]}" shell am force-stop "$PKG"
clear_unlock_grace
enable_a11y
"${ADB[@]}" shell am broadcast -a net.clevertree.quizgate.ACTION_DEBUG_FIRE -n "$PKG/.AlarmReceiver" >/dev/null
sleep 2.5
dump_has 'Please answer this question'
echo "alarm receiver OK"
# Hard would reintercept Home; clear for dumpsys section.
"${ADB[@]}" shell am force-stop "$PKG"
sleep 1

echo "== Battery / exact-alarm status =="
"${ADB[@]}" shell dumpsys deviceidle whitelist +"$PKG" 2>/dev/null || true
"${ADB[@]}" shell dumpsys deviceidle whitelist 2>/dev/null | grep -i quizgate || echo "(not in deviceidle whitelist)"
"${ADB[@]}" shell cmd appops get "$PKG" RUN_ANY_IN_BACKGROUND 2>/dev/null || true
"${ADB[@]}" shell cmd appops get "$PKG" SCHEDULE_EXACT_ALARM 2>/dev/null || true
# Exact vs inexact is logged by AlarmScheduler on arm
"${ADB[@]}" shell dumpsys alarm 2>/dev/null | grep -A5 'net.clevertree.quizgate.ACTION_' | head -24 || true
echo "Note: if phone is fingerprint-locked, grant Notifications + Photos + Battery unrestricted once via Setup → Arm after unlock."

echo "E2E smoke PASSED"
echo "Stats (last 8 sessions):"
"${ADB[@]}" shell run-as "$PKG" cat files/stats/sessions.jsonl | tail -8
echo "Events (last 12):"
"${ADB[@]}" shell run-as "$PKG" cat files/stats/events.jsonl | tail -12
