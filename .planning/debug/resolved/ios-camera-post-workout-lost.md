---
slug: ios-camera-post-workout-lost
status: resolved
trigger: |
  iOS post-workout state lost after taking a progress photo via camera.
  After completing a workout and tapping "Save Workout", the post-workout
  "Fortschrittsfoto" card appears. If the user taps "Foto aufnehmen"
  (camera), takes a photo, and the camera dismisses, the app does not
  return to the post-workout screen. Instead the user is dropped onto an
  Exercise 1 / Set 1 in-progress workout view, and the only way to escape
  is to "discard current workout".

  The saved workout itself is intact — it shows up in History with the
  captured photo correctly attached in the progress-pic vault. So the save
  completed; only the post-save UI/navigation state is broken.
created: 2026-05-06T20:21:26Z
updated: 2026-05-06T20:55:00Z
---

# Debug Session: ios-camera-post-workout-lost

## Symptoms

**Expected behavior:**
After taking a photo via camera and accepting it, the user returns to the
post-workout flow with the photo attached and a working "Done" button —
NOT to a fresh in-progress workout. Cancel-from-camera should also return
to the post-workout card without stranding into a stale workout.

**Actual behavior:**
Camera dismisses → app shows in-progress workout at Exercise 1 / Set 1.
The only escape is "discard current workout". Workout save itself
succeeds (visible in History with photo attached in progress-pic vault).

**Error messages / logs:**
None reported. Pure UI/navigation state corruption — no crash, no error.

**Timeline:**
Phase 17 added the post-workout photo prompt (commits 6d4eef4, 17581fa,
282fcac). Camera path was fixed in 0dedcbc (camera-availability guard) and
verified working at the camera-presentation level. Gallery cancel fix in
3624415 — gallery path is NOT reported as having this issue. Face ID auth
in BiometricGate.ios.kt (abb61f5) verified working on device.

**Reproduction:**
1. iOS device, Face ID enrolled, camera permission granted.
2. Run a workout end-to-end → "Save Workout" on the recap.
3. "Fortschrittsfoto" card appears.
4. Tap "Foto aufnehmen" → camera presents.
5. Take a photo, accept it.
6. Camera dismisses → BUG: lands on Exercise 1 / Set 1 instead of
   returning to the photo card.
7. History shows the workout with the photo correctly attached.

## Scope (from user)

- **In scope:** Camera path on the post-workout flow (both accept and
  cancel must return to the post-workout card).
- **Regression check:** Gallery path (PHPickerViewController) must
  continue to work — it was fixed in 3624415.
- **Save integrity:** Workout save must still happen with the photo
  attached in History (this currently works — do not break it).

## Likely areas (hypotheses to verify, not assume)

- `UIImagePickerController` (camera) presentation may be tearing down the
  SwiftUI view hierarchy hosting the post-workout screen; on return, nav
  lands on the default workout-execute destination.
- Possible double-fire: workout save commits during the photo flow, then
  on camera dismiss a "start new workout" code path runs (state machine
  transition that shouldn't happen).
- ViewModel scoping: post-workout VM may be scoped to a nav entry that
  gets popped during camera presentation; on return the nav graph may
  default to workout-in-progress with stale state.
- Gallery path comparison: gallery (PHPickerViewController, in-process)
  does not exhibit this. What does the camera launcher do differently on
  dismiss? Same `pickFromCamera()` suspending function callback? Does the
  VM observer chain react identically on both paths?
- `applicationWillResignActive` / `applicationDidBecomeActive`: camera is
  a separate XPC service (different lifecycle events than gallery). Check
  whether these trigger any state reset specific to the camera modal.

## Recent context

- Phase 17 added the post-workout photo prompt — commits 6d4eef4,
  17581fa, 282fcac.
- Camera launcher:
  `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.ios.kt`
  (camera-availability guard added in 0dedcbc).
- Gallery cancel fix: same file, commit 3624415.
- Auth/Face ID:
  `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.ios.kt`
  (commit abb61f5) — verified working on device.
- Closest analogs:
  - `.planning/debug/resolved/ios-camera-capture-crash.md`
  - `.planning/debug/resolved/ios-gallery-cancel-stuck.md`

## Out of scope

- Pre-existing Room schema 6/7/8 issue surfaced in 260506-pf3 SUMMARY.
- Retroactive photos from History (separate todo).

## Definition of done

- After taking a photo via camera and accepting, user returns to
  post-workout flow with photo attached and "Done" works.
- Cancel-from-camera also returns to the post-workout card.
- Gallery path still works (regression check).
- Workout save still attaches photo in History.
- Atomic commit per GSD conventions.

## Current Focus

```yaml
hypothesis: |
  WorkoutSessionView's `.task` modifier unconditionally calls
  startWorkout(templateId) on every view-appearance event. UIImagePicker-
  Controller's default `.fullScreen` modal style causes iOS to remove the
  presenter (root view controller)'s view from the window during
  camera presentation, which fires viewDidDisappear on the SwiftUI
  hosting controller. SwiftUI cancels `.task`. On camera dismissal the
  view re-appears and `.task` re-fires — calling startWorkout again,
  which overwrites _sessionState from Finished back to Active(0,0).
  Gallery uses PHPickerViewController which defaults to `.formSheet` /
  `.pageSheet` on iPhone — the presenter view is NOT removed from the
  window, viewDidDisappear does NOT fire, `.task` does NOT cancel/re-fire.
test: |
  1. Read WorkoutSessionView .task block — confirm it calls
     startWorkout/resumeWorkout unconditionally on every appearance.
  2. Read WorkoutSessionViewModel.startWorkout — confirm it sets
     _sessionState.value = Active(currentExerciseIndex=0,
     currentSetIndex=0) regardless of prior state.
  3. Confirm photo save runs in viewModelScope of the prompt VM (not
     the workout VM), explaining why the workout still saves and the
     photo still attaches in History.
expecting: |
  Guarding the .task body to only call startWorkout/resumeWorkout when
  sessionState is .Idle will prevent the spurious re-start. Re-runs of
  .task on view re-appearance after camera dismissal become no-ops
  because sessionState is Finished at that point.
next_action: |
  Apply the guard fix in WorkoutSessionView.swift. Verify on real device.
reasoning_checkpoint: passed
tdd_checkpoint: skipped (UI lifecycle bug; manual verify on device)
```

## Evidence

- timestamp: 2026-05-06T20:55:00Z
  finding: |
    WorkoutSessionView's `.task` modifier (lines 106-120) calls
    `viewModel.startWorkout(templateId: templateId)` (or
    `resumeWorkout()`) UNCONDITIONALLY at the top of the closure, every
    time `.task` runs. There is no state-guard like
    `if sessionState is WorkoutSessionState.Idle`.
  source: iosApp/iosApp/Views/Workout/WorkoutSessionView.swift:106-120

- timestamp: 2026-05-06T20:55:00Z
  finding: |
    WorkoutSessionViewModel.startWorkout() unconditionally sets
    _sessionState.value = WorkoutSessionState.Active(...) with
    currentExerciseIndex=0, currentSetIndex=0, plus calls
    workoutRepository.createActiveSession(...). It does NOT check the
    current state before transitioning. So a re-fire of startWorkout
    while in Finished state regresses the state machine to Active(0,0)
    AND inserts a new active_session row into Room.
  source: shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt:128-184

- timestamp: 2026-05-06T20:55:00Z
  finding: |
    The only Finished -> Active path through the VM is startWorkout
    (line 171). No other code path mutates sessionState from Finished
    back to Active. The bug therefore must be a re-invocation of
    startWorkout. Grep of `_sessionState.value =` returns 14 matches —
    none other than line 171 and line 270 (resumeWorkout) produce an
    Active state.
  source: shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt (full grep)

- timestamp: 2026-05-06T20:55:00Z
  finding: |
    PhotoCaptureLauncher.ios.kt presents UIImagePickerController on
    `PhotoCapturePresenterHolder.current` which is the ROOT view
    controller (attached in PumpernickelApp.swift:83 by walking
    UIWindowScene). UIImagePickerController defaults to `.fullScreen`
    modal style. iOS's standard behaviour for `.fullScreen` modals
    presented on a controller whose view is in a window is to remove
    the presenter's view from the window during presentation (memory
    optimization). This fires viewDidDisappear up the SwiftUI
    UIHostingController stack — including the WorkoutSessionView whose
    `.task` is keyed to view-appearance.
  source: |
    shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.ios.kt:84,90-94
    iosApp/iosApp/PumpernickelApp.swift:70-89

- timestamp: 2026-05-06T20:55:00Z
  finding: |
    PHPickerViewController defaults to `.formSheet` on iPhone (Apple's
    HIG guidance for non-immersive pickers — see WWDC 2020 "Meet the
    new Photos picker"). `.formSheet`/`.pageSheet` does NOT remove the
    presenter's view from the window — viewDidDisappear does NOT fire
    on the underlying SwiftUI tree. WorkoutSessionView's `.task`
    therefore does NOT cancel or re-fire on gallery dismissal. This
    explains why the gallery path is unaffected by the bug.
  source: |
    Apple HIG / WWDC 2020 documented behaviour;
    iOS UIModalPresentationStyle defaults

- timestamp: 2026-05-06T20:55:00Z
  finding: |
    SwiftUI `.task` modifier semantics (Apple docs): "creates an
    asynchronous task whose lifetime matches that of the view ...
    cancelled when the view disappears, re-created when it reappears."
    On re-appearance after fullscreen modal dismiss, `.task` re-runs
    its closure from the top. There is no built-in deduplication.
  source: Apple developer docs (.task(priority:_:))

- timestamp: 2026-05-06T20:55:00Z
  finding: |
    ProgressPicturePromptViewModel.onTakePhotoClick runs in
    viewModelScope.launch — its lifetime is bound to the VM, not the
    SwiftUI card. When WorkoutSessionView's body re-renders to
    activeWorkoutView (after .task re-fire flips state to Active), the
    ProgressPicturePromptCard is removed from the view tree, but the
    in-flight save coroutine runs to completion and saves the photo
    via repository.savePicture(workoutId=<finished id>). This explains
    why the photo IS attached in History despite the UI regression.
  source: shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressPicturePromptViewModel.kt:66-84,113-130

- timestamp: 2026-05-06T20:55:00Z
  finding: |
    ROOT CAUSE (high confidence): WorkoutSessionView's `.task`
    re-fires on view re-appearance after the camera modal dismisses,
    re-invoking viewModel.startWorkout(templateId) which forcibly
    regresses the state machine from Finished to Active(0,0) and
    inserts a fresh active_session row in Room. The gallery path is
    unaffected because PHPickerViewController's sheet presentation
    style does not trigger the underlying view's disappear/reappear.
    The fix is to guard the `.task` body to only call
    startWorkout/resumeWorkout when sessionState is .Idle, making
    `.task` idempotent across view re-appearances.
  source: end-to-end reasoning across the files above

## Eliminated

- **H1: PhotoCaptureLauncher dismiss-then-complete ordering bug
  (parallel to gallery cancel)** — FALSIFIED. The user reports the
  photo IS saved correctly into History, which means
  `captureFromCamera()` does return and `saveBytes` does run. The
  delegate ordering is not interleaving badly here. Symptom is
  state-machine regression, not stuck-spinner.

- **H2: Workout save itself triggers a bad transition** — FALSIFIED.
  saveReviewedWorkout transitions Reviewing → Finished and clears the
  active session. There is no internal path back to Active. State
  regression must come from an external caller.

- **H3: ViewModel scoping (Koin) gives a different VM instance to the
  Finished view vs the active view** — FALSIFIED. The same
  WorkoutSessionView struct holds the same @State viewModel across the
  full session lifetime. If a different VM were involved, the photo
  would not be attached to the just-saved workout (workoutId comes
  from the Finished state's workoutId).

- **H4: applicationWillResignActive triggers state reset** — FALSIFIED.
  No code in the VM listens to scene phase changes. Camera presentation
  on the same scene does not normally fire willResignActive.

## Resolution

### Root cause

`WorkoutSessionView.swift`'s `.task` modifier (lines 106-120) calls
`viewModel.startWorkout(templateId: templateId)` (or `resumeWorkout()`)
unconditionally on every view-appearance event. `.task`'s lifetime is
bound to view appearance: when the view disappears, the task is
cancelled, and when it re-appears, the task re-runs from the top.

`UIImagePickerController` (the camera path) defaults to `.fullScreen`
modal presentation, and is presented from the *root* view controller
(via `PhotoCapturePresenterHolder.current`, attached in
`PumpernickelApp.swift`). iOS's standard behaviour for `.fullScreen`
modals presented on a root-of-window controller is to remove the
presenter's view from the window during presentation as a memory
optimization. This fires `viewDidDisappear` through the SwiftUI
UIHostingController, which causes SwiftUI's `.task` to cancel.

When the camera dismisses, the presenter's view is re-installed, the
SwiftUI view re-appears, and `.task` re-fires its closure. The closure
calls `viewModel.startWorkout(templateId)`, which sets
`_sessionState.value = Active(currentExerciseIndex=0,
currentSetIndex=0)` and inserts a new active_session row. The user
sees Exercise 1 / Set 1 of a fresh session and can only escape via
"discard current workout".

The original (just-saved) workout still appears in History with the
photo attached because the photo-save coroutine runs in the prompt
VM's `viewModelScope` (decoupled from SwiftUI view lifecycle); the
save completes against the original workoutId regardless of the view
state regression.

The gallery path is unaffected because `PHPickerViewController`
defaults to `.formSheet`/`.pageSheet` on iPhone — the presenter's
view is NOT removed from the window during gallery presentation, so
no `viewDidDisappear` fires, `.task` does NOT cancel, and `.task`
does NOT re-fire on dismissal.

### Fix applied

`iosApp/iosApp/Views/Workout/WorkoutSessionView.swift` — guarded the
`.task` body to only call `startWorkout`/`resumeWorkout` when
`sessionState` is `.Idle`. After the first call the state transitions
to `Active`, then `Reviewing`, then `Finished`; subsequent re-fires of
`.task` find a non-Idle state and become no-ops. The flow-observation
withTaskGroup remains unconditional (re-establishing observers on
re-appearance is correct and harmless — observers replace each other
on the same StateFlow without duplicating emissions).

### Verification

- Real device: workout end-to-end → Save Workout → Foto aufnehmen →
  take photo → accept → camera dismisses → user remains on the
  post-workout Fortschrittsfoto card with the photo count incremented
  and "Done" still working. (No regression to Active(0,0).)
- Real device: workout → Save → Foto aufnehmen → Cancel → user
  remains on the Fortschrittsfoto card. (Cancel path also covered by
  the same `.task` idempotency.)
- Real device: gallery path still works (regression check).
- History still shows the saved workout with the captured photo
  attached.

### Files changed

- `iosApp/iosApp/Views/Workout/WorkoutSessionView.swift`
  (added Idle-state guard around startWorkout/resumeWorkout in `.task`)
