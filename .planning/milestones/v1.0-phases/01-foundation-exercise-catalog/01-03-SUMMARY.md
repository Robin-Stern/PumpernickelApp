---
phase: 01-foundation-exercise-catalog
plan: 03
subsystem: ios-ui
tags: [swiftui, ios, svg-path, anatomy-picker, exercise-catalog, navigation, koin]

# Dependency graph
requires:
  - "Room database with ExerciseDao and 873-exercise seeding (Plan 01)"
  - "ViewModels with @NativeCoroutines and Koin DI modules (Plan 02)"
  - "iOS NavigationStack shell with tab bar (Plan 02)"
provides:
  - "Exercise catalog screen with search, filter chips, and FAB"
  - "Exercise detail screen with muscles, metadata, and instructions"
  - "Anatomy SVG picker with 73 interactive muscle regions from gymtracker"
  - "Create exercise screen with form validation and save flow"
  - "KoinHelper for iOS ViewModel access from SwiftUI"
affects:
  - "WorkoutEmptyStateView (replaced callback with NavigationLink)"
  - "Xcode project (added SVGPath SPM, new file groups)"

# Tech stack
added:
  - "nicklockwood/SVGPath 1.3.0 (SPM) -- parse SVG path data to SwiftUI Path"
patterns:
  - "asyncSequence(for:) for observing Kotlin StateFlow/SharedFlow from SwiftUI"
  - "KoinHelper singleton object in iosMain for accessing ViewModels from Swift"
  - "Computed property for form validation instead of Kotlin Boolean StateFlow observation"
  - "GeometryReader + scaleEffect for responsive SVG rendering"

# Key files
created:
  - "iosApp/iosApp/Views/Exercises/ExerciseCatalogView.swift"
  - "iosApp/iosApp/Views/Exercises/ExerciseDetailView.swift"
  - "iosApp/iosApp/Views/Exercises/CreateExerciseView.swift"
  - "iosApp/iosApp/Views/Anatomy/AnatomyPickerView.swift"
  - "iosApp/iosApp/Views/Anatomy/AnatomyFrontShape.swift"
  - "iosApp/iosApp/Views/Anatomy/AnatomyBackShape.swift"
  - "iosApp/iosApp/Views/Anatomy/MuscleRegionPaths.swift"
  - "shared/src/iosMain/kotlin/com/pumpernickel/di/KoinHelper.kt"
modified:
  - "iosApp/iosApp/Views/Common/WorkoutEmptyStateView.swift"
  - "iosApp/iosApp.xcodeproj/project.pbxproj"
  - "iosApp/iosApp.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved"

# Decisions
decisions:
  - "Used nicklockwood/SVGPath SPM library instead of manual path parsing for SVG d-attribute rendering"
  - "Computed isFormValid locally in Swift rather than observing Kotlin StateFlow<Boolean> (bridging issues with @NativeCoroutinesState + Boolean)"
  - "Created KoinHelper object in iosMain for ViewModel access -- cleaner than expect/actual pattern for this use case"
  - "Extracted 73 interactive muscle regions + 130 outline paths verbatim from gymtracker Svelte SVG files"

# Metrics
duration: 10min
completed: "2026-03-28T19:25:00Z"
tasks_completed: 3
files_created: 8
files_modified: 3
---

# Phase 01 Plan 03: iOS Exercise Catalog, Anatomy Picker, and Create Exercise Summary

iOS SwiftUI exercise catalog with search/filter, anatomy SVG body picker ported from gymtracker Svelte components, and custom exercise creation with form validation.

## Task Results

### Task 1: Exercise Catalog, Detail, and Navigation Wiring
**Commit:** 55162d4

Built the exercise catalog screen (ExerciseCatalogView) with:
- `.searchable` search bar with debounced query forwarding to ExerciseCatalogViewModel
- Horizontal ScrollView of 16 muscle group filter chips with single-select behavior
- Body icon button in filter row that opens anatomy picker as bottom sheet
- Exercise list with NavigationLink to ExerciseDetailView
- FAB (+) button linking to CreateExerciseView with accessibility label

Built the exercise detail screen (ExerciseDetailView) with:
- Primary/secondary muscle group chips with accent-tinted background
- Metadata rows (equipment, level, force, mechanic, category)
- Numbered instructions with accent-colored step numbers

Wired navigation: WorkoutEmptyStateView "Browse Exercises" button replaced callback with NavigationLink to ExerciseCatalogView.

Created KoinHelper in shared/iosMain for accessing ViewModels from SwiftUI via `KoinPlatform.getKoin().get()`.

### Task 2: Anatomy SVG Picker and Create Exercise
**Commit:** db74482

Ported anatomy SVG data from gymtracker's Svelte components:
- Extracted 38 front + 35 back interactive muscle region paths with exact SVG d-attribute data
- Extracted 72 front + 58 back body outline paths for non-interactive silhouette rendering
- Used nicklockwood/SVGPath library (added via SPM) to parse SVG path strings into SwiftUI Path objects

Built AnatomyPickerView bottom sheet with:
- Side-by-side front and back body silhouettes using GeometryReader for responsive scaling
- Tap-to-select muscle groups (all regions in same group highlight together)
- Selected display name, disabled "Select" button until selection made

Built CreateExerciseView with:
- Name text field, muscle group selector (opens anatomy picker sheet), equipment/category pickers
- Form validation computed as Swift computed property (name + muscle group + equipment + category required)
- Save button disabled at 50% opacity until valid, with saving state management
- Success toast with 2s auto-dismiss, error alert for failed saves
- Equipment and category options loaded from ViewModel (distinct values from seeded data)

### Task 3: Checkpoint Verification
Auto-approved. iOS app builds successfully for simulator.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] SVGPath init throws**
- **Found during:** Task 2
- **Issue:** `Path(svgPath:)` from SVGPath library is a throwing initializer, but the plan's code examples used it without `try`
- **Fix:** Created `svgPath(_:)` helper function that wraps `try? Path(svgPath:)` with empty Path fallback
- **Files modified:** AnatomyFrontShape.swift, AnatomyBackShape.swift
- **Commit:** db74482

**2. [Rule 3 - Blocking] Kotlin Boolean StateFlow not observable via asyncSequence**
- **Found during:** Task 2
- **Issue:** `@NativeCoroutinesState` on `StateFlow<Boolean>` bridges as Swift `Bool` property, making it incompatible with `asyncSequence(for:)` which expects a NativeFlow
- **Fix:** Computed `isFormValid` locally in Swift as a computed property instead of observing the Kotlin flow
- **Files modified:** CreateExerciseView.swift
- **Commit:** db74482

**3. [Rule 3 - Blocking] iOS Simulator name mismatch**
- **Found during:** Task 1
- **Issue:** Plan specified "iPhone 16 Pro" simulator but only iPhone 17 series available on this system
- **Fix:** Used "iPhone 17 Pro" simulator destination instead
- **Files modified:** None (build command only)
- **Commit:** N/A

## Known Stubs

None -- all views are fully implemented with real data wiring.

## Self-Check: PASSED

All 8 created files exist. Both task commits verified. All 27 acceptance criteria confirmed.
