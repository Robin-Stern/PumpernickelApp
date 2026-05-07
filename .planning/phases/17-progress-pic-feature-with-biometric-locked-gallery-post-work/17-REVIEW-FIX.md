---
phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work
fixed_at: 2026-05-01T00:00:00Z
review_path: .planning/phases/17-progress-pic-feature-with-biometric-locked-gallery-post-work/17-REVIEW.md
iteration: 1
findings_in_scope: 12
fixed: 12
skipped: 0
status: all_fixed
worktree_branch: reviewfix-17-1777668559
---

# Phase 17: Code Review Fix Report

**Fixed at:** 2026-05-01
**Source review:** `17-REVIEW.md`
**Iteration:** 1
**Worktree branch:** `reviewfix-17-1777668559` (orchestrator merges into `main`)

**Summary:**
- Findings in scope (BLOCKER + MAJOR): 12
- Fixed: 12
- Skipped: 0

All BLOCKER and MAJOR findings were fixed. MINOR + INFO findings remain unaddressed by design (out of scope for this pass per `fix_scope: critical_warning`). One observation: the M-07 fix folded in the lifecycle guard but did *not* re-classify `ERROR_CANCELED` (m-03), which is left for a later pass with the rest of MINOR.

## Fixed Issues

### B-01 — iOS + Android `PhotoVault.read/delete` accept arbitrary relative paths (path-traversal class)

- **Files modified:** `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.ios.kt`, `shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.android.kt`
- **Commit:** `ed5665d`
- **Applied fix:** Added `resolveSafe(relativePath)` helper on both actuals. Validates `relativePath.startsWith("progress_pics/")`, then canonicalises (`File.canonicalPath` on Android, `URLByStandardizingPath` + `path` on iOS) and rejects anything that is not strictly inside `rootDir + File.separator`. `read`, `delete`, and `deleteAll` now route through the helper. Stale or tampered DB rows containing `..` segments or sibling-prefix paths (`progress_picsX/...`) are rejected. The writer is unchanged — D-17-08 schema is locked, only path *consumption* hardened.

### B-02 — `WorkoutSessionState.Finished.photoCount` is hard-coded `0`, never increments

- **Files modified:** `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt`
- **Commit:** `bd22fdd`
- **Applied fix:** Removed the dead `photoCount: Int = 0` field from `WorkoutSessionState.Finished`. Verified no consumer reads it: Android `WorkoutSessionScreen.FinishedContent` only reads `finished.workoutId`; iOS `17-IOS-HANDOFF.md` references `photoCount` exclusively from `PromptUiState` (the prompt VM) which is the live count source. Removing the dead field is the smaller, safer choice over wiring `ProgressPictureRepository` into `WorkoutSessionViewModel`. **Requires human verification only at the level of confirming Swift code does not pattern-match on `.Finished(workoutName:durationMillis:totalSets:totalExercises:workoutId:photoCount:)` — the handoff doc uses the workoutId-only shape, so this should be safe.**

### B-03 — Android `Uri.toFile()` is null/path-unsafe and bypasses content-resolver semantics

- **Files modified:** `shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.android.kt`
- **Commit:** `abe66ae`
- **Applied fix:** Reworked `PhotoCaptureLauncherHost.launchCamera()` to return the trusted cache `File` we created (not a `Uri` the camera echoed back). `captureFromCamera()` now reads bytes directly from this file. The unsafe top-level `Uri.toFile()` extension was deleted. `pickFromLibrary` continues to use `contentResolver.openInputStream(uri)` for content URIs (the legitimate library path).

### M-01 — `PhotoCaptureLauncherHost` race: pending deferred clobbered on concurrent launches

- **Files modified:** `shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.android.kt`
- **Commit:** `3ab15a5`
- **Applied fix:** Both `launchCamera()` and `launchLibrary()` now `complete(null)` on the prior pending deferred and clear the field before assigning a new one. The previous awaiter no longer suspends forever; the VM-layer `_busy` flag remains the primary front-line guard, this is defence-in-depth.

### M-02 — `Bitmap.createScaledBitmap` does not recycle the original bitmap

- **Files modified:** `shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.android.kt`
- **Commit:** `ed099c0`
- **Applied fix:** Two-pass decode in `resizeAndEncode`. Pass 1 uses `inJustDecodeBounds=true` to read source dimensions cheaply. Pass 2 uses `inSampleSize` (power-of-two) so the decoded bitmap is close to 1600px target — avoiding a 50 MB ARGB_8888 allocation. Both the decoded intermediate (if a separate scaled bitmap was created) and the final scaled bitmap are recycled after JPEG encoding.

### M-03 — `ProgressGalleryViewModel` re-reads `nutritionGoals.first()` and `getAllEntries()` on every emission

- **Files modified:** `shared/src/commonMain/kotlin/com/pumpernickel/data/db/NutritionDao.kt`, `shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressGalleryViewModel.kt`
- **Commit:** `f386c44`
- **Applied fix:** Added `NutritionDao.observeAllEntries(): Flow<List<ConsumptionEntryEntity>>`. Replaced the suspend-`first()` + suspend-`getAllEntries()` pattern with a 3-way `combine` of `observeGalleryTiles`, `nutritionGoals.onStart { emit(NutritionGoals()) }`, and `observeAllEntries`. The `onStart` default eliminates the cold-start hang risk; goal-day evaluation now reacts live to nutrition log changes.

### M-04 — `iosMain` `PhPickerDelegate` uses `CAST_NEVER_SUCCEEDS` for an ObjC class cast

- **Files modified:** `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.ios.kt`
- **Commit:** `85e0ed4`
- **Applied fix:** Replaced `provider.loadObjectOfClass(uiImageReadingClass) { ... }` with `provider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ -> ... }`. The K/N-fragile metaclass cast and its `@Suppress("CAST_NEVER_SUCCEEDS")` are gone; data is loaded as `NSData` and decoded via `UIImage.imageWithData(data)`. **Logic-fix flag:** the picker integration should be smoke-tested on simulator — the API surface is well-documented and standard, but compile-time only.

### M-05 — iOS `PhotoCaptureLauncher` retains `delegate` only via `@Suppress("UNUSED_EXPRESSION")`

- **Files modified:** `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.ios.kt`
- **Commit:** `4fa9c15`
- **Applied fix:** Added `@Volatile private var currentCameraDelegate / currentLibraryDelegate` fields on `PhotoCaptureLauncher`. Each capture suspends inside a `try { ... } finally { if (current === delegate) current = null }` block so the delegate is provably retained across the suspension point regardless of K/N continuation lowering, and cleared once the await completes. Concurrent calls are tolerated (the finally only nulls the field if it still references our delegate).

### M-06 — `PhotoCaptureLauncherHost.cameraLauncher` callback bound to recreated Activity

- **Files modified:** `androidApp/src/androidMain/AndroidManifest.xml`
- **Commit:** `98de46e`
- **Applied fix:** Added `android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden|uiMode|locale|fontScale|density"` on `MainActivity`. Compose handles these config changes via state recomposition; suppressing Activity recreation keeps the same `PhotoCaptureLauncherHost` instance alive across rotation/theme/locale changes — so the in-flight camera deferred completes against the correct host. Pragmatic fix per the reviewer's recommendation.

### M-07 — `BiometricGate.android` runs auth without main-thread guarantee

- **Files modified:** `shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.android.kt`
- **Commit:** `cfa24cd`
- **Applied fix:** Added a per-instance `Mutex` so concurrent `requestUnlock` calls serialise (prevents the BiometricPrompt FragmentManager `IllegalStateException` from "fragment already added"). Added a `Lifecycle.State.STARTED` guard so an Activity that was destroyed while the holder still pointed at it returns `UnlockResult.Error` instead of crashing inside `BiometricPrompt.authenticate`. Wrapped `prompt.authenticate(info)` in try/catch that maps any throwable to `UnlockResult.Error(t.message)`. m-03 (ERROR_CANCELED → Error mapping) was deliberately NOT folded in — that is MINOR scope.

### M-08 — `Bitmap` decoded in `produceState` not recycled — gallery + viewer leaks

- **Files modified:** `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ProgressGalleryScreen.kt`, `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ProgressViewerScreen.kt`
- **Commit:** `a8c5d3d`
- **Applied fix:** Added `decodeDownsampled` / `decodeDownsampledViewer` two-pass-decode helpers (inJustDecodeBounds → inSampleSize → decode). Gallery tiles now decode at ~400px target; the viewer at ~1600px target. Both screens hold the decoded `Bitmap` in `remember(bytes)` and recycle it via `DisposableEffect(decodedBitmap) { onDispose { decodedBitmap?.recycle() } }` so scroll-out / page-away frees the underlying memory immediately rather than waiting for GC. Coil 3 was *not* added — keeping the dependency graph small for the prototype, per the recommended-stack table that lists Coil as conditional.

### M-09 — `ProgressGalleryScreen` uses a `Modifier.blur` that is a no-op on Android < API 31

- **Files modified:** `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ProgressGalleryScreen.kt`
- **Commit:** `71c779b`
- **Applied fix:** API-version branch in `GalleryTile`. On API ≥ 31 (`Build.VERSION_CODES.S`), decode at 400px and apply `Modifier.blur(24.dp)` — smooth modern blur. On API < 31, decode at **32px** and let `ContentScale.Crop` upscale the bitmap to tile size — chunky pixelation that achieves the D-17-13 "faces unrecognisable" requirement on every supported API level. The `supportsRealBlur` flag drives both the decode target and the blur modifier so the privacy contract holds across the project's full minSdk=26 → API 35 range. **Logic-fix flag:** verify on a real Android 8/9/10/11 device that 32px decode + crop genuinely obscures faces — pixel test was visual reasoning only.

## Skipped Issues

None — all 12 in-scope findings were fixed.

## Notes on hard constraints

- **D-17-18 (iOS SwiftUI user-owned):** No `iosApp/iosApp/Views/**/*.swift` files were touched. Kotlin iOS code (`shared/src/iosMain/**/*.kt`) was modified as in-scope.
- **D-17-08 (schema locked):** `ProgressPictureEntity` shape unchanged.
- **D-17-14 (single-gate invariant):** `_unlockedWorkoutId` write sites in `ProgressViewerViewModel.kt` not modified — count remains exactly two.
- **D-17-06 (backup config):** `android:allowBackup="true"` unchanged.
- **Phase 16 TdeeCalculatorTest drift:** Out of scope per instructions — not touched.

---

_Fixed: 2026-05-01_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
