---
phase: 16-set-nutrition-goals-kcal-protein-carbs-fat-per-day-surface-p
verified: 2026-04-28T00:00:00Z
status: gaps_found
score: 7/10 must-haves verified
overrides_applied: 0
gaps:
  - truth: "After saving goals in the editor, the Overview tab rings reflect the new goals without requiring a manual refresh."
    status: failed
    reason: "iOS: NutritionGoalsEditorView instantiates its own OverviewViewModel via KoinHelper.shared.getOverviewViewModel(), which with Koin viewModel{} factory scope returns a new instance distinct from OverviewView's VM. OverviewView reads goals from uiState.nutritionGoals, which is only updated via refresh() (.first() one-shot load). The .sheet() presentation has no onDismiss callback. After save, OverviewView's VM uiState.nutritionGoals is stale until the user manually taps the refresh button. This is CR-01 from the code review."
    artifacts:
      - path: "iosApp/iosApp/Views/Overview/NutritionGoalsEditorView.swift"
        issue: "Line 8: creates its own OverviewViewModel instance (KoinHelper.shared.getOverviewViewModel()) rather than receiving the parent's instance"
      - path: "iosApp/iosApp/Views/Overview/OverviewView.swift"
        issue: "Lines 62-65: .sheet(isPresented: $showEditor) has no onDismiss: { viewModel.refresh() } callback; uiState.nutritionGoals (line 75) is only updated when uiStateFlow emits, which only happens on refresh()"
    missing:
      - "Either pass the OverviewView's viewModel instance into NutritionGoalsEditorView as a parameter, OR add onDismiss: { viewModel.refresh() } to the .sheet() modifier on OverviewView"

  - truth: "Picker wheels initialize once from stored goals and do not reset to stored values when the user has started editing."
    status: failed
    reason: "Android: All six stats fields use remember(storedStats) and all five picker values use remember(storedGoals) as keys. When the DataStore combine() emits a new value (which happens on cold start per CR-02), all local state is silently reset to stored values — discarding any in-progress user edits. The same race applies to storedGoals: the Flow can re-emit after the user has adjusted a picker, overwriting their changes. iOS has the same pattern: observeGoals() unconditionally overwrites kcalValue etc. on every emission. This is WR-03 / WR-04 from the code review."
    artifacts:
      - path: "androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/NutritionGoalsEditorScreen.kt"
        issue: "Lines 91-115: remember(storedStats) and remember(storedGoals) keys cause full field reset on every DataStore Flow emission, not just on first load"
      - path: "iosApp/iosApp/Views/Overview/NutritionGoalsEditorView.swift"
        issue: "Lines 243-257 (observeGoals): unconditionally writes kcalValue/proteinValue/carbsValue/fatValue/sugarValue on every emission, including the emission triggered by saveGoals()"
    missing:
      - "Android: use LaunchedEffect with a boolean guard flag instead of remember(storedStats/storedGoals) to initialize state only once"
      - "iOS: guard observeGoals() and observeStats() with a boolean flag so they initialize state on first emission only"

  - truth: "Tapping a suggestion card applies values to the drum pickers visibly and correctly."
    status: failed
    reason: "TdeeCalculator.buildSplit() returns kcal as a raw roundToInt() result (e.g. 2259 for the reference male). The Android DrumPicker range is (800..6000 step 50) — 2259 is not a multiple of 50 and is not in the list. DrumPicker.LaunchedEffect(selectedItem) calls items.indexOf(2259) == -1 and silently skips the scroll. The picker stays visually at the previous position (default 2500) while kcalValue state holds 2259. The same issue applies on iOS: Picker(.wheel) with a selection not matching any item in the stride range behaves undefined. This is WR-05 from the code review."
    artifacts:
      - path: "shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/TdeeCalculator.kt"
        issue: "Line 54: buildSplit() uses kcalDouble.roundToInt() without snapping to picker step (50). The kcal value applied to pickers is not guaranteed to be a multiple of 50."
      - path: "androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/NutritionGoalsEditorScreen.kt"
        issue: "Line 181: kcalValue = split.kcal — no snap to 50-step before applying to DrumPicker whose range is (800..6000 step 50)"
      - path: "iosApp/iosApp/Views/Overview/NutritionGoalsEditorView.swift"
        issue: "Line 197: kcalValue = Int(split.kcal) — same issue; stride(from: 800, through: 6000, by: 50) does not include non-multiples-of-50"
    missing:
      - "Snap kcalValue to nearest 50 before applying suggestion: kcalValue = (split.kcal / 50) * 50 (Android line 181 and iOS applySuggestion)"
      - "Alternatively, snap inside TdeeCalculator.buildSplit() so kcal is always a multiple of the picker step"
human_verification:
  - test: "Open the editor on both Android and iOS, enter stats, and tap each suggestion card (Defizit / Erhalt / Aufbau)."
    expected: "All five drum pickers scroll to the suggested values. The kcal picker must land on a multiple of 50 nearest to the calculated value."
    why_human: "Gap 3 (WR-05) requires visual verification that the picker actually scrolls and shows the correct value."
  - test: "iOS only: save goals in the editor, dismiss the sheet, and observe the nutrition rings on the Overview tab immediately after dismissal (before tapping the refresh button)."
    expected: "The rings update to show the newly saved goal values without requiring a manual refresh."
    why_human: "Gap 1 (CR-01) is the primary correctness gap. Visual confirmation needed to determine if the fix was applied."
  - test: "Open the editor on Android, start typing a new weight value, then wait 3 seconds without saving."
    expected: "The text field retains the user's typed value. It does not revert to the stored value."
    why_human: "Gap 2 (WR-03) is a race condition triggered by the DataStore combine() emitting. Timing-dependent; requires manual observation."
  - test: "iOS: tap the dismiss 'x' button on the Nutrition Goals banner on the Overview tab. Fully quit and reopen the app."
    expected: "The banner does not reappear after relaunch. No banner flash visible on subsequent tab appearances."
    why_human: "WR-06 (banner flashes briefly on every tab appearance because bannerVisible initializes to true). Needs visual check."
  - test: "Both platforms: complete the full happy path — enter stats, tap Maintain card, Save — and verify the rings update to the suggested values."
    expected: "Overview rings reflect the saved goals. Nutrition goal-day XP still awards correctly on the next day with matching intake."
    why_human: "End-to-end flow confirmation across all three gaps."
---

# Phase 16: Set Nutrition Goals — Verification Report

**Phase Goal:** Ship the user-facing goal-editor flow for nutrition goals (kcal/protein/fat/carbs/sugar) — including a Mifflin–St Jeor TDEE calculator that suggests Cut / Maintain / Bulk targets — plus a discoverability banner on the Overview tab. Personal stats persisted alongside nutritionGoals in DataStore. Engine, schema, Overview rings, and goal-day XP awarding are unchanged from Phase 15.
**Verified:** 2026-04-28
**Status:** gaps_found — 3 blockers from code review confirmed in codebase
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `UserPhysicalStats` data class + `Sex` + `ActivityLevel` enums exist in commonMain | VERIFIED | `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/UserPhysicalStats.kt` — exact spec, no imports, no defaults |
| 2 | Mifflin–St Jeor BMR formula is correct for both sexes | VERIFIED | `TdeeCalculator.kt` lines 26-32; male sexConstant=+5, female=-161; confirmed by 12 passing unit tests |
| 3 | TDEE = BMR × activity multiplier, 5 tiers at 1.2/1.375/1.55/1.725/1.9 | VERIFIED | `TdeeCalculator.kt` lines 17-23; Cut=−500 / Maintain=0 / Bulk=+300 per D-16-06 |
| 4 | Macro split: protein 2.2/2.0/1.8 g/kg; fat 25% of kcal; carbs remainder; sugar=50 | VERIFIED | `TdeeCalculator.kt` lines 53-68; `coerceAtLeast(0)` guards negative carbs |
| 5 | `SettingsRepository` persists UserPhysicalStats + bannerDismissed via DataStore | VERIFIED | Lines 30-36, 110-153; all 5 stat keys + banner key present; `setUserPhysicalStats` writes atomically in single `edit` block |
| 6 | `OverviewViewModel` exposes `userPhysicalStats`, `nutritionGoalsBannerVisible` StateFlows + `updateUserPhysicalStats` + `dismissBanner` methods | VERIFIED | Lines 117-131, 212-226; all `@NativeCoroutinesState` annotated; banner-dismiss chained in `updateNutritionGoals` |
| 7 | Discoverability banner appears on Overview tab; tapping opens editor; "×" dismisses persistently | VERIFIED | Android: `OverviewScreen.kt` lines 133-142; iOS: `OverviewView.swift` lines 29-38, 62-65; both call `dismissBanner()` |
| 8 | After saving goals, Overview tab rings reflect the new goals without requiring a manual refresh | **FAILED** | iOS: `NutritionGoalsEditorView` creates its own `OverviewViewModel` (line 8); `.sheet()` has no `onDismiss:` refresh callback; `OverviewView` reads goals from `uiState.nutritionGoals` which is only populated via `refresh()` |
| 9 | Picker wheels initialize from stored values and do not reset mid-edit | **FAILED** | Android: `remember(storedStats)` / `remember(storedGoals)` as keys (lines 91-115) reset all fields on every DataStore emission; iOS: `observeGoals()` unconditionally overwrites picker state on every emission |
| 10 | Tapping a suggestion card scrolls all drum pickers to the calculated values | **FAILED** | `TdeeCalculator.buildSplit()` returns raw `roundToInt()` kcal (e.g. 2259) which is not in the DrumPicker range `(800..6000 step 50)`; `DrumPicker.LaunchedEffect` silently skips when `items.indexOf(selectedItem) == -1` |

**Score:** 7/10 truths verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `shared/.../domain/model/UserPhysicalStats.kt` | Sex + ActivityLevel enums + data class, no defaults | VERIFIED | Exact match to spec; no imports; compiles |
| `shared/.../domain/nutrition/TdeeCalculator.kt` | Pure object: bmr/tdee/suggestions + TdeeSuggestions + MacroSplit | VERIFIED | All functions present; no coroutines/Koin/DB imports |
| `shared/src/commonTest/.../TdeeCalculatorTest.kt` | 12 unit tests for BMR/TDEE/macro split | VERIFIED | 12 `@Test` methods using `kotlin.test` only |
| `shared/.../data/repository/SettingsRepository.kt` | 5 stat keys + bannerDismissed key + flows + setters | VERIFIED | All keys defined; atomicity per D-16-10 write pattern used |
| `shared/.../presentation/overview/OverviewViewModel.kt` | 4 new StateFlows + 2 new methods | VERIFIED | All `@NativeCoroutinesState`; dismissBanner chained in updateNutritionGoals |
| `androidApp/.../screens/NutritionGoalsEditorScreen.kt` | 3-section editor with DrumPickers, stats, suggestions | VERIFIED (with gaps) | All 3 sections present; see Gap 2 (reset) and Gap 3 (kcal snap) |
| `androidApp/.../screens/OverviewScreen.kt` | Banner + edit pencil wired | VERIFIED | `AnimatedVisibility` banner + `NutritionGoalsEditorRoute` navigation |
| `iosApp/.../NutritionGoalsEditorView.swift` | SwiftUI Form editor with DisclosureGroup + wheel pickers | VERIFIED (with gaps) | All 3 sections present; see Gap 1 (separate VM), Gap 2 (reset), Gap 3 (kcal snap) |
| `iosApp/.../OverviewView.swift` | Banner + edit pencil + sheet presentation | VERIFIED (with gaps) | Sheet present; no onDismiss refresh callback (Gap 1) |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `OverviewScreen` | `NutritionGoalsEditorScreen` | `navController.navigate(NutritionGoalsEditorRoute)` | WIRED | Both edit pencil (line 147) and banner tap (line 139) navigate to route |
| `NutritionGoalsEditorRoute` | `NutritionGoalsEditorScreen` | `MainScreen.kt composable<NutritionGoalsEditorRoute>` | WIRED | Line 181-182 of MainScreen.kt |
| `OverviewView` | `NutritionGoalsEditorView` | `.sheet(isPresented: $showEditor)` | WIRED | Lines 62-65; both banner tap and edit pencil set `showEditor = true` |
| `NutritionGoalsEditorView.saveGoals()` | `OverviewViewModel.updateNutritionGoals` | `viewModel.updateNutritionGoals(goals:)` | PARTIAL — different VM instances on iOS | Editor's VM calls updateNutritionGoals on a factory-scoped instance, not OverviewView's instance; rings see stale goals |
| `OverviewViewModel.updateNutritionGoals` | `SettingsRepository.setNutritionGoalsBannerDismissed` | inline call on save | WIRED | Line 203 of OverviewViewModel.kt — dismiss chained correctly |
| `SettingsRepository.userPhysicalStats` | `OverviewViewModel.userPhysicalStats` | `settingsRepository.userPhysicalStats.stateIn(...)` | WIRED | Line 118-120 of OverviewViewModel.kt |
| `SettingsRepository.nutritionGoalsBannerDismissed` | `OverviewViewModel.nutritionGoalsBannerVisible` | `settingsRepository.nutritionGoalsBannerDismissed.map { !it }.stateIn(...)` | WIRED | Lines 128-130 |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `NutritionRingsCard` (Android) | `goals: NutritionGoals` | `uiState.nutritionGoals` loaded in `refresh()` via `.first()` | Yes — reads DataStore on each refresh | HOLLOW after save (WR-02 / CR-01: uiState not reactive to nutritionGoals StateFlow; Android OK because same VM via ViewModelStore, but see note) |
| `nutritionRingsSection` (iOS) | `goals` from `uiState.nutritionGoals` via `observeUiState()` | `uiStateFlow` on OverviewView's separate VM instance | Yes — DataStore real data | DISCONNECTED from editor save: editor's VM is a different factory instance; OverviewView's VM does not receive `updateNutritionGoals` call |
| `SuggestionRow` (Android) | `suggestions: TdeeSuggestions` | `derivedStateOf { TdeeCalculator.suggestions(currentStatsForCalc) }` | Yes — pure computation from text inputs | FLOWING (live computation, no network/DB) |
| `Section 2` suggestions (iOS) | `suggestions: SharedTdeeSuggestions` | `var suggestions: SharedTdeeSuggestions { SharedTdeeCalculator.shared.suggestions(stats: currentStats) }` | Yes — pure computed property | FLOWING |

---

### Behavioral Spot-Checks

| Behavior | Check | Result | Status |
|----------|-------|--------|--------|
| `TdeeCalculator.bmr` for 80kg/180cm/30/male = 1780.0 | Manual trace: 10×80 + 6.25×180 − 5×30 + 5 = 1780 | 1780.0 | PASS |
| `TdeeCalculator.buildSplit` kcal for moderately active male TDEE 2759 — cut is 2259 | 2259 not in `(800..6000 step 50)` | `False` (python3 verification) | FAIL — WR-05 confirmed |
| `DrumPicker.LaunchedEffect` behavior for `selectedItem` not in `items` | `items.indexOf(selectedItem) == -1` → silent skip, no scroll | No scroll, no crash | FAIL (silent — picker shows wrong position) |
| Phase 15 engine files unmodified | `git log` for NutritionGoalDayPolicy.kt, XpFormula.kt, NutritionGoals.kt since 2026-04-27 | 0 commits | PASS |
| `NutritionGoalsEditorView.swift` registered in pbxproj | grep NutritionGoalsEditorView project.pbxproj | PBXBuildFile + Sources entry found | PASS |

---

### Requirements Coverage (D-16 Decisions)

| Decision | Description | Status | Evidence |
|----------|-------------|--------|----------|
| D-16-01 | Edit button + banner on Overview opens editor | SATISFIED | Android edit pencil + banner both navigate to `NutritionGoalsEditorRoute`; iOS `.sheet(isPresented: $showEditor)` |
| D-16-02 | Single screen, 3 stacked sections (stats / suggestions / pickers) | SATISFIED | Both platforms: stats collapsible, suggestion row, picker section, save button |
| D-16-03 | Drum pickers — DrumPicker.kt on Android; Picker(.wheel) on iOS | SATISFIED | Android: 5 `DrumPicker` calls; iOS: 5 `wheelPicker` calls |
| D-16-04 | Mifflin–St Jeor BMR | SATISFIED | `TdeeCalculator.bmr()` — verified against known values in unit tests |
| D-16-05 | 5 activity tiers at correct multipliers | SATISFIED | `ACTIVITY_MULTIPLIER` map with all 5 tiers and correct values |
| D-16-06 | Cut=−500 / Maintain=0 / Bulk=+300 | SATISFIED | `suggestions()` method; verified by unit tests |
| D-16-07 | Macro derivation: protein 2.2/2.0/1.8 g/kg; fat 25%; carbs remainder; sugar=50 | SATISFIED | `buildSplit()` implementation + unit tests |
| D-16-08 | Live preview suggestions as stats change | SATISFIED | Android: `derivedStateOf { TdeeCalculator.suggestions(...) }`; iOS: computed property |
| D-16-09 | Stats section collapsible; skip allowed | SATISFIED | Android: `AnimatedVisibility(expanded)`; iOS: `DisclosureGroup` |
| D-16-10 | DataStore separate keys for 5 stats + banner | SATISFIED | `SettingsRepository.kt` lines 30-36 |
| D-16-11 | `UserPhysicalStats?` Flow (null = never set) | SATISFIED | `userPhysicalStats: Flow<UserPhysicalStats?>` returning null when any key missing |
| D-16-12 | kg/cm only in calculator | SATISFIED | `UserPhysicalStats` has `weightKg: Double`, `heightCm: Int`; no WeightUnit reference |
| D-16-13 | Banner above rings card, dismissable | SATISFIED | Both platforms have banner with "×" dismiss and tap-to-open |
| D-16-14 | Banner dismissed by "×" OR successful save | SATISFIED | `dismissBanner()` + `updateNutritionGoals` chaining `setNutritionGoalsBannerDismissed(true)` |
| D-16-15 | TOLERANCE = 0.10 unchanged | SATISFIED | `NutritionGoalDayPolicy.kt` not modified in Phase 16 (0 git commits) |
| D-16-16 | `NutritionGoals` sugar field kept; 5 macros | SATISFIED | `NutritionGoals.kt` not modified; all 5 rings present in both platforms |
| D-16-17 | `XpFormula` constants unchanged | SATISFIED | `XpFormula.kt` not modified; `NUTRITION_GOAL_DAY_XP = 25`, `STREAK_NUTRITION_7D = 100` verified |

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `NutritionGoalsEditorScreen.kt` | 91-115 | `remember(storedStats)` / `remember(storedGoals)` as keys — fields reset on every DataStore emission | BLOCKER | User edits silently discarded mid-input on DataStore re-emission |
| `NutritionGoalsEditorView.swift` | 243-257 | `observeGoals()` unconditionally overwrites picker state on every emission | BLOCKER | Picker reset after save-triggered DataStore emission during dismissal animation |
| `NutritionGoalsEditorScreen.kt` | 181 | `kcalValue = split.kcal` — raw `roundToInt()` value not snapped to picker step 50 | BLOCKER | DrumPicker silently ignores suggestion; stays at previous position |
| `NutritionGoalsEditorView.swift` | 197 | `kcalValue = Int(split.kcal)` — same kcal snap issue on iOS | BLOCKER | iOS Picker(.wheel) undefined behavior with out-of-range selection |
| `OverviewView.swift` | 16 | `@State private var bannerVisible: Bool = true` — initializes visible until async correction | WARNING | Dismissed banner briefly flashes on every tab appearance (WR-06) |
| `OverviewScreen.kt` | 88-90 | `LaunchedEffect(Unit) { viewModel.refresh() }` duplicates `init { refresh() }` in ViewModel | INFO | Double refresh on first composition; last `.value = ...` wins but wastes a coroutine run (IN-02) |

---

### Human Verification Required

#### 1. iOS rings update after editor save (Gap 1 — CR-01)

**Test:** Save goals in the iOS editor (tap "Ziele speichern"). After the sheet dismisses, observe the Overview nutrition rings immediately — before tapping the toolbar refresh button.
**Expected:** The rings show the newly saved calorie/protein/carbs/fat/sugar goals.
**Why human:** Requires visual verification of the iOS screen state post-dismiss. Cannot be determined by static analysis alone (depends on Koin instance lifecycle and whether DataStore emission races the sheet dismissal).

#### 2. Picker reset during mid-edit on Android (Gap 2 — WR-03)

**Test:** Open the Android editor. Begin typing a new weight (e.g. change "80" to "9"). Wait 2-3 seconds without tapping Save. Watch whether the weight field reverts to the stored value.
**Expected:** The field retains "9" (or the user's partial input) until they explicitly save.
**Why human:** Race condition triggered by DataStore combine() initial emission; timing-dependent and platform-specific.

#### 3. Suggestion card kcal picker position (Gap 3 — WR-05)

**Test:** On both platforms, enter stats and tap the "Erhalt" (Maintain) card. Observe the Kalorien picker wheel.
**Expected:** The picker visually scrolls to the nearest 50-kcal value to the TDEE suggestion.
**Why human:** Whether the silent skip is visible to the user (picker stays at old position) vs. whether the picker snaps to nearest depends on platform-specific behavior. Visual check required.

#### 4. Banner flash on iOS (WR-06)

**Test:** iOS only. Dismiss the banner via "×". Return to the Overview tab repeatedly. Check for a brief banner appearance before it disappears.
**Expected:** No banner flash after dismissal. Banner should be invisible immediately on each subsequent tab appearance.
**Why human:** Requires observing the animation transition which cannot be detected statically.

#### 5. Full happy-path end-to-end (all platforms)

**Test:** Enter weight/height/age/sex/activity, tap the "Erhalt" (Maintain) card, verify all 5 pickers update, tap "Ziele speichern". Navigate away and back. Check nutrition rings.
**Expected:** Rings show saved goal values. Nutrition goal-day XP mechanism continues to function (existing Phase 15 behavior unaffected).
**Why human:** End-to-end verification across the 3 code-quality gaps; requires interaction.

---

### Gaps Summary

Three gaps block the goal-editor flow from being fully functional:

**Gap 1 — CR-01 (BLOCKER): iOS rings stale after save.** The most critical issue. `NutritionGoalsEditorView` creates its own `OverviewViewModel` factory instance separate from `OverviewView`'s instance. The editor saves goals to DataStore and updates its own VM's `_uiState`, but `OverviewView`'s `uiState.nutritionGoals` stays at the pre-edit value because it was loaded once via `.first()` in `refresh()` and there is no `onDismiss:` refresh callback on the sheet. Root cause: `viewModel { OverviewViewModel(...) }` in Koin is factory-scoped; `KoinPlatform.getKoin().get()` returns a new instance each call on iOS. Fix: add `.sheet(isPresented: $showEditor, onDismiss: { viewModel.refresh() })` and ensure `OverviewView` passes its ViewModel to the editor (or the editor takes no goals-editing responsibility and delegates to `OverviewView`'s VM directly).

**Gap 2 — WR-03/WR-04 (BLOCKER): Picker/field state resets on DataStore re-emission.** Both Android's `remember(storedStats)` pattern and iOS's unconditional `observeGoals()` assignment overwrite user edits when the DataStore Flow emits during composition. This defeats the editing experience: any latent emission resets the user to stored values. Fix: use a one-shot initialization guard (either `LaunchedEffect` with a boolean flag on Android, or a `guard !initialized` in the Swift observation loops).

**Gap 3 — WR-05 (BLOCKER): Suggestion kcal not snapped to picker step.** `TdeeCalculator.buildSplit()` returns raw kcal (e.g. 2259), which is not a multiple of 50 and therefore not present in the DrumPicker list `(800..6000 step 50)`. Android's DrumPicker silently ignores the value (no scroll). iOS Picker(.wheel) has undefined behavior with out-of-range selection. The suggestion cards display the correct kcal values in text, but tapping a card fails to move the kcal picker. Fix: snap `kcalValue = (split.kcal / 50) * 50` before applying to the picker (or snap to step 50 inside `TdeeCalculator.buildSplit` for the kcal field only).

All three gaps stem from integration-level issues (not from the domain logic, which is correct and fully tested). The TDEE math (TdeeCalculator), DataStore persistence (SettingsRepository), ViewModel state management (OverviewViewModel), and Phase 15 engine files are all correctly implemented. The defects are in the wiring between the editor UI and the overview display, and in the state-initialization patterns used for editable fields.

---

_Verified: 2026-04-28_
_Verifier: Claude (gsd-verifier)_
