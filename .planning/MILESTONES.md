# Milestones

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
