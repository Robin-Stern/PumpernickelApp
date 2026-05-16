---
phase: quick-260516-nfn
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - shared/src/commonMain/kotlin/com/pumpernickel/data/geofence/DebugGeofenceProvider.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt
  - androidApp/build.gradle.kts
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/MainActivity.kt
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/DebugGeofencePanel.kt
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/SettingsSheet.kt
  - iosApp/iosApp/Views/Settings/DebugGeofencePanel.swift
  - iosApp/iosApp/Views/Settings/SettingsView.swift
  - iosApp/iosApp/AppDelegate.swift
  - iosApp/iosApp.xcodeproj/project.pbxproj
  - shared/src/iosMain/kotlin/com/pumpernickel/di/KoinHelper.kt
autonomous: false
requirements: [DEBUG-GPS-MOCK]

must_haves:
  truths:
    - "In DEBUG builds, GeofenceProvider resolves to DebugGeofenceProvider (not iOS/Android actual)"
    - "In RELEASE builds, GeofenceProvider resolves to the real IosGeofenceProvider / AndroidGeofenceProvider"
    - "User can tap 'Trigger Exit' in Settings (DEBUG-only) and the geofence chip transitions Inactive/InZone -> GracePeriod with countdown"
    - "User can tap 'Trigger Enter' and the chip returns to InZone, grace timer cancels"
    - "User can tap 'Trigger Error' and the chip transitions to Inactive (Error path)"
    - "Debug panel is invisible in release builds on both platforms"
    - "Debug trigger uses the CURRENT active regionId derived from the active workout session"
  artifacts:
    - path: "shared/src/commonMain/kotlin/com/pumpernickel/data/geofence/DebugGeofenceProvider.kt"
      provides: "Manual-trigger GeofenceProvider for debug builds"
      contains: "class DebugGeofenceProvider"
    - path: "androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/DebugGeofencePanel.kt"
      provides: "Android settings panel with trigger buttons (DEBUG-only)"
      contains: "fun DebugGeofencePanel"
    - path: "iosApp/iosApp/Views/Settings/DebugGeofencePanel.swift"
      provides: "iOS settings panel with trigger buttons (#if DEBUG)"
      contains: "struct DebugGeofencePanel"
  key_links:
    - from: "androidApp Koin module (debug)"
      to: "DebugGeofenceProvider"
      via: "BuildConfig.DEBUG conditional in SharedModule (or platformModule override)"
      pattern: "BuildConfig\\.DEBUG"
    - from: "iOS Koin bootstrap"
      to: "DebugGeofenceProvider"
      via: "#if DEBUG branch in AppDelegate / KoinHelper invocation"
      pattern: "#if DEBUG"
    - from: "DebugGeofencePanel triggerExit"
      to: "DebugGeofenceProvider.triggerExit(regionId)"
      via: "KoinHelper.getDebugGeofenceProvider() cast or interface check"
      pattern: "triggerExit"
---

<objective>
Add a **debug-only** GeofenceProvider mock that replaces the real iOS/Android actuals in DEBUG builds, plus a Settings panel with manual trigger buttons (Enter/Exit/Error). Goal: be able to demo/UAT Phase 19 flows (grace period chip, countdown, notification, early-exit penalty) **without leaving the desk** and without a physical device with real GPS.

Purpose: Phase 19 UATs (19-06 / 19-07) require physical devices because Simulator/Emulator do not deliver native geofence events. A manual-trigger mock unblocks demos, screen recordings, regression checks, and stakeholder review.

Output:
- New `DebugGeofenceProvider` in commonMain (one class, ~50 LOC).
- Build-gated Koin binding: in DEBUG, `GeofenceProvider` resolves to the mock; in RELEASE, untouched.
- Debug Settings panel (Android Compose + iOS SwiftUI) with three buttons: Trigger Enter / Trigger Exit / Trigger Error. Visible only in DEBUG builds.
- Zero behavior change in release builds.

**Hard constraints:**
- The real `IosGeofenceProvider` and `AndroidGeofenceProvider` MUST remain the binding for release builds. Verify via grep that no release-affecting code path resolves to `DebugGeofenceProvider`.
- The debug panel UI MUST be wrapped in `BuildConfig.DEBUG` (Android) and `#if DEBUG` (iOS). No reflection / runtime flag gymnastics.
- The trigger methods MUST use the VM's current active regionId (`"active-workout-${active.startTimeMillis}"`) — otherwise the VM ignores the event (it filters by regionId).
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/phases/19-geofencing-workout-enforcement/19-01-SUMMARY.md
@.planning/phases/19-geofencing-workout-enforcement/19-03-SUMMARY.md
@.planning/phases/19-geofencing-workout-enforcement/19-04-SUMMARY.md
@.planning/phases/19-geofencing-workout-enforcement/19-05-SUMMARY.md
@shared/src/commonMain/kotlin/com/pumpernickel/domain/geofence/GeofenceProvider.kt
@shared/src/commonMain/kotlin/com/pumpernickel/domain/geofence/GeofenceEvent.kt
@shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt
@shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt
@shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt

<interfaces>
<!-- Contracts the executor needs. Already in codebase — DO NOT re-explore. -->

From shared/src/commonMain/kotlin/com/pumpernickel/domain/geofence/GeofenceProvider.kt:
```kotlin
interface GeofenceProvider {
    suspend fun register(center: GeoPoint, radiusMeters: Double, id: String): Result<Unit>
    suspend fun unregister(id: String)
    val events: SharedFlow<GeofenceEvent>   // extraBufferCapacity >= 16, replay = 0
}
```

From shared/src/commonMain/kotlin/com/pumpernickel/domain/geofence/GeofenceEvent.kt:
```kotlin
sealed class GeofenceEvent {
    abstract val regionId: String
    data class Enter(override val regionId: String) : GeofenceEvent()
    data class Exit(override val regionId: String) : GeofenceEvent()
    data class Error(override val regionId: String, val message: String) : GeofenceEvent()
}
```

Region-id schema (verified in WorkoutSessionViewModel.kt and platform actuals):
- Format: `"active-workout-${active.startTimeMillis}"` where `active` is the WorkoutSession from WorkoutRepository.
- VM filter: events whose `regionId` does NOT match `activeRegionId` are dropped (`startGeofenceObserver`).

VM surface (from 19-05-SUMMARY):
```kotlin
val geofenceState: StateFlow<GeofenceUiState>   // Inactive / InZone / GracePeriod(remainingSeconds) / Exited
```

Koin binding sites:
- iOS: `shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt` line ~36: `single<GeofenceProvider> { IosGeofenceProvider(get()) }`
- Android: `shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt` line ~34: `single<GeofenceProvider> { AndroidGeofenceProvider(androidContext()) }`

Settings entry points (where debug panel is added):
- iOS: `iosApp/iosApp/Views/Settings/SettingsView.swift` — Form has `Section("Training")` at ~line 80. Add a `#if DEBUG` Section below it.
- Android: `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/SettingsSheet.kt` — Training block at ~line 227. Add a `if (BuildConfig.DEBUG)` Column below it.

Active session region-id access path:
- Approach: read `WorkoutRepository.getActiveSession()?.startTimeMillis` from the debug panel via Koin → build `"active-workout-$it"`. If null (no active session), disable buttons.
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add DebugGeofenceProvider in commonMain + build-gated Koin binding (Android BuildConfig.DEBUG + iOS #if DEBUG)</name>
  <files>
    - shared/src/commonMain/kotlin/com/pumpernickel/data/geofence/DebugGeofenceProvider.kt (CREATE)
    - shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt (read only — confirm binding stays in PlatformModule)
    - androidApp/build.gradle.kts (MODIFY — enable buildFeatures.buildConfig = true; add debug buildType if missing)
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/MainActivity.kt (MODIFY — at Koin start, if BuildConfig.DEBUG, load an in-place override module that rebinds GeofenceProvider)
    - shared/src/iosMain/kotlin/com/pumpernickel/di/KoinHelper.kt (MODIFY — add getDebugGeofenceProvider() that returns cast-or-null)
    - iosApp/iosApp/AppDelegate.swift (MODIFY — wrap a `#if DEBUG` block that loads a debug Koin override module after `doInitKoinIos()`)
  </files>
  <action>
**EDIT A — Create DebugGeofenceProvider (commonMain):**

File: `shared/src/commonMain/kotlin/com/pumpernickel/data/geofence/DebugGeofenceProvider.kt`

```kotlin
package com.pumpernickel.data.geofence

import com.pumpernickel.domain.geofence.GeofenceEvent
import com.pumpernickel.domain.geofence.GeofenceProvider
import com.pumpernickel.domain.location.GeoPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * DEBUG-only GeofenceProvider. Replaces the real iOS/Android actuals in DEBUG
 * builds so Phase 19 flows can be exercised on Simulator/Emulator without real GPS.
 *
 * WARNING: Must NEVER be bound in release builds. The Koin override is gated by
 * `BuildConfig.DEBUG` on Android and `#if DEBUG` on iOS — see MainActivity.kt
 * and AppDelegate.swift for the wiring.
 *
 * Trigger methods are imperative — UI calls e.g. `triggerExit(regionId)` to emit
 * a single GeofenceEvent.Exit on `events`. The VM filters by regionId, so callers
 * must pass the CURRENT active region id ("active-workout-${startTimeMillis}").
 */
class DebugGeofenceProvider : GeofenceProvider {

    private val _events = MutableSharedFlow<GeofenceEvent>(
        replay = 0,
        extraBufferCapacity = 16
    )
    override val events: SharedFlow<GeofenceEvent> = _events.asSharedFlow()

    // Mirror the real provider's contract: register stores the last id; ENTER may
    // be auto-emitted by the platform actual on first register, but for debug we
    // stay silent until the user taps a trigger button (cleaner UAT semantics).
    @Volatile
    private var lastRegisteredId: String? = null

    val lastRegisteredRegionId: String? get() = lastRegisteredId

    override suspend fun register(
        center: GeoPoint,
        radiusMeters: Double,
        id: String
    ): Result<Unit> {
        lastRegisteredId = id
        return Result.success(Unit)
    }

    override suspend fun unregister(id: String) {
        if (lastRegisteredId == id) lastRegisteredId = null
    }

    /** Emit GeofenceEvent.Enter for the given regionId. */
    fun triggerEnter(regionId: String) {
        _events.tryEmit(GeofenceEvent.Enter(regionId))
    }

    /** Emit GeofenceEvent.Exit for the given regionId — VM should start grace period. */
    fun triggerExit(regionId: String) {
        _events.tryEmit(GeofenceEvent.Exit(regionId))
    }

    /** Emit GeofenceEvent.Error — VM should transition chip to Inactive. */
    fun triggerError(regionId: String, message: String = "Debug-triggered error") {
        _events.tryEmit(GeofenceEvent.Error(regionId, message))
    }
}
```

**EDIT B — Enable BuildConfig + add debug buildType in androidApp/build.gradle.kts:**

In `androidApp/build.gradle.kts`, inside `android { ... }`:

1. Inside `buildFeatures { compose = true }` add: `buildConfig = true`
2. Add a `buildTypes { ... }` block (after `compileOptions`):

```kotlin
buildTypes {
    getByName("debug") {
        isMinifyEnabled = false
        // BuildConfig.DEBUG is true automatically for this build type.
    }
    getByName("release") {
        isMinifyEnabled = false
        // BuildConfig.DEBUG is false here.
    }
}
```

After edits, `androidApp/build.gradle.kts` must contain `buildConfig = true` exactly once. Verify with:
```
grep -c "buildConfig = true" androidApp/build.gradle.kts   # expect 1
```

**EDIT C — Android Koin debug override in MainActivity.kt:**

Locate the `initKoin { ... }` invocation (or equivalent Koin bootstrap call) in `androidApp/src/androidMain/kotlin/com/pumpernickel/android/MainActivity.kt`. The pattern in the codebase is `initKoin(...)` that takes a `KoinApplication.() -> Unit` lambda (see SharedModule.kt line 136).

Strategy: AFTER `initKoin(...)` completes (or by extending the lambda), load an extra Koin module that overrides `GeofenceProvider` when `BuildConfig.DEBUG == true`.

Add this code immediately AFTER the existing `initKoin(...)` call in `MainActivity.onCreate` (or wherever Koin is started):

```kotlin
import com.pumpernickel.android.BuildConfig
import com.pumpernickel.data.geofence.DebugGeofenceProvider
import com.pumpernickel.domain.geofence.GeofenceProvider
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module

// DEBUG-only: override GeofenceProvider with a manual-trigger mock so Phase 19
// flows can be exercised on the Emulator without real GPS. NEVER active in release.
if (BuildConfig.DEBUG) {
    loadKoinModules(
        module {
            single<GeofenceProvider>(override = true) { DebugGeofenceProvider() }
        }
    )
}
```

Note: `override = true` is the Koin 3.x syntax to override an existing binding. If the project's Koin version rejects it (Koin 4 deprecated it), substitute with `single<GeofenceProvider>(createdAtStart = false) { DebugGeofenceProvider() }` AND ensure `KoinApplication.allowOverride(true)` was set globally (likely already is — verify by grepping `allowOverride` in the codebase). If neither works in Koin 4.2.0, fall back to: in `PlatformModule.android.kt`, wrap the existing `single<GeofenceProvider> { AndroidGeofenceProvider(androidContext()) }` in `if (!BuildConfig.DEBUG)` — but this requires `BuildConfig` to be accessible from `shared` androidMain, which is NOT the case. **Preferred path:** keep override approach in `MainActivity.kt`; if Koin 4.2.0 syntax differs, look up the exact API in Koin docs and adapt. Do not invent flags.

**EDIT D — iOS Koin debug override in AppDelegate.swift:**

Locate `func application(_:didFinishLaunchingWithOptions:)` in `iosApp/iosApp/AppDelegate.swift` (Phase 19 03 created this file). It currently calls `KoinInitIosKt.doInitKoinIos()`.

Add this snippet AFTER `doInitKoinIos()`:

```swift
#if DEBUG
    // DEBUG-only: replace real GeofenceProvider with manual-trigger mock for Simulator/UAT.
    // Implemented via KoinHelper.loadDebugGeofenceOverride() — a new bridge function.
    KoinHelper.shared.loadDebugGeofenceOverride()
#endif
```

**EDIT E — Add bridge function in KoinHelper.kt (iosMain):**

In `shared/src/iosMain/kotlin/com/pumpernickel/di/KoinHelper.kt`, add:

```kotlin
import com.pumpernickel.data.geofence.DebugGeofenceProvider
import com.pumpernickel.domain.geofence.GeofenceProvider
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module

// (inside the existing KoinHelper class/object)

fun loadDebugGeofenceOverride() {
    loadKoinModules(
        module {
            single<GeofenceProvider>(override = true) { DebugGeofenceProvider() }
        }
    )
}

fun getDebugGeofenceProvider(): DebugGeofenceProvider? {
    val provider = org.koin.mp.KoinPlatform.getKoin().getOrNull<GeofenceProvider>()
    return provider as? DebugGeofenceProvider
}
```

If iOS uses `KoinHelper` as a Kotlin `object` (not `class`), match the existing style. Refer to current KoinHelper.kt patterns (Phase 19 03 added `getGeofenceProvider`, `getPermissionController`, `getEarlyExitTracker`) and follow the same shape.

**Same `override = true` caveat as Android applies — adapt to Koin 4.2.0 syntax if rejected.**

**Acceptance for Task 1:**
- `shared/src/commonMain/kotlin/com/pumpernickel/data/geofence/DebugGeofenceProvider.kt` exists and compiles for all three iOS targets + Android.
- `androidApp/build.gradle.kts` contains `buildConfig = true` (exactly once).
- `MainActivity.kt` has a `BuildConfig.DEBUG` block that calls `loadKoinModules`.
- `AppDelegate.swift` has a `#if DEBUG` block calling `KoinHelper.shared.loadDebugGeofenceOverride()`.
- `KoinHelper.kt` has `loadDebugGeofenceOverride()` + `getDebugGeofenceProvider()`.
- Release-build resolution check: in release config, the Koin override is NEVER loaded — verify by running an assembleRelease or simply confirming `BuildConfig.DEBUG` and `#if DEBUG` are the only gates (no env vars, no runtime flags).
  </action>
  <verify>
    <automated>
      # 1. Kotlin compiles for both platforms
      ./gradlew :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosX64 :androidApp:assembleDebug 2>&1 | tail -5
      # Expect: BUILD SUCCESSFUL

      # 2. File exists
      test -f shared/src/commonMain/kotlin/com/pumpernickel/data/geofence/DebugGeofenceProvider.kt && echo OK

      # 3. BuildConfig enabled (header comments stripped to avoid self-invalidating grep gate)
      grep -v '^#' androidApp/build.gradle.kts | grep -c "buildConfig = true"
      # Expect: 1

      # 4. Debug gates present (header comments stripped)
      grep -v '^//' androidApp/src/androidMain/kotlin/com/pumpernickel/android/MainActivity.kt | grep -c "BuildConfig.DEBUG"
      # Expect: >=1
      grep -v '^//' iosApp/iosApp/AppDelegate.swift | grep -c "#if DEBUG"
      # Expect: >=1

      # 5. Release path NOT contaminated
      grep -v '^//' shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt | grep -c "DebugGeofenceProvider"
      # Expect: 0
      grep -v '^//' shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt | grep -c "DebugGeofenceProvider"
      # Expect: 0
    </automated>
  </verify>
  <done>
    DebugGeofenceProvider exists in commonMain. BuildConfig is enabled. Both platforms have build-gated Koin override hooks. Real platform module files contain ZERO references to DebugGeofenceProvider. All targets compile.
  </done>
</task>

<task type="auto">
  <name>Task 2: Add Debug Settings panels (Android Compose BuildConfig.DEBUG + iOS SwiftUI #if DEBUG) with Enter/Exit/Error trigger buttons</name>
  <files>
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/DebugGeofencePanel.kt (CREATE)
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/SettingsSheet.kt (MODIFY — insert DebugGeofencePanel below Training section, gated by BuildConfig.DEBUG)
    - iosApp/iosApp/Views/Settings/DebugGeofencePanel.swift (CREATE)
    - iosApp/iosApp/Views/Settings/SettingsView.swift (MODIFY — insert #if DEBUG Section below Training section)
    - iosApp/iosApp.xcodeproj/project.pbxproj (MODIFY — register new Swift file)
  </files>
  <action>
**EDIT A — Create DebugGeofencePanel.kt (Android):**

File: `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/DebugGeofencePanel.kt`

```kotlin
package com.pumpernickel.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pumpernickel.data.geofence.DebugGeofenceProvider
import com.pumpernickel.data.repository.WorkoutRepository
import com.pumpernickel.domain.geofence.GeofenceProvider
import org.koin.compose.koinInject

/**
 * DEBUG-only Settings panel. Lets the user manually emit Enter/Exit/Error events
 * for the currently-active workout's region id. Not wired into release builds.
 *
 * Usage: wrap call site with `if (BuildConfig.DEBUG) { DebugGeofencePanel() }`.
 */
@Composable
fun DebugGeofencePanel(modifier: Modifier = Modifier) {
    val provider: GeofenceProvider = koinInject()
    val workoutRepository: WorkoutRepository = koinInject()
    val debug = provider as? DebugGeofenceProvider

    var activeRegionId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val active = workoutRepository.getActiveSession()
        activeRegionId = active?.startTimeMillis?.let { "active-workout-$it" }
    }

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = "DEBUG — Geofence Mock",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            text = "Region: ${activeRegionId ?: "(no active workout)"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val enabled = debug != null && activeRegionId != null
            Button(
                onClick = { activeRegionId?.let { debug?.triggerEnter(it) } },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            ) { Text("Enter") }
            Button(
                onClick = { activeRegionId?.let { debug?.triggerExit(it) } },
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier.weight(1f)
            ) { Text("Exit") }
            Button(
                onClick = { activeRegionId?.let { debug?.triggerError(it) } },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            ) { Text("Error") }
        }
        if (debug == null) {
            Text(
                text = "GeofenceProvider is NOT a DebugGeofenceProvider — release binding leaked into debug build?",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
```

**EDIT B — Wire DebugGeofencePanel into SettingsSheet.kt (Android):**

In `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/SettingsSheet.kt`, AFTER the Training section block (after the closing `}` of the Row at ~line 275, just before `Spacer(modifier = Modifier.height(20.dp))` at line 277), insert:

```kotlin
            if (com.pumpernickel.android.BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(20.dp))
                DebugGeofencePanel()
            }
```

Add import: `import com.pumpernickel.android.ui.components.DebugGeofencePanel`

**EDIT C — Create DebugGeofencePanel.swift (iOS):**

File: `iosApp/iosApp/Views/Settings/DebugGeofencePanel.swift`

```swift
#if DEBUG
import SwiftUI
import Shared

/// DEBUG-only Settings panel. Calls `DebugGeofenceProvider.trigger*` via KoinHelper
/// to emit synthetic geofence events for the active workout's region id.
/// Not present in release builds (entire file wrapped in `#if DEBUG`).
struct DebugGeofencePanel: View {
    @State private var activeRegionId: String?
    @State private var providerIsDebug: Bool = false

    private let workoutRepository = KoinHelper.shared.getWorkoutRepository()  // see KoinHelper

    var body: some View {
        Section("DEBUG — Geofence Mock") {
            Text("Region: \(activeRegionId ?? "(no active workout)")")
                .font(.caption)
                .foregroundColor(.secondary)
            HStack(spacing: 8) {
                Button("Enter") { triggerEnter() }
                    .buttonStyle(.borderedProminent)
                    .disabled(activeRegionId == nil || !providerIsDebug)
                Button("Exit") { triggerExit() }
                    .buttonStyle(.borderedProminent)
                    .tint(.red)
                    .disabled(activeRegionId == nil || !providerIsDebug)
                Button("Error") { triggerError() }
                    .buttonStyle(.bordered)
                    .disabled(activeRegionId == nil || !providerIsDebug)
            }
            if !providerIsDebug {
                Text("GeofenceProvider is NOT DebugGeofenceProvider — debug override failed.")
                    .font(.caption2)
                    .foregroundColor(.red)
            }
        }
        .task { await refreshActiveRegionId() }
    }

    private func refreshActiveRegionId() async {
        let debug = KoinHelper.shared.getDebugGeofenceProvider()
        providerIsDebug = (debug != nil)
        // Read active session — adapt to actual WorkoutRepository surface exposed via KoinHelper.
        // If KoinHelper does not expose getWorkoutRepository, add it as part of this task (same pattern
        // as getGeofenceProvider in Phase 19 plan 03).
        if let active = try? await workoutRepository.getActiveSession() {
            activeRegionId = "active-workout-\(active.startTimeMillis)"
        } else {
            activeRegionId = nil
        }
    }

    private func triggerEnter() {
        guard let id = activeRegionId,
              let debug = KoinHelper.shared.getDebugGeofenceProvider() else { return }
        debug.triggerEnter(regionId: id)
    }
    private func triggerExit() {
        guard let id = activeRegionId,
              let debug = KoinHelper.shared.getDebugGeofenceProvider() else { return }
        debug.triggerExit(regionId: id)
    }
    private func triggerError() {
        guard let id = activeRegionId,
              let debug = KoinHelper.shared.getDebugGeofenceProvider() else { return }
        debug.triggerError(regionId: id, message: "Debug-triggered error")
    }
}
#endif
```

**Important — KoinHelper supplementation:** `getWorkoutRepository()` may not exist yet. If absent, add it in `shared/src/iosMain/kotlin/com/pumpernickel/di/KoinHelper.kt` following the existing pattern (one-line getter resolving via `KoinPlatform.getKoin().get()`). The function `getDebugGeofenceProvider()` was already added in Task 1.

If `WorkoutRepository.getActiveSession()` returns a Kotlin suspend fun that bridges to Swift `async throws`, the `try? await` above is correct (KMP-NativeCoroutines pattern, confirmed in 19-06-SUMMARY decision 2). If it returns a non-suspend Kotlin function, drop the `await` and the `try?`.

**EDIT D — Wire DebugGeofencePanel into SettingsView.swift (iOS):**

In `iosApp/iosApp/Views/Settings/SettingsView.swift`, AFTER the `Section("Training") { ... }` block at ~line 80-86, insert:

```swift
                #if DEBUG
                DebugGeofencePanel()
                #endif
```

**EDIT E — Register DebugGeofencePanel.swift in project.pbxproj:**

Follow the manual pbxproj editing pattern from 19-06-SUMMARY (5 file refs added with B19060–B19064 / A19060–A19064 ids). Add:
- PBXBuildFile entry: `A19070 /* DebugGeofencePanel.swift in Sources */ = {isa = PBXBuildFile; fileRef = B19070 /* DebugGeofencePanel.swift */; };`
- PBXFileReference: `B19070 /* DebugGeofencePanel.swift */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = DebugGeofencePanel.swift; sourceTree = "<group>"; };`
- Add `B19070` to the `Settings` PBXGroup (same group as `WorkoutEnforcementDetailView.swift`).
- Add `A19070` to the PBXSourcesBuildPhase `files = ( ... )` array.

**Acceptance for Task 2:**
- Both panel files exist and compile.
- `SettingsSheet.kt` references `DebugGeofencePanel` inside a `BuildConfig.DEBUG` block.
- `SettingsView.swift` references `DebugGeofencePanel` inside a `#if DEBUG` block.
- `project.pbxproj` has B19070 + A19070 entries.
- `xcodebuild build -scheme iosApp -destination 'generic/platform=iOS Simulator'` succeeds.
- `:androidApp:assembleDebug` succeeds.
- Release builds compile and do NOT include the debug panel UI (verified via grep on the source — the gates are compile-time).
  </action>
  <verify>
    <automated>
      # 1. Build verification (both platforms)
      ./gradlew :androidApp:assembleDebug 2>&1 | tail -3
      # Expect: BUILD SUCCESSFUL
      xcodebuild build -scheme iosApp -destination 'generic/platform=iOS Simulator' -workspace iosApp/iosApp.xcworkspace 2>&1 | tail -3 || \
        xcodebuild build -scheme iosApp -destination 'generic/platform=iOS Simulator' -project iosApp/iosApp.xcodeproj 2>&1 | tail -3
      # Expect: ** BUILD SUCCEEDED **

      # 2. Files exist
      test -f androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/DebugGeofencePanel.kt && echo OK
      test -f iosApp/iosApp/Views/Settings/DebugGeofencePanel.swift && echo OK

      # 3. Both panels gated (header comments stripped)
      grep -v '^//' androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/SettingsSheet.kt | grep -c "BuildConfig.DEBUG.*DebugGeofencePanel\|DebugGeofencePanel.*BuildConfig.DEBUG"
      # Accept >=1 OR run a multi-line aware check:
      awk '/BuildConfig.DEBUG/{f=NR} /DebugGeofencePanel\(\)/{if(NR-f<=3) print "GATED"}' androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/SettingsSheet.kt
      # Expect: "GATED"

      grep -v '^//' iosApp/iosApp/Views/Settings/SettingsView.swift | grep -B1 -A1 "DebugGeofencePanel()" | grep -c "#if DEBUG"
      # Expect: 1

      # 4. pbxproj entries
      grep -c "DebugGeofencePanel.swift" iosApp/iosApp.xcodeproj/project.pbxproj
      # Expect: >=2 (PBXBuildFile + PBXFileReference)

      # 5. Trigger methods callable (compile-only check — covered by build above)
      grep -c "triggerExit\|triggerEnter\|triggerError" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/DebugGeofencePanel.kt
      # Expect: >=3
      grep -c "triggerExit\|triggerEnter\|triggerError" iosApp/iosApp/Views/Settings/DebugGeofencePanel.swift
      # Expect: >=3
    </automated>
  </verify>
  <done>
    Both debug panels exist, are gated by BuildConfig.DEBUG / #if DEBUG, and both DEBUG builds (Android + iOS Simulator) compile successfully. Panels are NOT present in any release-binding code path.
  </done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 3: Manual smoke test on Android Emulator + iOS Simulator</name>
  <what-built>
    - DebugGeofenceProvider in commonMain (manual-trigger mock).
    - Build-gated Koin override (BuildConfig.DEBUG on Android, #if DEBUG on iOS) — replaces the real GeofenceProvider in debug builds only.
    - Debug Settings panel on both platforms with three buttons: Trigger Enter / Trigger Exit / Trigger Error.
    - Trigger methods emit on DebugGeofenceProvider.events using the active workout's regionId.
  </what-built>
  <how-to-verify>
**Android (Emulator):**
1. Build & install debug: `./gradlew :androidApp:installDebug` → launch the app.
2. Start a workout (any template) and log one set so the geofence registers — verify the chip shows "In Zone" (or "Inactive" if no location permission; for the smoke test, the chip starting state is not what we're checking).
3. Open Settings → scroll past the Training section. Confirm "DEBUG — Geofence Mock" panel is visible with Region id `active-workout-{millis}` shown.
4. Tap **Exit**. Expected: chip transitions to **GracePeriod** with 5:00 countdown that ticks down each second.
5. Tap **Enter**. Expected: chip returns to **InZone**, grace timer stops/cancels, no penalty fires.
6. Tap **Exit** again, wait ~5 seconds, tap **Enter** before 0:00. Expected: chip returns to InZone, no XP penalty in next workout summary (penalty only fires at grace expiry).
7. Tap **Error**. Expected: chip transitions to **Inactive**.
8. Build a RELEASE APK locally: `./gradlew :androidApp:assembleRelease`. Install. Open Settings. Expected: **NO debug panel visible**. Optional: decompile the release APK and grep for "DebugGeofenceProvider" — should be absent or only in stripped strings.

**iOS (Simulator):**
1. Build & run debug scheme in Xcode (Simulator destination).
2. Start a workout and log one set.
3. Open Settings tab. Confirm "DEBUG — Geofence Mock" Section is visible with Region id.
4. Tap **Exit**. Expected: chip transitions to GracePeriod with countdown.
5. Tap **Enter**. Expected: chip returns to InZone.
6. Tap **Error**. Expected: chip transitions to Inactive.
7. Change scheme to Release (or Archive). Confirm debug Section is NOT present.

**Pass criteria:**
- All three triggers work on both platforms.
- Debug panel disabled when no active workout (buttons greyed out, region id shows "(no active workout)").
- Release builds on both platforms do NOT show the debug panel.

**Fail cues:**
- Trigger button does nothing → `getDebugGeofenceProvider()` returned null → Koin override was not loaded (check Logcat / Xcode console for Koin warnings; verify the `override = true` syntax compiled with the project's Koin version).
- Chip does not transition on Exit → regionId mismatch → the panel computed a different region id than the VM registered with. Confirm both use `"active-workout-${startTimeMillis}"` from the same source (`WorkoutRepository.getActiveSession().startTimeMillis`).
- Debug panel visible in release build → BuildConfig.DEBUG / #if DEBUG gate is on the wrong block.
  </how-to-verify>
  <resume-signal>Type "approved" or describe what failed (which platform, which trigger, what the chip did)</resume-signal>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| debug-build → release-build | Compile-time gate (`BuildConfig.DEBUG` / `#if DEBUG`) prevents debug code from reaching production users |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-DBG-01 | Tampering | DebugGeofenceProvider leaking to release | mitigate | Compile-time gates: BuildConfig.DEBUG on Android (only true for `debug` buildType), `#if DEBUG` on iOS (only compiled when Xcode build config = Debug). Verified via grep that PlatformModule.ios.kt and PlatformModule.android.kt contain zero references to DebugGeofenceProvider. Final manual verify step: build release APK + release iOS archive, confirm debug Section absent. |
| T-DBG-02 | Elevation of Privilege | Manual exit trigger evading XP penalty in production | mitigate | Same as T-DBG-01 — release builds cannot load the debug panel because the UI is wrapped in `#if DEBUG` / `BuildConfig.DEBUG`. No runtime flag, no env var, no hidden gesture. |
| T-DBG-03 | Spoofing | Debug trigger using wrong regionId | accept | Panel reads regionId from the same source the VM uses (`WorkoutRepository.getActiveSession()?.startTimeMillis`). If the active session changes mid-tap, worst case is a no-op event (VM filters by regionId). Low-impact debug-only code path. |
</threat_model>

<verification>
- DebugGeofenceProvider exists in commonMain and implements GeofenceProvider correctly.
- BuildConfig is enabled in androidApp/build.gradle.kts.
- Koin override loads in MainActivity (Android) and AppDelegate (iOS) ONLY when respective DEBUG flag is set.
- PlatformModule.android.kt and PlatformModule.ios.kt remain untouched (no DebugGeofenceProvider references).
- Both debug panels are gated by compile-time DEBUG flags.
- Both DEBUG builds compile; release builds do NOT include debug panel UI.
- Manual UAT (Task 3) passes on both platforms.
</verification>

<success_criteria>
- In a DEBUG build on either platform, the user can:
  1. Start a workout, log a set.
  2. Open Settings, see the DEBUG — Geofence Mock panel.
  3. Tap Trigger Exit → chip transitions to GracePeriod with countdown.
  4. Tap Trigger Enter → chip returns to InZone.
  5. Tap Trigger Error → chip transitions to Inactive.
- In a RELEASE build, the debug panel is invisible AND the real GeofenceProvider (Ios/Android actual) is the only binding resolved.
- All automated verification commands in tasks 1 + 2 pass.
- Manual UAT (task 3) returns "approved".
</success_criteria>

<output>
After completion, create `.planning/quick/260516-nfn-debug-gps-mock-f-r-phase-19-geofencing-b/260516-nfn-SUMMARY.md` per the standard quick-task summary template.
</output>
