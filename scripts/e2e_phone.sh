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

# Soft: Home dismisses (bypass). Hard: Accessibility reintercepts (default product mode).
# Override: GATE_MODE=soft|hard  (default soft so CI/smoke Home step stays stable without a11y).
GATE_MODE="${GATE_MODE:-soft}"
case "$GATE_MODE" in soft|hard) ;; *)
  echo "GATE_MODE must be soft or hard (got: $GATE_MODE)" >&2
  exit 1
  ;;
esac

echo "Device: $("${ADB[@]}" get-serialno)"
echo "Gate mode: $GATE_MODE"
"${ADB[@]}" install -r "$APK"

enable_a11y() {
  # Samsung may drop the binding after force-stop; re-assert for Hard smoke.
  local component="$PKG/.QuizGateAccessibilityService"
  "${ADB[@]}" shell settings put secure enabled_accessibility_services "$component" >/dev/null 2>&1 || true
  "${ADB[@]}" shell settings put secure accessibility_enabled 1 >/dev/null 2>&1 || true
}

"${ADB[@]}" shell am force-stop "$PKG"
if [[ "$GATE_MODE" == "hard" ]]; then
  enable_a11y
fi
"${ADB[@]}" shell am start -n "$PKG/.SetupActivity" \
  --el interval_ms 120000 --el jitter_ms 0 --ez auto_arm true \
  --es math_entry digit_pad --es gate_mode "$GATE_MODE"
sleep 1.5

echo "== Header =="
"${ADB[@]}" shell am force-stop "$PKG"
if [[ "$GATE_MODE" == "hard" ]]; then
  enable_a11y
fi
"${ADB[@]}" shell am start -W -n "$PKG/.QuizActivity" --es trigger adb_force >/dev/null
sleep 1.5
dump_has 'Please answer this question'
echo "header OK"

if [[ "$GATE_MODE" == "soft" ]]; then
  echo "== Soft Home bypass =="
  "${ADB[@]}" shell input keyevent KEYCODE_HOME
  sleep 2
  SESS=$("${ADB[@]}" shell run-as "$PKG" cat files/stats/sessions.jsonl)
  echo "$SESS" | tail -1 | grep -q '"outcome":"bypass"'
  echo "bypass OK"
else
  echo "== Hard Home reintercept =="
  enable_a11y
  "${ADB[@]}" shell input keyevent KEYCODE_HOME
  sleep 2.5
  # Quiz must return (confine); session must NOT end as soft bypass.
  dump_has 'Please answer this question'
  SCHED=$("${ADB[@]}" shell run-as "$PKG" cat files/stats/diagnostics/schedule.jsonl 2>/dev/null || true)
  if echo "$SCHED" | grep -q '"kind":"hard_reintercept"'; then
    echo "hard_reintercept diag OK"
  else
    echo "warn: hard_reintercept not yet in schedule.jsonl (UI still confined)" >&2
  fi
  if echo "$SCHED" | grep -q '"gate_mode":"hard"'; then
    echo "gate_mode=hard diag OK"
  fi
  # Leave quiz cleanly for later steps (parent-style: force-stop clears UI)
  "${ADB[@]}" shell am force-stop "$PKG"
  echo "hard reintercept OK"
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
"${ADB[@]}" shell am force-stop "$PKG"
wake
"${ADB[@]}" shell am start -W -n "$PKG/.QuizActivity" \
  --es trigger adb_digit_ok --es math_entry digit_pad --es force_qid m01 >/dev/null
sleep 2.5
dump_has 'text="OK"'
tap_text "2"
sleep 0.4
tap_text "OK"
sleep 0.8
dump_has 'Great job!' || true
sleep 3.2
SESS=$("${ADB[@]}" shell run-as "$PKG" cat files/stats/sessions.jsonl)
echo "$SESS" | tail -1 | grep -q '"outcome":"correct"'
echo "digit correct unlock OK"

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
if [[ "$GATE_MODE" == "hard" ]]; then
  enable_a11y
fi
"${ADB[@]}" shell am broadcast -a net.clevertree.quizgate.ACTION_DEBUG_FIRE -n "$PKG/.AlarmReceiver" >/dev/null
sleep 2
dump_has 'Please answer this question'
echo "alarm receiver OK"
if [[ "$GATE_MODE" == "soft" ]]; then
  "${ADB[@]}" shell input keyevent KEYCODE_HOME
else
  # Hard would reintercept Home; clear for dumpsys section.
  "${ADB[@]}" shell am force-stop "$PKG"
fi
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
