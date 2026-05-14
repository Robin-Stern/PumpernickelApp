# Milestones

## Post-v1.5 (Untracked) — Merged 2026-04-14

**Scope:** Nutrition feature, dynamic theming, nutrition goals, workout/history polish. Merged on `feature/workouts` (commit `fe297ad`) outside the GSD workflow — no PLAN.md / RESEARCH.md artifacts exist for this work. Recorded here for traceability.

**Commit range:** `4d02ce0..fe297ad` (28 commits)

**Key accomplishments:**

- **Nutrition feature (F2 from Lastenheft):** Full Food + Recipe + ConsumptionEntry domain. 11 use cases in `domain/nutrition/` (Add/Update/Delete/LoadFoods, Log/Load/DeleteConsumption, LookupBarcode, ValidateFoodInput, CalculateDailyMacros, CalculateRecipeMacros). 4 new Room entities (FoodEntity, RecipeEntity, RecipeIngredientEntity, ConsumptionEntryEntity) with `NutritionDao` and `NutritionDataSeeder`. 4 ViewModels in `presentation/nutrition/` (DailyLog, FoodEntry, RecipeList, RecipeCreation).
- **Barcode scanning:** OpenFoodFacts integration via Ktor CIO (`OpenFoodFactsApi`, `HttpClientFactory`, `OpenFoodFactsDto`). iOS `BarcodeScannerView` using AVFoundation with camera-permissions entry in Info.plist. `kotlinx-datetime` added to stack for consumption-date handling.
- **iOS nutrition views (`Views/Nutrition/`):** `NutritionDailyLogView` (now the Nutrition tab root, replacing placeholder), `NutritionFoodEntryView` (search + scan + ad-hoc entry), `NutritionRecipeListView`, `NutritionRecipeCreationView`, `BarcodeScannerView`, `MacroRowView`.
- **Dynamic theming:** Theme mode (light/dark/system) + 8 accent color presets persisted in `SettingsRepository`. iOS: `ThemeManager` (@Observable) + `Color.appAccent` extension + `FlowObservation` helpers. `AppRootView` observes `appThemeFlow` and `accentColorFlow` via `asyncSequence` in parallel `withTaskGroup`. Android: Material 3 palette aligned with semantic colors across workout screens.
- **Nutrition goals:** `NutritionGoals` domain model (calorie/protein/fat/carb/sugar) with defaults 2500/150/80/300/50. Persisted in `SettingsRepository` as string-encoded ints. Surfaced on Overview tab via `OverviewViewModel` (now receives 5 dependencies via Koin).
- **Workout/History polish:** Template editor redesign; `WorkoutHistoryDetailView` shows per-set count and RIR; PB calculation fix (volume-weighted aggregate consistent with firmware `TrendCalculator.cpp`); semantic colors replace hardcoded RGB values; rest timer visual improvements.
- **Infrastructure:** Room schema bumped v4 → v7 with `AutoMigration(6, 7)` registered; composeApp migrated to `android-kmp-library` Gradle plugin and `androidApp` extracted as a separate module; dependency versions bumped; camera/barcode permissions added to iOS Info.plist and Android manifest.

**Status of Overview tab:** Wired to `OverviewViewModel` displaying today's macros vs. goals, but full dashboard (training intensity, muscle heatmap) is placeholder-level — earmarked for a future milestone.

---

## v1.5 Android Material 3 UI (Shipped: 2026-03-31)

**Phases completed:** 4 phases, 9 plans

**Key accomplishments:**

- Android shell: Material 3 theme, bottom NavigationBar (Workout/Overview/Nutrition tabs), type-safe `@Serializable` routes via Navigation Compose 2.9.2, Koin DI wired with `androidContext()`, compileSdk 36 for Compose BOM 2025.06.00
- Exercise catalog parity: `ExerciseCatalogScreen` + `ExerciseDetailScreen` + `CreateExerciseScreen` with search, filter, custom-exercise creation, and 873-exercise seeded data
- Template management parity: `TemplateListScreen`, `TemplateEditorScreen` with move-up/down reorder (no reorderable library), `ExercisePickerScreen` sharing a `TemplateEditorViewModel` instance across screens via `parentEntry`, `WorkoutEmptyStateScreen`
- Workout session parity: custom drum/wheel picker with `LazyColumn` + `SnapFlingBehavior` for iOS-equivalent fling physics, full state machine UI (Active → Reviewing → Finished), `ExerciseOverviewSheet` with reorder/skip, abandon dialog, edit-set sheet hoisted to screen level for Active/Reviewing reuse
- History + settings + anatomy: `WorkoutHistoryListScreen` + `WorkoutHistoryDetailScreen` (DisposableEffect clears stale detail), `SettingsSheet` with kg/lbs toggle, `AnatomyPickerSheet` with Compose Canvas front/back body drawings sharing `MuscleRegionPaths` from commonMain

---

## v1.1 Workout Polish & Firmware Parity (Shipped: 2026-03-31)

**Phases completed:** 6 phases, 9 plans, 18 tasks

**Key accomplishments:**

- Firmware-parity auto-increment pre-fill logic in WorkoutSessionViewModel with SetPreFill StateFlow and 0-reps guard
- Native iOS scroll wheel pickers replacing TextFields for reps/weight input with preFill StateFlow binding, 0-reps guard, and edit sheet picker consistency
- Volume-weighted average PB displayed as blue "PB: XX.X kg" label in workout header via Room aggregate SQL query and StateFlow observation
- Reviewing sealed class state with recap screen showing all exercises/sets before save, tap-to-edit via existing wheel pickers
- Room schema v4 migration with exerciseOrder persistence, ViewModel reorderExercise/skipExercise methods, and crash-recovery-safe exercise order restoration
- Sectioned exercise overview sheet with drag-reorder on pending exercises and dual skip-exercise buttons (toolbar + sheet)
- X button with abandon confirmation dialog (save/discard/cancel) and ellipsis context menu replacing scattered toolbar actions
- Shared Color.appAccent constant replacing 9 hardcoded RGB values across 4 workout views, plus 32pt padding standardization on WorkoutFinishedView summary card
- Firmware-style minimal SET N lifting screen with tap-to-reveal input, haptic on set complete, and VoiceOver labels on all workout views

---

## v1.0 MVP (Shipped: 2026-03-29)

**Phases completed:** 4 phases, 12 plans, 26 tasks

**Key accomplishments:**

- KMP project with Room database, 873-exercise seeded catalog, 16-group muscle mapping, and deferred repository seeding pattern compiling for both iOS and Android targets
- Three shared ViewModels with @NativeCoroutines annotations, Koin DI wiring all layers, and iOS SwiftUI app with 3-tab bottom navigation building successfully for simulator
- Commit:
- Room entities, DAO, and repository for workout template CRUD with exercise name resolution via ExerciseRepository
- TemplateListViewModel and TemplateEditorViewModel with full CRUD state management, Koin DI wiring, and KoinHelper iOS getters
- SwiftUI template management UI with list/editor/picker views, inline target editing, drag-and-drop reorder, and ViewModel-driven form validation via KMP NativeCoroutines flow observation
- Room v3 data layer with 5 entities for active session crash recovery and completed workout history, WorkoutRepository with domain-clean boundary, and Koin DI wiring
- Sealed class state machine ViewModel with rest timer, elapsed ticker, per-set Room persistence, crash recovery resume, and hasActiveSession flow for SwiftUI
- SwiftUI workout execution flow with set logging, inline rest timer, haptic feedback, exercise jumping, and crash recovery resume prompt wired to hasActiveSessionFlow
- Extended DAO with 5 history queries, added DataStore Preferences for weight unit, created WeightUnit enum with KMP-safe integer formatting, and WorkoutRepository history methods
- Created WorkoutHistoryViewModel and SettingsViewModel, extended WorkoutSessionViewModel with previous performance Map and weight unit, wired all components into Koin DI with iOS KoinHelper accessors
- SwiftUI views for workout history browsing (list + detail), kg/lbs settings toggle, previous performance inline display, and navigation integration from the Workout tab

---
