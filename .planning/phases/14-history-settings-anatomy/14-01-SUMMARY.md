---
phase: 14-history-settings-anatomy
plan: 01
subsystem: android-history-settings
tags: [android, compose, history, settings, navigation, material3]
dependency_graph:
  requires:
    - WorkoutHistoryViewModel (shared)
    - SettingsViewModel (shared)
    - WorkoutHistoryDetailRoute (already existed in Routes.kt)
  provides:
    - WorkoutHistoryListScreen
    - WorkoutHistoryDetailScreen
    - SettingsSheet
    - WorkoutHistoryListRoute
  affects:
    - MainScreen.kt (NavHost entries added)
    - TemplateListScreen.kt (toolbar icons wired)
tech_stack:
  added: []
  patterns:
    - koinViewModel() + collectAsState() for ViewModel observation
    - ModalBottomSheet for settings overlay
    - SingleChoiceSegmentedButtonRow for weight unit toggle
    - LazyColumn with ListItem for history list
    - DisposableEffect for cleanup on detail screen exit
key_files:
  created:
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutHistoryListScreen.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutHistoryDetailScreen.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/SettingsSheet.kt
  modified:
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/Routes.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/TemplateListScreen.kt
decisions:
  - "WorkoutHistoryDetailScreen uses DisposableEffect onDispose to call clearDetail() — ensures stale workout detail is not shown when navigating to a new workout"
  - "Volume calculation in detail screen derived client-side from sets (weight * reps sum) — matches the same totalVolumeKgX10 semantics used in WorkoutSummary"
metrics:
  duration: ~5min
  completed_date: "2026-03-31"
  tasks_completed: 2
  files_created: 3
  files_modified: 3
---

# Phase 14 Plan 01: History, Settings & Navigation Summary

**One-liner:** Jetpack Compose history list/detail + SettingsSheet with SegmentedButton kg/lbs toggle, wired into NavHost and TemplateListScreen toolbar.

## What Was Built

Three new Compose screens and full navigation wiring to complete ANDROID-08:

1. **WorkoutHistoryListScreen** — Scaffold with TopAppBar, LazyColumn of ListItems showing workout name, exercise count, volume (unit-aware), date (Today/Yesterday/MMM d), and duration. Empty state with History icon and "No Workouts Yet" copy. Tapping a row navigates to WorkoutHistoryDetailRoute.

2. **WorkoutHistoryDetailScreen** — Scaffold with header (name, date+time, total volume), scrollable exercise sections with Surface-backed set rows showing set index, reps, and weight. Uses LaunchedEffect to load detail and DisposableEffect to clear on exit.

3. **SettingsSheet** — ModalBottomSheet with SingleChoiceSegmentedButtonRow toggling between kg and lbs, backed by SettingsViewModel. Unit change persists globally via DataStore.

**Navigation wiring:**
- `WorkoutHistoryListRoute` added to Routes.kt
- NavHost in MainScreen.kt now has composable entries for both history routes
- TemplateListScreen History icon navigates to `WorkoutHistoryListRoute`
- TemplateListScreen Settings icon shows `SettingsSheet`
- All Phase 14 TODO comments removed

## Tasks Completed

| Task | Description | Commit |
|------|-------------|--------|
| 1 | Create WorkoutHistoryListScreen, WorkoutHistoryDetailScreen, SettingsSheet | 98b1e0a |
| 2 | Wire navigation — Routes.kt, MainScreen.kt NavHost, TemplateListScreen toolbar | 2c0ed54 |

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — all screens are wired to live ViewModels with real data from WorkoutRepository and SettingsRepository.

## Self-Check: PASSED
