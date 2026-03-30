# Phase 9: Abandon Guards & Context Menu - Context

**Gathered:** 2026-03-30
**Status:** Ready for planning

<domain>
## Phase Boundary

Add exit confirmation when leaving a workout mid-session and a context menu with quick-access actions (skip, exercise overview, finish workout). This phase wires up UI affordances for capabilities that already exist in the ViewModel — no new KMP logic needed beyond minor glue methods.

</domain>

<decisions>
## Implementation Decisions

### Abandon Trigger
- **D-01:** Add an explicit close/X button (`xmark` SF Symbol) in the leading navigation bar toolbar position to trigger the abandon confirmation dialog. The navigation back button is already hidden (`.navigationBarBackButtonHidden(true)`). This replaces the current skip button position — skip moves to the context menu (D-06). Firmware equivalent: BACK button from any workout screen triggers WorkoutAbandonConfirmState.
- **D-02:** Only show the abandon dialog when 1+ sets have been completed (per FLOW-05 UAT: "Tapping exit with 1+ completed sets shows alert"). If no sets are completed, the X button discards immediately (no data to lose).

### Abandon Options
- **D-03:** Three-option `.confirmationDialog` matching FLOW-05 UAT criteria:
  - **"Save & Exit"** — Calls `enterReview()` then `saveReviewedWorkout()` to persist completed sets to history, then dismisses. Firmware equivalent: selectedOption 0 → WORKOUT_FINISH.
  - **"Discard"** — Calls existing `discardWorkout()` (clears active session, resets to Idle), then dismisses. Firmware equivalent: selectedOption 1 → START. Use `.destructive` button role.
  - **"Cancel"** — Dismisses dialog, returns to workout. Firmware equivalent: BACK from abandon confirm.
- **D-04:** Dialog title: "Abandon Workout?" with message showing progress summary (e.g., "Exercise 2/4, 5 sets completed") matching firmware's `progressStr` format.

### Context Menu Access
- **D-05:** Add a `Menu` view with `ellipsis.circle` SF Symbol in the trailing navigation bar toolbar position. This is the standard iOS pattern for action menus. The existing `list.bullet` exercise overview button moves inside this menu as one of the menu items.

### Context Menu Items
- **D-06:** Menu contains three actions:
  - **"Skip Exercise"** (`forward.fill` icon) — Calls existing `skipExercise()`. Disabled when on the last exercise (matching firmware's `skipDisabled` logic). Moved from the dedicated toolbar button to the menu.
  - **"Exercise Overview"** (`list.bullet` icon) — Opens existing `showExerciseOverview` sheet with reorder/skip capabilities. Subsumes firmware's "Adjust Order" menu item since the overview sheet already has drag reorder.
  - **"Finish Workout"** (`checkmark.circle` icon) — Calls `enterReview()` to go to recap screen. Provides a way to finish early without completing all exercises.

### Claude's Discretion
- Whether to add a destructive styling (red text) to "Discard" in the confirmation dialog — standard iOS pattern, likely yes
- Whether "Finish Workout" in context menu needs its own confirmation or goes straight to recap — firmware goes straight, recommend same
- Animation/transition details for dialog presentation

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Firmware Reference (Behavioral Spec)
- `/Users/olli/schenanigans/gymtracker/firmware/src/statemachine/states/WorkoutAbandonConfirmState.cpp` — Firmware abandon confirm: "Save & Exit" / "Discard" options, progress summary display, BACK cancels
- `/Users/olli/schenanigans/gymtracker/firmware/src/statemachine/states/WorkoutAbandonConfirmState.h` — Abandon state interface: selectedOption toggle, origin state return
- `/Users/olli/schenanigans/gymtracker/firmware/src/statemachine/states/WorkoutContextMenuState.cpp` — Firmware context menu: "Skip Current Exercise" / "Adjust Order", skip disabled check for last exercise
- `/Users/olli/schenanigans/gymtracker/firmware/src/statemachine/states/WorkoutContextMenuState.h` — Context menu interface: skipDisabled flag, origin state return

### KMP ViewModel (Reference — existing methods)
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt` — `discardWorkout()` (line 562), `enterReview()` (line 484), `saveReviewedWorkout()` (line 508), `skipExercise()` (line 456)

### SwiftUI Views (Modify)
- `iosApp/iosApp/Views/Workout/WorkoutSessionView.swift` — Main workout view: toolbar modification (lines 196-213), add `.confirmationDialog`, add `Menu` view. Currently has skip button (leading) and exercise overview button (trailing)

### Requirements
- `.planning/REQUIREMENTS.md` — FLOW-05 (abandon confirmation with 3 options), FLOW-06 (context menu with skip, reorder, finish)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **discardWorkout() (WorkoutSessionViewModel.kt:562):** Already clears active session, resets state to Idle — ready for "Discard" option
- **enterReview() (WorkoutSessionViewModel.kt:484):** Transitions to Reviewing state — used for "Save & Exit" path and "Finish Workout" menu action
- **saveReviewedWorkout() (WorkoutSessionViewModel.kt:508):** Persists reviewed workout to history — completes the "Save & Exit" flow
- **skipExercise() (WorkoutSessionViewModel.kt:456):** Advances cursor past current exercise — menu item calls this directly
- **ExerciseOverviewSheet:** Full exercise overview with sections and drag reorder — opened from menu item
- **showExerciseOverview @State (WorkoutSessionView.swift:25):** Existing boolean toggle for sheet presentation

### Established Patterns
- **Toolbar placement:** Leading position for primary action, trailing for secondary. Currently: skip (leading), overview (trailing)
- **.navigationBarBackButtonHidden(true):** Back button suppressed during workout — X button is the only exit path
- **.sheet(isPresented:):** Modal presentation pattern used for exercise overview and edit set sheets
- **NativeCoroutinesState observation:** All ViewModel StateFlows observed via asyncSequence in SwiftUI

### Integration Points
- **WorkoutSessionView toolbar (lines 196-213):** Replace current leading skip button with X/close button; replace trailing overview button with Menu containing skip, overview, finish
- **New @State needed:** `showAbandonDialog: Bool` for `.confirmationDialog` presentation
- **Completed sets count:** Available from `active.exercises` — iterate to count sets with completedSets > 0 for the D-02 guard and D-04 progress message

</code_context>

<specifics>
## Specific Ideas

- Firmware abandon shows workout name + "Ex X/Y, Z sets" progress string — mobile equivalent in `.confirmationDialog` message parameter
- Firmware context menu has 2 items (Skip, Adjust Order). Mobile adapts to 3 (Skip, Exercise Overview, Finish Workout) because the overview sheet already subsumes "Adjust Order" and early finish is a natural mobile addition
- Firmware skip in context menu swaps exerciseOrder entries — mobile's `skipExercise()` already handles this differently (cursor advancement), so context menu just calls the existing method
- Firmware disables skip when on last exercise — mobile should grey out / disable the menu item similarly

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 09-abandon-guards-context-menu*
*Context gathered: 2026-03-30*
