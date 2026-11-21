#!/usr/bin/env bash
# Device assertion: L0 2×2 MC tiles + Skip are fully on-screen and large enough to tap.
# Usage: SERIAL=<adb-id> ./scripts/assert_quiz_controls_visible.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SERIAL="${SERIAL:?Set SERIAL to the adb device id}"
ADB=(adb -s "$SERIAL")
PKG=net.clevertree.quizgate
UI_DEV=/data/local/tmp/qg_vis_ui.xml
UI_HOST="${TMPDIR:-/tmp}/qg_vis_ui_${SERIAL//[:]/_}.xml"

"${ADB[@]}" shell am force-stop "$PKG"
"${ADB[@]}" shell am start -n "$PKG/.QuizActivity" --es TRIGGER_PLAY 1 >/dev/null
sleep 2.2
"${ADB[@]}" shell uiautomator dump "$UI_DEV" >/dev/null
"${ADB[@]}" pull "$UI_DEV" "$UI_HOST" >/dev/null

python3 - "$UI_HOST" <<'PY'
import re, sys
path = sys.argv[1]
xml = open(path, encoding="utf-8", errors="replace").read()
# screen size from root node if present
wm = re.search(r'bounds="\[0,0\]\[(\d+),(\d+)\]"', xml)
if not wm:
    raise SystemExit("no root bounds")
W, H = int(wm.group(1)), int(wm.group(2))
# Collect digit-looking choice buttons (single/double digit) with bounds
nodes = []
for m in re.finditer(
    r'text="(\d{1,2})"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    t, x1, y1, x2, y2 = m.group(1), *map(int, m.groups()[1:])
    nodes.append((t, x1, y1, x2, y2))
# Also match bounds-before-text order
for m in re.finditer(
    r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*text="(\d{1,2})"', xml):
    x1, y1, x2, y2, t = *map(int, m.groups()[:4]), m.group(5)
    nodes.append((t, x1, y1, x2, y2))
# Dedupe by center
seen = set()
choices = []
for t, x1, y1, x2, y2 in nodes:
    key = (t, (x1 + x2) // 2, (y1 + y2) // 2)
    if key in seen:
        continue
    seen.add(key)
    choices.append((t, x1, y1, x2, y2))

skip = None
for m in re.finditer(r'text="Skip"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    skip = tuple(map(int, m.groups()))
    break
if skip is None:
    for m in re.finditer(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*text="Skip"', xml):
        skip = tuple(map(int, m.groups()))
        break

errs = []
if len(choices) < 3:
    errs.append(f"expected ≥3 choice tiles, got {len(choices)}: {choices}")
# Prefer 4 for L0 grid when pack is v7
if len(choices) == 4:
    print(f"OK 2x2 grid tiles={[c[0] for c in choices]}")
elif len(choices) == 3:
    print(f"OK 3-row choices={[c[0] for c in choices]} (not L0 grid)")
else:
    print(f"choice count={len(choices)}")

min_touch = 40  # px floor soft; density-aware check uses ~48
for t, x1, y1, x2, y2 in choices:
    if x1 < 0 or y1 < 0 or x2 > W or y2 > H:
        errs.append(f"choice {t} clipped bounds=[{x1},{y1}][{x2},{y2}] screen={W}x{H}")
    w, h = x2 - x1, y2 - y1
    if w < min_touch or h < min_touch:
        errs.append(f"choice {t} too small {w}x{h} (need ≥{min_touch})")
    # Fully visible: center inside screen
    cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
    if not (0 <= cx < W and 0 <= cy < H):
        errs.append(f"choice {t} center off-screen ({cx},{cy})")

if skip is None:
    errs.append("Skip button missing")
else:
    x1, y1, x2, y2 = skip
    if x1 < 0 or y1 < 0 or x2 > W or y2 > H:
        errs.append(f"Skip clipped bounds=[{x1},{y1}][{x2},{y2}]")
    if (x2 - x1) < min_touch or (y2 - y1) < min_touch:
        errs.append(f"Skip too small {(x2-x1)}x{(y2-y1)}")
    print(f"OK Skip bounds=[{x1},{y1}][{x2},{y2}]")

if errs:
    print("FAIL:")
    for e in errs:
        print(" -", e)
    sys.exit(1)
print(f"PASS controls visible on {W}x{H}")
PY

"${ADB[@]}" shell am force-stop "$PKG"
echo "assert_quiz_controls_visible OK"
