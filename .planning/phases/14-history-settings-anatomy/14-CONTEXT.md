# Phase 14: History, Settings & Anatomy - Context

**Gathered:** 2026-03-31
**Status:** Ready for planning

<domain>
## Phase Boundary

Port workout history (list + detail), settings (kg/lbs toggle), and anatomy picker (Canvas-drawn front/back body maps with touch region selection) to Jetpack Compose. Extract anatomy path data from Swift into shared KMP module. Wire remaining screens into NavHost.

</domain>

<decisions>
## Implementation Decisions

### Workout History
- **D-01:** WorkoutHistoryListScreen: flat LazyColumn with Material 3 ListItem. Headline = workout name, supporting = date + duration + exercise/set counts. Tap navigates to detail.
- **D-02:** Empty state: centered icon (History) + "No Workouts Yet" text (matching iOS pattern).
- **D-03:** WorkoutHistoryDetailScreen: TopAppBar with workout name, scrollable exercise sections with per-set rows (reps × weight), unit-aware display.
- **D-04:** Navigate from TemplateListScreen toolbar History icon (already wired in Phase 12).

### Settings
- **D-05:** Settings presented as Material 3 ModalBottomSheet (triggered from TemplateListScreen toolbar gear icon).
- **D-06:** Weight unit toggle uses Material 3 SegmentedButton (two segments: "kg" / "lbs"). Maps to `viewModel.setWeightUnit()`.
- **D-07:** Observe `weightUnit` StateFlow from SettingsViewModel.

### Anatomy Picker
- **D-08:** Extract muscle region path data from `iosApp/Views/Anatomy/MuscleRegionPaths.swift` into shared KMP module at `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/MuscleRegionPaths.kt`. Both platforms consume from shared.
- **D-09:** iOS AnatomyFrontShape/BackShape refactored to import from shared module (lightweight change — same Path data, just different import source).
- **D-10:** Android anatomy picker uses Compose `Canvas` with `drawPath()` for front/back body outlines and muscle regions.
- **D-11:** Touch detection: check which region Path contains the tap point via `Path.contains()` or bounding-box hit test.
- **D-12:** Two body views (front + back) side-by-side in a Row, matching iOS layout.
- **D-13:** Selected region highlighted with primary color, unselected in surface variant. Tap toggles selection.
- **D-14:** "Select" button at bottom, disabled until a region is selected. Confirm callback returns muscle group dbName.
- **D-15:** Presented as ModalBottomSheet from ExerciseCatalogScreen and ExercisePickerScreen (matching iOS `.sheet` presentation).

### Claude's Discretion
- Exact Canvas path coordinate scaling approach
- History list date formatting
- Settings sheet additional items (only unit toggle for now)
- Anatomy body outline stroke width/color

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### iOS Reference
- `iosApp/iosApp/Views/History/WorkoutHistoryListView.swift` — History list with empty state
- `iosApp/iosApp/Views/History/WorkoutHistoryDetailView.swift` — Workout detail with exercise/set rows
- `iosApp/iosApp/Views/Settings/SettingsView.swift` — Settings with kg/lbs toggle
- `iosApp/iosApp/Views/Anatomy/AnatomyPickerView.swift` — Picker layout (front + back + select button)
- `iosApp/iosApp/Views/Anatomy/AnatomyFrontShape.swift` — Front body Shape paths
- `iosApp/iosApp/Views/Anatomy/AnatomyBackShape.swift` — Back body Shape paths
- `iosApp/iosApp/Views/Anatomy/MuscleRegionPaths.swift` — PATH DATA TO EXTRACT (237 lines)

### Shared KMP ViewModels
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/history/WorkoutHistoryViewModel.kt` — History list + detail
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/settings/SettingsViewModel.kt` — Weight unit toggle

### Android Navigation
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/Routes.kt` — WorkoutHistoryDetailRoute already defined
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt` — NavHost to wire

### Requirements
- `.planning/REQUIREMENTS-v1.5.md` — ANDROID-08, ANDROID-09

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- All screen patterns from Phases 12-13: koinViewModel(), collectAsState(), TopAppBar, ListItem, ModalBottomSheet
- TemplateListScreen already has toolbar icons for History and Settings — just needs navigation wiring
- ExerciseCatalogScreen already shows anatomy picker trigger button

### Integration Points
- MainScreen.kt NavHost needs `composable<WorkoutHistoryDetailRoute>` entry
- TemplateListScreen History icon → navigate to WorkoutHistoryListScreen
- TemplateListScreen Settings icon → show SettingsSheet
- ExerciseCatalogScreen/ExercisePickerScreen anatomy button → show AnatomyPickerSheet
- Shared module needs new file: `domain/model/MuscleRegionPaths.kt`

</code_context>

<specifics>
## Specific Ideas

- iOS history uses simple List with NavigationLink — Android uses LazyColumn with clickable ListItem
- iOS settings uses Form with segmented Picker — Android uses SegmentedButton in ModalBottomSheet
- iOS anatomy uses Shape (SwiftUI) with CGPath — Android uses Canvas with Compose Path
- Path data is coordinate arrays for body outline and muscle region shapes — same data, different rendering API
- MuscleRegionPaths.swift defines: body outline points + per-muscle-group polygon points + displayName mapping

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 14-history-settings-anatomy*
*Context gathered: 2026-03-31*
