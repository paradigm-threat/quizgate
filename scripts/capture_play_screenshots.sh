#!/usr/bin/env bash
# Clear personal QuizGate state, seed generic demo stats, capture Play Store screenshots.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SERIAL="${SERIAL:?Set SERIAL to the adb device id}"
APK="${1:-$ROOT/build/apk/QuizGate.apk}"
OUT="${OUT_DIR:-$ROOT/store/play/upload/screenshots}"
ADB=(adb -s "$SERIAL")
PKG=net.clevertree.quizgate
UI_DEV=/data/local/tmp/ui.xml
UI_HOST="${TMPDIR:-/tmp}/quizgate_ui_${SERIAL//[:.]/_}.xml"
SHOT_TMP="${TMPDIR:-/tmp}/quizgate_shot_${SERIAL//[:.]/_}.png"
mkdir -p "$OUT"

wake() {
  "${ADB[@]}" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  "${ADB[@]}" shell wm dismiss-keyguard >/dev/null 2>&1 || true
  "${ADB[@]}" shell input keyevent 82 >/dev/null 2>&1 || true
}

# Wireless-debugging Settings often steals focus after pairing — clear it.
focus_app() {
  "${ADB[@]}" shell am force-stop com.android.settings >/dev/null 2>&1 || true
  wake
}

dump_ui() {
  local i err
  wake
  for i in 1 2 3 4 5 6 7 8 9 10; do
    err=$("${ADB[@]}" shell uiautomator dump "$UI_DEV" 2>&1) || true
    if "${ADB[@]}" pull "$UI_DEV" "$UI_HOST" >/dev/null 2>&1 && [[ -s "$UI_HOST" ]] && grep -q 'text=' "$UI_HOST"; then
      # Ignore Settings overlays from wireless debugging
      if grep -q 'package="com.android.settings"' "$UI_HOST" \
        && ! grep -q "package=\"$PKG\"" "$UI_HOST"; then
        focus_app
        "${ADB[@]}" shell am start -n "$PKG/.OnboardingActivity" >/dev/null 2>&1 || true
        sleep 0.6
        continue
      fi
      return 0
    fi
    sleep 0.6
  done
  echo "dump_ui failed: $err" >&2
  return 1
}

tap_text() {
  local needle="$1"
  dump_ui
  local bounds line
  # Buttons often render ALL CAPS; match case-insensitively on exact text.
  while IFS= read -r line; do
    if echo "$line" | grep -qiE "text=\"${needle}\""; then
      bounds=$(echo "$line" | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p')
      [[ -n "$bounds" ]] && break
    fi
  done < <(sed 's/></>\n</g' "$UI_HOST")
  if [[ -z "${bounds:-}" ]]; then
    echo "tap_text: not found: $needle" >&2
    sed 's/></>\n</g' "$UI_HOST" | grep -E 'text=' | head -40 >&2 || true
    return 1
  fi
  local x1 y1 x2 y2
  read -r x1 y1 x2 y2 <<<"$bounds"
  "${ADB[@]}" shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
}

tap_id() {
  local rid="$1"
  dump_ui
  local bounds
  bounds=$(sed 's/></>\n</g' "$UI_HOST" | grep -F "resource-id=\"$rid\"" | head -1 \
    | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p')
  if [[ -z "$bounds" ]]; then
    echo "tap_id: not found: $rid" >&2
    return 1
  fi
  local x1 y1 x2 y2
  read -r x1 y1 x2 y2 <<<"$bounds"
  "${ADB[@]}" shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
}

ensure_landscape() {
  "${ADB[@]}" shell settings put system accelerometer_rotation 0 >/dev/null 2>&1 || true
  for rot in 1 0 3 2; do
    "${ADB[@]}" shell settings put system user_rotation "$rot" >/dev/null 2>&1 || true
    sleep 0.6
    "${ADB[@]}" shell screencap -p /sdcard/qg_orient.png >/dev/null
    "${ADB[@]}" pull /sdcard/qg_orient.png "$SHOT_TMP" >/dev/null
    local dims
    dims=$(python3 -c "from PIL import Image; w,h=Image.open('$SHOT_TMP').size; print(f'{w} {h}')")
    local shot_w shot_h
    read -r shot_w shot_h <<<"$dims"
    echo "rotation=$rot shot=${shot_w}x${shot_h}" >&2
    if [[ "$shot_w" -gt "$shot_h" ]]; then
      return 0
    fi
  done
  echo "WARN: could not force landscape; shots will be rotated in post" >&2
  return 0
}

save_shot() {
  local name="$1"
  wake
  sleep 0.4
  "${ADB[@]}" shell screencap -p /sdcard/qg_shot.png
  "${ADB[@]}" pull /sdcard/qg_shot.png "$SHOT_TMP" >/dev/null
  python3 - "$SHOT_TMP" "$OUT/$name.jpg" <<'PY'
import sys
from PIL import Image
src, dst = sys.argv[1], sys.argv[2]
im = Image.open(src).convert("RGB")
w, h = im.size
# Prefer landscape tablet frame 1920x1200
if w < h:
    im = im.rotate(90, expand=True)
    w, h = im.size
# Center-crop / letterbox to 1920x1200 if close
target = (1920, 1200)
if (w, h) != target:
    scale = max(target[0] / w, target[1] / h)
    nw, nh = int(w * scale), int(h * scale)
    im = im.resize((nw, nh), Image.Resampling.LANCZOS)
    left = (nw - target[0]) // 2
    top = (nh - target[1]) // 2
    im = im.crop((left, top, left + target[0], top + target[1]))
im.save(dst, "JPEG", quality=92, optimize=True)
print(dst, im.size)
PY
}

seed_demo_stats() {
  # Friendly sample history — no personal names; multi-day for stats screen.
  local now ms_day
  now=$("${ADB[@]}" shell date +%s)
  now=${now//$'\r'/}
  ms_day=86400000
  local today_ms=$(( now * 1000 ))
  local d1=$(( today_ms - ms_day ))
  local d2=$(( today_ms - 2 * ms_day ))
  local d3=$(( today_ms - 3 * ms_day ))
  local d4=$(( today_ms - 4 * ms_day ))
  local events sessions
  events=$(cat <<EOF
{"ts":$((d4+3600000)),"session_id":"demo-d4a","type":"shown","question_id":"m01","choice":"","miss_streak":0}
{"ts":$((d4+3605000)),"session_id":"demo-d4a","type":"correct","question_id":"m01","choice":"2","miss_streak":0}
{"ts":$((d4+7200000)),"session_id":"demo-d4b","type":"shown","question_id":"m03","choice":"","miss_streak":0}
{"ts":$((d4+7208000)),"session_id":"demo-d4b","type":"wrong","question_id":"m03","choice":"4","miss_streak":1}
{"ts":$((d4+7212000)),"session_id":"demo-d4b","type":"shown","question_id":"m05","choice":"","miss_streak":1}
{"ts":$((d4+7218000)),"session_id":"demo-d4b","type":"correct","question_id":"m05","choice":"6","miss_streak":0}
{"ts":$((d3+4000000)),"session_id":"demo-d3a","type":"shown","question_id":"m02","choice":"","miss_streak":0}
{"ts":$((d3+4005000)),"session_id":"demo-d3a","type":"correct","question_id":"m02","choice":"3","miss_streak":0}
{"ts":$((d3+8000000)),"session_id":"demo-d3b","type":"shown","question_id":"m04","choice":"","miss_streak":0}
{"ts":$((d3+8006000)),"session_id":"demo-d3b","type":"correct","question_id":"m04","choice":"5","miss_streak":0}
{"ts":$((d3+8100000)),"session_id":"demo-d3c","type":"shown","question_id":"m06","choice":"","miss_streak":0}
{"ts":$((d3+8105000)),"session_id":"demo-d3c","type":"correct","question_id":"m06","choice":"5","miss_streak":0}
{"ts":$((d2+3500000)),"session_id":"demo-d2a","type":"shown","question_id":"m07","choice":"","miss_streak":0}
{"ts":$((d2+3508000)),"session_id":"demo-d2a","type":"wrong","question_id":"m07","choice":"1","miss_streak":1}
{"ts":$((d2+3512000)),"session_id":"demo-d2a","type":"shown","question_id":"m08","choice":"","miss_streak":1}
{"ts":$((d2+3518000)),"session_id":"demo-d2a","type":"wrong","question_id":"m08","choice":"9","miss_streak":2}
{"ts":$((d2+3522000)),"session_id":"demo-d2a","type":"shown","question_id":"m09","choice":"","miss_streak":2}
{"ts":$((d2+3528000)),"session_id":"demo-d2a","type":"correct","question_id":"m09","choice":"7","miss_streak":0}
{"ts":$((d1+3000000)),"session_id":"demo-d1a","type":"shown","question_id":"m10","choice":"","miss_streak":0}
{"ts":$((d1+3005000)),"session_id":"demo-d1a","type":"correct","question_id":"m10","choice":"8","miss_streak":0}
{"ts":$((d1+7000000)),"session_id":"demo-d1b","type":"shown","question_id":"m11","choice":"","miss_streak":0}
{"ts":$((d1+7005000)),"session_id":"demo-d1b","type":"correct","question_id":"m11","choice":"4","miss_streak":0}
{"ts":$((d1+7100000)),"session_id":"demo-d1c","type":"shown","question_id":"m12","choice":"","miss_streak":0}
{"ts":$((d1+7105000)),"session_id":"demo-d1c","type":"correct","question_id":"m12","choice":"6","miss_streak":0}
{"ts":$((today_ms-600000)),"session_id":"demo-today-a","type":"shown","question_id":"m01","choice":"","miss_streak":0}
{"ts":$((today_ms-595000)),"session_id":"demo-today-a","type":"correct","question_id":"m01","choice":"2","miss_streak":0}
{"ts":$((today_ms-400000)),"session_id":"demo-today-b","type":"shown","question_id":"m15","choice":"","miss_streak":0}
{"ts":$((today_ms-395000)),"session_id":"demo-today-b","type":"correct","question_id":"m15","choice":"9","miss_streak":0}
{"ts":$((today_ms-200000)),"session_id":"demo-today-c","type":"shown","question_id":"m16","choice":"","miss_streak":0}
{"ts":$((today_ms-198000)),"session_id":"demo-today-c","type":"wrong","question_id":"m16","choice":"3","miss_streak":1}
{"ts":$((today_ms-190000)),"session_id":"demo-today-c","type":"shown","question_id":"m06","choice":"","miss_streak":1}
{"ts":$((today_ms-185000)),"session_id":"demo-today-c","type":"correct","question_id":"m06","choice":"5","miss_streak":0}
EOF
)
  sessions=$(cat <<EOF
{"session_id":"demo-d4a","trigger":"play","started_at":$((d4+3600000)),"ended_at":$((d4+3605000)),"outcome":"correct","wrong_count":0,"questions_shown":["m01"],"duration_ms":5000}
{"session_id":"demo-d3a","trigger":"play","started_at":$((d3+4000000)),"ended_at":$((d3+4005000)),"outcome":"correct","wrong_count":0,"questions_shown":["m02"],"duration_ms":5000}
{"session_id":"demo-d2a","trigger":"play","started_at":$((d2+3500000)),"ended_at":$((d2+3528000)),"outcome":"correct","wrong_count":2,"questions_shown":["m07","m08","m09"],"duration_ms":28000}
{"session_id":"demo-d1a","trigger":"play","started_at":$((d1+3000000)),"ended_at":$((d1+3005000)),"outcome":"correct","wrong_count":0,"questions_shown":["m10"],"duration_ms":5000}
{"session_id":"demo-today-a","trigger":"play","started_at":$((today_ms-600000)),"ended_at":$((today_ms-595000)),"outcome":"correct","wrong_count":0,"questions_shown":["m01"],"duration_ms":5000}
EOF
)
  local tmp_e tmp_s
  tmp_e="${TMPDIR:-/tmp}/qg_demo_events.jsonl"
  tmp_s="${TMPDIR:-/tmp}/qg_demo_sessions.jsonl"
  printf '%s\n' "$events" >"$tmp_e"
  printf '%s\n' "$sessions" >"$tmp_s"
  "${ADB[@]}" shell "run-as $PKG mkdir -p files/stats"
  "${ADB[@]}" shell "run-as $PKG tee files/stats/events.jsonl >/dev/null" <"$tmp_e"
  "${ADB[@]}" shell "run-as $PKG tee files/stats/sessions.jsonl >/dev/null" <"$tmp_s"
}

write_generic_prefs() {
  # Factory-ish defaults suitable for store screenshots (bundled photos, default schedule, no resume pkg).
  local xml
  xml=$(cat <<'EOF'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="onboarding_done" value="false" />
    <boolean name="armed" value="false" />
    <string name="photo_source">bundled</string>
    <string name="math_entry">digit_pad</string>
    <string name="active_curriculum_id">basic-math</string>
    <string name="active_tier">all</string>
    <boolean name="sync_enabled" value="true" />
    <int name="minutes_balance" value="15" />
    <long name="interval_ms" value="1800000" />
    <long name="jitter_ms" value="900000" />
    <long name="next_alarm_at" value="0" />
</map>
EOF
)
  local tmp_p
  tmp_p="${TMPDIR:-/tmp}/qg_unlock_quiz.xml"
  printf '%s\n' "$xml" >"$tmp_p"
  "${ADB[@]}" shell "run-as $PKG tee shared_prefs/unlock_quiz.xml >/dev/null" <"$tmp_p"
}

echo "Device: $("${ADB[@]}" get-serialno) model=$("${ADB[@]}" shell getprop ro.product.model | tr -d '\r')"
focus_app
echo "== Install APK =="
"${ADB[@]}" install -r "$APK"

echo "== Clear personal app data =="
"${ADB[@]}" shell am force-stop "$PKG"
"${ADB[@]}" shell pm clear "$PKG"
sleep 0.5

echo "== Seed generic prefs + demo stats =="
# Launch once so data dirs exist, then stop and write files.
"${ADB[@]}" shell am start -W -n "$PKG/.OnboardingActivity" >/dev/null || true
sleep 1
"${ADB[@]}" shell am force-stop "$PKG"
write_generic_prefs
seed_demo_stats
"${ADB[@]}" shell run-as "$PKG" cat shared_prefs/unlock_quiz.xml
echo "--- demo events lines: $(${ADB[@]} shell run-as $PKG wc -l files/stats/events.jsonl | tr -d '\r') ---"

ensure_landscape || true

mark_onboarding_done() {
  local tmp_p="${TMPDIR:-/tmp}/qg_unlock_quiz_done.xml"
  cat >"$tmp_p" <<'EOF'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="onboarding_done" value="true" />
    <boolean name="armed" value="false" />
    <string name="photo_source">bundled</string>
    <string name="math_entry">digit_pad</string>
    <string name="active_curriculum_id">basic-math</string>
    <string name="active_tier">all</string>
    <boolean name="sync_enabled" value="true" />
    <int name="minutes_balance" value="15" />
    <long name="interval_ms" value="1800000" />
    <long name="jitter_ms" value="900000" />
    <long name="next_alarm_at" value="0" />
</map>
EOF
  "${ADB[@]}" shell "run-as $PKG tee shared_prefs/unlock_quiz.xml >/dev/null" <"$tmp_p"
}

ui_has() {
  dump_ui && grep -qiF "$1" "$UI_HOST"
}

wait_for() {
  local needle="$1" i
  for i in 1 2 3 4 5 6 7 8 9 10; do
    if ui_has "$needle"; then return 0; fi
    sleep 0.4
  done
  echo "wait_for timeout: $needle" >&2
  sed 's/></>\n</g' "$UI_HOST" 2>/dev/null | grep -E 'text=' | head -30 >&2 || true
  return 1
}

echo "== 01 onboarding welcome =="
focus_app
"${ADB[@]}" shell am force-stop "$PKG"
write_generic_prefs
"${ADB[@]}" shell am start -W -n "$PKG/.OnboardingActivity" >/dev/null
sleep 1.2
wait_for "Welcome to QuizGate"
save_shot "01_onboarding_welcome"

echo "== 02 onboarding photos =="
tap_id "net.clevertree.quizgate:id/btn_onboard_next"
sleep 0.8
wait_for "Quiz pictures"
save_shot "02_onboarding_photos"

echo "== 03 onboarding schedule =="
# Prefer the photo-actions button; do NOT fall through to Continue on the schedule step.
if ui_has "resource-id=\"net.clevertree.quizgate:id/btn_use_defaults\""; then
  tap_id "net.clevertree.quizgate:id/btn_use_defaults"
else
  # Already advanced, or next button still labeled as defaults on photos step
  tap_id "net.clevertree.quizgate:id/btn_onboard_next"
fi
sleep 0.8
wait_for "How often should QuizGate"
dump_ui
grep -qF 'text="Skip"' "$UI_HOST" || echo "WARN: expected onboarding Skip on schedule step" >&2
save_shot "03_onboarding_schedule"

echo "== 04 setup =="
# Capture Setup via direct launch + generic prefs (avoid relying on finishing onboarding UI).
"${ADB[@]}" shell am force-stop "$PKG"
mark_onboarding_done
seed_demo_stats
"${ADB[@]}" shell am start -W -n "$PKG/.SetupActivity" >/dev/null
sleep 1.0
wait_for "Arm scheduling"
save_shot "04_setup"

echo "== 05 quiz math =="
"${ADB[@]}" shell am force-stop "$PKG"
mark_onboarding_done
seed_demo_stats
"${ADB[@]}" shell am start -W -n "$PKG/.QuizActivity" \
  --es trigger play --es math_entry digit_pad --es force_qid m01 >/dev/null
sleep 1.5
wait_for "Please answer this question"
save_shot "05_quiz_math"

echo "== 06 success sheet =="
tap_text "2" || true
sleep 0.3
tap_text "OK" || true
sleep 1.0
wait_for "Great job!"
sleep 0.6
save_shot "06_success_sheet"

echo "== 07 stats history =="
if ! ui_has "View stats" && ! grep -q 'btn_view_stats' "$UI_HOST" 2>/dev/null; then
  "${ADB[@]}" shell am force-stop "$PKG"
  mark_onboarding_done
  seed_demo_stats
  "${ADB[@]}" shell am start -W -n "$PKG/.QuizActivity" \
    --es trigger play --es math_entry digit_pad --es force_qid m01 >/dev/null
  sleep 1.2
  tap_text "2" || true; sleep 0.3; tap_text "OK" || true; sleep 1.0
  wait_for "Great job!" || true
fi
tap_id "net.clevertree.quizgate:id/btn_view_stats" || tap_text "View stats"
sleep 1.0
wait_for "Your scores"
save_shot "07_stats_history"

echo "== Leave app / try widget (best-effort) =="
"${ADB[@]}" shell input keyevent KEYCODE_HOME
sleep 1
WIDGET_HOSTED=$("${ADB[@]}" shell dumpsys appwidget 2>/dev/null | grep -c 'net.clevertree.quizgate/.ScoreWidgetProvider' || true)
if [[ "${WIDGET_HOSTED//$'\r'/}" -gt 0 ]]; then
  # Only keep 08 if home launcher is foreground
  if "${ADB[@]}" shell dumpsys activity activities 2>/dev/null | head -40 | grep -qiE 'launcher|homescreen|touchwiz'; then
    save_shot "08_score_widget" || true
    echo "captured 08_score_widget (best-effort)"
  else
    echo "Widget provider registered but launcher not foreground — skip 08"
    rm -f "$OUT/08_score_widget.jpg"
  fi
else
  echo "No QuizGate widget pinned — skip 08_score_widget"
  rm -f "$OUT/08_score_widget.jpg"
fi

echo "== Final generic prefs for installed demo APK =="
"${ADB[@]}" shell am force-stop "$PKG"
mark_onboarding_done
seed_demo_stats
"${ADB[@]}" shell am broadcast -a android.appwidget.action.APPWIDGET_UPDATE -n "$PKG/.ScoreWidgetProvider" >/dev/null 2>&1 || true

echo "DONE. Screenshots in $OUT:"
ls -la "$OUT"/*.jpg
# Quick content sanity via filenames only; visual QA is separate
for f in 01_onboarding_welcome 02_onboarding_photos 03_onboarding_schedule 04_setup 05_quiz_math 06_success_sheet 07_stats_history; do
  [[ -f "$OUT/$f.jpg" ]] || { echo "MISSING $f.jpg" >&2; exit 1; }
done
echo "Required set present (01–07)."
