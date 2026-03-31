---
phase: 12-exercise-catalog-templates
plan: 01
subsystem: ui
tags: [compose, material3, navigation, exercise-catalog, koin, viewmodel]

# Dependency graph
requires:
  - phase: 11-android-shell-navigation
    provides: NavHost skeleton, Routes.kt, koinViewModel() pattern, Material 3 theme

provides:
  - ExerciseCatalogScreen with search bar, muscle group filter chips, equipment icons, FAB
  - ExerciseDetailScreen with muscles, metadata, instructions
  - CreateExerciseScreen with name/muscle/equipment/category form fields
  - All three exercise routes wired into workout tab NavHost

affects:
  - 12-02-templates (same NavHost, same screen package)
  - 13-workout-session (exercise picker integration)
  - 14-history-settings-anatomy (anatomy picker replaces dropdown in CreateExercise)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "ExposedDropdownMenuBox + menuAnchor(MenuAnchorType.PrimaryNotEditable) for read-only dropdowns"
    - "collectAsState() for StateFlow observation in Composables"
    - "LaunchedEffect(Unit) collecting SharedFlow for one-shot events (save result)"
    - "Icons.AutoMirrored.Filled.ArrowBack for back navigation icon"

key-files:
  created:
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ExerciseCatalogScreen.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ExerciseDetailScreen.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/CreateExerciseScreen.kt
  modified:
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/Routes.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt

key-decisions:
  - "collectAsState() used over collectAsStateWithLifecycle() - lifecycle-runtime-compose not in explicit deps"
  - "ExerciseDetailRoute.exerciseId fixed from Long to String to match Exercise.id domain type"
  - "Anatomy picker (Canvas body drawing) deferred to Phase 14 - muscle group uses ExposedDropdownMenuBox instead"

patterns-established:
  - "Screen package: com.pumpernickel.android.ui.screens"
  - "ViewModel injection: koinViewModel<XxxViewModel>()"
  - "State observation: val state by viewModel.stateFlow.collectAsState()"
  - "Navigation: navController.navigate(XxxRoute) or navController.navigate(XxxRoute(param))"
  - "Back navigation: navController.popBackStack()"
  - "SharedFlow events: LaunchedEffect(Unit) { viewModel.saveResult.collect { ... } }"

requirements-completed: [ANDROID-03]

# Metrics
duration: 10min
completed: 2026-03-31
---

# Phase 12 Plan 01: Exercise Catalog Screens Summary

**Material 3 exercise catalog with search/filter, detail view, and create form — all three screens wired into the workout NavHost with String-typed routes**

## Performance

- **Duration:** 10 min
- **Started:** 2026-03-31T16:20:58Z
- **Completed:** 2026-03-31T16:31:00Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- Fixed ExerciseDetailRoute.exerciseId type mismatch (Long -> String) matching Exercise.id domain type
- ExerciseCatalogScreen with search bar, 16-entry muscle group filter chip row, equipment-icon ListItems, and FAB navigating to CreateExerciseRoute
- ExerciseDetailScreen showing primary/secondary muscles as chips, metadata table, numbered instructions with primary-colored step numbers
- CreateExerciseScreen with name OutlinedTextField, muscle group ExposedDropdownMenuBox (all 16 MuscleGroup entries), equipment/category pickers, form validation gating the Create button, SharedFlow save result handling
- All three routes registered in workout tab NavHost in MainScreen.kt

## Task Commits

Each task was committed atomically:

1. **Task 1: Fix ExerciseDetailRoute type and create ExerciseCatalogScreen + ExerciseDetailScreen** - `1c43122` (feat)
2. **Task 2: Create CreateExerciseScreen and wire all exercise routes into NavHost** - `f9dd83d` (feat)

**Plan metadata:** (docs commit below)

## Files Created/Modified
- `androidApp/.../ui/navigation/Routes.kt` - Fixed ExerciseDetailRoute exerciseId: Long -> String
- `androidApp/.../ui/screens/ExerciseCatalogScreen.kt` - Search bar, filter chips, equipment icons, FAB (130 lines)
- `androidApp/.../ui/screens/ExerciseDetailScreen.kt` - Muscle groups, metadata, instructions (200 lines)
- `androidApp/.../ui/screens/CreateExerciseScreen.kt` - Full create form with dropdowns (220 lines)
- `androidApp/.../ui/navigation/MainScreen.kt` - Wired ExerciseCatalogRoute, ExerciseDetailRoute, CreateExerciseRoute

## Decisions Made
- `collectAsState()` used instead of `collectAsStateWithLifecycle()` because `lifecycle-runtime-compose` is not an explicit dependency in androidApp/build.gradle.kts. Both work correctly; the lifecycle-aware variant optimizes battery by pausing collection when the composable is not visible.
- `ExposedDropdownMenuBox` with `menuAnchor(MenuAnchorType.PrimaryNotEditable)` for the muscle group and equipment/category pickers, matching Material 3 best practice for read-only dropdowns.
- Anatomy picker (Canvas body-map drawing) intentionally deferred to Phase 14 per plan notes. Muscle group selection uses a simple ExposedDropdownMenuBox listing all 16 MuscleGroup enum entries.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- Gradle daemon lock conflict (PID 67711 holding `/Users/olli/.gradle/caches/8.12/fileHashes` lock from a background Kotlin LSP process). Resolved by killing the blocking daemon and removing the stale lock file. Build succeeded on subsequent attempt.

## Known Stubs

None. All ViewModel connections are live: ExerciseCatalogViewModel filters from the Room-backed ExerciseRepository, ExerciseDetailViewModel loads by String ID, CreateExerciseViewModel reads equipment/category from the repository and writes back on save.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Exercise catalog fully navigable on Android: catalog -> detail, catalog -> create, back stack works
- Phase 12-02 can build template list/editor screens in the same NavHost using the same patterns
- Anatomy picker slot exists in CreateExerciseScreen (Phase 14 will replace dropdown with Canvas body map)

---
*Phase: 12-exercise-catalog-templates*
*Completed: 2026-03-31*

## Self-Check: PASSED

- ExerciseCatalogScreen.kt: FOUND
- ExerciseDetailScreen.kt: FOUND
- CreateExerciseScreen.kt: FOUND
- 12-01-SUMMARY.md: FOUND
- Commit 1c43122: FOUND
- Commit f9dd83d: FOUND
