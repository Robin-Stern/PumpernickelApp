---
phase: 16-set-nutrition-goals-kcal-protein-carbs-fat-per-day-surface-p
plan: "05"
subsystem: androidApp/ui
tags: [android, compose, nutrition, navigation, drum-picker, overview-screen]
dependency_graph:
  requires: ["16-02", "16-04"]
  provides:
    - "NutritionGoalsEditorRoute in Routes.kt"
    - "composable<NutritionGoalsEditorRoute> in Overview NavHost"
    - "NutritionGoalsEditorScreen (3-section editor: stats/suggestions/pickers)"
    - "NutritionGoalsBanner above rings card in OverviewScreen"
    - "Edit pencil on NutritionRingsCard header"
  affects: []
tech_stack:
  added: []
  patterns:
    - "AnimatedVisibility(exit = slideOutVertically + fadeOut) for banner dismiss"
    - "derivedStateOf for live TdeeCalculator.suggestions from stats inputs"
    - "SingleChoiceSegmentedButtonRow for sex selector (exactly one selected)"
    - "ExposedDropdownMenuBox for activity level dropdown"
    - "DrumPicker reused from components package (5 calls)"
    - "remember(storedStats/storedGoals) to re-init state on first DB load"
key_files:
  created:
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/NutritionGoalsEditorScreen.kt
  modified:
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/Routes.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/OverviewScreen.kt
decisions:
  - "D-16-01: Editor opens via Overview-tab NavHost, not a global modal"
  - "D-16-02: Single screen with 3 collapsible sections"
  - "D-16-03: DrumPicker.kt reused, not duplicated"
  - "D-16-08: Live TdeeCalculator.suggestions update via derivedStateOf"
  - "D-16-09: Stats section starts expanded when userPhysicalStats == null"
  - "D-16-13: NutritionGoalsBanner with AnimatedVisibility exit animation"
  - "D-16-14: dismissBanner() on X tap; updateNutritionGoals chains dismiss on Save"
metrics:
  duration: "~20 minutes"
  completed: "2026-04-28"
  tasks_completed: 4
  files_changed: 4
---

# Phase 16 Plan 05: Android UI — NutritionGoalsEditorScreen + Overview Banner Summary

Android-side nutrition goals UI: new `NutritionGoalsEditorScreen` (3-section editor with collapsible stats, live suggestion cards via `TdeeCalculator`, five DrumPickers), route registration in the Overview NavHost, and `NutritionGoalsBanner` with animated dismiss above the rings card.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add NutritionGoalsEditorRoute + composable in Overview NavHost | 584137c | Routes.kt, MainScreen.kt |
| 2 | Create NutritionGoalsEditorScreen.kt with stats/suggestions/pickers/save | 4650fc8 | NutritionGoalsEditorScreen.kt (new, 589 lines) |
| 3 | Add NutritionGoalsBanner + edit pencil to OverviewScreen | 5962efd | OverviewScreen.kt |
| 4 | Human verification checkpoint (auto-approved) | — | — |

## Diff Summary

### Routes.kt (+3 lines)
```kotlin
// Overview tab — Nutrition Goals Editor (Phase 16)
@Serializable data object NutritionGoalsEditorRoute
```

### MainScreen.kt (+7 lines)
- Import: `com.pumpernickel.android.ui.screens.NutritionGoalsEditorScreen`
- Composable block inside Overview NavHost after `AchievementGalleryRoute`:
```kotlin
composable<NutritionGoalsEditorRoute> {
    NutritionGoalsEditorScreen(navController = overviewNavController)
}
```

### NutritionGoalsEditorScreen.kt (new, 589 lines)
- `NutritionGoalsEditorScreen` composable — Scaffold + TopAppBar "Ernährungsziele" + LazyColumn(spacedBy=24dp)
- `StatsSection` — collapsible Card with weight/height/age OutlinedTextFields, sex SingleChoiceSegmentedButtonRow, activity ExposedDropdownMenuBox
- `SuggestionRow` + `SuggestionCard` — three cards "Defizit/Erhalt/Aufbau" with live macro splits from `TdeeCalculator.suggestions(currentStatsForCalc)` via `derivedStateOf`; tapping a card highlights it (2dp accent border + 8% tint) and pushes its MacroSplit into the picker state vars
- `PickerSection` — five DrumPicker calls with exact ranges per UI-SPEC
- Save Button — builds `UserPhysicalStats` + `NutritionGoals` from state, calls `viewModel.updateUserPhysicalStats` + `viewModel.updateNutritionGoals`, then `popBackStack()`

### OverviewScreen.kt (+106 lines, -7 lines)
- Added `val bannerVisible by viewModel.nutritionGoalsBannerVisible.collectAsState()`
- Inserted `AnimatedVisibility(visible = bannerVisible, exit = slideOutVertically() + fadeOut(tween(300)))` block above `NutritionRingsCard`
- `NutritionGoalsBanner` private composable added at file end
- `NutritionRingsCard` signature updated to `(uiState, onEditClick: () -> Unit)`
- Title row updated: `Spacer.weight(1f) + Text("Ernährung · Heute") + Spacer.weight(1f) + IconButton(Edit, "Ziele bearbeiten")`

## DrumPicker Ranges Used

| Macro | Range | Step | Label |
|-------|-------|------|-------|
| Kalorien | 800–6000 | 50 | "Kalorien" |
| Protein | 20–400 | 5 | "Protein" |
| Kohlenhydrate | 20–700 | 5 | "Kohlenhydrate" |
| Fett | 10–250 | 5 | "Fett" |
| Zucker | 0–200 | 5 | "Zucker" |

All match UI-SPEC exactly.

## German Copy Verified

| String | Location |
|--------|----------|
| "Ernährungsziele" | TopAppBar title |
| "Meine Stats" | Stats section header |
| "Gewicht (kg)" | Weight field label |
| "Körpergröße (cm)" | Height field label |
| "Alter" | Age field label |
| "Männlich" / "Weiblich" | Sex segmented buttons |
| "Bürojob / kaum Bewegung" | Activity level 1 |
| "Leicht aktiv (1–3×/Woche)" | Activity level 2 |
| "Mäßig aktiv (3–5×/Woche)" | Activity level 3 |
| "Sehr aktiv (6–7×/Woche)" | Activity level 4 |
| "Extrem aktiv / körperlicher Beruf" | Activity level 5 |
| "Vorschlag berechnen" | Suggestion row title |
| "Defizit" / "Erhalt" / "Aufbau" | Suggestion card titles |
| "−500 kcal" / "TDEE" / "+300 kcal" | Suggestion card subtitles |
| "Zielwerte anpassen" | Pickers section title |
| "Ziele speichern" | Save button |
| "Persönliche Ziele setzen" | Banner headline |
| "Berechne deinen Tagesbedarf und passe deine Makros an." | Banner subtext |
| "Banner ausblenden" | Banner X button a11y |
| "Ziele bearbeiten" | Edit pencil a11y |
| "Zurück" | Back arrow a11y |

## Human Verification Items

Task 4 was a `checkpoint:human-verify` that was auto-approved in auto-mode. The following manual UAT steps need to be exercised on a real Android device/emulator:

1. **First-launch state**: Overview tab shows banner "Persönliche Ziele setzen" above rings card; rings card title shows pencil icon on the right.

2. **Banner tap navigation**: Tapping banner body opens editor with title "Ernährungsziele"; Stats section is EXPANDED (first launch, null stats); placeholder values weight=80, height=180, age=30, sex=Männlich, activity=Mäßig aktiv; three suggestion cards show real kcal numbers.

3. **Live-update suggestion cards**: Changing weight field updates all three card kcal numbers in real time (derivedStateOf).

4. **Card selection pre-fills pickers**: Tapping "Defizit" card highlights it with accent border + tint; five DrumPickers jump to the Cut split values.

5. **Picker tweak deselects card**: Scrolling a DrumPicker away from the card's value deselects the card.

6. **Save round-trip**: "Ziele speichern" pops back to Overview; banner is GONE; rings update to new goals.

7. **Re-open via pencil**: Pencil tap opens editor; Stats section is COLLAPSED (stats saved); pickers show saved values; tapping header expands it with persisted field values.

8. **Banner dismiss-only**: Clear data, see banner, tap X → banner slides up + fades (300ms); banner stays gone on reload.

9. **Persistence across kill**: Force-stop + relaunch → rings reflect saved goals, banner gone, editor shows collapsed Stats with persisted values.

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — all DrumPickers initialize from `viewModel.nutritionGoals.collectAsState()` (real DataStore-backed flow). All suggestion cards derive from `TdeeCalculator.suggestions(currentStatsForCalc)` (pure function, no stubs).

## Threat Flags

None — no new network endpoints, auth paths, or trust-boundary schema changes introduced.

## Self-Check: PASSED

- [x] Routes.kt exists and contains `NutritionGoalsEditorRoute`: FOUND
- [x] MainScreen.kt contains `composable<NutritionGoalsEditorRoute>`: FOUND
- [x] NutritionGoalsEditorScreen.kt exists: FOUND
- [x] OverviewScreen.kt contains `NutritionGoalsBanner`: FOUND
- [x] Commit 584137c exists: FOUND
- [x] Commit 4650fc8 exists: FOUND
- [x] Commit 5962efd exists: FOUND
- [x] `./gradlew :androidApp:assembleDebug` exits 0: PASSED
- [x] Phase 15 engine files untouched (git diff HEAD~3 HEAD shows only 4 Android UI files): PASSED
