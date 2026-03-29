---
phase: 02-template-management
plan: 01
subsystem: database
tags: [room, kmp, dao, repository, koin, workout-templates]

# Dependency graph
requires:
  - phase: 01-foundation-exercise-catalog
    provides: ExerciseEntity, ExerciseDao, ExerciseRepository, AppDatabase v1, Koin SharedModule
provides:
  - WorkoutTemplateEntity and TemplateExerciseEntity Room entities (DB v2)
  - WorkoutTemplateDao with Flow queries and suspend mutations
  - TemplateRepository with full CRUD and exercise name resolution
  - WorkoutTemplate and TemplateExercise domain models with toDomain mappers
  - formatWeightKg and parseWeightKgX10 weight display helpers
  - Koin DI wiring for WorkoutTemplateDao and TemplateRepository
affects: [02-02-template-viewmodels, 02-03-template-ios-ui, 03-workout-session]

# Tech tracking
tech-stack:
  added: []
  patterns: [ForeignKey CASCADE for parent-child entity relationships, repository-level exercise name resolution via ExerciseRepository, fallbackToDestructiveMigration for schema upgrades]

key-files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/data/db/WorkoutTemplateEntity.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/db/TemplateExerciseEntity.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/db/WorkoutTemplateDao.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/TemplateRepository.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/model/WorkoutTemplate.kt
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt

key-decisions:
  - "No @Transaction on DAO methods with function bodies -- KMP-safe for iOS target; batch reorder handled in TemplateRepositoryImpl"
  - "Exercise name resolution at repository level via ExerciseRepository.getExerciseById().first() -- ensures real display names, not placeholder strings"
  - "fallbackToDestructiveMigration(dropAllTables = true) for v1-to-v2 migration -- acceptable for prototype, seeder re-populates exercises"

patterns-established:
  - "ForeignKey CASCADE pattern: child entities auto-delete when parent removed"
  - "Repository-level name resolution: resolve IDs to display names at repository layer, not in DAO or ViewModel"
  - "Weight as Int x10: store kg * 10 as Int to avoid floating-point issues (D-06 gymtracker pattern)"

requirements-completed: [TMPL-01, TMPL-02, TMPL-03, TMPL-04, TMPL-05]

# Metrics
duration: 8min
completed: 2026-03-28
---

# Phase 2 Plan 1: Template Data Layer Summary

**Room entities, DAO, and repository for workout template CRUD with exercise name resolution via ExerciseRepository**

## Performance

- **Duration:** 8 min
- **Started:** 2026-03-28T20:25:59Z
- **Completed:** 2026-03-28T20:33:50Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments
- Complete data layer for workout template management with two Room entities, DAO, repository, and domain models
- Exercise name resolution at repository level ensures TemplateExercise always carries real exercise names and muscle groups
- Database bumped to version 2 with fallbackToDestructiveMigration for clean schema upgrades
- All Koin DI wiring in place -- WorkoutTemplateDao and TemplateRepository registered as singletons

## Task Commits

Each task was committed atomically:

1. **Task 1: Room entities, DAO, domain models, and toDomain mappers** - `e7847b4` (feat)
2. **Task 2: TemplateRepository, Koin DI wiring, and fallbackToDestructiveMigration** - `4171a39` (feat)

## Files Created/Modified
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/WorkoutTemplateEntity.kt` - Room entity for workout_templates table (Long PK, name, timestamps)
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/TemplateExerciseEntity.kt` - Room entity for template_exercises with ForeignKey CASCADE to templates
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/WorkoutTemplateDao.kt` - DAO with 3 Flow queries and 8 suspend mutations, no @Transaction
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt` - Bumped to version 2, registered new entities and workoutTemplateDao()
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/WorkoutTemplate.kt` - Domain models + toDomain mappers + weight formatting helpers
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/TemplateRepository.kt` - Interface + TemplateRepositoryImpl with full CRUD and name resolution
- `shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt` - Added WorkoutTemplateDao, TemplateRepository, fallbackToDestructiveMigration

## Decisions Made
- No @Transaction on DAO methods with function bodies -- avoids Room KMP compilation issues on iOS target; batch reorder logic lives in TemplateRepositoryImpl instead
- Exercise name resolution happens at repository level via ExerciseRepository.getExerciseById().first() -- guarantees real display names flow to ViewModels and UI
- Used fallbackToDestructiveMigration(dropAllTables = true) for v1-to-v2 migration -- acceptable for prototype since DatabaseSeeder re-populates exercise data on next launch

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Known Stubs
None - all data paths are fully wired with real implementations.

## Next Phase Readiness
- Data layer complete and compiling for iOS simulator -- ready for Plan 02 (TemplateListViewModel + TemplateEditorViewModel)
- TemplateRepository interface provides all operations needed by ViewModels
- Domain models (WorkoutTemplate, TemplateExercise) with toDomain mappers ready for UI consumption

## Self-Check: PASSED

All 7 created/modified files verified to exist. Both task commits (e7847b4, 4171a39) verified in git log. Build compiles successfully for iOS simulator.

---
*Phase: 02-template-management*
*Completed: 2026-03-28*
