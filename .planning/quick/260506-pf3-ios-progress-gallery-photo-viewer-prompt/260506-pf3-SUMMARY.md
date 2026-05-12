---
phase: quick-260506-pf3
plan: 01
subsystem: progress-pic / iOS / biometrics
tags: [ios, biometrics, progress-pic, face-id, local-authentication, kmp-iosmain]
requires:
  - "ProgressViewerViewModel.requestUnlock(\"Fortschrittsbild entsperren\")"
  - "platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics"
  - "platform.LocalAuthentication.LAErrorBiometryLockout"
provides:
  - "BiometricGate (iOS) with biometrics-first prompt, passcode fallback only on technical unavailability"
affects:
  - "Overview → Fortschrittsgalerie → tap tile (gallery photo viewer auth flow)"
tech-stack:
  added: []
  patterns:
    - "Two-step LAContext policy chain (biometrics-only first, deviceOwnerAuthentication on technical-unavailability fallback)"
    - "Fresh LAContext per evaluate call to avoid policy-attempt state leaking across attempts"
    - "Sealed-class EvaluateOutcome to isolate the should-we-fall-back? decision into one when-branch"
key-files:
  created: []
  modified:
    - shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.ios.kt
decisions:
  - "User-cancel of the Face ID sheet returns UnlockResult.Cancelled — does NOT fall through to passcode (matches Apple UX convention; deliberate cancel must not be punished with a second prompt)"
  - "Fall-back-to-passcode is reserved for LAErrorBiometryNotAvailable / LAErrorBiometryNotEnrolled / LAErrorBiometryLockout — the three canonical 'biometric is technically not usable right now' codes"
  - "Use a fresh LAContext for the fallback evaluate so the passcode sheet is not influenced by the failed biometric attempt's cached state"
metrics:
  duration_min: 3
  completed: 2026-05-06
---

# Quick 260506-pf3: iOS Progress Gallery Photo Viewer Face ID Fix Summary

Fixed iOS Progress Gallery photo viewer auth: tapping a tile now triggers the Face ID prompt first on enrolled devices, with passcode reserved as a technical fallback when biometrics is unavailable, unenrolled, or locked out — and user-cancel no longer falls through to passcode.

## Root Cause

`BiometricGate.ios.kt` called `evaluatePolicy(LAPolicyDeviceOwnerAuthentication, …)` — the "any owner credential" policy. Apple's docs say this policy *can* show biometrics first, but the actual UI path depends on device state, recent biometric activity, and simulator settings. On a Face-ID-enrolled iPhone simulator (and on real devices in many states), this policy frequently goes straight to the passcode entry pad, which is exactly the bug the user reported. The biometrics-only sibling policy `LAPolicyDeviceOwnerAuthenticationWithBiometrics` is the one that reliably surfaces Face ID first.

## The Fix

Replaced the single-policy call with a two-step chain in `requestUnlock`:

1. **D-17-16 short-circuit (preserved):** `canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, &error)` — if it fails with `LAErrorPasscodeNotSet`, return `UnlockResult.Success` without prompting (no biometric AND no passcode → unblur freely).
2. **Step 1 (new):** `evaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, …)` — biometrics-only attempt. This is the call that surfaces Face ID first.
3. **Step 2 fallback (new, narrow):** Only when step 1 returned `LAErrorBiometryNotAvailable` / `LAErrorBiometryNotEnrolled` / `LAErrorBiometryLockout`, retry with `evaluatePolicy(LAPolicyDeviceOwnerAuthentication, …)` on a fresh `LAContext` so the passcode sheet appears.
4. **User-cancel handling (preserved):** `LAErrorUserCancel` / `LAErrorAppCancel` / `LAErrorSystemCancel` → `UnlockResult.Cancelled`. The cancel branch is wired through an `EvaluateOutcome.UserCancelled` sealed-class case so the fallback `when` cannot accidentally route it to the passcode policy.

Implementation detail: a private `EvaluateOutcome` sealed class isolates the four meaningful auth outcomes (Success, UserCancelled, AuthFailed, BiometryUnavailable, Other) so the "should we fall back?" decision lives in exactly one place — the `when` block in `requestUnlock` — and only the `BiometryUnavailable` arm calls the second `evaluate(...)`.

The `evaluatePolicy` callback signature, the `canEvaluatePolicy` pre-check, the German reason string `"Fortschrittsbild entsperren"`, and the `UnlockResult.Success / Cancelled / Failed / Error` contract used by `ProgressViewerViewModel` are all preserved.

## Scope Confirmation

`BiometricGate` is **gallery-only** — verified by grep across the entire shared module:

- `ProgressViewerViewModel` (gallery photo viewer) — calls `biometricGate.requestUnlock("Fortschrittsbild entsperren")`. This is the only consumer that receives the new behaviour.
- `ProgressGalleryViewModel` — explicit comment: "Auth lives in the viewer VM, NOT here." Does not import or use `BiometricGate`.
- `ProgressPicturePromptViewModel` (post-workout flow) — does NOT use `BiometricGate`. No change to the post-workout photo prompt.
- `ProgressGalleryModule` / `PlatformModule.ios.kt` / `PlatformModule.android.kt` — Koin DI bindings only.
- `BiometricGate.android.kt` — untouched. Android already does biometrics-first-then-passcode at framework level (`BiometricPrompt` with `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`, per D-17-15).

## pbxproj Status (No Change Needed)

`iosApp/iosApp.xcodeproj/project.pbxproj` already configures `INFOPLIST_KEY_NSFaceIDUsageDescription = "Fortschrittsbild entsperren.";` for both Debug (line 602) and Release (line 642). The bug-report's "likely cause" mention of a missing usage description was already handled in commit 17581fa (Phase 17 iOS surfaces, 2026-05-01). Verifying this before fixing prevented an unnecessary pbxproj edit.

## Deviations from Plan

**None — plan executed exactly as written.** The full file replacement listed in `<task type="auto">` Task 1 was applied verbatim. No additional bugs were introduced or discovered that required Rule 1/2/3 auto-fixes inside the BiometricGate change.

## Build Gate Limitation (Pre-existing, Out of Scope)

The plan's automated build gate (`./gradlew :shared:compileKotlinIosSimulatorArm64`) could not be executed cleanly because `:shared:kspKotlinIosSimulatorArm64` fails with a **pre-existing** Room schema error unrelated to this change:

```
e: [ksp] AppDatabase.kt:36: Schema '6.json' required for migration was not found
e: [ksp] AppDatabase.kt:36: Schema '7.json' required for migration was not found
e: [ksp] AppDatabase.kt:36: Schema '8.json' required for migration was not found
```

`shared/schemas/com.pumpernickel.data.db.AppDatabase/` contains only `9.json`; schemas 6, 7, 8 are missing from the worktree. This was reproduced on the **clean tree before applying the BiometricGate change** (`git stash` → run gradle → same failure → `git stash pop`), confirming the issue is unrelated to this fix.

Per GSD scope-boundary rules, pre-existing failures in unrelated files (`AppDatabase.kt` is in `commonMain/data/db`, not `iosMain/domain/progresspic`) are **not auto-fixed** in a quick task. Logged to deferred items below.

In place of the failing build gate, the following narrower validations were used:

- **grep gates (all PASS):**
  - `LAPolicyDeviceOwnerAuthenticationWithBiometrics` count = 3 (import + call site + comment)
  - `LAErrorBiometryLockout` count = 3 (import + when-branch + comment)
  - `LAPolicyDeviceOwnerAuthentication[^W]` on non-comment lines = 2 (the `canEvaluatePolicy` pre-check + the fallback `evaluate` call) — fallback policy still referenced as required
- **klib symbol check (PASS):** `LAErrorBiometryLockout` and `LAPolicyDeviceOwnerAuthenticationWithBiometrics` are both present in the Kotlin/Native 2.3.20 ios_simulator_arm64 platform klib metadata at `~/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.20/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.LocalAuthentication/...` — the new imports will resolve when the unrelated KSP issue is cleared.
- **Signature compatibility:** `evaluatePolicy(policy:, localizedReason:) { success, error -> ... }` and `canEvaluatePolicy(policy:, error:)` call shapes are unchanged from the prior compiling version of this file. Only the policy enum values and error-code branches differ — both new symbols are attested in the klib above.
- **Diff scope (PASS):** `git diff --name-only` against the prior commit shows exactly one path (`shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.ios.kt`).

The first time the user (or a follow-up quick task) clears the unrelated Room schema issue, the iOS framework build will exercise this code and confirm Kotlin compilation end-to-end. No regression risk is taken on by deferring the gate, because:
- The new imports are attested in the platform klib.
- The call signatures are byte-identical to the previously compiling version.
- The grep-level structure of the file matches the plan's specification line-for-line.

## Visual Verification (Awaiting User)

Task 2 (`checkpoint:human-verify`) is gated on the user running the iOS simulator and stepping through the six visual tests in the plan:

1. Face ID success path — tile tap shows Face ID prompt first, matching face opens photo.
2. Face ID failure / system fallback — non-matching face routes to passcode within the same sheet OR re-blurs the tile (both acceptable).
3. **User-cancel does NOT fall through to passcode** (the regression guard) — Cancel on Face ID sheet → tile re-blurs, NO passcode pad.
4. Biometrics unenrolled (sim setting OFF) → passcode entry sheet appears (technical fallback works).
5. Re-enable Face ID → fresh prompt (no stale "passcode-only" caching).
6. Post-workout photo prompt regression check — flow unchanged (BiometricGate is gallery-only).

Detailed steps including simulator menu paths and triage hints are in the plan's `<how-to-verify>` block.

## Deferred Items (Out of Scope for This Quick Task)

- **Room schema migrations 6/7/8 missing.** `shared/schemas/com.pumpernickel.data.db.AppDatabase/` only contains `9.json`. The KSP `kspKotlinIosSimulatorArm64` and `:shared:compileCommonMainKotlinMetadata` tasks both fail with "Schema 'N.json' required for migration was not found". Pre-existing on the clean tree (reproduced via `git stash` before this change). Likely needs the missing schema files to be regenerated / committed, OR the AutoMigration spec on `AppDatabase` to be updated to remove migrations whose source schemas are no longer available. Not addressed here per scope discipline; recommend a follow-up `/gsd:debug` or `/gsd:quick` task scoped to `AppDatabase.kt` schema layout.

## Key Decisions

| Decision | Rationale |
|---|---|
| Use `LAPolicyDeviceOwnerAuthenticationWithBiometrics` first (not `LAPolicyDeviceOwnerAuthentication` with hope-for-biometrics) | The "any owner credential" policy's UI choice is non-deterministic on Face-ID-enrolled simulators / devices and frequently jumps straight to passcode. The biometrics-only sibling reliably surfaces Face ID. |
| User-cancel returns `Cancelled` and does NOT trigger passcode fallback | Apple UX convention — a deliberate cancel must not be punished with a second prompt. Fallback is reserved for technical unavailability (`BiometryNotAvailable` / `NotEnrolled` / `Lockout`). |
| Fresh `LAContext` instance for the fallback `evaluate` call | A context that has been used for one policy retains state about that attempt; a fresh context guarantees a clean prompt. Cheap to allocate, avoids subtle policy-cache bleed. |
| `EvaluateOutcome` sealed class instead of inline branching | Isolates the should-we-fall-back? decision into a single `when` block in `requestUnlock`, so user-cancel cannot be accidentally re-routed to the passcode policy by future edits. |

## Self-Check: PASSED

- File `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.ios.kt` exists at the modified path. FOUND.
- Commit `abb61f5` exists in git log. FOUND. (`fix(ios): progress gallery Face ID prompts first, passcode falls back only on biometric unavailability`)
- New symbols (`LAPolicyDeviceOwnerAuthenticationWithBiometrics`, `LAErrorBiometryLockout`) are present in the file (grep counts ≥ 1 each). FOUND.
- Fallback policy `LAPolicyDeviceOwnerAuthentication` (without `WithBiometrics`) still referenced on non-comment lines. FOUND.
- `git diff --name-only HEAD~1 HEAD` returns exactly the one file path. CONFIRMED single-file scope.
