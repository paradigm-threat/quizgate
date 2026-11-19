#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/UnlockQuiz/src"
TESTS="$ROOT/tests"
OUT="$ROOT/build/test-classes"
JSON="$ROOT/UnlockQuiz/assets/questions.json"
rm -rf "$OUT"
mkdir -p "$OUT"
# Core + tests only (no Android framework sources)
# Skip Android-only helpers (e.g. AnonUserId uses Context) so JVM unit tests stay runnable.
find "$SRC/net/clevertree/quizgate/core" -name '*.java' ! -name 'AnonUserId.java' > "$OUT/sources.txt"
find "$TESTS" -name '*.java' >> "$OUT/sources.txt"
javac --release 17 -d "$OUT" @"$OUT/sources.txt"

cp "$JSON" "$OUT/questions.json"
echo "== CatalogTest =="
java -cp "$OUT" CatalogTest "$JSON"
echo "== QuizSessionTest =="
java -cp "$OUT" QuizSessionTest "$JSON"
echo "== SchedulerTest =="
java -cp "$OUT" SchedulerTest
echo "== StatsStoreTest =="
java -cp "$OUT" StatsStoreTest "$JSON"
echo "== BoundedJsonlTest =="
java -cp "$OUT" BoundedJsonlTest
echo "== DiagnosticsStoreTest =="
java -cp "$OUT" DiagnosticsStoreTest
echo "== TelemetryRedactionTest =="
java -cp "$OUT" TelemetryRedactionTest
echo "== BuildFingerprintTest =="
java -cp "$OUT" BuildFingerprintTest

echo "== DigitAnswerTest =="
java -cp "$OUT" DigitAnswerTest
echo "== QuestionPickerTest =="
java -cp "$OUT" QuestionPickerTest
echo "== MathPresentationTest =="
java -cp "$OUT" MathPresentationTest
echo "== PerfPolicyTest =="
java -cp "$OUT" PerfPolicyTest
echo "== GatePolicyTest =="
java -cp "$OUT" GatePolicyTest
echo "== ScreenLockPolicyTest =="
java -cp "$OUT" ScreenLockPolicyTest
echo "== CurriculumParserTest =="
java -cp "$OUT" CurriculumParserTest "$ROOT/UnlockQuiz/assets/curricula/basic-math"
echo "== TimeEarnTest =="
java -cp "$OUT" TimeEarnTest
echo "== QuizLayoutPolicyTest =="
java -cp "$OUT" QuizLayoutPolicyTest
echo "== QuizUiScaleTest =="
java -cp "$OUT" QuizUiScaleTest

echo "ALL UNIT TESTS PASSED"
