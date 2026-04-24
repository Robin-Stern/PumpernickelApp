# Phase 9: Abandon Guards & Context Menu - Research

**Researched:** 2026-03-30
**Domain:** SwiftUI dialog/menu patterns, toolbar reorganization, KMP ViewModel integration
**Confidence:** HIGH

## Summary

This phase is a **SwiftUI-only UI reorganization** that wires up new affordances (exit confirmation dialog, context menu) to existing KMP ViewModel methods. No new Kotlin code is needed -- `discardWorkout()`, `enterReview()`, `saveReviewedWorkout()`, and `skipExercise()` all exist and are tested through prior phases. The work is entirely in `WorkoutSessionView.swift`: replacing the current toolbar layout, adding a `@State` boolean for the abandon dialog, and computing a completed-sets guard.

The current toolbar has a skip button (leading) and exercise overview button (trailing). After this phase: X/close button (leading), ellipsis Menu (trailing). The inline "Finish Workout" button at the bottom of the scroll view should be **removed** since "Finish Workout" moves into the context menu -- keeping both would be redundant and confusing.

**Primary recommendation:** Implement as a single plan with two tasks: (1) replace toolbar + add abandon dialog, (2) add context Menu + remove inline finish button. Both tasks modify `WorkoutSessionView.swift` only.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** X button (`xmark` SF Symbol) in leading toolbar position triggers abandon confirmation. Replaces the current skip button position.
- **D-02:** Only show abandon dialog when 1+ sets completed. If no sets completed, X button discards immediately.
- **D-03:** Three-option `.confirmationDialog`: "Save & Exit" (calls `enterReview()` then `saveReviewedWorkout()`), "Discard" (calls `discardWorkout()`, `.destructive` role), "Cancel" (dismisses dialog).
- **D-04:** Dialog title "Abandon Workout?" with progress summary message ("Exercise 2/4, 5 sets completed").
- **D-05:** `Menu` view with `ellipsis.circle` SF Symbol in trailing toolbar position. Existing `list.bullet` overview button moves inside this menu.
- **D-06:** Menu contains three actions: "Skip Exercise" (`forward.fill`, disabled on last exercise), "Exercise Overview" (`list.bullet`), "Finish Workout" (`checkmark.circle`).

### Claude's Discretion
- Destructive styling (red text) for "Discard" in confirmation dialog -- use `.destructive` button role (standard iOS pattern, renders red automatically)
- "Finish Workout" in context menu goes straight to recap without its own confirmation (firmware parity)
- Animation/transition details for dialog presentation -- use SwiftUI defaults (no custom animation needed)

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FLOW-05 | User sees abandon confirmation (save & exit / discard / cancel) when leaving mid-workout | `.confirmationDialog` with 3 buttons + completed-sets guard. All ViewModel methods exist: `enterReview()`, `saveReviewedWorkout()`, `discardWorkout()`. |
| FLOW-06 | User can access context menu during workout with skip, reorder, and finish options | `Menu` view in trailing toolbar. Calls existing `skipExercise()`, toggles `showExerciseOverview`, calls `enterReview()`. Skip disable logic derived from `currentExerciseIndex + 1 >= exercises.count`. |
</phase_requirements>

## Standard Stack

No new libraries needed. This phase uses only existing SwiftUI APIs.

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| SwiftUI | iOS 17+ | `.confirmationDialog`, `Menu`, `ToolbarItem` | All APIs available since iOS 15+; project targets iOS 17+ |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Shared (KMP framework) | existing | ViewModel method calls | Already wired; no new KMP exports needed |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `.confirmationDialog` | `.alert` | `.alert` is for simple OK/Cancel. `.confirmationDialog` is the correct API for action sheets with 3+ options -- renders as action sheet on iPhone, popover on iPad. |
| `Menu` in toolbar | `.contextMenu` on view | `.contextMenu` requires long-press, which is not discoverable. `Menu` with a visible icon in the toolbar is the standard iOS pattern for "more actions". |

## Architecture Patterns

### Current Toolbar Layout (lines 196-213)
```swift
.toolbar {
    ToolbarItem(placement: .navigationBarLeading) {
        // Skip button (forward.fill) -- conditional on not-last-exercise
    }
    ToolbarItem(placement: .navigationBarTrailing) {
        // Exercise overview button (list.bullet)
    }
}
```

### Target Toolbar Layout
```swift
.toolbar {
    ToolbarItem(placement: .navigationBarLeading) {
        // X/close button (xmark) -- triggers abandon guard
    }
    ToolbarItem(placement: .navigationBarTrailing) {
        // Menu (ellipsis.circle) -- contains skip, overview, finish
    }
}
```

### Pattern 1: Confirmation Dialog with Completed-Sets Guard
**What:** Show abandon dialog only when there is data to lose (1+ completed sets). Otherwise discard immediately.
**When to use:** Any time the X button is tapped during an active workout.
**Example:**
```swift
// Source: Apple SwiftUI documentation + CONTEXT.md D-02, D-03, D-04
@State private var showAbandonDialog = false

// In the X button action:
Button {
    let hasCompletedSets = exercises.contains { ex in
        ex.sets.contains { $0.isCompleted }
    }
    if hasCompletedSets {
        showAbandonDialog = true
    } else {
        viewModel.discardWorkout()
        dismiss()
    }
} label: {
    Image(systemName: "xmark")
}

// On the view:
.confirmationDialog(
    "Abandon Workout?",
    isPresented: $showAbandonDialog,
    titleVisibility: .visible
) {
    Button("Save & Exit") {
        viewModel.enterReview()
        viewModel.saveReviewedWorkout()
        dismiss()
    }
    Button("Discard", role: .destructive) {
        viewModel.discardWorkout()
        dismiss()
    }
    Button("Cancel", role: .cancel) { }
} message: {
    Text("Exercise \(exIdx + 1)/\(exercises.count), \(completedSetsCount) sets completed")
}
```

### Pattern 2: Menu in Toolbar with Conditional Disable
**What:** A `Menu` view inside a `ToolbarItem` with an SF Symbol label, containing `Button` items with icons.
**When to use:** Trailing toolbar position for secondary workout actions.
**Example:**
```swift
// Source: SwiftUI Menu documentation + CONTEXT.md D-05, D-06
ToolbarItem(placement: .navigationBarTrailing) {
    Menu {
        Button {
            viewModel.skipExercise()
        } label: {
            Label("Skip Exercise", systemImage: "forward.fill")
        }
        .disabled(Int(active.currentExerciseIndex) + 1 >= exercises.count)

        Button {
            showExerciseOverview = true
        } label: {
            Label("Exercise Overview", systemImage: "list.bullet")
        }

        Button {
            viewModel.enterReview()
        } label: {
            Label("Finish Workout", systemImage: "checkmark.circle")
        }
    } label: {
        Image(systemName: "ellipsis.circle")
    }
}
```

### Pattern 3: Save & Exit Async Flow
**What:** "Save & Exit" calls `enterReview()` which transitions state to `Reviewing`, then `saveReviewedWorkout()` which transitions to `Finished`. Both are async coroutine-backed. The `dismiss()` should be called after initiating both, but the view will naturally dismiss since the parent navigation handles it.
**When to use:** The "Save & Exit" button in the abandon dialog.
**Important:** `enterReview()` and `saveReviewedWorkout()` are both fire-and-forget `viewModelScope.launch` calls. Calling them sequentially from SwiftUI fires two independent coroutines. The first sets state to `Reviewing`, the second checks for `Reviewing` state. If there is a race, `saveReviewedWorkout()` might execute before `enterReview()` finishes. See Pitfall 1 below for the mitigation.

### Anti-Patterns to Avoid
- **Keeping the inline "Finish Workout" button AND the menu "Finish Workout" action:** Redundant. Remove the inline button (lines 174-190 of current WorkoutSessionView.swift). The context menu is the single access point now.
- **Using `.alert` instead of `.confirmationDialog`:** `.alert` is for informational dialogs. `.confirmationDialog` renders as an action sheet on iPhone, which is the correct iOS pattern for multiple destructive/non-destructive actions.
- **Adding `.contextMenu` to the scroll content:** Long-press context menus are not discoverable. The toolbar `Menu` is always visible.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Action sheet with 3 options | Custom overlay/sheet | `.confirmationDialog` | Native iOS action sheet rendering, accessibility built-in, cancel button auto-positioned |
| Dropdown menu with icons | Custom popover | `Menu` view | Native iOS menu rendering, supports `Label` with SF Symbols, disabled state built-in |
| Destructive button styling | Manual red coloring | `Button("Discard", role: .destructive)` | System handles color, bold weight, and positioning for destructive actions |
| Skip-disabled logic | Separate `@State` | Inline computed from `active.currentExerciseIndex + 1 >= exercises.count` | Same logic already used in current skip button conditional |

**Key insight:** Every behavior in this phase already exists as a ViewModel method or SwiftUI built-in. The only new code is wiring -- `@State` booleans, `.confirmationDialog`, and `Menu`.

## Common Pitfalls

### Pitfall 1: Race Condition Between enterReview() and saveReviewedWorkout()
**What goes wrong:** Both methods launch independent coroutines via `viewModelScope.launch`. If `saveReviewedWorkout()` executes before `enterReview()` finishes transitioning state to `Reviewing`, the `as? WorkoutSessionState.Reviewing` guard returns null and the save silently fails.
**Why it happens:** SwiftUI calls both methods synchronously from the button action, but each launches an async coroutine. On fast devices, both coroutines may be scheduled before either completes.
**How to avoid:** Create a dedicated `saveAndExit()` method in the ViewModel that chains both operations sequentially within a single coroutine:
```kotlin
fun saveAndExit() {
    viewModelScope.launch {
        val active = _sessionState.value as? WorkoutSessionState.Active ?: return@launch
        // Inline the enterReview logic
        timerJob?.cancel()
        elapsedJob?.cancel()
        val endTimeMillis = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val durationMillis = endTimeMillis - active.startTimeMillis
        val reviewing = WorkoutSessionState.Reviewing(
            templateId = active.templateId,
            templateName = active.templateName,
            exercises = active.exercises,
            startTimeMillis = active.startTimeMillis,
            durationMillis = durationMillis
        )
        _sessionState.value = reviewing
        // Now save (state is guaranteed to be Reviewing)
        // ... inline saveReviewedWorkout logic here
    }
}
```
**Warning signs:** "Save & Exit" occasionally produces no saved workout in history. Testing shows state remains `Active` after the dialog dismisses.
**Recommendation:** Either (a) add a `saveAndExit()` convenience method in the ViewModel (small KMP change, safest), or (b) call only `enterReview()` from "Save & Exit", then dismiss, and let the recap screen's existing "Save Workout" button handle persistence. Option (b) avoids KMP changes but leaves the user on the recap screen instead of dismissing directly. Option (a) is cleaner for FLOW-05 UAT ("save persists to history").

### Pitfall 2: Dismiss Timing After Async ViewModel Calls
**What goes wrong:** Calling `dismiss()` immediately after `viewModel.discardWorkout()` or `viewModel.saveAndExit()` may dismiss the view before the coroutine completes, potentially interrupting Room writes.
**Why it happens:** `dismiss()` is synchronous SwiftUI state change; ViewModel methods are fire-and-forget coroutines.
**How to avoid:** For `discardWorkout()`, the `clearActiveSession()` Room call is fast and non-critical (worst case: stale active session is detected on next app launch and the resume prompt handles it). Calling `dismiss()` immediately is acceptable. For "Save & Exit", the save MUST complete before dismiss. If using `saveAndExit()` approach, dismiss should happen reactively -- observe the state transition to `Finished` and dismiss then, OR dismiss immediately and trust the coroutine completes (ViewModel scope outlives the view).
**Warning signs:** Saved workouts occasionally missing from history after "Save & Exit".

### Pitfall 3: confirmationDialog Message Scope
**What goes wrong:** The `message:` closure of `.confirmationDialog` needs access to `active.exercises` to compute the progress string, but if placed outside the `if let active` scope, the data is not available.
**Why it happens:** `.confirmationDialog` is a modifier on the view, and the `active` binding may not be in scope.
**How to avoid:** Attach `.confirmationDialog` inside the `activeWorkoutView` function where `active` is already deconstructed, or compute the progress string into a `@State` / local variable before showing the dialog.

### Pitfall 4: Menu Disabled State on SwiftUI
**What goes wrong:** `.disabled()` on a `Button` inside a `Menu` may grey out the text but still allow taps on some iOS versions.
**Why it happens:** SwiftUI `Menu` button disable behavior has been inconsistent across iOS versions.
**How to avoid:** On iOS 17+ this works correctly. Verify on target device. As a safety net, also add a guard at the top of `skipExercise()` (already exists -- the method is a no-op on last exercise).
**Warning signs:** Skip fires on last exercise despite menu item appearing disabled.

## Code Examples

### Complete Toolbar Replacement
```swift
// Source: CONTEXT.md D-01, D-05, D-06 + SwiftUI documentation
.toolbar {
    ToolbarItem(placement: .navigationBarLeading) {
        Button {
            let hasCompletedSets = active.exercises.contains { ex in
                ex.sets.contains { $0.isCompleted }
            }
            if hasCompletedSets {
                showAbandonDialog = true
            } else {
                viewModel.discardWorkout()
                dismiss()
            }
        } label: {
            Image(systemName: "xmark")
        }
    }

    ToolbarItem(placement: .navigationBarTrailing) {
        Menu {
            Button {
                viewModel.skipExercise()
            } label: {
                Label("Skip Exercise", systemImage: "forward.fill")
            }
            .disabled(Int(active.currentExerciseIndex) + 1 >= exercises.count)

            Button {
                showExerciseOverview = true
            } label: {
                Label("Exercise Overview", systemImage: "list.bullet")
            }

            Button {
                viewModel.enterReview()
            } label: {
                Label("Finish Workout", systemImage: "checkmark.circle")
            }
        } label: {
            Image(systemName: "ellipsis.circle")
        }
    }
}
```

### Complete Confirmation Dialog
```swift
// Source: CONTEXT.md D-03, D-04 + SwiftUI .confirmationDialog API
.confirmationDialog(
    "Abandon Workout?",
    isPresented: $showAbandonDialog,
    titleVisibility: .visible
) {
    Button("Save & Exit") {
        viewModel.enterReview()
        viewModel.saveReviewedWorkout()
        dismiss()
    }
    Button("Discard", role: .destructive) {
        viewModel.discardWorkout()
        dismiss()
    }
    Button("Cancel", role: .cancel) { }
} message: {
    let completedSetsCount = active.exercises.reduce(0) { sum, ex in
        sum + ex.sets.filter { $0.isCompleted }.count
    }
    Text("Exercise \(Int(active.currentExerciseIndex) + 1)/\(exercises.count), \(completedSetsCount) sets completed")
}
```

### New @State Declaration
```swift
@State private var showAbandonDialog = false
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `.actionSheet()` | `.confirmationDialog()` | iOS 15 / SwiftUI 3.0 | Same visual result, modern API, supports `titleVisibility` and `message:` parameters |
| Custom popover menus | `Menu` view | iOS 14 / SwiftUI 2.0 | Built-in disclosure menu with SF Symbol labels, native animation |
| `Button(role: .destructive)` in alerts | Same in `.confirmationDialog` | iOS 15+ | System-rendered red text, positioned below non-destructive options |

**Deprecated/outdated:**
- `.actionSheet()` -- deprecated in favor of `.confirmationDialog()`. Do not use.

## Open Questions

1. **Save & Exit race condition resolution**
   - What we know: Calling `enterReview()` then `saveReviewedWorkout()` sequentially from SwiftUI fires two independent coroutines that may race.
   - What's unclear: Whether ViewModel's `viewModelScope.launch` blocks guarantee FIFO ordering on the same dispatcher (they do on `Dispatchers.Main`, which is the default, so this may be a non-issue on single-threaded Main dispatcher).
   - Recommendation: Test the sequential call pattern first. If it works reliably (Main dispatcher serializes coroutine starts), no KMP change needed. If flaky, add a `saveAndExit()` method. **HIGH confidence this works as-is** because `viewModelScope` uses `Dispatchers.Main.immediate` which is single-threaded, so `launch` blocks execute in order on the main thread.

2. **Whether to remove the inline "Finish Workout" button**
   - What we know: The context menu now has "Finish Workout". The inline button (lines 174-190) is redundant.
   - What's unclear: Whether the user wants both access points or just the menu.
   - Recommendation: Remove the inline button. The menu provides discoverable access, and keeping both creates UI confusion about which is the "real" finish action. The CONTEXT.md D-06 explicitly moves finish to the menu.

## Project Constraints (from CLAUDE.md)

- **Tech stack:** KMP + Compose Multiplatform, iOS first -- this phase is SwiftUI-only, no KMP changes needed (unless saveAndExit convenience method is added)
- **Platform focus:** iOS first -- all changes in `iosApp/iosApp/Views/Workout/WorkoutSessionView.swift`
- **Storage:** Local/offline only -- no new persistence, all methods already persist via Room
- **Scope:** Workout feature only -- this phase stays within workout session flow
- **GSD Workflow:** Changes executed through `/gsd:execute-phase`
- **Conventions:** Use `*Flow` suffix for NativeCoroutinesState observation (no new flows needed), sealed class `WorkoutSessionState.X` dot syntax for Swift interop

## Sources

### Primary (HIGH confidence)
- `WorkoutSessionView.swift` (current codebase) -- Existing toolbar layout, state declarations, view structure
- `WorkoutSessionViewModel.kt` (current codebase) -- All ViewModel methods verified: `discardWorkout()` (line 562), `enterReview()` (line 484), `saveReviewedWorkout()` (line 508), `skipExercise()` (line 456)
- `ExerciseOverviewSheet.swift` (current codebase) -- Existing sheet with skip/reorder, will be opened from menu
- `WorkoutAbandonConfirmState.cpp` (firmware) -- Behavioral spec for abandon confirm: 2 options, progress string format, cancel returns to origin
- `WorkoutContextMenuState.cpp` (firmware) -- Behavioral spec for context menu: skip + adjust order, skip disabled on last exercise

### Secondary (MEDIUM confidence)
- [SwiftUI Confirmation Dialogs - Use Your Loaf](https://useyourloaf.com/blog/swiftui-confirmation-dialogs/) -- confirmationDialog API patterns with destructive roles
- [Confirmation dialogs in SwiftUI - Swift with Majid](https://swiftwithmajid.com/2021/07/28/confirmation-dialogs-in-swiftui/) -- titleVisibility, message parameter usage
- [Hacking with Swift - confirmationDialog](https://www.hackingwithswift.com/books/ios-swiftui/showing-multiple-options-with-confirmationdialog) -- Three-button dialog examples
- [SwiftUI Menu and Context Menu - swiftyplace](https://www.swiftyplace.com/blog/swiftui-menu-and-context-menu-buttons-with-dropdown-lists) -- Menu in toolbar patterns

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- No new libraries, only SwiftUI built-ins (confirmationDialog, Menu, ToolbarItem)
- Architecture: HIGH -- All ViewModel methods exist and are tested. Toolbar replacement is straightforward.
- Pitfalls: MEDIUM -- The enterReview/saveReviewedWorkout race condition needs validation, but is likely fine due to Main dispatcher serialization.

**Research date:** 2026-03-30
**Valid until:** 2026-04-30 (stable -- no version-sensitive dependencies)
