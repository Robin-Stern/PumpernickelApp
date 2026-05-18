---
phase: quick-260517-vn7
plan: 01
subsystem: settings + workout-session
tags: [settings, debug, geofence, kmp, ios, android]
requires:
  - SettingsRepository (commonMain)
  - SettingsViewModel (commonMain)
  - WorkoutSessionViewModel.startGracePeriod (commonMain)
provides:
  - SettingsRepository.debugModeEnabled (Flow<Boolean>, default true)
  - SettingsRepository.gracePeriodSeconds (Flow<Long>, default 300L)
  - SettingsRepository.setDebugModeEnabled / setGracePeriodSeconds
  - SettingsViewModel.debugModeEnabled / gracePeriodSeconds StateFlows + setters (@NativeCoroutinesState)
affects:
  - iOS Settings (DEBUG-gated Section)
  - iOS WorkoutSessionView (#if DEBUG pill gating)
  - Android SettingsSheet (BuildConfig.DEBUG-gated section)
  - Android WorkoutSessionScreen (FAB gating)
tech-stack:
  added: []
  patterns:
    - "@NativeCoroutinesState StateFlows bridged as KotlinBoolean/KotlinLong on Swift side (.boolValue / .int64Value)"
    - "Multiple parallel .task modifiers for independent flow observations"
    - "ExposedDropdownMenuBox + menuAnchor(MenuAnchorType.PrimaryNotEditable) for >2-option pickers"
key-files:
  created: []
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/settings/SettingsViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt
    - iosApp/iosApp/Views/Settings/SettingsView.swift
    - iosApp/iosApp/Views/Workout/WorkoutSessionView.swift
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/SettingsSheet.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt
decisions:
  - "Captured grace-period at countdown start (settingsRepository.gracePeriodSeconds.first()) — picker changes do not retroactively affect a running countdown."
  - "XpFormula.GEOFENCE_GRACE_PERIOD_SECONDS retained as canonical default referenced from the repo — keeps a single source of truth for the 300L default."
  - "Boolean/Long StateFlow bridge requires .boolValue/.int64Value on Swift side (matches existing OverviewRankStrip + WorkoutSessionView patterns)."
  - "Android Debug section uses unqualified Material3 imports + MenuAnchorType.PrimaryNotEditable (matches CreateExerciseScreen/AiSettingsScreen API surface)."
metrics:
  duration: "~25 minutes (one-pass execution, one Android compile-error fix iteration)"
  completed: 2026-05-17
---

# Quick 260517-vn7: Settings Debug-Modus toggle + Grace-Period picker Summary

Replaces the always-on inline `DebugGeofencePanel()` in Settings with a user-controllable Debug section (toggle + 5-option grace-period picker). `WorkoutSessionViewModel.startGracePeriod` now reads the configured grace seconds from `SettingsRepository` at countdown start instead of the hard-coded `XpFormula` constant. The in-workout debug overlay (iOS pill / Android FAB) is now gated on the toggle so demo recordings can hide it cleanly.

## Tasks Completed

| Task | Name                                                                                             | Commit  |
| ---- | ------------------------------------------------------------------------------------------------ | ------- |
| 1    | Shared — SettingsRepository + SettingsViewModel + WorkoutSessionViewModel.startGracePeriod       | f3bb4a9 |
| 2    | iOS — SettingsView Debug Section + WorkoutSessionView pill gating                                | 9ffe9ad |
| 3    | Android — SettingsSheet Debug section + WorkoutSessionScreen FAB gating                          | eef64c2 |

## Verification Results

| Build                                               | Result      | Notes                                                                                     |
| --------------------------------------------------- | ----------- | ----------------------------------------------------------------------------------------- |
| `:shared:compileKotlinIosSimulatorArm64`            | BUILD SUCCESSFUL | warnings only, all pre-existing                                                       |
| `:shared:compileAndroidMain`                        | BUILD SUCCESSFUL | warnings only, all pre-existing                                                       |
| `xcodebuild iosApp -destination iPhone 17`          | BUILD SUCCEEDED  | 30.06 sec                                                                              |
| `:androidApp:assembleDebug`                         | BUILD SUCCESSFUL | first attempt failed (ExposedDropdownMenu scope issue), fixed forward in same task     |

## Default-Behavior Compliance

- `debugModeEnabled` defaults to `true` in the repo Flow (`prefs[debugModeEnabledKey] ?: true`). In a DEBUG build with no user changes the in-workout pill/FAB stays visible — verified by code path: pill renders behind `if debugModeEnabled` and the StateFlow initial value is `true`.
- `gracePeriodSeconds` defaults to `XpFormula.GEOFENCE_GRACE_PERIOD_SECONDS` (300L) via the repo Flow's elvis fallback. `WorkoutSessionViewModel.startGracePeriod` reads `settingsRepository.gracePeriodSeconds.first()` at countdown start — unchanged behavior for users who never touch the picker.
- After user picks `5 Sek.`, the NEXT grace-period start reads `5L` from the repo because `.first()` re-reads the Flow at every countdown invocation (the StateFlow always emits the current persisted value).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - API mismatch] Material3 ExposedDropdownMenu scope + menuAnchor signature**
- **Found during:** Task 3 first Android build (`compileDebugKotlin` failed with `Unresolved reference 'ExposedDropdownMenu'` and `@Composable invocations can only happen from the context of a @Composable function`).
- **Issue:** Plan pseudocode fully-qualified `androidx.compose.material3.ExposedDropdownMenu` and called `.menuAnchor()` with no argument. `ExposedDropdownMenu` is a member of `ExposedDropdownMenuBoxScope` and cannot be invoked through an FQN. `menuAnchor()` with no args was deprecated — the current stable overload is `menuAnchor(MenuAnchorType.PrimaryNotEditable)`.
- **Fix:** Added explicit imports (`Switch`, `ExposedDropdownMenuBox`, `ExposedDropdownMenuDefaults`, `OutlinedTextField`, `DropdownMenuItem`, `MenuAnchorType`), removed the `androidx.compose.material3.` prefix in the new code, and switched to `menuAnchor(MenuAnchorType.PrimaryNotEditable)` — matches the existing pattern in `CreateExerciseScreen.kt` / `AiSettingsScreen.kt`.
- **Files modified:** `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/SettingsSheet.kt`
- **Commit:** eef64c2 (single Task 3 commit — surgical fix folded into the same task)

### Other observations

- Plan suggested `xcodebuild` with `destination 'platform=iOS Simulator,name=iPhone 16'` — `iPhone 16` is not available on this machine; switched to `iPhone 17` (the closest equivalent). No semantic change.
- Plan's `grep -c "Debug-Modus|Grace-Period (Demo)"` expected 2; actual returns 4 because the labels appear in both the Toggle/Picker title and the subtitle/labels. The gate ("returns >= 2") is satisfied.
- iOS observer pattern uses `.boolValue` / `.int64Value` (matching `WorkoutSessionView.observeElapsedSeconds` and `OverviewRankStrip.xpLabel`) — confirms the plan's instinct that primitives bridge as `KotlinBoolean`/`KotlinLong`.

## Manual UAT (deferred to user)

1. Open Settings in DEBUG build — confirm "Debug" section appears with toggle ON + picker showing "5 Min."
2. Change picker to "5 Sek." → start a workout → log a set → trigger geofence exit via debug pill → confirm grace countdown starts at 5
3. Turn toggle OFF → start workout → confirm debug pill (iOS) / FAB (Android) is hidden
4. Kill app, relaunch → confirm toggle + picker values persist
5. Release-build smoke: no Debug section, no toggle/picker, no in-workout pill

## Success Criteria Status

- [x] `SettingsRepository` exposes `debugModeEnabled` + `gracePeriodSeconds` Flows + setters
- [x] `SettingsViewModel` exposes both as `@NativeCoroutinesState` StateFlows + setters
- [x] `WorkoutSessionViewModel.startGracePeriod()` reads from `settingsRepository.gracePeriodSeconds.first()`
- [x] `XpFormula.GEOFENCE_GRACE_PERIOD_SECONDS` retained as the canonical default
- [x] iOS Settings shows DEBUG-gated Section("Debug") with German Toggle + Picker
- [x] Android SettingsSheet shows DEBUG-gated Debug section with Switch + ExposedDropdownMenu
- [x] In-workout pill (iOS) / FAB (Android) gated on toggle, still wrapped in `#if DEBUG`/`BuildConfig.DEBUG`
- [x] Defaults preserve existing behavior (toggle ON, 300s grace)
- [x] Both platforms build clean (Debug configuration)

## Self-Check: PASSED

- FOUND: shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt
- FOUND: shared/src/commonMain/kotlin/com/pumpernickel/presentation/settings/SettingsViewModel.kt
- FOUND: shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt
- FOUND: iosApp/iosApp/Views/Settings/SettingsView.swift
- FOUND: iosApp/iosApp/Views/Workout/WorkoutSessionView.swift
- FOUND: androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/SettingsSheet.kt
- FOUND: androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt
- FOUND commit: f3bb4a9
- FOUND commit: 9ffe9ad
- FOUND commit: eef64c2
