# Phase 2: Template Management - Context

**Gathered:** 2026-03-28
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver full CRUD for workout templates. Users can create, edit, delete, and organize workout templates — each template has a name and an ordered list of exercises with configurable targets (sets, reps, weight, rest period). The template list becomes the primary content on the Workout tab, replacing the Phase 1 empty state.

</domain>

<decisions>
## Implementation Decisions

### Workout Tab Layout
- **D-01:** Template list becomes the Workout tab home screen, replacing the Phase 1 empty state (WorkoutEmptyStateView). If no templates exist, show an empty state with a "Create Template" CTA.
- **D-02:** "Browse Exercises" moves from being the primary CTA to being accessible within the template creation/editing flow (exercise picker).

### Exercise Picker Flow
- **D-03:** Reuse the existing ExerciseCatalogView in a picker/selection mode when adding exercises to a template. Same anatomy SVG filter and search, but tapping an exercise adds it to the template rather than navigating to detail.
- **D-04:** Exercises are added one at a time. After selecting an exercise, the user returns to the template editor where they can configure targets for that exercise and add more.

### Target Configuration
- **D-05:** Target configuration (sets, reps, weight, rest period) is done inline in the template editor view. Each exercise row in the template shows editable target fields.
- **D-06:** Weight stored as integer kg×10 (matching gymtracker's `target_weight_kg_x10` pattern). Display logic converts for the user. Lbs support deferred to Phase 4 (NAV-02, NAV-03).
- **D-07:** Rest period stored in seconds (matching gymtracker's `rest_period_sec`).
- **D-08:** Sensible defaults when adding an exercise to a template: 3 sets, 10 reps, 0 kg (user must set weight), 90 seconds rest.

### Data Model
- **D-09:** Two new Room entities following gymtracker's schema: `WorkoutTemplateEntity` (id, name, createdAt, updatedAt) and `TemplateExerciseEntity` (id, templateId, exerciseId, targetSets, targetReps, targetWeightKgX10, restPeriodSec, exerciseOrder).
- **D-10:** Exercise order is an integer field. Reordering updates the `exerciseOrder` of affected rows.

### Delete Confirmation
- **D-11:** Template deletion uses a standard iOS destructive alert dialog ("Delete Template? This cannot be undone.").

### Drag-and-Drop Reordering
- **D-12:** SwiftUI List with `.onMove` modifier for drag-and-drop exercise reordering within a template (TMPL-05). KMP ViewModel exposes a `moveExercise(from, to)` function that updates order indices.

### Claude's Discretion
- Database migration strategy (Room auto-migration vs manual)
- ViewModel state management patterns (follow Phase 1 patterns with StateFlow + @NativeCoroutinesState)
- Navigation between template list → template editor → exercise picker (follow Phase 1 navigation patterns)
- Template name validation rules (non-empty, reasonable length)
- Whether to use SwiftUI sheets or full-screen navigation for template creation/editing

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Gymtracker Data Model Reference
- `/Users/olli/schenanigans/gymtracker/api/src/repository/templates.rs` — Template and TemplateExercise structs: field names, types, and relationships to mirror in Room entities
- `/Users/olli/schenanigans/gymtracker/api/src/repository/workout.rs` — WorkoutTemplate and WorkoutTemplateExercise structs (alternate view of template data)

### Existing Codebase (Phase 1 patterns)
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt` — Room database definition; needs new DAOs for template entities
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/ExerciseEntity.kt` — Entity pattern to follow for new template entities
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ExerciseRepository.kt` — Repository pattern (interface + impl with seeding) to follow for TemplateRepository
- `shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt` — Koin DI wiring pattern for new DAOs, repositories, and ViewModels
- `shared/src/iosMain/kotlin/com/pumpernickel/di/KoinHelper.kt` — iOS KoinHelper pattern for exposing new ViewModels to SwiftUI
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/exercises/ExerciseCatalogViewModel.kt` — ViewModel pattern with @NativeCoroutinesState for iOS observation

### iOS UI Patterns (Phase 1)
- `iosApp/iosApp/Views/MainTabView.swift` — Tab navigation; Workout tab needs to change from empty state to template list
- `iosApp/iosApp/Views/Exercises/ExerciseCatalogView.swift` — Exercise catalog UI to be reused/adapted for exercise picker mode
- `iosApp/iosApp/Views/Common/WorkoutEmptyStateView.swift` — Current workout tab home; will be replaced by template list (with its own empty state)
- `iosApp/iosApp/Views/Anatomy/AnatomyPickerView.swift` — Anatomy SVG picker; reused in exercise picker for muscle group filtering

### Project Specs
- `.planning/REQUIREMENTS.md` — Phase 2 requirements: TMPL-01 through TMPL-05
- `.planning/ROADMAP.md` — Phase 2 success criteria and scope
- `CLAUDE.md` — Technology stack and version constraints

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **ExerciseCatalogView + ExerciseCatalogViewModel**: Full exercise browsing with search and anatomy SVG filtering — reuse in picker mode for template exercise selection
- **AnatomyPickerView**: Anatomy SVG picker for muscle group filtering — carries over per Phase 1 D-12 (used everywhere)
- **ExerciseRepository**: Exercise data access — template exercise picker will query this
- **Room database + Koin DI**: Established patterns for entities, DAOs, repositories, and ViewModel wiring
- **KoinHelper**: Pattern for exposing KMP ViewModels to SwiftUI
- **@NativeCoroutinesState / @NativeCoroutines**: Pattern for iOS StateFlow observation via KMP-NativeCoroutines

### Established Patterns
- **Architecture**: KMP shared business logic (ViewModels, repositories, Room DAOs) + SwiftUI for iOS UI
- **State management**: ViewModel with MutableStateFlow, exposed as StateFlow with @NativeCoroutinesState
- **DI**: Koin with `sharedModule` in commonMain, `platformModule` as expect/actual, `KoinHelper` for iOS
- **Navigation**: SwiftUI NavigationStack with NavigationLink
- **Data mapping**: Entity → Domain model via extension function (e.g., `ExerciseEntity.toDomain()`)

### Integration Points
- **AppDatabase**: Needs new abstract DAO functions for TemplateDao — triggers Room schema version bump
- **SharedModule (Koin)**: Register new DAOs, TemplateRepository, and template ViewModels
- **KoinHelper**: Add getters for new template ViewModels
- **MainTabView**: Workout tab content changes from WorkoutEmptyStateView to TemplateListView
- **ExerciseCatalogView**: Needs a "picker mode" variant or wrapper for template exercise selection

</code_context>

<specifics>
## Specific Ideas

- Template data model mirrors gymtracker's proven schema (template → exercises with targets and order)
- Weight stored as kg×10 integer (gymtracker pattern) — avoids floating point, enables exact comparisons
- Exercise picker reuses the anatomy SVG component established as a core UI element in Phase 1
- The Workout tab transitions from being an exercise browser to a template manager — this is the natural home for the workout starting point that Phase 3 will build on

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 02-template-management*
*Context gathered: 2026-03-28*
