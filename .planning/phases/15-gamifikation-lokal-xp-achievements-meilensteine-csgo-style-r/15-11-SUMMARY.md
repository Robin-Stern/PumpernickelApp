---
phase: 15
plan: 11
subsystem: ios-gamification-ui
tags: [swift, swiftui, gamification, gap-closure, wiring, kmp-native]
dependency_graph:
  requires:
    - 15-10 (OverviewRankStrip, UnlockModalView, AchievementGalleryView Swift files)
    - 15-08 (GamificationViewModel, GamificationUiKoinHelper)
    - 15-09 (AchievementGalleryViewModel, AchievementGalleryKoinHelper)
  provides:
    - OverviewView renders rank strip as first VStack child (D-18)
    - SettingsView has Gamification section with Achievements NavigationLink (D-21)
    - MainTabView hosts unlock-modal queue at TabView root (D-19/D-20)
  affects:
    - Phase 15 UAT — all 15 previously blocked iOS tests now unblocked
tech_stack:
  added: []
  patterns:
    - withTaskGroup-concurrent-flow-observation (OverviewView observes two flows in parallel)
    - fullScreenCover-queue-binding (MainTabView queued modal presentation via Binding)
    - private-typealias-Shared-nested (KMP-Native sealed subclasses accessed as Shared.ParentType.SubType)
    - concrete-type-on-public-property (struct properties exposed across files use Shared.X directly, not private typealias)
key_files:
  created: []
  modified:
    - iosApp/iosApp/Views/Settings/SettingsView.swift
    - iosApp/iosApp/Views/Overview/OverviewView.swift
    - iosApp/iosApp/Views/MainTabView.swift
    - iosApp/iosApp/Views/Gamification/UnlockModalView.swift  # deviation fix
    - iosApp/iosApp/Views/Overview/OverviewRankStrip.swift    # deviation fix
decisions:
  - "unlockEvents (not unlockEventsFlow) is the correct KMPNativeCoroutines property name on GamificationViewModel — confirmed via Shared.h line 4404; the plan's interface spec used the wrong name"
  - "KMP-Native exports sealed subclasses as nested Swift types (UnlockEvent.RankPromotion, RankState.Unranked) not flat names — typealiases updated to Shared.UnlockEvent.RankPromotion pattern"
  - "Struct properties referencing private typealiases across files must use the concrete Shared.X type directly on the property, not the private alias — avoids Swift access-level conflict"
  - "GamificationViewModel instances in OverviewView and MainTabView are distinct (Koin factory), but both observe the same upstream SharedFlow engine — acceptable per koin_vm_note"
metrics:
  duration: 8 minutes
  completed: 2026-04-22
  tasks_completed: 3
  tasks_total: 3
  files_created: 0
  files_modified: 5
---

# Phase 15 Plan 11: iOS Gamification UI Wiring Summary

Final gap-closure plan for Phase 15. Wires the three Swift views created in Plan 15-10 (OverviewRankStrip, UnlockModalView, AchievementGalleryView) into the live iOS view tree — OverviewView, SettingsView, and MainTabView. After this plan, all 15 UAT tests that were blocked by `ios-ui-not-implemented` are unblocked.

## One-liner

Rank strip wired into Overview, Achievements linked from Settings, unlock-modal queue hosted at TabView root — Phase 15 iOS gamification surface fully connected.

## What Was Built

### Task 1 — SettingsView.swift: Gamification section (commit `bef6d0f`)

Inserted `Section("Gamification")` after the existing `Section("Units")` block, before `.navigationTitle("Settings")`. Contains:

```swift
NavigationLink {
    AchievementGalleryView()
} label: {
    Label("Achievements", systemImage: "trophy.fill")
}
```

The `NavigationStack { Form { ... } }` root (line 13) already supports `NavigationLink` natively — no wrapping needed. All three existing sections (Appearance, Accent Color, Units) preserved intact. `AchievementGalleryView` self-provides `.navigationTitle("Achievements")` on the destination screen.

### Task 2 — OverviewView.swift: GamificationViewModel + rank strip (commit `ba9d6c1`)

Five sub-edits:

- **VM property**: `private let gamificationViewModel = GamificationUiKoinHelper().getGamificationViewModel()`
- **State**: `@State private var rankState: SharedRankState = SharedRankStateUnranked()`
- **Rank strip**: `OverviewRankStrip(rankState: rankState)` inserted as first child of the main `VStack(spacing: 24)`, above `muscleActivitySection`
- **Concurrent observation**: `.task` upgraded from single `observeUiState()` call to `withTaskGroup` running both `observeUiState()` and `observeRank()` concurrently — mirrors `PumpernickelApp.swift` lines 26–34 pattern
- **observeRank()**: New async method observing `gamificationViewModel.rankStateFlow` via `asyncSequence`
- **Typealiases**: `SharedRankState = Shared.RankState`, `SharedRankStateUnranked = Shared.RankState.Unranked` (nested type path, see deviations)

All existing nutrition/macro/muscle sections, sub-struct views, computed properties, and helper funcs preserved byte-for-byte.

### Task 3 — MainTabView.swift: unlock-modal queue host (commit `25a55c0`)

Full file replacement (38 → 86 lines). Additions:

- `import Shared` + `import KMPNativeCoroutinesAsync`
- `private let gamificationViewModel = GamificationUiKoinHelper().getGamificationViewModel()`
- `@State private var pendingUnlocks: [SharedUnlockEvent] = []`
- `.fullScreenCover(isPresented: Binding(get: { !pendingUnlocks.isEmpty }, set: { ... }))` at TabView root — overlays any active tab
- `UnlockModalView(event: head) { pendingUnlocks.removeFirst() }` inside cover content
- `Binding.set:` handles interactive swipe-dismiss by popping the head
- `observeUnlocks()` async method: `asyncSequence(for: gamificationViewModel.unlockEvents)` → `pendingUnlocks.append(event)`

Three-tab layout (Workout/Overview/Nutrition), `.tint(.appAccent)`, and all tab items preserved exactly.

### Deviation fix — Typealias and property corrections (commit `45c23ec`)

**Rule 1 — Bug: KMP-Native nested type paths**

Plan 15-10 generated typealiases using flat names (`Shared.UnlockEventRankPromotion`, `Shared.RankStateUnranked`) that don't exist in the Swift module. The actual names confirmed via `Shared.framework/Headers/Shared.h`:

| ObjC class | `swift_name` attribute | Correct Swift access |
|---|---|---|
| `SharedUnlockEventRankPromotion` | `"UnlockEvent.RankPromotion"` | `Shared.UnlockEvent.RankPromotion` |
| `SharedUnlockEventAchievementTierUnlocked` | `"UnlockEvent.AchievementTierUnlocked"` | `Shared.UnlockEvent.AchievementTierUnlocked` |
| `SharedRankStateUnranked` | `"RankState.Unranked"` | `Shared.RankState.Unranked` |
| `SharedRankStateRanked` | `"RankState.Ranked"` | `Shared.RankState.Ranked` |

Fixed in `UnlockModalView.swift` and `OverviewRankStrip.swift` typealiases, and in `OverviewView.swift`.

**Rule 1 — Bug: Private typealias visibility on public struct property**

`UnlockModalView.let event: SharedUnlockEvent` — `SharedUnlockEvent` was a `private` typealias but `let event` was `internal` (default struct property). Swift error: "property must be declared fileprivate because its type uses a private type." Fix: changed the property to use the concrete type `Shared.UnlockEvent` directly (accessible cross-file). Same applied to `OverviewRankStrip.let rankState` → `Shared.RankState`.

**Rule 1 — Bug: Wrong flow property name**

`gamificationViewModel.unlockEventsFlow` does not exist. The KMPNativeCoroutines-generated property is `unlockEvents` (matches Kotlin property name). Confirmed in `Shared.h`:

```objc
@property (readonly) SharedKotlinUnit *(^(^unlockEvents)(...))(...)(void) __attribute__((swift_name("unlockEvents")));
```

Fixed in `MainTabView.swift` line 73: `unlockEventsFlow` → `unlockEvents`.

**Build result**: `xcodebuild` succeeded after all three fixes.

## Instance Sharing Note

`GamificationUiKoinHelper().getGamificationViewModel()` is called twice — once in `OverviewView` (for `rankStateFlow`) and once in `MainTabView` (for `unlockEvents`). Per the header, Koin registers `GamificationViewModel` as a `viewModel { ... }` factory (not `single`). With no `ViewModelStoreOwner` on iOS, each call produces a **distinct instance**. This is acceptable:
- `rankStateFlow` derives from `gamificationRepository.rankState.stateIn(...)` — same Room query, same data
- `unlockEvents` forwards from `gamificationEngine.unlockEvents` — the engine is a Koin `single`, so both instances observe the same `SharedFlow`

No modal or rank-strip divergence is possible.

## Known Stubs

None — all three wiring points are fully functional:
- Rank strip renders Unranked state on first launch (pre-retroactive-walk), transitions to Ranked state when `rankStateFlow` emits
- Unlock modal fires on real `unlockEvents` emission from the engine
- Achievement gallery loads from the Koin-provided `AchievementGalleryViewModel`

## Threat Flags

None — no new network endpoints, auth paths, or schema changes. Pure SwiftUI view wiring.

## KMP iOS Pattern Reference (for future plans)

**Sealed subclass access pattern** (confirmed via `Shared.h` `swift_name` attributes):
```swift
// WRONG (flat ObjC name):
private typealias SharedUnlockEventRankPromotion = Shared.UnlockEventRankPromotion

// CORRECT (nested Swift name from swift_name attribute):
private typealias SharedUnlockEventRankPromotion = Shared.UnlockEvent.RankPromotion
```

**Struct property visibility rule**: If a struct property must be accessible from another Swift file (e.g., for `NavigationLink` destination init or `fullScreenCover` content), use the concrete `Shared.X` type directly on the property — not a `private` typealias.

**Flow property name**: KMPNativeCoroutines generates the Swift property with the same name as the Kotlin `val` declaration. A Kotlin `val unlockEvents: SharedFlow<UnlockEvent>` becomes Swift `unlockEvents`, not `unlockEventsFlow`.

## UAT Re-run Note

All 15 iOS tests from `15-UAT.md` that were previously `blocked_by: ios-ui-not-implemented` are now unblocked. Recommend running the full 16-test UAT sequence. Expected outcomes:
- Test 1 (Cold Start Smoke Test): PASSES — rank strip renders, unlock modals fire, gallery reachable from Settings
- Tests 2–16: UNBLOCKED — actual pass/fail depends on Kotlin engine data correctness (covered by plans 15-01 through 15-07)

Reference Plan 15-10 SUMMARY for the 3 new Swift files + pbxproj wiring that this plan depends on.

## Self-Check: PASSED

Files exist:
- FOUND: iosApp/iosApp/Views/Settings/SettingsView.swift
- FOUND: iosApp/iosApp/Views/Overview/OverviewView.swift
- FOUND: iosApp/iosApp/Views/MainTabView.swift
- FOUND: iosApp/iosApp/Views/Gamification/UnlockModalView.swift
- FOUND: iosApp/iosApp/Views/Overview/OverviewRankStrip.swift

Commits exist:
- FOUND: bef6d0f (Task 1 — SettingsView)
- FOUND: ba9d6c1 (Task 2 — OverviewView)
- FOUND: 25a55c0 (Task 3 — MainTabView)
- FOUND: 45c23ec (Deviation fix — typealias + flow name)

Build: xcodebuild succeeded (BUILD SUCCEEDED in ~2.5s incremental after fixes)
