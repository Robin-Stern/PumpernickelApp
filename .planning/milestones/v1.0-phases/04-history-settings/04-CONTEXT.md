# Phase 4: History & Settings - Context

**Gathered:** 2026-03-29
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver workout history browsing (list + detail), previous performance display during active workouts, and a kg/lbs unit toggle. Users can review past workouts sorted by date, tap into full detail, see what they did last time during an active session, and switch between kg and lbs globally. This phase completes the v1 workout tracking loop.

</domain>

<decisions>
## Implementation Decisions

### History Navigation
- **D-01:** History is accessible from the Workout tab — a "History" button/icon on the template list screen navigates to the history list. No new tabs needed.
- **D-02:** History list is a separate pushed view from the Workout tab, not a tab replacement or section within the template list.

### History List Presentation
- **D-03:** Each history row shows: date (formatted, e.g., "Today", "Yesterday", "Mar 27"), template name, exercise count, total volume (sum of reps × weight across all sets), and duration.
- **D-04:** List sorted by date, newest first (HIST-01). Uses existing `CompletedWorkoutDao.getAllWorkouts()` Flow which already sorts DESC.
- **D-05:** Total volume calculated as sum of (actualReps × actualWeightKgX10) across all sets in the workout, displayed converted to the user's selected unit.

### History Detail View
- **D-06:** Tapping a history entry pushes a detail view showing the full workout: header with name, date, duration, total volume, followed by exercise sections each listing their sets with reps and weight (HIST-03).
- **D-07:** Detail view layout mirrors the structure: exercise name as section header, sets listed below with set number, reps, and weight.

### Previous Performance Display
- **D-08:** During an active workout, each exercise shows the previous performance inline — a subtitle or secondary text showing what the user did last time for that exercise (HIST-04).
- **D-09:** "Previous performance" is sourced from the most recent completed workout that used the same template. Shows per-exercise: last session's sets with reps and weight (e.g., "Last: 3×10 @ 50.0 kg").
- **D-10:** If no previous workout exists for the template, no previous performance is shown (no empty state needed — just absent).

### Weight Unit Settings
- **D-11:** Settings accessible via a gear icon on the Workout tab (top-right navigation bar). Opens a settings sheet/view.
- **D-12:** Settings screen contains a kg/lbs toggle (NAV-02). Minimal settings screen for now — only the unit toggle.
- **D-13:** Internal storage remains kg×10 integer (Phase 2 D-06). Unit toggle is display-only — all weight values are converted at the presentation layer when displaying.
- **D-14:** Selected unit persisted via DataStore Preferences (lightweight key-value store, no Room needed).
- **D-15:** Unit preference applied globally: history list volumes, history detail weights, active workout set values, template editor weights (NAV-03). Conversion factor: 1 kg = 2.20462 lbs.

### Claude's Discretion
- Volume calculation display format (e.g., "1,250 kg" vs "1.2t" — use reasonable formatting)
- Date formatting specifics (relative dates for recent, absolute for older)
- Whether settings sheet is a SwiftUI sheet or pushed navigation view
- DAO query strategy for fetching previous performance (by templateId or by exerciseId)
- Whether to add a WorkoutHistoryViewModel or extend existing ViewModels
- DataStore Preferences setup and Koin integration approach

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Existing Data Layer (Phase 3 — workout storage)
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/CompletedWorkoutEntity.kt` — Completed workout table: id, templateId, name, startTimeMillis, endTimeMillis, durationMillis
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/CompletedWorkoutExerciseEntity.kt` — Workout exercises: workoutId, exerciseId, exerciseName, exerciseOrder
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/CompletedWorkoutSetEntity.kt` — Workout sets: workoutExerciseId, setIndex, actualReps, actualWeightKgX10
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/CompletedWorkoutDao.kt` — Existing DAO with getAllWorkouts() Flow and insert methods. Needs new queries for detail and previous performance.
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/CompletedWorkout.kt` — Domain models: CompletedWorkout, CompletedExercise, CompletedSet
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/WorkoutRepository.kt` — Repository with saveCompletedWorkout(). Needs new query methods for history retrieval.

### Active Workout Integration (Phase 3 — previous performance target)
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt` — Active workout VM. Needs previous performance data injected for HIST-04.

### UI Patterns (Phases 1-3)
- `iosApp/iosApp/Views/MainTabView.swift` — Tab navigation; Workout tab needs settings gear icon
- `iosApp/iosApp/Views/Templates/TemplateListView.swift` — Template list on Workout tab; needs History button/icon
- `iosApp/iosApp/Views/Workout/` — Active workout views; need previous performance display

### Architecture Patterns
- `shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt` — Koin DI registration for new ViewModels and repositories
- `shared/src/iosMain/kotlin/com/pumpernickel/di/KoinHelper.kt` — iOS ViewModel exposure pattern

### Project Specs
- `.planning/REQUIREMENTS.md` — Phase 4 requirements: HIST-01, HIST-02, HIST-03, HIST-04, NAV-02, NAV-03
- `.planning/ROADMAP.md` — Phase 4 success criteria and scope
- `CLAUDE.md` — Technology stack (DataStore Preferences 1.1.x for settings)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **CompletedWorkoutDao**: Already has `getAllWorkouts()` returning Flow sorted DESC — foundation for HIST-01. Needs new queries for detail fetching (exercises + sets by workoutId) and previous performance (last workout by templateId).
- **CompletedWorkout domain model**: Full domain model with exercises and sets already exists — can be reused for history detail display.
- **WorkoutRepository**: Has `saveCompletedWorkout()` — needs new query methods (`getWorkoutHistory()`, `getWorkoutDetail(id)`, `getPreviousPerformance(templateId)`).
- **KoinHelper + SharedModule**: Established DI patterns for adding new ViewModels.
- **SwiftUI list patterns**: TemplateListView provides the pattern for building the history list view.

### Established Patterns
- **Architecture**: KMP shared business logic + SwiftUI iOS UI
- **State management**: ViewModel with MutableStateFlow + @NativeCoroutinesState, *Flow suffix for iOS
- **DI**: Koin with sharedModule, KoinHelper for iOS
- **Data layer**: Entity → Domain model via toDomain(), repository interface + impl
- **Navigation**: SwiftUI NavigationStack + NavigationLink, pushed views
- **Weight format**: kg×10 integer throughout (Phase 2 D-06, Phase 3 D-12)

### Integration Points
- **CompletedWorkoutDao**: Needs new @Query methods for fetching workout with exercises/sets, and for previous performance by templateId
- **WorkoutRepository**: Needs new interface methods + impl for history queries
- **WorkoutSessionViewModel**: Needs previous performance data flow for HIST-04
- **TemplateListView**: Needs History button in navigation bar
- **MainTabView / Workout tab**: Needs settings gear icon
- **No schema migration needed** — completed_workouts tables already exist from Phase 3

</code_context>

<specifics>
## Specific Ideas

- Previous performance query should match by templateId to show the most recent workout using the same template — this gives the most relevant comparison
- Weight unit conversion is purely at the presentation layer — no storage changes, no migrations, no data duplication
- DataStore Preferences is the right tool for the unit setting — lighter than Room for a single key-value preference
- Total volume is a useful summary metric that makes each history row informative at a glance

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 04-history-settings*
*Context gathered: 2026-03-29*
