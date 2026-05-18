---
phase: quick-260517-w2f
plan: 01
subsystem: workout-ui
tags: [workout, ui-refactor, ios, android, end-workout, early-exit]
requires: []
provides:
  - "Single trailing-toolbar end-workout button (iOS + Android)"
  - "Eliminated leading X-button + Abandon Workout dialog on both platforms"
  - "Eliminated redundant 'Finish Workout' menu item on both platforms"
affects:
  - iosApp/iosApp/Views/Workout/WorkoutSessionView.swift
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt
key-files:
  created: []
  modified:
    - iosApp/iosApp/Views/Workout/WorkoutSessionView.swift
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt
decisions:
  - "Toolbar order chosen: minimal-reorder — visual L→R is Menu, End-Button, Chip on iOS (code-add order: Chip, End-Button, Menu in trailing items, which SwiftUI renders right-to-left)"
  - "Option B (clean) for Android: removed onDiscardWorkout param + threading from ActiveWorkoutContent — only remaining viewModel.discardWorkout() callsite is UndertrainedMusclesDialog CTA (out-of-scope per plan verification)"
  - "Removed now-unused Kotlin icon imports (Icons.Filled.Cancel, Icons.Default.Close) as direct consequence of the refactor"
metrics:
  duration_minutes: 8
  completed: "2026-05-17"
  tasks_completed: 2
  tasks_total: 3
  files_modified: 2
requirements:
  - QUICK-W2F-01
---

# Quick 260517-w2f: Workout End-Button-Konsolidierung Summary

Consolidated three redundant workout-end entry points (leading X-button + Abandon dialog, "Finish Workout" menu item, "Workout beenden" menu item) into a single context-sensitive trailing-toolbar button on both iOS and Android. The new button routes to direct Review when all sets are done, otherwise opens the existing EarlyExitConfirmDialog which is now the SOLE gate before penalty/budget consumption.

## Files Modified

| File | Net diff |
|------|----------|
| iosApp/iosApp/Views/Workout/WorkoutSessionView.swift | +21 / −63 (−42 LOC) |
| androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt | +6 / −79 (−73 LOC) |

## Commits

- `576bd72` refactor(260517-w2f): consolidate iOS end-workout buttons into single trailing action
- `0bb5c18` refactor(260517-w2f): consolidate Android end-workout buttons into single trailing action

## Verification Status

### Task 1 — iOS (auto)

- Build: `xcodebuild ... -destination 'generic/platform=iOS Simulator' -configuration Debug build` → **BUILD SUCCEEDED** (69.5s)
- All grep counts match plan expectations:
  - `showAbandonDialog`: 0 ✓
  - `Abandon Workout`: 0 ✓
  - `Finish Workout`: 0 ✓
  - `Workout beenden`: 1 (accessibility label) ✓
  - `viewModel.discardWorkout`: 0 ✓
  - `viewModel.requestEarlyExit`: 1 (dialog confirm closure) ✓
  - `checkmark.circle`: 1 (new button icon) ✓
  - `earlyExitDialogConfig`: 4 (≥4) ✓
  - `showEarlyExitDialog`: 3 (≥3) ✓

### Task 2 — Android (auto)

- Build: `./gradlew :androidApp:assembleDebug` → **BUILD SUCCESSFUL** (5s)
- All grep counts match plan expectations:
  - `showAbandonDialog`: 0 ✓
  - `Abandon Workout`: 0 ✓
  - `"Finish Workout"`: 0 ✓
  - `onShowAbandonDialog`: 0 ✓
  - `onDiscardWorkout`: 0 ✓
  - `navigationIcon`: 0 ✓
  - `early_exit_menu_label`: 1 (contentDescription on new IconButton) ✓
  - `Icons.Default.Check`: 4 (3 pre-existing + 1 new on line 482) ✓
  - `viewModel.requestEarlyExit`: 1 (EarlyExitConfirmDialog onConfirm) ✓
  - `viewModel.enterReview`: 2 (call-site lambda + inline allDone branch in onEarlyExitMenuClick) ✓
  - `viewModel.discardWorkout`: 1 — **expected exception per plan verification**: still called from UndertrainedMusclesDialog "add exercise for muscle" CTA at line 213 (out-of-scope per plan; do NOT remove).

### Task 3 — Manual UAT (deferred to user)

Manual verification gate — see PLAN §Task 3 `how-to-verify` for the 4 flows × 2 platforms = 8 verification steps.

## Resulting UI Surface

### iOS trailing toolbar

Code-add order in `.toolbar { ... }` (top-to-bottom): GeofenceStatusChip → End-Button → Menu.
SwiftUI renders trailing items right-to-left in code-add order, so visual L→R is: **[Menu] [End-Button] [Chip]** (the menu is rightmost, end-button in the middle, chip on the left).

Ellipsis menu now contains exactly 2 items:

- Skip Exercise
- Exercise Overview

### Android TopAppBar `actions = { ... }`

Visual L→R: **[GeofenceStatusChip] [End-Button (Icons.Default.Check)] [Overflow Menu]**.

Ellipsis menu now contains exactly 2 items:

- Skip Exercise
- Exercise Overview

## Plan-required Verification Notes

1. **`viewModel.discardWorkout()` from UI code**: Now unreferenced from active-workout UI paths on both platforms. Remaining single callsite at `WorkoutSessionScreen.kt:213` is the UndertrainedMusclesDialog's "add exercise for muscle" CTA — explicitly out-of-scope per plan verification, retained intact. VM method itself is intact on both platforms.

2. **`onEnterReview` parameter to `ActiveWorkoutContent` (Android)**: The "Finish Workout" DropdownMenuItem (the only consumer inside `ActiveWorkoutContent`'s body) was deleted. The `onEarlyExitMenuClick` lambda (defined in the outer scope at `WorkoutSessionScreen.kt:285-302`) directly invokes `viewModel.enterReview()` without going through the `onEnterReview` parameter, so `onEnterReview: () -> Unit` is now an unused parameter inside `ActiveWorkoutContent`. **Flagged for follow-up cleanup, not removed in this quick-task** per plan scope.

3. **`onDiscardWorkout` parameter and threading (Android)**: Per Option B (clean) decision in the plan — removed the param from the signature, the call-site lambda, AND the body. Verified via grep: zero remaining references.

4. **Icon choice**: `checkmark.circle` (iOS SF Symbol) / `Icons.Default.Check` (Android Material). Success-shaped neutral icon — destructive penalty path is still gated by the dialog's `.destructive` role on the Confirm button. If the user prefers a more "ending" connotation (e.g., `flag.checkered`, `stop.circle`), this is a one-line tweak in a follow-up quick-task.

5. **Toolbar visual ordering (iOS)**: Captured above. Minimal-reorder option taken — preserved the existing Chip-then-Menu code order and inserted the new End-Button between them. Visual L→R becomes Menu, End-Button, Chip. To get a different visual order (e.g., End-Button on the right), the trailing ToolbarItems need to be reordered in code; defer to user UAT.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Removed unused Kotlin icon imports**
- **Found during:** Task 2
- **Issue:** `Icons.Filled.Cancel` (was used by the deleted "Workout beenden" DropdownMenuItem's leadingIcon) and `Icons.Default.Close` (was used by the deleted navigationIcon's Icon) became unused imports after the refactor. Kotlin compiles fine with unused imports (warning, not error), but removing them is the correct cleanup tied to the deletions.
- **Fix:** Removed both import lines in the same Task 2 commit.
- **Files modified:** androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt
- **Commit:** 0bb5c18

No other deviations from plan. Both tasks executed exactly as specified.

## Threat Model Coverage

All threats in `<threat_model>` retained their plan-defined dispositions:

- T-quick-w2f-01 (Tampering, UI discard-path removed) — accepted per user direction; VM method intact.
- T-quick-w2f-02 (Repudiation, allDone silent-save) — accepted; expected UX.
- T-quick-w2f-03 (DoS, misclick) — mitigated by EarlyExitConfirmDialog destructive-role Confirm button; Cancel always available.
- T-quick-w2f-04, T-quick-w2f-05 — n/a.

No new threat surface introduced (UI-only refactor, no data-flow / permission / network changes).

## Self-Check: PASSED

- `iosApp/iosApp/Views/Workout/WorkoutSessionView.swift` — FOUND
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt` — FOUND
- Commit `576bd72` — FOUND
- Commit `0bb5c18` — FOUND
- iOS Debug build — BUILD SUCCEEDED
- Android Debug build — BUILD SUCCESSFUL
- All plan-mandated grep gates — PASSED
