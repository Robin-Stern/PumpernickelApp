---
phase: 19-geofencing-workout-enforcement
plan: 04
subsystem: android-platform
tags: [android, kmp, play-services, permissions, manifest, kotlin, cold-start, koin]

requires:
  - phase: 19-geofencing-workout-enforcement
    plan: 01
    provides: GeofenceProvider, PermissionController, PendingGeofenceExitStore, GeofenceEvent, PendingGeofenceExit interfaces in commonMain

provides:
  - AndroidGeofenceProvider: Play Services GeofencingClient actual for GeofenceProvider (D-19-01)
  - GeofenceBroadcastReceiver: statically-registered receiver with goAsync() + cold-start sentinel persistence (D-19-04, BLOCKER-19-3 fix)
  - AndroidPermissionController: ActivityResultContracts actual for PermissionController (D-19-12)
  - PermissionActivityHolder: activity lifecycle holder for permission launchers (D-19-12)
  - PlatformModule.android.kt: Koin bindings for GeofenceProvider, PermissionController, PendingGeofenceExitStore, EarlyExitTracker
  - AndroidManifest: ACCESS_FINE_LOCATION, ACCESS_BACKGROUND_LOCATION, POST_NOTIFICATIONS, FOREGROUND_SERVICE_LOCATION + GeofenceBroadcastReceiver exported=false

affects:
  - 19-05 (Wave 3 VM: consumePendingExit() + geofence event subscription — both providers now complete)
  - 19-06 (UI: geofence chip, Early-Exit button — Wave 4)

tech-stack:
  added:
    - com.google.android.gms:play-services-location (already declared as libs.play.services.location in shared/build.gradle.kts before this plan — no new dependency added)
  patterns:
    - "GeofenceBroadcastReceiver uses goAsync() + withTimeoutOrNull(8s) to persist PendingGeofenceExit to DataStore before 10s receiver budget elapses (BLOCKER-19-3 cold-start fix)"
    - "PermissionActivityHolder mirrors BiometricGateActivityHolder: object with attach/detach, registers ActivityResultContracts launchers in MainActivity.onCreate BEFORE setContent"
    - "AndroidGeofenceProvider.Companion.SHARED_EVENTS: singleton MutableSharedFlow shared between Koin-resolved instance (read) and framework-instantiated BroadcastReceiver (write)"
    - "GlobalContext.getOrNull() used in BroadcastReceiver to safely resolve Koin without crashing if the Application hasn't started yet"

key-files:
  created:
    - shared/src/androidMain/kotlin/com/pumpernickel/feature/geofence/AndroidGeofenceProvider.kt
    - shared/src/androidMain/kotlin/com/pumpernickel/feature/geofence/GeofenceBroadcastReceiver.kt
    - shared/src/androidMain/kotlin/com/pumpernickel/feature/permissions/PermissionActivityHolder.kt
    - shared/src/androidMain/kotlin/com/pumpernickel/feature/permissions/AndroidPermissionController.kt
  modified:
    - androidApp/src/androidMain/AndroidManifest.xml
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/MainActivity.kt
    - shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt

key-decisions:
  - "play-services-location was already declared in shared/build.gradle.kts (libs.play.services.location) — no new Gradle dependency needed"
  - "PendingGeofenceExitStore binding is in PlatformModule.android.kt only (not SharedModule) — single<PendingGeofenceExitStore> { get<SettingsRepository>() } — avoids double-binding since SharedModule binds SettingsRepository as a concrete type"
  - "GeofencingEvent.fromIntent() is marked @Deprecated in newer Play Services SDK; suppress with @Suppress(DEPRECATION) — it remains the only API for extracting geofence data from a PendingIntent-delivered broadcast"
  - "EarlyExitTracker takes SettingsRepository (not PendingGeofenceExitStore) per its constructor — resolves correctly since SettingsRepository is already in the Koin graph"

metrics:
  duration: "~12min"
  completed: "2026-05-15T15:07:39Z"
  tasks: 3
  files_created: 4
  files_modified: 3
---

# Phase 19 Plan 04: Android Geofence + Permission Stack Summary

**Play Services GeofencingClient actual + goAsync() cold-start sentinel BroadcastReceiver (BLOCKER-19-3 fix) + ActivityResultContracts PermissionController + Koin wiring for Wave 3 VM**

## Performance

- **Duration:** ~12 min
- **Started:** 2026-05-15T14:55:00Z
- **Completed:** 2026-05-15T15:07:39Z
- **Tasks:** 3
- **Files modified:** 7 (4 created, 3 modified)

## Accomplishments

- Shipped `AndroidGeofenceProvider` with Play Services `GeofencingClient`; singleton `SHARED_EVENTS` companion flow accessible by both the Koin-resolved instance and the framework-instantiated `BroadcastReceiver`
- Shipped `GeofenceBroadcastReceiver` with full BLOCKER-19-3 cold-start fix: `goAsync()` + `withTimeoutOrNull(8s)` + Koin-resolved `PendingGeofenceExitStore.setPendingExit()` — the EXIT sentinel is safely persisted to DataStore before the OS's 10-second receiver budget expires
- ENTER path clears the sentinel (guards against stale reconciliation if user returns before VM resumes)
- Shipped `AndroidPermissionController` (all 5 `PermissionController` methods) + `PermissionActivityHolder` (mirrors BiometricGateActivityHolder pattern, registered before `setContent`)
- `AndroidManifest.xml` now declares all 4 Phase 19 permissions + `GeofenceBroadcastReceiver` with `exported="false"` (T-19-04-01 mitigation)
- Koin platform module wires `GeofenceProvider`, `PermissionController`, `PendingGeofenceExitStore`, and `EarlyExitTracker` — Wave 3 VM can resolve all four types on Android

## Task Commits

1. **Task 1: AndroidGeofenceProvider + GeofenceBroadcastReceiver + Manifest + gradle dep** - `c0d4929` (feat)
2. **Task 2: AndroidPermissionController + PermissionActivityHolder + MainActivity attach** - `62edc8d` (feat)
3. **Task 3: Wire Android Koin (PlatformModule.android.kt) + PendingGeofenceExitStore binding** - `91f3926` (feat)

## Files Created/Modified

**Created:**
- `shared/src/androidMain/kotlin/com/pumpernickel/feature/geofence/AndroidGeofenceProvider.kt` — GeofencingClient actual; singleton SHARED_EVENTS companion; idempotent register/unregister; checks ACCESS_BACKGROUND_LOCATION
- `shared/src/androidMain/kotlin/com/pumpernickel/feature/geofence/GeofenceBroadcastReceiver.kt` — goAsync() + Koin-resolved PendingGeofenceExitStore; warm path via SHARED_EVENTS; BLOCKER-19-3 fix
- `shared/src/androidMain/kotlin/com/pumpernickel/feature/permissions/PermissionActivityHolder.kt` — object holder; attach/detach; registers RequestMultiplePermissions + 2x RequestPermission launchers
- `shared/src/androidMain/kotlin/com/pumpernickel/feature/permissions/AndroidPermissionController.kt` — all 5 PermissionController methods; openAppSettings() via ACTION_APPLICATION_DETAILS_SETTINGS

**Modified:**
- `androidApp/src/androidMain/AndroidManifest.xml` — added 4 permissions (ACCESS_FINE_LOCATION, ACCESS_BACKGROUND_LOCATION, POST_NOTIFICATIONS, FOREGROUND_SERVICE_LOCATION) + GeofenceBroadcastReceiver receiver declaration (exported=false)
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/MainActivity.kt` — PermissionActivityHolder.attach(this) before setContent, detach() in onDestroy
- `shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt` — 4 new Koin singles: PendingGeofenceExitStore, GeofenceProvider, PermissionController, EarlyExitTracker

## Decisions Made

- `play-services-location` was already declared (`libs.play.services.location`) — no build.gradle.kts edit required
- `PendingGeofenceExitStore` binding placed in `PlatformModule.android.kt` only (not `SharedModule`) to avoid double-binding: `SharedModule` registers `SettingsRepository` as a concrete type; the platform module adds the interface binding on top
- `GeofencingEvent.fromIntent()` is `@Deprecated` in newer Play Services SDK; suppressed with `@Suppress("DEPRECATION")` as it remains the only API for extracting geofence data from a `PendingIntent`-delivered broadcast
- `EarlyExitTracker(get())` in the Koin module correctly resolves `SettingsRepository` (not `PendingGeofenceExitStore`) per the EarlyExitTracker constructor signature

## Deviations from Plan

None — plan executed exactly as written, with one note: `play-services-location` was already present in `shared/build.gradle.kts` (task instruction said to add it "if not yet declared"), so Task 1's EDIT A was a no-op. All other edits proceeded as specified.

## STRIDE Mitigations Confirmed

| Threat | Mitigation | Verified |
|--------|-----------|---------|
| T-19-04-01: Forged broadcast intent | `android:exported="false"` on `<receiver>` | Manifest grep: 1 match |
| T-19-04-07: goAsync() 10s budget | `withTimeoutOrNull(8_000L)` + `finally { pendingResult.finish() }` | 2x goAsync() calls in receiver |
| T-19-04-08: Race on sentinel consume | `setPendingExit` overwrite semantics; Wave 3 VM uses DataStore `edit{}` atomic consume | Confirmed in BroadcastReceiver |

## Notes for Wave 3 (Plan 05 — ViewModel integration)

- `WorkoutSessionViewModel.checkForActiveSession()` **MUST** call `consumePendingExit()` BEFORE subscribing to `geofenceProvider.events` — the BroadcastReceiver may have written the sentinel before the VM was instantiated
- Cold-start sentinel flow: BroadcastReceiver writes `PendingGeofenceExit(workoutId, exitTimeMillis, regionId)` → VM resumes → `consumePendingExit()` returns the payload → validate `regionId == "active-workout-${session.startTimeMillis}"` → call `handleGeofenceExitGraceExpired(...)` directly (no grace timer — OS has already reported the EXIT)
- On `GeofenceEvent.Enter` during a warm session: cancel grace timer AND call `setPendingExit(null)` to invalidate any sentinel written in a race

## Self-Check

Checking all created files and commits exist:

- [x] `AndroidGeofenceProvider.kt` — FOUND
- [x] `GeofenceBroadcastReceiver.kt` — FOUND
- [x] `PermissionActivityHolder.kt` — FOUND
- [x] `AndroidPermissionController.kt` — FOUND
- [x] Commit `c0d4929` — Task 1
- [x] Commit `62edc8d` — Task 2
- [x] Commit `91f3926` — Task 3

## Self-Check: PASSED

---
*Phase: 19-geofencing-workout-enforcement*
*Completed: 2026-05-15*
