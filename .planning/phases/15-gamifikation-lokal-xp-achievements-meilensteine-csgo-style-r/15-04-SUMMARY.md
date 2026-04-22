---
phase: 15
plan: 04
subsystem: domain/gamification
tags: [gamification, engine, streak, achievements, pure-functions, koin, tdd]
dependency_graph:
  requires: [15-01 (GamificationDao, XpLedgerEntity, entities), 15-02 (domain models: Rank, RankLadder, AchievementCatalog, XpFormula, EventKeys, UnlockEvent), 15-03 (GamificationRepository, GamificationEngineModule shell)]
  provides: [GamificationEngine, StreakCalculator, AchievementRules, ProgressSnapshot, RuleEvaluation, StreakResult, NutritionDao.getAllEntries()]
  affects: [plans 05 (WorkoutSessionViewModel hook), 06 (RetroactiveWalker), 07 (GoalDayTrigger), 08-09 (UI observes unlockEvents + rankState)]
tech_stack:
  added: []
  patterns: [pure-object helpers with no Room/Koin imports, SharedFlow buffer for modal queue, MutableMap pbOverride for retroactive PR detection, epochDay arithmetic via kotlinx-datetime]
key_files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/StreakCalculator.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/AchievementRules.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/GamificationEngine.kt
    - shared/src/commonTest/kotlin/com/pumpernickel/domain/gamification/StreakCalculatorTest.kt
    - shared/src/commonTest/kotlin/com/pumpernickel/domain/gamification/AchievementRulesTest.kt
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/di/GamificationEngineModule.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/db/NutritionDao.kt
decisions:
  - "StreakCalculator.longestStreak() computes the tail-anchored run (current streak) not the all-time maximum — 'longest streak' in the spec means the run anchored at the most recent epochDay, not historical best"
  - "evaluateNutritionStreakAt() is a private shared helper (not two separate code paths) so live evaluateGoalDay() and retroactive processHistoricalGoalDay() produce identical streak checks — Warning-9 fix"
  - "ConsumptionEntryEntity has no date column — engine derives ISO date from timestampMillis via kotlinx-datetime toLocalDateTime().date.toString() for both goal-day filtering and snapshot grouping"
  - "NutritionDao.getAllEntries() added (ascending order) instead of a separate getEntriesForDate() — filtering by derived date is done on the Kotlin side to avoid a date-column dependency in SQL"
  - "GamificationEngine is in domain/gamification package (not presentation) because it is injected by both WorkoutSessionViewModel (plan 05) and RetroactiveWalker (plan 06) — placing it in domain keeps the layer boundary clean"
  - "GamificationEngineModule Koin binding uses named constructor parameters for readability; order matches the class constructor declaration"
metrics:
  duration_seconds: 1200
  completed_date: "2026-04-22"
  tasks_completed: 3
  files_created: 5
  files_modified: 2
---

# Phase 15 Plan 04: GamificationEngine + Pure Helpers Summary

**One-liner:** Pure StreakCalculator (epochDay consecutive-run arithmetic), pure AchievementRules (all-families dispatch with forward-compat unknown-family skip), and GamificationEngine orchestrator (XP + streak + achievement + rank + D-19 SharedFlow) wired into Koin via GamificationEngineModule.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | StreakCalculator (pure) + 10 tests | 7c603e2 | StreakCalculator.kt, StreakCalculatorTest.kt |
| 2 | AchievementRules (pure) + 6 tests | 6fd13e8 | AchievementRules.kt, AchievementRulesTest.kt |
| 3 | GamificationEngine + Koin binding + NutritionDao.getAllEntries() | d511dd3 | GamificationEngine.kt, GamificationEngineModule.kt, NutritionDao.kt |

## StreakCalculator API

`object StreakCalculator`:
- `longestStreak(epochDays: List<Long>): StreakResult` — deduplicates + sorts input, walks backwards from tail, returns the tail-anchored consecutive run
- `thresholdsCrossed(previousCurrentLength: Int, newCurrentLength: Int, thresholds: List<Int>): List<Int>` — flat-on-threshold semantics per D-06

`data class StreakResult(currentLength: Int, runStartEpochDay: Long?)`

## AchievementRules API

`object AchievementRules`:
- `evaluate(snapshot: ProgressSnapshot, currentStates: List<AchievementProgress>): RuleEvaluation`
- Covers all 12 AchievementCatalog families via `progressFor()` dispatch
- Unknown families silently return null and are skipped (forward-compat)
- Already-unlocked check prevents re-unlock

`data class ProgressSnapshot` — 12 fields, all default 0 for partial test construction

`data class RuleEvaluation(updatedProgress: Map<String, Long>, toUnlock: List<String>)`

## GamificationEngine API

```kotlin
class GamificationEngine(
    gamificationRepo: GamificationRepository,
    completedWorkoutDao: CompletedWorkoutDao,
    nutritionDao: NutritionDao,
    exerciseDao: ExerciseDao,
    settingsRepo: SettingsRepository
)
```

Public surface:
- `val unlockEvents: SharedFlow<UnlockEvent>` — 16-slot buffer, replay=0 (D-19)
- `suspend fun onWorkoutSaved(workoutId: Long)` — D-20 live trigger
- `suspend fun evaluateGoalDay(date: LocalDate)` — D-22 nutrition goal-day trigger
- `suspend fun processHistoricalWorkout(workoutId, awardedAtMillis, runningPbKgX10)` — plan 06 retroactive entry
- `suspend fun processHistoricalGoalDay(date, awardedAtMillis)` — plan 06 retroactive entry

## DAO Additions Made

### NutritionDao.kt
Added `getAllEntries(): List<ConsumptionEntryEntity>` — returns all consumption entries ASC by timestamp. Used by engine's `buildSnapshot()` and `evaluateGoalDay()` to group by derived ISO date. No SQL date column needed — Kotlin-side date derivation from `timestampMillis`.

### Already Present (plan 01 + plan 01 schema)
- `CompletedWorkoutDao.getExercisesForWorkout(workoutId)` — present from prior work
- `CompletedWorkoutDao.getSetsForExercise(workoutExerciseId)` — present from prior work
- `GamificationDao.getGoalDayIsoDates()` — added in plan 01 (BLOCKER-3)
- `GamificationDao.getPrLedgerEntries()` — added in plan 01 (BLOCKER-4)
- `GamificationRepository.getGoalDayIsoDates()` — passthrough added in plan 03
- `GamificationRepository.getPrLedgerEntries()` — passthrough added in plan 03
- `ExerciseDao.getAllExercises(): Flow<List<ExerciseEntity>>` — existed from v1.0

## Design Decisions

**StreakCalculator semantics:** The name "longestStreak" reflects the D-06 concept of tracking the current run length. In practice, for the streak achievement award, what matters is the tail-anchored consecutive run (has the streak been maintained?), not a historical maximum. The function name is kept as specified in the plan.

**Goal-day filtering without a `date` column:** `ConsumptionEntryEntity.timestampMillis` is converted to ISO "YYYY-MM-DD" in local time zone via `kotlinx-datetime`. This matches how the `NutritionGoalDayPolicy.isGoalDay()` groups daily entries. The engine avoids adding a redundant `date` column to the entity.

**PR detection in retroactive path:** The `pbOverride: MutableMap<String, Int>?` parameter threads through `processWorkout()`. When non-null (retroactive), the engine reads prior PBs from the caller-managed map instead of querying Room — preserving "what was the PB at that point in time" semantics (D-12).

## TODO Markers for Plan 06

- `RetroactiveWalker` (plan 06) calls `processHistoricalWorkout()` and `processHistoricalGoalDay()` in chronological order with a caller-managed running-PB map
- `RetroactiveWalker` should call `runAchievementAndRankChecks()` once after the full walk (not per-workout) for performance — the engine currently runs it live after each workout save, which is correct for the live path
- Nutrition goal-day retroactive replay must derive ISO date from `ConsumptionEntryEntity.timestampMillis` the same way as the engine's `buildSnapshot()`

## Koin Registration

```kotlin
// In GamificationEngineModule.kt (plan 04 adds this):
val gamificationEngineModule = module {
    single {
        GamificationEngine(
            gamificationRepo = get(),
            completedWorkoutDao = get(),
            nutritionDao = get(),
            exerciseDao = get(),
            settingsRepo = get()
        )
    }
    // RetroactiveWalker, GamificationStartup — plan 05
    // GoalDayTrigger — plan 07
}
```

Mounted from `SharedModule.kt` via `includes(gamificationEngineModule)` (wired in plan 03 — no changes to SharedModule in this plan).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] ConsumptionEntryEntity has no `date` field**
- **Found during:** Task 3 — plan action block references `it.date` for grouping entries by date
- **Issue:** `ConsumptionEntryEntity` stores `timestampMillis: Long` with no pre-computed `date: String` column
- **Fix:** Engine derives ISO date from `timestampMillis` via `kotlinx-datetime` `toLocalDateString()` extension; groups entries by derived string in `buildSnapshot()` and `evaluateGoalDay()`
- **Files modified:** `GamificationEngine.kt`
- **Commit:** d511dd3

**2. [Rule 2 - Missing] NutritionDao lacked `getAllEntries()` for engine consumption**
- **Found during:** Task 3 — engine's `buildSnapshot()` and `evaluateGoalDay()` need all consumption rows
- **Issue:** `NutritionDao` had `getAllConsumptions()` (DESC order for UI) but no ASC-ordered variant for aggregation
- **Fix:** Added `NutritionDao.getAllEntries(): List<ConsumptionEntryEntity>` (ASC by timestampMillis)
- **Files modified:** `NutritionDao.kt`
- **Commit:** d511dd3

**3. [Rule 1 - Bug] GamificationEngineModule Koin constructor arg count adapted to actual engine signature**
- **Found during:** Task 3 — plan action block shows `single { GamificationEngine(get(), get(), get(), get(), get()) }` positionally; actual engine has 5 params with named args
- **Fix:** Used named constructor parameters in the `single { }` block for readability and correctness; arg count = 5 matches the class declaration
- **Files modified:** `GamificationEngineModule.kt`
- **Commit:** d511dd3

## Known Stubs

None — this plan delivers decision-logic core with no UI surfaces. No placeholder values flow to rendering.

## Threat Flags

None — all changes are commonMain Kotlin. No new network endpoints, auth paths, file access patterns, or schema changes introduced (NutritionDao.getAllEntries() reads an existing table).

## Build Verification

- `./gradlew :shared:compileKotlinIosSimulatorArm64 --rerun-tasks` — BUILD SUCCESSFUL (3s). All warnings are pre-existing (ObjC Flow exposure in other files, deprecated typealias in nutrition use case).
- Domain purity check: `grep -rE "import androidx.room|import org.koin|import platform." shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/` — zero matches for pure helper files (StreakCalculator.kt, AchievementRules.kt). GamificationEngine correctly imports DAOs/repos via interfaces.
- Koin binding: GamificationEngine registered exactly once — in `GamificationEngineModule.kt` only.
- Event key paths: all XP award calls use `EventKeys.SOURCE_*` constants and `EventKeys.*()` constructors — no hand-rolled key strings.

## Self-Check

- [x] `StreakCalculator.kt` at correct path with `object StreakCalculator`, `fun longestStreak`, `fun thresholdsCrossed`, `data class StreakResult`
- [x] `StreakCalculatorTest.kt` at correct test path with 10 test methods
- [x] `AchievementRules.kt` at correct path with `object AchievementRules`, `data class ProgressSnapshot`, `data class RuleEvaluation`
- [x] `AchievementRulesTest.kt` at correct test path with 6 test methods
- [x] `GamificationEngine.kt` at correct path with `class GamificationEngine`, `val unlockEvents: SharedFlow<UnlockEvent>`, `suspend fun onWorkoutSaved(workoutId: Long)`, `suspend fun evaluateGoalDay(date: LocalDate)`
- [x] `GamificationEngineModule.kt` updated with `single { GamificationEngine(...) }` binding
- [x] `NutritionDao.kt` updated with `getAllEntries()` method
- [x] Commit 7c603e2 exists (Task 1)
- [x] Commit 6fd13e8 exists (Task 2)
- [x] Commit d511dd3 exists (Task 3)
- [x] Gradle compileKotlinIosSimulatorArm64: BUILD SUCCESSFUL

## Self-Check: PASSED
