---
phase: 12-exercise-catalog-templates
plan: 02
subsystem: ui
tags: [jetpack-compose, material3, navigation, viewmodel, koin, swipe-dismiss, template-management]

# Dependency graph
requires:
  - phase: 12-exercise-catalog-templates-plan-01
    provides: ExerciseCatalogScreen, ExerciseDetailScreen, CreateExerciseScreen, Routes, Koin modules for ViewModels
  - phase: 11-android-shell-navigation
    provides: MainScreen NavHost structure, Material 3 theme, PlaceholderScreen

provides:
  - TemplateListScreen: template list with TopAppBar, FAB, SwipeToDismissBox delete, start workout button
  - WorkoutEmptyStateScreen: empty state with FitnessCenter icon, title, description, Create Template button
  - TemplateEditorScreen: name OutlinedTextField, exercise list with inline target editing, move-up/down reorder, delete
  - ExercisePickerScreen: search field, muscle group FilterChip row, tap-to-select with single-select auto-dismiss
  - MainScreen wired with all 7 workout tab routes (TemplateList, TemplateEditor, ExercisePicker, WorkoutSession, ExerciseCatalog, ExerciseDetail, CreateExercise)
  - WorkoutPlaceholderScreen replaced by TemplateListScreen as workout tab root

affects: [13-workout-session, 14-history-settings-anatomy]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Parent back stack entry ViewModel sharing: koinViewModel(viewModelStoreOwner = parentEntry) for ExercisePicker -> TemplateEditor communication"
    - "SwipeToDismissBox with confirmValueChange = false to hold for AlertDialog confirmation before actual dismissal"
    - "Move-up/move-down IconButtons as pragmatic reorder alternative to drag-and-drop in LazyColumn"
    - "CompactTargetField composable for small labeled OutlinedTextField columns"

key-files:
  created:
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/TemplateListScreen.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutEmptyStateScreen.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/TemplateEditorScreen.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ExercisePickerScreen.kt
  modified:
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt

key-decisions:
  - "Move-up/down buttons used for exercise reorder instead of drag-and-drop: Compose LazyColumn lacks built-in drag reorder; avoids external reorderable library dependency for prototype scope"
  - "SwipeToDismissBox confirmValueChange returns false: holds swiped state visually while AlertDialog is shown, reset only after user cancels"
  - "ExercisePickerScreen reuses ExerciseCatalogViewModel via koinViewModel(): same search/filter logic, separate ViewModel instance per navigation entry"
  - "koinViewModel(viewModelStoreOwner = parentEntry) for ExercisePicker: gets same TemplateEditorViewModel instance that launched the picker, enabling direct addExercise() call"

patterns-established:
  - "CompactTargetField: labeled OutlinedTextField column pattern for inline numeric editing in exercise rows"
  - "LaunchedEffect(saveResult) pattern: observe SaveResult sealed class, pop back on success, show Snackbar on error, always clearSaveResult()"

requirements-completed: [ANDROID-04]

# Metrics
duration: 4min
completed: 2026-03-31
---

# Phase 12 Plan 02: Template Management Screens Summary

**Material 3 Compose template CRUD screens (list with swipe-delete, editor with inline targets, exercise picker with search/filter) replacing WorkoutPlaceholderScreen as the functional workout tab root**

## Performance

- **Duration:** ~4 min
- **Started:** 2026-03-31T17:14:18Z
- **Completed:** 2026-03-31T17:17:38Z
- **Tasks:** 2
- **Files modified:** 5 (4 created, 1 modified)

## Accomplishments
- Workout tab is now functional: TemplateListScreen replaces placeholder as NavHost root
- Full template CRUD: create via FAB/empty-state button, edit via tap, delete via swipe + confirm dialog
- TemplateEditorScreen with name field, exercise list, inline set/reps/weight/rest targets, move-up/down reorder, per-exercise delete
- ExercisePickerScreen with search, FilterChip muscle group filtering, tap-to-add single-select with immediate return to editor
- ExercisePicker -> TemplateEditor ViewModel sharing via parent back stack entry; addExercise() called directly

## Task Commits

1. **Task 1: TemplateListScreen with swipe-delete, FAB, and WorkoutEmptyStateScreen** - `7a31fe1` (feat)
2. **Task 2: TemplateEditorScreen, ExercisePickerScreen, NavHost wiring** - `49f6f6c` (feat)

## Files Created/Modified
- `androidApp/.../screens/WorkoutEmptyStateScreen.kt` - Empty state with FitnessCenter icon and Create Template button
- `androidApp/.../screens/TemplateListScreen.kt` - Template list with SwipeToDismissBox delete, FAB, ListItem rows
- `androidApp/.../screens/TemplateEditorScreen.kt` - Editor with OutlinedTextField name, exercise items with CompactTargetField row, move-up/down reorder
- `androidApp/.../screens/ExercisePickerScreen.kt` - Picker with search, FilterChip row, tap-to-select ListItems
- `androidApp/.../navigation/MainScreen.kt` - All 7 workout tab routes wired; WorkoutPlaceholderScreen removed

## Decisions Made
- **Move-up/down buttons for reorder** instead of drag-and-drop: Compose LazyColumn has no built-in drag reorder; avoids adding `reorderable` library for a prototype. Calls `viewModel.moveExercise(index, index-1)` / `viewModel.moveExercise(index, index+2)` using existing ViewModel logic.
- **SwipeToDismissBox confirmValueChange returns false**: holds visual swipe state while AlertDialog is shown; dismissState.reset() is called only when user cancels.
- **ExercisePicker reuses ExerciseCatalogViewModel**: same search/filter behavior; separate VM instance per navigation entry.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Workout tab is fully functional with template CRUD
- WorkoutSessionRoute shows placeholder — Phase 13 implements the full workout session screen
- TemplateEditorViewModel.moveExercise() uses toOffset semantics as documented; Android reorder buttons call this correctly
- All 7 NavHost routes are registered; Phase 13 only needs to replace the WorkoutSessionRoute placeholder

---
*Phase: 12-exercise-catalog-templates*
*Completed: 2026-03-31*

## Self-Check: PASSED

- FOUND: WorkoutEmptyStateScreen.kt
- FOUND: TemplateListScreen.kt
- FOUND: TemplateEditorScreen.kt
- FOUND: ExercisePickerScreen.kt
- FOUND: MainScreen.kt (modified)
- FOUND: 7a31fe1 (Task 1 commit)
- FOUND: 49f6f6c (Task 2 commit)
- BUILD SUCCESSFUL (androidApp:compileDebugKotlin)
