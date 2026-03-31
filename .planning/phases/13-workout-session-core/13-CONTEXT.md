# Phase 13: Workout Session Core - Context

**Gathered:** 2026-03-31
**Status:** Ready for planning

<domain>
## Phase Boundary

Port the complete workout execution flow to Jetpack Compose: active session with custom drum picker for reps/weight entry, rest timer with circular progress, exercise overview bottom sheet with drag reorder and skip, abandon guards (save/discard/cancel), post-workout recap with tap-to-edit sets, and finished summary state. This is the largest and most complex phase.

</domain>

<decisions>
## Implementation Decisions

### Custom Drum Picker (HIGHEST RISK)
- **D-01:** Custom composable using `LazyColumn` with `SnapFlingBehavior` for each picker column. Fling physics with snap-to-item behavior.
- **D-02:** Reps picker: 0–50, step 1. Weight picker: 0–1000, step 2.5kg (stored as kgX10: 0–10000, step 25).
- **D-03:** Two pickers side-by-side (reps + weight) in a Row composable. Visual: highlighted center item with faded items above/below.
- **D-04:** Picker observes kg/lbs unit from SettingsViewModel. Display converts kgX10 to display value based on unit.
- **D-05:** Picker must support pre-fill from ViewModel's preFill StateFlow (auto-increment: set 2+ uses previous actuals, set 1 uses template targets).

### Rest Timer
- **D-06:** CircularProgressIndicator with countdown text centered inside. Progress = remainingSeconds / totalSeconds.
- **D-07:** "Skip Rest" button below the timer. Maps to `viewModel.skipRest()`.
- **D-08:** Rest timer auto-starts after `completeSet()`. Completion triggers haptic feedback.

### Exercise Overview Sheet
- **D-09:** Material 3 `ModalBottomSheet` with 3 sections: Completed (greyed), Current (highlighted), Up Next (reorderable).
- **D-10:** Drag reorder for "Up Next" exercises using move-up/move-down buttons (matching Phase 12's approach, avoiding external drag library).
- **D-11:** Skip button per pending exercise. Maps to `viewModel.skipExercise()`.
- **D-12:** Tap exercise in "Up Next" to jump to it. Maps to `viewModel.jumpToExercise(index)`.

### Abandon Guards
- **D-13:** Material 3 `AlertDialog` with 3 actions: "Save & Exit" (filled), "Discard" (text/destructive red), "Cancel" (text).
- **D-14:** Triggered by back press or X button in toolbar. Guard skips when 0 sets completed (matches iOS behavior).
- **D-15:** Progress summary in dialog body: "Exercise X/Y, Z sets completed".

### Workout State Rendering
- **D-16:** Single `WorkoutSessionScreen` composable with `when` branches on sealed class state (Idle/Active/Reviewing/Finished). Matches iOS WorkoutSessionView pattern.
- **D-17:** Active state shows: current exercise name, set counter, drum pickers, "Complete Set" button, rest timer (when resting), toolbar with X (abandon) and Menu (skip, overview, finish).
- **D-18:** Minimal set screen: when not in rest and not showing input, display "SET N" + exercise name + "Tap when done". Toggle to reveal drum pickers on tap (matches iOS UX-01).

### Post-Workout Recap (Reviewing State)
- **D-19:** Summary header: exercise count, set count, total duration.
- **D-20:** Scrollable exercise list with per-set detail rows (reps × weight).
- **D-21:** Tap any set row to open edit sheet with drum pickers. Maps to existing ViewModel edit methods.
- **D-22:** "Save Workout" button commits reviewed data. Maps to `viewModel.saveReviewedWorkout()`.

### Finished State
- **D-23:** Summary card: workout name, duration, total sets, total exercises. "Done" button dismisses and resets to Idle.

### Toolbar / Navigation
- **D-24:** TopAppBar with leading X button (abandon), trailing overflow Menu (Skip Exercise, Exercise Overview, Finish Workout).
- **D-25:** WorkoutSessionScreen is a full-screen composable navigated to from TemplateListScreen via `WorkoutSessionRoute(templateId)`.

### Haptic Feedback
- **D-26:** Android `Vibrator` service via `context.getSystemService()`. Short vibration (50ms) on set completion. No expect/actual needed — Android-only code in androidApp module.

### Claude's Discretion
- Drum picker visual styling (font sizes, fade gradient, selection indicator)
- Rest timer color scheme (primary color fill vs custom)
- Exact layout proportions for active workout screen
- Animation transitions between workout states
- Edit sheet presentation (ModalBottomSheet vs Dialog)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### iOS Reference (behavior and layout to match)
- `iosApp/iosApp/Views/Workout/WorkoutSessionView.swift` — Full 766-line reference: Active/Reviewing/Finished states, picker setup, rest timer, abandon dialog, toolbar menu, minimal set screen, edit sheet
- `iosApp/iosApp/Views/Workout/RestTimerView.swift` — Rest timer countdown layout
- `iosApp/iosApp/Views/Workout/WorkoutSetRow.swift` — Set row display component
- `iosApp/iosApp/Views/Workout/WorkoutFinishedView.swift` — Finished summary screen
- `iosApp/iosApp/Views/Workout/ExerciseOverviewSheet.swift` — Exercise overview with sections, reorder, skip

### Shared KMP ViewModel (consume directly — DO NOT MODIFY)
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt` — State machine, all methods (completeSet, skipRest, skipExercise, enterReview, saveReviewedWorkout, discardWorkout, reorderExercise, jumpToExercise, resetToIdle, resumeWorkout)
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/WorkoutSession.kt` — SessionExercise, SessionSet domain models

### Android Navigation (already created)
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/Routes.kt` — WorkoutSessionRoute(templateId: Long) already defined
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt` — NavHost entry point to wire

### Requirements
- `.planning/REQUIREMENTS-v1.5.md` — ANDROID-05, ANDROID-06, ANDROID-07

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `WorkoutSessionViewModel`: Complete state machine with all methods — Android UI just observes and calls
- `Theme.kt`: Material 3 theme with accent green for rest timer, buttons
- All existing screen patterns from Phase 12: koinViewModel(), collectAsState(), TopAppBar toolbar

### Established Patterns (from Phases 11-12)
- Package: `ui/screens/` for screen composables
- ViewModel: `val viewModel: WorkoutSessionViewModel = koinViewModel()`
- State: `val sessionState by viewModel.sessionStateFlow.collectAsState()`
- Navigation: `navController.navigate(WorkoutSessionRoute(templateId))`

### Integration Points
- MainScreen.kt NavHost needs `composable<WorkoutSessionRoute>` entry
- TemplateListScreen already navigates to WorkoutSessionRoute
- WorkoutSessionViewModel.preFill, personalBest, previousPerformance, elapsedSeconds, weightUnit — all StateFlows to collect

### Key ViewModel Methods to Wire
- `startWorkout(templateId)` / `resumeWorkout()` — init
- `completeSet(reps, weightKgX10)` — set completion
- `skipRest()` — rest skip
- `skipExercise()` — exercise skip
- `enterReview()` — transition to recap
- `saveReviewedWorkout()` — save from recap
- `discardWorkout()` — abandon
- `reorderExercise(from, to)` — exercise reorder
- `jumpToExercise(index)` — exercise jump
- `resetToIdle()` — post-finish cleanup
- `editSet(exerciseIndex, setIndex, reps, weightKgX10)` — edit during recap

</code_context>

<specifics>
## Specific Ideas

- iOS uses UIPickerView(.wheel) — Android drum picker should feel similar with momentum scrolling and snap
- iOS uses `.sensoryFeedback(.success)` — Android uses `Vibrator.vibrate(VibrationEffect.createOneShot(50, DEFAULT_AMPLITUDE))`
- iOS rest timer is a simple VStack with countdown text and skip button — Android uses CircularProgressIndicator wrapping the countdown
- iOS abandon dialog uses `.confirmationDialog` with destructive role — Android uses AlertDialog with red text for Discard
- iOS minimal set screen shows "SET N" in large bold text with "Tap when done" — Android matches this with large MaterialTheme.typography.displayLarge

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 13-workout-session-core*
*Context gathered: 2026-03-31*
