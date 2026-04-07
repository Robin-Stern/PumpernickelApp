# Phase 12: Exercise Catalog & Templates - Context

**Gathered:** 2026-03-31
**Status:** Ready for planning

<domain>
## Phase Boundary

Port exercise catalog (search, detail, create) and template management (list, editor, exercise picker) screens to Jetpack Compose with Material 3 components. All screens consume existing shared KMP ViewModels. Navigation routes already exist from Phase 11.

</domain>

<decisions>
## Implementation Decisions

### Search UX
- **D-01:** Exercise catalog uses Material 3 SearchBar embedded in TopAppBar for search. Maps to iOS `.searchable()` behavior.
- **D-02:** Search is always visible (not behind a search icon tap), matching iOS `displayMode: .always`.

### List Items
- **D-03:** Use Material 3 `ListItem` composable for exercise lists and template lists — `headlineContent`, `supportingContent`, `leadingContent`, `trailingContent` slots.
- **D-04:** Exercise list items show: exercise name (headline), muscle groups (supporting), equipment icon (leading).
- **D-05:** Template list items show: template name (headline), exercise count + target info (supporting), workout launch button (trailing).

### Filter Chips
- **D-06:** Muscle group filter uses `FilterChip` composables in a `LazyRow` — horizontal scroll, single selection with "All" default. Maps to iOS `filterChipRow`.
- **D-07:** Selected chip uses primary color (#66BB6A), unselected uses surface variant.

### Template Management
- **D-08:** SwipeToDismiss for template delete with confirmation dialog (maps to iOS swipe + confirmation).
- **D-09:** FloatingActionButton for "create template" (maps to iOS `+` toolbar button).
- **D-10:** Tap template row navigates to TemplateEditorView for edit (maps to iOS NavigationLink).
- **D-11:** Template editor uses Material 3 `OutlinedTextField` for name/fields, drag-and-drop list for exercise reorder.

### Exercise Picker
- **D-12:** Exercise picker is a full-screen dialog or navigation destination (not a bottom sheet) — maps to iOS NavigationStack presentation.
- **D-13:** Single-select: tap exercise → callback fires → auto-dismiss. Matches iOS ExercisePickerView behavior (onSelect callback with immediate dismiss).

### Empty State
- **D-14:** Centered column: icon (FitnessCenter) + title + description + "Create Template" button. Maps to iOS WorkoutEmptyStateView.

### Toolbar & Navigation
- **D-15:** TemplateListScreen toolbar: History icon (leading, navigates to history), Settings icon + Create template FAB (trailing). Maps to iOS toolbar layout.
- **D-16:** Use Material 3 TopAppBar with `navigationIcon` and `actions` slots.

### Claude's Discretion
- Material 3 Card vs ListItem choice per screen (ListItem default, Card if visually better)
- Exact typography scale for headlines/body
- Icon choices for exercise equipment types
- Animation transitions between screens
- Drag handle style for template exercise reorder

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### iOS Reference (layout and behavior to match)
- `iosApp/iosApp/Views/Templates/TemplateListView.swift` — Template list layout, toolbar, workout launch, delete confirmation
- `iosApp/iosApp/Views/Templates/TemplateEditorView.swift` — Template editor with exercise picker, drag reorder
- `iosApp/iosApp/Views/Templates/ExercisePickerView.swift` — Exercise selection modal
- `iosApp/iosApp/Views/Exercises/ExerciseCatalogView.swift` — Exercise catalog with search, filter chips, FAB
- `iosApp/iosApp/Views/Exercises/ExerciseDetailView.swift` — Exercise detail with muscle groups
- `iosApp/iosApp/Views/Exercises/CreateExerciseView.swift` — Create custom exercise form
- `iosApp/iosApp/Views/Common/WorkoutEmptyStateView.swift` — Empty state when no templates

### Android Navigation (already created in Phase 11)
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/Routes.kt` — All routes already defined
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt` — NavHost to wire screens into

### Shared KMP ViewModels (consume directly)
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/exercises/ExerciseCatalogViewModel.kt` — Search, filter, list
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/exercises/ExerciseDetailViewModel.kt` — Detail state
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/exercises/CreateExerciseViewModel.kt` — Create form state
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/templates/TemplateListViewModel.kt` — Template CRUD, selection
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/templates/TemplateEditorViewModel.kt` — Editor state
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/settings/SettingsViewModel.kt` — kg/lbs unit for display

### Requirements
- `.planning/REQUIREMENTS-v1.5.md` — ANDROID-03, ANDROID-04

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Routes.kt`: All navigation routes pre-defined with correct parameter types (templateId: Long?, exerciseId: Long)
- `MainScreen.kt`: NavHost already in place — screens just need `composable<XxxRoute>` blocks wired in
- `Theme.kt`: Material 3 theme with accent green available via `MaterialTheme.colorScheme`
- All 5 ViewModels already registered in Koin — use `koinViewModel()` in composables

### Established Patterns (from Phase 11)
- Package structure: `ui/screens/` for screen composables, `ui/navigation/` for routing
- ViewModel injection: `val viewModel: XxxViewModel = koinViewModel()`
- State observation: `val state by viewModel.stateFlow.collectAsState()`
- Navigation: `navController.navigate(XxxRoute(params))`

### Integration Points
- MainScreen.kt NavHost needs `composable<>` entries for each new screen
- TemplateListScreen replaces WorkoutPlaceholderScreen as Workout tab root
- ExerciseCatalog navigates to ExerciseDetail, CreateExercise
- TemplateEditor navigates to ExercisePicker

</code_context>

<specifics>
## Specific Ideas

- iOS TemplateListView has a "Start Workout" button on each template row — port as a filled Material 3 Button or IconButton on the trailing slot
- iOS ExerciseCatalogView has a FAB for "Create Exercise" — use Material 3 FloatingActionButton with Add icon
- iOS ExercisePickerView is presented as a NavigationStack inside a sheet — on Android, navigate to a full screen with back navigation
- iOS template delete uses `.swipeActions` with red background — use Material 3 `SwipeToDismissBox` with red background

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 12-exercise-catalog-templates*
*Context gathered: 2026-03-31*
