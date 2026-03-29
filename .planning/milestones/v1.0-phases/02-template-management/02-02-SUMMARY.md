---
phase: 02-template-management
plan: 02
subsystem: presentation
tags: [viewmodel, koin, stateflow, nativecoroutines, kmp, ios]

# Dependency graph
requires:
  - phase: 02-01
    provides: "TemplateRepository, WorkoutTemplate/TemplateExercise domain models, WorkoutTemplateDao"
provides:
  - "TemplateListViewModel with reactive template list and delete"
  - "TemplateEditorViewModel with create/edit modes, exercise CRUD, reorder, save"
  - "Koin DI registrations for both template ViewModels"
  - "KoinHelper iOS getters for SwiftUI access"
affects: [02-03-template-ui]

# Tech tracking
tech-stack:
  added: []
  patterns: ["ViewModel with dual-mode (create/edit) state management", "In-memory exercise list for create mode, immediate persistence for edit mode", "D-08 default exercise targets (3 sets, 10 reps, 0 weight, 90s rest)"]

key-files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/templates/TemplateListViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/templates/TemplateEditorViewModel.kt
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/di/KoinHelper.kt

key-decisions:
  - "TemplateEditorViewModel takes both TemplateRepository and ExerciseRepository as constructor params for consistency and future flexibility"
  - "Create mode uses in-memory exercise list saved atomically; edit mode persists each change immediately"
  - "Negative temp IDs via kotlin.time.Clock.System for unsaved exercises in create mode"

patterns-established:
  - "Dual-mode ViewModel: create (in-memory) vs edit (immediate persist) with shared UI state"
  - "SaveResult sealed class pattern for communicating async outcomes to UI"
  - "moveExercise normalizes exerciseOrder indices after reorder to prevent gaps"

requirements-completed: [TMPL-01, TMPL-02, TMPL-03, TMPL-04, TMPL-05]

# Metrics
duration: 2min
completed: 2026-03-28
---

# Phase 02 Plan 02: Template ViewModels Summary

**TemplateListViewModel and TemplateEditorViewModel with full CRUD state management, Koin DI wiring, and KoinHelper iOS getters**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-28T20:37:54Z
- **Completed:** 2026-03-28T20:39:57Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- TemplateListViewModel exposes reactive template list via @NativeCoroutinesState StateFlow with delete capability
- TemplateEditorViewModel supports both create and edit modes with full exercise management (add, remove, update targets, reorder)
- Koin DI wires both ViewModels with correct dependencies (TemplateEditorViewModel gets two repos)
- KoinHelper exposes both ViewModels for SwiftUI consumption via KoinPlatform.getKoin().get()

## Task Commits

Each task was committed atomically:

1. **Task 1: TemplateListViewModel and TemplateEditorViewModel** - `c4cce73` (feat)
2. **Task 2: Koin DI registration and KoinHelper iOS getters** - `cf03703` (feat)

## Files Created/Modified
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/templates/TemplateListViewModel.kt` - Reactive template list with @NativeCoroutinesState and delete action
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/templates/TemplateEditorViewModel.kt` - Create/edit state management with exercise CRUD, reorder, validation, and save
- `shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt` - Added TemplateListViewModel and TemplateEditorViewModel Koin registrations
- `shared/src/iosMain/kotlin/com/pumpernickel/di/KoinHelper.kt` - Added getTemplateListViewModel() and getTemplateEditorViewModel() for iOS

## Decisions Made
- TemplateEditorViewModel constructor takes both TemplateRepository and ExerciseRepository for consistency and future flexibility, even though edit mode resolves names at the repository layer
- Create mode holds exercises in-memory with negative temp IDs, saving atomically on save(); edit mode persists each change immediately per research recommendation
- isFormValid uses combine of name + exercises flows with Eagerly start policy for instant validation feedback

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required

None - no external service configuration required.

## Known Stubs

None - all ViewModels are fully wired to real repository data sources.

## Next Phase Readiness
- Both template ViewModels ready for SwiftUI consumption via KoinHelper
- Plan 03 (template UI) can directly observe TemplateListViewModel.templates and use TemplateEditorViewModel for create/edit flows
- Full dependency chain verified: DAO -> Repository -> ViewModel -> KoinHelper -> SwiftUI

## Self-Check: PASSED

All 4 created/modified files verified on disk. Both task commits (c4cce73, cf03703) verified in git log.

---
*Phase: 02-template-management*
*Completed: 2026-03-28*
