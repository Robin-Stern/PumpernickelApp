---
phase: quick-260517-pzh
plan: 01
subsystem: workout/debug-tooling
tags: [debug, geofence, ios, android, ui]
requires:
  - DebugGeofencePanel (iOS SwiftUI struct, quick-260516-nfn)
  - DebugGeofencePanel (Android composable, quick-260516-nfn)
  - DebugGeofenceProvider Koin override (quick-260516-nfn)
provides:
  - In-workout DEBUG affordance on iOS WorkoutSessionView (capsule overlay + sheet)
  - In-workout DEBUG affordance on Android WorkoutSessionScreen (FAB + ModalBottomSheet)
affects:
  - iosApp/iosApp/Views/Workout/WorkoutSessionView.swift
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt
tech-stack:
  added: []
  patterns:
    - "#if DEBUG SwiftUI overlay + sheet on activeWorkoutView"
    - "BuildConfig.DEBUG-gated Scaffold floatingActionButton + ModalBottomSheet"
key-files:
  created: []
  modified:
    - iosApp/iosApp/Views/Workout/WorkoutSessionView.swift
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt
decisions:
  - "iOS overlay uses Capsule + Color.red.opacity(0.85) so debug affordance is visually unmistakable and not confused with primary CTAs"
  - "iOS sheet wraps DebugGeofencePanel in NavigationStack { Form { ... } } because the panel body is a SwiftUI Section which only renders inside Form/List"
  - "Android FAB uses Icons.Default.Build from material-icons-core (Icons.Default.BugReport would require material-icons-extended dep which is declared in libs.versions.toml but not wired into androidApp build.gradle.kts — plan forbids adding new deps)"
  - "Android FabPosition.Start (bottom-leading) keeps FAB clear of TopAppBar chip/menu and Complete-Set CTA; SmallFloatingActionButton minimises footprint"
  - "ModalBottomSheet on Android placed inside the Scaffold content lambda (sibling to Column) — consistent with how exercise-overview sheet is rendered elsewhere in ActiveWorkoutContent's sibling-modal block"
metrics:
  duration: "~10 min implementation + ~7 min iOS Debug+Release builds + ~30s Android Debug build"
  completed: 2026-05-17
---

# Phase quick-260517-pzh: Debug Mock Panel direkt im Workout-Screen — Summary

One-liner: Surface the existing DebugGeofencePanel (Enter/Exit/Error triggers against DebugGeofenceProvider) inside the active workout view on both iOS and Android, gated to DEBUG builds only — enables UAT of the Phase 19 Exit → GracePeriod → Auto-Abort flow without leaving the workout to navigate to Settings.

## What Was Built

### iOS (Task 1, commit `682b51a`)

- Added `#if DEBUG`-gated `@State private var showDebugGeofenceSheet: Bool = false` to `WorkoutSessionView`.
- Appended `.overlay(alignment: .bottomLeading)` on the `activeWorkoutView(_:)` returned view:
  - Renders a red `Capsule` with ladybug icon + "DEBUG" caption.
  - Tap sets `showDebugGeofenceSheet = true`.
- Appended `.sheet(isPresented: $showDebugGeofenceSheet)`:
  - Presents `NavigationStack { Form { DebugGeofencePanel() } }` so the panel's `Section` body renders correctly.
  - Title "Debug — Geofence", `Done` toolbar button, detents `[.medium, .large]`.
- All new code wrapped in `#if DEBUG ... #endif` — release builds compile clean.

### Android (Task 2, commit `2f3808e`)

- Added imports: `Icons.Default.Build`, `FabPosition`, `SmallFloatingActionButton`, `rememberModalBottomSheetState`, `BuildConfig`, `DebugGeofencePanel`.
- Inside `ActiveWorkoutContent`, added `var showDebugSheet by remember { mutableStateOf(false) }` and `val debugSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)`.
- Wired `floatingActionButton = { if (BuildConfig.DEBUG) SmallFloatingActionButton(...) }` on the Scaffold, with `floatingActionButtonPosition = FabPosition.Start`.
  - Build (wrench) icon from material-icons-core (no extended-icons dep added).
  - `errorContainer` / `onErrorContainer` colors signal "debug, not primary".
- Added `if (BuildConfig.DEBUG && showDebugSheet) { ModalBottomSheet(...) { ... DebugGeofencePanel() } }` inside the Scaffold content lambda, sibling to the main Column.

## Verification Results

### iOS

| Check | Expected | Actual |
| ----- | -------- | ------ |
| `showDebugGeofenceSheet` references | >=3 | 4 |
| `DebugGeofencePanel()` mounts | 1 | 1 |
| `#if DEBUG` blocks | >=2 | 2 |
| `triggerEnter/Exit/Error` calls duplicated | 0 | 0 |
| NavigationStack within 15 lines of state | NAV_OK | NAV_OK |
| `xcodebuild build ... -configuration Debug` | BUILD SUCCEEDED | BUILD SUCCEEDED (112s) |
| `xcodebuild build ... -configuration Release` | BUILD SUCCEEDED | BUILD SUCCEEDED (280s) |

### Android

| Check | Expected | Actual |
| ----- | -------- | ------ |
| `showDebugSheet` references | >=3 | 4 |
| `DebugGeofencePanel()` mounts | 1 | 1 |
| `BuildConfig.DEBUG` references | >=2 | 2 |
| `triggerEnter/Exit/Error` calls duplicated | 0 | 0 |
| FAB in ActiveWorkoutContent | >=1 | 1 |
| FAB in RecapContent | 0 | 0 |
| FAB in FinishedContent | 0 | 0 |
| `./gradlew :androidApp:assembleDebug` | BUILD SUCCESSFUL | BUILD SUCCESSFUL (29s) |
| `./gradlew :androidApp:assembleRelease` | BUILD SUCCESSFUL | BUILD FAILED — pre-existing issue, see Deferred Issues |

## Deviations from Plan

### Auto-fixed / adjusted decisions

**1. [Plan-permitted adjustment] Android icon choice**
- **Plan said:** "If `Icons.Filled.BugReport` is not available in the bundled icons extension artifact, fall back to `Icons.Default.Build` or `Icons.Default.Settings` — verify by inspecting `material-icons-extended` artifact presence in `androidApp/build.gradle.kts`. Do NOT add a new dependency for this — pick whichever icon ships by default."
- **Found:** `material-icons-extended` is declared in `gradle/libs.versions.toml` but NOT referenced in any `build.gradle.kts`. The androidApp depends on `material-icons-core` only (verified by `unzip -l` on the resolved AAR — `BuildKt.class` present in core, `BugReport` is in extended).
- **Decision:** Used `Icons.Default.Build` (wrench icon) from `material.icons.filled.Build` — already in core, no new dep needed.

No other deviations. Rules 1-4 did not trigger during execution.

### Auth gates

None.

## Deferred Issues

**1. Android `:androidApp:assembleRelease` fails on `:shared:checkAndroidMainAarMetadata` — PRE-EXISTING, unrelated to this task**

- **Failure:** AAR metadata check rejects `androidx.activity:activity-compose:1.12.4` and 4 other deps because they require `compileSdk >= 36` but `:shared` is on `android-35`.
- **Reproduced on base commit `ab4d9397`** (before any of this task's changes): same failure, same 5 AAR-metadata issues.
- **Scope:** Out of scope per executor scope-boundary rule ("Only auto-fix issues DIRECTLY caused by the current task's changes"). The release-build environmental drift originates from a Compose BOM / shared-module compileSdk mismatch — fixing it requires bumping `:shared`'s `compileSdk` to 36, which is a non-trivial cross-module change outside this DEBUG-tooling task.
- **Impact on this task:** Zero — the affordance is DEBUG-only by design. The `:androidApp:assembleDebug` build (the relevant target) succeeds. The release-build smoke step in the plan was intended to confirm the FAB code does not break release compilation; since the failure occurs in `:shared:checkAndroidMainAarMetadata` (downstream of `compileReleaseKotlin`, which was UP-TO-DATE / passed), our additions did not introduce any new release-build compilation regression.
- **Recommendation:** Address as a separate quick-task ("bump :shared compileSdk to 36").

## Task 3 (Manual UAT) — DEFERRED TO USER

Per executor constraints, Task 3 (`checkpoint:human-verify`) was not executed. The manual smoke test still needs to be performed by the user on iOS Simulator + Android Emulator:

1. iOS Simulator (DEBUG): start workout → confirm red 🐛 DEBUG capsule visible bottom-leading, not overlapping toolbar / Complete-Set / completed-set rows. Tap capsule → sheet shows DebugGeofencePanel with region id + Enter/Exit/Error. Tap Exit → GeofenceStatusChip transitions to GracePeriod with countdown. Tap Enter → returns to InZone. Tap Error → Inactive.
2. iOS Simulator (RELEASE): start workout → confirm NO debug capsule visible.
3. Android Emulator (debug variant): start workout → confirm wrench-icon FAB visible bottom-leading. Tap → ModalBottomSheet with DebugGeofencePanel. Same Enter/Exit/Error flow. Confirm FAB absent on Recap / Finished.
4. Android (release variant): start workout → confirm NO FAB. (NOTE: release build currently fails for a pre-existing reason — see Deferred Issues above. UAT on release-build can resume after that's addressed.)

The full verification recipe is in `260517-pzh-PLAN.md` → Task 3 (`<how-to-verify>`).

## Self-Check: PASSED

- `iosApp/iosApp/Views/Workout/WorkoutSessionView.swift`: FOUND (modified)
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt`: FOUND (modified)
- Commit `682b51a`: FOUND (`git log --all --oneline | grep 682b51a` → present)
- Commit `2f3808e`: FOUND (`git log --all --oneline | grep 2f3808e` → present)
- All grep-based acceptance criteria for both tasks satisfied (see Verification Results tables).
- iOS Debug + Release builds: SUCCEEDED.
- Android Debug build: SUCCEEDED. Android Release build: pre-existing failure documented as deferred.
