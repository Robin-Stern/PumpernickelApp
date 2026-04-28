---
phase: 16-set-nutrition-goals-kcal-protein-carbs-fat-per-day-surface-p
plan: "03"
subsystem: database
tags: [datastore, kotlin, kmp, settings-repository, user-physical-stats, nutrition-goals]

# Dependency graph
requires:
  - phase: 16-set-nutrition-goals-kcal-protein-carbs-fat-per-day-surface-p
    provides: "UserPhysicalStats domain model (UserPhysicalStats.kt, Sex enum, ActivityLevel enum) from plan 16-01"
provides:
  - "SettingsRepository.userPhysicalStats: Flow<UserPhysicalStats?> — null when any key missing, populated once all 5 saved"
  - "SettingsRepository.setUserPhysicalStats(stats) — atomic write of 5 DataStore string keys"
  - "SettingsRepository.nutritionGoalsBannerDismissed: Flow<Boolean> — default false, persisted"
  - "SettingsRepository.setNutritionGoalsBannerDismissed(dismissed) — banner state setter"
affects:
  - "16-04 OverviewViewModel — will bind to userPhysicalStats + nutritionGoalsBannerDismissed Flows"
  - "16-05 NutritionGoalsEditorViewModel — will call setUserPhysicalStats + setNutritionGoalsBannerDismissed"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Multi-key combine() Flow with nullable emit: combine 5 dataStore.data.map{} Flows; if ANY is null emit null (D-16-11)"
    - "Enum-as-String DataStore persistence: stored as .name, recovered with runCatching { enumValueOf<T>(it) }.getOrNull()"
    - "Atomic multi-field DataStore write: all 5 physical-stat keys written inside single dataStore.edit{} block"

key-files:
  created: []
  modified:
    - "shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt"

key-decisions:
  - "D-16-10: Five new stringPreferencesKey values (user_weight_kg, user_height_cm, user_age, user_sex, user_activity_level); no Room schema change"
  - "D-16-11: userPhysicalStats Flow returns null when ANY required key missing — calculator opens with placeholders, not defaults"
  - "D-16-13/14: nutrition_goals_banner_dismissed booleanPreferencesKey, default false, two dismissal triggers owned by ViewModel layer"
  - "runCatching on enumValueOf ensures corrupted/renamed enum values degrade gracefully to null rather than crashing the Flow"

patterns-established:
  - "Null-on-any-missing combine pattern: 5-Flow combine that returns null if any source is null — mirrors NutritionGoals combine but without defaults"
  - "Boolean sentinel via booleanPreferencesKey: mirrors retroactiveApplied pattern exactly (line-for-line shape)"

requirements-completed: []

# Metrics
duration: 12min
completed: 2026-04-28
---

# Phase 16 Plan 03: SettingsRepository Physical Stats + Banner Persistence Summary

**Five new DataStore string keys for UserPhysicalStats persistence plus a Boolean banner-dismissed sentinel, wired as Flow<UserPhysicalStats?> (null on first launch) and Flow<Boolean> (default false)**

## Performance

- **Duration:** 12 min
- **Started:** 2026-04-28T00:00:00Z
- **Completed:** 2026-04-28T00:12:00Z
- **Tasks:** 2
- **Files modified:** 1

## Accomplishments

- Added `userPhysicalStats: Flow<UserPhysicalStats?>` using `combine()` of 5 DataStore keys; emits `null` when any key absent (D-16-11)
- Added `setUserPhysicalStats(stats)` writing all 5 keys atomically in a single `edit{}` block; enums stored as `.name`, parsed with `runCatching { enumValueOf<T>() }.getOrNull()`
- Added `nutritionGoalsBannerDismissed: Flow<Boolean>` defaulting `false` and `setNutritionGoalsBannerDismissed(dismissed)` mirroring the existing `retroactiveApplied` shape exactly

## Task Commits

Each task was committed atomically:

1. **Task 1: Add user_physical_stats keys + Flow + setter** - `564fc8e` (feat)
2. **Task 2: Add nutrition_goals_banner_dismissed Boolean key + Flow + setter** - `564fc8e` (feat — committed together with Task 1 in same file)

## Files Created/Modified

- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt` — 60 additive lines: 3 new imports, 6 new key declarations, `userPhysicalStats` Flow + setter, `nutritionGoalsBannerDismissed` Flow + setter

## Decisions Made

- Both tasks committed in a single commit since they affect the same file and are independently verifiable via acceptance criteria
- Chose `runCatching { enumValueOf<T>(it) }.getOrNull()` for enum recovery (plan spec) — ensures future enum renames degrade to null Flow emission, not a crash
- Key declarations for both Task 1 and Task 2 placed in the key-declaration block together for readability

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. Both iOS Simulator Arm64 and Android debug targets compiled with zero errors (pre-existing warnings only, unrelated to this plan).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `SettingsRepository` now exposes all 6 new public symbols required by Phase 16 plans 04 and 05
- `OverviewViewModel` (plan 16-04) can bind `userPhysicalStats` and `nutritionGoalsBannerDismissed` as `StateFlow`s immediately
- `NutritionGoalsEditorViewModel` (plan 16-05) can call `setUserPhysicalStats` and `setNutritionGoalsBannerDismissed` without any further data-layer work

---
*Phase: 16-set-nutrition-goals-kcal-protein-carbs-fat-per-day-surface-p*
*Completed: 2026-04-28*
