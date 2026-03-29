# Domain Pitfalls

**Domain:** v1.1 Workout Polish & Firmware Parity -- scroll wheel pickers, state machine expansion, mid-workout reorder, abandon guards, post-workout recap, context menus, personal bests
**Researched:** 2026-03-29
**Applies to:** Existing KMP + SwiftUI workout app (v1.0 shipped)

---

## Critical Pitfalls

Mistakes that cause rewrites, data corruption, or broken user flows.

### Pitfall 1: Side-by-Side SwiftUI Picker Wheels Have Overlapping Touch Areas

**What goes wrong:** You replace the current reps `TextField` and weight `TextField` with two `.wheel`-style `Picker` views in an `HStack`. The pickers render correctly, but the touch/drag areas overlap. Scrolling the reps picker also scrolls the weight picker, or the left picker's drag area extends off-screen to the left while the right picker's drag area extends off-screen to the right. On some iOS versions, the middle picker (if three exist) becomes entirely unscrollable.

**Why it happens:** SwiftUI's `.wheel` Picker internally uses `UIPickerView`, which claims an intrinsic content size wider than its visible frame. When multiple wheel pickers sit side-by-side in an `HStack`, their gesture recognizer hit areas overlap because UIKit does not clip them to the SwiftUI frame.

**Consequences:** The core input mechanism (reps + weight entry) becomes unusable. This is the single most user-facing feature in v1.1 -- if the pickers do not work, the entire milestone fails.

**Prevention:**
1. Apply `.clipped()` to EACH individual `Picker` view (not just the parent `HStack`). This constrains the visible rendering but does not fully fix the touch area on all iOS versions.
2. Wrap pickers in a `GeometryReader` and explicitly set each picker's `frame(width:)` to `geometry.size.width / numberOfPickers`. Then apply `.clipped()`.
3. Add the `UIPickerView` intrinsic content size override to the iOS app:
   ```swift
   extension UIPickerView {
       open override var intrinsicContentSize: CGSize {
           CGSize(width: UIView.noIntrinsicMetric, height: 150)
       }
   }
   ```
   This tells UIKit the picker has no intrinsic width, forcing it to respect the SwiftUI frame. This is a global override -- place it in the app's `Extensions/` folder.
4. Test on a REAL DEVICE, not just the simulator. Touch area bugs manifest differently on physical hardware.
5. If the native `Picker(.wheel)` approach remains broken, fall back to a `UIViewRepresentable` wrapping `UIPickerView` directly. This gives full control over component count, row height, and gesture handling.

**Detection:** Place two wheel pickers side-by-side, attempt to scroll only the left one. If the right one also scrolls, you have this bug.

**Phase relevance:** First phase -- scroll wheel implementation. This is a blocker that must be prototyped and validated before building the full reps/weight picker UI.

**Confidence:** HIGH -- documented in multiple Apple Developer Forum threads ([thread/690610](https://developer.apple.com/forums/thread/690610), [thread/690791](https://developer.apple.com/forums/thread/690791)) and community posts. Known issue since iOS 15.

---

### Pitfall 2: Sealed Class Expansion Silently Breaks Swift Switch Statements

**What goes wrong:** You add new states to `WorkoutSessionState` (e.g., `Recap`, `Reordering`, `Abandoning`) in the Kotlin sealed class. The Kotlin code compiles fine because `when` is exhaustive. The Swift code in `WorkoutSessionView.swift` also compiles fine because it does not use exhaustive switching -- it uses `if let active = sessionState as? WorkoutSessionState.Active`. The new states silently fall through to the `else` branch (the loading spinner), and the user sees a spinner when they should see the recap screen.

**Why it happens:** Kotlin sealed classes compile to regular Objective-C classes when exported to Swift. Swift cannot perform exhaustive pattern matching on them. The current codebase uses `if let` casting chains:
```swift
if let active = sessionState as? WorkoutSessionState.Active {
    activeWorkoutView(active)
} else if let finished = sessionState as? WorkoutSessionState.Finished {
    WorkoutFinishedView(...)
} else {
    // Idle / loading -- THIS catches ALL unknown states
    ProgressView()
}
```
Any new sealed subclass silently lands in the `else` branch.

**Consequences:** New features appear broken (showing spinner instead of UI), and the cause is non-obvious because there is no compiler warning or runtime error.

**Prevention:**
1. **Before adding any new sealed subclass**, audit ALL Swift files that switch on that sealed class. The current `WorkoutSessionView.swift` has the critical switch at line 37-60.
2. Add explicit `else if let` branches for every new state BEFORE the final `else` fallback.
3. Add a `fatalError("Unknown WorkoutSessionState: \(sessionState)")` or at minimum a `print("WARNING: Unhandled state: \(sessionState)")` in the final `else` branch during development. This makes silent failures loud.
4. Consider restructuring the Swift side to use a helper function that maps ALL known states explicitly, making it harder to forget one:
   ```swift
   @ViewBuilder
   private func viewForState(_ state: WorkoutSessionState) -> some View {
       if let idle = state as? WorkoutSessionState.Idle { ... }
       else if let active = state as? WorkoutSessionState.Active { ... }
       else if let finished = state as? WorkoutSessionState.Finished { ... }
       else if let recap = state as? WorkoutSessionState.Recap { ... }
       else { fatalError("Unhandled state: \(state)") }
   }
   ```
5. **Do NOT adopt SKIE** just for this -- the project explicitly excludes SKIE in CLAUDE.md constraints, and adding a build tool dependency mid-milestone for one pattern is not worth the risk.

**Detection:** Add a new sealed subclass, build and run without updating Swift. If the app shows the wrong UI (spinner) instead of crashing or warning, you have this pitfall.

**Phase relevance:** EVERY phase that adds a new state. Create a checklist: "Did I update the Swift switch for this state?" This applies to adding `Recap`, `Reordering`, and any future states.

**Confidence:** HIGH -- this is a fundamental KMP/ObjC interop limitation. Verified in [Kotlin Slack thread](https://slack-chats.kotlinlang.org/t/451273/i-have-a-kmm-project-with-sealed-classes-and-classes-inside-), [SKIE docs](https://skie.touchlab.co/features/sealed), and [JetBrains YouTrack KT-45204](https://youtrack.jetbrains.com/issue/KT-45204).

---

### Pitfall 3: Mid-Workout Exercise Reorder Invalidates currentExerciseIndex

**What goes wrong:** User is on exercise index 2 (Bench Press). They open the reorder sheet and drag Bench Press from position 2 to position 0. The `currentExerciseIndex` still points at index 2, which is now a different exercise (e.g., Squats). The user returns from the reorder sheet to find they are suddenly logging sets for the wrong exercise. Worse: any sets already persisted to Room are keyed by `exerciseIndex`, so the crash recovery data now maps completed sets to the wrong exercises.

**Why it happens:** The current `WorkoutSessionState.Active` stores `currentExerciseIndex: Int` as a direct array index:
```kotlin
data class Active(
    val exercises: List<SessionExercise>,
    val currentExerciseIndex: Int,  // Direct index into exercises list
    val currentSetIndex: Int,
    ...
)
```
Reordering the `exercises` list changes what index 2 points to, but the `currentExerciseIndex` value is not updated.

**Consequences:** Wrong exercise shown after reorder. Completed sets attributed to wrong exercise in Room. Crash recovery restores corrupted data. This is a data integrity bug, not just a UI glitch.

**Prevention:**
1. **Use an indirection array** (the firmware pattern mentioned in the milestone context). Instead of reordering the `exercises` list itself, maintain a separate `exerciseOrder: List<Int>` that maps display position to original index:
   ```kotlin
   data class Active(
       val exercises: List<SessionExercise>,        // NEVER reordered
       val exerciseOrder: List<Int>,                 // [2, 0, 1] = display order
       val currentExercisePosition: Int,             // Index into exerciseOrder
       val currentSetIndex: Int,
       ...
   )
   ```
   The current exercise is always `exercises[exerciseOrder[currentExercisePosition]]`. Reordering only mutates `exerciseOrder`, never `exercises`.
2. **Alternative: Track by exerciseId, not index.** Store `currentExerciseId: String` instead of `currentExerciseIndex: Int`. After reorder, find the exercise by ID. This is simpler but requires a lookup on every access.
3. **Update Room crash recovery data** when reordering. The current `saveCompletedSet` uses `exerciseIndex` as a key. If exercises can be reordered, this key must either be the original (pre-reorder) index or the exercise ID.
4. **Test the following sequence:** Start workout -> complete 2 sets on exercise 2 -> reorder exercise 2 to position 0 -> verify sets are still attributed to the correct exercise -> force-kill app -> resume -> verify recovered data is correct.

**Detection:** Complete sets on exercise B, reorder so exercise A is where B was, check if the set data follows exercise B or stays at the index.

**Phase relevance:** Must be designed BEFORE implementing reorder. Retrofitting the indirection array onto a direct-index model requires changing every place that reads `currentExerciseIndex` (ViewModel + Swift views + Room persistence).

**Confidence:** HIGH -- this is a well-known array-index-invalidation bug. The milestone context explicitly calls out the "indirection array pattern from firmware" as the solution.

---

### Pitfall 4: Abandon Guard Cannot Intercept NavigationStack Back Gesture

**What goes wrong:** The user swipes from the left edge during an active workout. The `NavigationStack` pops the `WorkoutSessionView` without any confirmation dialog. The workout state in the ViewModel is now orphaned -- the view is gone, but the workout was never finished or discarded. If the user starts a new workout, they may get a "resume previous workout?" prompt for the abandoned one, but the UX is confusing and data may be in an inconsistent state.

**Why it happens:** The current code already uses `.navigationBarBackButtonHidden(true)` (line 61 of `WorkoutSessionView.swift`), which hides the back button AND disables the swipe-back gesture on standard push navigation. However, this means the ONLY way back is a custom back button that you control. The current code has NO custom back button -- the only exit is "Finish Workout." This is correct for the current v1.0 flow, but v1.1 requires an "abandon" option.

The danger emerges if:
(a) Someone removes `.navigationBarBackButtonHidden(true)` during refactoring, re-enabling unguarded back swipe.
(b) The workout session is refactored to use `.sheet()` or `.fullScreenCover()` instead of `navigationDestination`, where `interactiveDismissDisabled` is needed instead.
(c) Future iOS versions change the behavior of `navigationBarBackButtonHidden` with respect to the swipe gesture.

**Consequences:** Unguarded workout abandonment. Orphaned session state. Confused users who accidentally swiped back and lost their workout.

**Prevention:**
1. **Keep `.navigationBarBackButtonHidden(true)` on `WorkoutSessionView`.** This is already correct in the codebase.
2. **Add a custom toolbar button** (e.g., X or "End") that shows a confirmation `Alert`:
   ```swift
   .toolbar {
       ToolbarItem(placement: .navigationBarLeading) {
           Button {
               showAbandonConfirmation = true
           } label: {
               Image(systemName: "xmark")
           }
       }
   }
   .alert("End Workout?", isPresented: $showAbandonConfirmation) {
       Button("Save & Exit") { viewModel.finishWorkout() }
       Button("Discard", role: .destructive) { viewModel.discardWorkout(); dismiss() }
       Button("Cancel", role: .cancel) { }
   }
   ```
3. **If switching to `.fullScreenCover()`** (which might feel more appropriate for the immersive workout experience), use `.interactiveDismissDisabled(true)` to prevent swipe-down dismissal. `.fullScreenCover` does not have a back swipe gesture at all, which is an advantage for this use case.
4. **Add the abandon guard to the ViewModel side too:** The `discardWorkout()` method already exists. Ensure `finishWorkout()` can handle partially-completed workouts (it already does -- it filters to only completed sets).
5. **Test:** Navigate to workout -> try to swipe back from left edge -> verify nothing happens. Try to tap the back area in the nav bar -> verify nothing happens.

**Detection:** Remove `navigationBarBackButtonHidden` temporarily and observe if back swipe is possible. If so, the guard is presentation-layer only and fragile.

**Phase relevance:** Same phase as abandon guards. The ViewModel logic (save/discard) and the SwiftUI guard (prevent back) must ship together.

**Confidence:** HIGH -- the current codebase already prevents this correctly, but the fix is fragile (one line removal breaks it). Verified via [Apple docs](https://developer.apple.com/documentation/swiftui/view/navigationbarbackbuttonhidden(_:)) and [community posts](https://medium.com/@yunchingtan/swiftui-disable-back-swipe-gesture-dynamically-56c32d55cc4d).

---

## Moderate Pitfalls

### Pitfall 5: Weight Picker with 2.5kg Steps Requires Pre-Computed Value Array, Not a Range

**What goes wrong:** You try to create a SwiftUI `Picker` with `ForEach(0...4000, id: \.self)` representing 0.0 to 400.0 kg in 0.1 increments, or `stride(from: 0, through: 10000, by: 25)` for 2.5kg steps with kgX10 storage. The picker has 401 items (0, 25, 50, ..., 10000). This seems manageable, but:
- The picker scrolls sluggishly on older devices due to the number of items.
- The `Picker` value is an `Int` (kgX10), but the displayed text shows "62.5 kg." If the `.tag()` does not match the binding type exactly, selection silently breaks.
- Users find it hard to scroll from 0 to 100kg quickly (40 rows of scrolling in 2.5kg increments).

**Why it happens:** The weight domain (0-1000 kg per the milestone spec) at 2.5kg steps = 401 items. At finer granularity (0.5kg = 2001 items, 0.1kg = 10001 items), performance degrades. The `.wheel` style renders ALL items eagerly (unlike a `List` which is lazy).

**Prevention:**
1. **Use the kgX10 integer as the picker value.** The binding should be `@State private var weightKgX10: Int32`. Each picker row is tagged with the kgX10 value: `.tag(Int32(value))`. Display text converts: `"\(value / 10).\(value % 10)"`.
2. **Split into two pickers** for the weight: a "whole kg" picker (0-200 range) and a "decimal" picker (0, 5 for 0.0/0.5 increments, or 0, 2, 5, 7 for 0.25kg increments). This dramatically reduces the number of rows per picker and makes fast scrolling practical. This is how iOS clock/time pickers work (hours + minutes as separate wheels).
3. **Pre-compute the value array** as a constant, not in a `ForEach` closure:
   ```swift
   private let weightValues: [Int32] = stride(from: 0, through: 10000, by: 25).map { Int32($0) }
   ```
4. **Always use explicit `.tag()`.** SwiftUI's implicit tagging with `ForEach` only works when the `id` type matches the binding type. Type mismatches between `Int`, `Int32`, and `Int64` cause silent selection failures, especially across the KMP/Swift bridge where Kotlin `Int` becomes Swift `Int32`.

**Detection:** Set the picker binding to a value, then scroll to a different value and release. If the picker snaps back to the original value or the binding does not update, you have a tag type mismatch.

**Phase relevance:** Scroll wheel implementation phase. Prototype the weight picker early and test on device.

**Confidence:** HIGH -- tag type mismatches are the number one cause of broken SwiftUI Pickers per [Apple Developer Forums](https://developer.apple.com/forums/thread/689794) and [community guides](https://dev.to/devin-rosario/fix-swiftui-picker-not-updating-selection-common-issues-3da).

---

### Pitfall 6: Picker State Resets When Unrelated ViewModel State Updates

**What goes wrong:** The user is scrolling the weight picker to select 80kg. While the wheel is spinning, the elapsed time ticker updates (every second), causing a `sessionState` re-emission. SwiftUI re-renders the view, and the picker "jumps" back to the previous value, interrupting the user's scroll.

**Why it happens:** The current `WorkoutSessionView` observes `sessionState` via `asyncSequence`, and `sessionState` is re-emitted every second (because `updateRestState` or the elapsed ticker causes a new `Active` copy). Each re-emission triggers a view update. If the picker's binding source is derived from `sessionState`, the picker re-renders with the old value while the user is mid-scroll.

The existing code already has this issue with text fields (the `prefillInputs` method fires on state changes), but text fields are more forgiving -- users type and submit, they do not hold a continuous scroll gesture.

**Consequences:** Maddening UX where the picker fights the user. Users cannot reliably select values.

**Prevention:**
1. **Separate picker state from session state.** The reps and weight picker bindings should be `@State` variables LOCAL to the view, not derived from the ViewModel on every emission. Only sync from ViewModel -> local state when the exercise/set cursor changes (new exercise or new set), not on every state emission.
2. **Use `.onChange(of: active.currentExerciseIndex)` and `.onChange(of: active.currentSetIndex)` to trigger prefill**, not a continuous observation. The current code partially does this (lines 276-281 of `WorkoutSessionView.swift`), but the `observeSessionState` async function ALSO updates inputs (lines 407-417), creating a double-update path.
3. **Remove the input prefill from `observeSessionState`.** The current code has TWO places that update `repsInput`/`weightInput`: the `prefillInputs` method (called from `onAppear`/`onChange`) and the `observeSessionState` observer. Eliminate the duplicate. Keep only the `onChange` path.
4. **For the picker specifically:** Use `@State private var selectedReps: Int32` as the binding. Only write to it when the exercise/set changes. Let the picker own the value during scrolling.

**Detection:** Start a workout, begin scrolling a picker, and watch if it jumps every second (timer tick). If so, state updates are interfering with picker interaction.

**Phase relevance:** Scroll wheel implementation phase. Must be solved as part of the text-field-to-picker migration.

**Confidence:** HIGH -- this is a direct consequence of the current architecture. The existing code's dual input-update paths (lines 276-281 and 407-417 of `WorkoutSessionView.swift`) make this inevitable unless refactored.

---

### Pitfall 7: Post-Workout Recap State Must Be Immutable Snapshot, Not Live

**What goes wrong:** The user finishes their workout. A recap screen appears showing all exercises and sets. The user edits a set's reps in the recap. The edit triggers a Room write, which triggers a Flow re-emission, which causes the recap list to re-render from the database. But the database save has not completed yet, so the list flickers or shows stale data, or worse, the edit appears to revert.

**Why it happens:** If the recap screen reads data reactively from the same Room Flows that the active workout uses, edits create a write-read-render cycle with race conditions. The current `finishWorkout()` method writes to Room and then transitions to `Finished` state, which only carries summary data (name, duration, total sets/exercises), not the full exercise/set breakdown needed for a recap.

**Consequences:** Flickering UI during recap edits. Lost edits. User confusion about whether their changes were saved.

**Prevention:**
1. **Create the recap state as an immutable snapshot** captured at the moment of finishing. Add a new sealed class variant:
   ```kotlin
   data class Recap(
       val workoutName: String,
       val durationMillis: Long,
       val exercises: List<SessionExercise>,  // Full snapshot, not a Room Flow
       val startTimeMillis: Long
   ) : WorkoutSessionState()
   ```
2. **Transition to `Recap` instead of `Finished`** when the user taps "Finish Workout." The recap holds the complete exercise/set data in memory. Edits modify this in-memory snapshot. Only when the user confirms "Save" does the final data write to Room.
3. **Keep the current `Finished` state** as the post-save summary screen. The flow becomes: `Active -> Recap -> (user confirms) -> Finished`.
4. **Do NOT read from Room during recap.** The recap data lives entirely in the ViewModel's state. This avoids write-read race conditions.

**Detection:** Finish a workout, edit a set in recap, observe if the UI flickers or the edit reverts.

**Phase relevance:** Post-workout recap phase. Design the state transition (Active -> Recap -> Finished) before implementing the recap UI.

**Confidence:** MEDIUM -- this is an architectural design recommendation based on the current codebase structure. Not a verified bug, but a predictable consequence of the current data flow.

---

### Pitfall 8: Auto-Increment Prefill Conflicts with Scroll Wheel Selection

**What goes wrong:** Auto-increment feature: after completing set 1 (10 reps, 60kg), set 2 pre-fills with 10 reps and 60kg (the actual values from set 1, not the template targets). With text fields, this is straightforward -- just set the text. With scroll wheels, pre-filling means programmatically scrolling the picker to a value. If the prefill happens while the picker is already rendered, it causes a visible "snap" animation. If the prefill value is not in the picker's value array (e.g., the user entered a freeform value via the old text field that is not a valid 2.5kg increment), the picker cannot represent it.

**Why it happens:** The current `prefillInputs` function sets string values (`repsInput`, `weightInput`). Scroll wheels are bound to `Int32` values. The prefill logic must change from string manipulation to integer value assignment. Additionally, the `targetWeightKgX10` from the template might not align to the 2.5kg grid (e.g., a template with 67.5kg target = kgX10 of 675, which is valid if using 2.5kg steps, but 63kg = 630 is NOT a 2.5kg step).

**Consequences:** Picker shows wrong value, picker cannot represent the value, or picker snaps visually when prefilling.

**Prevention:**
1. **Snap prefill values to the nearest valid picker step.** If using 2.5kg steps (kgX10 increments of 25), round `targetWeightKgX10` to the nearest multiple of 25:
   ```kotlin
   fun snapToStep(kgX10: Int, stepKgX10: Int = 25): Int {
       return ((kgX10 + stepKgX10 / 2) / stepKgX10) * stepKgX10
   }
   ```
2. **Apply the auto-increment values ONLY when the cursor changes** (new set or new exercise), not on every state update. Use the same `onChange` trigger as the picker state management.
3. **Animate the prefill** using `.animation(.default)` on the picker, rather than having it snap. This gives visual feedback that the picker was programmatically set.
4. **Consider making the step configurable per exercise** (some exercises use 1.25kg plates, others use 2.5kg). But for v1.1, a global 2.5kg step is sufficient.

**Detection:** Complete a set with a non-standard weight, advance to the next set, verify the picker shows the correct (snapped) value without glitching.

**Phase relevance:** Auto-increment phase. Must be implemented alongside or after the scroll wheel picker, not before.

**Confidence:** MEDIUM -- the snap-to-step logic is straightforward, but the visual picker animation behavior requires device testing.

---

### Pitfall 9: Context Menu Conflicts with List Scroll and Long-Press Gestures

**What goes wrong:** You add `.contextMenu` to each exercise row in the exercise overview sheet (for "Skip Exercise", "Move Up", "Move Down" actions). The long-press gesture for the context menu conflicts with the drag gesture for list reordering (`onMove`). Users try to long-press to reorder but get the context menu instead, or they try to invoke the context menu but accidentally start a drag.

**Why it happens:** SwiftUI's `.contextMenu` uses a long-press gesture recognizer. The `onMove` (drag to reorder) also uses a long-press gesture. When both are present on the same list row, gesture priority is ambiguous and platform-version-dependent.

**Consequences:** Users cannot reliably access either reordering or the context menu. The interaction feels broken.

**Prevention:**
1. **Do NOT use both `.contextMenu` and `onMove` on the same list row.** Choose one interaction pattern per surface.
2. **Option A: Context menu for actions, Edit mode for reorder.** Use `.contextMenu` for quick actions (skip, etc.). Use SwiftUI's `EditButton` / `editMode` to enter a reorder mode where drag handles appear and context menus are hidden.
3. **Option B: No context menu, use swipe actions + sheet for reorder.** Use `.swipeActions` for quick actions (skip exercise, etc.). Use a dedicated "Reorder" button that opens the reorder sheet (similar to the current `ExerciseOverviewSheet` but with drag handles).
4. **Option C (recommended for this app): Keep the existing exercise overview sheet as the reorder surface.** The current `ExerciseOverviewSheet` already shows exercises with a "jump to" tap action. Add drag-to-reorder in that sheet. Add context menu on the main workout view's exercise header (not in a List, so no conflict with onMove).

**Detection:** Add both `.contextMenu` and `onMove` to a List row. Try to reorder. If the context menu appears instead, you have the conflict.

**Phase relevance:** Context menu and reorder phases. Design the interaction model before implementing either feature.

**Confidence:** HIGH -- well-known SwiftUI gesture conflict. Verified in [Apple documentation](https://developer.apple.com/documentation/swiftui/contextmenu) and community discussions.

---

### Pitfall 10: Adding Room Migration for New Fields Without Bumping Schema Version

**What goes wrong:** You add a `personalBestKgX10` column to a Room entity to support the personal best display feature. You forget to increment the database version number or add a migration. On Android, Room crashes on launch with "Room cannot verify the data integrity." On iOS, if `fallbackToDestructiveMigration()` is enabled (it should not be per Pitfall 4 in the v1.0 research), all data is silently wiped.

**Why it happens:** Room validates the schema at runtime against the compiled schema. Any entity change (new column, new table, new index) requires a version bump + migration. This is easy to forget when adding "just one column."

**Consequences:** App crashes on launch (Android) or data loss (iOS with destructive fallback). Users lose their workout history.

**Prevention:**
1. **The app is already at schema version 3** (based on the v1.0 shipped state). Any new entity change bumps to version 4 with an explicit `Migration(3, 4)`.
2. **Before writing any Room entity change, write the migration FIRST.** This forces you to think about the ALTER TABLE statement before modifying the entity class.
3. **Test the migration on both platforms** by installing the v1.0 build, creating data, then installing the v1.1 build and verifying data survives.
4. **For the personal best feature specifically:** Consider whether you need a new column at all. Personal bests can be computed from the existing `completed_workouts` / `completed_sets` tables with a MAX query. Adding a denormalized column is premature optimization for a local-only app.

**Detection:** Change an entity, build and run. If the app crashes on launch, you forgot the migration.

**Phase relevance:** Any phase that touches Room entities. Most likely the personal best display phase.

**Confidence:** HIGH -- this was already flagged in v1.0 research (Pitfall 4). Re-flagged here because v1.1 will likely need schema changes.

---

## Minor Pitfalls

### Pitfall 11: Kotlin Int vs Swift Int32 Tag Mismatch in Pickers

**What goes wrong:** You create a SwiftUI `Picker` binding of type `Int` (Swift's native integer, which is 64-bit on iOS). The `ForEach` iterates over values from the KMP shared module, which are `Int32` (Kotlin `Int` maps to `Int32` in Swift). The `.tag()` uses `Int32` values. The binding is `Int`. Tags do not match the binding type. The picker never updates the binding.

**Why it happens:** Kotlin's `Int` is 32-bit and maps to Swift's `Int32` (via Objective-C `int32_t`). Swift's `Int` is 64-bit on all Apple platforms. SwiftUI Picker requires the `.tag()` type to EXACTLY match the `@State` binding type. `Int32 != Int` in Swift, so the tag comparison always fails.

**Prevention:**
1. **Always use `Int32` for picker bindings when the values come from KMP.** Or convert all KMP values to Swift `Int` before tagging.
2. **Be explicit with tags:** `.tag(Int32(value))` if the binding is `Int32`, or `.tag(Int(value))` if the binding is `Int`.
3. **Add a debug helper** that prints the binding value on change to verify it updates.

**Detection:** Scroll the picker, check if the binding value changes in a debug print. If it stays at the initial value, tag types are mismatched.

**Phase relevance:** Scroll wheel implementation. A silent bug that wastes hours debugging.

**Confidence:** HIGH -- well-documented SwiftUI behavior. Verified in [Apple Developer Forums](https://developer.apple.com/forums/thread/118813) and [DEV Community](https://dev.to/devin-rosario/fix-swiftui-picker-not-updating-selection-common-issues-3da).

---

### Pitfall 12: Reorder Persistence in Room Uses exerciseIndex That Changes Meaning After Reorder

**What goes wrong:** The current `saveCompletedSet(exerciseIndex, setIndex, reps, weightKgX10, timestamp)` in `WorkoutRepository` persists sets keyed by their exercise index. After a reorder, exerciseIndex 0 might refer to a different exercise than it did when the set was originally completed. If the user reorders mid-workout and then the app crashes, recovery will assign sets to the wrong exercises.

**Why it happens:** The `exerciseIndex` is a positional key, not a stable identifier. Reordering changes the position-to-exercise mapping.

**Prevention:**
1. **Store `exerciseId: String` alongside `exerciseIndex` in the active session persistence.** Use `exerciseId` as the stable key for recovery, and `exerciseIndex` as the display order (which can be updated on reorder).
2. **Or: Persist the current `exerciseOrder` array** (from Pitfall 3's indirection array) to Room as part of the active session state. On recovery, reconstruct the order from this persisted array.

**Detection:** Complete sets, reorder exercises, force-kill app, resume. Check if sets are attributed to the correct exercises.

**Phase relevance:** Reorder phase. Must be addressed as part of the reorder implementation, not after.

**Confidence:** HIGH -- direct consequence of the current data model design.

---

### Pitfall 13: Personal Best Query Performance on Large History

**What goes wrong:** The personal best feature queries completed workouts to find the maximum weight for each exercise. With a naive query (`SELECT MAX(weightKgX10) FROM completed_sets WHERE exerciseId = ?`), this is fast. But if you need to display "personal best per exercise" for ALL exercises in the current workout simultaneously, you run N queries (one per exercise) on every state update.

**Prevention:**
1. **Query personal bests once at workout start** (in `startWorkout()`, alongside the existing `getPreviousPerformance()` call). Store them in a `Map<String, Int>` in the ViewModel.
2. **Add a Room DAO query** that fetches personal bests for multiple exercises in a single query:
   ```kotlin
   @Query("SELECT exerciseId, MAX(actualWeightKgX10) as maxWeight FROM completed_sets WHERE exerciseId IN (:exerciseIds) GROUP BY exerciseId")
   suspend fun getPersonalBests(exerciseIds: List<String>): List<PersonalBest>
   ```
3. **Do NOT observe this as a Flow** during the active workout. Personal bests only change when a workout is saved, not during execution. A one-time query is sufficient.

**Detection:** Profile the workout start time with 50+ workout history entries. If it takes more than 500ms, optimize the query.

**Phase relevance:** Personal best display phase. Minor performance concern that is easy to prevent with proper query design.

**Confidence:** MEDIUM -- performance depends on history size. For a university project with limited real data, this may never be noticeable. But the fix is trivial, so do it right.

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| Scroll wheel pickers | Touch area overlap (P1), tag type mismatch (P11), picker state reset on timer tick (P6) | Prototype two side-by-side pickers first. Use `GeometryReader` + `.clipped()` + `UIPickerView` extension. Use `Int32` consistently. Separate picker state from session state. |
| Auto-increment set logic | Prefill conflicts with picker (P8), snap-to-step needed (P8) | Apply prefill only on cursor change. Round template values to nearest valid step. |
| Minimal "doing set" screen | Picker state reset (P6) | Keep picker bindings as local `@State`, not derived from ViewModel on every emission. |
| Post-workout recap/edit | Recap must be snapshot (P7), sealed class expansion (P2) | Add `Recap` state to sealed class. Update Swift switch. Use in-memory snapshot, not Room Flow. |
| Mid-workout exercise reorder | Index invalidation (P3), Room key invalidation (P12), gesture conflict with context menu (P9) | Use indirection array. Persist exerciseId not just index. Choose one gesture pattern per surface. |
| Abandon guards | Back gesture interception (P4) | Keep `navigationBarBackButtonHidden(true)`. Add custom X button with confirmation alert. |
| Context menu | Gesture conflict with reorder (P9) | Do not combine `.contextMenu` and `onMove` on same row. Use separate surfaces. |
| Personal best display | Room migration (P10), query performance (P13) | Compute from existing data if possible. Single query at workout start. |
| General sealed class expansion | Silent Swift fallthrough (P2) | Audit all Swift switch sites before adding any new sealed subclass. Add warning in else branch. |

---

## Sources

- [Apple Developer Forums: Side by side Picker wheels failing](https://developer.apple.com/forums/thread/690610) -- Confidence: HIGH
- [Apple Developer Forums: Picker overlapping each other](https://developer.apple.com/forums/thread/690791) -- Confidence: HIGH
- [Apple Developer Forums: Picker wheel value selection issues](https://developer.apple.com/forums/thread/689794) -- Confidence: HIGH
- [Apple Developer Forums: Picker jumps during state changes](https://developer.apple.com/forums/thread/127218) -- Confidence: HIGH
- [Apple Developer Forums: Picker binding not working](https://developer.apple.com/forums/thread/118813) -- Confidence: HIGH
- [SwiftUI Recipes: Multi Column Wheel Picker](https://swiftuirecipes.com/blog/multi-column-wheel-picker-in-swiftui) -- Confidence: HIGH
- [Apple Developer Documentation: interactiveDismissDisabled](https://developer.apple.com/documentation/swiftui/view/interactivedismissdisabled(_:)) -- Confidence: HIGH
- [Apple Developer Documentation: navigationBarBackButtonHidden](https://developer.apple.com/documentation/swiftui/view/navigationbarbackbuttonhidden(_:)) -- Confidence: HIGH
- [SKIE: Sealed class interop](https://skie.touchlab.co/features/sealed) -- Confidence: HIGH
- [JetBrains YouTrack KT-45204: Sealed classes in Swift](https://youtrack.jetbrains.com/issue/KT-45204) -- Confidence: HIGH
- [DEV Community: Fix SwiftUI Picker not updating selection](https://dev.to/devin-rosario/fix-swiftui-picker-not-updating-selection-common-issues-3da) -- Confidence: HIGH
- [Medium: Disable back swipe gesture dynamically](https://medium.com/@yunchingtan/swiftui-disable-back-swipe-gesture-dynamically-56c32d55cc4d) -- Confidence: MEDIUM
- [Medium: Enhancing SwiftUI navigation](https://ahmed-yamany.medium.com/enhancing-swiftui-navigation-a-guide-to-disabling-interactive-pop-gesture-3494be66a000) -- Confidence: MEDIUM
- [Kotlin Slack: Sealed classes in KMM](https://slack-chats.kotlinlang.org/t/451273/i-have-a-kmm-project-with-sealed-classes-and-classes-inside-) -- Confidence: MEDIUM
- [Medium: State management in KMP](https://medium.com/@hiren6997/state-management-in-kotlin-multiplatform-my-complete-survival-guide-c03b32c08038) -- Confidence: MEDIUM
- [Apple Community: SwiftUI bug with wheel pickers](https://discussions.apple.com/thread/254616862) -- Confidence: MEDIUM
