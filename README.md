# AndroTask

A no-root Android macro recorder inspired by Macrorify, powered by Android Accessibility Services.
Record, edit, and replay touch/swipe/pinch gestures — no programming knowledge or root required.

---

## Features

| Feature | Details |
|---|---|
| **Gesture recording** | Tap, long-press, swipe, pinch, scroll, volume, navigation keys, text input |
| **Two recording modes** | Passive (detects taps via accessibility events) · Overlay (intercepts touches via a transparent layer) |
| **Playback** | Adjustable speed (0.25×–4×), loop count (including infinite ∞), per-loop delay |
| **Human-like timing** | Per-step jitter ± ms, per-step delay-before |
| **Enable/disable toggle** | Each macro has an on/off switch — disabled macros are greyed out and won't run |
| **Step editor** | Edit coordinates, duration, jitter, delay, label; reorder via arrows; duplicate; play from step |
| **Coordinate picker** | Tap "pick on screen" to choose XY coordinates visually without typing |
| **Undo history** | 50-level undo stack in the step editor |
| **Scheduling** | Schedule macros by time + day-of-week (Mon–Sun); correctly mapped to Android Calendar day constants |
| **Import / Export** | JSON export per-macro or all macros; import from file or clipboard |
| **Quick Settings tile** | Toggle recording from the notification shade |
| **Floating bubble** | 56 dp density-independent bubble visible over any app |
| **Dark theme** | Macrorify-inspired dark green color scheme throughout |
| **Step type color coding** | Each step type (tap, swipe, wait, …) has its own accent color |
| **No root required** | Uses Android Accessibility Services only |
| **No Android Studio required** | Builds from CLI (Termux / Debian / any JDK 17 environment) |

---

## Screenshots (UI matches Macrorify)

- **Home screen** — macro cards with green accent bar, enable/disable switch, step count badge, play button, overflow menu
- **Editor** — color-coded step chips, speed slider, recording mode selector, undo button, preview/play-from-step
- **Step editor** — visual type selector with color coding, on-screen coordinate picker
- **Scheduling** — day-of-week chips, existing schedule list with delete

---

## Requirements

- Android 8.0+ (API 26+)
- JDK 17
- Gradle 8.x

---

## Build from CLI

```bash
git clone https://github.com/BorgorNinja/AndroTask.git
cd AndroTask
chmod +x gradlew
./gradlew assembleDebug
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

---

## Permissions required

| Permission | Why |
|---|---|
| **Accessibility Service** | Dispatch gestures and detect taps in passive recording mode |
| **Draw Over Other Apps** | Show floating bubble and recording overlay |
| **Post Notifications** (Android 13+) | Persistent foreground service notification |

Enable via the in-app **Setup** screen, or manually:

> Settings → Accessibility → AndroTask → Enable  
> Settings → Apps → AndroTask → Display over other apps → Allow

---

## Quick start

1. Enable all permissions in the **Setup** screen.
2. Tap **+** on the home screen to create a macro.
3. Choose recording mode: **Passive** or **Overlay**.
4. Tap **Record Gestures**, perform your actions, tap **Stop**.
5. Save, then tap **▶** to run.
6. Use the **floating bubble** to toggle recording from any app.
7. Schedule macros via the **⋮ menu → Schedule**.

---

## Bug fixes in v1.1.0

- `wm.defaultDisplay.getRealMetrics()` deprecated since API 30 → replaced with `wm.currentWindowMetrics.bounds`
- `Divider()` deprecated in Material3 → replaced with `HorizontalDivider()`
- `ScheduledTrigger.repeatDays` was stored but never used — alarms now correctly fire only on selected days
- Day-of-week mismatch: stored `1=Mon…7=Sun` was not converted to `Calendar.MONDAY…Calendar.SUNDAY`
- Macro `isEnabled` toggle was missing from DAO / repository / ViewModel / UI
- Floating bubble was 150 raw pixels (tiny on high-DPI) → now 56 dp
- `initialized` flag in editor not reset when navigating between macros
- White flash on launch due to `Theme.Material.Light` → now `Theme.Material.NoActionBar` with `#0F0F0F` background

---

## Architecture

```
MainActivity
└── NavHost
    ├── HomeScreen        ← macro list, enable/disable, play, export/import, schedule
    ├── MacroEditorScreen ← step list, recording, undo, speed, save
    │   └── StepEditDialog
    ├── SettingsScreen
    └── OnboardingScreen  ← permission setup

FloatingOverlayService    ← foreground service, bubble, overlay capture
MacroAccessibilityService ← gesture dispatch, passive recording
MacroAlarmReceiver        ← scheduled macro triggers (per-day alarms)
MacroQuickTile            ← Quick Settings tile

data/
  Macro, MacroStep, MacroWithSteps
  ScheduledTrigger, Converters
  MacroDao, AppDatabase, MacroRepository

viewmodel/MacroViewModel  ← UI state, coroutine bridge to repo

ui/
  Theme.kt                ← Macrorify-style dark green MaterialTheme
  HomeScreen, MacroEditorScreen, StepEditDialog
  SettingsScreen, OnboardingScreen
```

---

## License

MIT
