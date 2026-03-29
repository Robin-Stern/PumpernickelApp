---
phase: 08-mid-workout-exercise-reorder
verified: 2026-03-29T19:15:00Z
status: passed
score: 13/13 must-haves verified
gaps: []
human_verification:
  - test: "Drag a pending exercise in the exercise overview sheet to a new position"
    expected: "Exercise moves to new position immediately; exercise overview reflects the new order; after app restart the reordered sequence is preserved"
    why_human: "Drag gesture behavior, visual reorder confirmation, and crash-recovery round-trip require device/simulator execution"
  - test: "Tap the forward.fill toolbar button to skip the current exercise"
    expected: "Cursor advances to next exercise; skip button disappears when on the last exercise"
    why_human: "Button visibility state and cursor advance require runtime UI observation"
  - test: "Tap 'Skip' inline in the exercise overview sheet on the current exercise row"
    expected: "Sheet dismisses, cursor advances to next exercise; button absent when current is last exercise"
    why_human: "Conditional button rendering and sheet-dismiss interaction require runtime observation"
---

# Phase 8: Mid-Workout Exercise Reorder Verification Report

**Phase Goal:** Allow reordering pending exercises mid-workout using the firmware's exerciseOrder pattern.
**Verified:** 2026-03-29T19:15:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `reorderExercise(from, to)` moves a pending exercise to a new position in the exercises list | VERIFIED | `WorkoutSessionViewModel.kt:428-449` — method exists, mutates `exercises` list + `templateOriginalIndices` in parallel, calls `persistExerciseOrder()` |
| 2 | Only exercises after `currentExerciseIndex` can be reordered | VERIFIED | `WorkoutSessionViewModel.kt:430-435` — `pendingStart = currentExerciseIndex + 1`; bounds guards reject `absFrom < pendingStart` |
| 3 | `skipExercise()` advances `currentExerciseIndex` by 1 and resets to first incomplete set | VERIFIED | `WorkoutSessionViewModel.kt:456-477` — advances index, finds `indexOfFirst { !it.isCompleted }`, updates Room cursor |
| 4 | Skip on last exercise is a no-op | VERIFIED | `WorkoutSessionViewModel.kt:460` — `if (nextIndex >= active.exercises.size) return` |
| 5 | After reorder, `completeSet()` persists the template-original `exerciseIndex` to Room, not the display index | VERIFIED | `WorkoutSessionViewModel.kt:312-320` — `templateOriginalIndices[exIdx]` used as `templateExIdx` passed to `saveCompletedSet()` |
| 6 | After crash + resume, exercises appear in the reordered sequence with completed sets correctly overlaid | VERIFIED | `WorkoutSessionViewModel.kt:228-261` — overlay applied in template order first, then `exerciseOrder` CSV string reorders the list; `templateOriginalIndices` rebuilt from stored order |
| 7 | Pre-migration sessions (empty `exerciseOrder`) fall back to template order on resume | VERIFIED | `WorkoutSessionViewModel.kt:257-260` — explicit `else` branch: `Pair(updatedExercises, updatedExercises.indices.toMutableList())` |
| 8 | User can open the exercise overview sheet and see Completed, Current, and Up Next sections | VERIFIED | `ExerciseOverviewSheet.swift:17-63` — three explicit `Section("Completed")`, `Section("Current")`, `Section("Up Next")` blocks |
| 9 | User can drag-reorder pending exercises via drag handles in the Up Next section | VERIFIED | `ExerciseOverviewSheet.swift:58-62` — `.onMove` on the `ForEach` inside `Section("Up Next")` only |
| 10 | Completed and current exercises are NOT draggable | VERIFIED | `.onMove` is absent from Completed and Current sections; only `Section("Up Next")` ForEach has `.onMove` |
| 11 | User can tap a Skip button during the active workout to advance to the next exercise | VERIFIED | `WorkoutSessionView.swift:197-205` — toolbar leading button calls `viewModel.skipExercise()`; also in sheet at `ExerciseOverviewSheet.swift:32-42` |
| 12 | Skip button is hidden or disabled when on the last exercise | VERIFIED | `WorkoutSessionView.swift:198` and `ExerciseOverviewSheet.swift:32` — both guard `Int(active.currentExerciseIndex) + 1 < exercises.count` |
| 13 | After reorder, the exercise overview reflects the new order | VERIFIED | `WorkoutSessionView.swift:87-89` — `onMove` calls `viewModel.reorderExercise()` which mutates `_sessionState` immediately; sheet receives updated `active.exercises` |

**Score:** 13/13 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `shared/src/commonMain/kotlin/com/pumpernickel/data/db/ActiveSessionEntity.kt` | `exerciseOrder` column for crash recovery | VERIFIED | Line 16-17: `@ColumnInfo(defaultValue = "")` + `val exerciseOrder: String = ""` |
| `shared/src/commonMain/kotlin/com/pumpernickel/data/db/WorkoutSessionDao.kt` | `updateExerciseOrder` DAO query | VERIFIED | Line 32-33: `@Query("UPDATE active_sessions SET exerciseOrder = :order ...")` + `suspend fun updateExerciseOrder(order: String, updatedAt: Long)` |
| `shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt` | Schema version 4 with AutoMigration 3->4 | VERIFIED | Line 20 `version = 4`, Line 23-24: `AutoMigration(from = 3, to = 4)` present alongside 2->3 |
| `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/WorkoutRepository.kt` | `updateExerciseOrder` repo method and `exerciseOrder` in `ActiveSessionData` | VERIFIED | Interface line 28: `suspend fun updateExerciseOrder(order: String)`; `ActiveSessionData` line 50: `val exerciseOrder: String = ""`; impl line 151-156: delegates to DAO |
| `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt` | `reorderExercise()`, `skipExercise()`, `templateOriginalIndices` tracking | VERIFIED | Line 106: `templateOriginalIndices`; line 428: `reorderExercise`; line 456: `skipExercise`; line 592: `persistExerciseOrder` |
| `iosApp/iosApp/Views/Workout/ExerciseOverviewSheet.swift` | Sectioned exercise list with `.onMove` on pending section | VERIFIED | Three sections present; `.onMove` on line 58; `var onMove` and `var onSkip` callbacks on lines 8-9 |
| `iosApp/iosApp/Views/Workout/WorkoutSessionView.swift` | Skip button and reorder sheet wiring | VERIFIED | Lines 87-93: `onMove`/`onSkip` closures passed to sheet; lines 197-205: toolbar skip button |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| `WorkoutSessionViewModel.reorderExercise()` | `WorkoutRepository.updateExerciseOrder()` | `persistExerciseOrder()` called after list mutation, launches coroutine | WIRED | `WorkoutSessionViewModel.kt:448` calls `persistExerciseOrder()`; line 592-596: launches `workoutRepository.updateExerciseOrder(orderString)` |
| `WorkoutSessionViewModel.completeSet()` | `WorkoutRepository.saveCompletedSet()` | `templateOriginalIndices[exIdx]` as exerciseIndex | WIRED | `WorkoutSessionViewModel.kt:312-320`: guard + lookup + call to `saveCompletedSet(templateExIdx, ...)` |
| `WorkoutSessionViewModel.resumeWorkout()` | `ActiveSessionData.exerciseOrder` | CSV parse, reorder exercises after completed-set overlay | WIRED | `WorkoutSessionViewModel.kt:247-261`: reads `activeSession.exerciseOrder`, splits CSV, maps to `updatedExercises[idx]`, assigns `templateOriginalIndices` |
| `ExerciseOverviewSheet .onMove` | `WorkoutSessionViewModel.reorderExercise()` | `onMove` closure calls viewModel method with pending-relative indices | WIRED | `WorkoutSessionView.swift:87-89`: `onMove: { from, to in viewModel.reorderExercise(from: Int32(from), to: Int32(to)) }` |
| `WorkoutSessionView skip button` | `WorkoutSessionViewModel.skipExercise()` | Button action calling `viewModel.skipExercise()` | WIRED | `WorkoutSessionView.swift:200`: `viewModel.skipExercise()` in toolbar button; `ExerciseOverviewSheet.swift:34`: `onSkip()` triggers `viewModel.skipExercise()` via sheet callback |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `WorkoutSessionViewModel` — `reorderExercise` | `_sessionState.value` (exercises list) | Mutates in-memory list from `Active` state; persists to Room via `workoutRepository.updateExerciseOrder()` | Yes — both in-memory update and Room write occur | FLOWING |
| `WorkoutSessionViewModel` — `resumeWorkout` | `orderedExercises` / `templateOriginalIndices` | `activeSession.exerciseOrder` CSV parsed from Room; exercises reordered from template-built list | Yes — real Room data drives reorder | FLOWING |
| `WorkoutSessionViewModel` — `completeSet` | `templateExIdx` | `templateOriginalIndices[exIdx]` — populated on start/resume | Yes — template-original index tracked from start | FLOWING |
| `ExerciseOverviewSheet` | `exercises`, `currentExerciseIndex` | Props from `active.exercises` and `active.currentExerciseIndex` in `WorkoutSessionView` | Yes — `sessionState` observed from ViewModel Flow | FLOWING |

### Behavioral Spot-Checks

Step 7b: SKIPPED — no runnable entry points accessible without starting iOS simulator or Android device. The KMP module compiles (confirmed by commit record); runtime behavioral checks route to human verification.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| FLOW-03 | 08-01, 08-02 | User can reorder pending exercises mid-workout via drag gesture | SATISFIED | `reorderExercise()` implemented in ViewModel; `.onMove` in `ExerciseOverviewSheet`; pending-only constraint enforced |
| FLOW-04 | 08-01 | Exercise reorder preserves completed set data and crash recovery integrity | SATISFIED | `templateOriginalIndices` ensures correct Room index on `completeSet()`; `exerciseOrder` CSV persisted on every reorder; `resumeWorkout()` restores both |
| FLOW-07 | 08-01, 08-02 | User can skip current exercise and move to the next one | SATISFIED | `skipExercise()` in ViewModel; toolbar button + sheet skip button in SwiftUI; no-op guard on last exercise |

**Orphaned requirements check:** FLOW-03, FLOW-04, FLOW-07 are the only requirements mapped to Phase 8 in REQUIREMENTS.md. All three are claimed by plans and all three are satisfied. No orphaned requirements.

**Note on FLOW-06:** REQUIREMENTS.md defines FLOW-06 ("User can access context menu during workout with skip, reorder, and finish options") but it is NOT claimed by Phase 8 plans. Phase 8 implements the underlying skip/reorder mechanics; FLOW-06 context menu wrapping is deferred to Phase 9 (noted in 08-02-SUMMARY.md: "Ready for Phase 9 (context menu and abandon guards)"). This is not a gap for Phase 8.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| None | — | — | — | — |

No TODO, FIXME, placeholder, stub, or empty implementation patterns found in any of the five KMP files or two Swift files modified by this phase.

### Human Verification Required

#### 1. Drag Reorder End-to-End

**Test:** Start a workout with 3+ exercises. Complete one set of the first exercise. Open the exercise overview sheet. Drag an item in the "Up Next" section to a different position. Close the sheet. Complete another set.
**Expected:** The exercise overview reflects the new pending order. After force-quitting and relaunching the app, tapping "Resume Workout" shows the same exercise order with completed sets correctly overlaid.
**Why human:** Drag gesture behavior, visual reorder confirmation, and the crash-recovery round-trip require device/simulator execution with actual Room persistence.

#### 2. Skip Button Visibility and Behavior

**Test:** Start a workout. While on the first exercise (not last), verify the forward.fill toolbar button is visible. Tap it. Then navigate to the last exercise and verify the button is absent.
**Expected:** Button visible on non-last exercises; absent on last exercise; tapping advances to next exercise and resets to first incomplete set.
**Why human:** Conditional view rendering and state transitions require runtime UI observation.

#### 3. Skip via Exercise Overview Sheet

**Test:** Open the exercise overview sheet. Verify "Skip" text appears inline with the current exercise row. Tap it.
**Expected:** Sheet dismisses, cursor moves to next exercise. When on the last exercise, no "Skip" label is visible.
**Why human:** SwiftUI sheet content and conditional button visibility require runtime verification.

### Gaps Summary

No gaps found. All 13 observable truths are verified, all 7 artifacts pass all four levels of verification (exists, substantive, wired, data-flowing), and all 5 key links are confirmed wired in actual code. All three requirement IDs (FLOW-03, FLOW-04, FLOW-07) are satisfied with clear implementation evidence.

---

_Verified: 2026-03-29T19:15:00Z_
_Verifier: Claude (gsd-verifier)_
