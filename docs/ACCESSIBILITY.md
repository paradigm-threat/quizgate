# Accessibility & visual contrast

QuizGate must stay readable for kids and parents in **light and dark** OS themes.
Invisible Home/Back, pale Skip, or light-on-white Setup rows are product bugs.

## What we automate (CI)

| Gate | Like (web) | What it catches |
|------|------------|-----------------|
| `tests/UiContrastTest.java` | **axe / ally** color rules | WCAG AA contrast for named text/control pairs in `values/` + `values-night/` |
| `QuizUiScaleTest` | layout overflow | Controls clipped / un-tappable |
| `ScreenLockPolicyTest` | behavior | Sleep/wake without forced wake |

Run with the rest of the suite:

```bash
./scripts/run_regression.sh
```

Add new kid-facing color pairs to `UiContrastCatalog.requiredPairs()` when you
introduce chrome. Surfaces used in drawables must be `@color/surface_*` so the
catalog and XML cannot drift.

## What to run on a device (manual)

Google’s **Accessibility Scanner** (Play Store) is the closest Android analog to
browser axe DevTools. After UI changes:

1. Install Accessibility Scanner.
2. Open QuizGate quiz + Parents (Setup) in **light** and **dark** OS themes.
3. Scan — fix any contrast / touch-target findings on Skip, digit keys, stats, Setup rows.

Espresso `AccessibilityChecks` / Compose accessibility validators are the
instrumented equivalent; this repo’s APK build is intentionally lightweight, so
the JVM contrast catalog is the CI stand-in.

## Theme rules

- App follows **OS night mode** (`values` / `values-night`).
- Light: `windowLightNavigationBar` + darker `system_bar` so OEM nav icons stay visible.
- Dark: light icons on near-black bars; Setup/stats cards use dark surfaces (never white rows with light text).
- Never hardcode `#FFFFFF` labels on cream fills — use `@color/header` / `@color/on_accent`.

## Do not force-stop to “refresh”

`adb shell am force-stop net.clevertree.quizgate` clears the Accessibility
service on Samsung. Prefer `am start` / install `-r` without force-stop when
testing UI.
