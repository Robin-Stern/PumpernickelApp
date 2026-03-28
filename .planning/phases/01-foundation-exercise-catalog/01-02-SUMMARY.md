---
phase: 01-foundation-exercise-catalog
plan: 02
subsystem: presentation
tags: [viewmodel, koin, swiftui, native-coroutines, di, navigation, ios]

# Dependency graph
requires:
  - phase: 01-01
    provides: "Room database, ExerciseDao, ExerciseRepository, domain models, KMP project structure"
provides:
  - "ExerciseCatalogViewModel with search/filter via debounce+flatMapLatest"
  - "ExerciseDetailViewModel with loadExercise(id) pattern"
  - "CreateExerciseViewModel with form validation and SaveResult sealed class"
  - "Koin DI SharedModule wiring database, repository, seeder, and ViewModels"
  - "Platform-specific Koin modules for Android and iOS"
  - "KoinInitIos entry point callable from Swift"
  - "iOS SwiftUI app with TabView bottom navigation (3 tabs)"
  - "Workout empty state, Overview/Nutrition placeholder screens"
  - "Xcode project with KMPNativeCoroutinesAsync SPM package"
affects: [01-03, 02-workout-templates, 03-workout-session]

# Tech tracking
tech-stack:
  added: [Koin Compose ViewModel 4.2.0, KMPNativeCoroutinesAsync SPM 1.0.2, SwiftUI TabView, NavigationStack]
  patterns: [MVVM with ViewModel exposing StateFlow, Koin viewModel DSL for KMP, @NativeCoroutines/@NativeCoroutinesState annotations, expect/actual platformModule for Koin DI, SwiftUI dark theme default]

key-files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/exercises/ExerciseCatalogViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/exercises/ExerciseDetailViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/exercises/CreateExerciseViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt
    - shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/di/KoinInitIos.kt
    - iosApp/iosApp/PumpernickelApp.swift
    - iosApp/iosApp/Views/MainTabView.swift
    - iosApp/iosApp/Views/Common/WorkoutEmptyStateView.swift
    - iosApp/iosApp/Views/Common/PlaceholderTabView.swift
    - iosApp/iosApp/Utilities/FlowObservation.swift
    - iosApp/iosApp.xcodeproj/project.pbxproj
  modified:
    - gradle/libs.versions.toml
    - shared/build.gradle.kts

key-decisions:
  - "Used kotlin.time.Clock.System (stdlib) instead of kotlinx.datetime.Clock.System (deprecated in 0.7.x) for timestamp generation"
  - "Added koin-compose-viewmodel to commonMain for viewModel DSL support in KMP"
  - "Created Xcode project from scratch with KMPNativeCoroutinesAsync SPM and Gradle build phase for framework embedding"
  - "PlaceholderTabView uses 'message' parameter name instead of 'body' to avoid Swift keyword conflict"

patterns-established:
  - "Koin DI wiring: platformModule (expect/actual) provides RoomDatabase.Builder, SharedModule builds database via BundledSQLiteDriver"
  - "ViewModel pattern: constructor injection of repository, StateFlow exposure with @NativeCoroutines annotations"
  - "iOS app structure: iosApp/iosApp/ with Views/, Views/Common/, Utilities/ directories"
  - "SwiftUI dark theme default via .preferredColorScheme(.dark) on root view"

requirements-completed: [NAV-01, EXER-01]

# Metrics
duration: 12min
completed: 2026-03-28
---

# Phase 1 Plan 02: ViewModels, Koin DI, iOS Navigation Shell Summary

**Three shared ViewModels with @NativeCoroutines annotations, Koin DI wiring all layers, and iOS SwiftUI app with 3-tab bottom navigation building successfully for simulator**

## Performance

- **Duration:** 12 min
- **Started:** 2026-03-28T18:49:50Z
- **Completed:** 2026-03-28T19:02:28Z
- **Tasks:** 2
- **Files modified:** 15

## Accomplishments
- Three ViewModels (ExerciseCatalogViewModel with debounce search, ExerciseDetailViewModel, CreateExerciseViewModel with form validation) compile with @NativeCoroutines annotations for Swift interop
- Koin DI SharedModule wires the complete dependency graph: platform-provided RoomDatabase.Builder -> AppDatabase via BundledSQLiteDriver -> ExerciseDao -> DatabaseSeeder -> ExerciseRepository -> ViewModels
- iOS SwiftUI app launches with bottom tab bar (Workout, Overview, Nutrition), Workout tab showing empty state per UI-SPEC, and dark theme default
- Xcode project created with KMPNativeCoroutinesAsync SPM package (v1.0.2) and Gradle build phase for Kotlin framework embedding

## Task Commits

Each task was committed atomically:

1. **Task 1: ViewModels with @NativeCoroutines and Koin DI modules** - `47c34c8` (feat)
2. **Task 2: iOS SwiftUI app entry point, navigation shell, and placeholder screens** - `48f655c` (feat)
3. **SPM Package.resolved lock file** - `30158f6` (chore)

## Files Created/Modified
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/exercises/ExerciseCatalogViewModel.kt` - Search/filter ViewModel with debounce(300)+flatMapLatest, @NativeCoroutines on exercises StateFlow
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/exercises/ExerciseDetailViewModel.kt` - Detail ViewModel with loadExercise(id) triggering repository lookup
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/exercises/CreateExerciseViewModel.kt` - Form ViewModel with validation, equipment/category options, SaveResult sealed class
- `shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt` - Koin module: database via BundledSQLiteDriver, DAO, seeder, repository, 3 ViewModels, initKoin() entry point
- `shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt` - Android Koin module providing RoomDatabase.Builder via androidContext()
- `shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt` - iOS Koin module providing RoomDatabase.Builder
- `shared/src/iosMain/kotlin/com/pumpernickel/di/KoinInitIos.kt` - iOS entry point: initKoinIos() callable from Swift as doInitKoinIos()
- `iosApp/iosApp/PumpernickelApp.swift` - @main app entry point, Koin init, dark theme
- `iosApp/iosApp/Views/MainTabView.swift` - TabView with 3 tabs, accent tint #66BB6A, Workout default
- `iosApp/iosApp/Views/Common/WorkoutEmptyStateView.swift` - Empty state with dumbbell icon, "No Workouts Yet", "Browse Exercises" CTA
- `iosApp/iosApp/Views/Common/PlaceholderTabView.swift` - Reusable placeholder for Overview/Nutrition tabs
- `iosApp/iosApp/Utilities/FlowObservation.swift` - Documentation for asyncSequence(for:) flow observation pattern
- `iosApp/iosApp.xcodeproj/project.pbxproj` - Xcode project with SPM, build phases, framework linking
- `gradle/libs.versions.toml` - Added koin-compose-viewmodel-navigation library entry
- `shared/build.gradle.kts` - Added koin-compose-viewmodel and koin-android dependencies

## Decisions Made
- **kotlin.time.Clock.System instead of kotlinx.datetime.Clock.System:** In kotlinx-datetime 0.7.x, Clock and Instant were migrated to the Kotlin standard library (kotlin.time package). The kotlinx.datetime.Clock.System reference is deprecated. Used kotlin.time.Clock.System.now().epochSeconds for custom exercise ID generation.
- **koin-compose-viewmodel for viewModel DSL:** The `viewModel {}` DSL function for Koin in KMP requires the `koin-compose-viewmodel` artifact in commonMain, not just `koin-core`. Added as a dependency.
- **Xcode project created from scratch:** No iosApp directory existed. Created a complete .xcodeproj with proper build phases (Gradle embedAndSignAppleFrameworkForXcode), SPM package references (KMPNativeCoroutinesAsync 1.0.2), and framework search paths pointing to shared/build/xcode-frameworks.
- **PlaceholderTabView parameter naming:** Used `message` instead of `body` for the text parameter to avoid conflict with Swift's `body` computed property in View protocol.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] kotlinx.datetime.Clock.System unresolved in 0.7.x**
- **Found during:** Task 1 (CreateExerciseViewModel implementation)
- **Issue:** Plan used `Clock.System.now().epochSeconds` with `import kotlinx.datetime.Clock`, but kotlinx-datetime 0.7.x migrated Clock to `kotlin.time.Clock` (Kotlin stdlib). The `kotlinx.datetime.Clock` interface is deprecated.
- **Fix:** Used fully qualified `kotlin.time.Clock.System.now().epochSeconds` which is the new API location
- **Files modified:** shared/src/commonMain/kotlin/com/pumpernickel/presentation/exercises/CreateExerciseViewModel.kt
- **Verification:** `./gradlew :shared:compileKotlinIosSimulatorArm64` succeeds
- **Committed in:** 47c34c8 (Task 1 commit)

**2. [Rule 3 - Blocking] Koin viewModel DSL not in koin-core**
- **Found during:** Task 1 (SharedModule compilation)
- **Issue:** `org.koin.core.module.dsl.viewModel` is not available in `koin-core` alone for KMP. Requires `koin-compose-viewmodel` artifact.
- **Fix:** Added `implementation(libs.koin.compose.viewmodel)` to commonMain dependencies in shared/build.gradle.kts
- **Files modified:** shared/build.gradle.kts, gradle/libs.versions.toml
- **Verification:** `./gradlew :shared:compileKotlinIosSimulatorArm64` succeeds
- **Committed in:** 47c34c8 (Task 1 commit)

**3. [Rule 3 - Blocking] ExperimentalCoroutinesApi opt-in needed for flatMapLatest**
- **Found during:** Task 1 (ExerciseCatalogViewModel compilation)
- **Issue:** `flatMapLatest` requires `@OptIn(ExperimentalCoroutinesApi::class)` in Kotlin coroutines 1.10.2
- **Fix:** Added `@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)` to ExerciseCatalogViewModel
- **Files modified:** shared/src/commonMain/kotlin/com/pumpernickel/presentation/exercises/ExerciseCatalogViewModel.kt
- **Verification:** Build succeeds without errors
- **Committed in:** 47c34c8 (Task 1 commit)

---

**Total deviations:** 3 auto-fixed (3 blocking)
**Impact on plan:** All auto-fixes necessary to resolve compilation failures. No scope creep.

## Issues Encountered
- iPhone 16 Pro simulator not available (Xcode 26.x uses iPhone 17 Pro naming). Used `iPhone 17 Pro` destination for build verification. Not a code issue.

## User Setup Required
None - no external service configuration required.

## Known Stubs
None - all ViewModels are fully wired to the repository layer. The iOS SwiftUI screens display static content as designed (empty states and placeholders per UI-SPEC). Flow observation from Swift will be wired in Plan 03 when the Exercise Catalog screen is built.

## Next Phase Readiness
- ViewModels ready for Plan 03 to connect to SwiftUI screens via KMPNativeCoroutinesAsync
- Koin DI complete: all dependencies resolvable on both platforms
- iOS app shell ready: NavigationStack in Workout tab for push navigation to Exercise Catalog
- WorkoutEmptyStateView has onBrowseExercises callback ready to wire to NavigationLink

## Self-Check: PASSED

All 13 key files verified present. All 3 task commits (47c34c8, 48f655c, 30158f6) verified in git log.

---
*Phase: 01-foundation-exercise-catalog*
*Completed: 2026-03-28*
