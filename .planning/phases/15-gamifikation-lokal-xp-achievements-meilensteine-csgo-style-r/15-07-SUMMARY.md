---
phase: 15
plan: 07
subsystem: presentation/overview + domain/gamification + di
tags: [gamification, viewmodel, stateflow, koin, goal-day-trigger, rank-state, d-18, d-22]
dependency_graph:
  requires: [15-03 (GamificationRepository.rankState Flow), 15-04 (GamificationEngine.evaluateGoalDay)]
  provides: [OverviewViewModel.rankState StateFlow<RankState>, GoalDayTrigger.maybeTrigger D-22 trigger]
  affects: [plans 08-09 which render the Overview rank strip by collecting OverviewViewModel.rankState]
tech_stack:
  added: []
  patterns: [stateIn(WhileSubscribed(5000)) canonical StateFlow exposure, fire-and-forget coroutine for non-blocking side effect, kotlin.time.Clock for KMP-safe timestamps]
key_files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/GoalDayTrigger.kt
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/overview/OverviewViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/di/GamificationEngineModule.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt
decisions:
  - "GoalDayTrigger registered in GamificationEngineModule (not SharedModule) — preserves one-plan-per-file-per-wave discipline, matching plan 03 Blocker-1 fix"
  - "goalDayTrigger.maybeTrigger() launched in its own viewModelScope.launch{} block so it does not block the existing muscle/macro UI refresh coroutine"
  - "kotlin.time.Clock import used (not kotlinx.datetime.Clock) — KMP-safe; kotlinx.datetime.Clock does not exist and would fail iOS compilation"
  - "OverviewViewModel constructor now has 7 params (5 original + gamificationRepository + goalDayTrigger); Koin binding updated to 7 get() calls"
  - "rankState inserted at line 107, immediately after nutritionGoals StateFlow (line 98-101), mirroring M-6 PATTERNS guidance"
metrics:
  duration_seconds: 600
  completed_date: "2026-04-22"
  tasks_completed: 2
  files_created: 1
  files_modified: 3
---

# Phase 15 Plan 07: GoalDayTrigger + OverviewViewModel rankState Summary

**One-liner:** GoalDayTrigger (D-22 policy object evaluating yesterday+today on Overview appearance) wired into OverviewViewModel alongside a new @NativeCoroutinesState rankState: StateFlow<RankState> backed by GamificationRepository.rankState via stateIn(WhileSubscribed(5000), Unranked).

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Create GoalDayTrigger helper + register in Koin | 583447a | GoalDayTrigger.kt, GamificationEngineModule.kt |
| 2 | Wire GamificationRepository + GoalDayTrigger into OverviewViewModel; expose rankState | da44f1a | OverviewViewModel.kt, SharedModule.kt, GoalDayTrigger.kt (import fix) |

## OverviewViewModel Constructor After Plan 07

```kotlin
class OverviewViewModel(
    private val workoutRepository: WorkoutRepository,           // param 1 (existing)
    private val exerciseRepository: ExerciseRepository,         // param 2 (existing)
    private val settingsRepository: SettingsRepository,         // param 3 (existing)
    private val loadConsumptionsForDate: LoadConsumptionsForDateUseCase,  // param 4 (existing)
    private val calculateDailyMacros: CalculateDailyMacrosUseCase,        // param 5 (existing)
    private val gamificationRepository: GamificationRepository, // param 6 (new — plan 07)
    private val goalDayTrigger: GoalDayTrigger                  // param 7 (new — plan 07)
) : ViewModel()
```

Koin binding: `viewModel { OverviewViewModel(get(), get(), get(), get(), get(), get(), get()) }` — 7 `get()` calls match 7 params.

## rankState Insertion Point

`rankState` StateFlow inserted at **line 107** in `OverviewViewModel.kt`, immediately after the existing `nutritionGoals` StateFlow (lines 98–101), following the M-6 PATTERNS.md canonical form exactly:

```kotlin
/**
 * D-18: rank + XP for the Overview strip. Unranked until first workout save.
 */
@NativeCoroutinesState
val rankState: StateFlow<RankState> = gamificationRepository
    .rankState
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RankState.Unranked)
```

## goalDayTrigger.maybeTrigger() Wiring

Wired in `fun refresh()` (line 115) as the **first** statement, in its own `viewModelScope.launch { try { goalDayTrigger.maybeTrigger() } catch (t: Throwable) { ... } }` block. The existing muscle/macro UI refresh runs in a second separate `viewModelScope.launch` block, ensuring the D-22 evaluation does not block the tab refresh.

## GoalDayTrigger Policy

- `maybeTrigger()` checks `lastEvaluatedDate == today` in-memory guard — no duplicate work within a session
- Evaluates `yesterday` (day-rollover case) and `today` (goals-already-met-by-late-afternoon case)
- Engine's `(source, eventKey)` unique index on `xp_ledger` provides persistent cross-session idempotency
- `lastEvaluatedDate` is NOT persisted — process death + re-launch re-evaluates, re-dedupes safely via ledger

## Build Verification

- `./gradlew :shared:compileTestKotlinIosSimulatorArm64` — BUILD SUCCESSFUL
- All warnings are pre-existing (ObjC exposure in nutrition use cases, deprecated kotlinx.datetime.Instant typealias, redundant `else` in WorkoutSessionViewModel). Zero new warnings introduced.
- KMP correctness: `kotlin.time.Clock` used in GoalDayTrigger (not `kotlinx.datetime.Clock` which does not exist in KMP commonMain).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] GoalDayTrigger used `kotlinx.datetime.Clock` which does not exist in KMP commonMain**
- **Found during:** Task 2 — `compileKotlinIosSimulatorArm64` failed with "Unresolved reference 'System'" at GoalDayTrigger.kt:28
- **Issue:** The plan action block listed `import kotlinx.datetime.Clock` but `Clock` is in `kotlin.time` package; `kotlinx.datetime` has no `Clock` class
- **Fix:** Changed import to `import kotlin.time.Clock` matching the pattern used across other commonMain files (`GamificationEngine.kt`, `WorkoutRepository.kt`, `OverviewViewModel.kt`)
- **Files modified:** `GoalDayTrigger.kt`
- **Commit:** da44f1a

**2. [Rule 3 - Blocker] Room schema files missing from worktree caused KSP failure**
- **Found during:** Task 2 — first compile failed with "Schema '7.json' required for migration was not found"
- **Issue:** The git worktree did not have `shared/schemas/` files (gitignored generated artifacts); Room KSP requires both source and target schema files to generate AutoMigration code
- **Fix:** Copied `6.json` and `7.json` from main repo into worktree schemas directory (not staged — gitignored)
- **Files modified:** None (gitignored runtime files, not committed)

## Known Stubs

None — this plan delivers ViewModel wiring and a domain policy object. No UI data paths and no placeholder values flow to rendering. Plans 08–09 will render `rankState`.

## Threat Flags

None — all changes are commonMain Kotlin (ViewModel + domain policy). No new network endpoints, auth paths, file access patterns, or schema changes introduced.

## Self-Check

- [x] `GoalDayTrigger.kt` exists with `class GoalDayTrigger`, `suspend fun maybeTrigger()`, evaluates `yesterday` and `today`
- [x] `GamificationEngineModule.kt` has `single { GoalDayTrigger(get()) }` (not in SharedModule.kt)
- [x] `OverviewViewModel.kt` has `private val gamificationRepository: GamificationRepository` and `private val goalDayTrigger: GoalDayTrigger` as params 6 and 7
- [x] `OverviewViewModel.kt` has `@NativeCoroutinesState val rankState: StateFlow<RankState>` at line 107
- [x] `OverviewViewModel.kt` has `goalDayTrigger.maybeTrigger()` call in `refresh()`
- [x] `OverviewViewModel.kt` has 3 `@NativeCoroutinesState` annotations (uiState, nutritionGoals, rankState)
- [x] `SharedModule.kt` has `viewModel { OverviewViewModel(get(), get(), get(), get(), get(), get(), get()) }` (7 get() calls)
- [x] Commit 583447a exists (Task 1)
- [x] Commit da44f1a exists (Task 2)
- [x] `./gradlew :shared:compileTestKotlinIosSimulatorArm64` — BUILD SUCCESSFUL

## Self-Check: PASSED
