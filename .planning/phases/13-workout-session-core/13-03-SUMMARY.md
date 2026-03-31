---
phase: 13-workout-session-core
plan: 03
subsystem: android-workout-session
tags: [android, compose, workout-session, exercise-overview, bottom-sheet, reorder, skip]
dependency_graph:
  requires: [13-02]
  provides: [ExerciseOverviewSheetContent, exercise-overview-wiring]
  affects: [androidApp-workout-session, exercise-reorder-flow, skip-exercise-flow]
tech_stack:
  added: []
  patterns:
    - "LazyColumn-based bottom sheet content (not wrapped in ModalBottomSheet — wired in caller)"
    - "Pending-relative onMove semantics for exercise reorder matching SwiftUI .onMove toOffset"
    - "Move-up/down IconButtons for reorder (Phase 12 TemplateEditorScreen pattern reused)"
key_files:
  created:
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ExerciseOverviewSheet.kt
  modified:
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt
decisions:
  - "ExerciseOverviewSheetContent is pure content composable without ModalBottomSheet wrapper — caller owns the sheet lifecycle, improving testability"
  - "Added onJumpToExercise and onReorderExercise callbacks to ActiveWorkoutContent to thread viewModel calls from WorkoutSessionScreen through the composable hierarchy"
metrics:
  duration_seconds: 105
  completed_date: "2026-03-31"
  tasks_completed: 2
  files_changed: 2
---

# Phase 13 Plan 03: Exercise Overview Bottom Sheet Summary

**One-liner:** Material 3 exercise overview bottom sheet with three sections (Completed/Current/Up Next), move-up/down reorder, skip, and jump-to functionality wired into WorkoutSessionScreen.

## What Was Built

Created `ExerciseOverviewSheetContent` — a standalone LazyColumn composable displaying workout exercise progress in three sections:

- **Completed** — exercises before currentExerciseIndex with at least one completed set; green checkmark icon, greyed text, tappable to jump back
- **Current** — highlighted exercise at currentExerciseIndex with play icon, bold name, and an orange "Skip" TextButton (shown only when not the last exercise)
- **Up Next** — pending exercises after currentExerciseIndex with move-up/move-down IconButtons for reorder and tap-to-jump

The composable calls back via `onSelect(Int)`, `onMove(from, to)`, `onSkip()`, and `onDismiss()`.

In `WorkoutSessionScreen`, the placeholder ModalBottomSheet content was replaced with `ExerciseOverviewSheetContent` wired to `viewModel.jumpToExercise`, `viewModel.reorderExercise`, and `viewModel.skipExercise`.

## Commits

| Task | Commit | Files |
|------|--------|-------|
| Task 1: ExerciseOverviewSheetContent composable | d918b41 | ExerciseOverviewSheet.kt (created, 229 lines) |
| Task 2: Wire into WorkoutSessionScreen | d8473ac | WorkoutSessionScreen.kt (modified, +14/-0 effective) |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing callbacks] Added onJumpToExercise and onReorderExercise to ActiveWorkoutContent**
- **Found during:** Task 2
- **Issue:** The plan's code snippet wired callbacks directly, but `ActiveWorkoutContent` is a private composable that doesn't hold the viewModel — callbacks must be threaded from `WorkoutSessionScreen` through `ActiveWorkoutContent`'s parameter list
- **Fix:** Added `onJumpToExercise: (Int) -> Unit` and `onReorderExercise: (Int, Int) -> Unit` to `ActiveWorkoutContent` signature; wired from `WorkoutSessionScreen` call-site to `viewModel.jumpToExercise` and `viewModel.reorderExercise`
- **Files modified:** WorkoutSessionScreen.kt
- **Commit:** d8473ac

## Known Stubs

None — all callbacks are wired to real ViewModel methods.

## Self-Check: PASSED

- FOUND: ExerciseOverviewSheet.kt
- FOUND: WorkoutSessionScreen.kt
- FOUND commit: d918b41 (Task 1)
- FOUND commit: d8473ac (Task 2)
