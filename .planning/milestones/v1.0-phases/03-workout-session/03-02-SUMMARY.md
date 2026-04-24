---
phase: 03-workout-session
plan: 02
subsystem: presentation
tags: [viewmodel, stateflow, coroutines, koin, sealed-class, state-machine, kmp]

# Dependency graph
requires:
  - phase: 03-01
    provides: "Room entities, DAOs, WorkoutRepository, domain models (SessionExercise, SessionSet, CompletedWorkout)"
provides:
  - "WorkoutSessionViewModel -- sealed class state machine driving workout execution"
  - "hasActiveSession StateFlow for SwiftUI resume prompt trigger"
  - "KoinHelper.getWorkoutSessionViewModel() for iOS access"
affects: [03-03, ios-workout-ui]

# Tech tracking
tech-stack:
  added: []
  patterns: ["Sealed class state machine (Idle -> Active -> Finished) for single-flow UI observation", "Wall-clock anchored coroutine timer to avoid drift", "Per-set Room persistence for crash recovery"]

key-files:
  created:
    - "shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt"
  modified:
    - "shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt"
    - "shared/src/iosMain/kotlin/com/pumpernickel/di/KoinHelper.kt"

key-decisions:
  - "Single StateFlow<WorkoutSessionState> sealed class for all UI state (Idle, Active, Finished) instead of multiple separate flows"
  - "Wall-clock anchoring for rest timer using Clock.System.now() to avoid cumulative delay drift"
  - "hasActiveSession as separate StateFlow<Boolean> for lightweight SwiftUI observation of crash recovery state"

patterns-established:
  - "Sealed class state machine: WorkoutSessionState with Idle/Active/Finished and nested RestState"
  - "Coroutine timer pattern: wall-clock anchored countdown with delay(1000L) and Clock.System.now() recalculation"
  - "Crash recovery pattern: persist to Room after every set, reconstruct from Room + template on resume"

requirements-completed: [WORK-01, WORK-02, WORK-03, WORK-04, WORK-05, WORK-06, WORK-08, WORK-09]

# Metrics
duration: 4min
completed: 2026-03-28
---

# Phase 03 Plan 02: Workout Session ViewModel Summary

**Sealed class state machine ViewModel with rest timer, elapsed ticker, per-set Room persistence, crash recovery resume, and hasActiveSession flow for SwiftUI**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-28T21:41:28Z
- **Completed:** 2026-03-28T21:45:03Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- WorkoutSessionViewModel with 10 public methods covering the full workout lifecycle (start, resume, complete set, skip rest, edit set, jump exercise, finish, discard, check active, reset)
- Sealed class state machine (Idle -> Active with RestState -> Finished) exposes single StateFlow for SwiftUI observation
- Per-set Room persistence enables crash recovery; resume reconstructs full session from Room + template data
- hasActiveSession StateFlow enables SwiftUI to detect and trigger the resume/discard prompt
- Wall-clock anchored rest timer avoids cumulative delay drift

## Task Commits

Each task was committed atomically:

1. **Task 1: WorkoutSessionViewModel with sealed class state machine, timer, persistence, and hasActiveSession flow** - `d934285` (feat)
2. **Task 2: Koin DI registration and KoinHelper iOS getter** - `7c502bd` (feat)

## Files Created/Modified
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt` - Core state machine ViewModel with sealed class states, rest timer, elapsed ticker, Room persistence, crash recovery, and 10 public API methods (455 lines)
- `shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt` - Added WorkoutSessionViewModel Koin registration
- `shared/src/iosMain/kotlin/com/pumpernickel/di/KoinHelper.kt` - Added getWorkoutSessionViewModel() iOS getter

## Decisions Made
- Single StateFlow<WorkoutSessionState> sealed class for all UI state rather than multiple separate state flows -- simplifies SwiftUI observation and ensures atomic state transitions
- Wall-clock anchoring for rest timer (Clock.System.now() on each tick) rather than simple decrement -- prevents cumulative drift from coroutine scheduling delays
- hasActiveSession as dedicated StateFlow<Boolean> checked via checkForActiveSession() -- lightweight signal for SwiftUI resume prompt without loading full session data

## Deviations from Plan

None -- plan executed exactly as written.

## Issues Encountered
- Room schema v2.json was missing in the worktree (only v3.json was generated). Copied from main repo to enable auto-migration compilation. This is a pre-existing gitignored artifact, not a code change.

## User Setup Required

None -- no external service configuration required.

## Known Stubs

None -- all data flows are wired to real Room repositories. No placeholder data or TODO markers.

## Next Phase Readiness
- WorkoutSessionViewModel is ready for iOS SwiftUI consumption via KoinHelper.getWorkoutSessionViewModel()
- Plan 03-03 (iOS workout session UI) can observe sessionState, elapsedSeconds, and hasActiveSession via asyncSequence
- All WORK-01 through WORK-09 business logic is implemented in shared KMP code

## Self-Check: PASSED

All files verified present, all commits verified in git log.

---
*Phase: 03-workout-session*
*Completed: 2026-03-28*
