---
phase: 16-set-nutrition-goals-kcal-protein-carbs-fat-per-day-surface-p
plan: "04"
subsystem: shared/presentation
tags: [viewmodel, stateflow, nutrition, datastore, ios-interop]
dependency_graph:
  requires: ["16-01", "16-03"]
  provides: ["OverviewViewModel.userPhysicalStats", "OverviewViewModel.nutritionGoalsBannerVisible", "OverviewViewModel.updateUserPhysicalStats", "OverviewViewModel.dismissBanner"]
  affects: ["16-05-android-overview-banner", "16-06-ios-overview-banner"]
tech_stack:
  added: []
  patterns: ["@NativeCoroutinesState + stateIn(WhileSubscribed(5000))", "viewModelScope.launch delegation", "Flow.map { !it } for boolean negation"]
key_files:
  created: []
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/overview/OverviewViewModel.kt
decisions:
  - "D-16-01: OverviewViewModel is the single VM for the Overview tab — no separate editor VM"
  - "D-16-13: nutritionGoalsBannerVisible starts true (negation of dismissed flag, default false)"
  - "D-16-14: updateNutritionGoals chains setNutritionGoalsBannerDismissed(true) on every save"
  - "D-16-11: userPhysicalStats is null until first save — null propagates to UI as placeholder signal"
metrics:
  duration: "~8 minutes"
  completed: "2026-04-28"
  tasks_completed: 2
  files_changed: 1
---

# Phase 16 Plan 04: OverviewViewModel Phase-16 Accessors Summary

Wire Phase 16 persistence accessors through `OverviewViewModel` — two new `@NativeCoroutinesState` StateFlows (`userPhysicalStats`, `nutritionGoalsBannerVisible`) plus two new setter methods (`updateUserPhysicalStats`, `dismissBanner`) with `updateNutritionGoals` chained to also dismiss the banner on every save (D-16-14).

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Expose userPhysicalStats + nutritionGoalsBannerVisible StateFlows | 53d0b87 | OverviewViewModel.kt |
| 2 | Add updateUserPhysicalStats + dismissBanner; chain dismissBanner in updateNutritionGoals | af0c050 | OverviewViewModel.kt |

## Diff Summary (OverviewViewModel.kt)

### New imports added (Task 1)
```kotlin
import com.pumpernickel.domain.model.UserPhysicalStats
import kotlinx.coroutines.flow.map
```

### New StateFlows added after `rankState` (Task 1)
```kotlin
@NativeCoroutinesState
val userPhysicalStats: StateFlow<UserPhysicalStats?> = settingsRepository
    .userPhysicalStats
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

@NativeCoroutinesState
val nutritionGoalsBannerVisible: StateFlow<Boolean> = settingsRepository
    .nutritionGoalsBannerDismissed
    .map { !it }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
```

### Modified updateNutritionGoals body (Task 2 — D-16-14)
Added `settingsRepository.setNutritionGoalsBannerDismissed(true)` after `setNutritionGoals(goals)` call.

### New methods added before `today()` (Task 2)
```kotlin
fun updateUserPhysicalStats(stats: UserPhysicalStats) { ... }
fun dismissBanner() { ... }
```

## iOS Bridge Property Names

Per the `@NativeCoroutinesState` + KMP-NativeCoroutines convention, the generated iOS property names are:
- `userPhysicalStats` -> `userPhysicalStatsFlow` (Swift async sequence)
- `nutritionGoalsBannerVisible` -> `nutritionGoalsBannerVisibleFlow` (Swift async sequence)

These are consumed as:
```swift
for try await stats in asyncSequence(for: viewModel.userPhysicalStatsFlow) { ... }
for try await visible in asyncSequence(for: viewModel.nutritionGoalsBannerVisibleFlow) { ... }
```

## Verification

- `grep -c "@NativeCoroutinesState" OverviewViewModel.kt` = **5** (was 3: uiState, nutritionGoals, rankState; now +2: userPhysicalStats, nutritionGoalsBannerVisible)
- `grep -c "setNutritionGoalsBannerDismissed(true)" OverviewViewModel.kt` = **2** (inside `updateNutritionGoals` + inside `dismissBanner`)
- `./gradlew :shared:compileKotlinIosSimulatorArm64` exits 0
- `./gradlew :shared:compileDebugKotlinAndroid` exits 0
- Diff shows ONLY the 2 new imports, 2 new StateFlows, 1 modified method body, 2 new methods — no unrelated edits

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — all StateFlows delegate to real `SettingsRepository` accessors provided by Plan 03. No hardcoded values or placeholder data.

## Self-Check: PASSED

- [x] `OverviewViewModel.kt` exists and is modified: FOUND
- [x] Commit 53d0b87 exists: FOUND
- [x] Commit af0c050 exists: FOUND
- [x] `@NativeCoroutinesState` count = 5: PASSED
- [x] Both targets compile: PASSED
