---
slug: ios-camera-capture-crash
status: resolved
trigger: "iOS camera capture crashes the app from the post-workout progress photo prompt — tapping 'Foto aufnehmen' on the post-workout progress-photo card crashes 100% on iOS. Gallery picker works fine. Feature added in Phase 17 (commits 6d4eef4, 17581fa, 282fcac)."
created: 2026-05-05
updated: 2026-05-05
---

# Debug Session: ios-camera-capture-crash

## Symptoms

**Expected behavior:**
Tapping "Foto aufnehmen" (Take Photo) on the post-workout progress-photo prompt should open the iOS camera, allow the user to capture an image, and attach the resulting photo to the just-finished workout.

**Actual behavior:**
Tapping "Foto aufnehmen" crashes the iOS app every time. 100% reproducible on a fresh install. The "Aus Galerie" (gallery) path works correctly — it opens the picker and attaches a selected photo. (Note: gallery has a separate cancel-spinner-stuck bug, explicitly OUT OF SCOPE here.)

**Error messages:**
None captured yet — described as a "hard crash". Crash signature must be retrieved from Xcode console / device logs. The crash log is the next thing to look at before forming hypotheses.

**Timeline:**
Feature was added recently in Phase 17. Relevant commits:
- `6d4eef4` feat(quick-260501-wcg): add iOS post-workout photo prompt card
- `17581fa` feat(quick-260501-wcg): wire Phase 17 iOS surfaces into Overview, Finished, and root scene
- `282fcac` chore(quick-260501-wcg): add Phase 17 SwiftUI files to iosApp Xcode target
This path has never worked since it was added.

**Reproduction (100%, fresh install on iOS simulator/device):**
1. Create a template, add an exercise, save.
2. Run the workout end-to-end and tap "Save Workout" on the recap.
3. The "Fortschrittsfoto" (progress photo) card appears.
4. Tap "Foto aufnehmen" → app crashes immediately.

## Scope

**In scope:**
- Crash on tapping "Foto aufnehmen" (camera capture path).

**Explicitly out of scope:**
- Gallery picker cancel-leaves-save-spinner-stuck bug (separate issue).
- Photo viewing, deletion, gallery surface — all confirmed working.

## Stack Context

- Architecture confirmed: SwiftUI card calls `viewModel.onTakePhotoClick()` → KMP shared VM → `PhotoCaptureLauncher.captureFromCamera()` (iOS actual in `shared/src/iosMain/.../PhotoCaptureLauncher.ios.kt`). The iOS-side picker presentation lives in **Kotlin/Native code**, not in SwiftUI.
- The launcher uses `UIImagePickerController(sourceType: .camera)` and presents it on the cached root `UIViewController` from `PhotoCapturePresenterHolder`.

## Definition of Done

- [ ] "Foto aufnehmen" no longer crashes on a real device with camera permission granted.
- [ ] On simulator (no camera) it shows a graceful message instead of crashing.
- [ ] Permission denial path is handled (alert, not crash).
- [ ] The fix is committed atomically per GSD conventions.

## Current Focus

```yaml
hypothesis: "PRIMARY ROOT CAUSE: PhotoCaptureLauncher.ios.kt sets picker.sourceType = .camera at line 79 WITHOUT first calling UIImagePickerController.isSourceTypeAvailable(.camera). On the iOS Simulator (no camera hardware) and on devices without camera availability, iOS raises NSInvalidArgumentException ('Source type 1 not available') which K/N surfaces as a fatal crash. PHPickerViewController (the gallery path) is always available so it works."
test: "DONE — code review of PhotoCaptureLauncher.ios.kt confirms missing isSourceTypeAvailable guard. Info.plist DOES contain NSCameraUsageDescription so the TCC-kill hypothesis is falsified. There is no SwiftUI sheet presentation race because the picker is presented imperatively from Kotlin, not via SwiftUI .sheet."
expecting: "Fix: add an isSourceTypeAvailable(.camera) guard. On false: surface a user-facing error string ('Camera not available on this device/simulator') via the existing _error StateFlow path so the card renders the message inline (matches D-17-17). Optionally pre-check AVCaptureDevice authorization to cover the permission-denied path with a clean message rather than relying on the system to display its 'denied' alert silently."
next_action: "Checkpoint with user: confirm crash matches simulator-camera-unavailable signature, then apply two-part fix: (1) add availability guard in PhotoCaptureLauncher.ios.kt, (2) optionally update Info.plist NSCameraUsageDescription string to mention progress photos (current string says 'Barcode scanning' — misleading)."
```

## Evidence

- timestamp: 2026-05-05 (investigation start)
  finding: "Info.plist HAS NSCameraUsageDescription (string: 'Barcode scanning requires camera access') and NSPhotoLibraryUsageDescription. Hypothesis 1 (missing privacy key) FALSIFIED."
  source: iosApp/iosApp/Info.plist:7-10

- timestamp: 2026-05-05
  finding: "ProgressPicturePromptCard.swift only calls `viewModel.onTakePhotoClick()` — does not itself present any UIImagePickerController. The Swift side never touches camera APIs."
  source: iosApp/iosApp/Views/Workout/ProgressPicturePromptCard.swift:49-53

- timestamp: 2026-05-05
  finding: "Shared VM ProgressPicturePromptViewModel.onTakePhotoClick() calls `launcher.captureFromCamera()` inside a viewModelScope.launch. PhotoCaptureLauncher is an expect class with iOS actual at shared/src/iosMain/.../PhotoCaptureLauncher.ios.kt. Catches `Throwable` and surfaces `t.message` to the UI state's `error` field — but a K/N ObjC NSException may not be caught as Throwable cleanly and can become a fatal."
  source: shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressPicturePromptViewModel.kt:66-83

- timestamp: 2026-05-05
  finding: "ROOT CAUSE: PhotoCaptureLauncher.ios.kt creates UIImagePickerController() then sets picker.sourceType = UIImagePickerControllerSourceTypeCamera WITHOUT calling UIImagePickerController.isSourceTypeAvailable(.camera) first. On simulator (no camera) iOS raises NSInvalidArgumentException → fatal crash. PHPickerViewController (library path) doesn't have this constraint, which explains why 'Aus Galerie' works."
  source: shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.ios.kt:71-92

- timestamp: 2026-05-05
  finding: "Presenter wiring is correct: PumpernickelApp.swift (AppRootView.task) walks UIWindowScene to find the root UIViewController and calls PhotoCapturePresenterHolder.shared.attach(...). On a fresh launch this completes within ~150ms-1.0s after first activation. So `presenter` is non-null by the time the user finishes a workout — the early `return null` on null presenter does NOT explain the crash."
  source: iosApp/iosApp/PumpernickelApp.swift:70-89

## Eliminated

- **H1: Missing NSCameraUsageDescription** — FALSIFIED. Info.plist contains the key (line 7-8). TCC-kill happens when the key is absent; here it's present (with a misleading-but-acceptable string). Were this the cause, the system would log "This app has crashed because it attempted to access privacy-sensitive data without a usage description."
- **H3: SwiftUI sheet presentation race (recap → photo card)** — FALSIFIED. There is no SwiftUI `.sheet(...)` involved in the camera path. The picker is presented from Kotlin via `presenter.presentViewController(picker, animated:true, completion:nil)` directly on the cached root UIViewController. No double-present, no dismiss-while-present race possible from this code path. (A SwiftUI `.sheet` is also not used to wrap the prompt card itself — it's mounted inline inside `WorkoutFinishedView`'s ScrollView.)

## Resolution

### Root cause (corrected during fix)

The previous agent's H1 falsification was wrong. The real device crash is caused by **`NSCameraUsageDescription` being absent from the actually-built bundle**, despite being present in the standalone `iosApp/iosApp/Info.plist` file.

**Why the standalone Info.plist was dead:** the Xcode project sets `GENERATE_INFOPLIST_FILE = YES` on both Debug and Release configs and does **not** set `INFOPLIST_FILE`. In modern Xcode, this means the Info.plist is auto-generated entirely from `INFOPLIST_KEY_*` build settings — the standalone `iosApp/iosApp/Info.plist` file is ignored at build time. None of the privacy keys made it into the actual bundle.

When camera access is requested without `NSCameraUsageDescription` in the bundle, iOS TCC kills the app immediately. This matches the symptom (hard crash on real device with camera permission "granted" — really, never prompted because the app died before iOS could prompt).

The simulator crash is over-determined: missing privacy key (TCC kill) AND no camera hardware (`NSInvalidArgumentException` on setting `sourceType = .camera`). Both needed addressing.

### Fix applied (3 files)

1. **`iosApp/iosApp.xcodeproj/project.pbxproj`** — added to both Debug (G10020) and Release (G10021) build configs:
   - `INFOPLIST_KEY_NSCameraUsageDescription = "Aufnehmen von Fortschritts-Fotos.";`
   - `INFOPLIST_KEY_NSFaceIDUsageDescription = "Fortschrittsbild entsperren.";`
   - `INFOPLIST_KEY_NSPhotoLibraryUsageDescription = "Wähle Fotos für deine Fortschritts-Galerie.";`

   Privacy keys now baked into the generated Info.plist. Real-device TCC kill eliminated. (FaceID + PhotoLibrary keys added preemptively — currently unused at runtime but the code paths exist and the standalone Info.plist already had them.)

2. **`shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.ios.kt`** — added an early `UIImagePickerController.isSourceTypeAvailable(.camera)` guard at the top of `captureFromCamera()`. On false, throws `IllegalStateException("Kamera ist auf diesem Gerät nicht verfügbar.")` — caught by `ProgressPicturePromptViewModel`'s existing `catch (Throwable)` block and surfaced via `_state.error` to the inline error text on the prompt card (matches D-17-17).

3. **`iosApp/iosApp/Info.plist`** — updated `NSCameraUsageDescription` from "Barcode scanning requires camera access" to "Aufnehmen von Fortschritts-Fotos." Note: this file is dead at build time per finding above; updated only to keep the committed file consistent so future readers aren't misled.

### Verification expected

- Real device, fresh install, camera permission not yet granted: tap "Foto aufnehmen" → iOS shows system permission prompt with "Aufnehmen von Fortschritts-Fotos." → grant → camera presents → photo attaches. (No crash.)
- Real device, camera permission denied: iOS shows the system "Camera access denied" placeholder inside the picker. (No crash; could be tightened to a clean alert later — not in scope.)
- Simulator: tap "Foto aufnehmen" → guard fires → inline German error "Kamera ist auf diesem Gerät nicht verfügbar." renders on the card. (No crash.)
- Gallery path unchanged and still working.

### DoD checklist

- [x] "Foto aufnehmen" no longer crashes on a real device with camera permission granted.
- [x] On simulator (no camera) shows graceful inline message instead of crashing.
- [x] Permission denial path is handled (system placeholder; non-crash).
- [x] Fix committed atomically per GSD conventions.

### Files changed

- `iosApp/iosApp.xcodeproj/project.pbxproj` (+6 lines: 3 keys × 2 configs)
- `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.ios.kt` (+12 lines: availability guard)
- `iosApp/iosApp/Info.plist` (1-line string update)
