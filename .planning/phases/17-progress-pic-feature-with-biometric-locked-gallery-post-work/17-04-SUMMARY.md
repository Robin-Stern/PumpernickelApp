---
phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work
plan: 04
subsystem: ios-platform-actuals
tags: [ios, kotlin-native, expect-actual, file-protection, icloud-backup-exclusion, lacontext, biometric, phpicker, uiimagepicker, info-plist, threat-mitigation]

# Dependency graph
requires:
  - phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work
    plan: 02
    provides: "expect class PhotoVault / PhotoCaptureLauncher / BiometricGate + UnlockResult sealed hierarchy in commonMain"
provides:
  - "actual class PhotoVault for iOS — JPEG file I/O under <Documents>/progress_pics/{id}.jpg with NSDataWritingFileProtectionComplete + NSURLIsExcludedFromBackupKey"
  - "actual class BiometricGate for iOS — LAContext with LAPolicyDeviceOwnerAuthentication, no-credential short-circuit, German reason copy"
  - "actual class PhotoCaptureLauncher for iOS — UIImagePickerController (camera) + PHPickerViewController (library), 1600px / quality 0.8 JPEG resize"
  - "PhotoCapturePresenterHolder object — root UIViewController registry the SwiftUI side attaches to (consumed by 17-08 IOS-HANDOFF)"
  - "Info.plist German usage descriptions (NSPhotoLibraryUsageDescription + NSFaceIDUsageDescription)"
  - "T-PHOTO-EXFIL mitigation landed (NSDataWritingFileProtectionComplete on every write)"
  - "T-CLOUD-LEAK mitigation landed (NSURLIsExcludedFromBackupKey = true on every saved file)"
  - "T-FILE-SHARING mitigation confirmed (UIFileSharingEnabled and LSSupportsOpeningDocumentsInPlace remain absent)"
affects:
  - "17-05-PLAN (capture flow VM): iOS path of savePicture() now has a working PhotoCaptureLauncher + PhotoVault to call into"
  - "17-06-PLAN (gallery VM): iOS path of observeGalleryTiles() can now resolve cover-photo bytes via PhotoVault.read"
  - "17-07-PLAN (viewer VM): iOS path of biometric unlock now has LAContext-backed BiometricGate.requestUnlock"
  - "17-08-PLAN (Koin DI + iOS handoff doc): single<PhotoVault> { PhotoVault() }, single<PhotoCaptureLauncher> { PhotoCaptureLauncher() }, single<BiometricGate> { BiometricGate() } now all resolvable on iOS — and the handoff doc must document PhotoCapturePresenterHolder.attach/detach for SwiftUI integration"

# Tech tracking
tech-stack:
  added: []  # purely Kotlin/Native bindings to existing iOS frameworks; no new deps
  patterns:
    - "Kotlin/Native ObjC delegate via NSObject() subclass implementing two protocols (UIImagePickerControllerDelegateProtocol + UINavigationControllerDelegateProtocol — UIImagePickerController retains its delegate as id<both>)"
    - "Callback -> suspend bridge via CompletableDeferred (chosen over suspendCancellableCoroutine because we need the deferred reachable from both delegate methods AND the awaiter must outlive the picker dismissal — CompletableDeferred has cleaner semantics for the two-method delegate protocols)"
    - "K/N CEnum constants accessed via the enum class qualifier — UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera (the bare constant is not in scope at the package level)"
    - "K/N ObjC class-as-protocol-conformant cast — UIImage.Companion adopts NSItemProviderReading at the ObjC class-method level; cast 'UIImage as NSItemProviderReadingProtocol' is the K/N idiom for loadObjectOfClass / canLoadObjectOfClass"
    - "ByteArray <-> NSData via usePinned + memcpy (forward direction uses NSData.create(bytes:length:) under @BetaInteropApi opt-in)"
    - "NSError-by-pointer K/N pattern: memScoped { val errVar = alloc<ObjCObjectVar<NSError?>>(); ...(error = errVar.ptr); errVar.value }"
    - "Singleton volatile holder for cross-stack root UIViewController plumbing — @kotlin.concurrent.Volatile (NOT @kotlin.jvm.Volatile which is JVM-only and deprecated for KMP)"

key-files:
  created:
    - "shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.ios.kt"
    - "shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.ios.kt"
    - "shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.ios.kt"
  modified:
    - "iosApp/iosApp/Info.plist"

key-decisions:
  - "Info.plist German strings settled at 'Wähle Fotos für deine Fortschritts-Galerie.' (photo library) and 'Fortschrittsbild entsperren.' (Face ID) — short enough to fit the iOS system sheet, declarative not imperative, matches Pumpernickel's existing copy register"
  - "PhotoCaptureLauncher uses CompletableDeferred (not suspendCancellableCoroutine) for the picker -> suspend bridge. UIImagePickerController dispatches finish/cancel through two separate delegate methods, both of which need to fulfill the same single suspending await. CompletableDeferred lets either method complete the deferred without coordinating a single continuation."
  - "PhotoCapturePresenterHolder is an `object` with @Volatile var current: UIViewController? rather than a class injected via Koin. Reason: the SwiftUI side attaches the root controller during scene lifecycle (very iOS-ish), and we want the iOS handoff doc to describe a single global registration point — same shape as the Android *ActivityHolder pattern from 17-03 will need."
  - "BiometricGate's no-credential short-circuit only triggers on LAErrorPasscodeNotSet — LAErrorBiometryNotAvailable / LAErrorBiometryNotEnrolled fall through to evaluatePolicy because LAPolicyDeviceOwnerAuthentication still allows passcode fallback in those cases. Two cases: passcode-set device with no biometric (we still prompt for passcode), no-passcode device (we unblur freely per D-17-16)."
  - "loadObjectOfClass / canLoadObjectOfClass cast: UIImage's metaclass adopts NSItemProviderReading via ObjC class-method category, but K/N's binding emits the parameter type as NSItemProviderReadingProtocol (the *instance* protocol). The cast `UIImage as NSItemProviderReadingProtocol` is the canonical K/N escape hatch — at runtime the ObjC dispatch reads the class methods regardless of the static Kotlin type."
  - "K/N ObjC delegate object lifetime: UIImagePickerController stores its delegate as a weak reference. The local `delegate` val keeps it alive until the await completes; the @Suppress(\"UNUSED_EXPRESSION\") delegate marker after await prevents Kotlin from dead-code-eliminating the val before the deferred resolves."

patterns-established:
  - "iOS expect/actual file-I/O pattern with file-protection + backup-exclusion at every write — sets template for any future iOS sandbox-stored sensitive blob (videos, audio notes, exported reports)"
  - "iOS expect/actual UI-presenting service via global @Volatile UIViewController holder + SwiftUI attach/detach in scene lifecycle — sets template for any future iOS picker-based capture (document picker, contact picker, etc.)"
  - "K/N binding-shape adaptation pattern: when the plan-text source code names a constant or method that doesn't compile, fall back to klib dump-metadata to find the actual binding name and adapt — the plan acknowledged this would happen for 'loadObjectOfClass' and 'PHPickerFilter.filter'"

requirements-completed:
  - D-17-05  # iOS path layout <Documents>/progress_pics/{id}.jpg
  - D-17-06  # iOS hardening: NSFileProtectionComplete + isExcludedFromBackupKey + Info.plist file-sharing absences
  - D-17-07  # 1600px long edge, JPEG quality 0.8 (iOS path)
  - D-17-15  # iOS LAContext deviceOwnerAuthentication, German reason copy
  - D-17-16  # iOS no-credential device short-circuit returns Success
  - D-17-17  # iOS auth cancel -> Cancelled, fail -> Failed; no error toast
  - D-17-19  # expect/actual platform code wraps all photo I/O — iOS side complete

# Metrics
duration: 9min3sec
completed: 2026-05-01
---

# Phase 17 Plan 04: iOS actuals — PhotoVault.ios / BiometricGate.ios / PhotoCaptureLauncher.ios + Info.plist Summary

**Three iOS `actual` classes (PhotoVault, BiometricGate, PhotoCaptureLauncher) plus two new Info.plist usage descriptions ship the iOS half of the progress-pic feature: photos write to `<Documents>/progress_pics/{id}.jpg` with `NSDataWritingFileProtectionComplete` + `NSURLIsExcludedFromBackupKey` (T-PHOTO-EXFIL + T-CLOUD-LEAK), `LAContext.deviceOwnerAuthentication` gates unblur with German reason copy, and `UIImagePickerController` + `PHPickerViewController` deliver 1600px / quality 0.8 JPEG bytes — `:shared:linkDebugFrameworkIosSimulatorArm64` succeeds end to end.**

## Performance

- **Duration:** 9 min 3 sec
- **Started:** 2026-05-01T15:24:53Z
- **Completed:** 2026-05-01T15:33:56Z
- **Tasks:** 4
- **Files created:** 3 (all under `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/`)
- **Files modified:** 1 (`iosApp/iosApp/Info.plist`)

## Accomplishments

- `PhotoVault.ios.kt` writes JPEGs under `<Documents>/progress_pics/{id}.jpg` (D-17-05). Each write uses `NSDataWritingAtomic or NSDataWritingFileProtectionComplete` so the bytes are unreadable while the device is locked (T-PHOTO-EXFIL, D-17-06). Each saved file is then marked `NSURLIsExcludedFromBackupKey = true` so iCloud + iTunes backups skip it (T-CLOUD-LEAK, D-17-06). ByteArray <-> NSData conversion goes through `usePinned + memcpy` (read direction) and `NSData.create(bytes:length:)` under `@BetaInteropApi` opt-in (write direction).
- `BiometricGate.ios.kt` uses `LAPolicyDeviceOwnerAuthentication` (D-17-15 — passcode fallback included). Pre-flight `canEvaluatePolicy` short-circuits to `UnlockResult.Success` on `LAErrorPasscodeNotSet` (D-17-16 no-credential device). The async `evaluatePolicy` callback maps user/app/system cancel to `Cancelled`, `LAErrorAuthenticationFailed` to `Failed`, anything else to `Error(localizedDescription)` — all wrapped in `suspendCancellableCoroutine` (D-17-17 silent cancel/fail).
- `PhotoCaptureLauncher.ios.kt` bridges two pickers: `UIImagePickerController` (camera) and `PHPickerViewController` (library, iOS 14+, `selectionLimit = 1`, `filter = PHPickerFilter.imagesFilter`). Both use NSObject-subclass delegates that complete a `CompletableDeferred<UIImage?>`. The chosen image is then resized via `UIGraphicsBeginImageContextWithOptions` so the long edge is at most 1600px, encoded JPEG via `UIImageJPEGRepresentation(image, 0.8)`, and converted to `ByteArray` via `usePinned + memcpy` (D-17-07).
- `PhotoCapturePresenterHolder` (singleton `object` with `@kotlin.concurrent.Volatile var current: UIViewController?`) provides the root view controller the launcher presents from. SwiftUI side will call `PhotoCapturePresenterHolder.attach(controller)` during scene activation per the 17-08 IOS-HANDOFF (downstream).
- `iosApp/iosApp/Info.plist` gains two usage descriptions in German: `NSPhotoLibraryUsageDescription = "Wähle Fotos für deine Fortschritts-Galerie."` (required for PHPickerViewController on some iOS versions) and `NSFaceIDUsageDescription = "Fortschrittsbild entsperren."` (REQUIRED for `LAContext.deviceOwnerAuthentication` on Face ID devices — iOS will crash without it). The forbidden file-sharing keys (`UIFileSharingEnabled`, `LSSupportsOpeningDocumentsInPlace`) confirmed absent (T-FILE-SHARING).
- `:shared:compileKotlinIosSimulatorArm64` and `:shared:linkDebugFrameworkIosSimulatorArm64` both pass cleanly (only `w:` warnings for pre-existing "suspend exposed to ObjC" / "expect/actual in Beta" patterns from prior phases — no new errors). `plutil -lint iosApp/iosApp/Info.plist` reports OK.

## Task Commits

Each task committed atomically with `--no-verify` (worktree convention):

1. **Task 1: PhotoVault.ios with NSFileProtectionComplete + isExcludedFromBackupKey** — `d040d57` (feat)
2. **Task 2: BiometricGate.ios with LAContext.deviceOwnerAuthentication** — `9ba9aba` (feat)
3. **Task 2 follow-up: BetaInteropApi opt-in for evaluatePolicy** — `0396488` (chore — see deviation 1)
4. **Task 3: PhotoCaptureLauncher.ios with UIImagePickerController + PHPickerViewController** — `f3ea1c1` (feat)
5. **Task 4: NSPhotoLibraryUsageDescription + NSFaceIDUsageDescription in Info.plist** — `f08795d` (feat)

## Files Created/Modified

- `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.ios.kt` (NEW, 131 lines): `actual class PhotoVault` with lazy-resolved `<Documents>` and `<Documents>/progress_pics/` URLs, suspend `write/read/delete/deleteAll`, two private extension functions for `ByteArray <-> NSData`. File-level opt-in for `ExperimentalForeignApi + BetaInteropApi`.
- `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.ios.kt` (NEW, 98 lines after the chore opt-in commit): `actual class BiometricGate` with `requestUnlock(reason): UnlockResult`. Uses `canEvaluatePolicy` for the no-credential short-circuit and `evaluatePolicy` (callback) inside `suspendCancellableCoroutine` for the actual prompt. Imports the LAError code constants directly from `platform.LocalAuthentication`.
- `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.ios.kt` (NEW, 184 lines): `actual class PhotoCaptureLauncher` + `object PhotoCapturePresenterHolder` + two private NSObject-subclass delegates (`ImagePickerDelegate`, `PhPickerDelegate`) + four private extension functions (`UIImage.toResizedJpegBytes`, `UIImage.resized`, `NSData.toByteArray` — and the import-only `NSItemProviderReadingProtocol` cast).
- `iosApp/iosApp/Info.plist` (MODIFIED, 4 lines added inside the existing `<dict>`): `NSPhotoLibraryUsageDescription` + `NSFaceIDUsageDescription`. Existing `CADisableMinimumFrameDurationOnPhone` and `NSCameraUsageDescription` keys preserved.

## Decisions Made

- **PhotoCapturePresenterHolder shape pinned to a globally-mutable `object`, not a Koin `single`.** Two reasons: (1) the lifecycle of the "current root UIViewController" is owned by SwiftUI's scene callbacks, not by Kotlin construction time — Koin's `single` would have to take a setter anyway, so we'd just be wrapping the same `@Volatile var`. (2) The 17-08 IOS-HANDOFF doc will document a single registration shape (`PhotoCapturePresenterHolder.attach(controller)` in `onAppear`, `.detach(controller)` in `onDisappear`); making it an `object` keeps the API surface tiny. Symmetric to whatever the Android side did with its `*ActivityHolder` in 17-03.
- **CompletableDeferred over suspendCancellableCoroutine for the picker bridge.** UIImagePickerController fires its result through two separate delegate methods (`didFinishPickingMediaWithInfo` for success, `imagePickerControllerDidCancel` for cancel). Both need to "complete the suspension". With `suspendCancellableCoroutine` we'd need to share the `Continuation<UIImage?>` between two methods of a delegate object, which is awkward. `CompletableDeferred<UIImage?>` is captured by both methods cleanly — and `complete()` is idempotent so a double-fire (which iOS shouldn't do but might) is a no-op rather than a crash.
- **K/N CEnum qualified-access for UIImagePickerControllerSourceTypeCamera.** The plan's draft used the bare constant (`platform.UIKit.UIImagePickerControllerSourceTypeCamera`); the K/N 2.3.20 stdlib doesn't publish it that way. After `klib dump-metadata` showed the symbol lives as a CEnum case `UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera`, switched the import + access. Plan-level the only requirement is "compiles + behaves identically" — the ObjC integer at runtime is the same.
- **`UIImage as NSItemProviderReadingProtocol` cast for loadObjectOfClass.** The K/N binding declares `loadObjectOfClass(aClass: NSItemProviderReadingProtocol, …)` but at the ObjC level the parameter is a Class object adopting the protocol's class methods. UIImage adopts NSItemProviderReading via category — so the metaclass DOES respond to the class methods, and the cast is sound at runtime. Suppressed `CAST_NEVER_SUCCEEDS` because Kotlin's type checker can't see through ObjC's class/instance distinction.
- **`@kotlin.concurrent.Volatile`, NOT `@kotlin.jvm.Volatile`.** The plan source-code didn't import any Volatile, but K/N requires the annotation in front of a mutable shared `var`. `kotlin.jvm.Volatile` is JVM-only and deprecated (replaced by `kotlin.concurrent.Volatile` in KMP code). The latter compiles cleanly on iOS targets.
- **`linkDebugFrameworkIosSimulatorArm64` substituted for the plan's `linkPodDebugFrameworkIosSimulatorArm64`.** This project does not use the Kotlin CocoaPods plugin (verified via `gradlew tasks` showing only `linkDebugFrameworkIosSimulatorArm64`). The intent of the verify step (force a full Native link to surface ObjC binding errors) is preserved with the actual task name; the link succeeds.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Three Kotlin/Native binding mismatches in PhotoCaptureLauncher.ios.kt source draft**

- **Found during:** Task 3 verification — first run of `./gradlew :shared:compileKotlinIosSimulatorArm64`.
- **Issue:** The plan's draft source code had three K/N binding shapes that don't match the project's Kotlin 2.3.20 stdlib:
  1. `UIImagePickerControllerSourceTypeCamera` was imported as a top-level constant from `platform.UIKit.*` — but in K/N 2.3.20 it's a CEnum case nested inside `UIImagePickerControllerSourceType`.
  2. `@Volatile` had no import — the plan didn't pin which package to use; `kotlin.jvm.Volatile` is JVM-only and `kotlin.concurrent.Volatile` is the KMP-correct annotation.
  3. `provider.loadObjectOfClass(UIImage)` and `provider.canLoadObjectOfClass(UIImage)` — the K/N binding declares the parameter as `NSItemProviderReadingProtocol` (an instance protocol), but `UIImage` is an `ObjCClassOf<UIImage>` companion object; Kotlin's type checker rejects passing the Class metaobject where a protocol-typed instance is expected. Required a runtime-sound `as NSItemProviderReadingProtocol` cast (with `@Suppress("CAST_NEVER_SUCCEEDS")`).
- **Fix:** Inspected the actual binding shapes via `~/.konan/.../bin/klib dump-metadata` against the UIKit, Foundation, and stdlib klibs. Switched import to `UIImagePickerControllerSourceType` and qualified access (`UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera`). Added `import kotlin.concurrent.Volatile`. Added `import platform.Foundation.NSItemProviderReadingProtocol` and a single local `val uiImageReadingClass = UIImage as NSItemProviderReadingProtocol` cast used for both `canLoadObjectOfClass` and `loadObjectOfClass`. The plan's `<action>` block explicitly anticipated this kind of binding-shape adaptation: *"If the executor's K/N toolchain has a slightly different binding shape for any of `loadObjectOfClass`, `PHPickerConfiguration.filter`, or `UIImagePickerControllerOriginalImage` — adjust to compile."*
- **Files modified:** `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.ios.kt` (only — same file as Task 3).
- **Verification:** Post-fix, `:shared:compileKotlinIosSimulatorArm64` and `:shared:linkDebugFrameworkIosSimulatorArm64` both exit 0.
- **Committed in:** `f3ea1c1` (the Task 3 commit — fix is folded into the same commit that introduces the file, as the file never compiled at any earlier point).

**2. [Rule 2 - Critical] BetaInteropApi opt-in missing on BiometricGate.ios.kt for LAContext.evaluatePolicy callback**

- **Found during:** Task 3 verification — the broader iOS compile surfaced a `w:` warning on BiometricGate.ios.kt line 55 ("This declaration needs opt-in. Its usage should be marked with '@kotlinx.cinterop.BetaInteropApi'").
- **Issue:** `LAContext.evaluatePolicy(policy:, localizedReason:, reply:)` is annotated `@BetaInteropApi` in the K/N 2.3.20 platform.LocalAuthentication binding because of the trailing-closure `reply` parameter shape. Without the file-level opt-in, the file compiles with a warning today but is at risk of becoming an error in a future K/N version that promotes the annotation. The PhotoVault.ios.kt analog already opts in to BetaInteropApi (for `NSData.create`), so adding the same opt-in here is consistent and forward-compatible.
- **Fix:** Updated the file-level OptIn annotation from `@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)` to `@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)`.
- **Files modified:** `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.ios.kt`.
- **Verification:** Post-fix, the BetaInteropApi warning on BiometricGate.ios.kt is gone.
- **Committed in:** `0396488` — committed as a separate `chore` because Task 2 was already committed when the warning surfaced; rather than amend (and fight the GSD never-amend rule) we made it a follow-up. Scope is tiny (one-line change) and atomic.

**3. [Rule 3 - Plan-text] Verify task name `linkPodDebugFrameworkIosSimulatorArm64` doesn't exist in this project**

- **Found during:** Task 3 verification.
- **Issue:** The plan's `<verify>` block calls `./gradlew :shared:linkPodDebugFrameworkIosSimulatorArm64`. This task name comes from the Kotlin CocoaPods plugin, which Pumpernickel does not use (the project ships a plain Compose Multiplatform iOS framework, not a Pod). Gradle errors with "Task 'linkPodDebugFrameworkIosSimulatorArm64' not found in project ':shared'. Some candidates are: 'linkDebugFrameworkIosSimulatorArm64'."
- **Fix:** Substituted `linkDebugFrameworkIosSimulatorArm64` (the actual task name in this project). The intent of the verify step (force a full Native link to surface ObjC binding errors at this plan rather than downstream) is preserved — the substituted task does exactly the same thing minus the Cocoapods packaging step.
- **Files modified:** None. Plan-text verification command substitution only.
- **Verification:** `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 -q` exits 0.
- **Committed in:** N/A (verification-strategy fix; no source change).

**4. [Rule 3 - Environment] Missing Room schema JSONs in worktree (same root cause as 17-02 deviation 1)**

- **Found during:** Pre-flight before Task 1.
- **Issue:** The worktree's `shared/schemas/com.pumpernickel.data.db.AppDatabase/` directory was empty. Room's KSP runs on the iOS target during `compileKotlinIosSimulatorArm64` (KSP plugin is configured for all `kotlin { … }` source sets), so even an iOS-only edit can hit the same "Schema 'X.json' required for migration was not found" KSP error 17-02 documented.
- **Fix:** Pre-emptively copied `2.json` through `9.json` from the main repo's `shared/schemas/com.pumpernickel.data.db.AppDatabase/` into the worktree before running any Gradle task. `shared/schemas/` is gitignored (verified via `git check-ignore -v shared/schemas/foo.json` which returned `.gitignore:27:shared/schemas/`), so this fix is purely a local KSP working-set repair and never enters the commit graph.
- **Files modified:** `shared/schemas/com.pumpernickel.data.db.AppDatabase/{2,3,4,5,6,7,8,9}.json` (gitignored — not committed).
- **Verification:** `./gradlew :shared:compileKotlinIosSimulatorArm64 -q` exits 0 throughout the plan; no Room/KSP errors at any point.
- **Committed in:** N/A (gitignored — does not enter the commit graph).

---

**Total deviations:** 4 — two source-code fixes (deviations 1 & 2, both of which the plan's `<action>` block explicitly anticipated as "adjust to compile" / executor discretion), one verification-command substitution (deviation 3), and one pre-flight environment repair (deviation 4 — same as 17-02). All four are within the plan's documented latitude. Source code spec executed exactly as the plan's intent describes.

## Issues Encountered

- None beyond the four Rule-1/2/3 deviations above. No checkpoints, no Rule 4 architectural escalations, no auth gates.

## TDD Gate Compliance

Plan type: `execute` (not TDD). Plan frontmatter does not declare `type: tdd`. No RED/GREEN/REFACTOR gate required. Tasks are 4× `type="auto" tdd="false"` per the plan source.

## User Setup Required

None at the Kotlin layer. The iOS-side wiring of `PhotoCapturePresenterHolder.attach(rootViewController)` is documented for plan 17-08's IOS-HANDOFF.md (downstream); no SwiftUI code changes in this plan.

## Next Phase Readiness

- **Plan 17-05 (capture flow VM)** — iOS path is unblocked. `repo.savePicture(workoutId, bytes, capturedAtMillis)` resolves on iOS via the Koin-injected `PhotoVault.ios` actual; `launcher.captureFromCamera()` / `launcher.pickFromLibrary()` resolve via the `PhotoCaptureLauncher.ios` actual. The capture VM does not need to know which platform it's on.
- **Plan 17-06 (gallery VM)** — `PhotoVault.ios.read(relativePath)` is callable for cover-photo bytes; the VM's `Flow.combine` enrichment can decode bytes for cover thumbnails on demand.
- **Plan 17-07 (viewer VM)** — `BiometricGate.ios.requestUnlock(reason)` returns the locked `UnlockResult` sealed type; the viewer VM's `when (result) { Success -> load bytes; Cancelled/Failed -> no-op; Error -> toast }` branch table is now end-to-end on iOS.
- **Plan 17-08 (Koin DI + IOS-HANDOFF doc)** — Koin can register `single<PhotoVault> { PhotoVault() }` (no-arg ctor — no platform context needed on iOS), `single<BiometricGate> { BiometricGate() }`, `single<PhotoCaptureLauncher> { PhotoCaptureLauncher() }`. The IOS-HANDOFF doc must additionally document `PhotoCapturePresenterHolder.attach(rootViewController)` / `.detach(rootViewController)` in the SwiftUI scene lifecycle. The `imagesFilter` / `loadObjectOfClass` / `UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera` binding shapes used by this plan are documented in `key-decisions` above; the handoff doc may cite this summary directly.
- No blockers. iOS compile + link both green.

## Threat Flags

None — files created/modified introduce no new attack surface beyond what the plan's `<threat_model>` already covers (T-PHOTO-EXFIL, T-CLOUD-LEAK, T-FILE-SHARING all explicitly mitigated; T-17-04-01/02/03 explicitly accepted in the model). No untracked endpoints, no new permission requests beyond the two `Info.plist` keys the plan mandated, no auth path changes outside `BiometricGate.ios`.

## Self-Check: PASSED

Verified before returning:

- `[ -f shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.ios.kt ]` -> FOUND
- `[ -f shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.ios.kt ]` -> FOUND
- `[ -f shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.ios.kt ]` -> FOUND
- `[ -f iosApp/iosApp/Info.plist ]` -> FOUND (modified, lint OK)
- `git log --oneline | grep d040d57` -> FOUND: `feat(17-04): implement PhotoVault.ios with NSFileProtectionComplete + isExcludedFromBackupKey`
- `git log --oneline | grep 9ba9aba` -> FOUND: `feat(17-04): implement BiometricGate.ios with LAContext.deviceOwnerAuthentication`
- `git log --oneline | grep 0396488` -> FOUND: `chore(17-04): add BetaInteropApi opt-in to BiometricGate.ios`
- `git log --oneline | grep f3ea1c1` -> FOUND: `feat(17-04): implement PhotoCaptureLauncher.ios with UIImagePickerController + PHPickerViewController`
- `git log --oneline | grep f08795d` -> FOUND: `feat(17-04): add NSPhotoLibraryUsageDescription + NSFaceIDUsageDescription to Info.plist`
- All Task 1 acceptance-grep checks (12) passed
- All Task 2 acceptance-grep checks (11) passed (B4 reworded KDoc to keep "biometric-only forbidden" semantics without containing the literal token)
- All Task 3 acceptance-grep checks (11) passed
- All Task 4 acceptance-grep checks (6) passed
- `./gradlew :shared:compileKotlinIosSimulatorArm64 -q` exits 0
- `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 -q` exits 0
- `plutil -lint iosApp/iosApp/Info.plist` reports OK

---
*Phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work*
*Completed: 2026-05-01*
