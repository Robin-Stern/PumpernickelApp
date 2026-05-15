---
phase: 19-geofencing-workout-enforcement
plan: "06"
subsystem: ios-ui
tags: [ios, swiftui, geofence, notifications, settings, kmp-native-coroutines]
dependency_graph:
  requires: [19-03, 19-05]
  provides: [ios-phase19-ui-surface]
  affects: [WorkoutSessionView, SettingsView]
tech_stack:
  added:
    - UserNotifications framework (UNUserNotificationCenter local notifications)
    - KMPNativeCoroutinesCore import in WorkoutEnforcementDetailView
  patterns:
    - asyncSequence(for:) to observe @NativeCoroutinesState flows
    - Direct Swift async bridge for Kotlin suspend funs (KMP-NativeCoroutines 1.0.2+)
    - @AppStorage for cross-view-rebuild flag persistence with cold-start reset in AppDelegate
    - .confirmationDialog(presenting:) for type-safe dialog configuration
    - Manual pbxproj editing (PBXBuildFile + PBXFileReference + PBXGroup + PBXSourcesBuildPhase)
key_files:
  created:
    - iosApp/iosApp/Views/Workout/GeofenceStatusChip.swift
    - iosApp/iosApp/Views/Workout/PermissionRationaleSheet.swift
    - iosApp/iosApp/Views/Workout/PermissionBanner.swift
    - iosApp/iosApp/Views/Workout/EarlyExitConfirmDialog.swift
    - iosApp/iosApp/Utilities/NotificationCenter+Geofence.swift
    - iosApp/iosApp/Views/Settings/WorkoutEnforcementDetailView.swift
  modified:
    - iosApp/iosApp/Views/Workout/WorkoutSessionView.swift
    - iosApp/iosApp/Views/Settings/SettingsView.swift
    - iosApp/iosApp/AppDelegate.swift
    - iosApp/iosApp.xcodeproj/project.pbxproj
decisions:
  - "earlyExitBudget sourced from WorkoutSessionViewModel.earlyExitBudgetFlow (@NativeCoroutinesState) not EarlyExitTracker.budget (raw Kotlinx_coroutines_coreFlow) — both read the same DataStore stream but only the VM flow is properly exported"
  - "asyncFunction(for:) replaced with direct Swift async bridge throughout — KMP-NativeCoroutines 1.0.2+ exports suspend funs as native Swift async"
  - "graceExpired notification placed in handleGeofenceStateChange() not .onChange(of:) — GeofenceUiState does not auto-conform to Equatable via KMP"
  - "WARN-19-2: rationale flag uses @AppStorage + UserDefaults.set(false) in AppDelegate for cold-start reset semantics"
metrics:
  duration: "~90min"
  completed: "2026-05-15"
  tasks_completed: 3
  tasks_total: 4
  files_changed: 10
---

# Phase 19 Plan 06: iOS UI Surface — Status Chip, Rationale, Banners, Notifications Summary

**One-liner:** SwiftUI phase-19 UI surface — 4-state geofence chip, permission rationale/banners, early-exit confirm dialog, Settings detail view, and 5 UNUserNotificationCenter triggers wired into WorkoutSessionView.

## Tasks Executed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | New SwiftUI components + Notifications helper | 248f237 | GeofenceStatusChip, PermissionRationaleSheet, PermissionBanner, EarlyExitConfirmDialog, NotificationCenter+Geofence, project.pbxproj |
| 2 | Wire into WorkoutSessionView + AppDelegate | 4a0525e | WorkoutSessionView.swift, AppDelegate.swift |
| 3 | Settings Training section + WorkoutEnforcementDetailView | 928200e | SettingsView.swift, WorkoutEnforcementDetailView.swift |
| Fix | Build fixes — earlyExitBudget source, missing pbxproj refs, type fix | c7cf4dd | WorkoutEnforcementDetailView.swift, project.pbxproj, WorkoutSessionView.swift |
| Fix | Remove deprecated asyncFunction(for:) calls | 9086f26 | WorkoutSessionView.swift |
| 4 | Visual UAT | — | checkpoint:human-verify (PENDING) |

## KMP-Native-Coroutines Property Names

The following NativeCoroutinesState-exported property names were verified against the compiled Shared.framework:

- `viewModel.geofenceStateFlow` — type: NativeFlow of GeofenceUiState (from @NativeCoroutinesState on WorkoutSessionViewModel)
- `viewModel.earlyExitBudgetFlow` — type: NativeFlow of EarlyExitBudget (from @NativeCoroutinesState on WorkoutSessionViewModel)

Both use the `*Flow` naming convention from KMP-NativeCoroutines.

## Equatable Conformance on GeofenceUiState

GeofenceUiState does NOT auto-conform to Equatable via KMP. The `.onChange(of: geofenceState)` approach from the plan was replaced with logic moved into `handleGeofenceStateChange(old:new:)` which receives both old and new values and performs `is GeofenceUiState.*` pattern matching. No Equatable extension needed.

## pbxproj Manual Edits

5 new Phase-19 Swift files registered (Task 1):
- B19060/A19060: GeofenceStatusChip.swift → Workout group + Sources
- B19061/A19061: PermissionRationaleSheet.swift → Workout group + Sources
- B19062/A19062: PermissionBanner.swift → Workout group + Sources
- B19063/A19063: EarlyExitConfirmDialog.swift → Workout group + Sources
- B19064/A19064: NotificationCenter+Geofence.swift → Utilities group + Sources
- B19065/A19065: WorkoutEnforcementDetailView.swift → Settings group + Sources

Pre-existing missing refs fixed (deviation):
- B10140/A10140: TutorialOverlayView.swift → new Onboarding group + Sources
- B10141/A10141: RankLadderView.swift → Gamification group + Sources

## UAT Results

Task 4 is a `checkpoint:human-verify` — UAT must be run by the user on a real iOS device (geofencing requires GPS; Simulator does not deliver native region events). The 12-step UAT protocol is documented in 19-06-PLAN.md Task 4.

Build verification (automated): `xcodebuild build -scheme iosApp -destination 'generic/platform=iOS Simulator'` exits 0.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] WorkoutEnforcementDetailView: earlyExitTracker.budget unusable with asyncSequence**
- **Found during:** Task 3 build verification
- **Issue:** `EarlyExitTracker.budget` is a raw `Kotlinx_coroutines_coreFlow` protocol in Swift. `asyncSequence(for:)` requires a `NativeFlow<T,E,U>` typed argument; Swift cannot infer the type parameter from the protocol.
- **Fix:** Pivoted budget source to `WorkoutSessionViewModel.earlyExitBudgetFlow` which is `@NativeCoroutinesState` (properly exported as NativeFlow). Both read the same `SettingsRepository.earlyExits` DataStore stream — data is identical.
- **Files modified:** WorkoutEnforcementDetailView.swift
- **Commit:** c7cf4dd

**2. [Rule 3 - Blocker] Pre-existing missing pbxproj file references blocking build**
- **Found during:** Build verification pass
- **Issue:** `TutorialOverlayView.swift` (Onboarding/) and `RankLadderView.swift` (Gamification/) existed on disk but had no PBXFileReference/PBXBuildFile entries in project.pbxproj, causing "cannot find 'X' in scope" errors.
- **Fix:** Added B10140/A10140 TutorialOverlayView + new Onboarding group; B10141/A10141 RankLadderView in Gamification group.
- **Files modified:** project.pbxproj
- **Commit:** c7cf4dd

**3. [Rule 1 - Bug] Pre-existing type mismatch in WorkoutSessionView: [MuscleGroup] vs [UndertrainedMuscle]**
- **Found during:** Build verification pass after adding pre-existing missing file refs
- **Issue:** `@State private var undertrainedMuscles: [MuscleGroup]` but `undertrainedMusclesFlow` emits `[UndertrainedMuscle]`. Also `$0.displayName` → should be `$0.group.displayName` since `UndertrainedMuscle.group: MuscleGroup`.
- **Fix:** Changed type to `[UndertrainedMuscle]` and updated mapping.
- **Files modified:** WorkoutSessionView.swift
- **Commit:** c7cf4dd

**4. [Rule 1 - Bug] Deprecated asyncFunction(for:) in WorkoutSessionView (our code)**
- **Found during:** Post-build review of code introduced in Task 2
- **Issue:** KMP-NativeCoroutines 1.0.2+ exports Kotlin suspend funs as native Swift async functions; `asyncFunction(for:)` is deprecated.
- **Fix:** Replaced all 5 `asyncFunction(for: permissionController.*)` calls with direct `try? await permissionController.*()`.
- **Files modified:** WorkoutSessionView.swift
- **Commit:** 9086f26

## Self-Check

### Files created/modified exist:

- FOUND: iosApp/iosApp/Views/Workout/GeofenceStatusChip.swift
- FOUND: iosApp/iosApp/Views/Workout/PermissionRationaleSheet.swift
- FOUND: iosApp/iosApp/Views/Workout/PermissionBanner.swift
- FOUND: iosApp/iosApp/Views/Workout/EarlyExitConfirmDialog.swift
- FOUND: iosApp/iosApp/Utilities/NotificationCenter+Geofence.swift
- FOUND: iosApp/iosApp/Views/Settings/WorkoutEnforcementDetailView.swift
- FOUND: iosApp/iosApp/Views/Workout/WorkoutSessionView.swift
- FOUND: iosApp/iosApp/Views/Settings/SettingsView.swift
- FOUND: iosApp/iosApp/AppDelegate.swift

### Commits exist:

- FOUND: 248f237 (feat: new SwiftUI components)
- FOUND: 4a0525e (feat: WorkoutSessionView wiring)
- FOUND: 928200e (feat: Settings + WorkoutEnforcementDetailView)
- FOUND: c7cf4dd (fix: build fixes)
- FOUND: 9086f26 (fix: asyncFunction deprecation)

### Build verification:

`xcodebuild build -scheme iosApp -destination 'generic/platform=iOS Simulator'` — BUILD SUCCEEDED

## Self-Check: PASSED
