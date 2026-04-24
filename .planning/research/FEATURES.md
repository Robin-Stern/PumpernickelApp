# Feature Landscape: v1.1 Workout Polish & Firmware Parity

**Domain:** Fitness workout tracking - UI polish and firmware feature parity
**Researched:** 2026-03-29
**Overall confidence:** HIGH (firmware reference is first-party; competitor patterns verified across multiple sources)

## Table Stakes

Features that polished workout apps (Strong, Hevy, JEFIT, FitBod) universally provide. Missing any of these makes the app feel like a prototype.

| Feature | Why Expected | Complexity | Depends On | Notes |
|---------|--------------|------------|------------|-------|
| Auto-fill from previous set | Every competitor does this. Strong, Hevy, Setgraph, RepCount all pre-fill weight/reps from your last performance. Users expect to adjust, not re-enter from scratch. | Low | Existing `completeSet()` flow | **The firmware already does this** (lines 86-96 of WorkoutSetEntryState.cpp): first set uses template targets, subsequent sets copy previous set's actual values. The app currently pre-fills from *template* targets for every set -- needs to switch to previous-set-actual after the first completed set. |
| Abandon guard (save & exit vs discard) | Every serious tracker prevents accidental data loss. Hevy has "Discard Workout" button with implicit confirmation. Strong allows finishing at any time. The firmware has a dedicated `WorkoutAbandonConfirmState` with two options: "Save & Exit" and "Discard". | Medium | Existing `discardWorkout()` and `finishWorkout()` | Currently the app has resume/discard on crash recovery but no guard when the user tries to navigate away mid-workout. The firmware defaults to "Save & Exit" (the safer option), which is the correct UX pattern. |
| Context menu on exercises | Hevy: three-dot menu with reorder, replace, remove. Strong: drag-and-drop reorder + swipe-to-delete. JEFIT: edit button with drag handles. Users expect some way to manage exercises during a workout. | Medium | Existing `ExerciseOverviewSheet` | The firmware has `WorkoutContextMenuState` with "Skip Current Exercise" and "Adjust Order". The iOS convention is a context menu (long press or three-dot button) or swipe actions. |
| Validation and error prevention | No app lets you submit 0 reps or negative weight without feedback. Strong and Hevy both have implicit validation. | Low | Existing `completeSet()` | Currently `completeSet(reps: 0, weightKgX10: 0)` succeeds silently. Needs minimum validation (reps >= 1, or at least a confirmation for 0). |

## Differentiators

Features that elevate the experience beyond competitors. Not all apps have these, but users notice and appreciate them.

| Feature | Value Proposition | Complexity | Depends On | Notes |
|---------|-------------------|------------|------------|-------|
| Scroll wheel pickers for reps/weight | **Firmware parity + iOS-native feel.** The firmware uses rotary encoder scroll pickers as its primary input (WorkoutSetEntryState renders a two-column wheel picker). On iOS, SwiftUI's `.pickerStyle(.wheel)` is the native equivalent. Strong uses number pads; Hevy uses text fields. Scroll wheels are faster for gym use (sweaty hands, gloves) and feel more "physical" than typing. | Medium | New SwiftUI view replacing text fields in `setInputSection` | Use two side-by-side `Picker(.wheel)` views. Reps: 0-50 (step 1). Weight: 0-200kg displayed (0-2000 internal, step 2.5kg = 25 in kgX10). Must handle lbs conversion for display. The firmware's weight picker steps by 2.5kg (25 in weight_kg_x10), which matches standard plate increments. |
| Minimal "doing set" screen | **Firmware parity.** The firmware has a dedicated `WorkoutStartSetState` that shows ONLY the set number, exercise name, and muscle group -- a distraction-free screen while actually lifting. The user presses a button when done to enter the data entry screen. This two-screen flow (doing set -> data entry) is unique to the firmware and not standard in mobile apps. | Low | New SwiftUI view | Strong, Hevy, and most mobile apps combine the "doing set" and "data entry" into one screen. The firmware's separation makes sense for a button-driven device but may feel like unnecessary friction on a touchscreen. **Recommendation:** Implement as an optional overlay or simplified view that auto-dismisses, not a mandatory intermediate screen. A large set number with exercise name, tap anywhere to go to data entry. Keeps firmware parity without adding friction. |
| Post-workout recap with edit | **Firmware parity + Hevy pattern.** The firmware has `WorkoutFinishState` -> `WorkoutRecapState` -> `WorkoutSaveState`. Hevy shows a pre-save review screen where you can edit workout name, duration, and review exercises before saving, followed by motivational summary slides (PRs, volume, consistency). Currently the app jumps straight to a simple "Workout Complete!" screen with no editing. | High | Existing `WorkoutFinishedView`, existing `finishWorkout()` | This is the highest-complexity new feature. Needs: (1) intercept finish flow to show recap instead of immediately saving, (2) display all exercises with sets/reps/weight, (3) allow editing individual sets, (4) allow removing exercises or sets, (5) "Save" button that persists to Room. The firmware's recap is read-only (browse exercises, then save). Hevy allows editing. **Recommendation:** Start with firmware-style read-only recap + save button. Editing can come later if time permits. |
| Personal best display on set entry | **Firmware parity + Hevy pattern.** The firmware shows "PB: X.X kg" at the bottom of the set entry screen. Hevy has "Live Personal Record Notification" that triggers a banner when you beat a PR, tracking 5 types: heaviest weight, best 1RM, best set volume, most reps, best duration. Strong tracks PRs in history but doesn't show them during workout. | Medium | New query in `WorkoutRepository`, existing `previousPerformance` flow | The firmware only tracks average weight as PB (from ExerciseTrend). For the mobile app, start simple: show heaviest weight ever lifted for each exercise during set entry. This requires a new Room query: `SELECT MAX(actualWeightKgX10) FROM completed_sets WHERE exerciseId = ?`. **Do not** build the full Hevy-style multi-type PR system -- that's scope creep for a university project. |
| Mid-workout exercise reorder | **Firmware parity.** The firmware has `WorkoutExerciseMoveState` with a dedicated reorder UI: select an exercise, scroll up/down to move it, confirm or cancel. This is essential for gym use (equipment unavailable, want to change order). | Medium | Existing `ExerciseOverviewSheet`, new ViewModel method | The firmware uses an `exerciseOrder[]` indirection array, not direct reordering of the workout data. This is the correct pattern: maintain a queue order that maps to exercise indices, so completed set data stays consistent. SwiftUI's `.onMove` modifier handles drag-and-drop in lists. **Key constraint from firmware:** Only exercises after the current position in the queue can be reordered (you can't move a completed exercise). |

## Anti-Features

Features to explicitly NOT build in this milestone. Either too complex, out of scope, or actively harmful.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Hevy-style multi-slide post-workout summary | Hevy shows 4-5 motivational slides after workout (PRs, consistency graph, volume comparison, muscle diagram, "you lifted a truck!"). Impressive but massive scope: requires charting, muscle group aggregation, creative comparisons. | Simple recap screen listing exercises, sets, and best weight per exercise. Save and done. |
| Live PR notification banner during workout | Hevy's live PR fires a banner when you beat a record. Requires real-time comparison against all historical data for 5 PR types on every set completion. Complex, distracting during sets. | Show personal best weight on the set entry screen (static display, not a popup). Let the user notice they beat it. |
| Superset/drop set/warm-up set types | Hevy and Strong both support set types (normal, warm-up, drop set, failure). Adds complexity to data model, Room schema, UI. Not in the firmware, not in the Lastenheft. | All sets are normal sets. The schema supports this later via an enum column. |
| RPE/RIR tracking | FitBod tracks Reps in Reserve. Hevy supports RPE. Adds UI fields, data model changes, and dubious value for a prototype. | Defer entirely. Not in firmware, not in Lastenheft. |
| Exercise replacement during workout | Hevy allows replacing one exercise with another mid-workout. Complex: requires exercise picker integration, data migration for the swapped exercise. | Users can skip an exercise (via context menu) and it won't be saved. They can't swap it for a different one mid-workout. |
| Workout notes/comments | Strong and Hevy both support per-workout and per-exercise notes. Adds text fields, storage, display. Nice to have but not table stakes. | Defer. Not in firmware, not critical for the Lastenheft requirements. |
| Auto-advance to next exercise on last set | Hevy has "smart superset scrolling" that auto-advances. The firmware transitions through its FSM automatically. | The app already auto-advances via `computeNextCursor()`. The key difference is the firmware shows a "doing set" screen before each set. Keep the existing auto-advance behavior. |
| Weight plate calculator | Hevy and FitBod show which plates to load on the barbell. Useful but complex (need to know barbell weight, available plates, do the math). | Defer entirely. Not in firmware. |

## Feature Dependencies

```
Auto-fill from previous set ---- (standalone, touches only prefillInputs logic)
                                 |
Scroll wheel pickers ----------- (replaces text fields, can coexist with auto-fill)
     |
     v
Personal best display ---------- (adds to set entry screen, needs Room query)

Minimal "doing set" screen ----- (new screen before set entry, standalone)

Mid-workout exercise reorder --- (extends ExerciseOverviewSheet, adds ViewModel method)
     |
     v
Context menu ------------------- (wraps skip + reorder into a menu, needs reorder first)
     |
     v
Abandon guard ------------------ (intercepts back navigation, needs context menu for "skip exercise" too)

Post-workout recap/edit -------- (intercepts finish flow, most complex, do last)
     |
     depends on: all exercise/set data being correct at finish time
     depends on: personal best query (to show PB badges on recap)
```

## Detailed Feature Specifications

### 1. Scroll Wheel Pickers

**Firmware reference:** `WorkoutSetEntryState` renders two side-by-side pickers (reps on left, weight on right) with scroll wheel semantics. The active picker is highlighted with an inverted box. Only 3-4 values are visible at a time, centered on the selected value.

**iOS implementation pattern:**
- Two `Picker` views with `.pickerStyle(.wheel)` in an `HStack`
- Reps picker: values 0-50, integer step
- Weight picker: values in display unit (kg or lbs), step 2.5kg (or 5lbs equivalent)
- The firmware's weight steps of 2.5kg (25 in kgX10) match standard Olympic plate increments
- For lbs mode: 5lb steps are conventional (2.5kg ~ 5.5lbs, but gyms use 5lb increments)
- SwiftUI wheel pickers have a fixed height and show ~5 visible options, which matches the firmware's 3-4 visible items

**Key UX details from competitors:**
- Strong: uses number pad (text input), not wheel pickers
- Hevy: uses text fields with numeric keyboard
- The firmware's wheel picker is actually a differentiator -- faster for in-gym use
- Must preserve the "Complete Set" button below the pickers

**Complexity notes:**
- SwiftUI `Picker(.wheel)` is straightforward for integer ranges
- Weight picker needs to display formatted values (e.g., "52.5" for kgX10 value of 525)
- Must handle unit conversion display (kg vs lbs) while storing in kgX10
- The edit sheet for completed sets should also use pickers (consistency)

### 2. Auto-Increment (Pre-fill from Previous Set)

**Firmware reference:** `WorkoutSetEntryState::onEnter()` lines 86-96:
```
if (workoutCurrentSetIdx == 0) {
    // First set: use template targets
    repsValue = template.targetReps;
    weightValue = template.targetWeight;
} else {
    // Subsequent set: use previous set's actual values
    repsValue = previousSet.reps;
    weightValue = previousSet.weight;
}
```

**Current app behavior:** `prefillInputs()` in `WorkoutSessionView.swift` always uses `currentSet.targetReps` and `currentSet.targetWeightKgX10` from the template. This means every set pre-fills with template defaults, ignoring what the user actually did on the previous set.

**Required change:** After the user completes set N, set N+1 should pre-fill with set N's actual values (not template targets). This requires:
1. ViewModel: modify `completeSet()` to update the next set's target values in state, OR
2. SwiftUI: modify `prefillInputs()` to check for the previous completed set's actuals

**Recommendation:** Do this in the ViewModel (option 1). When `completeSet()` updates the exercises list, also update the next set's `targetReps` and `targetWeightKgX10` to match the just-completed set's actuals. This keeps the single source of truth in the ViewModel and the SwiftUI `prefillInputs` logic unchanged.

**Competitor patterns:**
- Strong: auto-fills from last workout's values for the same exercise (cross-session)
- Hevy: offers choice between "last occurrence across all routines" or "last occurrence in current routine"
- RepCount: prefills today's workout with last workout's weights
- **The firmware pattern (same-session, previous set)** is different from all these -- it's intra-workout, not cross-workout. The app already has cross-workout via `previousPerformance`. Both patterns are valuable.

### 3. Minimal "Doing Set" Screen

**Firmware reference:** `WorkoutStartSetState` renders:
- Large centered "SET" label
- Huge set number (24pt font)
- Exercise name (with marquee if long)
- Primary muscle group
- "CENTER when done" hint at bottom

This screen exists because the firmware device sits on your wrist/equipment while you lift, and you press a physical button when done. On a phone, the equivalent is: the screen you see while lifting, before you enter data.

**Mobile app adaptation:**
- Full-screen overlay or sheet showing: set number (huge), exercise name, muscle group
- Tap anywhere or "Done" button to transition to the set entry (scroll wheel pickers)
- Optional: show rest timer countdown if rest just finished
- **This should be opt-in.** Most mobile users prefer to see the data entry screen immediately. Make it a DataStore preference: "Show 'doing set' screen" toggle (default: off for mobile, firmware users can enable it).

**Why opt-in:** Mobile apps (Strong, Hevy, FitBod) never have a separate "doing set" screen. They show the data entry form and the user fills it in when they're done with the set. Adding a mandatory intermediate screen would feel like friction to anyone who hasn't used the firmware.

### 4. Post-Workout Recap/Edit

**Firmware flow:**
1. Last set of last exercise -> rest timer -> `WorkoutFinishState` ("DONE! Workout Complete" with SELECT: View Recap, BACK: Abandon)
2. `WorkoutRecapState` - scrollable list: "Save Workout" at top, then each exercise with set count and best weight
3. `WorkoutSaveState` - actually persists to flash

**Hevy flow:**
1. Tap "Finish" -> pre-save review screen (edit name, duration, date, review exercises/sets)
2. Tap "Save" -> post-save motivational slides (PRs, consistency, volume)

**Recommended mobile implementation:**
1. Tapping "Finish Workout" transitions to a recap screen (NOT directly saving)
2. Recap screen shows:
   - Workout name (editable? nice-to-have, not required)
   - Duration
   - Total exercises / total sets
   - Per-exercise breakdown: exercise name, completed sets with reps/weight, best weight
   - Personal best indicator if any set beat the PB
3. "Save Workout" button at bottom (prominent, green)
4. "Back to Workout" option to continue working out (changed their mind about finishing)
5. On save: persist to Room, transition to simple "Workout Complete!" confirmation, then dismiss

**What NOT to do:**
- Don't allow inline editing of sets on the recap screen in v1.1 (too complex). The user can go back to the workout to edit.
- Don't show motivational slides (Hevy's "you lifted a truck!"). Save that for v2.
- Don't show progress charts on the recap.

### 5. Mid-Workout Exercise Reorder

**Firmware reference:** Three-layer system:
1. `WorkoutContextMenuState` - menu with "Skip Current Exercise" and "Adjust Order"
2. `WorkoutExerciseListState` - shows all exercises, select one to move
3. `WorkoutExerciseMoveState` - scroll to move the selected exercise, confirm or cancel

**Key firmware design decisions (carry forward):**
- Uses an `exerciseOrder[]` indirection array, not direct array manipulation
- Only exercises AFTER the current queue position can be reordered
- Completed exercises appear but are dimmed/smaller and non-movable
- Current exercise is indicated with a triangle marker
- Cancel restores original order (snapshot before move)
- The "Skip" action swaps current exercise with the next one in the queue

**Mobile adaptation:**
- Extend `ExerciseOverviewSheet` to support reorder mode
- Use SwiftUI `.onMove` for drag-and-drop (already used in `TemplateEditorView`)
- Constraint: only future (non-completed) exercises are movable
- Add "Skip Exercise" as a swipe action or button in the exercise overview
- ViewModel needs: `reorderExercises(from:, to:)` and `skipCurrentExercise()` methods
- These methods operate on the session's exercise list order, NOT Room data

### 6. Abandon Guards

**Firmware reference:** `WorkoutAbandonConfirmState` has:
- Title: "Abandon Workout?"
- Workout name display
- Progress summary: "Ex X/Y, Z sets"
- Two options: "Save & Exit" (default) and "Discard"
- BACK cancels and returns to workout

**Triggers in firmware:**
- BACK press on set entry when on reps column (leftmost) and sets have been confirmed
- BACK_LONG from any workout screen when sets confirmed
- If NO sets confirmed yet, BACK exits freely (no guard needed)

**Mobile adaptation:**
- SwiftUI `.alert` or `.confirmationDialog` with three options:
  1. "Save & Exit" (saves completed sets as a partial workout, like finishWorkout)
  2. "Discard" (deletes active session, returns to template list)
  3. "Cancel" (stay in workout)
- **Default action should be "Cancel"** (the safest choice on mobile, unlike firmware where default is "Save & Exit")
- Trigger: back navigation, swipe-to-dismiss, app backgrounding for extended period
- Guard condition: at least one set is completed (same as firmware)
- If no sets completed, dismiss freely without prompt

### 7. Context Menu

**Firmware reference:** `WorkoutContextMenuState` provides:
- "Skip Current Exercise" - swaps current with next in queue
- "Adjust Order" - opens exercise list for reordering

**Mobile adaptation:**
- Add context menu to the exercise header section or the toolbar
- Options:
  1. "Skip Exercise" - advance to next exercise (same as firmware skip: swap in queue)
  2. "Reorder Exercises" - open exercise overview in reorder mode
  3. "Jump to Exercise" - (already exists via ExerciseOverviewSheet)
- Implementation: SwiftUI `.contextMenu` modifier or a dedicated toolbar button with a menu
- The firmware triggers the context menu via an UP button press. On iOS, use a toolbar "..." button or long-press gesture.

### 8. Personal Best Display

**Firmware reference:** `WorkoutSetEntryState` shows "PB: X.X kg" at the bottom of the screen. Loaded from `ExerciseTrend.averageWeight_kg_x10` (which is actually the best/average weight from trends data).

**Mobile implementation:**
- Add a new Room DAO query: get the maximum weight ever lifted for a given exercise ID across all completed workouts
- Display below the set input pickers: "PB: 80.0 kg" or "PB: 176.4 lbs"
- If the user's current weight entry exceeds the PB, highlight it (e.g., gold color, "New PB!" label)
- This is NOT a popup notification (unlike Hevy's live PR banner) -- it's a static display element

**Data source:** Query `completed_workout_sets` table joining through `completed_workout_exercises`:
```sql
SELECT MAX(actualWeightKgX10)
FROM completed_workout_sets cs
JOIN completed_workout_exercises ce ON cs.completedExerciseId = ce.id
WHERE ce.exerciseId = :exerciseId
```

### 9. General UI Polish

| Polish Item | Why Needed | Complexity | Notes |
|-------------|-----------|------------|-------|
| Input validation | Prevent saving invalid data (0 reps, negative weight) | Low | Add guards in `completeSet()` and set entry UI |
| Keyboard dismissal | Text fields (if any remain) should dismiss on tap outside | Low | Already standard SwiftUI pattern, may need `.onTapGesture` with `UIApplication.shared.sendAction` |
| Accessibility labels | VoiceOver support for pickers and set rows | Low | Add `.accessibilityLabel` to picker values and set rows |
| Haptic feedback on set completion | Satisfying confirmation when completing a set | Low | Already have haptics on rest completion; add `UIImpactFeedbackGenerator.impactOccurred()` on set confirm |
| Animated transitions | Smooth transition between rest timer and set entry | Low-Med | SwiftUI `.animation` and `.transition` modifiers |
| Weight display consistency | Ensure all weight displays use the same formatting | Low | Audit all `formatWeight` calls for consistency |

## MVP Recommendation for v1.1

**Priority order** (based on user impact, firmware parity, and complexity):

1. **Auto-increment from previous set** - Lowest complexity, highest daily-use impact. Every set entry becomes faster. Do this first.
2. **Scroll wheel pickers** - Core firmware parity feature, transforms the input UX. Do alongside or immediately after auto-increment.
3. **Abandon guards** - Data safety feature. Without it, accidental back navigation loses an entire workout. Critical for trust.
4. **Personal best display** - Low-medium complexity, adds motivation during workout. Simple Room query + UI element.
5. **Context menu + skip exercise** - Medium complexity, unlocks exercise management during workout.
6. **Mid-workout exercise reorder** - Medium complexity, extends the context menu. Build after skip is working.
7. **Minimal "doing set" screen** - Low complexity but opt-in. Can build anytime, not blocking anything.
8. **Post-workout recap** - Highest complexity, touches the finish flow. Do last so all other features are stable.

**Defer to v1.2 if time is tight:**
- Post-workout recap (the current simple "Workout Complete!" screen is functional)
- Animated transitions
- Exercise reorder (skip exercise alone covers the critical use case)

## Sources

### Competitor Analysis
- [Strong App](https://www.strong.app/) - Clean interface, number pad input, auto-fill from previous workout
- [Strong Help: My First Workout](https://help.strongapp.io/article/229-my-first-workout) - Workout flow documentation
- [Hevy Features](https://www.hevyapp.com/features/) - Feature list and UX patterns
- [Hevy Live PR](https://www.hevyapp.com/features/live-pr/) - Live personal record notification details
- [Hevy Workout Settings](https://www.hevyapp.com/features/workout-settings/) - 12 configurable workout settings
- [Hevy Track Workouts](https://www.hevyapp.com/features/track-workouts/) - Set logging interface and auto-fill
- [Hevy Track Exercises](https://www.hevyapp.com/features/track-exercises/) - Previous workout values display
- [Hevy Workout Log](https://www.hevyapp.com/features/workout-log/) - Post-workout recap and save flow
- [JEFIT Reorder Exercises](https://www.jefit.com/product-tips-faq/how-to-reorder-exercises-android-and-ios/) - Exercise reorder via drag handles
- [FitBod](https://fitbod.me/) - Auto-increment weight/reps, workout summary with awards
- [Setgraph Best App to Log Workout](https://setgraph.app/ai-blog/best-app-to-log-workout-tested-by-lifters) - Comparative analysis of logging speed

### Technical Implementation
- [SwiftUI Picker wheel style](https://developer.apple.com/documentation/swiftui/pickerstyle/wheel) - Apple documentation for wheel picker
- [SwiftUI Custom Wheel Picker](https://blog.stackademic.com/swiftui-custom-wheel-picker-38800698ae2f) - Custom picker implementation patterns
- [SwiftUI List onMove](https://sarunw.com/posts/swiftui-list-onmove/) - Drag-and-drop reorder in SwiftUI lists
- [Scrolling Pickers in SwiftUI](https://uvolchyk.medium.com/scrolling-pickers-in-swiftui-de4a9c653fb6) - ScrollView-based picker alternatives

### Firmware Reference (first-party, HIGH confidence)
- `gymtracker/firmware/src/statemachine/states/WorkoutSetEntryState.cpp` - Scroll wheel picker + auto-increment + PB display
- `gymtracker/firmware/src/statemachine/states/WorkoutStartSetState.cpp` - Minimal "doing set" screen + exercise queue
- `gymtracker/firmware/src/statemachine/states/WorkoutAbandonConfirmState.cpp` - Abandon guard flow
- `gymtracker/firmware/src/statemachine/states/WorkoutContextMenuState.cpp` - Context menu with skip + reorder
- `gymtracker/firmware/src/statemachine/states/WorkoutExerciseMoveState.cpp` - Exercise reorder with indirection array
- `gymtracker/firmware/src/statemachine/states/WorkoutRecapState.cpp` - Post-workout recap screen
- `gymtracker/firmware/src/statemachine/states/WorkoutFinishState.cpp` - Finish flow with compact + recap transition
