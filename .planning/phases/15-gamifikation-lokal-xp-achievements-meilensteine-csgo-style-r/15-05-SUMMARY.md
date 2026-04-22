---
phase: 15
plan: 05
subsystem: data/repository + di + platform-startup
tags: [gamification, retroactive-walker, startup, koin, android, ios, first-launch]
dependency_graph:
  requires: [15-01 (Room schema + GamificationDao), 15-02 (domain models), 15-03 (GamificationRepository, AchievementStateSeeder, SettingsRepository.retroactiveApplied), 15-04 (GamificationEngine with processHistoricalWorkout + processHistoricalGoalDay)]
  provides: [RetroactiveWalker, GamificationStartup, GamificationStartupIos, runAchievementAndRankChecksForReplay, Android/iOS first-launch trigger wiring]
  affects: [plans 06+ (GoalDayTrigger, UI) — startup is now canonical first-launch path]
tech_stack:
  added: []
  patterns: [sentinel-gated one-shot replay via DataStore boolean flag, GlobalScope.launch on iOS for one-shot background work, KoinPlatform.getKoin() in iosMain (mirrors KoinHelper.kt pattern)]
key_files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/RetroactiveWalker.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/di/GamificationStartup.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/di/GamificationStartupIos.kt
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/GamificationEngine.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/di/GamificationEngineModule.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/PumpernickelApplication.kt
    - iosApp/iosApp/PumpernickelApp.swift
decisions:
  - "RetroactiveWalker derives ISO date from ConsumptionEntryEntity.timestampMillis via kotlinx-datetime (no date column exists on the entity) — matches GamificationEngine.buildSnapshot() derivation exactly (Warning-9 fix)"
  - "GamificationStartupIos uses KoinPlatform.getKoin() (not GlobalContext) — GlobalContext is not available in iosMain; KoinPlatform is the standard pattern per existing KoinHelper.kt"
  - "Schema files 6.json and 7.json copied from main repo into worktree at build time (gitignored); required by Room KSP for AutoMigration compilation on iosSimulatorArm64"
  - "Sentinel set only on success (replay() returns without exception) — failure leaves retroactiveApplied=false so next launch retries; (source,eventKey) unique index prevents double-award on partial re-run (D-13)"
  - "runAchievementAndRankChecksForReplay() placed immediately before the Internals divider in GamificationEngine — after processHistoricalGoalDay, before private helpers"
metrics:
  duration_seconds: 600
  completed_date: "2026-04-22"
  tasks_completed: 2
  files_created: 3
  files_modified: 4
---

# Phase 15 Plan 05: RetroactiveWalker + GamificationStartup Summary

**One-liner:** Sentinel-gated RetroactiveWalker (chronological workout + goal-day XP replay with running-PB map), ordered GamificationStartup wrapper, and Android/iOS platform startup calls wired via Dispatchers.IO / GlobalScope.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | RetroactiveWalker + runAchievementAndRankChecksForReplay | 0fc53b8 | RetroactiveWalker.kt, GamificationEngine.kt |
| 2 | GamificationStartup + Koin bindings + Android/iOS init-path calls | 9d385cf | GamificationStartup.kt, GamificationEngineModule.kt, PumpernickelApplication.kt, GamificationStartupIos.kt, PumpernickelApp.swift |

## Android Application Subclass

File: `androidApp/src/androidMain/kotlin/com/pumpernickel/android/PumpernickelApplication.kt`

`startup.run()` inserted after the `initKoin { androidContext(...) }` block in `onCreate()`. Uses `CoroutineScope(Dispatchers.IO).launch` with `GlobalContext.get().get<GamificationStartup>()` to resolve from Koin on a background thread.

## iOS App Root

File: `iosApp/iosApp/PumpernickelApp.swift` — `AppRootView.body`

`GamificationStartupIos.shared.trigger()` inserted as the first statement inside the existing `.task { ... }` modifier (before `withTaskGroup`). The trigger is non-blocking (fire-and-forget via `GlobalScope.launch`), so it does not interfere with the `withTaskGroup` for theme/accent observation.

The iosMain Kotlin helper `GamificationStartupIos` is at:
`shared/src/iosMain/kotlin/com/pumpernickel/di/GamificationStartupIos.kt`

## NutritionGoals Field Type Notes

`NutritionGoals` fields are all non-nullable `Int` (calorieGoal, proteinGoal, fatGoal, carbGoal, sugarGoal). The `NutritionGoalDayPolicy.isGoalDay()` predicate already handles the `goal <= 0` skip case. The walker passes `settingsRepo.nutritionGoals.first()` directly to `NutritionGoalDayPolicy.isGoalDay()` without any null-coercion — the types match exactly.

## RetroactiveWalker Design Notes

- **Chronological order:** `completedWorkoutDao.getAllWorkouts().first().sortedBy { it.startTimeMillis }` — ensures PR detection uses "PB at that point in time" semantics (D-12).
- **Running-PB map:** `mutableMapOf<String, Int>()` passed by reference to `engine.processHistoricalWorkout()` — the engine updates it as each historical PR is detected.
- **Goal-day date derivation:** `entry.timestampMillis.toLocalDateString()` using the same `Instant.fromEpochMilliseconds().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()` pattern as `GamificationEngine.buildSnapshot()`.
- **No transaction wrapper:** Per plan spec, no `withTransaction { }` — dedupe via `(source, eventKey)` unique index provides idempotency (D-13). Sentinel only set on full success.

## Build Verification

- `./gradlew :shared:compileTestKotlinIosSimulatorArm64` — BUILD SUCCESSFUL (3s). All warnings are pre-existing (deprecated Instant typealias in engine, ObjC suspend exposure in nutrition use cases — none from plan 05 changes).
- `./gradlew :shared:compileKotlinMetadata` — BUILD SUCCESSFUL (1s).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] GlobalContext not available in iosMain**
- **Found during:** Task 2 — iOS compile step
- **Issue:** `org.koin.core.context.GlobalContext` is not accessible from the iosMain source set; the compiler emitted "Unresolved reference 'GlobalContext'"
- **Fix:** Replaced `GlobalContext.get().get<GamificationStartup>()` with `KoinPlatform.getKoin().get<GamificationStartup>()` — the same pattern used by `KoinHelper.kt` in the same iosMain package
- **Files modified:** `GamificationStartupIos.kt`
- **Commit:** 9d385cf

**2. [Rule 3 - Blocking] Room KSP missing schema files 6.json and 7.json in worktree**
- **Found during:** Task 2 — first iOS compile run
- **Issue:** Worktree only had schema 8.json; AutoMigration(6,7) and (7,8) require 6.json and 7.json present at compile time
- **Fix:** Copied from main repo (`/Users/olli/.../PumpernickelApp/shared/schemas/`) to worktree at same relative path. Files are gitignored (expected — schema files live in main repo, worktrees get them on-demand)
- **Files modified:** worktree schemas directory (not committed — gitignored)
- **Commit:** N/A

## Known Stubs

None — this plan delivers startup infrastructure only. No UI rendering paths introduced.

## Threat Flags

None — all changes are local Kotlin/Swift wiring. No new network endpoints, auth paths, or schema changes introduced.

## Self-Check

- [x] `RetroactiveWalker.kt` at `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/`
- [x] `GamificationStartup.kt` at `shared/src/commonMain/kotlin/com/pumpernickel/di/`
- [x] `GamificationStartupIos.kt` at `shared/src/iosMain/kotlin/com/pumpernickel/di/`
- [x] `GamificationEngine.kt` has `suspend fun runAchievementAndRankChecksForReplay()`
- [x] `GamificationEngineModule.kt` has `single { RetroactiveWalker(get(), get(), get(), get()) }` and `single { GamificationStartup(get(), get()) }`
- [x] `SharedModule.kt` NOT modified (no `RetroactiveWalker` or `GamificationStartup` bindings there)
- [x] `PumpernickelApplication.kt` has `CoroutineScope(Dispatchers.IO).launch { startup.run() }` after `initKoin`
- [x] `PumpernickelApp.swift` has `GamificationStartupIos.shared.trigger()` inside existing `.task`
- [x] Commit 0fc53b8 exists (Task 1)
- [x] Commit 9d385cf exists (Task 2)
- [x] `./gradlew :shared:compileTestKotlinIosSimulatorArm64` — BUILD SUCCESSFUL

## Self-Check: PASSED
