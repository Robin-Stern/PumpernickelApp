---
phase: 08-mid-workout-exercise-reorder
plan: 01
subsystem: database, workout-logic
tags: [room, migration, viewmodel, reorder, skip, crash-recovery, kmp]

# Dependency graph
requires:
  - phase: 07-post-workout-recap
    provides: Reviewing state and saveReviewedWorkout() flow
provides:
  - Room schema v4 with exerciseOrder column for crash recovery
  - reorderExercise() ViewModel method for pending exercise reorder
  - skipExercise() ViewModel method for cursor advance
  - templateOriginalIndices tracking for correct Room set persistence
  - exerciseOrder persistence and restoration in resumeWorkout()
affects: [08-02 SwiftUI reorder UI]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Template-original index mapping via parallel templateOriginalIndices list"
    - "CSV string persistence for exercise order crash recovery"
    - "SwiftUI .onMove semantics with removeAt/add offset compensation"

key-files:
  created: []
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/data/db/ActiveSessionEntity.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/db/WorkoutSessionDao.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/WorkoutRepository.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt

key-decisions:
  - "Template-original index tracking via parallel MutableList<Int> instead of indirection array"
  - "exerciseOrder stored as CSV string in active_sessions for minimal schema impact"
  - "completeSet() uses templateOriginalIndices[exIdx] for Room persistence, cursor stays as display index"
  - "resumeWorkout() falls back to template order when exerciseOrder is empty (pre-migration compat)"

patterns-established:
  - "Parallel index list pattern: templateOriginalIndices shadows exercises list for original-to-display mapping"
  - "CSV exercise order persistence: exerciseOrder column with @ColumnInfo(defaultValue = '') for AutoMigration"

requirements-completed: [FLOW-03, FLOW-04, FLOW-07]

# Metrics
duration: 2min
completed: 2026-03-29
---

# Phase 8 Plan 1: Mid-Workout Exercise Reorder (KMP Layer) Summary

**Room schema v4 migration with exerciseOrder persistence, ViewModel reorderExercise/skipExercise methods, and crash-recovery-safe exercise order restoration**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-29T18:36:04Z
- **Completed:** 2026-03-29T18:38:49Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- Room schema migration 3-to-4 with exerciseOrder column using AutoMigration and @ColumnInfo(defaultValue = "")
- ViewModel reorderExercise() with pending-only constraint, SwiftUI .onMove offset semantics, and parallel templateOriginalIndices tracking
- ViewModel skipExercise() with last-exercise no-op guard and cursor advance to first incomplete set
- Crash recovery: exerciseOrder persisted to Room on every reorder, restored in resumeWorkout() with pre-migration fallback

## Task Commits

Each task was committed atomically:

1. **Task 1: Room migration 3-to-4 and repository layer for exerciseOrder persistence** - `75cb96e` (feat)
2. **Task 2: ViewModel reorderExercise, skipExercise, templateOriginalIndices, and crash recovery** - `89d397c` (feat)

## Files Created/Modified
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/ActiveSessionEntity.kt` - Added exerciseOrder column with @ColumnInfo(defaultValue = "")
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/WorkoutSessionDao.kt` - Added updateExerciseOrder DAO query
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt` - Bumped to version 4 with AutoMigration(from = 3, to = 4)
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/WorkoutRepository.kt` - Added updateExerciseOrder interface/impl, exerciseOrder in ActiveSessionData
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt` - Added reorderExercise(), skipExercise(), persistExerciseOrder(), templateOriginalIndices tracking, crash recovery in resumeWorkout()

## Decisions Made
- Template-original index tracking via parallel MutableList<Int> instead of firmware indirection array (simpler, matches D-01)
- completeSet() sends templateOriginalIndices[exIdx] to Room while cursor stays as display index (correct set persistence after reorder)
- resumeWorkout() falls back to template order when exerciseOrder is empty, supporting pre-migration sessions (D-08)
- reorderExercise() uses absTo-1 compensation for removeAt/add semantics matching SwiftUI .onMove toOffset behavior

## Deviations from Plan
None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Known Stubs
None - all methods are fully implemented with correct data flow.

## Next Phase Readiness
- KMP layer complete: reorderExercise(), skipExercise(), and crash recovery methods are callable from Swift
- Ready for Plan 02 (SwiftUI reorder UI) to wire the exercise overview sheet with .onMove and skip button
- templateOriginalIndices initialized on start, restored on resume, cleared on discard/reset

## Self-Check: PASSED

All 5 files verified present. Both commits (75cb96e, 89d397c) verified in git log.

---
*Phase: 08-mid-workout-exercise-reorder*
*Completed: 2026-03-29*
