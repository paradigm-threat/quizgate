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
find "$SRC/net/clevertree/quizgate/core" -name '*.java' > "$OUT/sources.txt"
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
echo "== DigitAnswerTest =="
java -cp "$OUT" DigitAnswerTest
echo "== QuestionPickerTest =="
java -cp "$OUT" QuestionPickerTest
echo "== PerfPolicyTest =="
java -cp "$OUT" PerfPolicyTest
echo "== CurriculumParserTest =="
java -cp "$OUT" CurriculumParserTest "$ROOT/UnlockQuiz/assets/curricula/basic-math"
echo "ALL UNIT TESTS PASSED"
