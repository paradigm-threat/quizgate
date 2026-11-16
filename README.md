# QuizGate

CleverTree soft quiz gate for young kids: every hour + morning gate, math digit pad + colors, local device photo transitions, unlock after 3 misses or bypass (Home). Today’s stats on the home screen. No MacroDroid. Free — no ads, no IAP, no subscription.

Package: `net.clevertree.quizgate`

Photos are loaded from the device gallery at runtime (MediaStore). No personal photos are bundled in the APK or repo.

## Build

```bash
./scripts/run_unit_tests.sh
./scripts/build_apk.sh
```

## Install / test

```bash
SERIAL=<device> ./scripts/e2e_phone.sh
# or
adb -s <device> install -r build/apk/QuizGate.apk
adb -s <device> shell am start -n net.clevertree.quizgate/.SetupActivity
```

Force quiz:

```bash
adb shell am start -n net.clevertree.quizgate/.QuizActivity --es trigger adb_force
```

Short interval (2 min) then arm:

```bash
adb shell am start -n net.clevertree.quizgate/.SetupActivity --el interval_ms 120000 --ez auto_arm true
```

Fire quiz via alarm receiver (no MacroDroid):

```bash
adb shell am broadcast -a net.clevertree.quizgate.ACTION_DEBUG_FIRE -n net.clevertree.quizgate/.AlarmReceiver
```

On OPPO/ColorOS: open the app once, accept **battery optimization exemption**, notifications, and **photos** permission so exact hourly alarms and gallery transitions work.

Dump stats (debuggable / run-as):

```bash
adb shell run-as net.clevertree.quizgate cat files/stats/sessions.jsonl
adb shell run-as net.clevertree.quizgate cat files/stats/events.jsonl
```

## Behavior

- Header: “Please answer this question”
- MVP catalog: addition-under-10 math + colors only (no counting, no animals)
- Math: spoken + `2 + 5 = _` (digit pad by default)
- Wrong → fade + “Retry in” countdown → new question
- 3 wrong → fail overlay + 6s countdown → unlock
- Leave without answering → bypass (no reopen), logged
- YouTube / music soft-pauses when the quiz opens and tries to resume after unlock
- Success / error transitions use random images from the device gallery when permitted
