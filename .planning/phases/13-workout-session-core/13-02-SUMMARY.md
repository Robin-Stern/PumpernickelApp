---
phase: 13-workout-session-core
plan: 02
subsystem: android-workout-session
tags: [android, compose, workout-session, drum-picker, rest-timer, haptic]
dependency_graph:
  requires: [13-01]
  provides: [WorkoutSessionScreen, NavHost-workout-session-wiring]
  affects: [androidApp-navigation, workout-execution-flow]
tech_stack:
  added: []
  patterns:
    - "Scaffold + TopAppBar with DropdownMenu for workout actions"
    - "when(sealed class) branching for WorkoutSessionState"
    - "LaunchedEffect(preFill) for syncing picker pre-fill state"
    - "LaunchedEffect(setIdx, exIdx) to reset showSetInput on exercise/set change"
    - "Vibrator/VibrationEffect with Build.VERSION.SDK_INT guard for haptic feedback"
    - "CircularProgressIndicator with progress lambda for rest timer"
key_files:
  created:
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt
  modified:
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt
decisions:
  - "ActiveWorkoutContent extracted as private composable taking all state as parameters — avoids ViewModel re-injection inside sub-composables and keeps each helper function pure"
  - "LaunchedEffect(Unit) calls viewModel.startWorkout(templateId) — matches iOS .task pattern"
  - "Deprecation warning on vibrate(Long) suppressed with @Suppress — handled with SDK version guard per plan spec"
metrics:
  duration_minutes: 3
  completed_date: "2026-03-31"
  tasks_completed: 2
  files_changed: 2
requirements_completed: [ANDROID-05, ANDROID-06]
---

# Phase 13 Plan 02: WorkoutSessionScreen — Active State UI Summary

**One-liner:** Compose workout session screen with drum picker set entry, rest timer countdown, minimal set screen, toolbar actions, abandon dialog, and PB/prev-performance header wired into NavHost.

## What Was Built

### Task 1: WorkoutSessionScreen.kt (695 lines)

Full workout execution screen implementing the Active state of `WorkoutSessionState`. Key sub-composables:

- **HeaderSection**: exercise name, set counter (N of M), elapsed timer (monospace), previous performance in orange, personal best in blue
- **MinimalSetScreen**: fullscreen tap target showing SET + set number + exercise name, tap reveals drum pickers
- **SetInputSection**: `RepsPicker` + `WeightPicker` side by side, "Complete Set" button with haptic feedback (Vibrator/VibrationEffect, SDK-guarded)
- **RestTimerSection**: remaining seconds in displayLarge monospace, `CircularProgressIndicator` with progress ratio, red color when <= 3s, "Skip Rest" TextButton
- **RestCompleteSection**: "Rest Complete!" in primary color + "Continue" button
- **CompletedSetsSection**: checkmark rows per completed set with reps/weight, clickable placeholder for Plan 04 edit
- **Toolbar**: Close (abandon guard) + DropdownMenu (Skip Exercise, Exercise Overview, Finish Workout)
- **AlertDialog**: abandon dialog with Save & Exit / Discard / Cancel, shows set count context
- **ModalBottomSheet**: exercise overview placeholder (Plan 03 implements)
- Reviewing/Finished/Idle placeholder branches for Plan 04

All 6 StateFlows collected: `sessionState`, `elapsedSeconds`, `preFill`, `previousPerformance`, `personalBest`, `weightUnit`.

`LaunchedEffect(preFill)` syncs picker values via `snapToWeightStep()`. `LaunchedEffect(setIdx, exIdx)` resets `showSetInput = false` on exercise/set advancement.

### Task 2: MainScreen.kt

Replaced `PlaceholderScreen` in `composable<WorkoutSessionRoute>` with `WorkoutSessionScreen(templateId, navController)`. Added import. Tapping "Start Workout" (play icon) on any template now navigates to the live workout session screen.

## Deviations from Plan

None — plan executed exactly as written. The deprecation warning on `vibrate(Long)` is expected behavior from the SDK-version guard pattern specified in the plan and does not affect correctness.

## Known Stubs

1. **Reviewing state placeholder** — `WorkoutSessionScreen.kt:~line 94` — "Reviewing... (coming soon)" text. Plan 04 implements the recap view.
2. **Finished state placeholder** — `WorkoutSessionScreen.kt:~line 100` — "Finished! (coming soon)" text. Plan 04 implements the finished screen.
3. **Exercise Overview placeholder** — `WorkoutSessionScreen.kt:~line 246` — "Exercise Overview (coming in Plan 03)" text. Plan 03 implements `ExerciseOverviewSheet`.
4. **Completed set tap-to-edit** — `WorkoutSessionScreen.kt:CompletedSetsSection` — `clickable { /* tap-to-edit wired in Plan 04 */ }`. Plan 04 wires in the edit sheet.

These stubs are intentional — each is documented with the plan that will resolve it. The plan's primary goal (Active state workout execution) is fully functional.

## Self-Check: PASSED

- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt` — FOUND
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt` — FOUND (modified)
- Commit `8c23f94` (Task 1: WorkoutSessionScreen) — verified
- Commit `41eb0ab` (Task 2: NavHost wiring) — verified
- `./gradlew :androidApp:compileDebugKotlin` — BUILD SUCCESSFUL
