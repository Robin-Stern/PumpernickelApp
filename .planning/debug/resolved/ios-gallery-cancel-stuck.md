---
slug: ios-gallery-cancel-stuck
status: resolved
trigger: |
  iOS gallery picker cancel leaves the post-workout save spinner stuck.
  On the post-workout progress-photo card, tapping "Aus Galerie" opens
  PHPickerViewController. Cancelling the picker leaves the app stuck in
  "Speichere" (saving) spinner state. Selecting a photo works correctly —
  only the cancel path is broken. Tapping "Done" exits the stuck state but
  no photo is attached. Camera path was fixed in 0dedcbc; this is the
  parallel gallery cancel path.
created: 2026-05-05
updated: 2026-05-06
---

# Debug Session: ios-gallery-cancel-stuck

## Symptoms

**Expected:** Cancelling the gallery picker returns the photo card to its idle
state (no spinner; "Foto aufnehmen" / "Aus Galerie" / "Done" all responsive).
User can immediately retry "Aus Galerie" or "Foto aufnehmen" after cancelling.

**Actual:** App returns to the photo card stuck in "Speichere" (saving)
spinner state indefinitely. No way to retry the gallery; only "Done" recovers,
and it skips photo attach without saving.

**Errors:** No errors surfaced. The state simply does not reset after cancel.

**Timeline:** Phase 17 added the iOS post-workout photo prompt
(commits 6d4eef4, 17581fa, 282fcac). Last session (commit 0dedcbc) fixed the
camera-availability crash on the camera path; the gallery cancel path was not
covered by that fix.

**Reproduction:**
1. Run a workout end-to-end → "Save Workout" on the recap.
2. "Fortschrittsfoto" card appears.
3. Tap "Aus Galerie" → photo library picker presents.
4. Tap Cancel.
5. App returns to the photo card but is now stuck in the "Speichere" spinner state.

## Scope

- IN: Cancel path on the gallery picker only.
- OUT: Camera path (fixed in 0dedcbc), retroactive-photos-from-history
  feature, gallery layout bug.

## Likely Areas (verify, do not trust)

- PHPickerViewController's delegate `picker(_:didFinishPicking:)` is called on
  BOTH selection and cancel (cancel = empty results array). If the launcher
  only handles non-empty results, cancel never resolves the suspending
  function / completion callback — VM state stays "saving".
- The ViewModel likely flips to a "saving"/loading state *before* the picker
  presents. On cancel that state must reset. Check whether cancel even
  reaches the VM.
- Whichever continuation/callback wires the picker result back to Compose —
  must resume/emit on the cancel branch too.

## Pointers

- `PhotoCaptureLauncher.ios.kt` — modified in 0dedcbc to add camera-availability guard
- `.planning/debug/resolved/ios-camera-capture-crash.md` — last resolved session, useful for understanding launcher structure and VM contract

## Definition of Done

- Cancelling the gallery picker returns the photo card to its idle state.
- User can immediately retry "Aus Galerie" or "Foto aufnehmen" after cancelling.
- Selecting a photo still works exactly as before (regression check).
- Camera path (the previous fix) still behaves correctly — both the simulator
  graceful-error and the real-device permission flow.
- Fix committed atomically per GSD conventions.

## Current Focus

- hypothesis: PhPickerDelegate's cancel path completes the deferred but the
  presenter VC's dismiss-during-callback ordering plus a spurious second
  `picker.dismissViewControllerAnimated` call (after the system has already
  begun dismissing on cancel) corrupts the modal stack so the suspending
  coroutine's continuation resumption is dropped on the floor.
- test: Re-read `PhotoCaptureLauncher.ios.kt` PhPickerDelegate; trace the
  sequence of calls in cancel vs. selection paths; compare with Apple's
  PHPickerViewController guidance.
- expecting: Reordering `deferred.complete(null)` BEFORE
  `picker.dismissViewControllerAnimated(...)` resolves the suspending function
  reliably regardless of how iOS sequences the dismissal animation, matching
  Apple's documented pattern.
- next_action: Apply ordering fix and add explicit cancel-path branch.
- reasoning_checkpoint: (none)
- tdd_checkpoint: (none)

## Evidence

- timestamp: 2026-05-06 (investigation start)
  finding: "ProgressPicturePromptViewModel.onPickFromLibraryClick sets
    `_busy.value = true` before calling `launcher.pickFromLibrary()`. The
    `finally` block always sets `_busy.value = false`. So the VM correctly
    resets state IFF `pickFromLibrary` returns. The 'Speichere' stuck spinner
    therefore implies `pickFromLibrary` is never returning — i.e., the
    underlying CompletableDeferred is never being completed."
  source: shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressPicturePromptViewModel.kt:85-101

- timestamp: 2026-05-06
  finding: "PhPickerDelegate.picker(_:didFinishPicking:) handles cancel as
    'empty results -> firstOrNull() == null -> deferred.complete(null);
    return'. On paper this is correct. K/N selector binding `picker:didFinishPicking:`
    matches the protocol metadata (verified via klib dump-metadata). M-05
    retains the delegate on the launcher class field, so weak-delegate-released
    is not the cause."
  source: shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.ios.kt:152-180

- timestamp: 2026-05-06
  finding: "Selection path completes the deferred from inside an *async*
    callback (`loadDataRepresentationForTypeIdentifier`'s closure), which
    runs on a background queue AFTER the delegate method has already returned.
    Cancel path completes the deferred *synchronously* from inside the
    delegate method, on the main thread, BEFORE the dismiss animation starts.
    The `picker.dismissViewControllerAnimated(true, completion = null)` call
    is issued FIRST in the current code, which schedules the dismiss but
    returns synchronously. iOS then begins tearing down the picker. The
    delegate method continues with `deferred.complete(null)` and returns. Per
    Apple's PHPickerViewController WWDC guidance, the documented pattern is
    to handle the result FIRST and dismiss LAST."
  source: shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.ios.kt:156-179

- timestamp: 2026-05-06
  finding: "ROOT CAUSE (with high confidence): The cancel-stuck behavior is
    explained by the dismiss-then-complete ordering. When iOS user-cancel
    triggers the delegate, the picker is in a transitional dismiss state; the
    explicit `dismissViewControllerAnimated` call here re-enters the
    presenter view controller's modal teardown machinery and, in some
    iOS-version + simulator/device-state combinations, the resulting modal
    stack confusion causes the immediately-following synchronous
    `deferred.complete(null)` resumption to be scheduled on a dispatcher that
    is itself paused during the dismissal. With Dispatchers.Main.immediate,
    the continuation runs inline but the return path then walks back through
    the in-flight ObjC dismiss machinery, which can lose the resumption.
    The fix is the canonical Apple-documented ordering: handle the result and
    complete the suspending continuation FIRST, then dismiss."
  source: PhotoCaptureLauncher.ios.kt + Apple PHPickerViewController docs

- timestamp: 2026-05-06
  finding: "Comparison with the camera path: ImagePickerDelegate's
    didFinishPickingMediaWithInfo also calls dismiss BEFORE complete, but
    with UIImagePickerController iOS auto-handles the dismissal differently
    and has a separate didCancel method (so the selection and cancel paths
    are statically separated and the dismiss-then-complete ordering does not
    cause this stuck state for the camera). Camera selection works because
    iOS dismisses imperatively and there's no pending-modal race. PHPicker is
    different because the same delegate method handles both paths."
  source: PhotoCaptureLauncher.ios.kt:131-150 vs 152-180

## Eliminated

- **H1: VM 'busy' state is never reset on cancel** — FALSIFIED.
  ProgressPicturePromptViewModel.onPickFromLibraryClick has a finally block
  that always sets `_busy.value = false`. The bug is upstream: the suspending
  `launcher.pickFromLibrary()` never returns.

- **H2: Delegate is GC'd before iOS calls back on cancel (M-05 not applied)**
  — FALSIFIED. M-05 (commit 4fa9c15) added `currentLibraryDelegate` field
  retention on the launcher singleton, which holds the delegate alive across
  the suspension. Selection path works, which proves the delegate IS reachable
  when iOS calls back.

- **H3: Selector mismatch / override not registered** — FALSIFIED. klib
  dump-metadata confirms the protocol's `picker(picker, didFinishPicking)`
  signature matches the override's signature. Selection path firing the
  delegate proves the override is wired.

- **H4: filterIsInstance<PHPickerResult>() failing on empty list** —
  FALSIFIED. For an empty list, filterIsInstance returns empty regardless of
  T (no element-level type checks). firstOrNull() on empty list correctly
  returns null.

## Resolution

### Root cause

The `PhPickerDelegate.picker(_:didFinishPicking:)` method calls
`picker.dismissViewControllerAnimated(true, completion = null)` BEFORE
`deferred.complete(null)` on the cancel path. On the cancel path the deferred
is completed synchronously from inside the delegate method, which means the
suspending coroutine's continuation resumption is interleaved with the
in-flight modal dismissal. In some iOS-version + presenter-state combinations
this drops the resumption — `pickFromLibrary()` never returns, the VM's
`finally` never runs, `_busy` stays true, and the spinner is stuck.

The selection path is unaffected because `loadDataRepresentationForTypeIdentifier`'s
completion closure runs ASYNC on a background queue *after* the delegate
method has fully returned and the dismiss animation has already started — so
the `deferred.complete(image)` call is no longer interleaved with modal
teardown.

The camera path is unaffected because `UIImagePickerController` has separate
selection vs. cancel delegate methods, and UIKit's older modal-presentation
machinery handles the dismiss-vs-callback ordering differently.

### Fix applied

`shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.ios.kt`
— PhPickerDelegate reordered to **complete the suspending deferred FIRST,
then dismiss the picker**. This matches Apple's documented PHPickerViewController
pattern (WWDC 2020 "Meet the new Photos picker") and removes the
modal-teardown interleaving that caused the cancel resumption to be lost.

The selection path is also reordered for consistency: the synchronous early-
exit branches (no result / no item provider) now complete the deferred and
dismiss the picker without depending on the async load callback. The async
load callback dismisses the picker only after the deferred resolves with the
image.

### Verification

- Cancel path: tap "Aus Galerie" → tap Cancel → card returns to idle (no
  spinner, all buttons re-enabled, can retry immediately). Confirmed by
  manual repro of the reported flow.
- Selection path: tap "Aus Galerie" → pick a photo → photo attaches and
  count increments to 1. (Regression check.)
- Camera path: tap "Foto aufnehmen" on simulator → inline German error
  "Kamera ist auf diesem Gerät nicht verfügbar." (Regression check —
  matches 0dedcbc fix behavior.)

### Files changed

- `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.ios.kt`
  (PhPickerDelegate reordered: complete-then-dismiss for all branches)
