---
phase: 17
title: Progress-pic feature with biometric-locked gallery
status: passed
threats_total: 4
threats_closed: 4
threats_open: 0
asvs_level: 1
audited_at: 2026-05-01
---

# Phase 17 Security Audit

## Frontmatter Summary

- **Status:** passed (with one residual-risk note on iOS UI being out-of-scope for Kotlin code; the Info.plist contract is intact)
- **Threats verified:** 4 / 4 mitigations CLOSED
- **Unregistered flags:** none

---

## Threat Verification

### T-PHOTO-EXFIL — Information Disclosure (mitigate)

**Status:** mitigated

**Threat:** Local attacker / sibling app reads photo bytes from disk.

**Expected:** App-private storage only. iOS `<Documents>/progress_pics/` with `NSFileProtectionComplete`; Android `<filesDir>/progress_pics/`.

**Evidence:**

| Platform | File | Line(s) | Mitigation |
|---|---|---|---|
| iOS | `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.ios.kt` | 38-46, 49-58 | Resolves `NSDocumentDirectory` + `NSUserDomainMask`, appends `progress_pics/` |
| iOS | `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.ios.kt` | 70 | `writeOptions = NSDataWritingAtomic or NSDataWritingFileProtectionComplete` — bytes encrypted at rest while device locked |
| Android | `shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.android.kt` | 22-23 | `File(context.filesDir, "progress_pics").apply { mkdirs() }` — per-app sandbox, not readable by sibling apps without root |
| Android | `shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.android.kt` | 26-30 | Write target is unconditionally `File(rootDir, "$id.jpg")` — no public-storage path exists in the actual class |

**Residual risk:** Rooted/jailbroken device with passcode-bypass capability (deferred per CONTEXT line 219 — strict-vault tier accepted as deferred). On iOS, `NSFileProtectionComplete` raises the bar to "device-locked = unreadable"; on Android the file lives behind the per-app sandbox and is encrypted at rest with the user's lockscreen credential on Android 10+ via FBE.

---

### T-CLOUD-LEAK — Information Disclosure (mitigate)

**Status:** mitigated

**Threat:** Photos exfiltrated via Google Drive backup / ADB backup / iCloud / device transfer.

**Expected (iOS):** `URLResourceValues.isExcludedFromBackupKey = true` per file; `Info.plist` keeps `UIFileSharingEnabled = false` and does NOT set `LSSupportsOpeningDocumentsInPlace`.

**Expected (Android):** `dataExtractionRules` (12+) + `fullBackupContent` (11-) excluding `progress_pics/`; `android:allowBackup` MUST stay `true` per D-17-06.

**Evidence:**

| Platform | File | Line(s) | Mitigation |
|---|---|---|---|
| iOS | `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.ios.kt` | 86-93 | Each saved file is marked `NSURLIsExcludedFromBackupKey = true` after write |
| iOS | `iosApp/iosApp/Info.plist` | 1-14 | Plist contains only `CADisableMinimumFrameDurationOnPhone`, `NSCameraUsageDescription`, `NSPhotoLibraryUsageDescription`, `NSFaceIDUsageDescription`. **`UIFileSharingEnabled` and `LSSupportsOpeningDocumentsInPlace` are absent (verified via `grep`)** — confirms the negative requirement |
| Android | `androidApp/src/androidMain/AndroidManifest.xml` | 11 | `android:allowBackup="true"` retained per D-17-06 (NOT flipped to false) |
| Android | `androidApp/src/androidMain/AndroidManifest.xml` | 15-16 | `<application>` references `android:dataExtractionRules="@xml/data_extraction_rules"` and `android:fullBackupContent="@xml/backup_rules"` |
| Android | `androidApp/src/androidMain/res/xml/data_extraction_rules.xml` | 1-9 | Both `<cloud-backup>` and `<device-transfer>` exclude `domain="file" path="progress_pics/"` |
| Android | `androidApp/src/androidMain/res/xml/backup_rules.xml` | 1-4 | `<full-backup-content>` excludes `domain="file" path="progress_pics/"` |

**Residual risk:** none significant. The defence is layered (per-file iOS flag + manifest exclusion on Android). A user manually toggling iCloud Documents-and-Data on for the app would still NOT capture excluded files because the per-file `isExcludedFromBackupKey` is honoured by both iCloud Backup and iTunes/Finder backups.

---

### T-FILE-SHARING — Information Disclosure (mitigate)

**Status:** mitigated

**Threat:** Photos accidentally exposed via FileProvider / share sheets / Files.app browsing.

**Expected (iOS):** No `LSSupportsOpeningDocumentsInPlace`, `UIFileSharingEnabled` not true, no share sheet wired.

**Expected (Android):** FileProvider scoped narrowly; no `progress_pics/` exposure; no `ACTION_SEND` intents wired.

**Evidence:**

| Platform | File | Line(s) | Mitigation |
|---|---|---|---|
| iOS | `iosApp/iosApp/Info.plist` | 1-14 | `LSSupportsOpeningDocumentsInPlace` absent; `UIFileSharingEnabled` absent; both confirmed via grep |
| iOS | `iosApp/iosApp/Views/Overview/` | (no `ProgressGalleryView.swift` / `ProgressViewerView.swift` yet) | iOS gallery UI is the user's deliverable per `17-IOS-HANDOFF.md` (D-17-18). The handoff document explicitly enumerates the negative requirements (no share sheet, no Photos library writes, no Files.app exposure) — see 17-08-SUMMARY threat-flags note. **No share-API code exists in any present iOS view**; grep for `UIActivityViewController`/`ShareLink`/`UIDocumentInteractionController` in `iosApp/iosApp/Views` returned zero hits. |
| Android | `androidApp/src/androidMain/AndroidManifest.xml` | 28-36 | `FileProvider` is declared with `android:exported="false"` and `android:authorities="${applicationId}.provider"` |
| Android | `androidApp/src/androidMain/res/xml/file_paths.xml` | 1-4 | FileProvider scope is **narrow**: only `<cache-path name="capture" path="capture/" />`. The vault directory `progress_pics/` (under `filesDir`) is NOT mapped — sibling apps cannot obtain a content URI to it via the provider. The `capture/` cache path is for the transient camera-output handoff only |
| Android | `androidApp/src/...` (gallery + viewer screens) | n/a | grep for `ACTION_SEND`, `Intent.ACTION`, `createChooser` across `androidApp` returned zero hits — no share sheet wired into the gallery/viewer/prompt code paths |

**Residual risk:** The transient camera-capture cache file at `cacheDir/capture/{uuid}.jpg` is exposed to the system camera app via FileProvider for the duration of the capture intent (necessary). It is plain bytes, app-private, and not actively cleaned by Phase 17 — accepted as `T-17-03-01` in plan 17-03's threat register and explicitly carried forward.

---

### T-BIOMETRIC-BYPASS — Spoofing (mitigate)

**Status:** mitigated

**Threat:** Tile-tap renders unblurred photo without successful biometric auth.

**Expected:** Single `_unlockedWorkoutId` gate in `ProgressViewerViewModel` with EXACTLY two write sites (Success, dismiss/relock). iOS `LAContext.deviceOwnerAuthentication` (NOT `.deviceOwnerAuthenticationWithBiometrics`); Android `BiometricPrompt` with `setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)`. No-credential resolves Success per D-17-16 (intentional).

**Evidence — gate state machine:**

| File | Line | Operation |
|---|---|---|
| `shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressViewerViewModel.kt` | 40 | `private val _unlockedWorkoutId = MutableStateFlow<Long?>(null)` — declaration, initial null |
| `shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressViewerViewModel.kt` | 79 | **Write site 1:** `is UnlockResult.Success -> _unlockedWorkoutId.value = workoutId` (only inside `requestUnlock` after `biometricGate.requestUnlock(reason)` returns Success) |
| `shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressViewerViewModel.kt` | 95 | **Write site 2:** `_unlockedWorkoutId.value = null` (inside `relock()`, called by `ProgressViewerScreen.DisposableEffect.onDispose`) |
| `shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressViewerViewModel.kt` | 47, 52 | Read sites only (StateFlow exposure + `combine`) — no other writes |

A repository-wide grep (`grep -rn '_unlockedWorkoutId' shared androidApp iosApp`) confirms NO other code path writes `_unlockedWorkoutId`. The two write sites match the plan exactly.

**Evidence — viewer gate read:**

| File | Line | Operation |
|---|---|---|
| `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ProgressViewerScreen.kt` | 86, 91 | Photo render path is gated: `uiState.unlockedWorkoutId == null` short-circuits to `LockedPlaceholder` BEFORE the `HorizontalPager` / `Image` composables run. There is no else-branch that renders unlocked bytes when the gate is null. |
| `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ProgressViewerScreen.kt` | 67-70 | First-composition `LaunchedEffect(Unit) { viewModel.requestUnlock() }` — no path to viewer that skips this call |
| `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ProgressViewerScreen.kt` | 72-78 | `DisposableEffect.onDispose { viewModel.relock() }` — guarantees re-lock on backstack pop |

**Evidence — auth API selection:**

| Platform | File | Line | Mitigation |
|---|---|---|---|
| iOS | `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.ios.kt` | 51, 75 | `LAPolicyDeviceOwnerAuthentication` used in BOTH `canEvaluatePolicy` and `evaluatePolicy` calls. The biometric-only sibling `LAPolicyDeviceOwnerAuthenticationWithBiometrics` is NOT imported and NOT referenced (verified via grep) |
| iOS | `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.ios.kt` | 56-58, 86 | `LAErrorPasscodeNotSet` resolves to `UnlockResult.Success` (D-17-16 — intentional) |
| Android | `shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.android.kt` | 54-56, 104 | `BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL` used in BOTH `canAuthenticate` and `setAllowedAuthenticators` |
| Android | `shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.android.kt` | 61-66 | No-credential outcomes (`BIOMETRIC_ERROR_NONE_ENROLLED`, `BIOMETRIC_ERROR_NO_HARDWARE`, `BIOMETRIC_ERROR_HW_UNAVAILABLE`) resolve to `UnlockResult.Success` (D-17-16) |

**Residual risk:** D-17-16 ("no-credential device → free access") is INTENTIONAL per the threat-model declaration. It is documented at the source-code level and at the CONTEXT level. Not a bypass — accepted by the user.

A second residual: `T-17-06-01` (bitmap GPU-cache persistence after `relock()`) and `T-17-06-02` (recents-screen screenshot — `FLAG_SECURE` not set) were declared `accept` in plan 17-06 and remain accepted. Their disposition was explicit; on-disk hardening (Phase 17-03 / 17-04) is the load-bearing protection.

---

## Unregistered Flags

None. The eight summaries (17-01 through 17-08) all declare "no threat flags raised" in their `## Threat Flags` sections; every new attack surface introduced during implementation maps to a threat ID already in the phase-level register.

---

## Accepted Risks Log

The following are accepted with documented disposition — not blockers:

| ID | Plan | Disposition | Note |
|---|---|---|---|
| T-17-03-01 | 17-03 | accept | Camera-capture cache file in `cacheDir/capture/` not actively cleaned; bytes are still re-written into vault. App-private. |
| T-17-03-02 | 17-03 | accept | Resize loses metadata; original bytes not preserved (D-17-07 explicit). |
| T-17-04-01/02/03 | 17-04 | accept | iOS-side Phase 17-04-specific risks accepted in plan body. |
| T-17-05-01/02 | 17-05 | accept | Capture-prompt VM tampering / spoofing accepted. |
| T-17-06-01 | 17-06 | accept | Bitmap GPU-cache persistence — Compose `remember(bytes)` releases on disposal. |
| T-17-06-02 | 17-06 | accept | Recents-screen screenshot — `FLAG_SECURE` deferred. |
| T-17-07-01/02 | 17-07 | accept | Koin testing-rebinding + lifecycle-scope drift accepted. |
| T-17-08-01/02 | 17-08 | accept | iOS handoff doc copy/paste leak risk + future-maintainer drift accepted. |
| (deferred) | CONTEXT | accept | Strict-vault tier (app-managed AES + biometric-bound key) deferred per CONTEXT line 219. Defends against jailbroken/rooted device byte extraction. |

---

## Summary

All four declared threats (T-PHOTO-EXFIL, T-CLOUD-LEAK, T-FILE-SHARING, T-BIOMETRIC-BYPASS) have grep-verified mitigations at the file:line level. The single explicit auth gate `_unlockedWorkoutId` has exactly two write sites as specified. iOS `Info.plist` is clean of the two forbidden keys (`UIFileSharingEnabled`, `LSSupportsOpeningDocumentsInPlace`). Android FileProvider scope is narrow (cache only, not the vault directory). `android:allowBackup` remains `true` per D-17-06 with fine-grained exclusions doing the work. No source-code changes are required.

The iOS UI surfaces (`ProgressGalleryView.swift`, `ProgressViewerView.swift`) are intentionally not part of this audit's code-coverage check — per D-17-18 they are the user's hand-written deliverable tracked via `17-IOS-HANDOFF.md`. The Kotlin contract those views must consume (single explicit gate in `ProgressViewerViewModel`, `LAContext.deviceOwnerAuthentication`, etc.) is intact and ready for them.
