# Phase 10: Minimal Set Screen & UI Polish - Research

**Researched:** 2026-03-30
**Domain:** SwiftUI UX polish -- minimal set display, haptics, accessibility, visual consistency
**Confidence:** HIGH

## Summary

This phase is entirely SwiftUI-side work with zero KMP/ViewModel changes. It adds a firmware-style minimal "SET N" screen as an intermediate view before showing wheel pickers, adds haptic feedback on set completion, adds accessibility labels to all interactive workout elements, and extracts the hardcoded accent color into a shared constant while standardizing padding and typography.

The codebase is well-structured for these changes. The `WorkoutSessionView.swift` already uses `@State` toggles extensively (`showExerciseOverview`, `showEditSheet`, `showAbandonDialog`), so adding `showSetInput` follows an established pattern. The accent color `Color(red: 0.4, green: 0.733, blue: 0.416)` appears 25+ times across the entire app, but the CONTEXT.md scopes color extraction to workout views only (D-12). Accessibility labels are completely absent from all workout views -- zero `accessibilityLabel` or `accessibilityValue` modifiers exist today.

**Primary recommendation:** Implement in three waves: (1) Color extraction + padding standardization (foundation), (2) Minimal set screen + haptics (behavioral), (3) Accessibility labels (non-breaking additive pass).

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Firmware-style intermediate screen shown when a new set begins (after rest completes, after skipping rest, or first set of an exercise). Displays: large "SET N" label, exercise name, and "Tap when done" prompt. User taps to reveal the full wheel picker input section. Matches firmware's WorkoutStartSetState -> WorkoutSetEntryState two-step pattern.
- **D-02:** Implemented as a SwiftUI-only view mode within WorkoutSessionState.Active -- no new KMP sealed class state. Use a @State private var showSetInput: Bool toggle: false = minimal screen, true = full picker input. Toggle resets to false when currentSetIndex or currentExerciseIndex changes (new set begins).
- **D-03:** The minimal screen does NOT show during rest (rest timer has its own view). It appears only when restState is Idle or RestComplete and showSetInput is false. When rest completes, the "Rest Complete!" view transitions to the minimal set screen (not directly to pickers).
- **D-04:** Add UINotificationFeedbackGenerator().notificationOccurred(.success) when "Complete Set" button is tapped, immediately before calling viewModel.completeSet(). Uses the same haptic API as the existing rest-complete haptic.
- **D-05:** No additional haptics for other actions (skip exercise, finish workout, etc.). Keeps haptic feedback focused on the primary lifting action (set completion) and rest completion (already implemented).
- **D-06:** Add accessibilityLabel and accessibilityValue to all interactive elements in workout views: reps picker, weight picker, Complete Set button, Skip Rest button, Continue button, set rows (tap to edit), X close button, context menu items, exercise overview items, edit set sheet pickers and save button, recap set rows.
- **D-07:** Scope limited to workout session views: WorkoutSessionView, RestTimerView, ExerciseOverviewSheet, WorkoutFinishedView, WorkoutSetRow, and recap/edit sheet sections. Template and exercise catalog views are out of scope for this phase (they already have partial labels).
- **D-08:** Picker labels should include current value context (e.g., accessibilityLabel("Reps picker") + accessibilityValue("\(selectedReps) reps")). Set rows should read as "Set 1: 10 reps at 50 kilograms, tap to edit".
- **D-09:** Extract the hardcoded accent color Color(red: 0.4, green: 0.733, blue: 0.416) into a shared Color extension constant (e.g., Color.appAccent) used across all workout views. Currently duplicated in Complete Set button, Continue button, rest timer progress bar, and Finish Workout button.
- **D-10:** Standardize horizontal padding to a consistent value across workout screens (audit current mix of .padding(), .padding(.horizontal, 32), .padding(.horizontal, 16)).
- **D-11:** Verify typography hierarchy is consistent: .title2.weight(.bold) for exercise name, .headline for set info, .subheadline for metadata/secondary info, .caption for picker labels. Fix any deviations.
- **D-12:** Scope: workout tab screens only (WorkoutSessionView, RestTimerView, ExerciseOverviewSheet, WorkoutFinishedView, WorkoutSetRow). Template list, exercise catalog, and settings views are out of scope.

### Claude's Discretion
- Exact transition animation between minimal set screen and full picker input (fade, slide, or instant)
- Whether the minimal screen shows elapsed workout time or just set/exercise info
- Specific padding value to standardize on (16 or 20 or 24)
- Whether to add accessibilityHint in addition to accessibilityLabel where appropriate
- Color constant naming convention and file location

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| UX-01 | User sees minimal "doing set" screen while lifting (set number + exercise name + tap prompt) | Firmware WorkoutStartSetState.cpp reference fully analyzed; SwiftUI @State toggle pattern established in codebase; .transition(.opacity) recommended for smooth fade |
| UX-02 | Haptic feedback fires on set completion (not just rest completion) | UINotificationFeedbackGenerator pattern already in codebase at line 595; single line addition before completeSet() call |
| UX-03 | All interactive elements have accessibility labels for VoiceOver | Zero accessibility labels exist today; full inventory of 20+ elements needing labels documented; SwiftUI Picker wheel accessibility patterns researched |
| UX-04 | Visual consistency across workout screens (colors, spacing, typography) | 25+ color duplications found; padding audit shows mix of 12/24/32/default; typography hierarchy largely consistent with minor deviations |
</phase_requirements>

## Standard Stack

No new libraries or dependencies required. This phase uses only existing SwiftUI APIs.

### Core APIs Used
| API | Purpose | iOS Requirement | Notes |
|-----|---------|-----------------|-------|
| SwiftUI `accessibilityLabel(_:)` | VoiceOver label | iOS 14+ | Available, deployment target is 17.0 |
| SwiftUI `accessibilityValue(_:)` | VoiceOver current value | iOS 14+ | For pickers, dynamic values |
| SwiftUI `accessibilityHint(_:)` | VoiceOver action hint | iOS 14+ | Optional per discretion |
| `UINotificationFeedbackGenerator` | Haptic on set complete | iOS 10+ | Already used for rest completion |
| SwiftUI `Color` extension | Shared accent color | N/A | New file, no dependency |
| SwiftUI `.transition(.opacity)` | Fade between minimal/input views | iOS 13+ | Clean transition for set screen toggle |
| SwiftUI `.onChange(of:)` | Reset toggle on set/exercise change | iOS 17+ (new closure syntax) | Two-parameter closure form available on iOS 17 |

## Architecture Patterns

### Recommended File Structure
```
iosApp/iosApp/
  Extensions/
    Color+App.swift           # NEW: Color.appAccent extension
  Views/
    Workout/
      WorkoutSessionView.swift   # MODIFY: minimal set screen, haptics, a11y, color/padding
      RestTimerView.swift         # MODIFY: a11y labels, color constant
      WorkoutSetRow.swift         # MODIFY: a11y labels, color constant
      ExerciseOverviewSheet.swift # MODIFY: a11y labels
      WorkoutFinishedView.swift   # MODIFY: a11y labels, color constant, padding
```

### Pattern 1: Minimal Set Screen as View Mode Toggle
**What:** `@State private var showSetInput: Bool = false` controls whether the user sees the minimal "SET N" screen or the full picker input within `activeWorkoutView()`.
**When to use:** When rest is idle/complete and user hasn't tapped yet.
**Implementation:**

```swift
// In activeWorkoutView(_ active:)
// Replace the current else branch (set input) with:
} else {
    if showSetInput {
        setInputSection(exercise: currentExercise, setIdx: setIdx)
    } else {
        minimalSetScreen(exercise: currentExercise, setIdx: setIdx)
    }
}
```

The toggle resets to `false` whenever `currentSetIndex` or `currentExerciseIndex` changes:

```swift
// Add to activeWorkoutView, on the ScrollView:
.onChange(of: active.currentSetIndex) { _, _ in
    showSetInput = false
}
.onChange(of: active.currentExerciseIndex) { _, _ in
    showSetInput = false
}
```

**Critical detail from D-03:** When rest completes and user taps "Continue" (which calls `viewModel.skipRest()`), the rest state transitions from `RestComplete` to `Idle/NotResting`. At that point `showSetInput` should already be `false` (set by onChange), so the minimal screen appears naturally. No extra logic needed -- the flow is:

1. Rest timer counts down -> RestComplete appears -> User taps Continue
2. `skipRest()` sets restState to NotResting
3. `showSetInput` is `false` (reset by onChange when set index changed) -> minimal screen shows
4. User taps minimal screen -> `showSetInput = true` -> picker input shows

### Pattern 2: Minimal Set Screen Layout (Firmware Parity)
**What:** Centered, distraction-free screen matching firmware's WorkoutStartSetState render.
**Firmware reference (WorkoutStartSetState.cpp:103-184):**
- "SET" label centered in bold serif
- Large set number below
- Exercise name below set number (marquee if too long -- not needed on mobile, screen is wider)
- "CENTER when done" hint at bottom

**Mobile equivalent:**

```swift
@ViewBuilder
private func minimalSetScreen(exercise: SessionExercise, setIdx: Int) -> some View {
    VStack(spacing: 16) {
        Spacer()

        Text("SET")
            .font(.title3.weight(.semibold))
            .foregroundColor(.secondary)

        Text("\(setIdx + 1)")
            .font(.system(size: 72, weight: .bold, design: .rounded))

        Text(exercise.exerciseName)
            .font(.title3)
            .foregroundColor(.secondary)

        Spacer()

        Text("Tap when done")
            .font(.subheadline)
            .foregroundColor(.secondary)
    }
    .frame(maxWidth: .infinity, minHeight: 300)
    .contentShape(Rectangle())
    .onTapGesture {
        withAnimation(.easeInOut(duration: 0.2)) {
            showSetInput = true
        }
    }
}
```

### Pattern 3: Color Extension
**What:** Extract `Color(red: 0.4, green: 0.733, blue: 0.416)` into a shared constant.
**File:** `iosApp/iosApp/Extensions/Color+App.swift`

```swift
import SwiftUI

extension Color {
    static let appAccent = Color(red: 0.4, green: 0.733, blue: 0.416)
}
```

Replace all 25+ occurrences across the codebase (but per D-12, this phase scopes to workout views only -- however, since color extraction is a mechanical find-and-replace with zero behavioral risk, replacing all occurrences in a single pass is safer than leaving half the codebase on the old pattern).

### Pattern 4: Accessibility Labels
**What:** Add `accessibilityLabel` and `accessibilityValue` to all interactive workout elements.
**Key patterns:**

```swift
// Picker with dynamic value context (D-08)
Picker("Reps", selection: $selectedReps) { ... }
    .accessibilityLabel("Reps picker")
    .accessibilityValue("\(selectedReps) reps")

// Button with clear action description
Button("Complete Set") { ... }
    .accessibilityLabel("Complete set")
    .accessibilityHint("Logs current reps and weight")

// Set row with full description (D-08)
WorkoutSetRow(...)
    .accessibilityLabel("Set \(setIndex + 1): \(actualReps) reps at \(formatWeight(actualWeightKgX10)), tap to edit")
    .accessibilityElement(children: .ignore) // treat as single a11y element
```

### Anti-Patterns to Avoid
- **Adding accessibilityLabel to Picker with .wheel style then also on individual items:** The Picker already exposes each row to VoiceOver; add the label to the Picker container only, not individual ForEach items.
- **Using .sensoryFeedback modifier instead of UINotificationFeedbackGenerator:** The codebase already uses UINotificationFeedbackGenerator for rest completion (line 595). Mixing two haptic APIs for the same type of feedback is inconsistent. Stick with UINotificationFeedbackGenerator.
- **Resetting showSetInput in the flow observation:** Do NOT reset `showSetInput` in `observeSessionState()`. Use `.onChange(of:)` on the view instead -- this keeps view state management in the view layer, matching the existing pattern.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Shared color constants | Custom theming system | `extension Color { static let appAccent }` | Simple extension is all that's needed; no theming framework required for a single color |
| Haptic feedback | Custom haptic engine patterns | `UINotificationFeedbackGenerator().notificationOccurred(.success)` | One-liner; already established in codebase |
| Accessibility | Custom VoiceOver announcements | `.accessibilityLabel()` + `.accessibilityValue()` | SwiftUI built-in modifiers handle all cases |
| View transitions | Custom animation controller | `withAnimation(.easeInOut(duration: 0.2))` + `.transition(.opacity)` | SwiftUI's built-in transition system handles fade cleanly |

## Common Pitfalls

### Pitfall 1: Minimal Screen Flashes During Rest Transitions
**What goes wrong:** The minimal set screen briefly appears during rest state transitions (Resting -> RestComplete -> Idle) because there's a frame where restState is neither Resting nor RestComplete.
**Why it happens:** State flows from KMP emit sequentially; SwiftUI re-renders on each emission.
**How to avoid:** The condition for showing the minimal screen must check `restState is RestState.Idle || restState is RestState.NotResting` (not just "not resting and not rest complete"). The `RestComplete` view handles its own display and transitions to the minimal screen only when user taps Continue.
**Warning signs:** Brief flash of "SET N" text between rest timer and "Rest Complete!" views.

### Pitfall 2: onChange Reset Race with PreFill
**What goes wrong:** `showSetInput` resets to `false` via `.onChange(of: active.currentSetIndex)`, but the pre-fill flow also fires on set index change, causing picker values to update while the picker is hidden.
**Why it happens:** Both `.onChange` and the pre-fill flow observation react to the same state change.
**How to avoid:** This is actually fine -- the pre-fill updates `selectedReps` and `selectedWeightKgX10` while the picker is hidden (minimal screen showing). When the user taps to reveal pickers, the values are already correct. No race condition because both updates happen on the main thread.
**Warning signs:** None expected -- this is a non-issue if implemented correctly.

### Pitfall 3: Accessibility on Wheel Pickers
**What goes wrong:** Adding `.accessibilityLabel` to a `Picker(.wheel)` can override the system's built-in VoiceOver behavior, making the selected value unreadable.
**Why it happens:** SwiftUI Picker with wheel style already provides VoiceOver support for individual rows. An outer label can interfere.
**How to avoid:** Add `.accessibilityLabel` to the Picker (not its parent VStack), and use `.accessibilityValue` for the current selection. Test with VoiceOver on a simulator or device. If the built-in behavior is sufficient, the label may only need to be on the parent VStack with `.accessibilityElement(children: .contain)`.
**Warning signs:** VoiceOver reads "Reps picker" but not the current value, or reads values twice.

### Pitfall 4: Color Extraction Breaks Opacity Variants
**What goes wrong:** Some uses of the accent color include `.opacity()` modifiers (e.g., `Color(red: 0.4, green: 0.733, blue: 0.416).opacity(0.5)`). Simple find-replace to `Color.appAccent` misses the `.opacity()` chaining.
**Why it happens:** Grep shows occurrences with `.opacity()` in non-workout files (ExerciseDetailView, CreateExerciseView).
**How to avoid:** The replacement `Color.appAccent.opacity(0.5)` works fine -- `Color.appAccent` is a `Color`, and `.opacity()` chains naturally. Just ensure the find-replace accounts for the `.opacity()` suffix where it exists.
**Warning signs:** Compilation errors (none expected) or visual differences (test by building).

### Pitfall 5: Padding Standardization Changes Layout
**What goes wrong:** Changing `.padding(.horizontal, 24)` to `.padding(.horizontal, 32)` (or vice versa) shifts button widths, card margins, and overall visual rhythm.
**Why it happens:** Padding values affect the visual balance of the screen.
**How to avoid:** Audit the current values first (done below), pick the most common value, and apply consistently. Build and visually verify on a simulator.
**Warning signs:** Buttons or cards looking too narrow or too wide after the change.

## Code Examples

### Current Padding Audit (Workout Views)
```
WorkoutSessionView.swift:
  - .padding()                        x5 (16pt default on all sides)
  - .padding(.horizontal, 32)         x4 (buttons: Complete Set, Continue, Save, edit sheet Save)
  - .padding(.vertical, 8)            x1
  - .padding(.vertical, 12)           x1
  - .padding(.top, 8)                 x1

RestTimerView.swift:
  - .padding(.horizontal, 32)         x1 (progress bar)
  - .padding(.vertical, 24)           x1

WorkoutSetRow.swift:
  - .padding(.vertical, 8)            x1
  - .padding(.horizontal, 12)         x1

WorkoutFinishedView.swift:
  - .padding()                        x1
  - .padding(.horizontal, 24)         x1 (summary card -- inconsistent with 32)
  - .padding(.horizontal, 32)         x1 (Done button)
  - .padding(.bottom, 32)             x1
```

**Recommendation:** Standardize horizontal button/card padding to **32** (the dominant value in workout views). The one inconsistency is `WorkoutFinishedView.swift:30` using `.padding(.horizontal, 24)` on the summary card -- change to 32. WorkoutSetRow's `.padding(.horizontal, 12)` is for the row interior and should remain at 12 (row-internal padding is different from screen-level padding).

### Current Color Occurrences in Workout Views (Scope of D-09/D-12)
```
WorkoutSessionView.swift:153  - "Rest Complete!" foregroundColor
WorkoutSessionView.swift:164  - Continue button background
WorkoutSessionView.swift:346  - Complete Set button background (active state)
WorkoutSessionView.swift:442  - Edit sheet Save button background
WorkoutSessionView.swift:562  - Recap Save Workout button background
RestTimerView.swift:26        - Progress bar fill
WorkoutSetRow.swift:18        - Checkmark icon foregroundColor
WorkoutFinishedView.swift:16  - Large checkmark icon foregroundColor
WorkoutFinishedView.swift:41  - Done button background
```

9 occurrences in workout views. All are direct `Color(red: 0.4, green: 0.733, blue: 0.416)` -- none use `.opacity()` within workout scope.

### Elements Needing Accessibility Labels (D-06, D-07)

**WorkoutSessionView.swift (activeWorkoutView):**
1. X close button (toolbar leading)
2. Ellipsis menu button (toolbar trailing)
3. Skip Exercise menu item
4. Exercise Overview menu item
5. Finish Workout menu item
6. Skip Rest button
7. Continue button (rest complete)
8. Reps picker
9. Weight picker
10. Complete Set button
11. Completed set rows (via WorkoutSetRow)
12. Edit sheet reps picker
13. Edit sheet weight picker
14. Edit sheet Save button
15. Edit sheet Cancel button

**WorkoutSessionView.swift (recapView):**
16. Recap set rows (via WorkoutSetRow)
17. Save Workout button

**RestTimerView.swift:**
18. Remaining seconds display
19. Progress bar

**ExerciseOverviewSheet.swift:**
20. Exercise rows (completed/current/pending)
21. Skip button (current exercise)
22. Done button
23. Drag handles on pending exercises

**WorkoutFinishedView.swift:**
24. Done button
25. Summary rows

**WorkoutSetRow.swift:**
26. Entire row as single accessible element

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `UINotificationFeedbackGenerator` | `.sensoryFeedback` modifier | iOS 17 (2023) | SwiftUI-native, but project already uses UIKit haptic pattern -- stay consistent |
| Manual `.accessibilityLabel` on every element | SwiftUI auto-generated labels from `Text` | iOS 13+ | Buttons with Text("Complete Set") get automatic label; still need explicit labels for icon-only buttons and value context |

**Note on `.sensoryFeedback`:** While iOS 17 introduced the `.sensoryFeedback(trigger:)` modifier as a pure SwiftUI alternative, the codebase already uses `UINotificationFeedbackGenerator` for rest completion haptics (WorkoutSessionView.swift:595-597). Per D-04, the decision is to use the same UIKit API for consistency. Do not introduce `.sensoryFeedback` alongside the existing pattern.

## Open Questions

1. **Whole-app color extraction vs workout-only scope**
   - What we know: D-12 scopes to workout views only. But Color.appAccent extension will exist and could be used everywhere.
   - What's unclear: Whether to replace all 25+ occurrences (mechanical, zero-risk) or strictly only the 9 in workout views.
   - Recommendation: Create the extension, replace the 9 workout view occurrences per scope. The extension will naturally be adopted by other views in future phases. Doing all 25+ is tempting but exceeds stated scope.

2. **Minimal screen during first set of first exercise**
   - What we know: The first set starts immediately (no rest before it). showSetInput defaults to false.
   - What's unclear: Whether the user wants to see the minimal screen before the very first set of the workout.
   - Recommendation: Yes -- D-01 says "first set of an exercise" is a trigger. The minimal screen should appear for the first set too, which happens naturally since showSetInput initializes to false.

## Project Constraints (from CLAUDE.md)

- **Tech stack:** KMP + Compose Multiplatform. This phase is SwiftUI-only, no KMP changes.
- **Platform focus:** iOS first. All changes are in iosApp/.
- **Storage:** No storage changes in this phase.
- **Scope:** Workout feature only. Accessibility and color changes scoped to workout views per D-12.
- **GSD Workflow:** All edits via GSD workflow.
- **Established patterns:** Use existing @State toggle pattern, existing UINotificationFeedbackGenerator pattern, existing font hierarchy.

## Sources

### Primary (HIGH confidence)
- Firmware reference: `WorkoutStartSetState.cpp` and `.h` -- behavioral spec for minimal set screen layout (lines 103-184)
- Codebase analysis: `WorkoutSessionView.swift` (698 lines), `RestTimerView.swift` (42 lines), `WorkoutSetRow.swift` (44 lines), `ExerciseOverviewSheet.swift` (110 lines), `WorkoutFinishedView.swift` (71 lines) -- complete source analysis
- [Apple SwiftUI Accessibility Documentation](https://developer.apple.com/documentation/swiftui/view-accessibility) -- modifier reference
- [CVS Health iOS Accessibility Techniques - Pickers](https://github.com/cvs-health/ios-swiftui-accessibility-techniques/blob/main/iOSswiftUIa11yTechniques/Documentation/Pickers.md) -- Picker VoiceOver patterns

### Secondary (MEDIUM confidence)
- [Create with Swift - Accessibility Labels](https://www.createwithswift.com/preparing-your-app-for-voiceover-use-accessibility-label/) -- best practices for label/value/hint
- [Create with Swift - Accessibility Values](https://www.createwithswift.com/preparing-your-app-for-voice-over-accessibility-value-3/) -- value patterns for pickers
- [Hacking with Swift - Haptic Effects](https://www.hackingwithswift.com/books/ios-swiftui/adding-haptic-effects) -- UIKit vs SwiftUI haptic comparison
- [Hacking with Swift - Transitions](https://www.hackingwithswift.com/quick-start/swiftui/how-to-add-and-remove-views-with-a-transition) -- SwiftUI view transition patterns

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- no new dependencies, all SwiftUI built-in APIs
- Architecture: HIGH -- follows established codebase patterns (@State toggle, UIKit haptics)
- Pitfalls: HIGH -- verified against actual codebase code, firmware reference analyzed line-by-line

**Research date:** 2026-03-30
**Valid until:** 2026-04-30 (stable -- SwiftUI accessibility APIs are mature and unchanging)
