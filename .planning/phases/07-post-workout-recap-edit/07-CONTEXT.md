# Phase 7: Post-Workout Recap & Edit - Context

**Gathered:** 2026-03-29
**Status:** Ready for planning

<domain>
## Phase Boundary

Add a post-workout recap screen where users review all completed exercises and sets before saving. Users can edit any set's reps and weight from the recap screen. This introduces a new `Reviewing` sealed class state between Active and Finished in WorkoutSessionState.

</domain>

<decisions>
## Implementation Decisions

### Recap Trigger & Flow
- **D-01:** "Finish Workout" button transitions directly to `Reviewing` state (no intermediate "Done!" screen). The firmware's two-step flow (WorkoutFinishState → WorkoutRecapState) is simplified to a single transition for mobile. The current `finishWorkout()` method will be refactored to enter `Reviewing` instead of immediately saving and jumping to `Finished`.
- **D-02:** "Save Workout" action in recap triggers the actual save-to-history logic and transitions to `Finished` state. This matches the firmware's WorkoutRecapState → WorkoutSaveState flow.

### Recap Layout
- **D-03:** Scrollable vertical list with exercise name headers and per-set detail rows underneath each exercise. Matches firmware WorkoutRecapState's nested list pattern (exercise title → sub-items). Only exercises with completed sets are shown (matching existing `finishWorkout()` filter logic).
- **D-04:** Each exercise section shows: exercise name, number of completed sets, and individual set rows with reps and weight.

### Set Detail & Editing
- **D-05:** Each set row shows "Set N: X reps @ Y.Y kg" (unit-aware via existing WeightUnit). Rows are tappable to open edit sheet (FLOW-02).
- **D-06:** Reuse the existing wheel picker edit sheet pattern from WorkoutSessionView (editSetSheet) for editing sets in recap. Same interaction: tap set → sheet with reps/weight pickers → save. The `editCompletedSet()` ViewModel method already supports editing any set by exercise/set index.

### Claude's Discretion
- Visual styling of recap screen (colors, spacing, typography) — follow existing workout screen patterns
- Whether to show workout duration and total stats in recap header — reasonable to include as summary context
- Back navigation behavior from recap (return to active workout or prevent it) — use judgment based on firmware pattern where BACK from recap returns to the workout

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Firmware Reference (Behavioral Spec)
- `/Users/olli/schenanigans/gymtracker/firmware/src/statemachine/states/WorkoutRecapState.cpp` — Firmware recap screen: scrollable list, "Save Workout" at top, exercise titles with set count and best weight sub-items
- `/Users/olli/schenanigans/gymtracker/firmware/src/statemachine/states/WorkoutRecapState.h` — Recap state interface: cursor-based navigation, highlightable items
- `/Users/olli/schenanigans/gymtracker/firmware/src/statemachine/states/WorkoutFinishState.cpp` — Firmware finish state: "DONE!" screen that precedes recap (simplified to direct transition in mobile)
- `/Users/olli/schenanigans/gymtracker/firmware/src/statemachine/states/WorkoutSaveState.cpp` — Firmware save state: actual persistence after recap confirmation

### KMP ViewModel (Modify)
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt` — WorkoutSessionState sealed class (add Reviewing), finishWorkout() (refactor to enter Reviewing), editCompletedSet() (already works for recap editing)

### SwiftUI Views (Modify/Create)
- `iosApp/iosApp/Views/Workout/WorkoutSessionView.swift` — Main workout view: add Reviewing state branch, existing editSetSheet pattern to reuse
- `iosApp/iosApp/Views/Workout/WorkoutFinishedView.swift` — Current finished view (remains as post-save summary)

### Domain Models (Reference)
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/WorkoutSession.kt` — SessionExercise, SessionSet data classes (used in Reviewing state)
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/CompletedWorkout.kt` — CompletedWorkout, CompletedExercise, CompletedSet (save target)

### Requirements
- `.planning/REQUIREMENTS.md` — FLOW-01 (recap screen), FLOW-02 (edit sets from recap)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **editSetSheet (WorkoutSessionView.swift:346-415):** Wheel picker edit sheet for modifying completed sets — reuse directly for recap editing
- **editCompletedSet() (WorkoutSessionViewModel.kt:330-352):** ViewModel method for updating set values by exercise/set index — already functional for recap
- **completedSetsSection (WorkoutSessionView.swift:312-341):** UI pattern for displaying completed sets with tap-to-edit — adapt for recap's all-exercises view
- **WorkoutSetRow:** Existing component for rendering individual set rows with tap handlers
- **WorkoutFinishedView:** Post-save summary screen — remains unchanged, shown after recap save

### Established Patterns
- **Sealed class state machine:** WorkoutSessionState (Idle/Active/Finished) — add Reviewing variant following same pattern
- **NativeCoroutinesState + asyncSequence observation:** All ViewModel StateFlows observed via asyncSequence(for: viewModel.xFlow) in SwiftUI
- **Sheet presentation:** .sheet(isPresented:) for modal editing (edit set sheet, exercise overview)
- **Weight formatting:** WeightUnit.formatWeight(kgX10:) for unit-aware display

### Integration Points
- **WorkoutSessionView.body:** Switch on sessionState — add case for Reviewing
- **WorkoutSessionViewModel.finishWorkout():** Refactor to transition to Reviewing instead of saving immediately
- **New method needed:** saveReviewedWorkout() that performs the current finishWorkout() save logic from Reviewing state
- **Room active session:** Reviewing state should keep active session intact (crash recovery still works until actual save)

</code_context>

<specifics>
## Specific Ideas

- Firmware recap shows "Save Workout" as the top item (highlighted, selectable). Mobile equivalent: prominent "Save Workout" button at bottom of recap screen (iOS convention).
- Firmware shows per-exercise sub-items: "{N} sets" and "Best: {X.X} kg". Mobile can show richer per-set detail since screen space allows it, and FLOW-02 requires individual set tappability.
- Firmware recap is read-only for exercises (browse-only). Mobile extends this with tap-to-edit capability per FLOW-02.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 07-post-workout-recap-edit*
*Context gathered: 2026-03-29*
