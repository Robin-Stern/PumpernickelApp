---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
stopped_at: Completed 15-11-PLAN.md — iOS gamification UI fully wired, xcodebuild passes
last_updated: "2026-04-23T18:40:43.853Z"
last_activity: 2026-04-23
progress:
  total_phases: 2
  completed_phases: 2
  total_plans: 15
  completed_plans: 15
  percent: 100
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-22)

**Core value:** Users can select a workout template and execute it set-by-set — logging reps, weight, and rest periods — with a clean, reliable flow
**Current focus:** Phase 15.1 — ranks-and-achievements-browser

## Current Position

Milestone: v1.5 (shipped 2026-03-31)
Phase: 15.1
Plan: Not started
Branch: `feature/workouts` @ `985884c`
Status: Executing Phase 15.1 (human UAT pending)
Last activity: 2026-04-23

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

- Total plans completed: 25 (12 v1.0 + 9 v1.1)
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
| Phase 15 P10 | 2 | 3 tasks | 4 files |
| Phase 15 P11 | 8 | 3 tasks | 5 files |

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
- [Phase 15]: OverviewRankStrip is passive (let rankState) — OverviewView remains single subscription owner per existing pattern
- [Phase 15]: UnlockModalView typealias uses flat Shared.UnlockEventRankPromotion — matches KMP-NativeCoroutines 1.0.2 flat export convention
- [Phase 15]: unlockEvents (not unlockEventsFlow) is the correct KMPNativeCoroutines property name — KMP-Native generates the Swift property name from the Kotlin val name directly
- [Phase 15]: KMP-Native sealed subclasses are nested Swift types (Shared.UnlockEvent.RankPromotion) not flat names — use swift_name attribute in Shared.h to determine correct access path

### Roadmap Evolution

- Phase 15 added (2026-04-22): Gamifikation lokal — XP, Achievements, Meilensteine, CSGO-Style Ranks (F4 from Lastenheft). Added outside an active milestone — assign to a new milestone before planning.
- Phase 15.1 inserted after Phase 15 (2026-04-23): Ranks & Achievements Browser — rank ladder + achievement catalog UI (URGENT). Surfaced by Phase 15 UAT — current gamification is opaque: users see their rank and unlock toasts but cannot browse what tiers / achievements exist. Pure presentation over existing GamificationRepository / RankLadder / AchievementCatalog — no new domain logic.
- Phase 16 added (2026-04-28): Set nutrition goals (kcal/protein/carbs/fat per day) — surface progress on Overview tab and award bonus XP when daily goal achieved within tolerance. Builds on existing post-v1.5 NutritionGoals model (in SettingsRepository) + Phase 15 gamification engine; user mentions a ±5–10% tolerance + XP reward already partially in code — planner should investigate first.

### Pending Todos

None.

### Blockers/Concerns

- GSD history has a gap: nutrition + theming shipped without phase artifacts. If future work needs to reference how those features were built, `git log` is authoritative — not `.planning/phases/`.

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 260423-sja | Clean up AchievementGalleryScreen category headers — M3 section-break hierarchy (titleLarge + 20/8 padding + hairline divider) | 2026-04-23 | 4c0740b | [260423-sja-clean-up-achievementgalleryscreen-catego](./quick/260423-sja-clean-up-achievementgalleryscreen-catego/) |

## Session Continuity

Last session: 2026-04-22T19:21:16.409Z
Stopped at: Completed 15-11-PLAN.md — iOS gamification UI fully wired, xcodebuild passes
Resume file: None
Next step: `/gsd-plan-phase 15` to break Phase 15 into plans
