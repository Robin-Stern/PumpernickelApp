# Phase 8: Mid-Workout Exercise Reorder - Context

**Gathered:** 2026-03-29
**Status:** Ready for planning

<domain>
## Phase Boundary

Allow users to reorder pending exercises mid-workout and skip exercises. Implements the firmware's exercise reorder concept adapted for mobile: only pending (future) exercises can be reordered, completed and current exercises are locked. Crash recovery must preserve the reordered exercise sequence.

</domain>

<decisions>
## Implementation Decisions

### Reorder Mechanism
- **D-01:** Use in-memory list reorder (swap items in the `exercises` list within `WorkoutSessionState.Active`) rather than the firmware's `exerciseOrder[]` indirection array. The indirection pattern exists in firmware because C arrays can't be easily resized — Kotlin lists support direct reorder via `removeAt`/`add`. After reorder, `currentExerciseIndex` stays unchanged (it points to the current exercise, and only items after it move). This is simpler and avoids the translation layer between logical and physical indices.
- **D-02:** Only exercises after `currentExerciseIndex` (pending exercises) can be reordered. Completed exercises and the current exercise are locked in place — matching firmware's `workoutQueuePos` boundary.

### Reorder UI Gesture
- **D-03:** Use SwiftUI `.onMove` modifier with an `EditButton` pattern, matching the existing TemplateEditorView drag-reorder pattern. Show pending exercises in a reorderable list; completed and current exercises are displayed but not movable.
- **D-04:** Reorder UI is accessed from the workout session screen — either inline in the exercise overview or via a dedicated sheet/screen showing the exercise order. The exercise list should visually distinguish completed (greyed/checked), current (highlighted), and pending (draggable) exercises, mirroring the firmware's visual hierarchy.

### Skip Exercise Behavior
- **D-05:** Skip advances the cursor to the next exercise (`currentExerciseIndex + 1`). The skipped exercise retains 0 completed sets. Per FLOW-07 UAT criteria, skipped exercises appear in history with 0 completed sets (matching `saveReviewedWorkout()` filter that already excludes exercises with no completed sets — so skipped exercises are naturally excluded from saved history).
- **D-06:** Skip is available as an action button or menu item during the active workout — not restricted to a context menu (Phase 9 will add the full context menu, but skip should work standalone in Phase 8 to satisfy FLOW-07).

### Crash Recovery for Exercise Order
- **D-07:** Persist the current exercise order to Room so crash recovery reconstructs the correct sequence. Add an `exerciseOrder` text column to `active_sessions` storing a comma-separated list of exercise indices (e.g., "0,1,3,2,4"), or use a dedicated ordering table. Room migration 3→4 required.
- **D-08:** On resume after crash, `resumeWorkout()` reads the persisted exercise order and reconstructs the `exercises` list in that order before overlaying completed sets. If no order is persisted (pre-migration sessions), fall back to template order.

### Claude's Discretion
- Visual styling of the reorder screen (follow existing workout screen patterns)
- Whether to use a sheet or inline section for the exercise reorder UI
- Animation details for drag-and-drop reorder
- Whether skip confirmation is needed (firmware skips immediately with no confirmation)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Firmware Reference (Behavioral Spec)
- `/Users/olli/schenanigans/gymtracker/firmware/src/statemachine/states/WorkoutExerciseListState.cpp` — Firmware exercise list: cursor-based navigation, completed/current/pending visual hierarchy, only pending items selectable for move
- `/Users/olli/schenanigans/gymtracker/firmware/src/statemachine/states/WorkoutExerciseListState.h` — Exercise list state interface
- `/Users/olli/schenanigans/gymtracker/firmware/src/statemachine/states/WorkoutExerciseMoveState.cpp` — Firmware move mode: swap-based reorder with cancel/confirm, exerciseOrder[] indirection array manipulation
- `/Users/olli/schenanigans/gymtracker/firmware/src/development/tests/TestReorder.cpp` — Firmware reorder tests (behavioral reference for edge cases)

### KMP ViewModel (Modify)
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt` — Add `reorderExercise(from, to)`, `skipExercise()` methods; update `resumeWorkout()` to respect persisted order

### Room Entities (Modify)
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/ActiveSessionEntity.kt` — Add `exerciseOrder` column for crash recovery persistence
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/ActiveSessionSetEntity.kt` — exerciseIndex references may need adjustment after reorder
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/WorkoutSessionDao.kt` — Add update query for exercise order persistence
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt` — Version 3→4 migration

### SwiftUI Views (Modify/Create)
- `iosApp/iosApp/Views/Workout/WorkoutSessionView.swift` — Add reorder UI and skip button
- `iosApp/iosApp/Views/Templates/TemplateEditorView.swift` — Reference for `.onMove` pattern (reuse approach)

### Domain Models (Reference)
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/WorkoutSession.kt` — SessionExercise, SessionSet data classes
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/WorkoutRepository.kt` — Repository methods for active session management

### Requirements
- `.planning/REQUIREMENTS.md` — FLOW-03 (drag reorder pending exercises), FLOW-04 (crash recovery integrity), FLOW-07 (skip exercise)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **TemplateEditorView.swift `.onMove` (line 57):** SwiftUI drag-reorder pattern with `moveExercise(from:to:)` — adapt for workout session exercise list
- **TemplateEditorViewModel.moveExercise() (line 146):** In-memory list reorder with order normalization — model for WorkoutSessionViewModel.reorderExercise()
- **jumpToExercise() (WorkoutSessionViewModel.kt:377):** Existing method to jump cursor to specific exercise — basis for skip logic (jump to currentExerciseIndex + 1)
- **computeNextCursor() (WorkoutSessionViewModel.kt:534):** Cursor advancement logic — reference for skip behavior

### Established Patterns
- **Sealed class state machine:** WorkoutSessionState (Idle/Active/Reviewing/Finished) — reorder operates within Active state, no new state variant needed
- **Room crash recovery:** ActiveSessionEntity + ActiveSessionSetEntity with cursor tracking — extend with exerciseOrder column
- **AutoMigration:** Schema v2→v3 used `@AutoMigration` — v3→v4 may use same if the change is additive (new column with default)
- **NativeCoroutinesState observation:** All ViewModel StateFlows observed via asyncSequence in SwiftUI

### Integration Points
- **WorkoutSessionState.Active.exercises:** The list that gets reordered (only items after currentExerciseIndex)
- **ActiveSessionEntity:** Needs exerciseOrder column for crash recovery
- **WorkoutRepository:** Needs `updateExerciseOrder()` method for Room persistence
- **resumeWorkout():** Must read persisted order and apply it when reconstructing exercises from template

</code_context>

<specifics>
## Specific Ideas

- Firmware shows completed exercises greyed out, current with play icon, pending with drag handles — mobile equivalent: completed items with checkmark/grey, current with accent highlight, pending with drag handle icon and `.onMove` capability
- Firmware exercise list is a separate state (WorkoutExerciseListState). Mobile can present this as a sheet over the workout screen for quick access
- Skip is immediate in firmware (no confirmation dialog) — keep same behavior for Phase 8; Phase 9 will add the context menu that wraps skip alongside other actions

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 08-mid-workout-exercise-reorder*
*Context gathered: 2026-03-29*
