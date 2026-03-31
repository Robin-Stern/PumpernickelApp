---
phase: 13-workout-session-core
plan: "04"
subsystem: ui
tags: [compose, material3, workout-session, recap, review, finished]

# Dependency graph
requires:
  - phase: 13-workout-session-core
    provides: "Plan 01 DrumPicker (RepsPicker/WeightPicker), Plan 02 ActiveWorkoutContent, Plan 03 ExerciseOverviewSheet"
provides:
  - "Reviewing state: full post-workout recap with summary header, per-exercise set breakdown, tap-to-edit via ModalBottomSheet"
  - "Finished state: completion screen with checkmark, summary card, Done button"
  - "EditSetSheetContent composable with drum pickers pre-filled to selected set values"
  - "SummaryRow and formatDuration helpers shared by recap and finished screens"
  - "Complete 4-state WorkoutSessionScreen: Idle -> Active -> Reviewing -> Finished -> Idle"
affects: [14-history-settings-anatomy]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Edit sheet state variables (showEditSheet, editExerciseIndex, editSetIndex, editSelectedReps, editSelectedWeightKgX10) hoisted to WorkoutSessionScreen level so both Active and Reviewing branches can open the same sheet"
    - "Recap exercise list uses forEachIndexed with originalIndex passed to onEditSet so ViewModel receives the correct index regardless of display order"

key-files:
  created: []
  modified:
    - "androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt"

key-decisions:
  - "Edit sheet state hoisted to WorkoutSessionScreen level: both Active completed sets and Reviewing recap rows need the same edit sheet, so hoisting avoids duplication and keeps EditSetSheetContent pure"
  - "CompletedSetsSection updated to accept exerciseIndex + onEditSet callback (default no-op): backward-compatible signature change enables tap-to-edit without breaking the composable's existing callers"
  - "RecapContent and FinishedContent have no @OptIn(ExperimentalMaterial3Api::class) annotation: ModalBottomSheet is called from the WorkoutSessionScreen-level when block which already carries the annotation"

patterns-established:
  - "formatDuration: millis -> 'Xm YYs' (under 1h) or 'Xh YYm' (1h+) — shared between RecapContent summary header and FinishedContent SummaryRow"
  - "SummaryRow: reusable label/value row with SpaceBetween arrangement in Surface card"

requirements-completed: [ANDROID-06, ANDROID-07]

# Metrics
duration: 3min
completed: "2026-03-31"
---

# Phase 13 Plan 04: Reviewing and Finished States Summary

**Post-workout recap screen with tap-to-edit sets via drum picker sheet, and finished summary card with Done button — completing the full 4-state workout lifecycle on Android**

## Performance

- **Duration:** ~3 min
- **Started:** 2026-03-31T17:05:54Z
- **Completed:** 2026-03-31T17:08:58Z
- **Tasks:** 2
- **Files modified:** 1

## Accomplishments

- Replaced Reviewing placeholder with RecapContent: summary header showing exercise count, set count, and formatted duration; scrollable exercise sections with per-set rows
- All completed set rows in both Reviewing recap and Active completed sets section are now tappable — opens EditSetSheetContent with RepsPicker and WeightPicker pre-filled to the set's current values
- Replaced Finished placeholder with FinishedContent: CheckCircle icon, "Workout Complete!" heading, summary card (workout name, duration, exercises, sets), and Done button that calls resetToIdle() + popBackStack()
- Added formatDuration helper and SummaryRow composable shared by both screens

## Task Commits

Each task was committed atomically:

1. **Task 1 + Task 2: Reviewing state recap with edit sheet + Finished state summary card** - `7829ac3` (feat)

**Plan metadata:** (final docs commit below)

## Files Created/Modified

- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt` - Complete 4-state screen: Reviewing + Finished states implemented, edit sheet state hoisted, CompletedSetsSection wired for tap-to-edit

## Decisions Made

- Edit sheet state variables hoisted to WorkoutSessionScreen level so both Active and Reviewing branches can open the shared EditSetSheetContent without duplication
- CompletedSetsSection signature extended with `exerciseIndex` and `onEditSet` callback (default no-op) for backward-compatible tap-to-edit wiring
- RecapContent builds exercise list with `forEachIndexed` preserving `originalIndex` passed to `editCompletedSet()` — ensures correct ViewModel index mapping

## Deviations from Plan

None — plan executed exactly as written. Both tasks were implemented in a single pass since they modify the same file and share helper functions (formatDuration, EditSetSheetContent).

## Issues Encountered

None. Build succeeded on first attempt with only a pre-existing `vibrate()` deprecation warning unrelated to this plan's changes.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- WorkoutSessionScreen now implements all 4 states (Idle, Active, Reviewing, Finished) — Android workout session feature is complete
- Phase 13 is complete; Phase 14 (History, Settings, Anatomy) can begin
- No blockers

---
*Phase: 13-workout-session-core*
*Completed: 2026-03-31*
