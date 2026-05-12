---
phase: 260512-lrq
plan: 01
subsystem: ios-di
tags: [ios, koin, di, location-provider, hotfix, parity]
dependency_graph:
  requires:
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/location/LocationProvider.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/data/location/IosLocationProvider.kt
  provides:
    - "iOS Koin binding for LocationProvider (single<LocationProvider> -> IosLocationProvider)"
  affects:
    - shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt (resolves at runtime)
tech-stack:
  added: []
  patterns:
    - "Koin platform module parity between Android and iOS sourceSets"
key-files:
  created: []
  modified:
    - shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt
decisions:
  - "Use no-arg constructor for IosLocationProvider() (confirmed by interfaces block in PLAN)"
  - "Place new Koin binding directly after createDataStoreIos() and before the Phase 17 comment to keep ordering consistent with Android module"
metrics:
  duration_seconds: 63
  duration_human: "~1 min"
  tasks_completed: 1
  files_modified: 1
  commits: 1
  completed_date: "2026-05-12"
---

# Quick 260512-lrq: Fix iOS DI — Register IosLocationProvider as LocationProvider Summary

One-line registration `single<LocationProvider> { IosLocationProvider() }` added to `PlatformModule.ios.kt` (with two new imports), restoring Koin DI parity with Android and preventing the iOS startup crash when `WorkoutSessionViewModel` requests `LocationProvider`.

## What Was Done

Single atomic change to `shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt`:

1. Added two imports in alphabetical order within the `com.pumpernickel.*` import group:
   - `com.pumpernickel.data.location.IosLocationProvider`
   - `com.pumpernickel.domain.location.LocationProvider`
2. Added one Koin `single<LocationProvider> { IosLocationProvider() }` binding inside the existing `actual val platformModule` module block, placed directly after the `createDataStoreIos()` line and before the Phase 17 comment.

No other files were touched.

## Diff

### Before (lines 3–14 imports; lines 16–25 module body)

```kotlin
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.RoomDatabase
import com.pumpernickel.data.db.AppDatabase
import com.pumpernickel.data.db.getDatabaseBuilder
import com.pumpernickel.data.preferences.createDataStoreIos
import com.pumpernickel.domain.ai.SecureKeyStore
import com.pumpernickel.domain.progresspic.BiometricGate
import com.pumpernickel.domain.progresspic.PhotoCaptureLauncher
import com.pumpernickel.domain.progresspic.PhotoVault
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<RoomDatabase.Builder<AppDatabase>> { getDatabaseBuilder() }
    single<DataStore<Preferences>> { createDataStoreIos() }
    // Phase 17 — actuals from 17-04 use no-arg ctors (D-17-19).
    single<PhotoVault> { PhotoVault() }
    single<PhotoCaptureLauncher> { PhotoCaptureLauncher() }
    single<BiometricGate> { BiometricGate() }
    // Phase 18 — BYOK key store, no-arg ctor (REQ-AI-06).
    single<SecureKeyStore> { SecureKeyStore() }
}
```

### After

```kotlin
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.RoomDatabase
import com.pumpernickel.data.db.AppDatabase
import com.pumpernickel.data.db.getDatabaseBuilder
import com.pumpernickel.data.location.IosLocationProvider
import com.pumpernickel.data.preferences.createDataStoreIos
import com.pumpernickel.domain.ai.SecureKeyStore
import com.pumpernickel.domain.location.LocationProvider
import com.pumpernickel.domain.progresspic.BiometricGate
import com.pumpernickel.domain.progresspic.PhotoCaptureLauncher
import com.pumpernickel.domain.progresspic.PhotoVault
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<RoomDatabase.Builder<AppDatabase>> { getDatabaseBuilder() }
    single<DataStore<Preferences>> { createDataStoreIos() }
    single<LocationProvider> { IosLocationProvider() }
    // Phase 17 — actuals from 17-04 use no-arg ctors (D-17-19).
    single<PhotoVault> { PhotoVault() }
    single<PhotoCaptureLauncher> { PhotoCaptureLauncher() }
    single<BiometricGate> { BiometricGate() }
    // Phase 18 — BYOK key store, no-arg ctor (REQ-AI-06).
    single<SecureKeyStore> { SecureKeyStore() }
}
```

### Unified diff (from `git diff HEAD~1 HEAD`)

```
diff --git a/shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt b/shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt
index 7a45c11..8ae40c4 100644
--- a/shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt
+++ b/shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt
@@ -5,8 +5,10 @@ import androidx.datastore.preferences.core.Preferences
 import androidx.room.RoomDatabase
 import com.pumpernickel.data.db.AppDatabase
 import com.pumpernickel.data.db.getDatabaseBuilder
+import com.pumpernickel.data.location.IosLocationProvider
 import com.pumpernickel.data.preferences.createDataStoreIos
 import com.pumpernickel.domain.ai.SecureKeyStore
+import com.pumpernickel.domain.location.LocationProvider
 import com.pumpernickel.domain.progresspic.BiometricGate
 import com.pumpernickel.domain.progresspic.PhotoCaptureLauncher
 import com.pumpernickel.domain.progresspic.PhotoVault
@@ -16,6 +18,7 @@ import org.koin.dsl.module
 actual val platformModule: Module = module {
     single<RoomDatabase.Builder<AppDatabase>> { getDatabaseBuilder() }
     single<DataStore<Preferences>> { createDataStoreIos() }
+    single<LocationProvider> { IosLocationProvider() }
     // Phase 17 — actuals from 17-04 use no-arg ctors (D-17-19).
     single<PhotoVault> { PhotoVault() }
     single<PhotoCaptureLauncher> { PhotoCaptureLauncher() }
```

## Commit

| Hash      | Message                                                                                                  | Files                                                                  |
| --------- | -------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| `9ef3eca` | `fix(ios): register IosLocationProvider as LocationProvider in PlatformModule (parity with Android)`      | `shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt` |

- Stats: 1 file changed, 3 insertions(+), 0 deletions
- No file deletions (clean post-commit deletion check)
- `git diff --name-only HEAD~1 HEAD` confirmed: only the targeted file changed

## Verify Outcomes

All gates from the plan's `<verify>` block were executed against the post-edit file (`F = shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt`):

| Gate                                                                                       | Expected | Actual | Status |
| ------------------------------------------------------------------------------------------ | -------- | ------ | ------ |
| `grep -c 'import com.pumpernickel.data.location.IosLocationProvider'` (comments filtered)  | `1`      | `1`    | PASS   |
| `grep -c 'import com.pumpernickel.domain.location.LocationProvider'` (comments filtered)   | `1`      | `1`    | PASS   |
| `grep -c 'single<LocationProvider> { IosLocationProvider() }'` (comments filtered)         | `1`      | `1`    | PASS   |
| `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 --quiet` exit code                  | `0`      | `0`    | PASS   |

### Gradle link build details

- Command: `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 --quiet`
- Working directory: `/Users/olli/Studium/semester_6/mobile_app_dev/PumpernickelApp` (no worktree)
- Exit code: `0`
- Build duration: **19 seconds** (warm Gradle/Kotlin Native cache; well within the 15-minute timeout budget)
- Errors (`e:` lines): `0`
- Warnings (`w:` lines): `253` — all pre-existing (suspend-fn-exposed-to-ObjC, `expect`/`actual` beta, deprecated `kotlinx.datetime.Instant`, redundant conversions in `SecureKeyStore.ios.kt` / `BiometricGate.ios.kt`, etc.). None originate from `PlatformModule.ios.kt`. Per the scope-boundary rule, these are out of scope and logged as observation only.

## Success Criteria

1. `grep -c 'single<LocationProvider> { IosLocationProvider() }'` returns `1` — PASS
2. iOS framework links cleanly (exit 0) — PASS
3. `git diff --name-only HEAD~1 HEAD` shows exactly one changed file — PASS (`shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt`)
4. iOS platform module is now structurally symmetric with `PlatformModule.android.kt` (matching `single<LocationProvider> { ... }` registration, modulo platform-specific constructor — Android uses `AndroidLocationProvider(androidContext())`, iOS uses no-arg `IosLocationProvider()`) — PASS

All four success criteria met.

## Deviations from Plan

None — plan executed exactly as written. No bugs found, no missing critical functionality, no blocking issues, no architectural decisions required.

## Decisions Made

- **No-arg `IosLocationProvider()` constructor** — confirmed by the `<interfaces>` block in the plan (line 18 of `IosLocationProvider.kt` per the plan's interfaces section). No `androidContext()` equivalent is needed on iOS, so the binding is a clean no-arg factory.
- **Insertion position** — placed the new `single<LocationProvider>` line directly after `createDataStoreIos()` and before the `// Phase 17 …` comment, matching the location specified in the plan's task body and keeping platform-agnostic singletons grouped at the top of the module.

## Authentication Gates

None encountered. Task did not require auth, secrets, or external services.

## Self-Check: PASSED

- File `shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt`: FOUND (28 lines, 3 inserted)
- Commit `9ef3eca`: FOUND in `git log --oneline`
- Grep gates: all three return `1`
- Gradle link exit code: `0` (19 s)
- Single-file diff confirmed: exactly one file in `git diff --name-only HEAD~1 HEAD`

## Notes for Follow-up

- Real runtime crash check (Koin graph resolution at iOS app startup) is deferred to the user in Xcode per the plan — out of scope for this task.
- The 253 pre-existing build warnings (suspend-fn-exposed-to-ObjC, kotlin.time.Instant deprecation, etc.) are not introduced by this change and remain available to address in a dedicated cleanup task.
