---
phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work
plan: 03
subsystem: android-platform
tags: [android, kmp, expect-actual, biometric, file-io, manifest, backup-rules, fileprovider, biometricprompt, photopicker]

# Dependency graph
requires:
  - phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work
    plan: 02
    provides: "expect class PhotoVault / PhotoCaptureLauncher / BiometricGate + sealed UnlockResult — the contracts this plan implements on Android"
provides:
  - "actual class PhotoVault on Android — File I/O against context.filesDir/progress_pics/"
  - "actual class BiometricGate on Android — BiometricPrompt with BIOMETRIC_STRONG OR DEVICE_CREDENTIAL + no-credential = Success short-circuit (D-17-16)"
  - "actual class PhotoCaptureLauncher on Android — TakePicture + PickVisualMedia, returns 1600px long-edge JPEG quality 0.8"
  - "BiometricGateActivityHolder — singleton holder for the FragmentActivity used by BiometricPrompt (MainActivity attaches in 17-05)"
  - "PhotoCaptureLauncherActivityHolder + PhotoCaptureLauncherHost — symmetric holder for ComponentActivity + pre-registered ActivityResultLaunchers"
  - "Manifest-layer T-CLOUD-LEAK mitigation: backup_rules.xml + data_extraction_rules.xml exclude progress_pics/"
  - "FileProvider <provider> + file_paths.xml — wired for the TakePicture flow (cache-path 'capture/')"
  - "androidx.biometric:biometric:1.2.0-alpha05 on the classpath for both shared/androidMain and androidApp"
affects:
  - "17-05-PLAN — MainActivity onCreate must call BiometricGateActivityHolder.attach(this) + PhotoCaptureLauncherActivityHolder.attach(host) and detach symmetrically; MUST swap MainActivity superclass from ComponentActivity to androidx.fragment.app.FragmentActivity (BiometricPrompt requires it)"
  - "17-04-PLAN (iOS actuals) — independent, no cross-plan coupling"
  - "17-08-PLAN (Koin DI) — single<PhotoVault>, single<BiometricGate>, single<PhotoCaptureLauncher> bind via androidContext() in PlatformModule.android.kt"

# Tech tracking
tech-stack:
  added:
    - "androidx.biometric:biometric:1.2.0-alpha05 (libs.androidx.biometric in libs.versions.toml; declared on both androidApp and shared/androidMain)"
  patterns:
    - "ActivityHolder singleton (object with @Volatile var current + attach/detach) — bridges per-Activity Android APIs (BiometricPrompt, ActivityResultLauncher) into a Koin-managed singleton without leaking Activity references; the host MainActivity owns the lifecycle"
    - "PhotoCaptureLauncherHost — separate class from the actual itself, instantiated by the Activity in onCreate so registerForActivityResult runs before STARTED state per Activity Result API contract"
    - "CompletableDeferred bridges callback-based platform APIs (BiometricPrompt.AuthenticationCallback, ActivityResultLauncher) into suspend functions"
    - "Bitmap resize pipeline: BitmapFactory.decodeByteArray -> createScaledBitmap (filter=true) capped at 1600px long-edge -> compress(JPEG, 80) -> ByteArray (D-17-07)"
    - "Manifest-layer backup exclusion via fine-grained dataExtractionRules + fullBackupContent — keeps android:allowBackup=true for unrelated app data while excluding progress_pics/ from cloud backup + device transfer (D-17-06)"

key-files:
  created:
    - "shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.android.kt"
    - "shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.android.kt"
    - "shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.android.kt"
    - "androidApp/src/androidMain/res/xml/backup_rules.xml"
    - "androidApp/src/androidMain/res/xml/data_extraction_rules.xml"
    - "androidApp/src/androidMain/res/xml/file_paths.xml"
  modified:
    - "gradle/libs.versions.toml (biometric version + library entry)"
    - "androidApp/build.gradle.kts (libs.androidx.biometric implementation)"
    - "shared/build.gradle.kts (libs.androidx.biometric on androidMain — required for BiometricGate.android.kt to compile; deviation Rule 3)"
    - "androidApp/src/androidMain/AndroidManifest.xml (dataExtractionRules + fullBackupContent attrs, USE_BIOMETRIC permission, FileProvider <provider>)"

key-decisions:
  - "biometric library added to BOTH shared/androidMain AND androidApp. Plan 17-03 only specified androidApp; however the actual class BiometricGate.android.kt lives in shared/androidMain so the shared compile classpath needs it. This is the only way :shared:compileDebugKotlinAndroid resolves androidx.biometric.* imports — confirmed by the explicit verification command in the plan."
  - "FileProvider was NOT pre-wired in the manifest from any prior phase (CameraX barcode flow uses Surface + ImageProxy, not FileProvider URIs). Added a fresh <provider> entry pointing at @xml/file_paths plus the file_paths.xml resource (cache-path name='capture' path='capture/'). Plan 17-05's MainActivity wiring does not need to add anything FileProvider-related — it's all here."
  - "ActivityHolder pattern, not Koin-injected Activity. Koin's androidContext() returns Application (not the foreground Activity). BiometricPrompt + ActivityResultLauncher both need an Activity reference, so the executor introduced two singleton holders (BiometricGateActivityHolder, PhotoCaptureLauncherActivityHolder) that MainActivity attaches/detaches around its lifecycle. Symmetric pattern across the two services — one consistent setup hook in MainActivity.onCreate."
  - "Negative-button-text intentionally not set on BiometricPrompt.PromptInfo.Builder. When DEVICE_CREDENTIAL is part of allowedAuthenticators, calling that builder option throws IllegalArgumentException at build() time. Set ONLY title + allowedAuthenticators per D-17-15."
  - "USE_BIOMETRIC permission added to manifest. The androidx.biometric library declares this permission via its own AndroidManifest, but declaring it explicitly in the app manifest makes the dependency intent obvious to anyone reading the manifest and is harmless when manifest-merger sees the same permission declared twice (it dedupes)."
  - "Quality 80 (not 0.8) — the Plan's text mentions 'quality 0.8' as the spec but Bitmap.compress takes an integer 0..100. The acceptance check pins the literal '/* quality = */ 80' inline comment."

requirements-completed:
  - D-17-03
  - D-17-05
  - D-17-06
  - D-17-07
  - D-17-15
  - D-17-16
  - D-17-17
  - D-17-19

# Metrics
duration: 6min 5sec
completed: 2026-05-01
---

# Phase 17 Plan 03: Android actuals — PhotoVault / BiometricGate / PhotoCaptureLauncher + OS hardening Summary

**Three new Kotlin actual classes in shared/androidMain — file I/O over context.filesDir/progress_pics/, BiometricPrompt with BIOMETRIC_STRONG | DEVICE_CREDENTIAL and no-credential-device free-pass (D-17-16), system camera + photo picker returning 1600px-long-edge JPEG quality 0.8 — plus three new XML resources, a FileProvider <provider>, manifest backup-exclusion attrs, and a new androidx.biometric dependency on both shared and androidApp classpaths. T-PHOTO-EXFIL + T-CLOUD-LEAK are mitigated at the manifest layer; `:androidApp:assembleDebug` exits 0.**

## Performance

- **Duration:** 6 min 5 sec
- **Started:** 2026-05-01T15:25:56Z
- **Completed:** 2026-05-01T15:32:01Z
- **Tasks:** 4 (all atomic commits)
- **Files created:** 6 (3 Kotlin actuals, 3 XML resources)
- **Files modified:** 4 (libs.versions.toml, androidApp/build.gradle.kts, shared/build.gradle.kts, AndroidManifest.xml)

## Accomplishments

- **PhotoVault.android.kt** wraps `java.io.File` against `context.filesDir/progress_pics/` with all four expect methods running through `withContext(Dispatchers.IO)`. Returns the canonical relative path `"progress_pics/{id}.jpg"` from `write(...)` so the DB row stores a path the platform code can resolve back to a full file. App-private storage is the OS-level T-PHOTO-EXFIL mitigation — sibling apps cannot read it without root.
- **BiometricGate.android.kt** uses `androidx.biometric.BiometricPrompt` with `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` exactly per D-17-15. The no-credential branch (`BIOMETRIC_ERROR_NONE_ENROLLED` / `NO_HARDWARE` / `HW_UNAVAILABLE`) short-circuits to `UnlockResult.Success` per D-17-16 — the user with no device security is not blocked. Failure (`onAuthenticationFailed`) is silent per D-17-17; only ERROR / SUCCESS callbacks complete the deferred. The negative-button-text builder option is intentionally not called (would throw `IllegalArgumentException` when `DEVICE_CREDENTIAL` is in `allowedAuthenticators`).
- **PhotoCaptureLauncher.android.kt** ships the `PhotoCaptureLauncherActivityHolder` + `PhotoCaptureLauncherHost` pair: `Host` is what MainActivity instantiates in `onCreate` (so `registerForActivityResult` runs before STARTED, per Activity Result API contract), and the holder is what the Koin-managed actual reads. `TakePicture()` writes to a FileProvider URI in `cacheDir/capture/`; `PickVisualMedia(ImageOnly)` returns a `content://` URI. Both URIs are read into `ByteArray` via `readUriBytes` (handles both schemes), then `BitmapFactory.decodeByteArray -> createScaledBitmap (filter=true) -> Bitmap.compress(JPEG, 80)` resizes the long edge to 1600px and re-encodes (D-17-07).
- **Manifest-layer T-CLOUD-LEAK mitigation:** `backup_rules.xml` (Android 11- via `fullBackupContent`) and `data_extraction_rules.xml` (Android 12+ via `dataExtractionRules`, both `cloud-backup` and `device-transfer` blocks) exclude `progress_pics/` so the photo files are NOT eligible for Auto Backup to Google Drive or device-to-device transfer. `android:allowBackup="true"` is preserved per D-17-06 — global flip to false would break legitimate backups of unrelated app data.
- **FileProvider wiring:** the prior CameraX barcode flow used `Surface + ImageProxy`, never FileProvider URIs — so this plan introduces the manifest `<provider>` entry pointing at `@xml/file_paths` and the `file_paths.xml` resource (`cache-path name="capture" path="capture/"`). 17-05's MainActivity wiring does not need to add anything FileProvider-related; this plan owns it.
- **`androidx.biometric:biometric:1.2.0-alpha05`** on both `androidApp` (per plan) AND `shared/androidMain` (Rule 3 deviation — required for `BiometricGate.android.kt` to compile against `androidx.biometric.*` imports).
- **Builds clean:** `:androidApp:assembleDebug -q` exits 0; `:shared:compileDebugKotlinAndroid -q` exits 0 — both verifications passed.

## Task Commits

Each task committed atomically with `--no-verify` (worktree convention):

1. **Task 1: Add androidx.biometric dependency + Android OS hardening config** — `769f96c` (chore)
2. **Task 2: Implement actual class PhotoVault for Android** — `2ff4261` (feat)
3. **Task 3: Implement actual class BiometricGate for Android with BiometricPrompt** — `0d6c732` (feat)
4. **Task 4: Implement actual class PhotoCaptureLauncher for Android** — `018e4bd` (feat)

## Files Created/Modified

### Created (Kotlin)

- `shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.android.kt` — `actual class PhotoVault(private val context: Context)`. 50 lines including KDoc; four suspend methods (`write/read/delete/deleteAll`) all wrapped in `withContext(Dispatchers.IO)`; `rootDir` lazy-creates `progress_pics/` under `filesDir`.
- `shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.android.kt` — `object BiometricGateActivityHolder` (volatile FragmentActivity holder with attach/detach) + `actual class BiometricGate(private val context: Context)`. 110 lines. `requestUnlock` runs on `Dispatchers.Main` (BiometricPrompt requires main-thread invocation), reads activity from holder, queries `BiometricManager.canAuthenticate` to short-circuit no-credential devices to `Success` (D-17-16), otherwise builds `BiometricPrompt` with `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` and bridges its callback to a `CompletableDeferred<UnlockResult>`.
- `shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.android.kt` — `object PhotoCaptureLauncherActivityHolder` + `class PhotoCaptureLauncherHost(activity, context)` (owns the `cameraLauncher` + `libraryLauncher` registrations) + `actual class PhotoCaptureLauncher(private val context: Context)`. 153 lines. `launchCamera` creates a UUID-named cache file, hands its FileProvider URI to `TakePicture`; `launchLibrary` launches `PickVisualMedia(ImageOnly)`. Result URIs flow through `readUriBytes` (handles both `file:` and `content:` schemes) and `resizeAndEncode` (1600px long-edge cap, JPEG quality 80).

### Created (XML resources)

- `androidApp/src/androidMain/res/xml/backup_rules.xml` — `<full-backup-content>` with one `<exclude domain="file" path="progress_pics/" />`. Android 11- backup framework reads this via `android:fullBackupContent`.
- `androidApp/src/androidMain/res/xml/data_extraction_rules.xml` — `<data-extraction-rules>` with `<cloud-backup>` AND `<device-transfer>` blocks, each excluding `progress_pics/`. Android 12+ backup framework reads this via `android:dataExtractionRules`.
- `androidApp/src/androidMain/res/xml/file_paths.xml` — `<paths>` with one `<cache-path name="capture" path="capture/" />`. Read by FileProvider for the TakePicture URI grant.

### Modified

- `gradle/libs.versions.toml` — added `biometric = "1.2.0-alpha05"` under `[versions]` and `androidx-biometric = { module = "androidx.biometric:biometric", version.ref = "biometric" }` under `[libraries]`.
- `androidApp/build.gradle.kts` — `implementation(libs.androidx.biometric)` added to the androidMain dependencies block alongside the existing camerax block.
- `shared/build.gradle.kts` — `implementation(libs.androidx.biometric)` added to `androidMain.dependencies` alongside `koin.android` + `ktor.client.okhttp`. **Required because `BiometricGate.android.kt` lives in shared/androidMain and the shared module compiles independently — adding biometric only to androidApp would not put it on shared's compile classpath.**
- `androidApp/src/androidMain/AndroidManifest.xml` — added `<uses-permission android:name="android.permission.USE_BIOMETRIC" />`; added `android:dataExtractionRules="@xml/data_extraction_rules"` + `android:fullBackupContent="@xml/backup_rules"` to `<application>`; preserved `android:allowBackup="true"` per D-17-06; added `<provider android:name="androidx.core.content.FileProvider" android:authorities="${applicationId}.provider" android:exported="false" android:grantUriPermissions="true">` with `<meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths" />` for the TakePicture flow.

## Decisions Made

- **Biometric library on shared/androidMain (not just androidApp).** The plan's Task 1 only adds the dep to `androidApp/build.gradle.kts`, but `actual class BiometricGate.android.kt` lives in `shared/src/androidMain/`. The shared module compiles independently of androidApp — adding the dep only to androidApp does NOT put it on shared's compile classpath, so the `androidx.biometric.*` imports in `BiometricGate.android.kt` would fail to resolve, and `:shared:compileDebugKotlinAndroid` (the plan's own verify command for Task 3) would fail. Adding it to both is the only working configuration. Documented as a Rule 3 (blocking) deviation.
- **FileProvider added in this plan, not deferred to 17-05.** The plan's Task 4 instructed: "if `grep -q 'androidx.core.content.FileProvider' androidApp/src/androidMain/AndroidManifest.xml` fails, add a `<provider>` entry … and document the addition." That grep does fail (no prior phase wired FileProvider), so this plan adds the `<provider>` entry + `file_paths.xml`. 17-05's MainActivity wiring does NOT need to touch FileProvider config.
- **USE_BIOMETRIC permission declared explicitly in manifest.** `androidx.biometric` declares this in its own manifest, so manifest-merger would supply it transitively, but declaring it in the app manifest makes the security intent obvious at code-review time. Manifest-merger dedupes duplicate permission declarations cleanly.
- **`PhotoCaptureLauncherHost` extracted as a separate class.** The plan's pattern recommends a single object holder, but `registerForActivityResult` MUST be called before the Activity reaches STARTED. Keeping the registration code inside an inner class that the Activity instantiates in `onCreate` (before `setContent`) is the pattern that respects this contract. The holder then just stores the host instance.
- **CameraX is on the classpath but NOT used.** The plan's CONTEXT discretion (line 22) says CameraX is "available but not required." `TakePicture` is the simpler API for a single still — no preview UI, no lifecycle owner threading, no SurfaceProvider plumbing. CameraX continues to serve the barcode scanner exclusively.
- **Quality 80 (integer), not 0.8.** Spec wording is "quality 0.8" (D-17-07) but `Bitmap.compress` takes an `Int` in 0..100. Used `80` with the inline comment `/* quality = */ 80` per the plan's acceptance check.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added `androidx-biometric` to `shared/build.gradle.kts` androidMain dependencies**

- **Found during:** Task 3 implementation (writing `BiometricGate.android.kt`)
- **Issue:** The plan's Task 1 only specifies adding `implementation(libs.androidx.biometric)` to `androidApp/build.gradle.kts`. However, `BiometricGate.android.kt` (Task 3) lives in `shared/src/androidMain/` and imports `androidx.biometric.BiometricManager` + `androidx.biometric.BiometricPrompt`. The shared module compiles independently — its androidMain classpath is what `:shared:compileDebugKotlinAndroid` resolves against. Without the dep on shared, the imports fail and the plan's own verify command (Task 3 line 418) fails.
- **Fix:** Added `implementation(libs.androidx.biometric)` to `shared/build.gradle.kts` androidMain.dependencies block alongside `koin.android` + `ktor.client.okhttp`.
- **Files modified:** `shared/build.gradle.kts`
- **Committed in:** `769f96c` (Task 1 — bundled with the plan-specified androidApp dep change since both are dependency wiring)
- **Verification:** `:shared:compileDebugKotlinAndroid -q` exits 0 after Task 4 (when all three actuals are present).

**2. [Rule 2 - Critical] Added `<uses-permission android:name="android.permission.USE_BIOMETRIC" />` to AndroidManifest.xml**

- **Found during:** Task 1 manifest edit
- **Issue:** The plan does NOT mention declaring `USE_BIOMETRIC`, but it's the standard permission for `BiometricPrompt` invocations. The `androidx.biometric` library does declare it in its own manifest (manifest-merger would cover it), but declaring it in the app manifest makes the security intent visible to reviewers and to `lint`'s `MissingPermission` checks, and is harmless if duplicated (manifest-merger dedupes).
- **Fix:** Added `<uses-permission android:name="android.permission.USE_BIOMETRIC" />` alongside the existing `CAMERA` + `INTERNET` permissions.
- **Files modified:** `androidApp/src/androidMain/AndroidManifest.xml`
- **Committed in:** `769f96c` (Task 1 — bundled with the rest of the manifest edits)

**3. [Rule 3 - Environment] Restored Room schema JSONs in worktree's gitignored `shared/schemas/`**

- **Found during:** Pre-Task-1 environment check (same root cause as 17-01 / 17-02 deviations)
- **Issue:** The worktree's `shared/schemas/com.pumpernickel.data.db.AppDatabase/` directory was empty. Room's KSP needs schemas `2.json` through `8.json` (and writes `9.json`) on disk to validate the existing AutoMigrations. Without them, `:androidApp:assembleDebug` fails at the KSP step before reaching the Kotlin compile step.
- **Fix:** Copied `2.json` through `8.json` from the main repo's `shared/schemas/com.pumpernickel.data.db.AppDatabase/` into the worktree. `shared/schemas/` is gitignored (verified with `git check-ignore`), so this fix is purely a local KSP working-set repair and never enters the commit graph.
- **Files modified:** `shared/schemas/com.pumpernickel.data.db.AppDatabase/{2..8}.json` (gitignored — not committed)
- **Verification:** Both `:androidApp:assembleDebug` and `:shared:compileDebugKotlinAndroid` exit 0.
- **Committed in:** N/A (gitignored — does not enter the commit graph)

**4. [Rule 1 - Plan-text-fix] BiometricGate comment originally contained the literal string `setNegativeButtonText` (which broke acceptance check 8)**

- **Found during:** Task 3 acceptance verification (check 8: `! grep -q 'setNegativeButtonText' BiometricGate.android.kt`)
- **Issue:** The plan's example code at lines 411-413 explicitly says "`BiometricPrompt.PromptInfo.Builder().setNegativeButtonText(...)` is intentionally NOT called …" as a comment intended to land in the source. But the acceptance check (line 428) is `! grep -q 'setNegativeButtonText'` — i.e., the literal string must NOT appear anywhere in the file, including comments. The plan's example code and acceptance check contradict each other.
- **Fix:** Rewrote the comment to convey the same warning without the literal API name: "the negative-button-text builder option is intentionally NOT called. When DEVICE_CREDENTIAL is part of the allowed authenticators, that option throws IllegalArgumentException at build() time. Set ONLY title + allowedAuthenticators."
- **Files modified:** `shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.android.kt`
- **Committed in:** Folded into the original Task 3 commit `0d6c732` (the edit was applied before commit).
- **Verification:** Acceptance check 8 (`! grep -q 'setNegativeButtonText'`) now PASSES.

---

**Total deviations:** 4 auto-fixed (one Rule 3 dep wiring, one Rule 2 permission, one Rule 3 environment, one Rule 1 plan-text contradiction). None touch the source-code logic of the actual classes — they're all build wiring or comment phrasing. No scope creep; plan source-code spec executed exactly as written for the actual class bodies.

## Issues Encountered

- None beyond the four auto-fixed deviations above.
- The `:androidApp:assembleDebug` verification command in Task 1 cannot pass in isolation (the actual classes haven't been written yet) — same intrinsic limitation 17-02 documented for `:shared:compileKotlinIosSimulatorArm64`. Verified via dependency-resolution check (`./gradlew :androidApp:dependencies | grep biometric` confirmed `androidx.biometric:biometric:1.2.0-alpha05` resolved cleanly) instead. After all four tasks ship, both `:androidApp:assembleDebug` AND `:shared:compileDebugKotlinAndroid` exit 0.

## TDD Gate Compliance

Plan type: `execute` (not TDD). No RED/GREEN gate required.

## User Setup Required

None. Manifest changes auto-apply on next build. Once the user installs the next debug APK on a real device:
- The OS will respect `dataExtractionRules` immediately (Android 12+ Auto Backup will skip `progress_pics/`).
- `BiometricPrompt` will show the system biometric/passcode UI on the first tile-tap (post 17-06 / 17-07 wiring) — but only after MainActivity is attached to `BiometricGateActivityHolder` (plan 17-05's responsibility) AND the MainActivity superclass is swapped from `ComponentActivity` to `androidx.fragment.app.FragmentActivity`.

## Threat Flags

None — the plan's threat register (T-PHOTO-EXFIL, T-CLOUD-LEAK, T-BIOMETRIC-BYPASS, T-17-03-01, T-17-03-02) covers every new surface introduced in this plan. T-PHOTO-EXFIL + T-CLOUD-LEAK are now mitigated at the manifest layer (the plan's stated goal); T-BIOMETRIC-BYPASS is mitigated at the API level (`BiometricGate.requestUnlock` returns a typed sealed result; the gate state in 17-06's ProgressViewerViewModel will only flip on `Success`); T-17-03-01 and T-17-03-02 stay accepted per plan disposition.

## Next Plan Readiness

- **Plan 17-04 (iOS actuals)** is independent — no cross-plan coupling. Will land its own iOS actuals against the same locked expect contracts from 17-02.
- **Plan 17-05 (capture flow VM + MainActivity wiring)** has two locked obligations from this plan:
  1. Swap MainActivity superclass from `androidx.activity.ComponentActivity` to `androidx.fragment.app.FragmentActivity` (BiometricPrompt requires it).
  2. In `MainActivity.onCreate`, before `setContent`: call `BiometricGateActivityHolder.attach(this)` and instantiate `PhotoCaptureLauncherHost(this, this)` then `PhotoCaptureLauncherActivityHolder.attach(host)`. In `onDestroy`, symmetrically detach both. The host instantiation MUST run before `setContent` because `registerForActivityResult` requires the Activity to be in CREATED state and not yet STARTED.
- **Plan 17-06 (gallery VM)** consumes `BiometricGate.requestUnlock` directly — the `UnlockResult` sealed class lets the VM `when`-exhaustively branch on `Success → emit nav event`, `Cancelled / Failed → no-op`, `Error(message) → log/toast` per CONTEXT D-17-14 / D-17-17.
- **Plan 17-07 (viewer VM)** consumes `PhotoVault.read` for unblurred bytes after `BiometricGate` returns `Success`.
- **Plan 17-08 (Koin DI)** has the cleanest possible wiring: `single<PhotoVault> { PhotoVault(androidContext()) }`, `single<BiometricGate> { BiometricGate(androidContext()) }`, `single<PhotoCaptureLauncher> { PhotoCaptureLauncher(androidContext()) }` — all in `PlatformModule.android.kt`. No constructor-injected Activity references; all activity wiring goes through the holders.

## Self-Check: PASSED

Verified before returning:

- `[ -f shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.android.kt ]` -> FOUND
- `[ -f shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.android.kt ]` -> FOUND
- `[ -f shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.android.kt ]` -> FOUND
- `[ -f androidApp/src/androidMain/res/xml/backup_rules.xml ]` -> FOUND
- `[ -f androidApp/src/androidMain/res/xml/data_extraction_rules.xml ]` -> FOUND
- `[ -f androidApp/src/androidMain/res/xml/file_paths.xml ]` -> FOUND
- `git log --oneline | grep 769f96c` -> FOUND: `chore(17-03): add androidx.biometric dependency + Android backup-exclusion config`
- `git log --oneline | grep 2ff4261` -> FOUND: `feat(17-03): implement actual class PhotoVault for Android`
- `git log --oneline | grep 0d6c732` -> FOUND: `feat(17-03): implement actual class BiometricGate for Android with BiometricPrompt`
- `git log --oneline | grep 018e4bd` -> FOUND: `feat(17-03): implement actual class PhotoCaptureLauncher for Android`
- All 12 + 5 + 8 + 9 = 34 acceptance-grep checks across the four tasks PASS (verified inline)
- `:androidApp:assembleDebug -q` -> exit 0
- `:shared:compileDebugKotlinAndroid -q` -> exit 0

---
*Phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work*
*Completed: 2026-05-01*
