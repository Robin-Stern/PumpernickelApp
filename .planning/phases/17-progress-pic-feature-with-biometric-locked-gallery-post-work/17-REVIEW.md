---
phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work
reviewed: 2026-05-01T00:00:00Z
depth: standard (advisory)
files_reviewed: 22
files_reviewed_list:
  - shared/src/commonMain/kotlin/com/pumpernickel/data/db/ProgressPictureEntity.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/data/db/ProgressPictureDao.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ProgressPictureRepository.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressGalleryViewModel.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressViewerViewModel.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressPicturePromptViewModel.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/di/ProgressGalleryModule.kt
  - shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.android.kt
  - shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.android.kt
  - shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.android.kt
  - shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt
  - shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.ios.kt
  - shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.ios.kt
  - shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.ios.kt
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/MainActivity.kt
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ProgressGalleryScreen.kt
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ProgressViewerScreen.kt
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/ProgressPicturePromptCard.kt
  - androidApp/src/androidMain/AndroidManifest.xml
  - androidApp/src/androidMain/res/xml/backup_rules.xml
  - androidApp/src/androidMain/res/xml/data_extraction_rules.xml
  - iosApp/iosApp/Info.plist
findings:
  blocker: 3
  major: 9
  minor: 7
  info: 3
  total: 22
status: issues_found
---

# Phase 17: Code Review Report (Advisory)

**Reviewed:** 2026-05-01
**Depth:** standard (advisory — findings are recommendations)
**Files Reviewed:** 27
**Status:** issues_found

## Summary

Phase 17 ships a coherent privacy-conscious photo feature with the `_unlockedWorkoutId` gate correctly limited to two write sites in `ProgressViewerViewModel.kt:79` (Success) and `:95` (relock). OS-level hardening (`NSFileProtectionComplete`, `isExcludedFromBackupKey`, Android `dataExtractionRules` + `fullBackupContent`) is in place.

That said, there are several **correctness and security** issues:

1. The Android `PhotoCaptureLauncherHost` has a **race condition** that can leak deferreds on concurrent re-launches and silently drop user captures.
2. `Uri.toFile()` in `PhotoCaptureLauncher.android.kt` performs **path traversal-friendly** `File()` resolution from a URI's raw `path` (no host, no decoding) — exploitable if the camera app ever returns a non-`file://` URI with a malicious path component.
3. iOS `PhotoVault.read()` resolves arbitrary `relativePath` strings from the DB against `documentsDir` with **no path validation** — a stale or tampered DB row could read files outside `progress_pics/`.

There are also bugs around `Bitmap` lifecycle (memory leak), `WorkoutSessionState.Finished.photoCount` never being updated, hard-suspending `nutritionGoals.first()` calls per Flow emission, and an iOS NSItemProvider unsafe cast pattern that the executor admitted to with a `CAST_NEVER_SUCCEEDS` suppression.

## Severity Counts

| Severity | Count |
|---|---|
| BLOCKER | 3 |
| MAJOR | 9 |
| MINOR | 7 |
| INFO | 3 |
| **Total** | **22** |

---

## BLOCKERS

### B-01 — iOS `PhotoVault.read/delete` accept arbitrary relative paths (path-traversal class)

- **Severity:** BLOCKER (security)
- **File:** `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.ios.kt:98-109`
- **Problem:** `read(relativePath)` and `delete(relativePath)` call `documentsDir.URLByAppendingPathComponent(relativePath, ...)` directly with whatever string the DB row holds. If a row is ever crafted/tampered to contain `../Library/Cookies/...` or `progress_pics/../../something.plist`, this resolves outside the photo vault. `URLByAppendingPathComponent` does NOT normalise / reject `..` segments. The Android variant (`File(filesDir, relativePath)`) has the same exposure.
- **Why it matters:** Although the writer *only* uses `progress_pics/{uuid}.jpg`, any future Room migration / sync / import path that round-trips this column lifts the trust boundary. The Phase 17 framing is "vault" — defense in depth requires the reader to enforce the prefix.
- **Fix:** Validate `relativePath.startsWith("progress_pics/")` AND that the resolved absolute path is still under the rootDir (resolve, normalise via `URL.standardizedURL` / Android `File.canonicalPath`, then `startsWith` check). Reject otherwise. Apply to **both** iOS and Android actuals.

### B-02 — `WorkoutSessionState.Finished.photoCount` is hard-coded `0`, never increments

- **Severity:** BLOCKER (incorrect behavior — feature regression)
- **File:** `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt:601` (init) and nowhere else (no setter)
- **Problem:** The `Finished` state carries `photoCount: Int = 0`, but the VM never collects from `ProgressPictureRepository.observePhotoCount(workoutId)` and never updates the field. The `ProgressPicturePromptCard` reads its own count from its dedicated VM, so the UI happens to render correctly — but per CONTEXT integration-points line 190 the field on `Finished` was added for a reason and is now dead state. Either the field should be wired (collect a Flow on enter-Finished and `copy(photoCount = ...)`) or the field should be removed.
- **Fix:** Either remove `photoCount` from `Finished` (and from any iOS handoff that promised it) or actually wire it via `progressPictureRepository.observePhotoCount(workoutId).collect { count -> _sessionState.update { ... copy(photoCount = count) } }` inside `saveReviewedWorkout`. Inject `ProgressPictureRepository` into `WorkoutSessionViewModel` if you keep it.

### B-03 — Android `Uri.toFile()` is null/path-unsafe and bypasses content-resolver semantics

- **Severity:** BLOCKER (correctness + minor security)
- **File:** `shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.android.kt:153`
- **Problem:** `private fun Uri.toFile(): File = File(requireNotNull(path) { ... })` uses `Uri.path` directly. For a `file://` URI the `path` is decoded but for content-style URIs that happen to have a `file` scheme but include query/fragment encoding, this yields the wrong path. More importantly, a malicious or buggy camera app could return a URI whose path traverses outside the cache (e.g. `file:///data/data/com.pumpernickel.android/databases/AppDatabase`) and `readBytes()` will happily read it. The TakePicture contract uses the URI YOU passed it, but a malicious package responding to the camera intent can echo a different URI back through the result.
- **Fix:** When `success == true` from `TakePicture`, do not trust the result URI. Instead read directly from `pendingCameraFile` (which you control). Drop the `uri.toFile()` helper or replace with `androidx.core.net.toFile()` plus a `canonicalPath.startsWith(cacheDir.canonicalPath)` check.

---

## MAJOR

### M-01 — `PhotoCaptureLauncherHost` race: pending deferred clobbered on concurrent launches

- **Severity:** MAJOR
- **File:** `shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.android.kt:53-92`
- **Problem:** `pendingCameraDeferred` and `pendingLibraryDeferred` are mutable single-slot fields. If `launchCamera()` is called twice (e.g. via fast double-tap or after onCreate re-entry while a previous deferred is still suspended), the second call overwrites the field — the first deferred suspends forever (coroutine leak), and the result of the second eventually completes only the second deferred. The VM guards with `_busy.value` but the host class has no such guard and is reachable from anywhere with the holder reference.
- **Fix:** Reject concurrent launches: `if (pendingCameraDeferred != null) return null` (or throw IllegalStateException). Better: complete the prior deferred with `null` ("cancelled by new launch") before assigning. Same for library.

### M-02 — `Bitmap.createScaledBitmap` does not recycle the original bitmap (memory pressure)

- **Severity:** MAJOR
- **File:** `shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.android.kt:131-149`
- **Problem:** `decodeByteArray` returns a Bitmap that may be ~50 MB+ for a modern phone camera (4032×3024 ARGB_8888). `createScaledBitmap` returns a new bitmap; the original is left for the GC, which on Android often takes one or more frames + a Full GC. Repeated camera captures can OOM. Also: `BitmapFactory.decodeByteArray` without `inSampleSize` decodes the full original at full resolution before downscaling — wasted memory.
- **Fix:** Use `BitmapFactory.Options` with two-pass decode (first `inJustDecodeBounds = true` to read dims, compute `inSampleSize` to downsample during decode, then second decode). After scaling, call `original.recycle()` if `scaled !== original`.

### M-03 — `ProgressGalleryViewModel` re-reads `nutritionGoals.first()` and `getAllEntries()` on every emission

- **Severity:** MAJOR
- **File:** `shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressGalleryViewModel.kt:71-95`
- **Problem:** Two issues:
  1. `settingsRepository.nutritionGoals.first()` suspends until the first emission. If the underlying DataStore Flow has not yet hit its first value (cold start, slow disk), the entire enrichment pipeline blocks indefinitely — the UI stays in `isLoading = true`.
  2. Every gallery-tile emission re-reads `nutritionDao.getAllEntries()` (full table scan) and re-runs N+1 `getPrLedgerEntriesForWorkout` calls. The "N+1 note" comment acknowledges this but the `combine` pattern would compose Flows correctly without re-reading: `combine(repository.observeGalleryTiles(), settingsRepository.nutritionGoals, nutritionDao.observeAllEntries())`.
- **Fix:** Compose Flows with `combine`. Provide `nutritionGoals` a default fallback to avoid forever-suspend on cold start (`.onStart { emit(NutritionGoals.DEFAULT) }` or use a StateFlow with a non-null initial). Convert `NutritionDao.getAllEntries` to `observeAllEntries(): Flow<...>` if not already.

### M-04 — `iosMain` `PhPickerDelegate` uses `CAST_NEVER_SUCCEEDS` for an ObjC class cast

- **Severity:** MAJOR
- **File:** `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.ios.kt:140-147`
- **Problem:** `@Suppress("CAST_NEVER_SUCCEEDS") val uiImageReadingClass = UIImage as NSItemProviderReadingProtocol` is suppressing a compiler error that says the cast is impossible. At runtime Kotlin/Native may or may not let this cast through depending on the K/N version (the comment claims it works because UIImage's *metaclass* conforms — Kotlin sees the class object, not the metaclass). This is fragile. If K/N tightens the cast, the picker silently returns `null` for every selection, breaking the library flow with no error surfaced.
- **Fix:** Use the K/N-idiomatic pattern: `provider.loadDataRepresentationForTypeIdentifier("public.image") { data, error -> ... }` then decode `NSData` → `UIImage(data:)`. Avoids the protocol/metaclass cast entirely.

### M-05 — iOS `PhotoCaptureLauncher` retains `delegate` only via `@Suppress("UNUSED_EXPRESSION") delegate`

- **Severity:** MAJOR
- **File:** `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.ios.kt:75, 95`
- **Problem:** The picker holds the delegate via a `weak` ObjC property. The Kotlin local `val delegate` IS the strong reference, but `await()` is a suspension point — the compiler may extract the local into a continuation; whether `@Suppress("UNUSED_EXPRESSION") delegate` post-suspend is enough to keep it alive depends on K/N's continuation lowering and is not guaranteed by the language. If GC collects the delegate before the user picks, the picker callback never fires and the deferred hangs forever.
- **Fix:** Hold the delegate explicitly in a class-level field (or as a property of a suspend wrapper) until the deferred completes. Pattern: assign `currentDelegate = delegate` in a `private var` on `PhotoCaptureLauncher`, clear it in a `finally` block after `await()`.

### M-06 — `PhotoCaptureLauncherHost.cameraLauncher` callback uses Activity at registration time, but Activity may be recreated

- **Severity:** MAJOR
- **File:** `shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.android.kt:48-69` & `MainActivity.kt:34`
- **Problem:** `PhotoCaptureLauncherHost` is constructed with the Activity in `MainActivity.onCreate` and registers two `ActivityResultLauncher` callbacks against that Activity instance. On configuration change (rotation, system theme change, etc.), `MainActivity` is destroyed and recreated; `onDestroy` detaches the *old* host and onCreate creates a *new* one, but if a camera capture is in-flight when rotation occurs, the previously registered callbacks fire on the old (destroyed) Activity — `pendingCameraDeferred` is on the old host (now discarded), so the deferred never completes. The VM's `_busy.value = true` is stuck.
- **Fix:** Use the standard pattern: register the launcher inside a `Composable` via `rememberLauncherForActivityResult` OR retain the host across recreation by hoisting it to a `ViewModel`/`SavedStateHandle`-backed singleton. Alternatively, document `android:configChanges` for the Activity to suppress recreation (already easier since you control the Activity).

### M-07 — `BiometricGate.android` runs auth on `Dispatchers.Main` but no main-thread guarantee for `BiometricPrompt.authenticate`

- **Severity:** MAJOR
- **File:** `shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.android.kt:46-109`
- **Problem:** Two concerns:
  1. `BiometricGateActivityHolder.current` may have changed between the `withContext(Dispatchers.Main)` dispatch and the `prompt.authenticate(info)` call (Activity recreation during rotation); the `activity` local is captured at the start and may now be in DESTROYED state. `BiometricPrompt.authenticate` against a destroyed FragmentActivity throws.
  2. If `requestUnlock` is called twice in parallel (fast tap), there's no guard — two prompts try to attach to the same FragmentActivity, which the BiometricPrompt fragment manager rejects with an `IllegalStateException`.
- **Fix:** Wrap `prompt.authenticate(info)` in a try/catch returning `UnlockResult.Error(...)`. Add a per-instance `Mutex` so concurrent `requestUnlock` calls serialise.

### M-08 — `Bitmap` decoded in `produceState` not recycled, scrolling the gallery leaks bitmaps

- **Severity:** MAJOR
- **File:** `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ProgressGalleryScreen.kt:147-152`, `ProgressViewerScreen.kt:108-114`
- **Problem:** `BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap()` produces a Bitmap that is wrapped by Compose. On scroll, the gallery items are re-composed; old bitmaps are dropped to GC but not recycled. With ~50 photos a 1600px JPEG decodes to ~10 MB ARGB_8888 — easy 500MB working set without a max heap configured.
- **Fix:** Use Coil 3 (`coil-compose`) which is already on the recommended stack — it handles caching, downsampling, and bitmap pooling. Alternatively, decode with `inSampleSize` to tile dimensions (~400px) and recycle on `DisposableEffect.onDispose`.

### M-09 — `ProgressGalleryScreen` uses a `Modifier.blur` that does NOT obscure on Android < API 31

- **Severity:** MAJOR
- **File:** `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ProgressGalleryScreen.kt:173`
- **Problem:** `Modifier.blur` in Compose is a no-op on Android API 30 and below (per official docs). On a device running Android 11, the gallery will show **unblurred** photos in the grid — the privacy framing of the entire feature is broken silently. The minSdk on this project is not visible in the reviewed scope, but if it is anything below 31 (Android 12) this is a security finding.
- **Fix:** Either bump minSdk to 31, or implement a fallback: pre-render a heavily downsampled / pixelated version, OR use `RenderScript` ScriptIntrinsicBlur (deprecated but works on older), OR use the Material 3 surface tint hack (apply a translucent over-paint plus low resolution so the photo is unrecognisable). Phase 17 D-17-13 explicitly says "must keep faces unrecognisable" — silently dropping the blur fails the requirement.

---

## MINOR

### m-01 — `volumeKgX10 / 10L` integer division loses tenths consistently with spec but should use `floor` notation

- **Severity:** MINOR
- **File:** `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ProgressPictureRepository.kt:75`
- **Problem:** `dto.volumeKgX10 / 10L` is integer division; matches D-17-12's `floor()` requirement. However, the type in `ProgressGalleryTile` is `Long` — code reviewers will see the truncation bug pattern. Add a comment.
- **Fix:** Add an inline comment: `// integer division — floor(kg) per D-17-12`.

### m-02 — `relock()` fires on every `DisposableEffect.onDispose`, including configuration changes

- **Severity:** MINOR
- **File:** `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ProgressViewerScreen.kt:72-78`
- **Problem:** `DisposableEffect(Unit) { onDispose { viewModel.relock() } }` runs on rotation/config-change too, forcing a re-prompt for the same view session. Per D-17-14 this might be the intent ("per-tile every-tap"), but the user experiences it as the prompt firing in the middle of viewing because they rotated the phone.
- **Fix:** Distinguish `popBackStack` from configuration change. Use `LifecycleEventObserver` listening to `ON_DESTROY` only when `isFinishing`/`isRemoving`, OR move the relock call to the navigation pop side (e.g., `navController.addOnDestinationChangedListener`).

### m-03 — `BiometricPrompt` callback `onAuthenticationError` treats `ERROR_CANCELED` as user-Cancelled, hiding system errors

- **Severity:** MINOR
- **File:** `shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.android.kt:81-86`
- **Problem:** `ERROR_CANCELED` is "the operation was canceled because the API is locked out due to too many attempts". Mapping that to `UnlockResult.Cancelled` rather than `UnlockResult.Error` hides a real lockout from the caller. The viewer VM treats Cancelled and Failed identically, so the user just gets a silent locked placeholder.
- **Fix:** Map `ERROR_CANCELED` to `UnlockResult.Error("Locked out")` so caller can surface a hint. `ERROR_USER_CANCELED` and `ERROR_NEGATIVE_BUTTON` are the true user-cancelled cases.

### m-04 — `fileUrl.setResourceValue(true as Any?, ...)` non-null error is silently swallowed

- **Severity:** MINOR
- **File:** `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.ios.kt:86-93`
- **Problem:** Backup-exclusion failure goes unnoticed; the file is written but synced to iCloud. Comment claims this is "defence in depth" but T-CLOUD-LEAK is one of the named threats — silent failure here defeats the named mitigation.
- **Fix:** Check the BOOL return of `setResourceValue` and at minimum log to `os_log` / `NSLog`. Optionally attempt one retry.

### m-05 — `PromptCard` busy spinner and "Speichere…" are inside an `else if` that can hide the photo count when busy

- **Severity:** MINOR
- **File:** `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/ProgressPicturePromptCard.kt:125-146`
- **Problem:** While the save is in flight, the user no longer sees the "N Fotos angehängt" line — minor UX wobble. Not a bug, just a state visibility issue.
- **Fix:** Render both: the "saving" indicator and the count, side by side.

### m-06 — `PhotoVault.android` `rootDir.mkdirs()` returns false on permission failure but is ignored

- **Severity:** MINOR
- **File:** `shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.android.kt:22-24`
- **Problem:** `File.mkdirs()` returns `false` if creation fails. Subsequent `writeBytes` will throw `FileNotFoundException`. The exception is propagated to the prompt VM's try/catch, which then surfaces `t.message` — but `filesDir` should always be writable so the failure mode is unusual. Document or ignore.
- **Fix:** Add `check(rootDir.exists() || rootDir.mkdirs())` for explicit failure surface.

### m-07 — `formatGermanThousand` reverses StringBuilder via `.reverse()` then `toString()` — fine, but the function is duplicable

- **Severity:** MINOR
- **File:** `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ProgressGalleryScreen.kt:231-243`
- **Problem:** Pure helper; if reused on iOS / commonMain, would belong in shared. Currently Android-only, but the project has other formatting in `commonMain`.
- **Fix:** Move to `shared/src/commonMain/.../format/Numbers.kt` for reuse and consistency.

---

## INFO

### I-01 — `PhotoCaptureLauncher.ios` uses `UIImagePickerController` for camera, deprecated path on iOS 14+

- **Severity:** INFO
- **File:** `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.ios.kt:64-66`
- **Note:** UIImagePickerController is still supported but Apple recommends `AVCapturePhotoOutput` / `PHPickerViewController`-style flows for modern apps. For a prototype this is acceptable. Long-term: migrate to AVFoundation for camera or document the choice in the iOS handoff.

### I-02 — `BiometricGate.ios` short-circuits on `LAErrorPasscodeNotSet` per D-17-16, but does not log the choice

- **Severity:** INFO
- **File:** `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.ios.kt:54-58`
- **Note:** A dev/debug log here would help diagnose user reports of "the photos open without auth" (which is intended on no-passcode devices). No action needed.

### I-03 — `progressGalleryModule` registers `NutritionGoalDayPolicy` as a `single { NutritionGoalDayPolicy }` of an `object`

- **Severity:** INFO
- **File:** `shared/src/commonMain/kotlin/com/pumpernickel/di/ProgressGalleryModule.kt:48`
- **Note:** Registering a Kotlin `object` in Koin is unusual but works. If the policy is consumed from any other module in the future, prefer moving the binding to `gamificationModule` so it sits next to the gamification consumers.

---

## File Group Summary

### Database / persistence
- `ProgressPictureEntity.kt` — clean.
- `ProgressPictureDao.kt` — clean (volume sum LEFT JOIN behavior is correct, EXISTS gates by photos).
- `AppDatabase.kt` — clean, AutoMigration 8→9 is additive and safe.
- `ProgressPictureRepository.kt` — see m-01 (note division), B-01 (path validation needed).

### Shared ViewModels
- `ProgressGalleryViewModel.kt` — see M-03 (Flow recomposition + cold-start hang).
- `ProgressViewerViewModel.kt` — `_unlockedWorkoutId` gate verified correct (writes at lines 79 + 95 only).
- `ProgressPicturePromptViewModel.kt` — clean.
- `WorkoutSessionViewModel.kt` — see B-02 (`photoCount` never updated).

### Platform actuals
- iOS PhotoVault — see B-01, m-04.
- iOS BiometricGate — clean (I-02).
- iOS PhotoCaptureLauncher — see M-04, M-05, I-01.
- Android PhotoVault — see B-01, m-06.
- Android BiometricGate — see M-07, m-03.
- Android PhotoCaptureLauncher — see B-03, M-01, M-02, M-06.

### DI
- `ProgressGalleryModule.kt` — clean (I-03).
- `PlatformModule.android/ios.kt` — clean.
- iOS KoinHelpers — clean.

### Android UI
- `ProgressGalleryScreen.kt` — see M-08, M-09, m-07.
- `ProgressViewerScreen.kt` — see M-08, m-02.
- `ProgressPicturePromptCard.kt` — see m-05.
- `MainActivity.kt` — see M-06.

### Manifests / resources
- `AndroidManifest.xml`, `backup_rules.xml`, `data_extraction_rules.xml`, `file_paths.xml` — clean.
- `Info.plist` — clean (NSPhotoLibraryUsageDescription + NSFaceIDUsageDescription added).

---

_Reviewed: 2026-05-01_
_Reviewer: Claude (gsd-code-reviewer, advisory)_
_Depth: standard_
