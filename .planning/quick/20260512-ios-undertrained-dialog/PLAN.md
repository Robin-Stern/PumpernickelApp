---
slug: ios-undertrained-dialog
created: 2026-05-12
status: in-progress
---

# iOS: Undertrained Muscles Alert

**Goal:** Add the "Vernachlässigte Muskeln" dialog to iOS WorkoutSessionView — matching the feature already present on Android.

## Context

- Android (`WorkoutSessionScreen.kt:1263`) shows an `AlertDialog` on workout start when `undertrainedMuscles` is non-empty.
- Shared `WorkoutSessionViewModel` already exposes `undertrainedMuscles: StateFlow<List<MuscleGroup>>` with `@NativeCoroutinesState` → available on iOS as `undertrainedMusclesFlow`.
- iOS `WorkoutSessionView.swift` has no observer for this flow and no UI for the dialog.

## Tasks

1. Add `@State private var showUndertrainedDialog = false` and `@State private var undertrainedMuscles: [MuscleGroup] = []` to `WorkoutSessionView`.
2. Add `group.addTask { await observeUndertrainedMuscles() }` to the task group in `.task`.
3. Implement `observeUndertrainedMuscles()` — watches `viewModel.undertrainedMusclesFlow`, sets `undertrainedMuscles` and `showUndertrainedDialog = true` when non-empty.
4. Add `.alert("Vernachlässigte Muskeln", isPresented: $showUndertrainedDialog)` modifier on `activeWorkoutView` with muscle list in the message and an OK button.

## File

`iosApp/iosApp/Views/Workout/WorkoutSessionView.swift`
