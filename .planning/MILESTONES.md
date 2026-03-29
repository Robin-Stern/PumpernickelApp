# Milestones

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
