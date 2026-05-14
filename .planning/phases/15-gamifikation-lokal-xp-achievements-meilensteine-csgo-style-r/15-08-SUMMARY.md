---
phase: 15
plan: 08
subsystem: presentation/gamification + android/ui
tags: [gamification, viewmodel, stateflow, sharedflow, android, overview, unlock-modal, koin, ios-contract]
dependency_graph:
  requires: [15-02 (RankState, UnlockEvent, Rank), 15-03 (GamificationRepository.rankState Flow), 15-04 (GamificationEngine.unlockEvents SharedFlow)]
  provides: [GamificationViewModel (shared), GamificationUiModule Koin binding, GamificationUiKoinHelper iOS factory, OverviewRankStrip Android composable, UnlockModalHost Android composable]
  affects: [plan 09 (AchievementGalleryViewModel uses same module pattern), iOS user who hand-writes OverviewRankStrip.swift + UnlockModalView.swift per ios_integration_contract]
tech_stack:
  added: []
  patterns: [GamificationViewModel wraps repo + engine flows, @NativeCoroutinesState StateFlow + @NativeCoroutines SharedFlow, mutableStateListOf queue for D-20 one-at-a-time modal, LaunchedEffect(event) for per-unlock haptic, outer Box wrapping Scaffold for root-level modal overlay]
key_files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/gamification/GamificationViewModel.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/di/GamificationUiKoinHelper.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/OverviewRankStrip.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/UnlockModal.kt
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/di/GamificationUiModule.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/OverviewScreen.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt
decisions:
  - "GamificationViewModel ctor has exactly 2 get()s — GamificationRepository (plan 03) + GamificationEngine (plan 04); Koin cross-module get() resolution is automatic"
  - "OverviewRankStrip uses RankState.Ranked.currentRankThreshold + nextRankThreshold (actual plan-02 field names), not thresholdForCurrent/thresholdForNext listed in the plan action block"
  - "GamificationUiKoinHelper is a class (not object) matching plan spec; KoinHelper.kt is an object — both approaches work, class gives Swift GamificationUiKoinHelper() constructor"
  - "MainScreen outer Box wraps Scaffold exactly; UnlockModalHost() is a composition sibling to Scaffold so AlertDialog uses its own window layer independent of tab hierarchy"
metrics:
  duration_seconds: 900
  completed_date: "2026-04-22"
  tasks_completed: 3
  files_created: 4
  files_modified: 3
---

# Phase 15 Plan 08: GamificationViewModel + Android Overview Strip + Unlock Modal Summary

**One-liner:** Shared GamificationViewModel (@NativeCoroutinesState rankState + @NativeCoroutines unlockEvents), Android OverviewRankStrip (D-18 compact strip with D-11 unranked literal), and Android UnlockModalHost (D-19 + D-20 AlertDialog queue with 50ms haptic) wired into OverviewScreen and MainScreen respectively.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | GamificationViewModel + Koin wiring + iOS factory | b218edb | GamificationViewModel.kt, GamificationUiModule.kt, GamificationUiKoinHelper.kt |
| 2 | OverviewRankStrip composable + OverviewScreen insertion | 50d03e7 | OverviewRankStrip.kt, OverviewScreen.kt |
| 3 | UnlockModalHost + MainScreen root-level wiring | ca1844e | UnlockModal.kt, MainScreen.kt |

## GamificationViewModel Contract

```kotlin
class GamificationViewModel(
    private val gamificationRepository: GamificationRepository,  // get() #1
    private val gamificationEngine: GamificationEngine           // get() #2
) : ViewModel() {
    @NativeCoroutinesState
    val rankState: StateFlow<RankState>   // iOS: asyncSequence(for: viewModel.rankStateFlow)

    @NativeCoroutines
    val unlockEvents: SharedFlow<UnlockEvent>  // iOS: asyncSequence(for: viewModel.unlockEventsFlow)
}
```

Koin binding: `viewModel { GamificationViewModel(get(), get()) }` — 2 `get()` calls.

## OverviewRankStrip Insertion Point

`OverviewRankStrip(rankState = rankState)` inserted at the first content position inside the scrollable Column in `OverviewScreen.kt`, **between** `Spacer(height = 8.dp)` and `MuscleActivityCard(uiState)`. This satisfies D-18 (strip at top of Overview tab, above muscle-activity card).

The strip renders two states:
- `RankState.Unranked` → lock icon + `"Unranked — complete a workout to unlock Silver"` (D-11 verbatim literal)
- `RankState.Ranked` → MilitaryTech icon tinted by rank tier + `displayName` + `totalXp / nextRankThreshold XP` + `LinearProgressIndicator`

Progress calculation uses `RankState.Ranked.currentRankThreshold` and `nextRankThreshold` (the actual plan-02 field names, not the `thresholdForCurrent/thresholdForNext` aliases listed in the plan action block).

## MainScreen Structural Change

Wrapped the existing `Scaffold { ... }` in an outer `Box(modifier = Modifier.fillMaxSize())`. Added `UnlockModalHost()` as a sibling to `Scaffold` inside that outer Box:

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        bottomBar = { NavigationBar { ... } }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            // Workout tab NavHost ... (UNCHANGED)
            // Overview tab Box ... (UNCHANGED)
            // Nutrition tab NavHost ... (UNCHANGED)
        }
    }

    // D-19 + D-20: root-level unlock modal host
    UnlockModalHost()
}
```

`UnlockModalHost` pulls `GamificationViewModel` via `koinViewModel()` with no parameters. Since both `OverviewScreen` and `UnlockModalHost` request `GamificationViewModel` from the same Koin `viewModel { }` scope within the same Activity's `ViewModelStore`, they share the same instance — ensuring `rankState` updates and `unlockEvents` emissions are synchronized.

## UnlockModalHost Queue Behavior (D-20)

- `mutableStateListOf<UnlockEvent>()` buffers incoming events from the SharedFlow
- Head of queue is displayed in an `AlertDialog`
- `LaunchedEffect(event)` fires a 50ms VibrationEffect haptic on each new event appearing (keyed on event identity — exactly one haptic per unlock)
- `pending.removeAt(0)` on dismiss pops head and shows next queued event
- Multiple unlocks from one `saveReviewedWorkout` → sequential modals, never stacked

## iOS Integration Contract (User-Implemented)

This plan ships only the shared VM + KoinHelper factory for iOS. The user hand-writes:

1. `iosApp/iosApp/Views/Overview/OverviewRankStrip.swift` — observes `GamificationUiKoinHelper().getGamificationViewModel().rankStateFlow` via `asyncSequence(for:)`, renders Unranked / Ranked states with `ProgressView`, inserts as first VStack child in `OverviewView.swift` above `muscleActivitySection`.

2. `iosApp/iosApp/Views/Gamification/UnlockModalView.swift` — presented via `.fullScreenCover(isPresented:)` at `MainTabView` level; observes `unlockEventsFlow` via `asyncSequence(for:)` into `@State pendingUnlocks: [UnlockEvent]`; fires `UINotificationFeedbackGenerator().notificationOccurred(.success)` on `.onAppear`.

iOS callers use `GamificationUiKoinHelper().getGamificationViewModel()` (class, not singleton object — gives a natural Swift constructor).

## Build Verification

- `./gradlew :shared:compileTestKotlinIosSimulatorArm64` — BUILD SUCCESSFUL
- `./gradlew :androidApp:compileDebugKotlin` — BUILD SUCCESSFUL
- All warnings are pre-existing (ObjC suspend exposure in nutrition use cases, deprecated kotlinx.datetime typealias, redundant `else` in WorkoutSessionViewModel, annotation target in MainScreen.kt TopLevelTab enum). Zero new warnings introduced by plan 08 changes.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] OverviewRankStrip used wrong RankState.Ranked field names**
- **Found during:** Task 2 — plan action block listed `state.thresholdForCurrent` and `state.thresholdForNext`, but the actual `RankState.Ranked` sealed class from plan 02 has `currentRankThreshold` and `nextRankThreshold`
- **Fix:** Used the correct plan-02 field names (`currentRankThreshold`, `nextRankThreshold`) throughout the strip's `RankedContent` composable
- **Files modified:** `OverviewRankStrip.kt`
- **Commit:** 50d03e7

## Known Stubs

None. `OverviewRankStrip` renders live data from `GamificationViewModel.rankState` (backed by `GamificationRepository.rankState` Flow). `UnlockModalHost` observes live `unlockEvents` SharedFlow. No hardcoded placeholder values flow to UI rendering.

## Threat Flags

None. All new files are Android/iOS UI and commonMain ViewModel. No new network endpoints, auth paths, file access patterns, or schema changes introduced in plan 08.

## Self-Check: PASSED
