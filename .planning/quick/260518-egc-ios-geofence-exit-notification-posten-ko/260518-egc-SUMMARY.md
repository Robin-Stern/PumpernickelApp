---
phase: quick-260518-egc
plan: 01
subsystem: ios-notifications
tags: [ios, notifications, geofence, parity, swift, appdelegate]
requires:
  - iosApp/iosApp/Utilities/NotificationCenter+Geofence.swift  # postGeofenceNotification helper (already in place)
  - WorkoutSessionView.swift call-sites (handleGeofenceStateChange + Early-Exit-Dialog)  # untouched, already posting
provides:
  - "UN authorization request at first launch ([.alert, .sound, .badge])"
  - "UNUserNotificationCenter delegate wired to AppDelegate"
  - "Foreground presentation policy ([.banner, .sound, .list]) for geofence notifications"
affects:
  - iosApp/iosApp/AppDelegate.swift
tech-stack:
  added: []
  patterns:
    - "UIApplicationDelegateAdaptor → AppDelegate as UNUserNotificationCenterDelegate (idiomatic UN setup in didFinishLaunching)"
key-files:
  created: []
  modified:
    - iosApp/iosApp/AppDelegate.swift
decisions:
  - "Authorization request placed in AppDelegate.didFinishLaunching (not PumpernickelApp.init) so the delegate is wired before any postGeofenceNotification call site can fire."
  - "Foreground willPresent returns [.banner, .sound, .list] — matches the Debug-Sheet Mock-Exit test path where the app is in the foreground."
  - "Kotlin commonMain side (NotificationService expect/actual, GeofenceProvider, ViewModel) left untouched — silent-fail root cause was purely Swift-side missing prerequisites."
metrics:
  duration_minutes: 3
  completed: 2026-05-18T08:31:05Z
  tasks_completed: 2
  tasks_total: 3  # task 3 is checkpoint:human-verify (manual UAT, intentionally not executed)
  files_modified: 1
---

# Quick 260518-egc: iOS Geofence Exit Notification Posten Fix — Summary

JWT-style one-liner: Wire `UNUserNotificationCenter` authorization + foreground delegate in `AppDelegate` so existing Swift `postGeofenceNotification(_:)` calls actually present on iOS (parity with Android).

## What changed

Single Swift file edit. Two iOS-specific prerequisites that were missing — without them, every `UNUserNotificationCenter.current().add(request)` call silently failed.

### iosApp/iosApp/AppDelegate.swift

1. Added `import UserNotifications`.
2. Declared `AppDelegate` conformance to `UNUserNotificationCenterDelegate`.
3. In `application(_:didFinishLaunchingWithOptions:)`, after the existing Koin / GeofenceProvider / LocationPermissionRequester bootstrap (steps 1–5 preserved verbatim), added:
   - `UNUserNotificationCenter.current().delegate = self`
   - `requestAuthorization(options: [.alert, .sound, .badge])` with logging
4. Added `userNotificationCenter(_:willPresent:withCompletionHandler:)` returning `[.banner, .sound, .list]` so foreground Mock-Exit notifications (the Debug-Sheet test path) actually present instead of being silently suppressed by iOS.

The pre-existing Phase-19 logic (UserDefaults rationale-flag reset, Koin init, debug override, force-resolve provider, eager `LocationPermissionRequester.shared`) is preserved in its original order — nothing in the geofence cold-start reconcile path is touched.

## Why these two and not a Kotlin-side fix

The root cause is on the Swift side: the only `requestAuthorization` call in the codebase lives in `shared/.../NotificationService.ios.kt`'s init block, but that `NotificationServiceIos` class is never resolved from Koin by the iOS UI layer. Result: no authorization → `add(request)` rejected → silent drop. The Kotlin side is correct; it just relies on the iOS host having authorization. The host never asked.

`willPresent` is a separate iOS-specific quirk: by default iOS *suppresses* notifications when the app is in the foreground. The user's primary test path (Debug-Sheet Mock-Exit) runs with the app in the foreground, so the delegate's `willPresent` returning `[.banner, .sound, .list]` is the actual test-environment fix.

## Tasks

| #   | Type                    | Name                                                        | Status         | Commit    |
| --- | ----------------------- | ----------------------------------------------------------- | -------------- | --------- |
| 1   | auto                    | AppDelegate as UNUserNotificationCenterDelegate + auth req  | done           | `289f2e8` |
| 2   | auto (read-only verify) | PumpernickelApp.swift sanity — UIApplicationDelegateAdaptor | verified, no-op | —         |
| 3   | checkpoint:human-verify | Manual UAT — notifications arrive                           | deferred (UAT) | —         |

Task 2 confirmed via Read: `@UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate` is present on line 9 of `iosApp/iosApp/PumpernickelApp.swift`; no edit required. Task 3 is intentionally left for the user to validate against a device/simulator per the executor constraints.

## Verification

- Grep verification (Task 1 automated checks): all 4 patterns present
  - `UNUserNotificationCenterDelegate`: 2 occurrences
  - `requestAuthorization`: 1 occurrence
  - `willPresent`: 1 occurrence
  - `.delegate = self`: 1 occurrence
- iOS Xcode build (`xcodebuild ... -destination 'generic/platform=iOS Simulator' build`): **BUILD SUCCEEDED** (71.9s, no new warnings related to the change).

## Deviations from Plan

None — plan executed exactly as written. No auto-fixes, no Rule 4 escalations, no auth gates.

## Known Stubs

None.

## Manual UAT (deferred to user)

The user must perform the manual test described in Task 3 of the plan:

1. Delete app from Simulator/device (so iOS shows the one-shot authorization prompt) — or manually toggle Settings → PumpernickelApp → Notifications → Allow.
2. Launch app → tap "Allow" on the "PumpernickelApp möchte dir Mitteilungen senden" dialog.
3. Start a workout, open Debug-Sheet (red ladybug pill), set a short grace, trigger Mock-Exit.
4. Expected: banner appears in foreground for `.exitDetected`; second banner on grace-expiry; re-enter posts `.reEntered`; Early-Exit-Dialog posts `.earlyExitWithBudget` or `.earlyExitWithPenalty`.

Failure-mode triage hints are documented in the plan's `<how-to-verify>` block.

## Self-Check: PASSED

- FOUND: iosApp/iosApp/AppDelegate.swift (modified, 30 insertions / 1 deletion)
- FOUND: commit 289f2e8 in `git log --oneline -3`
- FOUND: BUILD SUCCEEDED in xcodebuild output
- FOUND: All 4 grep verification patterns matched ≥ 1 occurrence
