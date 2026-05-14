---
phase: 15
plan: 10
subsystem: ios-gamification-ui
tags: [swift, swiftui, gamification, xcode, pbxproj, gap-closure]
dependency_graph:
  requires:
    - 15-08 (GamificationUiKoinHelper + GamificationViewModel)
    - 15-09 (AchievementGalleryKoinHelper + AchievementGalleryViewModel)
  provides:
    - OverviewRankStrip SwiftUI view (D-11/D-18)
    - UnlockModalView SwiftUI view (D-19)
    - AchievementGalleryView SwiftUI view (D-21)
    - Xcode target registration for all three files
  affects:
    - 15-11 (wires the three new views into OverviewView, SettingsView, MainTabView)
tech_stack:
  added: []
  patterns:
    - passive-view-pattern (OverviewRankStrip takes RankState as let, no VM ownership)
    - asyncSequence-flow-observation (AchievementGalleryView observes uiStateFlow)
    - UINotificationFeedbackGenerator-haptic (UnlockModalView .onAppear)
    - private-typealias-Shared (all three files use Shared.* type aliases)
key_files:
  created:
    - iosApp/iosApp/Views/Gamification/UnlockModalView.swift
    - iosApp/iosApp/Views/Gamification/AchievementGalleryView.swift
    - iosApp/iosApp/Views/Overview/OverviewRankStrip.swift
  modified:
    - iosApp/iosApp.xcodeproj/project.pbxproj
decisions:
  - "OverviewRankStrip is passive (let rankState) — OverviewView remains single subscription owner, consistent with existing OverviewView pattern for other Kotlin flows"
  - "UnlockModalView typealias uses flat Shared.UnlockEventRankPromotion (not nested Shared.UnlockEvent.RankPromotion) — matches KMP-NativeCoroutines 1.0.2 flat export convention"
  - "AchievementGalleryView owns its own VM via AchievementGalleryKoinHelper() — separate from GamificationUiKoinHelper used by rank strip, two distinct Koin bindings"
  - "Gamification PBXGroup (E10018) appended as last child of E10003 Views per pbxproj_insertion_contract spec"
metrics:
  duration: 2 minutes
  completed: 2026-04-22
  tasks_completed: 3
  tasks_total: 3
  files_created: 3
  files_modified: 1
---

# Phase 15 Plan 10: iOS Gamification UI Surface Summary

GAP CLOSURE plan that creates the three Swift files declared missing by 15-UAT.md. Kotlin side (ViewModels, Koin factories, engine pipeline) was complete from plans 08+09; this plan makes that domain layer observable on iOS.

## One-liner

Three SwiftUI gamification views (rank strip, unlock modal, achievement gallery) registered in Xcode, closing the iOS UI gap that blocked all 15 UAT tests.

## What Was Built

### Task 1 — UnlockModalView.swift + AchievementGalleryView.swift (commit `ada40e1`)

**`iosApp/iosApp/Views/Gamification/UnlockModalView.swift`**
- D-19 full-screen celebratory modal
- Accepts `SharedUnlockEvent` + `onDismiss` closure
- Branches on `SharedUnlockEventRankPromotion` and `SharedUnlockEventAchievementTierUnlocked`
- Fires `UINotificationFeedbackGenerator().notificationOccurred(.success)` in `.onAppear`
- Tier-banded rank tints + tier tints for visual flair
- Passive — host (MainTabView, wired in plan 15-11) owns queue state

**`iosApp/iosApp/Views/Gamification/AchievementGalleryView.swift`**
- D-21 2-column LazyVGrid achievement gallery
- Observes `AchievementGalleryKoinHelper().getAchievementGalleryViewModel().uiStateFlow` via `asyncSequence`
- Fixed category order: `.volume, .consistency, .prHunter, .variety`
- Category headers span full grid width via `.gridCellColumns(2)`
- Locked tiles: 0.45 opacity, lock icon, "currentProgress / threshold" footer
- Unlocked tiles: full opacity, trophy icon, tier-coloured border, "Unlocked YYYY-MM-DD" footer
- Kotlin Map key extraction matches OverviewView.swift lines 49–55 idiom

### Task 2 — OverviewRankStrip.swift (commit `a97d554`)

**`iosApp/iosApp/Views/Overview/OverviewRankStrip.swift`**
- D-11/D-18 compact rank strip
- Passive view — `let rankState: SharedRankState` (no VM, no @State)
- Unranked branch: lock icon + D-11 literal verbatim: `"Unranked — complete a workout to unlock Silver"`
- Ranked branch: rosette icon + rank displayName + XP label (`totalXp / nextRankThreshold XP`) + ProgressView
- Handles `KotlinLong?` for `nextRankThreshold` via `.int64Value`
- Tier-banded tints match UnlockModalView for visual consistency

### Task 3 — project.pbxproj registration (commit `180817b`)

6 additive edits to `iosApp/iosApp.xcodeproj/project.pbxproj`:

| Edit | Section | Change |
|------|---------|--------|
| 1 | PBXBuildFile | Added A10120, A10121, A10122 entries after A10110 |
| 2 | PBXFileReference | Added B10120, B10121, B10122 entries after B10110 |
| 3 | PBXGroup E10017 Overview | Added B10120 OverviewRankStrip.swift as second child |
| 4 | PBXGroup E10018 Gamification (new) | Created with B10121 + B10122 as children; inserted after E10017 block |
| 5 | PBXGroup E10003 Views | Appended E10018 Gamification as last child |
| 6 | PBXSourcesBuildPhase F10002 | Appended A10120, A10121, A10122 after A10110 |

`plutil -lint` result: `iosApp/iosApp.xcodeproj/project.pbxproj: OK`

## Must-Have Verification

- [x] `OverviewRankStrip.swift` defines `struct OverviewRankStrip: View` with `let rankState: SharedRankState`
- [x] D-11 literal present verbatim: `"Unranked — complete a workout to unlock Silver"`
- [x] `UnlockModalView.swift` defines `struct UnlockModalView: View` handling both UnlockEvent subclasses
- [x] D-19 haptic `UINotificationFeedbackGenerator().notificationOccurred(.success)` in `.onAppear`
- [x] `AchievementGalleryView.swift` defines `struct AchievementGalleryView: View` with LazyVGrid
- [x] Category order `[.volume, .consistency, .prHunter, .variety]` present
- [x] All three files registered in pbxproj (4 sections each — 4 occurrences per filename confirmed)
- [x] New `E10018 /* Gamification */` PBXGroup exists and is child of E10003 Views
- [x] `B10120 OverviewRankStrip.swift` in E10017 Overview children list
- [x] `plutil -lint` reports OK

## Deviations from Plan

None — plan executed exactly as written.

All 6 pbxproj UUID assignments match the reserved values from `<pbxproj_insertion_contract>`:
- `B10120/A10120` → OverviewRankStrip.swift
- `B10121/A10121` → UnlockModalView.swift
- `B10122/A10122` → AchievementGalleryView.swift
- `E10018` → Gamification PBXGroup

## Typealias Names (for Plan 15-11 executor)

The flat Kotlin-Native export naming was used throughout:
- `Shared.UnlockEventRankPromotion` (not `Shared.UnlockEvent.RankPromotion`)
- `Shared.UnlockEventAchievementTierUnlocked`
- `Shared.RankStateUnranked` (not `Shared.RankState.Unranked`)
- `Shared.RankStateRanked`
- `Shared.Category` with cases `.volume`, `.consistency`, `.prHunter`, `.variety`
- `Shared.Tier` with cases `.bronze`, `.silver`, `.gold`

If a Xcode build fails on these names, check `shared/build/.../Shared.framework/Headers/Shared.h` for the actual generated Swift names and update typealiases accordingly.

## Known Stubs

None — the three new views are fully implemented. They are not yet reachable from existing code (Plan 15-11 wires them into OverviewView, SettingsView, and MainTabView).

## Threat Flags

None — no new network endpoints, auth paths, or schema changes introduced. All three files are pure SwiftUI read-only views.

## Next Step

Plan 15-11 wires the three new views into the existing host views:
- `OverviewView.swift`: insert `OverviewRankStrip(rankState:)` as first VStack child + add GamificationViewModel subscription
- `SettingsView.swift`: add Gamification section with Achievements NavigationLink to AchievementGalleryView
- `MainTabView.swift`: attach `.fullScreenCover` for UnlockModalView with unlock queue management

## Self-Check: PASSED

Files exist:
- FOUND: iosApp/iosApp/Views/Gamification/UnlockModalView.swift
- FOUND: iosApp/iosApp/Views/Gamification/AchievementGalleryView.swift
- FOUND: iosApp/iosApp/Views/Overview/OverviewRankStrip.swift

Commits exist:
- FOUND: ada40e1 (Task 1)
- FOUND: a97d554 (Task 2)
- FOUND: 180817b (Task 3)

plutil -lint: OK
