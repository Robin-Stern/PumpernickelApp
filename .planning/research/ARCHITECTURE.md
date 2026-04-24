# Architecture Patterns: v1.1 Workout Polish & Firmware Parity

**Domain:** Feature integration into existing KMP ViewModel + SwiftUI architecture
**Researched:** 2026-03-29
**Overall confidence:** HIGH (based on direct codebase analysis + firmware reference)

## Executive Summary

The v1.1 features split cleanly into three categories by where they live architecturally:

1. **SwiftUI-only changes** (no KMP ViewModel modifications): scroll wheel pickers, minimal "doing set" screen, UI polish
2. **KMP ViewModel extensions** (new methods/state, no state machine restructure): auto-increment logic, personal best queries, exercise reorder
3. **State machine evolution** (new states or transitions): post-workout recap/edit, abandon guards

The existing `WorkoutSessionState` sealed class (Idle/Active/Finished) needs one new state: `Reviewing` -- a post-finish editing phase before the workout is persisted to history. The `Active` state gains an `exerciseOrder` field for mid-workout reorder. Everything else layers onto the existing architecture without structural changes.

## Recommended Architecture

### Current Architecture (v1.0)

```
SwiftUI (WorkoutSessionView)
  |-- observes StateFlow via NativeCoroutinesAsync
  |-- @State for local UI (input fields, sheet toggles)
  v
KMP ViewModel (WorkoutSessionViewModel)
  |-- sealed class: Idle | Active | Finished
  |-- Active holds: exercises[], cursor, restState
  |-- methods: startWorkout, completeSet, skipRest, finishWorkout, etc.
  v
Repository (WorkoutRepository)
  |-- active session CRUD (crash recovery)
  |-- completed workout persistence
  v
Room DAOs (WorkoutSessionDao, CompletedWorkoutDao)
```

### Target Architecture (v1.1)

```
SwiftUI (WorkoutSessionView) -- EXTENDED
  |-- scroll wheel pickers replace TextField inputs
  |-- minimal "doing set" view (exercise name + set number only)
  |-- abandon guard alerts (.alert modifier)
  |-- context menu (actionSheet / confirmationDialog)
  |-- personal best display (reads new StateFlow)
  v
KMP ViewModel (WorkoutSessionViewModel) -- EXTENDED
  |-- sealed class: Idle | Active | Reviewing | Finished
  |                                 ^^^^^^^^^
  |                                 NEW STATE
  |-- Active gains: exerciseOrder[] for reorder
  |-- new methods: reorderExercise, getPersonalBest, finishForReview, saveReviewedWorkout, editReviewedSet
  |-- auto-increment logic: embedded in completeSet cursor advance
  v
Repository (WorkoutRepository) -- EXTENDED
  |-- new: getPersonalBest(exerciseId) query
  |-- active session now persists exerciseOrder
  v
Room DAOs -- EXTENDED
  |-- CompletedWorkoutDao: new PB query
  |-- WorkoutSessionDao: exerciseOrder persistence (optional, for crash recovery)
```

### Component Boundaries

| Component | Responsibility | Communicates With |
|-----------|---------------|-------------------|
| `WorkoutSessionView` (SwiftUI) | UI rendering, gesture handling, picker presentation, alerts | ViewModel via StateFlow observation |
| `ScrollWheelPicker` (SwiftUI) | iOS-native Picker(.wheel) for reps/weight input | Parent view via @Binding |
| `WorkoutRecapView` (SwiftUI) | Post-workout review/edit UI | ViewModel via Reviewing state |
| `WorkoutSessionViewModel` (KMP) | State machine, business logic, data orchestration | Repository layer |
| `WorkoutRepository` (KMP) | Data access abstraction | Room DAOs |
| `CompletedWorkoutDao` (Room) | Personal best queries, workout history | SQLite via Room |
| `WorkoutSessionDao` (Room) | Active session crash recovery | SQLite via Room |

### Data Flow

**Set completion with auto-increment:**
```
User scrolls picker -> SwiftUI @State updates -> tap "Complete Set"
  -> ViewModel.completeSet(reps, weightKgX10)
    -> persist to Room (crash recovery)
    -> compute next cursor
    -> emit new Active state with next set pre-filled from ACTUAL values of just-completed set
  -> SwiftUI observes new state
    -> picker values update to previous set's actuals (auto-increment)
```

**Post-workout recap flow:**
```
User taps "Finish Workout"
  -> ViewModel.finishForReview()
    -> cancel timers
    -> emit Reviewing state (holds editable workout data, NOT yet persisted)
  -> SwiftUI renders WorkoutRecapView
    -> user can edit set values, review exercises
    -> tap "Save" -> ViewModel.saveReviewedWorkout()
      -> persist to completed_workouts
      -> clear active session
      -> emit Finished state (summary display)
    -> tap "Back to Workout" -> ViewModel.returnToActive()
      -> re-emit Active state (resume workout)
```

**Exercise reorder during workout:**
```
User opens context menu -> "Reorder Exercises"
  -> SwiftUI shows reorder sheet (reads exercises + exerciseOrder from Active state)
  -> user drags/reorders
  -> ViewModel.reorderExercises(newOrder: List<Int>)
    -> updates Active.exerciseOrder
    -> persists order to Room (crash recovery)
    -> emits updated Active state
  -> UI reflects new exercise sequence
```

## Feature-by-Feature Integration Analysis

### 1. Scroll Wheel Pickers

**Category:** SwiftUI-only

**What changes:**
- Replace `TextField` with `Picker(.wheel)` in `setInputSection()` and `editSetSheet`
- Reps picker: 0-50 integer range
- Weight picker: 0-999.5 in 2.5 steps (matching firmware's 25-increment on kgX10)

**KMP changes:** NONE. The ViewModel already receives `reps: Int` and `weightKgX10: Int` from `completeSet()`. The picker is purely a SwiftUI input mechanism.

**Implementation detail:**
```swift
// Reps picker
Picker("Reps", selection: $repsValue) {
    ForEach(0...50, id: \.self) { Text("\($0)") }
}
.pickerStyle(.wheel)
.frame(width: 80, height: 120)

// Weight picker: generate values 0, 25, 50, ..., 10000 (0.0 to 1000.0 kg)
// Display as formatted weight, bind to kgX10 Int
Picker("Weight", selection: $weightKgX10) {
    ForEach(Array(stride(from: 0, through: 10000, by: 25)), id: \.self) { val in
        Text(formatWeight(val)).tag(val)
    }
}
.pickerStyle(.wheel)
.frame(width: 120, height: 120)
```

**Firmware reference:** The firmware uses a rotary encoder with 2.5kg steps for weight and 1-step for reps. The SwiftUI Picker(.wheel) provides the same tactile scroll experience on iOS.

**Risk:** Picker with 401 weight values (0 to 10000 by 25) may lag on older devices. If so, reduce range or use a custom wheel. Test on physical device early.

### 2. Auto-Increment Set Logic

**Category:** KMP ViewModel change (minor)

**What changes in KMP:**
The `completeSet()` method currently advances the cursor and the SwiftUI `prefillInputs()` reads `targetReps`/`targetWeightKgX10` from the template. Auto-increment means the NEXT set should pre-fill with the ACTUAL values of the just-completed set, not the template targets.

**Current behavior (v1.0):**
```kotlin
// SessionSet has targetReps and targetWeightKgX10 from template
// SwiftUI prefillInputs() reads: currentSet.targetReps, currentSet.targetWeightKgX10
```

**New behavior (v1.1) -- Recommended approach:**
In `completeSet()`, after advancing the cursor, update the next set's target values to match the just-completed set's actuals. This keeps the data flow simple: SwiftUI still reads `targetReps`/`targetWeightKgX10` but those now reflect the previous set's actuals.

```kotlin
fun completeSet(reps: Int, weightKgX10: Int) {
    viewModelScope.launch {
        val active = _sessionState.value as? WorkoutSessionState.Active ?: return@launch
        // ... existing set completion logic ...

        // Auto-increment: update next set's targets to this set's actuals
        val nextCursor = computeNextCursor(exIdx, setIdx, active.exercises)
        val updatedExercises = /* existing update */ .let { exercises ->
            val (nextExIdx, nextSetIdx) = nextCursor
            if (nextExIdx == exIdx && nextSetIdx > setIdx) {
                // Same exercise, next set -> auto-increment from actuals
                exercises.mapIndexed { eIdx, exercise ->
                    if (eIdx == nextExIdx) {
                        exercise.copy(sets = exercise.sets.map { set ->
                            if (set.setIndex == nextSetIdx && !set.isCompleted) {
                                set.copy(targetReps = reps, targetWeightKgX10 = weightKgX10)
                            } else set
                        })
                    } else exercise
                }
            } else exercises // Different exercise: use template targets (no auto-increment)
        }
        // ... emit state ...
    }
}
```

**Firmware reference confirms this approach:** The firmware does `prevSet.reps` / `prevSet.weight_kg_x10` for subsequent sets within the same exercise, and template targets for the first set. This is exactly the behavior described above.

**SwiftUI changes:** NONE beyond what already exists. `prefillInputs()` already reads `targetReps`/`targetWeightKgX10`, which will now contain auto-incremented values.

### 3. Minimal "Doing Set" Screen

**Category:** SwiftUI-only

**What changes:**
The firmware has a `WorkoutStartSetState` that shows just the set number, exercise name, and "press when done" -- a minimal screen displayed WHILE the user is physically performing the set (rack the weight, do reps, then tap to enter values).

In the mobile app, this translates to a simplified view state within `activeWorkoutView`:
- Show: exercise name, "SET 3", muscle group (optional)
- Hide: input pickers, completed sets list, all other UI
- Single tap to transition to the set entry pickers

**Implementation:** Add a `@State private var isDoingSet: Bool = false` toggle in SwiftUI. When true, render a minimal center-screen display. When the user taps (or a timer-based auto-transition fires), flip to the picker entry view.

**KMP changes:** NONE. This is a UI presentation mode within the same `Active` ViewModel state. The ViewModel does not need to know whether the user is looking at the minimal screen or the picker screen.

**Firmware reference:** `WorkoutStartSetState` shows "SET [number]" + exercise name. SELECT transitions to `WorkoutSetEntryState` (pickers). Direct mapping to an `isDoingSet` toggle in SwiftUI.

### 4. Post-Workout Recap/Edit

**Category:** State machine evolution (new `Reviewing` state)

**What changes in KMP:**

Add a new state to the sealed class:
```kotlin
sealed class WorkoutSessionState {
    data object Idle : WorkoutSessionState()
    data class Active(/* existing */) : WorkoutSessionState()

    data class Reviewing(
        val templateId: Long,
        val templateName: String,
        val exercises: List<SessionExercise>,  // Editable
        val startTimeMillis: Long,
        val durationMillis: Long
    ) : WorkoutSessionState()

    data class Finished(/* existing */) : WorkoutSessionState()
}
```

**New ViewModel methods:**
```kotlin
// Transition: Active -> Reviewing (replaces direct Active -> Finished)
fun finishForReview() {
    // Cancel timers, compute duration, emit Reviewing state
    // DO NOT persist to completed_workouts yet
}

// Edit a set during review
fun editReviewedSet(exerciseIndex: Int, setIndex: Int, reps: Int, weightKgX10: Int) {
    // Update Reviewing state's exercises list
}

// Save reviewed workout: Reviewing -> Finished
fun saveReviewedWorkout() {
    // Persist to completed_workouts (same logic as current finishWorkout)
    // Clear active session
    // Emit Finished state
}

// Go back to workout: Reviewing -> Active
fun returnToActive() {
    // Re-emit Active state, restart timers
}
```

**Flow change:**
- **v1.0:** `Active` -> `finishWorkout()` -> `Finished` (auto-saves)
- **v1.1:** `Active` -> `finishForReview()` -> `Reviewing` -> `saveReviewedWorkout()` -> `Finished`
- **v1.1 escape hatch:** `Reviewing` -> `returnToActive()` -> `Active` (resume workout)

**SwiftUI changes:** New `WorkoutRecapView` that renders the `Reviewing` state with editable exercise/set cards. Replaces or wraps the existing `WorkoutFinishedView` flow.

**Firmware reference:** The firmware has `WorkoutFinishState` -> `WorkoutRecapState` -> `WorkoutSaveState`. The recap shows exercises with their sets/best weight and lets the user scroll through before saving. The `Reviewing` state maps to this.

**Active session cleanup:** The active session in Room should NOT be cleared when entering `Reviewing`. Only clear it when `saveReviewedWorkout()` or `discardWorkout()` is called. This preserves crash recovery through the review phase.

### 5. Mid-Workout Exercise Reorder

**Category:** KMP ViewModel extension (Active state modification)

**What changes in KMP:**

Add `exerciseOrder` to the `Active` state:
```kotlin
data class Active(
    val templateId: Long,
    val templateName: String,
    val exercises: List<SessionExercise>,
    val exerciseOrder: List<Int>,  // NEW: indices into exercises[], determines execution order
    val currentQueuePosition: Int, // NEW: position in exerciseOrder
    val currentExerciseIndex: Int,  // Keep: actual index into exercises[] (derived from exerciseOrder[currentQueuePosition])
    val currentSetIndex: Int,
    val startTimeMillis: Long,
    val restState: RestState = RestState.NotResting
) : WorkoutSessionState()
```

**New ViewModel methods:**
```kotlin
// Reorder remaining exercises (cannot reorder already-completed ones)
fun reorderExercises(newOrder: List<Int>) {
    val active = _sessionState.value as? WorkoutSessionState.Active ?: return
    // Validate: completed exercises stay in place, only pending ones can move
    // Update exerciseOrder + persist to Room for crash recovery
    _sessionState.value = active.copy(exerciseOrder = newOrder)
}

// Skip current exercise (move to end of queue) -- firmware "skip" behavior
fun skipCurrentExercise() {
    val active = _sessionState.value as? WorkoutSessionState.Active ?: return
    // Swap current with next in exerciseOrder (firmware behavior)
    // Advance cursor to the new current exercise
}
```

**Impact on existing methods:**
- `computeNextCursor()` must use `exerciseOrder` to determine the next exercise instead of sequential index iteration
- `startWorkout()` initializes `exerciseOrder` as identity: `[0, 1, 2, ..., n-1]`
- `resumeWorkout()` must restore `exerciseOrder` from Room
- `finishForReview()` / `saveReviewedWorkout()` should compact exercises to template order (matching firmware `WorkoutFinishState` behavior)

**Room changes:** Add `exerciseOrder` column to `ActiveSessionEntity` (stored as comma-separated string or JSON). This is a schema migration (version 4).

**SwiftUI changes:** New `ExerciseReorderSheet` with drag-and-drop (SwiftUI `List` with `.onMove`). Only pending exercises (not yet started/completed) can be reordered.

**Firmware reference:** The firmware uses an `exerciseOrder[]` array as an indirection layer. `workoutQueuePos` is the current position in the queue. This is exactly the architecture recommended above. The firmware's `WorkoutExerciseMoveState` allows moving exercises up/down with encoder rotation.

### 6. Abandon Guards

**Category:** Mixed (KMP ViewModel minor + SwiftUI alerts)

**What changes in KMP:**
The ViewModel already has `discardWorkout()`. For "Save & Exit" (the firmware's default abandon option), we need:

```kotlin
// Save current progress as a completed workout and exit
fun saveAndExit() {
    // Same as finishForReview() -> saveReviewedWorkout() but without the review step
    // Or: directly call existing finishWorkout() logic
}
```

**SwiftUI changes:** This is primarily a SwiftUI concern -- intercepting the back navigation gesture and showing a confirmation alert.

```swift
// In activeWorkoutView or its parent:
@State private var showAbandonAlert = false

// Intercept back button
.navigationBarBackButtonHidden(true)
.toolbar {
    ToolbarItem(placement: .navigationBarLeading) {
        Button("Back") {
            let hasCompletedSets = /* check Active state */
            if hasCompletedSets {
                showAbandonAlert = true
            } else {
                viewModel.discardWorkout()
                dismiss()
            }
        }
    }
}
.alert("Abandon Workout?", isPresented: $showAbandonAlert) {
    Button("Save & Exit") { viewModel.saveAndExit(); dismiss() }
    Button("Discard", role: .destructive) { viewModel.discardWorkout(); dismiss() }
    Button("Cancel", role: .cancel) { }
}
```

**Firmware reference:** `WorkoutAbandonConfirmState` offers "Save & Exit" (default, safe) and "Discard". The guard activates only when sets have been confirmed (exactly matching the `hasCompletedSets` check). Crucial detail: Save & Exit routes through `WorkoutFinishState` -> recap flow. For the mobile app, `saveAndExit()` can either go through the recap or save directly -- recommend saving directly (faster UX on mobile).

### 7. Context Menu

**Category:** SwiftUI-only presentation + KMP methods already exist or planned

**What changes:**
The context menu surfaces actions available during a workout:
- **Skip Exercise** -> calls `skipCurrentExercise()` (new, from section 5)
- **Reorder Exercises** -> opens `ExerciseReorderSheet` (new, from section 5)
- **Finish Workout** -> calls `finishForReview()` (new, from section 4)

**SwiftUI implementation:** Use `.confirmationDialog` or a custom sheet triggered from a toolbar button (the existing list.bullet button could transform into this, or add a separate ellipsis button).

**KMP changes:** All methods already covered in sections 4 and 5.

**Firmware reference:** `WorkoutContextMenuState` has "Skip Current Exercise" and "Adjust Order". Direct mapping.

### 8. Personal Best Display

**Category:** KMP data layer + ViewModel extension + SwiftUI display

**What changes in KMP:**

**New DAO query:**
```kotlin
// In CompletedWorkoutDao:
@Query("""
    SELECT MAX(s.actualWeightKgX10)
    FROM completed_workout_sets s
    JOIN completed_workout_exercises e ON s.workoutExerciseId = e.id
    WHERE e.exerciseId = :exerciseId
""")
suspend fun getPersonalBestWeight(exerciseId: String): Int?

// Also useful: best volume (reps * weight) per set
@Query("""
    SELECT MAX(CAST(s.actualReps AS INTEGER) * CAST(s.actualWeightKgX10 AS INTEGER))
    FROM completed_workout_sets s
    JOIN completed_workout_exercises e ON s.workoutExerciseId = e.id
    WHERE e.exerciseId = :exerciseId
""")
suspend fun getPersonalBestVolume(exerciseId: String): Long?
```

**New StateFlow in ViewModel:**
```kotlin
// Map of exerciseId -> personal best weight (kgX10)
private val _personalBests = MutableStateFlow<Map<String, Int>>(emptyMap())
@NativeCoroutinesState
val personalBests: StateFlow<Map<String, Int>> = _personalBests.asStateFlow()
```

Load personal bests in `startWorkout()` / `resumeWorkout()` alongside previous performance loading.

**SwiftUI changes:** Display "PB: 72.5 kg" below/beside the set entry pickers, similar to how previous performance is already shown. Only show when the current weight entry approaches or exceeds the PB.

**Firmware reference:** The firmware stores PB as `trend.averageWeight_kg_x10` (average, not max). Recommend using MAX weight instead -- more motivating and standard in fitness apps. The firmware displays "PB:XX.X" at the bottom of the set entry screen.

## Patterns to Follow

### Pattern 1: State Extensions via Data Class Copy

**What:** Extend existing states by adding fields to data classes rather than creating new sealed class variants.
**When:** Adding data to an existing state (exerciseOrder to Active, personalBests alongside session).
**Example:**
```kotlin
// Good: extend Active with new field
data class Active(
    // ... existing fields ...
    val exerciseOrder: List<Int>,  // New field with default
) : WorkoutSessionState()

// Bad: creating a separate ActiveWithReorder state
```

### Pattern 2: New States for New Lifecycle Phases

**What:** Add new sealed class variants when a genuinely new lifecycle phase exists.
**When:** The user is in a conceptually different mode (reviewing vs actively working out).
**Example:**
```kotlin
// Reviewing is NOT "Active with a flag" -- it's a different lifecycle phase
// Timers are stopped, data is not being collected, user intent is review/edit
data class Reviewing(...) : WorkoutSessionState()
```

### Pattern 3: SwiftUI-Side UI Modes Within Same ViewModel State

**What:** Use `@State` booleans in SwiftUI for UI presentation variants that do not affect business logic.
**When:** The ViewModel's Active state is unchanged, but the UI shows different views (minimal set screen vs. picker entry).
**Example:**
```swift
@State private var isDoingSet = false  // UI-only toggle, no ViewModel involvement
```

### Pattern 4: Indirection Arrays for Reorderable Collections

**What:** Use an order array (`exerciseOrder: List<Int>`) to maintain logical order separate from physical array indices.
**When:** Items can be reordered but their data must remain at stable indices (sets reference exercises by index).
**Why:** Avoids moving data in the exercises array (which would break set references, Room persistence, and crash recovery).

## Anti-Patterns to Avoid

### Anti-Pattern 1: Putting UI State in the ViewModel

**What:** Adding `isDoingSet`, `showAbandonAlert`, `showContextMenu` to the KMP ViewModel.
**Why bad:** These are iOS-specific UI concerns. Adding them to the shared ViewModel couples it to iOS presentation logic and makes the ViewModel harder to test and maintain.
**Instead:** Keep these as `@State` in SwiftUI. The ViewModel exposes data and actions; the view decides how to present them.

### Anti-Pattern 2: Multiple StateFlows for Related State

**What:** Creating separate StateFlows for `exerciseOrder`, `currentQueuePosition`, etc.
**Why bad:** SwiftUI observes each flow independently, causing intermediate states where exerciseOrder has updated but currentQueuePosition has not. This creates UI flicker and potential crashes.
**Instead:** Embed all related state in the `Active` data class. One emission = one consistent snapshot.

### Anti-Pattern 3: Persisting Reviewed Workout Before User Confirms Save

**What:** Writing to `completed_workouts` when entering the Reviewing state.
**Why bad:** The user might edit values or go back to the workout. Early persistence means stale data in history.
**Instead:** Keep reviewed data in the ViewModel's `Reviewing` state. Only persist on explicit "Save" action.

### Anti-Pattern 4: Reordering by Moving Data in the Exercises Array

**What:** Actually reordering `Active.exercises` list elements during mid-workout reorder.
**Why bad:** `ActiveSessionSetEntity` references exercises by `exerciseIndex` (array position). Moving exercises breaks all existing set references and crash recovery.
**Instead:** Use the `exerciseOrder` indirection array. The exercises list stays stable; only the order of traversal changes.

## Build Order (Dependency-Aware)

The features have these dependencies:

```
Auto-increment (2) -> standalone, no deps
Scroll wheel pickers (1) -> standalone, no deps
Personal best display (8) -> standalone (DAO query + StateFlow)
Minimal doing-set screen (3) -> standalone, no deps
Abandon guards (6) -> depends on saveAndExit, which depends on recap flow (4)
Exercise reorder (5) -> depends on exerciseOrder in Active state
Context menu (7) -> depends on skip (5) + finish-for-review (4)
Post-workout recap (4) -> depends on Reviewing state (core state machine change)
```

**Recommended build order:**

1. **Auto-increment** (KMP only, 1-2 hours) -- smallest KMP change, immediate UX win, zero risk
2. **Scroll wheel pickers** (SwiftUI only, 2-3 hours) -- biggest UX upgrade, zero KMP risk
3. **Personal best display** (KMP DAO + StateFlow + SwiftUI, 2-3 hours) -- standalone, motivating feature
4. **Post-workout recap/edit** (KMP state machine + SwiftUI, 4-6 hours) -- core state machine change, must come before abandon guards
5. **Exercise reorder** (KMP Active extension + Room migration + SwiftUI, 4-6 hours) -- introduces exerciseOrder, must come before context menu
6. **Abandon guards** (KMP minor + SwiftUI alerts, 2-3 hours) -- depends on finishForReview from step 4
7. **Context menu** (SwiftUI + wires existing KMP methods, 2-3 hours) -- aggregates skip/reorder/finish actions
8. **Minimal doing-set screen** (SwiftUI only, 1-2 hours) -- pure UI, can slot in anywhere but nice to polish last
9. **General UI polish** (SwiftUI, ongoing) -- validation, keyboard handling, accessibility

**Phase ordering rationale:**
- Steps 1-3 are independent and could be parallelized, but sequential is safer for a solo developer
- Step 4 (recap) is the foundation for steps 6 (abandon guards) and 7 (context menu)
- Step 5 (reorder) is the foundation for step 7 (context menu's skip/reorder actions)
- Step 8 (doing set screen) is pure polish and has no downstream dependencies

## Room Schema Migration

v1.1 requires one schema change: adding `exerciseOrder` to `ActiveSessionEntity` for crash recovery of reordered workouts.

```kotlin
@Entity(tableName = "active_sessions")
data class ActiveSessionEntity(
    @PrimaryKey val id: Long = 1,
    val templateId: Long,
    val templateName: String,
    val currentExerciseIndex: Int,
    val currentSetIndex: Int,
    val currentQueuePosition: Int,  // NEW
    val exerciseOrderJson: String,   // NEW: e.g., "0,1,2,3"
    val startTimeMillis: Long,
    val lastUpdatedMillis: Long
)
```

**Migration:** Room auto-migration or manual `Migration(3, 4)` adding the two columns with defaults (`currentQueuePosition = 0`, `exerciseOrderJson = ""`). Empty exerciseOrderJson means identity order (backward compatible with v1.0 sessions).

## KMP/SwiftUI Boundary Summary

| Feature | KMP Changes | SwiftUI Changes |
|---------|------------|-----------------|
| Scroll wheel pickers | NONE | Replace TextField with Picker(.wheel) |
| Auto-increment | Modify `completeSet()` to update next set targets | NONE (already reads targets) |
| Minimal doing-set screen | NONE | New `@State isDoingSet` + minimal view |
| Post-workout recap/edit | New `Reviewing` state + 3 methods | New `WorkoutRecapView` |
| Exercise reorder | `exerciseOrder` in Active + reorder methods + Room migration | New `ExerciseReorderSheet` |
| Abandon guards | `saveAndExit()` method | `.alert` modifiers on back navigation |
| Context menu | NONE (wires existing methods) | `.confirmationDialog` or sheet |
| Personal best display | New DAO query + `personalBests` StateFlow | Display PB label in set entry |
| UI polish | NONE | Validation, accessibility, keyboard |

## Sources

- Direct codebase analysis of `WorkoutSessionViewModel.kt`, `WorkoutSessionView.swift`, all Room entities and DAOs (HIGH confidence)
- Direct analysis of gymtracker firmware: `WorkoutSetEntryState.cpp`, `WorkoutStartSetState.cpp`, `WorkoutRecapState.cpp`, `WorkoutFinishState.cpp`, `WorkoutAbandonConfirmState.cpp`, `WorkoutContextMenuState.cpp`, `WorkoutExerciseMoveState.cpp` (HIGH confidence)
- SwiftUI Picker(.wheel) behavior from Apple documentation and training data (HIGH confidence)
- Room KMP migration patterns from existing codebase (3 prior migrations) (HIGH confidence)
