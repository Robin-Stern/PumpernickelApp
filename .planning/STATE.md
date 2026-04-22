---
gsd_state_version: 1.0
milestone: v1.5
milestone_name: Android Material 3 UI
status: completed
stopped_at: v1.5 shipped 2026-03-31; post-milestone work merged outside GSD (fe297ad)
last_updated: "2026-04-22T00:00:00.000Z"
last_activity: 2026-04-22
progress:
  total_phases: 4
  completed_phases: 4
  total_plans: 9
  completed_plans: 9
  percent: 100
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-22)

**Core value:** Users can select a workout template and execute it set-by-set — logging reps, weight, and rest periods — with a clean, reliable flow
**Current focus:** iOS work on `feature/workouts`; next milestone not yet opened

## Current Position

Milestone: v1.5 (shipped 2026-03-31)
Phase: none (between milestones)
Branch: `feature/workouts` @ `fe297ad`
Status: awaiting `/gsd:new-milestone` before next planned iOS work
Last activity: 2026-04-22 (GSD reconciliation)

Progress: [██████████] 100% (v1.5 shipped)

## ⚠️  Untracked Drift

Between the last GSD artifact (Phase 14 completed 2026-03-31, commit `4d02ce0`) and the current branch tip (`fe297ad`, 2026-04-14), 28 commits landed on `feature/workouts` outside the GSD workflow. Highlights:

- **Nutrition feature (F2 from Lastenheft)** — Food / Recipe / ConsumptionEntry domain, 11 use cases in `domain/nutrition/`, 4 ViewModels in `presentation/nutrition/`, OpenFoodFacts barcode lookup, iOS views in `Views/Nutrition/` (DailyLog, FoodEntry, RecipeList, RecipeCreation, BarcodeScanner, MacroRow), Android screens + navigation
- **Dynamic theming** — Light/dark/system toggle + 8 accent color presets, persisted in DataStore, iOS `ThemeManager` (@Observable) with `asyncSequence` observation on app root, unified Material palette on Android
- **Nutrition goals** — `NutritionGoals` domain model (calorie/protein/fat/carb/sugar), persisted in `SettingsRepository`, surfaced on Overview tab via `OverviewViewModel`
- **Workout/History polish** — Template editor redesign, history detail set-count + RIR display, PB calculation fix, semantic colors replacing hardcoded RGB, rest timer improvements
- **Infrastructure** — Room schema v4 → v7 (AutoMigration 6→7 registered; 4 new nutrition entities), composeApp → `android-kmp-library` plugin with androidApp module extracted, kotlinx-datetime + Ktor client added to stack, camera/barcode permissions wired

See `MILESTONES.md` → "Post-v1.5 (Untracked)" for the full summary. No per-phase artifacts exist for this work — treat it as shipped but not planned.

## Performance Metrics

**Velocity:**

- Total plans completed: 21 (12 v1.0 + 9 v1.1)
- v1.1 execution: 6 phases, 9 plans, 18 tasks in 2 days

**By Phase (v1.1):**

| Phase | Plans | Duration |
|-------|-------|----------|
| Phase 05 P01 | 3min | 2 tasks |
| Phase 05 P02 | 2min | 2 tasks |
| Phase 06 P01 | 5min | 2 tasks |
| Phase 07 P01 | 3min | 2 tasks |
| Phase 08 P01 | 2min | 2 tasks |
| Phase 08 P02 | 2min | 2 tasks |
| Phase 09 P01 | 1min | 2 tasks |
| Phase 10 P01 | 4min | 2 tasks |
| Phase 10 P02 | 3min | 2 tasks |
| Phase 11-android-shell-navigation P01 | 5 | 2 tasks | 12 files |
| Phase 12-exercise-catalog-templates P01 | 10 | 2 tasks | 5 files |
| Phase 12-exercise-catalog-templates P02 | 4 | 2 tasks | 5 files |
| Phase 13-workout-session-core P01 | 2 | 1 tasks | 1 files |
| Phase 13-workout-session-core P02 | 3 | 2 tasks | 2 files |
| Phase 13-workout-session-core P03 | 105 | 2 tasks | 2 files |
| Phase 13-workout-session-core P04 | 3 | 2 tasks | 1 files |
| Phase 14-history-settings-anatomy P01 | 5 | 2 tasks | 6 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
See PROJECT.md for full decision history across v1.0, v1.1, v1.5, and post-v1.5.

- [Phase 11-android-shell-navigation]: compileSdk bumped to 36: Compose BOM 2025.06.00 requires API 36
- [Phase 11-android-shell-navigation]: initKoin() accepts KoinApplication lambda to enable androidContext() before module loading
- [Phase 11-android-shell-navigation]: KMP v2 source layout: src/androidMain/ required by KMP Gradle plugin in Kotlin 2.3
- [Phase 11-android-shell-navigation]: Compose BOM placed in top-level dependencies{} block: platform() unavailable in KMP sourceSets block
- [Phase 12-exercise-catalog-templates]: collectAsState() used over collectAsStateWithLifecycle() - lifecycle-runtime-compose not in explicit androidApp deps
- [Phase 12-exercise-catalog-templates]: ExerciseDetailRoute.exerciseId fixed from Long to String to match Exercise.id domain type
- [Phase 12-exercise-catalog-templates]: Anatomy picker (Canvas body drawing) deferred to Phase 14 - muscle group uses ExposedDropdownMenuBox
- [Phase 12-exercise-catalog-templates]: Move-up/down buttons for exercise reorder: avoids reorderable library dependency for prototype scope; calls viewModel.moveExercise() with existing ViewModel logic
- [Phase 12-exercise-catalog-templates]: koinViewModel(viewModelStoreOwner = parentEntry) in ExercisePickerRoute: shares same TemplateEditorViewModel instance for direct addExercise() call across screens
- [Phase 13-workout-session-core]: LazyColumn + SnapFlingBehavior for drum picker: gives iOS-equivalent fling physics without Canvas draw complexity
- [Phase 13-workout-session-core]: ActiveWorkoutContent extracted as private composable with all state as parameters to keep sub-composables pure and avoid ViewModel re-injection
- [Phase 13-workout-session-core]: ExerciseOverviewSheetContent is pure content composable without ModalBottomSheet wrapper — caller owns sheet lifecycle for testability
- [Phase 13-workout-session-core]: onJumpToExercise and onReorderExercise threaded through ActiveWorkoutContent parameter list from WorkoutSessionScreen
- [Phase 13-workout-session-core]: Edit sheet state hoisted to WorkoutSessionScreen level: both Active and Reviewing branches share EditSetSheetContent without duplication
- [Phase 13-workout-session-core]: CompletedSetsSection extended with exerciseIndex + onEditSet callback (default no-op): backward-compatible tap-to-edit wiring
- [Phase 14-history-settings-anatomy]: WorkoutHistoryDetailScreen uses DisposableEffect onDispose to call clearDetail() — ensures stale detail is not shown on re-navigation
- [Post-v1.5 (untracked)]: MuscleRegionPaths moved from iOS-only to commonMain/domain/model for cross-platform anatomy reuse
- [Post-v1.5 (untracked)]: composeApp migrated to `android-kmp-library` plugin; androidApp extracted as separate module (enables per-module dependency graphs)
- [Post-v1.5 (untracked)]: iOS theming uses `ThemeManager.shared` (@Observable) + `Color.appAccent` computed extension; observed from `AppRootView` via `withTaskGroup` on two flows
- [Post-v1.5 (untracked)]: NutritionGoals defaults chosen as 2500 kcal / 150g protein / 80g fat / 300g carbs / 50g sugar; persisted as string-encoded ints in DataStore
- [Post-v1.5 (untracked)]: OpenFoodFacts barcode lookup via Ktor CIO client; no local caching (network required per scan)

### Roadmap Evolution

- Phase 15 added (2026-04-22): Gamifikation lokal — XP, Achievements, Meilensteine, CSGO-Style Ranks (F4 from Lastenheft). Added outside an active milestone — assign to a new milestone before planning.

### Pending Todos

None.

### Blockers/Concerns

- GSD history has a gap: nutrition + theming shipped without phase artifacts. If future work needs to reference how those features were built, `git log` is authoritative — not `.planning/phases/`.

## Session Continuity

Last session: 2026-04-22 (Phase 15 context gathered)
Stopped at: Phase 15 context gathered
Resume file: .planning/phases/15-gamifikation-lokal-xp-achievements-meilensteine-csgo-style-r/15-CONTEXT.md
Next step: `/gsd-plan-phase 15` to break Phase 15 into plans
