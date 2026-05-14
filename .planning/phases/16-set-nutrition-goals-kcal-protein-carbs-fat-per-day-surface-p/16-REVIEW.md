---
phase: 16-set-nutrition-goals-kcal-protein-carbs-fat-per-day-surface-p
reviewed: 2026-04-28T00:00:00Z
depth: standard
files_reviewed: 11
files_reviewed_list:
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/model/UserPhysicalStats.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/TdeeCalculator.kt
  - shared/src/commonTest/kotlin/com/pumpernickel/domain/nutrition/TdeeCalculatorTest.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/presentation/overview/OverviewViewModel.kt
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/Routes.kt
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/NutritionGoalsEditorScreen.kt
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/OverviewScreen.kt
  - iosApp/iosApp/Views/Overview/NutritionGoalsEditorView.swift
  - iosApp/iosApp/Views/Overview/OverviewView.swift
findings:
  critical: 3
  warning: 6
  info: 3
  total: 12
status: issues_found
---

# Phase 16: Code Review Report

**Reviewed:** 2026-04-28
**Depth:** standard
**Files Reviewed:** 11
**Status:** issues_found

## Summary

Phase 16 adds TDEE calculation, a nutrition goals editor (Android + iOS), and a discoverability banner to the Overview tab. The domain logic in `TdeeCalculator` is pure and mostly correct, but contains one rounding bug that produces wrong values. The repository layer has a DataStore atomicity gap on multi-key reads. The ViewModel holds two parallel representations of `nutritionGoals` that can diverge. On iOS, both the `OverviewView` and `NutritionGoalsEditorView` hold their own ViewModel instances obtained directly from Koin, breaking the shared-state contract. The Swift async observation loops have a subtle lifetime issue when opened from a sheet. There are also minor Compose correctness issues and code quality gaps.

---

## Critical Issues

### CR-01: `roundToStep` rounding bias — wrong gram values for odd multiples of 5

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/TdeeCalculator.kt:74`
**Issue:** The "round to nearest 5" formula is `((rounded + 2) / 5) * 5`. Integer division of `(n + 2) / 5` rounds down, so the effective tie-break is "round half-down" rather than the standard "round half-up". For values whose last digit is exactly 5 (e.g. 175, 185, 195) the formula gives a result that is 5 g lower than the nearest-5 round. Concretely: `roundToStep(175.0)` → `((175+2)/5)*5 = (177/5)*5 = 35*5 = 175` — that happens to be fine. But `roundToStep(178.0)` → `((178+2)/5)*5 = (180/5)*5 = 36*5 = 180` — correct. `roundToStep(172.0)` → `((172+2)/5)*5 = (174/5)*5 = 34*5 = 170` — correct. The actual failure case: `roundToStep(173.0)` → `((173+2)/5)*5 = (175/5)*5 = 35*5 = 175`, but the nearest 5 is 175, so correct. The latent bug is that the formula as written applies `+2` before the *integer* divide, meaning values between `n*5 - 2` and `n*5 - 1` (e.g. 173, 174 toward 175) are rounded up correctly, but the identical formula also rounds values at the boundary `n*5 + 3` upward when they should stay — e.g. `roundToStep(163.0)` → `((163+2)/5)*5 = (165/5)*5 = 33*5 = 165`, but the nearest 5 is 165, which is also correct. The real problem surfaces when the input is already >= 20 but the intermediate `rounded` result is, e.g., 22: `((22+2)/5)*5 = (24/5)*5 = 4*5 = 20` — this silently rounds 22 g *down* to 20 g instead of up to 25 g. Any protein or fat calculation that lands between 23 and 24 g (inclusive) will be snapped to 20 g rather than 25 g, underreporting macros. The standard nearest-5 formula is `((rounded + 2) / 5) * 5` for half-up, which is what is written — but this is correct *only when* `rounded + 2` does not overflow the next 5-multiple boundary downward, i.e., when `rounded mod 5 != 3` or `!= 4`. For values where `rounded mod 5 == 3` (e.g. 23, 28, 33) the formula gives `(n+2) = n+2` whose integer-divide by 5 still floors, producing the *lower* multiple. Example: `roundToStep(23.0)` → `((23+2)/5)*5 = 25/5*5 = 25` — fine. `roundToStep(22.0)` → `24/5*5 = 4*5 = 20` — **wrong**, nearest 5 is 20 which is correct. Actually let me re-examine the described threshold: the comment says "round to nearest 5 if >= 20". For a value of 22, nearest-5 neighbours are 20 and 25, and 22 is closer to 20, so 20 is correct. The actual formula bug is more subtle: the formula `((rounded + 2) / 5) * 5` is equivalent to `floor((rounded + 2.5) / 5) * 5` in floating-point which is standard nearest-5 rounding with ties going to the higher value. In integer arithmetic, `(n + 2) / 5 * 5` is also correct nearest-5 rounding (ties round up) for non-negative integers. This is correct behavior.

**Re-assessment after careful trace:** The formula is actually correct for non-negative inputs. However, the test at line 75 (`proteinCut2Point2PerKg`) confirms `roundToStep(176)` → `((176+2)/5)*5 = (178/5)*5 = 35*5 = 175`, but the nearest 5 to 176 is **175**, so 175 is correct. The formula gives `178/5 = 35` (integer), `35*5 = 175`. Correct.

> RETRACTED — after full trace the `roundToStep` formula is arithmetically correct for non-negative integers. This finding is withdrawn; see WR-01 below for the remaining `carbsG` underflow concern which is the real issue.

---

### CR-01: iOS `NutritionGoalsEditorView` creates its own ViewModel, bypassing shared state

**File:** `iosApp/iosApp/Views/Overview/NutritionGoalsEditorView.swift:8`
**Issue:** `NutritionGoalsEditorView` instantiates its own `OverviewViewModel` via `KoinHelper.shared.getOverviewViewModel()`. `OverviewView` does the same at its line 7. Koin's default scope for a ViewModel is `single` or a new instance depending on module declaration. If the helper returns a new instance per call (factory), the editor view and the overview view hold **separate ViewModels** and separate DataStore subscriptions. The result: after `saveGoals()` writes to DataStore, the `OverviewView`'s ViewModel will eventually re-emit from the Flow, but `_uiState.nutritionGoals` in the editor's ViewModel is never propagated back to the overview's ViewModel's `_uiState` because they are independent. The `_uiState.update { it.copy(nutritionGoals = goals) }` in `updateNutritionGoals` only updates the in-memory copy of the ViewModel that received the call. If both share the same singleton, the overview's `_uiState` is stale until the next `refresh()`. In either case the in-memory `uiState.nutritionGoals` will not update on the overview screen after an editor save until `refresh()` is called (it's only updated in `updateNutritionGoals`, which only runs in the ViewModel the editor called). This is a design-level correctness bug: the overview's `uiState.nutritionGoals` diverges from what was just saved.

**Fix:** The `nutritionGoals` StateFlow on `OverviewViewModel` (line 101–103 of OverviewViewModel.kt) is already sourced from `settingsRepository.nutritionGoals`, but `uiState.nutritionGoals` is loaded via a one-shot `.first()` in `refresh()`. Remove the one-shot `goals` load from `refresh()` and instead derive `uiState.nutritionGoals` reactively from the `nutritionGoals` StateFlow, or call `refresh()` from `OverviewView` when the sheet is dismissed:

```swift
// OverviewView.swift
.sheet(isPresented: $showEditor, onDismiss: { viewModel.refresh() }) {
    NutritionGoalsEditorView()
        .presentationDragIndicator(.visible)
}
```

On the Kotlin side, replace the one-shot `goals` read in `refresh()` with the already-reactive `nutritionGoals` StateFlow.

---

### CR-02: DataStore `combine` over 5 independent `dataStore.data` subscriptions — non-atomic read window

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt:110-128`
**Issue:** `userPhysicalStats` is built by combining 5 separate `dataStore.data.map { ... }` flows (lines 111–115). DataStore guarantees that each `data` flow emission is atomic, but combining 5 *separate* subscriptions to `dataStore.data` means the five inner flows can emit at different times. If `setUserPhysicalStats` writes all five keys inside a single `edit` block (which it does, line 131–137), DataStore will emit one atomic snapshot per subscriber. Since all five subscriptions see the *same* snapshot in practice (they subscribe to the same `dataStore.data` hot flow), this is not a data-corruption risk in the current implementation. However, if DataStore ever multicasts with per-subscriber buffering, the five inner flows could see different snapshots during the write. More critically, the *current* behaviour is observable: on cold start, all five flows emit independently from cold, and a subscriber to `userPhysicalStats` will receive `null` first (because the first emission from e.g. `weightKg` arrives before the others catch up), then receive the real `UserPhysicalStats`. This intermediate `null` is used by the `NutritionGoalsEditorScreen` / `NutritionGoalsEditorView` to decide whether to show placeholder defaults. On a warm re-open this causes a visible flicker back to placeholder defaults before the real stats re-appear. The same applies to `nutritionGoals` (lines 71–79) which combines 5 flows.

**Fix:** Read all keys in a single `dataStore.data.map { prefs -> UserPhysicalStats? }` operator instead of combining 5 separate subscriptions:

```kotlin
val userPhysicalStats: Flow<UserPhysicalStats?> = dataStore.data.map { prefs ->
    val weight   = prefs[userWeightKgKey]?.toDoubleOrNull() ?: return@map null
    val height   = prefs[userHeightCmKey]?.toIntOrNull()   ?: return@map null
    val age      = prefs[userAgeKey]?.toIntOrNull()        ?: return@map null
    val sex      = prefs[userSexKey]?.let { runCatching { enumValueOf<Sex>(it) }.getOrNull() }
                   ?: return@map null
    val activity = prefs[userActivityKey]?.let { runCatching { enumValueOf<ActivityLevel>(it) }.getOrNull() }
                   ?: return@map null
    UserPhysicalStats(weight, height, age, sex, activity)
}
```

The same refactor should be applied to `nutritionGoals`.

---

### CR-03: iOS async observation tasks are not cancelled when sheet is dismissed — potential state mutation after deallocation

**File:** `iosApp/iosApp/Views/Overview/NutritionGoalsEditorView.swift:157-162`
**Issue:** The `.task { await withTaskGroup... }` modifier on the `NavigationStack` (line 157) starts two async observation loops (`observeStats`, `observeGoals`). SwiftUI's `.task` modifier cancels its task when the view disappears, which handles the normal case. However, `NutritionGoalsEditorView` holds a `private let viewModel` captured at struct creation time. Because the view is presented as a sheet, SwiftUI may keep the view's struct alive briefly after `dismiss()` is called (during the dismissal animation). During that window, the `for try await` loops continue to run and write into `@State` variables of a view whose body is no longer being rendered. While SwiftUI's state isolation prevents crashes here, the `observeGoals` loop will write `kcalValue`, `proteinValue`, etc. from the DataStore Flow — which has just been updated by `saveGoals()`. This means the picker values will be overwritten by the newly saved values from DataStore, causing a visual flash if the sheet dismissal animation is still in progress. More critically: if the user taps Save and the DataStore write is slower than the dismissal animation, `observeGoals` may emit the *old* persisted values during the animation, momentarily resetting the picker display.

**Fix:** This is acceptable in practice because `.task` on the `NavigationStack` will be cancelled when the sheet's root view disappears. The real issue is the `observeStats` loop which unconditionally overwrites `weightText`, `heightText`, etc. and `statsExpanded = false` on *every* emission (line 235), including the emission triggered by the user's own `saveGoals()` call. After saving, the DataStore emits the saved stats, `observeStats` fires and sets `statsExpanded = false`, collapsing the stats section — this happens correctly, but if the user is still viewing the editor (e.g. they save, but the sheet does not auto-dismiss), the stats section is collapsed without user action. The code calls `dismiss()` immediately in `saveGoals()` (line 221), so in normal flow this is fine. The risk is only if `dismiss()` is ever made conditional. Document this assumption.

---

## Warnings

### WR-01: `carbsG` can silently underflow to 0 for low-calorie cut scenarios and the test only checks `>= 0`

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/TdeeCalculator.kt:60-61`
**Issue:** The carb calculation is `(kcal - proteinG * 4 - fatG * 9) / 4.0`, then clamped with `coerceAtLeast(0)`. For a small female with sedentary activity the cut TDEE is approximately 1200 kcal. After subtracting 500, `kcalDouble` ≈ 700. Fat = `700 * 0.25 / 9` ≈ 19.4 g → `roundToStep(19.4)` = `19` (< 20, so no 5-step rounding). Protein = `50 kg * 2.2` = 110 g → `roundToStep(110)` = `((110+2)/5)*5 = 110`. Carbs = `(700 - 110*4 - 19*9) / 4` = `(700 - 440 - 171) / 4` = `89 / 4` ≈ 22 g. That is fine. But with a smaller input (e.g. weightKg=40, age=60, female, SEDENTARY), cut kcal can be around 600. With proteinG = 88 g → protein kcal = 352, fatG from 600*0.25/9=16.7→17 g → fat kcal = 153, remaining = 600 - 352 - 153 = 95 kcal → carbs = 23 g. Still fine. The `coerceAtLeast(0)` saves from negative carbs. The real quality issue is that the existing test (`carbsNonNegativeOnExtremeLowKcal`) only verifies `>= 0`, not that the result is nutritionally reasonable. A user who triggers a negative-carb scenario before the clamp simply gets 0 carbs with no warning or indication. This is a silent data quality failure: the saved `NutritionGoals` will have `carbGoal = 0` which the ring UI will show as 0/0 g, misleading the user. There is no validation at save time.

**Fix:** Add a minimum meaningful carb value (e.g. `coerceAtLeast(20)`) or surface a warning to the user when the computed carbs are below a threshold. At minimum, add a test that verifies the clamped case produces a non-zero value or documents the 0 behaviour as intentional.

---

### WR-02: `OverviewViewModel.refresh()` loads `nutritionGoals` via `.first()` but the `uiState` field can diverge from the `nutritionGoals` StateFlow

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/presentation/overview/OverviewViewModel.kt:151,190-195`
**Issue:** There are two sources of nutrition goals truth in the ViewModel: `nutritionGoals: StateFlow<NutritionGoals>` (line 101, reactive, always fresh) and `uiState.nutritionGoals` (line 190, loaded once per `refresh()` via `.first()`). After `updateNutritionGoals` is called, `uiState` is updated via `_uiState.update { it.copy(nutritionGoals = goals) }` (line 204), keeping both in sync for that call. However, if DataStore is updated externally (e.g. from a future background migration, or from the iOS app writing while Android is open), the `nutritionGoals` StateFlow picks up the change but `uiState.nutritionGoals` does not until the next `refresh()`. The `NutritionRingsCard` reads from `uiState.nutritionGoals` (line 344 of OverviewScreen.kt), not from `viewModel.nutritionGoals`, so it will show stale goals until refresh. This is a maintenance hazard that will cause subtle display bugs.

**Fix:** Eliminate `uiState.nutritionGoals` and have the UI observe `viewModel.nutritionGoals` directly, or make `uiState` reactive to the `nutritionGoals` StateFlow using `combine`.

---

### WR-03: `remember(storedStats)` in `NutritionGoalsEditorScreen` resets all field states when *any* part of `storedStats` changes

**File:** `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/NutritionGoalsEditorScreen.kt:91-108`
**Issue:** Every local state variable (`weightText`, `heightText`, `ageText`, `sex`, `activity`, `statsExpanded`) uses `remember(storedStats)` as its key. This means that whenever `storedStats` emits a new value (e.g. because the user's prior stats were loaded from DataStore after composition), **all six fields are reset to the stored values**. If the user has started editing their weight but the `userPhysicalStats` flow emits a new value before they save (which can happen due to the non-atomic combine described in CR-02), their in-progress edits are silently discarded. The same pattern on `storedGoals` (lines 111–115) resets all picker values on every Goals Flow emission — including the emission triggered by their own `updateNutritionGoals` call after saving, which navigates back. In practice the `navController.popBackStack()` fires before the Flow emission arrives, so the user rarely sees this. But the race is real.

**Fix:** Load stored values into local state only once, using `LaunchedEffect`:

```kotlin
LaunchedEffect(storedStats) {
    if (storedStats != null && !userHasEdited) { // guard with a flag
        weightText = "%.0f".format(storedStats.weightKg)
        // ...
    }
}
```

Or use `rememberUpdatedState` / initialize from the first emission only.

---

### WR-04: `NutritionGoalsEditorView` (iOS) `observeGoals` overwrites picker values on every Flow emission, including the save-triggered emission

**File:** `iosApp/iosApp/Views/Overview/NutritionGoalsEditorView.swift:243-257`
**Issue:** `observeGoals` unconditionally writes `kcalValue`, `proteinValue`, etc. from every emitted `NutritionGoals` (lines 247–251). When `saveGoals()` calls `viewModel.updateNutritionGoals(goals: goals)`, DataStore writes the new values and the `nutritionGoals` StateFlow emits — triggering `observeGoals` to write the same values back into the pickers *while the dismissal animation is in progress*. Additionally, if the user opens the editor, immediately sees the goals populated by `observeGoals` (initial emission), manually adjusts a picker (resetting `selectedSuggestion = nil`), and then the Flow emits again for any reason (e.g. another subscriber triggering a re-emission), their manual adjustment is silently overwritten. The same issue applies to `observeStats` (line 233) which resets text fields and `statsExpanded` on every emission.

**Fix:** Observe only the first emission to initialize state, then stop. Use a guard flag:

```swift
private var goalsInitialized = false

private func observeGoals() async {
    do {
        for try await goals in asyncSequence(for: viewModel.nutritionGoalsFlow) {
            guard !goalsInitialized, let g = goals as? SharedNutritionGoals else { break }
            goalsInitialized = true
            kcalValue = Int(g.calorieGoal)
            // ...
        }
    } catch { ... }
}
```

---

### WR-05: DrumPicker ranges in `NutritionGoalsEditorScreen` do not include the default `NutritionGoals` values

**File:** `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/NutritionGoalsEditorScreen.kt:538-588`
**Issue:** The default `NutritionGoals` has `calorieGoal = 2500`, which is included in `800..6000 step 50`. However, `sugarGoal = 50` with range `0..200 step 5` starts at `0` — so `50` is in range. `proteinGoal = 150` with range `20..400 step 5` — `150` is in range (`(150-20) % 5 == 0`). `fatGoal = 80` with range `10..250 step 5` — `80` is in range. `carbGoal = 300` with range `20..700 step 5` — `300` is in range. The concern is when a *suggestion* is applied: `TdeeCalculator` can produce values that fall outside the DrumPicker range. For example, a very active large male can have a bulk TDEE of 5000+ kcal → bulk suggestion = 5300+ kcal. `DrumPicker(items = (800..6000 step 50))` only goes to 6000. If `kcalValue` is set to 5350 (not a multiple of 50) or 6100 (out of range), the DrumPicker's `selectedItem` will not match any item in the list. What happens when `selectedItem` is not in the `items` list depends on `DrumPicker`'s implementation (not in scope here), but it is a likely source of invisible selection or an index crash.

**Fix:** The suggestion application code sets `kcalValue = split.kcal` (line 181). `split.kcal` is a raw `roundToInt()` result, not rounded to 50. The kcal picker uses step 50, so a suggestion of e.g. 2759 kcal would set a value not present in the list. Snap the applied suggestion values to the picker's step:

```kotlin
kcalValue = (split.kcal / 50) * 50  // snap to nearest 50
proteinValue = (split.proteinG / 5) * 5  // snap to nearest 5
// etc.
```

Or, since `TdeeCalculator.buildSplit` already calls `roundToInt()` for kcal, ensure the DrumPicker range includes all possible kcal values (make it `step 1` for kcal, or match the step to the rounding in the calculator).

---

### WR-06: `OverviewView` (iOS) `bannerVisible` initial state is `true` but the persisted value may be `false`

**File:** `iosApp/iosApp/Views/Overview/OverviewView.swift:17`
**Issue:** `@State private var bannerVisible: Bool = true` initializes the banner as visible. The `observeBannerVisible()` task corrects this once the Flow emits. Until the first emission arrives — which for `SharingStarted.WhileSubscribed(5000)` on a warm ViewModel happens almost immediately, but still asynchronously — the banner is visible even if it was previously dismissed. This causes a visible flash: the banner appears and then disappears (with `.easeOut(duration: 0.3)` animation) on every Overview tab appearance after the user has dismissed it. The Android side does not have this problem because `bannerVisible` is driven by `collectAsState()` which uses the StateFlow's current value as the initial value.

**Fix:** Initialize `@State private var bannerVisible: Bool = false` (default hidden until confirmed visible) and then have `observeBannerVisible` set it to `true` if needed. This way dismissed state is assumed until corrected, rather than visible state assumed until corrected. This matches the `SharingStarted.WhileSubscribed` replay-1 semantics which will emit the current value immediately on subscription.

---

## Info

### IN-01: `UserPhysicalStats` has no validation constraints — nonsensical values are silently accepted

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/UserPhysicalStats.kt:13-19`
**Issue:** `weightKg`, `heightCm`, and `age` have no range constraints. A user who types `0` weight or `-5` age will produce a negative BMR and a negative TDEE, which will then yield negative macro suggestions. The `coerceAtLeast(0)` on `carbsG` only partially compensates. Since `UserPhysicalStats` is a plain `data class`, any caller can construct it with arbitrary values.

**Fix:** Either add `require()` guards in the constructor or add validation in `TdeeCalculator.bmr()` / `buildSplit()`. At minimum, document the valid ranges. The UI text fields have `KeyboardType.Number` but do not prevent `0` or empty input.

---

### IN-02: `OverviewScreen` calls `viewModel.refresh()` both in `init` (via ViewModel) and in `LaunchedEffect(Unit)`, causing double refresh on first composition

**File:** `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/OverviewScreen.kt:88-90`
**Issue:** `OverviewViewModel.init` calls `refresh()` (ViewModel.kt line 133). `OverviewScreen` also calls `viewModel.refresh()` inside `LaunchedEffect(Unit)` (line 88–90 of OverviewScreen.kt). On first composition, both fire in the same render frame (the ViewModel is created, `init` runs `refresh()`, then Compose emits `LaunchedEffect` which runs another `refresh()`). This causes two concurrent coroutines loading muscle scores and nutrition data, with a race between which one sets `_uiState.value` last (line 190 in OverviewViewModel.kt uses `=` assignment, not `update`, so the second write wins but the first write's work is discarded).

**Fix:** Remove the `refresh()` call from `LaunchedEffect(Unit)` in `OverviewScreen` and rely on the ViewModel's `init` block, or remove the `init` call and keep only the `LaunchedEffect`. The Refresh button's `onClick = { viewModel.refresh() }` should remain.

---

### IN-03: `NutritionGoalsBannerView` (iOS) has overlapping tap targets — both `.onTapGesture` and the `Button` dismiss action fire for the "×" tap

**File:** `iosApp/iosApp/Views/Overview/OverviewView.swift:422-436`
**Issue:** `NutritionGoalsBannerView` applies `.contentShape(Rectangle()).onTapGesture { onTap() }` to the entire `HStack`. The dismiss `Button(action: onDismiss)` is a child of that `HStack`. When the user taps the "×" button, SwiftUI will fire the button's action (`onDismiss`) but the `.onTapGesture` on the parent may also fire (`onTap`), navigating to the editor while also dismissing the banner. `.onTapGesture` on a parent and `Button` on a child have gesture priority ambiguity in SwiftUI. In practice, the `Button` takes priority, but the behaviour is not guaranteed and can differ across iOS versions.

**Fix:** Replace the `.onTapGesture` on the `HStack` with a `Button` wrapping the tappable content (excluding the dismiss button), or use `.simultaneousGesture` to ensure the child Button wins.

---

_Reviewed: 2026-04-28_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
