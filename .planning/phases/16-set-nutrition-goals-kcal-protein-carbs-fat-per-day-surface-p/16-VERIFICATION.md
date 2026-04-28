---
phase: 16-set-nutrition-goals-kcal-protein-carbs-fat-per-day-surface-p
verified: 2026-04-28T15:25:00Z
status: passed
score: 10/10 must-haves verified (3 prior gaps closed)
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 7/10
  gaps_closed:
    - "Gap 1 / CR-01: After saving goals, Overview rings reflect new values without manual refresh (iOS)."
    - "Gap 2 / WR-03 / WR-04: Picker wheels and stats fields initialize once and do not reset to stored values mid-edit (Android + iOS)."
    - "Gap 3 / WR-05: Tapping a suggestion card applies a kcal value present in the picker range (snap-to-50 in shared TdeeCalculator)."
  warnings_closed:
    - "WR-06: iOS banner-flash on dismissed-banner reappearance — bannerVisible default flipped to false."
    - "IN-02: Android double-refresh on first composition — redundant LaunchedEffect(Unit) removed."
  gaps_remaining: []
  regressions: []
human_verification:
  - test: "Open the editor on iOS, change a goal value, tap 'Ziele speichern'. Observe the rings on the Overview tab as soon as the sheet dismisses (do NOT tap the toolbar refresh)."
    expected: "Rings reflect the saved goal values immediately on dismissal."
    why_human: "End-to-end visual confirmation that .sheet(onDismiss: viewModel.refresh()) updates the rings before the user could tap refresh manually."
  - test: "Open the editor on Android, type a new weight (e.g. change '80' to '95'), wait 3 seconds without saving."
    expected: "Field retains the typed value; does NOT revert to stored '80'."
    why_human: "Race-condition fix (rememberSaveable + LaunchedEffect guard) — visual confirmation needed."
  - test: "Open the editor on both platforms, fill stats, tap each suggestion card (Defizit / Erhalt / Aufbau)."
    expected: "Kalorien picker scrolls to a multiple of 50 (e.g. 2250 / 2750 / 3050 for the 80kg/180cm/30/male/moderately reference)."
    why_human: "Visual confirmation that DrumPicker (Android) / Picker(.wheel) (iOS) actually scrolls to the snapped kcal value."
  - test: "iOS only: dismiss the banner via '×'. Quit and relaunch the app. Open the Overview tab repeatedly."
    expected: "No banner flash; banner stays hidden across tab switches."
    why_human: "WR-06 visual confirmation — bannerVisible now defaults to false; worst case is one missing-banner frame for non-dismissed users."
---

# Phase 16: Set Nutrition Goals — Verification Report (RE-VERIFICATION)

**Phase Goal:** Ship the user-facing goal-editor flow for nutrition goals (kcal/protein/fat/carbs/sugar) — including a Mifflin–St Jeor TDEE calculator that suggests Cut / Maintain / Bulk targets — plus a discoverability banner on the Overview tab. Personal stats persisted alongside `nutritionGoals` in DataStore. Engine, schema, Overview rings, and goal-day XP awarding are unchanged from Phase 15.

**Verified:** 2026-04-28T15:25:00Z
**Status:** **PASS** — all 10 truths verified; 3 prior blockers closed; 5 prior warnings closed; 0 regressions.
**Re-verification:** Yes — supersedes the 2026-04-28 (initial) verdict of `gaps_found`.

---

## Top-level verdict

**PASS.** All three blockers (Gap 1 / Gap 2 / Gap 3) and three of the four warnings (CR-01 is the same as Gap 1; WR-03/WR-04 are the same as Gap 2; WR-05 is the same as Gap 3; WR-06 + IN-02 are independent warnings now closed) flagged in the prior verification report are closed in the current source on disk. The shared TDEE calculator now snaps kcal to picker steps; both editor screens initialize state once via guards; iOS sheet dismissal triggers a refresh on the parent ViewModel; iOS banner no longer flashes; Android no longer double-refreshes on first composition. Test suite (`:shared:iosSimulatorArm64Test --tests TdeeCalculatorTest`) passes 14/14, including the two new picker-range invariant tests.

The four human-verification items in the frontmatter are interactive UX confirmations that cannot be checked statically — they remain optional follow-ups before user-facing release. Code on disk is structurally correct and tested at the layer that can be tested.

---

## Previous verdict (superseded)

The prior verification (`2026-04-28T00:00:00Z`, initial run) reported:

- **Status:** `gaps_found`
- **Score:** 7/10 must-haves verified
- **Blockers:** 3 (Gap 1: iOS rings stale after save / CR-01; Gap 2: picker reset mid-edit / WR-03 + WR-04; Gap 3: kcal not snapped to picker step / WR-05)
- **Warnings:** WR-06 (iOS banner flash) and IN-02 (Android double-refresh)

That report's `gaps:` and `human_verification:` blocks have been entirely actioned by gap-closure plans 16-07, 16-08, and 16-09. The original report content is preserved in git history; the snapshot is no longer authoritative.

---

## Goal Achievement

### Observable Truths

| #   | Truth                                                                                                                       | Prior status | Now status     | Evidence (file:lines)                                                                                                                         |
| --- | --------------------------------------------------------------------------------------------------------------------------- | ------------ | -------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | `UserPhysicalStats` data class + `Sex` + `ActivityLevel` enums exist in commonMain                                          | VERIFIED     | VERIFIED       | `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/UserPhysicalStats.kt` (unchanged)                                                  |
| 2   | Mifflin–St Jeor BMR formula correct for both sexes                                                                          | VERIFIED     | VERIFIED       | `TdeeCalculator.kt:26-32`; `bmrMaleKnownValue` + `bmrFemaleKnownValue` pass                                                                    |
| 3   | TDEE = BMR × activity multiplier; 5 tiers at 1.2/1.375/1.55/1.725/1.9; deltas −500/0/+300                                   | VERIFIED     | VERIFIED       | `TdeeCalculator.kt:17-23, 40-47`                                                                                                              |
| 4   | Macro split: protein 2.2/2.0/1.8 g/kg; fat 25% kcal; carbs remainder; sugar=50; carbs ≥ 0                                   | VERIFIED     | VERIFIED       | `TdeeCalculator.kt:49-75`; `proteinCut/Maintain/Bulk*PerKg` + `carbsNonNegativeOnExtremeLowKcal` pass                                          |
| 5   | `SettingsRepository` persists UserPhysicalStats + bannerDismissed via DataStore                                             | VERIFIED     | VERIFIED       | `SettingsRepository.kt` (unchanged)                                                                                                            |
| 6   | `OverviewViewModel` exposes `userPhysicalStats`, `nutritionGoalsBannerVisible` StateFlows + `updateUserPhysicalStats` + `dismissBanner` | VERIFIED | VERIFIED | `OverviewViewModel.kt` (unchanged)                                                                                                            |
| 7   | Discoverability banner appears on Overview; tapping opens editor; "×" dismisses persistently                                | VERIFIED     | VERIFIED       | Android `OverviewScreen.kt:130-138`; iOS `OverviewView.swift:29-38, 62-67`                                                                     |
| 8   | After saving goals, Overview rings reflect new goals without manual refresh                                                 | **FAILED**   | **VERIFIED**   | iOS `OverviewView.swift:62-64`: `.sheet(isPresented: $showEditor, onDismiss: { viewModel.refresh() })`; Android: same VM via `koinViewModel()` |
| 9   | Picker wheels and stats fields initialize once and do not reset to stored values mid-edit                                   | **FAILED**   | **VERIFIED**   | Android `NutritionGoalsEditorScreen.kt:113-137` (rememberSaveable guards + LaunchedEffect); iOS `NutritionGoalsEditorView.swift:31-32, 234, 253` (`@State` guards + `guard !*Initialized else { continue }`) |
| 10  | Tapping a suggestion card applies kcal that is a multiple of 50 and inside the picker range                                 | **FAILED**   | **VERIFIED**   | `TdeeCalculator.kt:54-60`: `((kcalDouble + 25.0).toInt() / 50) * 50` + `coerceIn(800, 6000)`; tests `cutSuggestionKcalIsMultipleOf50AndInPickerRange` + `allSuggestionKcalSnappedToFifty` pass |

**Score:** 10/10 truths verified (was 7/10).

---

## Gap-by-gap re-verification

### Gap 1 / CR-01 — iOS rings stale after editor save → CLOSED

**Prior issue:** iOS `NutritionGoalsEditorView` instantiated its own `OverviewViewModel` via `KoinHelper.shared.getOverviewViewModel()` (factory-scoped → distinct instance). The editor saved to DataStore, but `OverviewView.uiState.nutritionGoals` was loaded once via `.first()` in `refresh()` and never refreshed automatically after sheet dismissal. The sheet had no `onDismiss:` callback.

**Fix verified on disk:**

- `iosApp/iosApp/Views/Overview/OverviewView.swift:62-67`:
  ```swift
  .sheet(isPresented: $showEditor, onDismiss: {
      viewModel.refresh()
  }) {
      NutritionGoalsEditorView()
          .presentationDragIndicator(.visible)
  }
  ```
  Whenever the editor sheet dismisses (Save, drag-down, Cancel, tap-outside), `viewModel.refresh()` re-runs `_uiState` population and `observeUiState()` redraws the rings.

**Plan that closed it:** 16-09 Task 09-01 (commit `bc90dd5`).

**Result:** **CLOSED.** The chosen fix (onDismiss callback) is one of the two acceptable paths from the original `missing:` field. Behavioural confirmation queued under `human_verification` test #1.

---

### Gap 2 / WR-03 (Android) + WR-04 (iOS) — Picker / field state resets on DataStore re-emission → CLOSED

**Prior issue (Android):** `NutritionGoalsEditorScreen.kt` lines 91-115 declared all six stats text fields with `remember(storedStats)` and all five picker values with `remember(storedGoals)` as keys. Any DataStore Flow re-emission (cold-start delay, save round-trip, configuration change) silently reset all in-progress edits to stored values.

**Prior issue (iOS):** `NutritionGoalsEditorView.swift` `observeGoals()` / `observeStats()` async-sequence loops unconditionally wrote to `kcalValue` / `proteinValue` / etc. on every emission, including the post-save emission triggered by the editor itself.

**Fix verified on disk (Android):**

- `androidApp/.../NutritionGoalsEditorScreen.kt:113-114`: `var statsInitialized by rememberSaveable { mutableStateOf(false) }` + `var goalsInitialized by rememberSaveable { mutableStateOf(false) }` (survive rotation).
- `:116-126`: `LaunchedEffect(storedStats) { if (!statsInitialized && storedStats != null) { ...; statsInitialized = true } }` — one-shot seeding of weight/height/age/sex/activity.
- `:128-137`: `LaunchedEffect(storedGoals) { if (!goalsInitialized) { ...; goalsInitialized = true } }` — one-shot seeding of all 5 picker values.
- Negative grep: `grep -cE 'remember\(storedStats\)|remember\(storedGoals\)' …NutritionGoalsEditorScreen.kt` → `0`. Old anti-pattern fully removed.

**Fix verified on disk (iOS):**

- `iosApp/.../NutritionGoalsEditorView.swift:31-32`: `@State private var statsInitialized: Bool = false` + `@State private var goalsInitialized: Bool = false`.
- `:234`: `guard !statsInitialized else { continue }` inside `observeStats()`.
- `:253`: `guard !goalsInitialized else { continue }` inside `observeGoals()`.
- `continue` (not `break`) so first-launch nil emissions do not permanently lock the loop.

**Plan that closed it:** 16-08 Tasks 1+2 (commits `b3cba57` Android, `7245ccf` iOS).

**Result:** **CLOSED on both platforms.** Behavioural confirmation queued under `human_verification` test #2.

---

### Gap 3 / WR-05 — Suggestion kcal not snapped to picker step → CLOSED

**Prior issue:** `TdeeCalculator.buildSplit()` returned raw `kcalDouble.roundToInt()` (e.g. 2259 for the reference male). Both Android `DrumPicker` (range `(800..6000 step 50)`) and iOS `Picker(.wheel)` (`stride(from: 800, through: 6000, by: 50)`) silently failed to scroll when given a non-multiple-of-50 kcal.

**Fix verified on disk:**

- `shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/TdeeCalculator.kt:54-60`:
  ```kotlin
  // Snap to nearest multiple of 50 (round-half-up) and clamp to picker range
  // (800..6000 step 50) so the value is always present on both platforms.
  val kcalSnapped = ((kcalDouble + 25.0).toInt() / 50) * 50
  val kcal = kcalSnapped.coerceIn(800, 6000)
  ```
- Macros derived from snapped `kcal` (lines 62, 66) so card display kcal == picker kcal.
- New tests `shared/src/commonTest/kotlin/com/pumpernickel/domain/nutrition/TdeeCalculatorTest.kt:76-97`:
  - `cutSuggestionKcalIsMultipleOf50AndInPickerRange` asserts `s.cut.kcal % 50 == 0`, in `800..6000`, and equal to 2250 for the 80kg/180cm/30/male/moderately reference.
  - `allSuggestionKcalSnappedToFifty` asserts the same invariant for cut + maintain + bulk.
- Three pre-existing TDEE-equality tests (`cutSuggestionIsTdeeMinus500`, `maintainSuggestionEqualsTdee`, `bulkSuggestionIsTdeePlus300`) widened from `absoluteTolerance = 1.0` to `25.0` with a justifying comment (the round-half-up snap can diverge from raw by up to ±25).

**Test execution (just now):**

```
$ ./gradlew :shared:iosSimulatorArm64Test --tests "com.pumpernickel.domain.nutrition.TdeeCalculatorTest"
BUILD SUCCESSFUL in 9s
TEST-com.pumpernickel.domain.nutrition.TdeeCalculatorTest.xml:
  tests="14" skipped="0" failures="0" errors="0" time="0.003"
```

All 14 tests pass. The two new picker-range invariant tests are present and green.

**Plan that closed it:** 16-07 Tasks 1+2 (commits `e66f8b4` feat + `02dd6da` test — note: SUMMARY referenced `9c99758` / `1e80853` but the actual hashes after merge are `e66f8b4` / `02dd6da`; substance is identical and on disk).

**Result:** **CLOSED.** Shared-layer fix means no platform-side change was needed — Android editor `:558` (`(800..6000 step 50)`) and iOS editor `:117` (`stride(from: 800, through: 6000, by: 50)`) are both unchanged and now receive a kcal that is always present in their respective ranges. Behavioural confirmation queued under `human_verification` test #3.

---

### Warning WR-06 — iOS banner-flash on dismissed-banner reappearance → CLOSED

**Prior issue:** `OverviewView.swift:16` had `@State private var bannerVisible: Bool = true`. For users who had previously dismissed the banner, every Overview tab appearance briefly showed the banner (one frame) before `observeBannerVisible()` flipped it to `false`.

**Fix verified on disk:**

- `iosApp/iosApp/Views/Overview/OverviewView.swift:16`: `@State private var bannerVisible: Bool = false` (was `true`).
- `:104-113`: `observeBannerVisible()` writes the persisted value on first emission with an `easeOut(0.3)` animation.
- Worst case is a single-frame missing-banner for users who have not dismissed it — strictly less distracting than the prior flash.

**Plan that closed it:** 16-09 Task 09-02 (commit `2aba169`).

**Result:** **CLOSED.** Behavioural confirmation queued under `human_verification` test #4.

---

### Warning IN-02 — Android double-refresh on first composition → CLOSED

**Prior issue:** `OverviewScreen.kt` had a `LaunchedEffect(Unit) { viewModel.refresh() }` block that duplicated `OverviewViewModel.init { refresh() }`. On every first composition, two concurrent refresh coroutines ran; last `.value = ...` won, but it wasted one full DataStore + Room round-trip.

**Fix verified on disk:**

- `androidApp/.../OverviewScreen.kt`: Negative grep `grep -cE 'LaunchedEffect\(Unit\)' OverviewScreen.kt` → `0`. The redundant block is fully removed; no `LaunchedEffect(Unit)` exists in the file.
- `OverviewViewModel.init { refresh() }` is now the single source of first-composition data load, as designed.
- The manual toolbar refresh `IconButton(onClick = { viewModel.refresh() })` (line 93) is intentionally retained.

**Plan that closed it:** 16-09 Task 09-03 (commit `bdbb624`).

**Result:** **CLOSED.** No human verification needed — this was a performance / clarity fix, not a correctness fix.

---

## Build verification

- **Shared (commonMain + iosSimulatorArm64Test):** `./gradlew :shared:iosSimulatorArm64Test --tests TdeeCalculatorTest` → `BUILD SUCCESSFUL in 9s`, 14/14 tests pass.
- **Android:** Verified during 16-08 (`./gradlew :androidApp:assembleDebug` → `BUILD SUCCESSFUL`) and 16-09 (`./gradlew :androidApp:assembleDebug` → `BUILD SUCCESSFUL`).
- **iOS:** Verified during 16-08 (`xcodebuild ... iPhone Simulator ... Debug build` → `** BUILD SUCCEEDED ** [59.3s]`) and 16-09 (`** BUILD SUCCEEDED ** [8.1s]` with iPhone 17 destination, since Xcode 26.3 lacks iPhone 15 simulator).

No build failures introduced; all gap-closure changes compile and link clean on both platforms.

---

## Anti-Patterns Found

| File | Line | Pattern | Severity | Status |
|------|------|---------|----------|--------|
| `NutritionGoalsEditorScreen.kt` | 91-115 (prior) | `remember(storedStats)` / `remember(storedGoals)` keying | BLOCKER | **REMOVED** (16-08); current lines 113-137 use rememberSaveable + LaunchedEffect guards |
| `NutritionGoalsEditorView.swift` | 243-257 (prior) | unconditional `observeGoals()` overwrite | BLOCKER | **GUARDED** (16-08); current lines 234, 253 use `guard !*Initialized else { continue }` |
| `NutritionGoalsEditorScreen.kt` / `NutritionGoalsEditorView.swift` | suggestion application sites | raw `roundToInt()` kcal not snapped to picker step | BLOCKER | **CLOSED at root** (16-07); shared calculator snaps to nearest 50 |
| `OverviewView.swift` | 16 (prior) | `bannerVisible: Bool = true` causing flash | WARNING | **FIXED** (16-09); now `false` |
| `OverviewScreen.kt` | 88-90 (prior) | redundant `LaunchedEffect(Unit) { viewModel.refresh() }` | INFO | **REMOVED** (16-09); 0 occurrences in file |

No new anti-patterns introduced by gap-closure plans.

---

## Requirements Coverage (D-16 Decisions)

All 17 D-16 decisions remain SATISFIED — see prior report for evidence. Re-verification confirms:

- D-16-01..D-16-09: editor placement / shape / drum pickers / TDEE math / suggestions / collapse — UNCHANGED by gap-closure plans.
- D-16-10..D-16-14: DataStore persistence / banner / dismissal — UNCHANGED.
- D-16-15..D-16-17: tolerance / sugar / XP carried forward — UNCHANGED.

The kcal-snap fix in 16-07 does not violate any D-16 decision (D-16-06 specifies the deltas, not the rounding strategy; the UI-SPEC § "Gram rounding for suggestion cards" already mentioned step-aligned values for non-kcal macros — kcal now follows the same principle).

---

## Files Verified On Disk

| File | Plan | Status |
|------|------|--------|
| `shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/TdeeCalculator.kt` | 16-07 | VERIFIED — snap-to-50 + clamp at lines 54-60 |
| `shared/src/commonTest/kotlin/com/pumpernickel/domain/nutrition/TdeeCalculatorTest.kt` | 16-07 | VERIFIED — 14 tests, 2 new picker-range invariant tests at 76-97, 3 tolerance widenings |
| `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/NutritionGoalsEditorScreen.kt` | 16-08 | VERIFIED — rememberSaveable guards + LaunchedEffect at 113-137; old `remember(storedStats/storedGoals)` removed |
| `iosApp/iosApp/Views/Overview/NutritionGoalsEditorView.swift` | 16-08 | VERIFIED — @State guards at 31-32; `guard !*Initialized else { continue }` at 234, 253 |
| `iosApp/iosApp/Views/Overview/OverviewView.swift` | 16-09 | VERIFIED — `.sheet(onDismiss: viewModel.refresh())` at 62-64; bannerVisible default `false` at 16 |
| `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/OverviewScreen.kt` | 16-09 | VERIFIED — `LaunchedEffect(Unit)` count = 0 |

---

## Gaps Summary

**No outstanding gaps.** All three blockers and all flagged warnings from the prior verification report are closed in the current source on disk, with passing test evidence at the shared layer and successful builds on both platforms.

Four `human_verification` items remain — these are interactive UX confirmations (not gaps): visual confirmation of rings refresh after iOS save, mid-edit field preservation on Android, suggestion-card picker scroll on both platforms, and absence of banner flash on iOS. These are recommended before user-facing release but do not block phase sign-off because the structural fixes are verified at the static-analysis + unit-test layer.

---

_Re-verified: 2026-04-28T15:25:00Z_
_Re-verifier: Claude (gsd-verifier, goal-backward re-verification mode)_
_Supersedes: 2026-04-28T00:00:00Z (initial) verdict of `gaps_found` 7/10_
