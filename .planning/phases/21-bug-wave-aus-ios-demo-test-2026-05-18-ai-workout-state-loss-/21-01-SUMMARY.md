---
phase: 21-bug-wave-aus-ios-demo-test-2026-05-18-ai-workout-state-loss-
plan: 01
subsystem: ui
tags: [geofence, notifications, kmp, swiftui, compose, kotlin-shared, datastore]

# Dependency graph
requires:
  - phase: 19-geofencing-workout-enforcement
    provides: GeofenceNotifications (Android) + GeofenceNotification enum (iOS), 5-trigger notification surface
  - phase: 20-clean-architecture-refactor-dependency-rule-fixen-repository
    provides: infrastructure/ layer convention in commonMain (D-20-02), SettingsRepository.gracePeriodSeconds: Flow<Long>
  - phase: quick-260517-vn7
    provides: user-configurable gracePeriodSeconds in SettingsRepository + SettingsViewModel.gracePeriodSeconds: StateFlow<Long>
provides:
  - infrastructure/geofence/formatGraceDuration(seconds: Int): String pure helper in commonMain
  - Android exit-notification body now formatted from user-configured Settings.gracePeriodSeconds
  - iOS exit-notification body now formatted via Shared.GraceDurationFormatKt + observed gracePeriodSecondsFlow
affects: [21-bug-wave, future geofence-notification work, B5]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Cross-platform formatter in commonMain consumed by both Android (import) and iOS (Shared.<file>Kt) — D-21-08"
    - "Android-side: SettingsViewModel observed at Composable root + value threaded into LaunchedEffect notification call"
    - "iOS-side: SettingsViewModel.gracePeriodSecondsFlow observed inside withTaskGroup alongside other view-lifecycle flows"

key-files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/geofence/GraceDurationFormat.kt
    - shared/src/commonTest/kotlin/com/pumpernickel/infrastructure/geofence/GraceDurationFormatTest.kt
  modified:
    - androidApp/src/main/res/values/strings.xml
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/notifications/GeofenceNotifications.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt
    - iosApp/iosApp/Utilities/NotificationCenter+Geofence.swift
    - iosApp/iosApp/Views/Workout/WorkoutSessionView.swift

key-decisions:
  - "Helper lives in commonMain/infrastructure/geofence/ per D-21-06 + D-21-08 (layer convention from Phase 20); pure function, no platform dependency"
  - "Plural form 'X Minuten' kept even for 60s edge case — D-21-06 explicit low-prio on singular handling"
  - "iOS settingsViewModel hoisted out of '#if DEBUG' block so non-DEBUG builds can subscribe to gracePeriodSecondsFlow; debugModeEnabled stays DEBUG-only"
  - "Android: SettingsViewModel observed at WorkoutSessionScreen root via koinViewModel() — value threaded into the LaunchedEffect that calls postExitDetected on grace-entry transition"

patterns-established:
  - "Pure cross-platform formatter pattern: package com.pumpernickel.infrastructure.<feature>, kotlin.test in commonTest, Swift consumes via Shared.<File>Kt"
  - "Notification-body parameterization: enum/function gains explicit param rather than re-reading platform Settings inline (keeps call pure + testable)"

requirements-completed: []

# Metrics
duration: ~15min
completed: 2026-05-19
---

# Phase 21 Plan 01: B5 — Dynamic Grace-Period in Geofence-Exit Notification Summary

**Cross-platform geofence-exit notification body now reflects the user-configured grace-period from Settings via a new commonMain `formatGraceDuration(seconds: Int): String` helper, replacing the hardcoded "5 Minuten" string on both Android and iOS.**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-05-19T00:21:00Z (approx — first task commit at 00:21)
- **Completed:** 2026-05-19T00:30:00Z (approx — final commit + verification)
- **Tasks:** 3 (one TDD pair counted as Task 1)
- **Files modified:** 5 (+ 2 created)

## Accomplishments

- Created `GraceDurationFormat.kt` pure helper in commonMain with seconds/minutes/hour boundary handling and defensive `<= 0` clamp
- 9-test commonTest suite covering every behavioral case from the plan spec; all green on `:shared:iosSimulatorArm64Test`
- Android `GeofenceNotifications.postExitDetected(context, graceSeconds: Int)` signature update + strings.xml `%1$s` template; `WorkoutSessionScreen` observes `SettingsViewModel.gracePeriodSeconds` at composable root and threads the value through to the grace-entry notification call site
- iOS `GeofenceNotification.exitDetected` enum case gains a `graceSeconds: Int` associated value; `WorkoutSessionView` observes `settingsViewModel.gracePeriodSecondsFlow` via a new `observeGracePeriodSeconds()` task inside the existing `withTaskGroup`, hoisting `settingsViewModel` out of the `#if DEBUG` block so the subscription works in Release builds too
- Cross-platform builds: `:shared:iosSimulatorArm64Test` PASS, `:androidApp:compileDebugKotlin` PASS, `xcodebuild` iOS Debug PASS

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: failing test for formatGraceDuration** — `3611112` (test)
2. **Task 1 GREEN: implement formatGraceDuration helper in commonMain** — `d87f4d7` (feat)
3. **Task 2: wire dynamic grace-period into Android exit notification** — `0470f6c` (feat)
4. **Task 3: wire dynamic grace-period into iOS exit notification** — `14e15a2` (feat)

_Task 1 followed TDD (RED → GREEN, no REFACTOR — pure function shape was already minimal). Tasks 2 + 3 are caller wiring + acceptance-driven, no separate test files._

## Files Created/Modified

- `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/geofence/GraceDurationFormat.kt` — top-level `formatGraceDuration(seconds: Int): String` pure helper covering `<=0`, `< 60`, `< 3600`, `== 3600`, `> 3600` ranges
- `shared/src/commonTest/kotlin/com/pumpernickel/infrastructure/geofence/GraceDurationFormatTest.kt` — 9 `@Test`s for sec/min/hour boundaries + defensive 0/-5 cases
- `androidApp/src/main/res/values/strings.xml` — `geofence_notification_exit_body` switched from hardcoded "5 Minuten …" to `%1$s …` template
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/notifications/GeofenceNotifications.kt` — `postExitDetected(context, graceSeconds: Int)` formats body via `formatGraceDuration`; import added
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt` — observes `SettingsViewModel.gracePeriodSeconds` at screen root, passes `.toInt()` to `postExitDetected` on grace-entry transition
- `iosApp/iosApp/Utilities/NotificationCenter+Geofence.swift` — `case exitDetected(graceSeconds: Int)`; body switch uses `Shared.GraceDurationFormatKt.formatGraceDuration(seconds: Int32(graceSeconds))`; `import Shared` added
- `iosApp/iosApp/Views/Workout/WorkoutSessionView.swift` — `settingsViewModel` hoisted out of `#if DEBUG`; new `@State gracePeriodSeconds: Int64 = 300` + `observeGracePeriodSeconds()` task registered in `withTaskGroup`; `handleGeofenceStateChange` passes `Int(gracePeriodSeconds)` to `.exitDetected(graceSeconds:)`

## Decisions Made

- **Helper layer**: `infrastructure/geofence/` (not `domain/`) — Phase-20 dependency rule guard (D-20-01) keeps domain free of platform-facing concerns; format-helper is presentation-adjacent infrastructure
- **Plural in 60s case**: spec explicitly accepts "1 Minuten" — implementing singular would require either i18n plurals or an extra branch with no functional payoff for a German-only prototype
- **iOS observer location**: kept inside the existing `withTaskGroup` on `.task {}` next to `observeGeofenceState` rather than introducing a sheet/onChange pattern — symmetric with `observeElapsedSeconds`, `observePreviousPerformance`, etc.
- **iOS @State default 300**: mirrors the Settings/XpFormula default (5 min) so the first-emission window before the flow lands does not show a misleading "0 Sekunden"

## Deviations from Plan

None — plan executed as written. Three minor implementation choices noted under Decisions Made above (D-21-06 + D-21-08 already constrained the shape).

The plan said the task structure was 3 tasks; Task 1 (the TDD pair) is captured as two commits (`test:` + `feat:`) per the TDD gate convention, but counted as one task per plan numbering.

## Issues Encountered

- The `iOS settingsViewModel` constant was previously declared inside a `#if DEBUG` block (quick-260517-pzh). Subscribing to `gracePeriodSecondsFlow` from outside the DEBUG block required hoisting the declaration. Solved by moving `settingsViewModel = KoinHelper.shared.getSettingsViewModel()` above the DEBUG fence; `debugModeEnabled` `@State` and its observer remain DEBUG-gated.
- The `:shared:jvmTest` task does not exist in this project's KMP layout; switched to `:shared:iosSimulatorArm64Test` per the available test tasks.

## User Setup Required

None — pure code change, no external service / no DataStore migration / no Info.plist key.

## Next Phase Readiness

- B5 is closed; remaining Wave-A plan (21-02 / B4 — Daily-Log Submit-Clear) is independent and unblocked.
- Format helper available for reuse if other notification bodies (grace-expired, early-exit-budget) want dynamic durations in future phases.
- Manual UAT pending per plan `<verification>` step 4: "set grace=10 in Settings → trigger geofence-exit → notification reads '10 Sekunden um zurückzukommen, …'". Non-blocking for plan-close per D-21-09.

## Self-Check: PASSED

- `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/geofence/GraceDurationFormat.kt`: FOUND
- `shared/src/commonTest/kotlin/com/pumpernickel/infrastructure/geofence/GraceDurationFormatTest.kt`: FOUND
- `androidApp/src/main/res/values/strings.xml`: FOUND (modified, `%1$s` present, `5 Minuten` count = 0)
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/notifications/GeofenceNotifications.kt`: FOUND (`formatGraceDuration` x2, `graceSeconds` x2)
- `iosApp/iosApp/Utilities/NotificationCenter+Geofence.swift`: FOUND (`exitDetected(graceSeconds:)`, `GraceDurationFormatKt`, `5 Minuten` count = 0)
- Commit `3611112` (test RED): FOUND
- Commit `d87f4d7` (feat GREEN helper): FOUND
- Commit `0470f6c` (feat Android wiring): FOUND
- Commit `14e15a2` (feat iOS wiring): FOUND
- `:shared:iosSimulatorArm64Test --tests "...GraceDurationFormatTest"`: PASS
- `:androidApp:compileDebugKotlin`: PASS
- `xcodebuild iosApp Debug iphonesimulator`: BUILD SUCCEEDED

---
*Phase: 21-bug-wave-aus-ios-demo-test-2026-05-18-ai-workout-state-loss-*
*Completed: 2026-05-19*
