---
phase: quick-260516-nfn
plan: "01"
subsystem: geofencing-debug
tags: [debug, geofencing, koin, ios, android, phase-19]
dependency_graph:
  requires:
    - phase-19 (IosGeofenceProvider, AndroidGeofenceProvider, GeofenceProvider interface, WorkoutSessionViewModel regionId schema)
  provides:
    - DebugGeofenceProvider (manual-trigger mock, commonMain)
    - Build-gated Koin override (Android BuildConfig.DEBUG + iOS #if DEBUG)
    - Debug Settings panels (Enter/Exit/Error trigger buttons) on both platforms
  affects:
    - shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt (allowOverride)
    - shared/src/iosMain/kotlin/com/pumpernickel/di/KoinHelper.kt (new debug bridge functions)
tech_stack:
  added:
    - DebugGeofenceProvider (commonMain) — manual-trigger GeofenceProvider mock
    - BuildConfig.DEBUG gate in PumpernickelApplication.kt
    - KoinHelper.loadDebugGeofenceOverride() + getDebugGeofenceProvider() + getWorkoutRepository()
    - #if DEBUG gate in AppDelegate.swift
    - DebugGeofencePanel.kt (Android Compose)
    - DebugGeofencePanel.swift (iOS SwiftUI)
  patterns:
    - Koin allowOverride(true) + loadKoinModules for post-startKoin binding replacement
    - lastRegisteredRegionId synchronous property for iOS regionId read (avoids Kotlin/Swift async bridge)
key_files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/data/geofence/DebugGeofenceProvider.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/DebugGeofencePanel.kt
    - iosApp/iosApp/Views/Settings/DebugGeofencePanel.swift
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/di/KoinHelper.kt
    - androidApp/build.gradle.kts
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/PumpernickelApplication.kt
    - iosApp/iosApp/AppDelegate.swift
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/SettingsSheet.kt
    - iosApp/iosApp/Views/Settings/SettingsView.swift
    - iosApp/iosApp.xcodeproj/project.pbxproj
decisions:
  - "@Volatile removed: not available in commonMain Kotlin; replaced with plain var (debug-only, no concurrency concern)"
  - "override = true removed: Koin 4.x deprecated it; replaced with allowOverride(true) on startKoin + plain single<T>"
  - "lastRegisteredRegionId used for iOS regionId (synchronous) instead of async suspend bridge via @NativeCoroutines — avoids KMP-NativeCoroutines object/iosMain annotation complexity"
  - "Koin override loaded in PumpernickelApplication.onCreate (after initKoin), not in MainActivity — Application is the correct Koin bootstrap site in this project"
metrics:
  completed_date: "2026-05-16"
  tasks_completed: 2
  tasks_total: 3
  files_created: 3
  files_modified: 8
---

# Quick Task 260516-nfn: Debug GPS Mock for Phase 19 Geofencing — Summary

**One-liner:** Debug-only DebugGeofenceProvider (commonMain) with manual trigger buttons in Settings, build-gated via BuildConfig.DEBUG (Android) and #if DEBUG (iOS), so Phase 19 geofence flows can be UAT'd on Simulator/Emulator without real GPS.

## What Was Built

### Task 1 — DebugGeofenceProvider + Build-gated Koin Binding (commits: a5efe3c)

- **DebugGeofenceProvider** created in `shared/src/commonMain/kotlin/com/pumpernickel/data/geofence/`. Implements `GeofenceProvider` with a `MutableSharedFlow(replay=0, extraBufferCapacity=16)`. Three imperative trigger methods: `triggerEnter(regionId)`, `triggerExit(regionId)`, `triggerError(regionId, message)`. Stores `lastRegisteredRegionId` from `register()` for use by the iOS panel.
- **BuildConfig** enabled in `androidApp/build.gradle.kts` via `buildConfig = true` + explicit `debug`/`release` buildTypes.
- **Android Koin override** loaded in `PumpernickelApplication.onCreate` (after `initKoin`) inside `if (BuildConfig.DEBUG)` block.
- **iOS Koin override** called from `AppDelegate.application(_:didFinishLaunchingWithOptions:)` inside `#if DEBUG` block, BEFORE force-resolving the provider.
- **`allowOverride(true)`** added to `startKoin { }` in `SharedModule.initKoin()` — required for Koin 4.x post-startKoin binding replacement.
- **KoinHelper** extended with: `getWorkoutRepository()`, `loadDebugGeofenceOverride()`, `getDebugGeofenceProvider()`.

### Task 2 — Debug Settings Panels (commit: 108b622)

- **DebugGeofencePanel.kt** (Android Compose): shows region id from `WorkoutRepository.getActiveSession()`, three `Button` composables wired to `DebugGeofenceProvider.trigger*()`. Disabled when no active workout or provider is not `DebugGeofenceProvider`.
- **DebugGeofencePanel.swift** (iOS SwiftUI): reads `lastRegisteredRegionId` synchronously from `DebugGeofenceProvider` via `KoinHelper.shared.getDebugGeofenceProvider()`. Same three buttons. Refreshed `.onAppear`.
- Both panels wired into their respective Settings screens inside compile-time DEBUG gates.
- `DebugGeofencePanel.swift` registered in `project.pbxproj` (B19070 + A19070, Settings group + Sources phase).

## Task 3 — Manual Smoke Test (PENDING)

Task 3 is a `checkpoint:human-verify` — awaiting manual UAT on Android Emulator + iOS Simulator.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] @Volatile not available in commonMain**
- **Found during:** Task 1 compile
- **Issue:** `@Volatile` is a JVM/Android-only annotation, not available in Kotlin multiplatform commonMain
- **Fix:** Removed annotation — `lastRegisteredId` is debug-only, single-thread access in practice; field read is safe without the annotation
- **Files modified:** `shared/src/commonMain/kotlin/com/pumpernickel/data/geofence/DebugGeofenceProvider.kt`
- **Commit:** a5efe3c

**2. [Rule 1 - Bug] override = true not available in Koin 4.x**
- **Found during:** Task 1 compile (iOS target)
- **Issue:** `single<T>(override = true)` parameter was removed in Koin 4.x
- **Fix:** Added `allowOverride(true)` to `startKoin { }` in SharedModule.initKoin(); removed `override = true` from both Android and iOS debug module definitions
- **Files modified:** `shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt`, `PumpernickelApplication.kt`, `KoinHelper.kt`
- **Commit:** a5efe3c

**3. [Rule 1 - Bug] @NativeCoroutines on iosMain suspend fun not generating Swift async bridge**
- **Found during:** Task 2 iOS build — `getActiveRegionIdAsync` member not found
- **Issue:** `@NativeCoroutines` on `object` members in `iosMain` (not `commonMain`) does not generate `Async`-suffixed Swift wrappers; the KMP-NativeCoroutines Gradle plugin only processes `commonMain` sources
- **Fix:** Replaced async regionId lookup with synchronous `DebugGeofenceProvider.lastRegisteredRegionId` property (set by `register()` when WorkoutSessionViewModel starts the geofence). This is semantically equivalent for the debug use case: the VM always calls `register()` before the user can navigate to Settings.
- **Files modified:** `KoinHelper.kt` (removed `@NativeCoroutines suspend fun getActiveRegionId()`), `DebugGeofencePanel.swift` (use `lastRegisteredRegionId` via `getDebugGeofenceProvider()`)
- **Commit:** 108b622

**4. [Rule 3 - Deviation] Koin initialized in PumpernickelApplication, not MainActivity**
- **Found during:** Task 1 — plan said to add debug override in `MainActivity.kt`
- **Issue:** `initKoin` is called in `PumpernickelApplication.onCreate`, not in `MainActivity`
- **Fix:** Placed `loadKoinModules` in `PumpernickelApplication.kt` after `initKoin { }` — semantically identical, correct location
- **Files modified:** `PumpernickelApplication.kt` (not `MainActivity.kt`)
- **Commit:** a5efe3c

## Threat Surface Scan

T-DBG-01 and T-DBG-02 mitigations verified:
- `PlatformModule.ios.kt`: 0 references to `DebugGeofenceProvider` (grep confirmed)
- `PlatformModule.android.kt`: 0 references to `DebugGeofenceProvider` (grep confirmed)
- Android gate: `BuildConfig.DEBUG` evaluated at compile time — false in `release` buildType
- iOS gate: `#if DEBUG` is a compile-time conditional — `DebugGeofencePanel.swift` body not compiled in Release

No new threat surface introduced beyond what the plan's threat model anticipates.

## Known Stubs

None — all panels are fully wired. The regionId display shows `"(no active workout)"` when no session is active, which is the intended behavior (buttons are disabled in that state).

## Self-Check: PASSED

| Check | Result |
|-------|--------|
| DebugGeofenceProvider.kt exists | FOUND |
| Android DebugGeofencePanel.kt exists | FOUND |
| iOS DebugGeofencePanel.swift exists | FOUND |
| Commit a5efe3c exists | FOUND |
| Commit 108b622 exists | FOUND |
| PlatformModule.ios.kt has 0 DebugGeofenceProvider refs | 0 (PASS) |
| PlatformModule.android.kt has 0 DebugGeofenceProvider refs | 0 (PASS) |
| Android build (assembleDebug) | BUILD SUCCESSFUL |
| iOS build (generic/platform=iOS Simulator) | BUILD SUCCEEDED |
