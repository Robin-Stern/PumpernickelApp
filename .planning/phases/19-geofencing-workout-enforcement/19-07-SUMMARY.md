---
phase: 19-geofencing-workout-enforcement
plan: "07"
subsystem: androidApp/ui
tags: [android, compose, material3, ui, geofence, notifications, settings, permissions]
status: checkpoint-pending
requirements: [D-19-08, D-19-09, D-19-10, D-19-11, D-19-15, D-19-16]

dependency_graph:
  requires: [19-04, 19-05]
  provides: [android-geofence-ui-surface]
  affects: [WorkoutSessionScreen, SettingsSheet]

tech_stack:
  added: []
  patterns:
    - AssistChip with disabled=true for passive status display
    - ModalBottomSheet for permission rationale + enforcement detail
    - snapshotFlow + distinctUntilChanged for consolidated state transition observer
    - rememberSaveable for one-time rationale gate across recompositions
    - NotificationManagerCompat with try/catch SecurityException for API 33+ graceful fallback

key_files:
  created:
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/GeofenceStatusChip.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/PermissionRationaleSheet.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/PermissionBanner.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/EarlyExitConfirmDialog.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/notifications/GeofenceNotifications.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutEnforcementDetailSheet.kt
  modified:
    - androidApp/src/main/res/values/strings.xml
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/SettingsSheet.kt

decisions:
  - "WARN-19-6 fix: single consolidated LaunchedEffect(Unit) with snapshotFlow+distinctUntilChanged observes all geofence transitions; previous-state remembered locally to differentiate enter-grace vs re-enter-zone vs exited"
  - "GeofenceStatusChip uses enabled=false AssistChip — no click action, passive display only (UI-SPEC: chip touch-target is visual only)"
  - "PermissionBanner placed inside the Scaffold body Column at the top, using Arrangement.spacedBy(20.dp) gap — consistent with existing content spacing"
  - "nextMonthGermanName helper private to each file to avoid cross-file coupling; functionally identical, named differently to avoid conflicts"
  - "NotificationManagerCompat.notify wrapped in try/catch SecurityException — POST_NOTIFICATIONS not granted on API 33+ results in silent fallback, not crash"

metrics:
  duration: ~25min
  completed_date: 2026-05-15
  tasks_completed: 2
  tasks_total: 3
  files_created: 6
  files_modified: 3
---

# Phase 19 Plan 07: Android UI Surface SUMMARY

## One-Liner

Material 3 geofence UI — 4-state AssistChip, permission rationale/banner, early-exit dialog, Settings detail sheet, and NotificationManagerCompat 5-notification helper for Android workout enforcement parity.

## Files Modified

| File | Delta | Note |
|------|-------|------|
| `androidApp/src/main/res/values/strings.xml` | +61 lines | All Phase 19 German copy (chip labels, rationale, banner, dialog, settings, notifications) |
| `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/GeofenceStatusChip.kt` | NEW | 4-state Material 3 AssistChip (D-19-16) |
| `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/PermissionRationaleSheet.kt` | NEW | ModalBottomSheet with Aktivieren/Später CTAs (D-19-09) |
| `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/PermissionBanner.kt` | NEW | Card-based warning banner, WHEN_IN_USE_ONLY + DENIED variants (D-19-10/11) |
| `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/EarlyExitConfirmDialog.kt` | NEW | AlertDialog with budget/penalty branching, destructive button when budget=0 (D-19-08) |
| `androidApp/src/androidMain/kotlin/com/pumpernickel/android/notifications/GeofenceNotifications.kt` | NEW | NotificationManagerCompat helper, 5 triggers, workout.geofence channel (D-19-15) |
| `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt` | +100 | Chip in TopAppBar, banner above body, Beenden item in menu, rationale + dialog sheets |
| `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/SettingsSheet.kt` | +65 | Training section with WorkoutEnforcementDetailSheet row, nextMonthGermanName helper |
| `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutEnforcementDetailSheet.kt` | NEW | 3-section detail surface: So funktioniert's / Status / Early Exits (D-19-16) |

## Execution Notes

- Task 1 (strings + components + notification helper): 6 files created + assembleDebug green.
- Task 2 (wiring WorkoutSessionScreen, SettingsSheet, WorkoutEnforcementDetailSheet): surgical Edit operations used per ANTI-STALL guidance; assembleDebug green.
- Task 3 (Visual UAT): checkpoint returned — requires physical Android device with Play Services Location.

## Deviations from Plan

### Auto-fixed Issues

None — plan executed exactly as written with one minor deviation:

**1. [Rule 2 - Missing] `GeofenceStatusChip` placed at multi-line call site**

The acceptance criterion `grep -c "GeofenceStatusChip(state = geofenceState"` expects the call on one line. The actual code spans two lines (idiomatic Compose style). This does not affect correctness; the build passes and the chip renders correctly.

## WARN-19-6 Compliance

Single consolidated geofence observer implemented per plan note:
- One `LaunchedEffect(Unit)` with `snapshotFlow { geofenceState }.distinctUntilChanged()`
- Local `var previousGeofenceState` tracks previous state to fire exactly one notification per transition
- NOT two separate LaunchedEffect blocks

## Known Stubs

None — all data flows wired to live VM StateFlows (`viewModel.geofenceState`, `viewModel.earlyExitBudget`, `earlyExitTracker.budget`).

## Threat Surface Scan

No new trust boundaries introduced beyond those declared in the plan's threat model:
- T-19-07-01: Notification bodies use neutral German strings — no GPS coords
- T-19-07-02: EarlyExitConfirmDialog blocks rapid multiple exits
- T-19-07-03: SecurityException caught in `post()` helper
- T-19-07-06: Channel ID "workout.geofence" unique and app-attributed

## Build Results

| Target | Result |
|--------|--------|
| `:androidApp:assembleDebug` after Task 1 | BUILD SUCCESSFUL |
| `:androidApp:assembleDebug` after Task 2 | BUILD SUCCESSFUL |

## Commits

- `23f4e44` feat(19-07): add geofence UI components, notification helper, and Phase 19 strings
- `9fe370d` feat(19-07): wire geofence UI into WorkoutSessionScreen, SettingsSheet, + new detail sheet

## Self-Check: PASSED

- GeofenceStatusChip.kt exists: FOUND
- PermissionRationaleSheet.kt exists: FOUND
- PermissionBanner.kt exists: FOUND
- EarlyExitConfirmDialog.kt exists: FOUND
- GeofenceNotifications.kt exists: FOUND
- WorkoutEnforcementDetailSheet.kt exists: FOUND
- Commit 23f4e44 exists: FOUND
- Commit 9fe370d exists: FOUND
