---
phase: 18-ai-features-f6-workout-generation-f8-meal-generation-byok-op
plan: 01
subsystem: database
tags: [room, kotlin-multiplatform, schema-migration, automigration, provenance]

# Dependency graph
requires: []
provides:
  - Room schema v10 with AutoMigration(9, 10) registered
  - Nullable source: String? column on WorkoutTemplateEntity, ExerciseEntity, RecipeEntity, FoodEntity
  - source field propagated through WorkoutTemplate, Exercise, Recipe, Food domain models
  - entity-to-domain mapper pass-through for source in WorkoutTemplate + Exercise
  - ExerciseRepository.createExercise wired to pass exercise.source through
  - FoodRepositoryImpl fully wired: toEntity/toDomain/saveRecipe/updateRecipe/loadRecipes all carry source
affects:
  - 18-02 onwards (AI authoring plans that write source = "AI")
  - All plans that construct Exercise, Food, Recipe, or WorkoutTemplate objects

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Room AutoMigration additive nullable column: Kotlin default = null causes Room to emit nullable column in AutoMigration DDL — no MigrationSpec needed"
    - "source: String? = null provenance pattern: null treated as USER on read, AI-authored entries tagged as 'AI'"

key-files:
  created: []
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/db/WorkoutTemplateEntity.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/db/ExerciseEntity.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/db/RecipeEntity.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/db/FoodEntity.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/model/WorkoutTemplate.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Exercise.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Recipe.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Food.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ExerciseRepository.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/FoodRepositoryImpl.kt

key-decisions:
  - "AutoMigration(9, 10) requires no MigrationSpec — additive nullable column is handled by Room automatically (same pattern as 8->9)"
  - "source column stored as String? (not enum) — avoids schema lock-in; null means USER on read, 'AI' for AI-authored"
  - "FoodRepositoryImpl updated at all 5 call sites (toEntity, toDomain, saveRecipe, updateRecipe, loadRecipes) to ensure round-trip fidelity"

patterns-established:
  - "D-18-11 provenance pattern: source: String? = null on entity + domain model, pass-through in all mappers"

requirements-completed: [REQ-AI-01, REQ-AI-04]

# Metrics
duration: 2min
completed: 2026-05-07
---

# Phase 18 Plan 01: Room v9 to v10 Source Column Summary

**Room schema bumped to v10 via AutoMigration(9, 10) — nullable `source: String?` provenance column added to 4 entities and propagated end-to-end through domain models and all mapper/repository call sites**

## Performance

- **Duration:** 2 min
- **Started:** 2026-05-07T17:04:21Z
- **Completed:** 2026-05-07T17:06:23Z
- **Tasks:** 3
- **Files modified:** 11

## Accomplishments

- Added `val source: String? = null` to WorkoutTemplateEntity, ExerciseEntity, RecipeEntity, FoodEntity — AutoMigration(9, 10) registered in AppDatabase (version = 10)
- Propagated source field through all 4 domain models (WorkoutTemplate, Exercise, Recipe, Food) with toDomain() pass-through on WorkoutTemplate and Exercise
- Wired source end-to-end in ExerciseRepository.createExercise and all 5 FoodRepositoryImpl construction/mapping call sites

## Task Commits

Each task was committed atomically:

1. **Task 1: Add nullable source column to all 4 entities** - `c248fb2` (feat)
2. **Task 2: Bump AppDatabase to version 10 with AutoMigration(9,10)** - `00f2d28` (feat)
3. **Task 3: Propagate source field through domain models + mappers + repositories** - `2e8ddf9` (feat)

## Files Created/Modified

- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt` - version = 10, AutoMigration(9, 10) added
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/WorkoutTemplateEntity.kt` - source: String? = null appended
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/ExerciseEntity.kt` - source: String? = null appended
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/RecipeEntity.kt` - source: String? = null appended
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/FoodEntity.kt` - source: String? = null appended
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/WorkoutTemplate.kt` - source field + toDomain() pass-through
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Exercise.kt` - source field + toDomain() pass-through
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Recipe.kt` - source field added
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Food.kt` - source field added
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ExerciseRepository.kt` - createExercise passes source = exercise.source
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/FoodRepositoryImpl.kt` - toEntity, toDomain, saveRecipe, updateRecipe, loadRecipes all wire source

## Decisions Made

- AutoMigration(9, 10) requires no MigrationSpec — Room handles additive nullable columns automatically (same pattern as 8→9 which added progress_picture_path to active session entities). No hand-rolled SQL needed.
- source stored as `String?` not an enum — avoids KSP/Room enum complexity; `null` means USER on read, `"AI"` for AI-authored entries. Downstream code interprets at read time per D-18-11.
- FoodRepositoryImpl updated at all 5 call sites (not just toDomain) so round-trip write/read fidelity is guaranteed from the start.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- Schema v10 is stable. All downstream AI authoring plans (18-02 onwards) can now write `source = "AI"` to tag AI-generated exercises, foods, recipes, and workout templates without further schema work.
- Existing v9 databases will be migrated automatically on first launch — existing rows receive `source = null`, which is treated as USER on read.

## Self-Check

- [x] AppDatabase.kt contains `version = 10` and `AutoMigration(from = 9, to = 10)`: verified
- [x] All 4 entity files contain `val source: String? = null`: verified
- [x] All 4 domain model files contain `val source: String? = null`: verified
- [x] ExerciseRepository.kt contains `source = exercise.source`: verified
- [x] FoodRepositoryImpl.kt contains 5 `source =` assignments: verified
- [x] Commits c248fb2, 00f2d28, 2e8ddf9 exist in git log: verified

## Self-Check: PASSED

---
*Phase: 18-ai-features-f6-workout-generation-f8-meal-generation-byok-op*
*Completed: 2026-05-07*
