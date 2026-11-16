#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
BT="$(ls -d "$SDK"/build-tools/*/ | sort -V | tail -1)"
PLAT="$SDK/platforms/android-34/android.jar"
APP="$ROOT/UnlockQuiz"
OUT="$ROOT/build/apk"
GEN="$ROOT/build/gen"
OBJ="$ROOT/build/obj"
DEX="$ROOT/build/dex"
RES_FLAT="$ROOT/build/flat"
rm -rf "$OUT" "$GEN" "$OBJ" "$DEX" "$RES_FLAT"
mkdir -p "$OUT" "$GEN" "$OBJ" "$DEX" "$RES_FLAT"

"$BT/aapt2" compile --dir "$APP/res" -o "$RES_FLAT/"
FLAT_ARGS=()
while IFS= read -r -d '' f; do FLAT_ARGS+=("$f"); done < <(find "$RES_FLAT" -name '*.flat' -print0)

"$BT/aapt2" link -o "$OUT/unsigned.apk" -I "$PLAT" \
  --manifest "$APP/AndroidManifest.xml" \
  --java "$GEN" \
  --auto-add-overlay \
  "${FLAT_ARGS[@]}" \
  -A "$APP/assets"

find "$APP/src" "$GEN" -name '*.java' > "$OUT/sources.txt"
javac --release 17 -classpath "$PLAT" -d "$OBJ" @"$OUT/sources.txt"
"$BT/d8" --lib "$PLAT" --output "$DEX" $(find "$OBJ" -name '*.class')
(cd "$DEX" && zip -u "$OUT/unsigned.apk" classes.dex)

KS="$ROOT/debug.keystore"
if [[ ! -f "$KS" ]]; then
  keytool -genkeypair -v -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=QuizGate,O=Clevertree,C=US"
fi
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT/QuizGate.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify "$OUT/QuizGate.apk"
ls -la "$OUT/QuizGate.apk"
echo "APK=$OUT/QuizGate.apk"
