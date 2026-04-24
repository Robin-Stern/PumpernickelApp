---
phase: 07-post-workout-recap-edit
plan: 01
subsystem: workout
tags: [sealed-class, state-machine, swiftui, recap, review-flow]

# Dependency graph
requires:
  - phase: 03-workout-session
    provides: WorkoutSessionState sealed class (Idle/Active/Finished), finishWorkout(), editCompletedSet()
  - phase: 05-scroll-wheel-pickers
    provides: Wheel picker edit sheet, WorkoutSetRow component with onTap
provides:
  - Reviewing sealed class state for post-workout recap
  - enterReview() method replacing finishWorkout()
  - saveReviewedWorkout() method for persisting from recap
  - recapView() SwiftUI screen with exercise list, tappable set rows, save button
affects: [workout-session, workout-history]

# Tech tracking
tech-stack:
  added: []
  patterns: [state-split-pattern, dual-state-method-handling]

key-files:
  created: []
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt
    - iosApp/iosApp/Views/Workout/WorkoutSessionView.swift

key-decisions:
  - "Split finishWorkout() into enterReview() + saveReviewedWorkout() for two-step save flow"
  - "Store full exercise list in Reviewing state, filter to completed in UI only (avoids index mismatch Pitfall 5)"
  - "Reuse existing editSetSheet and editCompletedSet() for recap editing (no new UI components)"
  - "navigationBarBackButtonHidden(true) on recap to prevent accidental back navigation"

patterns-established:
  - "State split pattern: refactor single method into transition + persist when adding intermediate state"
  - "Dual-state method: extend existing methods with when expression to handle multiple sealed class variants"

requirements-completed: [FLOW-01, FLOW-02]

# Metrics
duration: 3min
completed: 2026-03-29
---

# Phase 7 Plan 1: Post-Workout Recap & Edit Summary

**Reviewing sealed class state with recap screen showing all exercises/sets before save, tap-to-edit via existing wheel pickers**

## Performance

- **Duration:** 3 min
- **Started:** 2026-03-29T15:50:15Z
- **Completed:** 2026-03-29T15:52:46Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Added `Reviewing` data class to `WorkoutSessionState` sealed class, carrying full exercise list, template info, and duration
- Replaced `finishWorkout()` with `enterReview()` that transitions Active to Reviewing without saving (crash recovery intact)
- Created `saveReviewedWorkout()` that builds CompletedWorkout from Reviewing state, persists to Room, and transitions to Finished
- Extended `editCompletedSet()` to handle both Active and Reviewing states via `when` expression
- Built recap screen UI with summary header (exercise count, set count, duration), scrollable exercise sections with tappable set rows, and prominent Save Workout button
- All set editing from recap reuses existing wheel picker edit sheet and editCompletedSet() method

## Files Modified

- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt` -- Added Reviewing state, enterReview(), saveReviewedWorkout(), extended editCompletedSet()
- `iosApp/iosApp/Views/Workout/WorkoutSessionView.swift` -- Added recapView(), Reviewing branch in body, formatDuration(), changed Finish Workout to call enterReview()

## Decisions Made

- Split `finishWorkout()` into `enterReview()` + `saveReviewedWorkout()` following the state-split pattern (D-01, D-02)
- Full exercise list stored in Reviewing state; filtering to exercises with completed sets happens in SwiftUI view layer only (avoids Pitfall 5 index mismatch)
- Reused existing `editSetSheet` and `editCompletedSet()` for recap editing -- no new components needed (D-06)
- Used `.navigationBarBackButtonHidden(true)` on recap screen to prevent accidental back navigation (per plan recommendation)

## Deviations from Plan

None -- plan executed exactly as written.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | 01642ab | Add Reviewing state, enterReview(), saveReviewedWorkout(), extend editCompletedSet() |
| 2 | ffe6ce3 | Add recap screen UI with exercise list, tappable set rows, and save button |

## Verification Results

- Kotlin compilation: BUILD SUCCESSFUL (shared:compileKotlinIosSimulatorArm64)
- No references to old `finishWorkout()` in either Kotlin or Swift
- `enterReview` defined in Kotlin, called from Swift
- `saveReviewedWorkout` defined in Kotlin, called from Swift
- State branch ordering correct: Active -> Reviewing -> Finished -> Idle

## Self-Check: PASSED

All files exist. All commits verified.

---
*Phase: 07-post-workout-recap-edit*
*Completed: 2026-03-29*
