#!/usr/bin/env bash
# Headless regression gate for CI (packs + SI heuristics + JVM unit tests).
# Device e2e stays manual: SERIAL=<id> ./scripts/e2e_phone.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "== Validate curriculum packs =="
python3 curricula/scripts/validate_packs.py

echo "== SI batch review (heuristic) =="
python3 curricula/scripts/si_batch_review.py

echo "== Unit tests =="
./scripts/run_unit_tests.sh

# Optional device gate: MC/Skip fully on-screen and tappable.
if [[ -n "${SERIAL:-}" ]]; then
  echo "== Device: quiz controls visible (SERIAL=$SERIAL) =="
  "$ROOT/scripts/assert_quiz_controls_visible.sh"
elif command -v adb >/dev/null 2>&1; then
  DEV=$(adb devices 2>/dev/null | awk '/\tdevice$/{print $1; exit}')
  if [[ -n "${DEV:-}" ]]; then
    echo "== Device: quiz controls visible (adb $DEV) =="
    SERIAL="$DEV" "$ROOT/scripts/assert_quiz_controls_visible.sh"
  fi
fi

echo "REGRESSION OK"
