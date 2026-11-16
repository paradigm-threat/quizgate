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

echo "REGRESSION OK"
