---
phase: 260512-lrq
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt
autonomous: true
requirements:
  - QUICK-260512-lrq
must_haves:
  truths:
    - "iOS Koin graph resolves LocationProvider without NoDefinitionFoundException"
    - "WorkoutSessionViewModel can be instantiated on iOS at app startup"
    - "iOS platform module mirrors Android registration for LocationProvider"
  artifacts:
    - path: "shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt"
      provides: "iOS Koin platform module with LocationProvider registration"
      contains: "single<LocationProvider> { IosLocationProvider() }"
  key_links:
    - from: "shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt"
      to: "shared/src/iosMain/kotlin/com/pumpernickel/data/location/IosLocationProvider.kt"
      via: "Koin single binding"
      pattern: "single<LocationProvider> \\{ IosLocationProvider\\(\\) \\}"
    - from: "shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt"
      to: "shared/src/commonMain/kotlin/com/pumpernickel/domain/location/LocationProvider.kt"
      via: "import statement"
      pattern: "import com.pumpernickel.domain.location.LocationProvider"
---

<objective>
Fix iOS app startup crash caused by missing Koin DI registration for `LocationProvider`.

Purpose: `WorkoutSessionViewModel` requires `LocationProvider` as a constructor parameter. The Android platform module registers it (line 22), but the iOS platform module does not — Koin throws `NoDefinitionFoundException` when the ViewModel graph is resolved, crashing the app at startup.

Output: One-line registration `single<LocationProvider> { IosLocationProvider() }` added to `PlatformModule.ios.kt` with matching imports, restoring parity with the Android platform module.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@CLAUDE.md
@.planning/STATE.md

<interfaces>
<!-- Contracts confirmed in codebase. Executor uses these directly — no exploration needed. -->

From shared/src/commonMain/kotlin/com/pumpernickel/domain/location/LocationProvider.kt:
```kotlin
package com.pumpernickel.domain.location

interface LocationProvider {
    suspend fun getCurrentLocation(): GeoPoint?
}
```

From shared/src/iosMain/kotlin/com/pumpernickel/data/location/IosLocationProvider.kt:
```kotlin
package com.pumpernickel.data.location

class IosLocationProvider : LocationProvider {
    // no-arg constructor — confirmed line 18
    override suspend fun getCurrentLocation(): GeoPoint? { ... }
}
```

From shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt (reference pattern, line 22):
```kotlin
single<LocationProvider> { AndroidLocationProvider(androidContext()) }
```

Current state of shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt (lines 16–25):
```kotlin
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
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Register IosLocationProvider as LocationProvider in PlatformModule.ios.kt</name>
  <files>shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt</files>
  <action>
Modify ONLY `shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt`. Two edits, no other changes:

1. **Add two imports** to the existing import block (keep alphabetical order within the `com.pumpernickel.*` group). Insert these two lines so the final import order is:
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
   ```

2. **Add one Koin registration** inside the existing `actual val platformModule: Module = module { ... }` block, immediately after the `createDataStoreIos()` line and BEFORE the `// Phase 17 — actuals from 17-04 use no-arg ctors (D-17-19).` comment. The resulting block must read exactly:
   ```kotlin
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

DO NOT touch any other file. DO NOT modify `IosLocationProvider.kt`, `LocationProvider.kt`, `WorkoutSessionViewModel.kt`, or `PlatformModule.android.kt`. Use a no-arg constructor for `IosLocationProvider()` — confirmed by the interfaces block above. No worktree isolation. Do not `git add shared/build/`.

After the edit, run the Gradle compile-check from the project root `/Users/olli/Studium/semester_6/mobile_app_dev/PumpernickelApp` to confirm the iOS framework still links cleanly:
```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 --quiet
```
Timeout: 15 minutes (Kotlin/Native link is slow on cold cache). Expect exit code 0.

Commit message (single atomic commit):
`fix(ios): register IosLocationProvider as LocationProvider in PlatformModule (parity with Android)`
  </action>
  <verify>
    <automated>
F=/Users/olli/Studium/semester_6/mobile_app_dev/PumpernickelApp/shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt && \
test "$(grep -v '^#' "$F" | grep -c 'import com.pumpernickel.data.location.IosLocationProvider')" = "1" && \
test "$(grep -v '^#' "$F" | grep -c 'import com.pumpernickel.domain.location.LocationProvider')" = "1" && \
test "$(grep -v '^#' "$F" | grep -c 'single<LocationProvider> { IosLocationProvider() }')" = "1" && \
cd /Users/olli/Studium/semester_6/mobile_app_dev/PumpernickelApp && \
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 --quiet
    </automated>
  </verify>
  <done>
- `PlatformModule.ios.kt` contains the two new imports (alphabetically placed) and the new `single<LocationProvider> { IosLocationProvider() }` line in the correct position within the `module { ... }` block.
- `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 --quiet` exits 0 from the project root.
- No other files modified (verify with `git diff --name-only` → only `shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt`).
- Single atomic commit created with message `fix(ios): register IosLocationProvider as LocationProvider in PlatformModule (parity with Android)`.
- Real iOS crash-check is deferred to the user in Xcode (out of scope for this task).
  </done>
</task>

</tasks>

<verification>
- Grep gates (filtered to strip comments) confirm both imports and the Koin registration are present exactly once.
- Gradle `linkDebugFrameworkIosSimulatorArm64` proves the change compiles and links on the iOS simulator target — this is the compile-time guarantee that the `LocationProvider`/`IosLocationProvider` symbols resolve correctly.
- The runtime crash check (Koin graph resolution at app startup) will be verified by the user manually in Xcode after the commit lands.
</verification>

<success_criteria>
1. `grep -c 'single<LocationProvider> { IosLocationProvider() }' shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt` returns `1`.
2. iOS framework links cleanly: `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 --quiet` exits 0.
3. `git diff --name-only HEAD~1 HEAD` shows exactly one changed file: `shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt`.
4. iOS platform module is now structurally symmetric with `PlatformModule.android.kt` (same set of `single<...>` registrations, modulo platform-specific constructors).
</success_criteria>

<output>
After completion, create `.planning/quick/260512-lrq-fix-ios-di-register-ioslocationprovider-/260512-lrq-SUMMARY.md` per `summary.md` template.
</output>
