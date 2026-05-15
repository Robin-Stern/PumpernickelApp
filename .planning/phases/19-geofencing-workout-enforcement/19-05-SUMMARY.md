---
phase: 19-geofencing-workout-enforcement
plan: "05"
subsystem: presentation/workout
tags: [kotlin, kmp, viewmodel, gamification, integration, geofence, cold-start]
requirements: [D-19-04, D-19-05, D-19-06, D-19-08, D-19-13, D-19-14, D-19-15]
status: complete
---

# Plan 19-05 SUMMARY — VM Integration

## Files Modified

| File | Δ | Note |
|------|---|------|
| `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/GamificationEngine.kt` | +38 | `processAbandonedWorkout(workoutId)` — volume XP only, no PR/achievement/rank/streak (Task 1) |
| `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt` | +372 / -10 | Constructor 6→11 args, GeofenceUiState/EarlyExitResult sealed classes, cold-start reconciliation, geofence lifecycle, grace timer, early-exit flow (Task 2) |
| `shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt` | +1 / -1 | 11-arg `get()` binding (Task 3) |

## Execution Notes

- **Worktree agent stalled mid-Task-2** (after ~10 minutes inside the file rewrite). Salvaged Task 1 commit `5fc62c8` from the worktree branch; executed Tasks 2 & 3 inline via surgical `Edit` operations.
- Plan 19-01 had already removed F5 traces (`startInactivityTimer`, `inactivityJob`) pre-emptively from `WorkoutSessionViewModel.kt`, so EDIT 11 was a no-op.
- `restoreActiveSessionFromRoom` decision: extracted `resumeWorkout`'s body into a new private `suspend fun rehydrateActiveSession()` — reused by both `resumeWorkout()` and the cold-start reconciliation path. Cleaner than duplicating 90+ lines.

## Wave 4 Cheat Sheet — VM Surface

```kotlin
// Status chip
val geofenceState: StateFlow<GeofenceUiState>
sealed class GeofenceUiState {
    data object Inactive                                   // pre-1st-set OR permission missing
    data object InZone                                     // user inside radius
    data class GracePeriod(val remainingSeconds: Int)      // 300 → 0, render m:ss
    data object Exited                                     // transient pre-disposal
}

// Settings + confirm dialog
val earlyExitBudget: StateFlow<EarlyExitBudget>            // { used, remaining, yearMonth }

// User-initiated end-of-workout
fun requestEarlyExit(): EarlyExitResult
sealed class EarlyExitResult {
    data object NoActiveSession
    data object NormalReview                               // all sets done → enterReview()
    data object Processing                                 // async — observe sessionState for Finished
}
```

**iOS (SwiftUI):** KMP-NativeCoroutines exports `geofenceState` as flat `Shared.GeofenceUiState.*`. Cases: `inactive`, `inZone`, `gracePeriod(remainingSeconds:)`, `exited`.

## BLOCKER Fixes

### BLOCKER-19-3 — Cold-start reconciliation
`checkForActiveSession()` body (verified):
```kotlin
val pending = pendingGeofenceExitStore.consumePendingExit()     // 1) atomic read+clear
val hasActive = workoutRepository.hasActiveSession()
_hasActiveSession.value = hasActive
if (pending != null && hasActive) {
    val active = workoutRepository.getActiveSession()
    val expectedRegionId = active?.startTimeMillis?.let { "active-workout-$it" }
    if (expectedRegionId != null && pending.regionId == expectedRegionId) {
        rehydrateActiveSession()                                 // 2) restore state
        handleGeofenceExitGraceExpired(                          // 3) penalty without grace
            exitTimeMillisOverride = pending.exitTimeMillis
        )
    }
}
```
`consumePendingExit()` is invoked BEFORE the platform actuals' SharedFlow event observer subscribes (`startGeofenceObserver` only runs after register-on-1st-set). Ledger `(source, eventKey)` unique index absorbs any warm/cold race.

### BLOCKER-19-4 — Region-id strategy
Every regionId built as `"active-workout-${active.startTimeMillis}"`. `onGeofenceExitPenalty` called with `workoutId = active.startTimeMillis` (NOT the post-save `CompletedWorkout.id`). Matches platform actuals' `parseWorkoutIdFromRegionId` schema; ledger eventKey uniqueness comes from `(startTimeMillis, exitTimeMillis)` pair.

## Acceptance Criteria — All Pass

| Check | Result |
|---|---|
| `startInactivityTimer` count | 0 ✓ |
| `inactivityJob` count | 0 ✓ |
| 5 new constructor params | all 1 ✓ |
| `startGeofenceObserver` / `startGracePeriod` / `handleGeofenceExitGraceExpired` / `requestEarlyExit` | all 1 ✓ |
| `geofenceProvider.register` / `.unregister` | 1 / 4 ✓ |
| `consumePendingExit` in `checkForActiveSession` body | 1 ✓ |
| `active-workout-${active.startTimeMillis}` | 1 ✓ |
| `workoutId = active.startTimeMillis` (penalty call) | 1 ✓ |
| SharedModule 11-arg binding | 1 ✓ |

## Build Results

| Target | Result |
|---|---|
| `:shared:compileKotlinIosArm64` | ✅ BUILD SUCCESSFUL (warnings only) |
| `:androidApp:assembleDebug` | ✅ BUILD SUCCESSFUL |
| `:shared:compileCommonMainKotlinMetadata` | ⚠️ FAILS on `expect object AppDatabaseConstructor` resolution — pre-existing Room KMP metadata-stage issue, does NOT block actual platform builds. Tracked as Out-of-Scope per Wave 2 19-04 SUMMARY notes. |

## Commits

- `5fc62c8` feat(19-05): add processAbandonedWorkout to GamificationEngine (rescued from stalled worktree)
- `fc1fd2e` feat(19-05): wire geofence + early-exit lifecycle into WorkoutSessionViewModel
- `ff23adb` feat(19-05): update SharedModule WorkoutSessionViewModel binding to 11 args
