---
phase: 19-geofencing-workout-enforcement
plan: 01
subsystem: domain
tags: [kotlin, kmp, commonMain, domain, gamification, geofence, cold-start, datastore]

requires:
  - phase: 15-gamifikation-lokal-xp-achievements-meilensteine-csgo-style-r
    provides: GamificationRepository.awardXp, EventKeys, XpFormula, XpLedger dedupe pattern

provides:
  - GeofenceProvider interface (D-19-03) — contract for Wave 2 iOS/Android actuals
  - GeofenceEvent sealed class (D-19-03) — Enter/Exit/Error with regionId
  - EarlyExitTracker + EarlyExitBudget (D-19-07) — monthly budget domain wrapper
  - PendingGeofenceExit data class (D-19-04) — cold-start sentinel payload
  - PendingGeofenceExitStore interface (D-19-04) — set/consume/peek cold-start contract
  - LocationPermissionStatus enum (D-19-12) — NOT_DETERMINED/DENIED/RESTRICTED/WHEN_IN_USE/ALWAYS
  - PermissionController interface (D-19-12) — platform-agnostic permission gateway
  - XpFormula.geofenceExitPenalty(planned, logged) — staffeled -50..-200 XP formula (D-19-05)
  - EventKeys.SOURCE_GEOFENCE_EXIT + geofenceExit(workoutId, exitTimeMillis) (D-19-06)
  - GamificationEngine.onGeofenceExitPenalty(workoutId, exitTimeMillis, planned, logged) (D-19-05)
  - SettingsRepository.earlyExits: Flow<EarlyExitBudget> + incrementEarlyExitUsed() (D-19-07)
  - SettingsRepository implements PendingGeofenceExitStore via 5 new DataStore keys (D-19-04)

affects:
  - 19-03 (iOS actual: IosGeofenceProvider + IosPermissionController)
  - 19-04 (Android actual: AndroidGeofenceProvider + AndroidPermissionController)
  - 19-05 (Wave 3 ViewModel integration: consumePendingExit + onGeofenceExitPenalty call sites)
  - 19-06 (UI: WorkoutSessionView geofence chip + Early-Exit button)
  - 19-07 (Settings: EarlyExit row + detail sheet)

tech-stack:
  added: []
  patterns:
    - "kotlin.time.Clock.System (not kotlinx.datetime.Clock) is the correct import for Clock.System.now() in KMP commonMain"
    - "DataStore edit{} block used for atomic multi-key read-clear in consumePendingExit"
    - "Lazy month-reset: earlyExits Flow emits 0-used snapshot on month flip without touching DataStore on read"

key-files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/geofence/GeofenceEvent.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/geofence/GeofenceProvider.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/geofence/EarlyExitTracker.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/geofence/PendingGeofenceExit.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/geofence/PendingGeofenceExitStore.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/permissions/LocationPermissionStatus.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/permissions/PermissionController.kt
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/XpFormula.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/EventKeys.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/GamificationEngine.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt

key-decisions:
  - "kotlin.time.Clock is the correct import for Clock.System.now() — kotlinx.datetime.Clock is a separate interface without a System companion"
  - "startInactivityTimer() removed from WorkoutSessionViewModel in this plan (Rule 3 pre-empt) since GamificationEngine.onInactivityPenalty was removed, making the call site uncompilable — Plan 05 no longer needs to remove it"
  - "SettingsRepository implements PendingGeofenceExitStore inline (no separate class) — one Koin single{} bind PendingGeofenceExitStore::class resolves both"

patterns-established:
  - "Phase 19 geofence region-id format: active-workout-{startTimeMillis}"
  - "EarlyExitBudget lazy reset: read month from DataStore, compare to currentYearMonth(), emit 0-used if stale — no write on read"

requirements-completed: [D-19-03, D-19-04, D-19-05, D-19-06, D-19-07, D-19-12, D-19-15]

duration: 7min
completed: 2026-05-15
---

# Phase 19 Plan 01: commonMain Foundation Summary

**GeofenceProvider/PermissionController interfaces + EarlyExit budget + cold-start PendingGeofenceExitStore, with F5 inactivity path fully replaced by Phase 19 geofence-exit penalty (D-19-06)**

## Performance

- **Duration:** 7 min
- **Started:** 2026-05-15T14:30:09Z
- **Completed:** 2026-05-15T14:37:24Z
- **Tasks:** 3
- **Files modified:** 12 (7 created, 5 modified)

## Accomplishments

- Shipped all 7 new commonMain interfaces/models that Wave 2 (iOS/Android actuals) and Wave 3 (ViewModel) bind against
- Replaced F5 inactivity mechanism completely: XpFormula constants removed, GamificationEngine.onGeofenceExitPenalty replaces onInactivityPenalty, EventKeys.SOURCE_GEOFENCE_EXIT added
- SettingsRepository now implements PendingGeofenceExitStore — DataStore-backed cold-start sentinel with atomic read-clear semantics

## Task Commits

1. **Task 1: Add geofence + permission interfaces + cold-start sentinel models in commonMain** - `57b1c18` (feat)
2. **Task 2: Refactor XpFormula + EventKeys + GamificationEngine for geofence penalty path** - `515a5c8` (refactor)
3. **Task 3: Add Early-Exit + PendingGeofenceExit DataStore keys + Flows to SettingsRepository** - `9665af1` (feat)

## Files Created/Modified

**Created:**
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/geofence/GeofenceEvent.kt` — sealed class Enter/Exit/Error with regionId
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/geofence/GeofenceProvider.kt` — register/unregister/events SharedFlow interface
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/geofence/EarlyExitTracker.kt` — budget domain wrapper + EarlyExitBudget data class (BUDGET=2/month)
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/geofence/PendingGeofenceExit.kt` — cold-start sentinel payload (workoutId, exitTimeMillis, regionId)
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/geofence/PendingGeofenceExitStore.kt` — setPendingExit/consumePendingExit/peekPendingExit interface
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/permissions/LocationPermissionStatus.kt` — 5-state enum
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/permissions/PermissionController.kt` — 5-method interface

**Modified:**
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/XpFormula.kt` — removed INACTIVITY_PENALTY_XP + INACTIVITY_TIMEOUT_SECONDS; added GEOFENCE_EXIT_PENALTY_PER_MISSED_SET=10/MIN=50/MAX=200, GEOFENCE_GRACE_PERIOD_SECONDS=300L, geofenceExitPenalty()
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/EventKeys.kt` — added SOURCE_GEOFENCE_EXIT + geofenceExit(workoutId, exitTimeMillis)
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/GamificationEngine.kt` — replaced onInactivityPenalty with onGeofenceExitPenalty, removed GeoPoint import
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt` — implements PendingGeofenceExitStore, adds earlyExits Flow + incrementEarlyExitUsed() + 5 DataStore keys
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt` — removed startInactivityTimer + inactivityJob (Rule 3 auto-fix, see Deviations)

## Decisions Made

- `kotlin.time.Clock` (not `kotlinx.datetime.Clock`) is the correct import for `Clock.System.now()` in KMP commonMain. The two Clocks are different types — `kotlinx.datetime.Clock` is an interface with no `System` companion.
- SettingsRepository implements PendingGeofenceExitStore directly (no wrapper class). Wave 2 Koin modules bind via `single { settingsRepository } bind PendingGeofenceExitStore::class`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Pre-emptively removed startInactivityTimer from WorkoutSessionViewModel**
- **Found during:** Task 2 (GamificationEngine refactor)
- **Issue:** Plan removes `GamificationEngine.onInactivityPenalty` but `WorkoutSessionViewModel.startInactivityTimer()` still called it. Removing the method without updating the call site causes a commonMain compile error (Kotlin: Unresolved reference `onInactivityPenalty`).
- **Fix:** Removed `startInactivityTimer()` method and `inactivityJob`, and its call sites from `completeSet()`, `enterReview()`, `discardWorkout()` in WorkoutSessionViewModel. This work was scheduled for Plan 05 (Wave 3) but had to be pulled forward to unblock compilation.
- **Files modified:** `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt`
- **Verification:** `compileKotlinMetadata` + all three iOS targets compile successfully
- **Committed in:** `515a5c8` (Task 2 commit)
- **Impact on Plan 05:** Plan 05 no longer needs to remove `startInactivityTimer` — it's already gone. Plan 05 should focus solely on adding the new geofence event subscription and consumePendingExit() call.

**2. [Rule 1 - Bug] Fixed wrong Clock import in SettingsRepository**
- **Found during:** Task 3 (iOS compile target check)
- **Issue:** Used `import kotlinx.datetime.Clock` but `kotlinx.datetime.Clock` is an interface without a `System` companion. iOS target reported `Unresolved reference: System`.
- **Fix:** Changed import to `import kotlin.time.Clock` (matches pattern in GoalDayTrigger.kt, WorkoutRepository.kt)
- **Files modified:** `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt`
- **Verification:** All three iOS compile targets pass
- **Committed in:** `9665af1` (Task 3 commit)

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 bug)
**Impact on plan:** Both fixes necessary for correctness. No scope creep. The startInactivityTimer removal is a natural pre-empt that simplifies Plan 05.

## Issues Encountered

None beyond the auto-fixed deviations above.

## Notes for Wave 2 (Plans 03/04 — iOS/Android actuals)

- Bind `SettingsRepository` as `PendingGeofenceExitStore` in each PlatformModule:
  ```kotlin
  single { settingsRepository } bind PendingGeofenceExitStore::class
  ```
  (one Koin binding resolves both interfaces via the same instance)
- Region-id format: `"active-workout-${startTimeMillis}"` — use `ActiveSessionEntity.startTimeMillis` (NOT `.id` which is a singleton=1)
- On EXIT event, call `pendingGeofenceExitStore.setPendingExit(PendingGeofenceExit(workoutId, exitTimeMillis, regionId))` BEFORE emitting on `events` SharedFlow

## Notes for Wave 3 (Plan 05 — ViewModel integration)

- `startInactivityTimer` is already removed (see Deviations). Do NOT attempt to remove it again.
- Add `consumePendingExit()` call in `checkForActiveSession()` BEFORE subscribing to `geofenceProvider.events`
- Add the geofence-events subscription observing `GeofenceEvent.Exit` → start 5-min grace period timer (`GEOFENCE_GRACE_PERIOD_SECONDS = 300L`)
- On grace expiry: call `gamificationEngine.onGeofenceExitPenalty(workoutId = session.startTimeMillis, exitTimeMillis, plannedSetCount, loggedSetCount)`
- On `GeofenceEvent.Enter` during grace: cancel timer, clear any `pendingGeofenceExitStore.setPendingExit(null)`

## Self-Check

Checking all created files and commits exist:

- [x] `GeofenceProvider.kt` — FOUND
- [x] `GeofenceEvent.kt` — FOUND
- [x] `EarlyExitTracker.kt` — FOUND
- [x] `PendingGeofenceExit.kt` — FOUND
- [x] `PendingGeofenceExitStore.kt` — FOUND
- [x] `LocationPermissionStatus.kt` — FOUND
- [x] `PermissionController.kt` — FOUND
- [x] Commit `57b1c18` — Task 1
- [x] Commit `515a5c8` — Task 2
- [x] Commit `9665af1` — Task 3

## Self-Check: PASSED

---
*Phase: 19-geofencing-workout-enforcement*
*Completed: 2026-05-15*
