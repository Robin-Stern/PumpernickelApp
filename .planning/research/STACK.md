# Stack Research: v1.1 Workout Polish & Firmware Parity

**Domain:** iOS-native UI components for workout tab polish (KMP + SwiftUI)
**Researched:** 2026-03-29
**Confidence:** HIGH
**Scope:** Stack additions/changes for scroll wheel pickers, drag-and-drop reorder, haptic feedback, context menus, and KMP interop considerations. Does NOT re-cover the validated base stack (Kotlin 2.3.20, CMP 1.10.3, Room 2.8.4, Koin 4.2.0, etc.).

---

## Key Finding: No New Dependencies Required

Every feature in the v1.1 milestone is achievable with **built-in SwiftUI APIs (iOS 17+) and existing KMP infrastructure**. No new Gradle dependencies, no new Swift packages, no new libraries. The project already has everything it needs.

The iOS deployment target is **17.0**, which unlocks the full modern SwiftUI API surface including `.sensoryFeedback()`, `.contextMenu()`, `.confirmationDialog()`, `Picker(.wheel)`, and `List` with `.onMove()`.

---

## SwiftUI Components for New Features

### 1. Scroll Wheel Pickers (Reps & Weight)

| Component | API | iOS Version | Status |
|-----------|-----|-------------|--------|
| `Picker` with `.pickerStyle(.wheel)` | SwiftUI built-in | iOS 13+ | Available, no new dependency |

**What it replaces:** The current `TextField` with `.keyboardType(.numberPad)` / `.keyboardType(.decimalPad)` for reps and weight input in `WorkoutSessionView.setInputSection()` (lines 227-282).

**Implementation approach:** Use two `Picker` views with `.pickerStyle(.wheel)` side-by-side in an `HStack`. SwiftUI has no built-in multi-component picker, but composing multiple wheel pickers in an `HStack` with `.clipped()` and `.frame(maxWidth: .infinity)` replicates the multi-drum UIPickerView pattern.

**Reps picker (0-50):**
```swift
Picker("Reps", selection: $selectedReps) {
    ForEach(0...50, id: \.self) { value in
        Text("\(value)").tag(value)
    }
}
.pickerStyle(.wheel)
.frame(width: 100)
.clipped()
```

**Weight picker (0-1000, 2.5kg steps):**
For weight with 2.5kg increments (stored as kgx10 integers: 0, 25, 50, 75, ...), generate the range in the ViewModel and expose it as a list. The picker selects an index/value, SwiftUI converts it to the kgx10 format.

```swift
// Weight values: [0, 25, 50, 75, 100, ...] (kgx10 integer steps of 25)
Picker("Weight", selection: $selectedWeightKgX10) {
    ForEach(weightValues, id: \.self) { value in
        Text(formatWeight(value)).tag(value)
    }
}
.pickerStyle(.wheel)
.frame(width: 120)
.clipped()
```

**KMP interop consideration:** The weight step values (0, 2.5, 5.0, ...) and reps range (0-50) should be defined as constants in `commonMain` Kotlin code, not hardcoded in Swift. Expose them via the ViewModel or a shared constants object so both platforms use the same ranges. The conversion between picker selection and kgx10 format stays in the ViewModel.

**Why NOT a custom wheel picker library:** The built-in `Picker(.wheel)` is sufficient for discrete numeric ranges. Third-party libraries like `tokiensis/WheelPicker` add unnecessary dependency for what is a standard SwiftUI component. The only reason to use a custom wheel picker would be for continuous/infinite scrolling, which is not needed here (finite ranges of 0-50 reps and 0-1000kg).

**Confidence: HIGH** -- `Picker(.wheel)` is a stable, well-documented SwiftUI API available since iOS 13. Multi-wheel composition via `HStack` is a well-established pattern with multiple community examples.

### 2. Mid-Workout Exercise Reorder (Drag-and-Drop)

| Component | API | iOS Version | Status |
|-----------|-----|-------------|--------|
| `ForEach.onMove(perform:)` | SwiftUI built-in | iOS 13+ | Available, already used in codebase |
| `.environment(\.editMode, .constant(.active))` | SwiftUI built-in | iOS 13+ | Available, already used in codebase |

**What it replaces/extends:** The current `ExerciseOverviewSheet` (read-only exercise list with jump-to) will gain reorder capability. The pattern is already proven in `TemplateEditorView` (line 57-60 for `.onMove`, line 79 for always-active edit mode).

**Implementation approach:** Apply the same pattern from `TemplateEditorView` to the `ExerciseOverviewSheet`:

```swift
List {
    ForEach(Array(exercises.enumerated()), id: \.offset) { index, exercise in
        // existing row content
    }
    .onMove { source, destination in
        if let from = source.first {
            viewModel.reorderExercise(from: Int32(from), to: Int32(destination))
        }
    }
}
.environment(\.editMode, .constant(.active))
```

**KMP interop consideration -- this is the critical part:** The `reorderExercise(from:to:)` method must be added to `WorkoutSessionViewModel` in `commonMain`. It needs to:
1. Reorder the `exercises` list in the current `WorkoutSessionState.Active`
2. Adjust `currentExerciseIndex` if the move affects the current position
3. Persist the new order to Room's active session (for crash recovery)
4. Emit the updated state via `_sessionState`

The reorder logic belongs in the ViewModel (Kotlin), not in Swift. The SwiftUI `onMove` callback just forwards the indices to the ViewModel. This matches the existing pattern where `TemplateEditorView` calls `viewModel.moveExercise(from:to:)`.

**Confidence: HIGH** -- This exact pattern (ForEach + onMove + constant editMode) is already working in the codebase in `TemplateEditorView`. Extending it to the workout session sheet is straightforward.

### 3. Haptic Feedback

| Component | API | iOS Version | Status |
|-----------|-----|-------------|--------|
| `.sensoryFeedback(_:trigger:)` | SwiftUI built-in | iOS 17+ | Available (deployment target is 17.0) |
| `UINotificationFeedbackGenerator` | UIKit | iOS 10+ | Already used in codebase |
| `UIImpactFeedbackGenerator` | UIKit | iOS 10+ | Available, no new dependency |

**Current state:** The app already uses `UINotificationFeedbackGenerator` for rest timer completion (WorkoutSessionView lines 398-404). This works but is the UIKit imperative approach.

**Recommended approach for v1.1:** Use SwiftUI's native `.sensoryFeedback()` modifier for new haptic triggers. Since the deployment target is iOS 17.0, no `#available` checks are needed. Keep the existing `UINotificationFeedbackGenerator` usage for the rest timer (it works, no need to refactor), but use `.sensoryFeedback()` for all new haptic points.

**Haptic feedback map for v1.1 features:**

| Event | Feedback Type | Implementation |
|-------|---------------|----------------|
| Set completed | `.success` | `.sensoryFeedback(.success, trigger: completedSetCount)` |
| Scroll wheel selection change | `.selection` | `.sensoryFeedback(.selection, trigger: selectedValue)` on Picker |
| Rest timer finished | `.success` (existing) | Keep current `UINotificationFeedbackGenerator` |
| Personal best achieved | `.success` + `.impact(.heavy)` | `.sensoryFeedback(.impact(weight: .heavy), trigger: personalBestTrigger)` |
| Exercise reorder drag | `.alignment` | Automatic with `List.onMove` -- SwiftUI provides this for free |
| Abandon workout (destructive action) | `.warning` | `.sensoryFeedback(.warning, trigger: showAbandonDialog)` |
| Workout finished | `.success` | `.sensoryFeedback(.success, trigger: workoutFinished)` |

**How `.sensoryFeedback()` works:** It is trigger-based. You attach it to a view and provide an `Equatable` value. When the value changes, the haptic fires. This is declarative and fits naturally with SwiftUI's reactive model.

```swift
.sensoryFeedback(.selection, trigger: selectedReps)
.sensoryFeedback(.success, trigger: completedSetCount)
```

**Why NOT a haptics library:** Libraries like `markbattistella/HapticsManager` or `fatihdurmaz/SwiftUIHapticFeedback` wrap the same UIKit APIs that `.sensoryFeedback()` already wraps declaratively. They add no value when your deployment target is iOS 17+.

**Confidence: HIGH** -- `.sensoryFeedback()` is a first-party Apple API, well-documented, and the project's iOS 17 deployment target means it is universally available.

### 4. Context Menus

| Component | API | iOS Version | Status |
|-----------|-----|-------------|--------|
| `.contextMenu { }` | SwiftUI built-in | iOS 13+ | Available, no new dependency |
| `.confirmationDialog()` | SwiftUI built-in | iOS 15+ | Available, already used pattern in codebase |

**Use cases for context menus in v1.1:**

**A. Exercise row context menu (during active workout):**
```swift
ExerciseRow(exercise: exercise)
    .contextMenu {
        Button { viewModel.skipExercise(index: idx) } label: {
            Label("Skip Exercise", systemImage: "forward.fill")
        }
        Button { showReorderSheet = true } label: {
            Label("Reorder Exercises", systemImage: "arrow.up.arrow.down")
        }
    }
```

**B. Completed set row context menu:**
```swift
WorkoutSetRow(...)
    .contextMenu {
        Button { openEditSheet(exerciseIndex: exIdx, setIndex: set.setIndex) } label: {
            Label("Edit Set", systemImage: "pencil")
        }
        Button(role: .destructive) { viewModel.deleteSet(exIdx: exIdx, setIdx: set.setIndex) } label: {
            Label("Delete Set", systemImage: "trash")
        }
    }
```

**C. Template list context menu (enhancement):**
```swift
TemplateRow(template: template)
    .contextMenu {
        Button { startWorkout(template) } label: {
            Label("Start Workout", systemImage: "play.fill")
        }
        Button { editTemplate(template) } label: {
            Label("Edit Template", systemImage: "pencil")
        }
        Divider()
        Button(role: .destructive) { confirmDelete(template) } label: {
            Label("Delete", systemImage: "trash")
        }
    }
```

**KMP interop consideration:** Context menu actions (skip, delete set, etc.) require new methods on the KMP ViewModel. These are simple action dispatches -- the SwiftUI context menu calls `viewModel.methodName()` just like existing button taps. No special interop handling needed.

**Confidence: HIGH** -- `.contextMenu` and `.confirmationDialog` are stable, well-documented SwiftUI APIs.

### 5. Abandon Guards (Confirmation Dialog)

| Component | API | iOS Version | Status |
|-----------|-----|-------------|--------|
| `.confirmationDialog()` | SwiftUI built-in | iOS 15+ | Available, already used pattern |
| `.alert()` | SwiftUI built-in | iOS 13+ | Already used in codebase |
| `.interactiveDismissDisabled()` | SwiftUI built-in | iOS 15+ | Available, for preventing accidental sheet dismiss |

**Implementation approach:** The codebase already has a resume/discard alert pattern in `TemplateListView` (lines 66-76). The v1.1 abandon guard extends this pattern to the active workout session.

```swift
.confirmationDialog(
    "End Workout?",
    isPresented: $showAbandonDialog,
    titleVisibility: .visible
) {
    Button("Save & Exit") {
        viewModel.finishWorkout()
    }
    Button("Discard Workout", role: .destructive) {
        viewModel.discardWorkout()
        dismiss()
    }
    // Cancel is automatic
}
```

**KMP interop consideration:** The ViewModel already has `finishWorkout()` and `discardWorkout()` methods. The abandon guard is purely a SwiftUI presentation concern -- no new ViewModel methods needed for the basic flow. However, if a "save progress and exit" option is wanted (save partial workout without marking it as finished), a new ViewModel method like `saveAndExit()` would be needed in `commonMain`.

**Confidence: HIGH** -- `.confirmationDialog()` is the standard SwiftUI approach for destructive action confirmation.

---

## KMP Interop Considerations Summary

### What Stays in KMP (commonMain Kotlin)

| Concern | Where | Why |
|---------|-------|-----|
| Reps/weight ranges and step values | `WorkoutSessionViewModel` or shared constants | Single source of truth for both platforms |
| Exercise reorder logic | `WorkoutSessionViewModel.reorderExercise()` | Needs to update state + persist to Room |
| Personal best detection | `WorkoutSessionViewModel` or `WorkoutRepository` | Database query, business logic |
| Skip exercise logic | `WorkoutSessionViewModel.skipExercise()` | State machine transition |
| Auto-increment (prefill from previous set) | `WorkoutSessionViewModel` | Already partially implemented in `computeNextCursor()` |
| Abandon guard state | `WorkoutSessionViewModel` | Already has `finishWorkout()` and `discardWorkout()` |

### What Stays in SwiftUI (iOS-only)

| Concern | Where | Why |
|---------|-------|-----|
| Picker `.wheel` style rendering | SwiftUI views | Platform-native UI component, no KMP equivalent |
| Haptic feedback (`sensoryFeedback`) | SwiftUI views | iOS-only API, purely presentational |
| Context menu presentation | SwiftUI views | iOS-native long-press interaction |
| Confirmation dialog presentation | SwiftUI views | iOS-native action sheet |
| Drag handle display (editMode) | SwiftUI views | iOS-native list editing affordance |

### Interop Pattern (Unchanged from v1.0)

The existing pattern works perfectly for all v1.1 features:

```
SwiftUI View --[calls method]--> KMP ViewModel --[updates StateFlow]--> SwiftUI observes via asyncSequence
```

Specifically:
1. User interacts with SwiftUI component (picker, context menu, drag)
2. SwiftUI calls a method on the KMP ViewModel (e.g., `viewModel.reorderExercise(from:to:)`)
3. ViewModel processes the action, updates `_sessionState` MutableStateFlow
4. SwiftUI observes the state change via `asyncSequence(for: viewModel.sessionStateFlow)`
5. UI recomposes with new state

No new bridging code, no SKIE, no additional KMP-NativeCoroutines configuration. The existing `@NativeCoroutinesState` + `asyncSequence(for:)` pattern handles everything.

---

## New KMP ViewModel Methods Required

These methods need to be added to `WorkoutSessionViewModel` in `commonMain`:

| Method | Purpose | Complexity |
|--------|---------|------------|
| `reorderExercise(from: Int, to: Int)` | Move exercise during active workout | Medium -- must adjust currentExerciseIndex and persist |
| `skipExercise(exerciseIndex: Int)` | Skip remaining sets of an exercise | Low -- advance cursor past exercise |
| `getPersonalBest(exerciseId: String): Flow<PersonalBest?>` | Query best set for exercise | Medium -- new Room query |
| `autoIncrementSet()` | Prefill next set with previous actual values | Low -- read last completed set's values |

No new Room entities or schema migrations are needed. Personal best can be computed from existing `CompletedWorkoutSetEntity` data with a new DAO query.

---

## What NOT to Add

| Do Not Add | Why Not | Use Instead |
|------------|---------|-------------|
| Any third-party SwiftUI picker library (WheelPicker, etc.) | Built-in `Picker(.wheel)` does exactly what's needed for discrete numeric ranges | `Picker` with `.pickerStyle(.wheel)` |
| SKIE (Touchlab) | All v1.1 features work through existing KMP-NativeCoroutines interop. No new Swift/Kotlin bridging patterns needed | Existing `@NativeCoroutinesState` + `asyncSequence(for:)` |
| HapticsManager or similar haptic libraries | `.sensoryFeedback()` is built into SwiftUI for iOS 17+ | `.sensoryFeedback(_:trigger:)` modifier |
| Core Haptics / CHHapticEngine | Only needed for custom haptic patterns with precise timing. Standard feedback types cover all v1.1 use cases | `.sensoryFeedback()` for declarative haptics |
| Any drag-and-drop library | `ForEach.onMove()` with constant editMode is the standard approach, already proven in this codebase | Built-in `onMove(perform:)` |
| Room schema migration | No new entities needed. Personal bests are derived from existing completed workout data | New DAO query on existing tables |
| Charting library (for personal best trends) | Out of scope for v1.1. Personal best is a simple "best ever" value display, not a chart | Simple Text display |

---

## Alternatives Considered

| Approach | Alternative | Why the Recommended Approach Wins |
|----------|-------------|-----------------------------------|
| `Picker(.wheel)` for reps/weight | Custom `UIViewRepresentable` wrapping `UIPickerView` | The SwiftUI Picker is sufficient for finite discrete ranges. `UIViewRepresentable` adds complexity and breaks SwiftUI's declarative model. Only use UIKit bridge if you need infinite scrolling or custom cell rendering. |
| `Picker(.wheel)` for reps/weight | Stepper with +/- buttons | Steppers are tedious for large ranges (0-1000 weight). Wheel pickers let users spin to a value quickly, matching the firmware's rotary encoder UX. |
| `Picker(.wheel)` for reps/weight | TextField with number pad (current) | Keyboard input works but is slower and requires dismissing the keyboard. Wheel pickers are more ergonomic in a gym setting (sweaty hands, one-hand operation). |
| `.sensoryFeedback()` for haptics | `UIImpactFeedbackGenerator` (UIKit) | `.sensoryFeedback()` is declarative, trigger-based, and integrates naturally with SwiftUI's reactive model. UIKit generators require imperative `.prepare()` + `.impactOccurred()` calls. For new code on iOS 17+, prefer the SwiftUI API. |
| `List.onMove()` for reorder | `draggable()` + `dropDestination()` (Transferable protocol) | `onMove` is purpose-built for list reordering and is far simpler. The Transferable-based API is for cross-app drag-and-drop or complex data transfer. Overkill for in-list reordering. |
| `List.onMove()` for reorder | Custom `DragGesture` implementation | Reinventing what `onMove` already provides. Custom drag would need manual hit testing, animation, and state management. |
| `.contextMenu` for exercise actions | `.swipeActions` (already used for delete) | Context menus are better for multiple actions (skip, reorder, edit). Swipe actions are limited to 1-2 actions and are best for primary destructive action. Use both: swipe for quick delete, context menu for full action set. |
| `.confirmationDialog` for abandon guard | `.alert()` | Confirmation dialogs slide up from the bottom on iPhone, can hold many options, and feel more natural for "choose an action" vs. alerts which are for "acknowledge a situation". The abandon guard has 3 options (save & exit, discard, cancel) which fits confirmationDialog better. |

---

## Sources

- [Apple Developer Docs: Picker wheel style](https://developer.apple.com/documentation/swiftui/pickerstyle/wheel) -- Built-in SwiftUI wheel picker (HIGH confidence)
- [Multi Column Wheel Picker in SwiftUI](https://swiftuirecipes.com/blog/multi-column-wheel-picker-in-swiftui) -- HStack composition pattern for multi-wheel (MEDIUM confidence)
- [Apple Developer Docs: SensoryFeedback](https://developer.apple.com/documentation/swiftui/sensoryfeedback) -- iOS 17+ declarative haptics (HIGH confidence)
- [Sensory feedback in SwiftUI (Swift with Majid)](https://swiftwithmajid.com/2023/10/10/sensory-feedback-in-swiftui/) -- Comprehensive sensoryFeedback guide (HIGH confidence)
- [SwiftUI Sensory Feedback (Use Your Loaf)](https://useyourloaf.com/blog/swiftui-sensory-feedback/) -- Feedback types and trigger patterns (HIGH confidence)
- [Apple Developer Docs: ContextMenu](https://developer.apple.com/documentation/swiftui/contextmenu) -- Built-in context menu (HIGH confidence)
- [How to show a context menu (Hacking with Swift)](https://www.hackingwithswift.com/quick-start/swiftui/how-to-show-a-context-menu) -- Context menu patterns (HIGH confidence)
- [SwiftUI Confirmation Dialogs (Use Your Loaf)](https://useyourloaf.com/blog/swiftui-confirmation-dialogs/) -- confirmationDialog patterns (HIGH confidence)
- [How to Reorder List rows in SwiftUI (Sarunw)](https://sarunw.com/posts/swiftui-list-onmove/) -- onMove patterns (HIGH confidence)
- [onMove in SwiftUI List (Xavier7t)](https://xavier7t.com/onmove-in-swiftui-list) -- Drag reorder patterns (HIGH confidence)
- [Adding haptic effects (Hacking with Swift)](https://www.hackingwithswift.com/books/ios-swiftui/adding-haptic-effects) -- UIFeedbackGenerator reference (HIGH confidence)
- [KMP-NativeCoroutines GitHub](https://github.com/rickclephas/KMP-NativeCoroutines) -- Existing interop library, v1.0.2 in project (HIGH confidence)
- Existing codebase patterns: `TemplateEditorView.swift` (onMove, editMode), `WorkoutSessionView.swift` (UINotificationFeedbackGenerator, asyncSequence observation) -- verified by reading source (HIGH confidence)

---

*Stack research for: v1.1 Workout Polish & Firmware Parity*
*Researched: 2026-03-29*
*Conclusion: Zero new dependencies. All features use built-in SwiftUI APIs + existing KMP interop.*
