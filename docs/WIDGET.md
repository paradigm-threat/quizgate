# Home score widget + next-quiz countdown

## Why the widget may be missing

`ScoreWidgetProvider` is registered in the manifest and listed by the launcher, but **Android 12+ / Samsung One UI never auto-places widgets**. The home screen stays empty until a parent pins one.

On device `R95YA000ARX` (Galaxy Tab A9+), dumpsys showed the provider installed with **zero hosted instances** — so the tile was invisible by design, not because update logic failed.

## Add the widget

**In-app (preferred):** Setup → **Add home score widget** → confirm the system pin prompt.

**Manual (Samsung):** Long-press home → **Widgets** → **QuizGate** → **Today's score** → place on a home page.

After a quiz or stats change, `ScoreWidgetProvider.refreshAll()` redraws placed instances (`Ready!` or `right / asked`).

## Next-quiz countdown (no widget required)

Setup shows **Next quiz in ~N min** from `AppPrefs.nextAlarmAt()` (set by `AlarmScheduler`). The line refreshes about every 30s while Setup is open. Arm scheduling first; leave the interval at the default **30 ± 15** unless briefly testing.
