# Phase 10: Minimal Set Screen & UI Polish - Context

**Gathered:** 2026-03-30
**Status:** Ready for planning

<domain>
## Phase Boundary

Add a firmware-style minimal "doing set" screen while lifting, add haptic feedback on set completion, add accessibility labels to all interactive workout elements, and standardize visual consistency (colors, spacing, typography) across workout screens. This is the final polish phase for the v1.1 milestone — no new features, only UX refinement of existing workout flow.

</domain>

<decisions>
## Implementation Decisions

### Minimal Set Screen (UX-01)
- **D-01:** Firmware-style intermediate screen shown when a new set begins (after rest completes, after skipping rest, or first set of an exercise). Displays: large "SET N" label, exercise name, and "Tap when done" prompt. User taps to reveal the full wheel picker input section. Matches firmware's `WorkoutStartSetState` → `WorkoutSetEntryState` two-step pattern.
- **D-02:** Implemented as a SwiftUI-only view mode within `WorkoutSessionState.Active` — no new KMP sealed class state. Use a `@State private var showSetInput: Bool` toggle: `false` = minimal screen, `true` = full picker input. Toggle resets to `false` when `currentSetIndex` or `currentExerciseIndex` changes (new set begins).
- **D-03:** The minimal screen does NOT show during rest (rest timer has its own view). It appears only when `restState` is `Idle` or `RestComplete` and `showSetInput` is `false`. When rest completes, the "Rest Complete!" view transitions to the minimal set screen (not directly to pickers).

### Haptic Feedback (UX-02)
- **D-04:** Add `UINotificationFeedbackGenerator().notificationOccurred(.success)` when "Complete Set" button is tapped, immediately before calling `viewModel.completeSet()`. Uses the same haptic API as the existing rest-complete haptic.
- **D-05:** No additional haptics for other actions (skip exercise, finish workout, etc.). Keeps haptic feedback focused on the primary lifting action (set completion) and rest completion (already implemented).

### Accessibility Labels (UX-03)
- **D-06:** Add `accessibilityLabel` and `accessibilityValue` to all interactive elements in workout views: reps picker, weight picker, Complete Set button, Skip Rest button, Continue button, set rows (tap to edit), X close button, context menu items, exercise overview items, edit set sheet pickers and save button, recap set rows.
- **D-07:** Scope limited to workout session views: WorkoutSessionView, RestTimerView, ExerciseOverviewSheet, WorkoutFinishedView, WorkoutSetRow, and recap/edit sheet sections. Template and exercise catalog views are out of scope for this phase (they already have partial labels).
- **D-08:** Picker labels should include current value context (e.g., `accessibilityLabel("Reps picker")` + `accessibilityValue("\(selectedReps) reps")`). Set rows should read as "Set 1: 10 reps at 50 kilograms, tap to edit".

### Visual Consistency (UX-04)
- **D-09:** Extract the hardcoded accent color `Color(red: 0.4, green: 0.733, blue: 0.416)` into a shared `Color` extension constant (e.g., `Color.appAccent`) used across all workout views. Currently duplicated in Complete Set button, Continue button, rest timer progress bar, and Finish Workout button.
- **D-10:** Standardize horizontal padding to a consistent value across workout screens (audit current mix of `.padding()`, `.padding(.horizontal, 32)`, `.padding(.horizontal, 16)`).
- **D-11:** Verify typography hierarchy is consistent: `.title2.weight(.bold)` for exercise name, `.headline` for set info, `.subheadline` for metadata/secondary info, `.caption` for picker labels. Fix any deviations.
- **D-12:** Scope: workout tab screens only (WorkoutSessionView, RestTimerView, ExerciseOverviewSheet, WorkoutFinishedView, WorkoutSetRow). Template list, exercise catalog, and settings views are out of scope.

### Claude's Discretion
- Exact transition animation between minimal set screen and full picker input (fade, slide, or instant)
- Whether the minimal screen shows elapsed workout time or just set/exercise info
- Specific padding value to standardize on (16 or 20 or 24)
- Whether to add `accessibilityHint` in addition to `accessibilityLabel` where appropriate
- Color constant naming convention and file location

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Firmware Reference (Behavioral Spec)
- `/Users/olli/schenanigans/gymtracker/firmware/src/statemachine/states/WorkoutStartSetState.cpp` — Firmware minimal set screen: "SET" label (centered, bold), large set number, exercise name with marquee, muscle group, "CENTER when done" hint. This is the primary behavioral reference for UX-01.
- `/Users/olli/schenanigans/gymtracker/firmware/src/statemachine/states/WorkoutStartSetState.h` — StartSetState interface

### SwiftUI Views (Modify)
- `iosApp/iosApp/Views/Workout/WorkoutSessionView.swift` — Main workout view: add minimal set screen mode, haptic on Complete Set, accessibility labels throughout. Currently ~600 lines with active view, rest timer integration, set input, completed sets, edit sheet, recap view.
- `iosApp/iosApp/Views/Workout/RestTimerView.swift` — Rest timer view: add accessibility labels, uses hardcoded accent color for progress bar
- `iosApp/iosApp/Views/Workout/WorkoutSetRow.swift` — Set row component: add accessibility label with full set description
- `iosApp/iosApp/Views/Workout/ExerciseOverviewSheet.swift` — Exercise overview: add accessibility labels for exercise items and drag handles
- `iosApp/iosApp/Views/Workout/WorkoutFinishedView.swift` — Finished summary: add accessibility labels, verify color consistency

### KMP ViewModel (Reference only — no changes expected)
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt` — ViewModel unchanged for this phase; all changes are SwiftUI-side

### Requirements
- `.planning/REQUIREMENTS.md` — UX-01 (minimal set screen), UX-02 (haptic on set complete), UX-03 (accessibility labels), UX-04 (visual consistency)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **UINotificationFeedbackGenerator (WorkoutSessionView.swift:595):** Existing haptic pattern for rest completion — reuse same approach for set completion
- **RestTimerView:** Clean, focused component — good reference for minimal set screen layout (centered content, large text, clear hierarchy)
- **WorkoutSetRow:** Existing set row component — add accessibility label here, consumed by both active workout and recap views

### Established Patterns
- **Sealed class state observation:** `asyncSequence(for: viewModel.sessionStateFlow)` — minimal screen is SwiftUI-only, no ViewModel changes
- **@State toggle pattern:** `showExerciseOverview`, `showEditSheet`, `showAbandonDialog` — follow same pattern for `showSetInput`
- **Color hardcoding:** `Color(red: 0.4, green: 0.733, blue: 0.416)` used in 4+ places — needs extraction
- **Toolbar layout:** Leading X button + trailing ellipsis Menu — established in Phase 9, no changes needed

### Integration Points
- **activeWorkoutView() (WorkoutSessionView.swift:120):** Entry point for minimal screen — add view mode toggle between minimal and full input
- **.onChange(of: active.currentSetIndex)** / `.onChange(of: active.currentExerciseIndex)` — reset `showSetInput` to false when set/exercise changes
- **Color.appAccent extension** — new file or extension in existing Common/ directory

</code_context>

<specifics>
## Specific Ideas

- Firmware's WorkoutStartSetState centers "SET" in large serif bold, set number in even larger bold, exercise name in smaller font. Mobile equivalent: `.largeTitle.weight(.bold)` for set number, `.title3` for exercise name, clean centered layout.
- Firmware shows "CENTER when done" at the bottom — mobile equivalent: "Tap when done" or just make the whole screen tappable with a subtle prompt.
- The minimal screen gives lifters a distraction-free view while performing their set — they see only what matters (which set, which exercise).
- Visual polish should not change any behavior, only tighten the existing look. No new screens or flows beyond the minimal set screen.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 10-minimal-set-screen-ui-polish*
*Context gathered: 2026-03-30*
