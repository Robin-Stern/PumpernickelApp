---
phase: 11-android-shell-navigation
plan: 01
subsystem: ui
tags: [compose, material3, navigation, koin, android, kmp]

# Dependency graph
requires: []
provides:
  - Android app build config with Compose BOM 2025.06.00 and Material 3
  - PumpernickelApplication with Koin DI initialization (androidContext wiring)
  - MainActivity with edge-to-edge Compose setContent
  - PumpernickelTheme with Material 3 lightColorScheme (primary = #66BB6A)
  - Type-safe @Serializable route objects for all future screens
  - MainScreen with 3-tab NavigationBar and per-tab NavHost
  - PlaceholderScreen (reusable) and WorkoutPlaceholderScreen
affects: [12-catalog-and-templates, 13-workout-session, 14-history-settings-anatomy]

# Tech tracking
tech-stack:
  added:
    - "Compose BOM 2025.06.00"
    - "androidx.compose.material3:material3"
    - "androidx.compose.ui:ui"
    - "androidx.activity:activity-compose:1.10.1"
    - "androidx.navigation:navigation-compose (via existing version)"
    - "org.jetbrains.kotlin.plugin.compose (compose-compiler)"
  patterns:
    - "KMP v2 source layout: src/androidMain/ instead of src/main/"
    - "compileSdk = 36 required by Compose BOM 2025.06.00"
    - "KoinApplication lambda config for platform-specific androidContext()"
    - "Per-tab back stack via AnimatedVisibility + retained NavController"

key-files:
  created:
    - androidApp/src/androidMain/AndroidManifest.xml
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/PumpernickelApplication.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/MainActivity.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/theme/Theme.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/Routes.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/PlaceholderScreen.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutPlaceholderScreen.kt
  modified:
    - gradle/libs.versions.toml
    - build.gradle.kts
    - androidApp/build.gradle.kts
    - shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt

key-decisions:
  - "compileSdk bumped to 36: Compose BOM 2025.06.00 requires API 36 (activity-compose 1.12.4, navigationevent-compose)"
  - "initKoin() accepts KoinApplication lambda instead of List<Module>: enables androidContext() setup before module loading"
  - "KMP v2 source layout (src/androidMain/): enforced by KMP Gradle plugin, old src/main/ deprecated"
  - "Compose BOM in top-level dependencies{} block: platform() not available inside KMP kotlin{} sourceSets block"

patterns-established:
  - "Android source layout: src/androidMain/kotlin/com/pumpernickel/android/"
  - "Tab state: rememberSaveable { mutableIntStateOf(0) } starts on Workout tab"
  - "Per-tab back stack: AnimatedVisibility wrapping NavHost, retained navController outside animation"

requirements-completed: [ANDROID-01, ANDROID-02]

# Metrics
duration: 5min
completed: 2026-03-31
---

# Phase 11 Plan 01: Android Shell Navigation Summary

**Material 3 Android app with 3-tab NavigationBar, per-tab NavHost, Koin DI wiring, and #66BB6A green accent theme — compile-verified with Compose BOM 2025.06.00**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-31T15:09:28Z
- **Completed:** 2026-03-31T15:14:30Z
- **Tasks:** 2
- **Files modified:** 12

## Accomplishments

- Android app builds successfully with Compose Material 3 (BOM 2025.06.00)
- PumpernickelApplication initializes Koin with androidContext() before shared module loads
- 3-tab NavigationBar (Workout/Overview/Nutrition) with FitnessCenter/BarChart/Restaurant icons
- Per-tab back stack preserved via AnimatedVisibility + retained NavController
- All route objects defined for Phases 12-14 (TemplateList, TemplateEditor, ExerciseCatalog, ExerciseDetail, CreateExercise, ExercisePicker, WorkoutSession, WorkoutHistoryDetail)

## Task Commits

Each task was committed atomically:

1. **Task 1: Build config, Application class, MainActivity, and Material 3 theme** - `c215c7b` (feat)
2. **Task 2: Type-safe routes, NavigationBar with per-tab NavHost, and placeholder screens** - `49b1b61` (feat)

## Files Created/Modified

- `gradle/libs.versions.toml` - Added Compose BOM, activity-compose, compose-compiler plugin
- `build.gradle.kts` - Added compose-compiler plugin alias (apply false)
- `androidApp/build.gradle.kts` - Compose plugin, compileSdk 36, dependencies wiring
- `shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt` - initKoin() accepts KoinApplication lambda
- `androidApp/src/androidMain/AndroidManifest.xml` - PumpernickelApplication wired, MainActivity registered
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/PumpernickelApplication.kt` - Koin init with androidContext
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/MainActivity.kt` - Edge-to-edge Compose setContent
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/theme/Theme.kt` - Material 3 lightColorScheme, PumpernickelTheme
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/Routes.kt` - 11 @Serializable route objects
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt` - NavigationBar + per-tab NavHost
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/PlaceholderScreen.kt` - Reusable placeholder composable
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutPlaceholderScreen.kt` - Workout tab placeholder

## Decisions Made

- **compileSdk bumped to 36:** Compose BOM 2025.06.00 pulls in activity-compose 1.12.4 and navigationevent-compose 1.0.2 which require API 36. Bumped both compileSdk and targetSdk.
- **initKoin() accepts KoinApplication lambda:** Changed from `List<Module>` parameter to `KoinApplication.() -> Unit` lambda. This allows `androidContext()` to be called before modules are loaded. iOS caller (`initKoinIos()`) passes empty lambda, unchanged behavior.
- **KMP v2 source layout enforced:** Gradle plugin warned that `src/main/kotlin` is deprecated; moved all sources to `src/androidMain/kotlin`. Manifest moved from `src/main/` to `src/androidMain/`.
- **Compose BOM in top-level dependencies{}:** The `platform()` function is not available inside `kotlin { sourceSets { androidMain.dependencies {} } }` in KMP — placed BOM and versionless Compose deps in the module-level `dependencies {}` block instead.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] compileSdk bumped from 35 to 36**
- **Found during:** Task 1 (initial compilation attempt)
- **Issue:** Compose BOM 2025.06.00 resolved `activity-compose:1.12.4` and `navigationevent-compose-android:1.0.2`, both requiring API 36. Build failed at `checkDebugAarMetadata`.
- **Fix:** Set `compileSdk = 36` and `targetSdk = 36` in androidApp/build.gradle.kts
- **Files modified:** androidApp/build.gradle.kts
- **Committed in:** c215c7b (Task 1 commit)

**2. [Rule 1 - Bug] Migrated to KMP v2 source layout**
- **Found during:** Task 1 (processDebugMainManifest failure)
- **Issue:** KMP Gradle plugin expected `src/androidMain/AndroidManifest.xml`, not `src/main/`. Manifest and sources moved.
- **Fix:** Created `src/androidMain/` directory tree; moved manifest and all new source files there; removed old `src/main/`
- **Files modified:** All Android source files
- **Committed in:** c215c7b (Task 1 commit)

**3. [Rule 1 - Bug] platform() in top-level dependencies{} block**
- **Found during:** Task 1 (script compilation error in build.gradle.kts)
- **Issue:** Kotlin 2.3 reports `platform(Any)` as error inside `kotlin { sourceSets { androidMain.dependencies {} } }` blocks.
- **Fix:** Moved Compose BOM + versionless Compose dependencies to module-level `dependencies {}` block
- **Files modified:** androidApp/build.gradle.kts
- **Committed in:** c215c7b (Task 1 commit)

**4. [Rule 2 - Missing Critical] initKoin() signature changed to accept lambda**
- **Found during:** Task 1 (design review of PumpernickelApplication)
- **Issue:** The existing `initKoin(List<Module>)` signature cannot accept `androidContext()` setup. PlatformModule uses `androidContext()` which requires it to be set before module loading — impossible with the old signature.
- **Fix:** Changed to `fun initKoin(appDeclaration: KoinApplication.() -> Unit = {})`. iOS remains unaffected (calls empty lambda).
- **Files modified:** shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt
- **Verification:** iOS `compileKotlinIosSimulatorArm64` still passes; Android `compileDebugKotlin` passes.
- **Committed in:** c215c7b (Task 1 commit)

---

**Total deviations:** 4 auto-fixed (2 Rule 1 bugs, 1 Rule 1 blocking layout issue, 1 Rule 2 missing critical)
**Impact on plan:** All auto-fixes were required for correct build configuration. No scope creep.

## Issues Encountered

- Compose BOM 2025.06.00 pin date was later than expected — pulled in API 36 dependencies. Resolved by bumping compileSdk/targetSdk to 36.

## Known Stubs

- `WorkoutPlaceholderScreen` and `PlaceholderScreen` are intentional placeholders. Phase 12 will replace `WorkoutPlaceholderScreen` with `TemplateListScreen`. Overview and Nutrition placeholders are accepted as-is for v1.5 scope.

## Next Phase Readiness

- Phase 12 (Catalog & Templates) can begin: `TemplateListRoute`, `TemplateEditorRoute`, `ExerciseCatalogRoute`, `ExerciseDetailRoute`, `CreateExerciseRoute`, `ExercisePickerRoute` are all defined. Replace `WorkoutPlaceholderScreen` composable destination in `MainScreen.kt`.
- All ViewModels are injectable via Koin (shared module already registered)
- No blockers.

---
*Phase: 11-android-shell-navigation*
*Completed: 2026-03-31*
