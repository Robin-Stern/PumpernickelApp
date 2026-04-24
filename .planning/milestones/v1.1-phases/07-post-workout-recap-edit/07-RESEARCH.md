# Phase 7: Post-Workout Recap & Edit - Research

**Researched:** 2026-03-29
**Domain:** KMP ViewModel state machine extension + SwiftUI recap screen
**Confidence:** HIGH

## Summary

This phase adds a `Reviewing` state to the existing `WorkoutSessionState` sealed class, creating an intermediate step between Active and Finished. When the user taps "Finish Workout," the ViewModel transitions to `Reviewing` (carrying all exercise and set data) instead of immediately saving. A new SwiftUI recap screen renders all completed exercises and sets in a scrollable list, with tap-to-edit capability reusing the existing edit set sheet. A "Save Workout" button triggers the actual persistence logic (currently in `finishWorkout()`) and transitions to `Finished`.

The implementation is well-scoped: the Kotlin side requires one new sealed class variant, a refactor of `finishWorkout()` into two methods (enter review + save), and no new dependencies. The Swift side requires one new view function in `WorkoutSessionView` and a new branch in the existing `sessionState` switch. All editing infrastructure (`editCompletedSet()`, `editSetSheet`, `WorkoutSetRow`) already exists and works across exercise/set indices.

**Primary recommendation:** Split `finishWorkout()` into `enterReview()` (transitions Active to Reviewing) and `saveReviewedWorkout()` (moves from Reviewing to Finished via existing save logic). Build the recap screen as a `@ViewBuilder` function inside `WorkoutSessionView` following the existing `activeWorkoutView()` pattern.

<user_constraints>

## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** "Finish Workout" button transitions directly to `Reviewing` state (no intermediate "Done!" screen). The firmware's two-step flow (WorkoutFinishState -> WorkoutRecapState) is simplified to a single transition for mobile. The current `finishWorkout()` method will be refactored to enter `Reviewing` instead of immediately saving and jumping to `Finished`.
- **D-02:** "Save Workout" action in recap triggers the actual save-to-history logic and transitions to `Finished` state. This matches the firmware's WorkoutRecapState -> WorkoutSaveState flow.
- **D-03:** Scrollable vertical list with exercise name headers and per-set detail rows underneath each exercise. Matches firmware WorkoutRecapState's nested list pattern (exercise title -> sub-items). Only exercises with completed sets are shown (matching existing `finishWorkout()` filter logic).
- **D-04:** Each exercise section shows: exercise name, number of completed sets, and individual set rows with reps and weight.
- **D-05:** Each set row shows "Set N: X reps @ Y.Y kg" (unit-aware via existing WeightUnit). Rows are tappable to open edit sheet (FLOW-02).
- **D-06:** Reuse the existing wheel picker edit sheet pattern from WorkoutSessionView (editSetSheet) for editing sets in recap. Same interaction: tap set -> sheet with reps/weight pickers -> save. The `editCompletedSet()` ViewModel method already supports editing any set by exercise/set index.

### Claude's Discretion
- Visual styling of recap screen (colors, spacing, typography) -- follow existing workout screen patterns
- Whether to show workout duration and total stats in recap header -- reasonable to include as summary context
- Back navigation behavior from recap (return to active workout or prevent it) -- use judgment based on firmware pattern where BACK from recap returns to the workout

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope

</user_constraints>

<phase_requirements>

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FLOW-01 | User sees post-workout recap screen with all exercises and sets before saving | Reviewing sealed class state carries full exercise/set data; recap view renders all completed exercises with set details |
| FLOW-02 | User can edit any set's reps and weight from the recap screen | Existing `editCompletedSet()` ViewModel method works for any exercise/set index; existing `editSetSheet` SwiftUI pattern reusable directly |

</phase_requirements>

## Standard Stack

No new libraries or dependencies required. This phase uses only existing stack components:

### Core (already in project)
| Library | Version | Purpose | Role in This Phase |
|---------|---------|---------|-------------------|
| Kotlin StateFlow | stdlib | State management | Reviewing state emitted via existing `_sessionState` MutableStateFlow |
| KMPNativeCoroutinesAsync | 1.0.2 | Swift-Kotlin bridge | `asyncSequence(for: viewModel.sessionStateFlow)` already observes all state changes including new Reviewing |
| Room KMP | 2.8.4 | Persistence | Existing save logic in WorkoutRepository; active session kept intact during Reviewing for crash recovery |
| SwiftUI | iOS 17+ | UI | Recap screen built with existing SwiftUI patterns (ScrollView, VStack, ForEach) |

**No `npm install` / `gradle sync` changes needed.**

## Architecture Patterns

### Recommended Approach

```
WorkoutSessionState sealed class (Kotlin):
  Idle -> Active -> Reviewing -> Finished
                      ^             |
                      |             |
                  finishWorkout()  saveReviewedWorkout()
```

### Pattern 1: Sealed Class State Extension

**What:** Add `Reviewing` as a new data class in the `WorkoutSessionState` sealed hierarchy. It carries the same exercise data as `Active` plus computed summary fields.

**When to use:** When the state machine needs a new distinct phase with different UI rendering.

**Example:**

```kotlin
// In WorkoutSessionViewModel.kt, inside sealed class WorkoutSessionState
data class Reviewing(
    val templateName: String,
    val exercises: List<SessionExercise>,
    val startTimeMillis: Long,
    val durationMillis: Long,
    val totalSets: Int,
    val totalExercises: Int
) : WorkoutSessionState()
```

**Key design choice:** `Reviewing` stores `exercises: List<SessionExercise>` (not `CompletedExercise`) because `editCompletedSet()` already operates on the Active state's `SessionExercise` list by exercise/set index. By keeping the same data structure, the edit method works with minimal changes -- it just needs to also handle the `Reviewing` state type.

### Pattern 2: Method Split (finishWorkout -> enterReview + saveReviewedWorkout)

**What:** Refactor `finishWorkout()` into two methods. `enterReview()` stops timers and transitions to Reviewing. `saveReviewedWorkout()` performs the save logic currently in `finishWorkout()`.

**When to use:** When existing method conflates two distinct operations.

**Example:**

```kotlin
fun enterReview() {
    viewModelScope.launch {
        val active = _sessionState.value as? WorkoutSessionState.Active ?: return@launch
        timerJob?.cancel()
        elapsedJob?.cancel()

        val endTimeMillis = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val durationMillis = endTimeMillis - active.startTimeMillis

        // Filter to exercises with completed sets (same as current finishWorkout filter)
        val exercisesWithSets = active.exercises.filter { ex ->
            ex.sets.any { it.isCompleted }
        }

        _sessionState.value = WorkoutSessionState.Reviewing(
            templateName = active.templateName,
            exercises = exercisesWithSets,
            startTimeMillis = active.startTimeMillis,
            durationMillis = durationMillis,
            totalSets = exercisesWithSets.sumOf { ex -> ex.sets.count { it.isCompleted } },
            totalExercises = exercisesWithSets.size
        )
    }
}

fun saveReviewedWorkout() {
    viewModelScope.launch {
        val reviewing = _sessionState.value as? WorkoutSessionState.Reviewing ?: return@launch
        // Build CompletedWorkout from reviewing state exercises
        // ... (existing save logic from finishWorkout)
    }
}
```

### Pattern 3: Recap View as ViewBuilder Function

**What:** Add the recap screen as a `@ViewBuilder` private function inside `WorkoutSessionView`, matching the existing `activeWorkoutView(_:)` pattern.

**When to use:** When a new state branch needs full-screen rendering within the same parent view.

**Example:**

```swift
// In WorkoutSessionView.body, extend the Group:
} else if let reviewing = sessionState as? WorkoutSessionState.Reviewing {
    recapView(reviewing)
}

@ViewBuilder
private func recapView(_ reviewing: WorkoutSessionState.Reviewing) -> some View {
    ScrollView {
        VStack(spacing: 20) {
            // Summary header
            // Exercise sections with set rows
            // Save button
        }
    }
}
```

### Pattern 4: editCompletedSet Reuse Across States

**What:** Extend `editCompletedSet()` to work with both `Active` and `Reviewing` states. The method currently casts `_sessionState.value as? WorkoutSessionState.Active` -- it needs an additional branch for `Reviewing`.

**When to use:** When an existing method must operate on data that now lives in two state types.

**Example:**

```kotlin
fun editCompletedSet(exerciseIndex: Int, setIndex: Int, reps: Int, weightKgX10: Int) {
    viewModelScope.launch {
        val currentState = _sessionState.value

        when (currentState) {
            is WorkoutSessionState.Active -> {
                // existing logic
            }
            is WorkoutSessionState.Reviewing -> {
                val updatedExercises = currentState.exercises.mapIndexed { eIdx, exercise ->
                    if (eIdx == exerciseIndex) {
                        exercise.copy(sets = exercise.sets.map { set ->
                            if (set.setIndex == setIndex) {
                                set.copy(actualReps = reps, actualWeightKgX10 = weightKgX10)
                            } else set
                        })
                    } else exercise
                }
                workoutRepository.updateSetValues(exerciseIndex, setIndex, reps, weightKgX10)
                _sessionState.value = currentState.copy(
                    exercises = updatedExercises,
                    totalSets = updatedExercises.sumOf { ex -> ex.sets.count { it.isCompleted } }
                )
            }
            else -> return@launch
        }
    }
}
```

### Anti-Patterns to Avoid
- **Creating a separate ViewModel for recap:** The recap is part of the workout session lifecycle. A separate ViewModel would require duplicating state and observation logic. Keep it in WorkoutSessionViewModel.
- **Navigating to a separate SwiftUI screen for recap:** The recap is a state of the workout session, not a navigation destination. Using state-driven rendering (as established) keeps the flow clean and avoids navigation stack complexity.
- **Converting SessionExercise to CompletedExercise for recap display:** This would break `editCompletedSet()` which operates on `SessionExercise`. Keep the same data type through the Reviewing state.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Edit set UI | New edit sheet for recap | Existing `editSetSheet` in WorkoutSessionView | Already built, tested, handles wheel pickers, unit-awareness, save callback |
| Set row display | New set row component for recap | Existing `WorkoutSetRow` component | Already handles formatting, tap callbacks, visual styling |
| Weight formatting | Custom formatting in recap | Existing `WeightUnit.formatWeight(kgX10:)` | Unit-aware, handles kg/lbs, handles decimal display |
| Duration formatting | New duration formatter | Existing `formatElapsed()` / `formatDuration()` in WorkoutFinishedView | Already handles h/m/s formatting |
| Active session persistence | New persistence for Reviewing state | Existing Room active session (keep intact) | Reviewing state keeps active session alive; crash during review = resume to Active on restart |

**Key insight:** This phase is primarily a refactor and UI addition. Over 80% of the required functionality already exists -- the `editCompletedSet` method, the `editSetSheet` UI, `WorkoutSetRow`, weight formatting, and save logic. The main new code is the Reviewing state variant and the recap layout.

## Common Pitfalls

### Pitfall 1: editCompletedSet Only Handles Active State
**What goes wrong:** The existing `editCompletedSet()` casts to `WorkoutSessionState.Active`. If called from the recap screen (Reviewing state), the cast fails silently and edits are lost.
**Why it happens:** Method was written before Reviewing state existed.
**How to avoid:** Update `editCompletedSet()` to handle both `Active` and `Reviewing` states with a `when` expression.
**Warning signs:** Tapping a set in recap, editing values, and seeing no change reflected.

### Pitfall 2: Active Session Cleared Prematurely
**What goes wrong:** If `Reviewing` transitions clear the active session from Room, a crash during review loses all workout data.
**Why it happens:** Temptation to "clean up" during state transition.
**How to avoid:** Keep the active session in Room intact throughout the Reviewing state. Only call `clearActiveSession()` inside `saveReviewedWorkout()` after successful save -- exactly as the current `finishWorkout()` does.
**Warning signs:** Force-quit during recap, reopen app, no resume prompt.

### Pitfall 3: Timer Jobs Not Cancelled on Review Entry
**What goes wrong:** Rest timer or elapsed ticker continues running in background after entering recap, causing state updates on a stale Active state.
**Why it happens:** `enterReview()` forgets to cancel timer jobs.
**How to avoid:** Cancel both `timerJob` and `elapsedJob` in `enterReview()`, same as current `finishWorkout()`.
**Warning signs:** Console logs showing state updates after recap is displayed.

### Pitfall 4: Kotlin Sealed Class New Variant Not Handled in Swift
**What goes wrong:** Adding `Reviewing` to the sealed class in Kotlin creates a new type. If SwiftUI's `Group` body doesn't match on it, the Idle/loading spinner shows instead of the recap screen.
**Why it happens:** Swift doesn't enforce exhaustive checking on Kotlin sealed classes (they appear as class hierarchies).
**How to avoid:** Add explicit `else if let reviewing = sessionState as? WorkoutSessionState.Reviewing` check before the Idle fallback in `WorkoutSessionView.body`.
**Warning signs:** Seeing "Starting workout..." spinner after tapping Finish Workout.

### Pitfall 5: Exercise Index Mismatch After Filtering
**What goes wrong:** `enterReview()` filters exercises to only those with completed sets. But `editCompletedSet()` uses exercise indices from the original Active state list. If exercise 0 was skipped, the filtered list's index 0 maps to original index 1.
**Why it happens:** Filtering changes the index mapping between the full exercise list and the display list.
**How to avoid:** When building the Reviewing state, either (a) keep the full exercise list and filter only in the UI, or (b) store a mapping of display-index to original-index. Option (a) is simpler -- filter in the view, not in the state.
**Warning signs:** Editing set in recap changes a different exercise's set.

### Pitfall 6: Back Navigation from Recap
**What goes wrong:** User taps back from recap and expects to return to the workout, but the state machine doesn't support Reviewing -> Active transition.
**Why it happens:** The firmware supports BACK from recap (returns to workout). Mobile should too per Claude's discretion notes.
**How to avoid:** Implement a `cancelReview()` method that restarts timers and transitions back to Active state. Alternatively, use `.navigationBarBackButtonHidden(true)` and provide explicit "Back to Workout" and "Save" buttons.
**Warning signs:** User accidentally finishes, can't get back to do more sets.

## Code Examples

### Reviewing State Data Class (Kotlin)

```kotlin
// Source: Based on existing WorkoutSessionState sealed class pattern in WorkoutSessionViewModel.kt
data class Reviewing(
    val templateId: Long,
    val templateName: String,
    val exercises: List<SessionExercise>,  // Full list, including exercises with 0 completed sets
    val startTimeMillis: Long,
    val durationMillis: Long
) : WorkoutSessionState()
```

Note: Store the full exercise list and compute totalSets/totalExercises dynamically. This avoids the index mismatch pitfall (Pitfall 5) and keeps data consistent after edits.

### Recap View SwiftUI Structure

```swift
// Source: Based on existing activeWorkoutView pattern in WorkoutSessionView.swift
@ViewBuilder
private func recapView(_ reviewing: WorkoutSessionState.Reviewing) -> some View {
    ScrollView {
        VStack(spacing: 20) {
            // Header: workout name + summary stats
            VStack(spacing: 8) {
                Text("Workout Recap")
                    .font(.title2.weight(.bold))
                Text(reviewing.templateName)
                    .font(.headline)
                    .foregroundColor(.secondary)
                // Duration, total sets, total exercises
            }

            // Exercise sections
            let exercisesWithSets = reviewing.exercises.enumerated().filter { (_, ex) in
                ex.sets.contains { $0.isCompleted }
            }
            ForEach(Array(exercisesWithSets), id: \.offset) { originalIndex, exercise in
                VStack(alignment: .leading, spacing: 8) {
                    // Exercise header
                    Text(exercise.exerciseName)
                        .font(.headline)
                    Text("\(exercise.sets.filter { $0.isCompleted }.count) sets")
                        .font(.subheadline)
                        .foregroundColor(.secondary)

                    // Set rows (tappable for edit)
                    ForEach(exercise.sets.filter { $0.isCompleted }, id: \.setIndex) { set in
                        WorkoutSetRow(
                            setIndex: set.setIndex,
                            actualReps: set.actualReps?.int32Value ?? 0,
                            actualWeightKgX10: set.actualWeightKgX10?.int32Value ?? 0,
                            isCompleted: true,
                            weightUnit: weightUnit,
                            onTap: {
                                editExerciseIndex = Int32(originalIndex)
                                editSetIndex = set.setIndex
                                editSelectedReps = Int(set.actualReps?.int32Value ?? 0)
                                editSelectedWeightKgX10 = snapToWeightStep(Int(set.actualWeightKgX10?.int32Value ?? 0))
                                showEditSheet = true
                            }
                        )
                    }
                }
                .padding()
                .background(Color(UIColor.secondarySystemBackground))
                .cornerRadius(12)
            }

            // Save button (prominent, at bottom per D-02)
            Button("Save Workout") {
                viewModel.saveReviewedWorkout()
            }
            .font(.body.weight(.semibold))
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .background(Color(red: 0.4, green: 0.733, blue: 0.416))
            .cornerRadius(12)
            .padding(.horizontal, 32)
        }
        .padding()
    }
    .navigationTitle("Recap")
    .navigationBarTitleDisplayMode(.inline)
    .navigationBarBackButtonHidden(true)
}
```

### editCompletedSet Extension for Reviewing State

```kotlin
// Source: Extension of existing editCompletedSet in WorkoutSessionViewModel.kt
fun editCompletedSet(exerciseIndex: Int, setIndex: Int, reps: Int, weightKgX10: Int) {
    viewModelScope.launch {
        val currentState = _sessionState.value
        val exercises = when (currentState) {
            is WorkoutSessionState.Active -> currentState.exercises
            is WorkoutSessionState.Reviewing -> currentState.exercises
            else -> return@launch
        }

        val updatedExercises = exercises.mapIndexed { eIdx, exercise ->
            if (eIdx == exerciseIndex) {
                exercise.copy(
                    sets = exercise.sets.map { set ->
                        if (set.setIndex == setIndex) {
                            set.copy(actualReps = reps, actualWeightKgX10 = weightKgX10)
                        } else set
                    }
                )
            } else exercise
        }

        workoutRepository.updateSetValues(exerciseIndex, setIndex, reps, weightKgX10)

        _sessionState.value = when (currentState) {
            is WorkoutSessionState.Active -> currentState.copy(exercises = updatedExercises)
            is WorkoutSessionState.Reviewing -> currentState.copy(exercises = updatedExercises)
            else -> return@launch
        }
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| finishWorkout() saves immediately | enterReview() -> saveReviewedWorkout() two-step | This phase | Users can verify data before it is persisted to history |
| Active -> Finished (2 states) | Active -> Reviewing -> Finished (3 states) | This phase | Adds review opportunity without breaking existing state machine |

**Not deprecated/outdated:** All existing patterns (sealed class state machine, NativeCoroutinesState observation, sheet presentation) remain current and unchanged.

## Open Questions

1. **Back navigation from recap to active workout**
   - What we know: Firmware supports BACK from recap returning to the workout. Context.md marks this as Claude's discretion.
   - What's unclear: Whether the elapsed ticker should resume and rest state should reset when returning to Active from Reviewing.
   - Recommendation: Support going back. `cancelReview()` restores the original Active state (including timers). Show a "Back to Workout" toolbar button. This matches firmware behavior and prevents user frustration from accidental finishes.

2. **Crash recovery during Reviewing state**
   - What we know: The active session in Room is kept intact during Reviewing (not cleared until save). On crash and reopen, `checkForActiveSession()` will find it.
   - What's unclear: Should resume after crash during review go back to Active or Reviewing?
   - Recommendation: Resume to Active (the safe default). The Reviewing state is transient -- the user simply re-taps "Finish Workout" to see the recap again. This avoids needing to persist Reviewing state metadata to Room.

## Project Constraints (from CLAUDE.md)

- **Tech stack:** Kotlin Multiplatform + Compose Multiplatform (phase uses KMP ViewModel, SwiftUI for iOS-first UI)
- **Platform focus:** iOS first (recap screen built in SwiftUI)
- **Storage:** Local/offline only (Room KMP, no changes needed for this phase)
- **Scope:** Workout feature only (recap is part of workout flow)
- **MVVM pattern:** ViewModel exposes StateFlow, Compose/SwiftUI observes via `collectAsState()` / `asyncSequence(for:)`
- **Sealed class state machine:** Established pattern -- extend, don't replace
- **NativeCoroutinesState:** All ViewModel StateFlows use `@NativeCoroutinesState` annotation for Swift observation
- **Weight handling:** Integer math only (kgX10), unit-aware via `WeightUnit.formatWeight(kgX10:)`

## Sources

### Primary (HIGH confidence)
- `WorkoutSessionViewModel.kt` -- Current sealed class state machine, `finishWorkout()` logic, `editCompletedSet()` method
- `WorkoutSessionView.swift` -- Current SwiftUI state rendering pattern, editSetSheet, completedSetsSection, observation pattern
- `WorkoutFinishedView.swift` -- Current finished state display (remains as post-save summary)
- `WorkoutSetRow.swift` -- Reusable set row component with tap handler
- `WorkoutRepository.kt` -- Save/clear active session logic, `updateSetValues()` method
- `WorkoutRecapState.cpp` (firmware) -- Behavioral spec: scrollable exercise list with sub-items, "Save Workout" action
- `WorkoutSaveState.cpp` (firmware) -- Behavioral spec: save triggers from recap, success/failure handling
- `WorkoutFinishState.cpp` (firmware) -- Behavioral spec: DONE screen -> recap transition, BACK behavior

### Secondary (MEDIUM confidence)
- `CONTEXT.md` -- User decisions D-01 through D-06, discretion areas

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- no new dependencies, all existing tools
- Architecture: HIGH -- direct extension of established sealed class state machine pattern, all integration points verified in source code
- Pitfalls: HIGH -- identified from code analysis of actual cast sites, data flow, and timer management

**Research date:** 2026-03-29
**Valid until:** 2026-04-28 (stable -- no external dependency changes)
