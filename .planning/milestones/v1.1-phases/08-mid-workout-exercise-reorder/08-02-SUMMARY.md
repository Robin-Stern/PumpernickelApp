---
phase: 08-mid-workout-exercise-reorder
plan: 02
subsystem: ios-ui
tags: [swiftui, drag-reorder, onMove, exercise-overview, skip-exercise]

# Dependency graph
requires:
  - phase: 08-mid-workout-exercise-reorder
    plan: 01
    provides: reorderExercise() and skipExercise() ViewModel methods
provides:
  - Sectioned ExerciseOverviewSheet with Completed/Current/Up Next sections
  - Drag-reorder on pending exercises via .onMove
  - Skip exercise button in toolbar and exercise overview sheet
affects: [09-context-menu-abandon-guards]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Sectioned List with permanent editMode for selective .onMove on one section only"
    - "Skip button conditionally visible based on exercise index vs count guard"

key-files:
  created: []
  modified:
    - iosApp/iosApp/Views/Workout/ExerciseOverviewSheet.swift
    - iosApp/iosApp/Views/Workout/WorkoutSessionView.swift

key-decisions:
  - "Permanent .editMode(.constant(.active)) on entire List for drag handle visibility, .onMove only on Up Next section"
  - "Skip button placed in both exercise overview sheet (inline with current exercise) and toolbar (forward.fill icon)"

patterns-established:
  - "Sectioned exercise list pattern: Completed (green checkmark), Current (blue play icon), Up Next (empty circle + drag handles)"
  - "Dual skip access: toolbar icon for quick skip + sheet button for overview-based skip"

requirements-completed: [FLOW-03, FLOW-07]

# Metrics
duration: 2min
completed: 2026-03-29
---

# Phase 8 Plan 2: Mid-Workout Exercise Reorder (SwiftUI UI) Summary

**Sectioned exercise overview sheet with drag-reorder on pending exercises and dual skip-exercise buttons (toolbar + sheet)**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-29T18:41:13Z
- **Completed:** 2026-03-29T18:44:01Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- ExerciseOverviewSheet rebuilt with three sections: Completed (green checkmark, non-draggable), Current (blue play icon, skip button), Up Next (drag handles via .onMove)
- Skip exercise accessible from two locations: forward.fill toolbar button and inline Skip button in exercise overview sheet
- Both skip and reorder wired to ViewModel methods (skipExercise(), reorderExercise()) with proper Int32 type conversion

## Task Commits

Each task was committed atomically:

1. **Task 1: Rebuild ExerciseOverviewSheet with completed/current/pending sections and drag reorder** - `abeb359` (feat)
2. **Task 2: Wire reorder and skip callbacks in WorkoutSessionView** - `343102f` (feat)

## Files Created/Modified
- `iosApp/iosApp/Views/Workout/ExerciseOverviewSheet.swift` - Sectioned List with Completed/Current/Up Next sections, .onMove on pending, skip button, onMove/onSkip callbacks
- `iosApp/iosApp/Views/Workout/WorkoutSessionView.swift` - Sheet passes onMove/onSkip closures to ExerciseOverviewSheet, toolbar skip button with forward.fill icon

## Decisions Made
- Permanent editMode on the List to always show drag handles (matching TemplateEditorView pattern from D-03)
- Skip button in both toolbar (leading, forward.fill icon) and exercise overview sheet (inline with current exercise) for dual access per D-06
- Skip buttons conditionally hidden when on last exercise to prevent no-op

## Deviations from Plan
None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Known Stubs
None - all callbacks are fully wired to ViewModel methods.

## Next Phase Readiness
- Phase 8 complete: both KMP layer (Plan 01) and SwiftUI UI (Plan 02) for mid-workout exercise reorder
- Users can drag-reorder pending exercises and skip exercises during active workouts
- Ready for Phase 9 (context menu and abandon guards) which will add the full context menu wrapping skip alongside other actions

## Self-Check: PASSED
