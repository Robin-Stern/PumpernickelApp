---
phase: 16-set-nutrition-goals-kcal-protein-carbs-fat-per-day-surface-p
plan: 07
subsystem: domain-nutrition
tags: [tdee, kotlin, kmp, picker, drum-picker, swiftui-picker, snap-to-50, gap-closure]

# Dependency graph
requires:
  - phase: 16
    provides: "TdeeCalculator.buildSplit() returning raw kcalDouble.roundToInt() value (16-03)"
  - phase: 16
    provides: "Android DrumPicker (16-05) and iOS Picker(.wheel) (16-06) with kcal range (800..6000 step 50)"
provides:
  - "buildSplit() snaps kcal to nearest multiple of 50 (round-half-up) before macro derivation"
  - "kcal clamped to picker range (800..6000) so suggestion-applied values are always selectable"
  - "Macros (proteinG, fatG, carbsG) derived from snapped kcal — card display and picker stay consistent"
  - "Two new tests proving the picker-range invariant: cutSuggestionKcalIsMultipleOf50AndInPickerRange, allSuggestionKcalSnappedToFifty"
affects: [16-08, 16-09, future-nutrition-suggestion-UI-work]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Shared-layer snap-to-grid: shared calculator outputs values aligned to UI picker steps so platform UIs need no workaround"
    - "Test tolerance widening + invariant assertions: when an upstream change loosens an existing equality, widen tolerance and add a new tighter invariant test (multiple-of-50) that pins the new behaviour"

key-files:
  created: []
  modified:
    - "shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/TdeeCalculator.kt"
    - "shared/src/commonTest/kotlin/com/pumpernickel/domain/nutrition/TdeeCalculatorTest.kt"

key-decisions:
  - "Snap-to-50 lives in TdeeCalculator.buildSplit() (shared layer) so Android + iOS automatically receive picker-aligned kcal values — no platform-side change needed."
  - "Round-half-up via integer truncation: ((kcalDouble + 25.0).toInt() / 50) * 50, then coerceIn(800, 6000) for picker bound safety."
  - "Macros derived from the snapped kcal (not pre-snap raw kcal) to keep card display kcal and macro grams internally consistent."
  - "Three existing tests (cut/maintain/bulk kcal) widened from absoluteTolerance = 1.0 → 25.0 — kcal can now diverge from raw (tdee ± delta) by up to ±25 due to snap-to-50; bmr*KnownValue tests retain 0.01 tolerance because BMR is unchanged."

patterns-established:
  - "Shared-layer alignment to UI grid: when the same UI control on multiple platforms requires values from a fixed step grid, perform the snap once in shared code rather than twice in platform code."

requirements-completed: []  # plan frontmatter requirements: []

# Metrics
duration: 4min
completed: 2026-04-28
---

# Phase 16 Plan 07: Snap kcal to multiple of 50 in TdeeCalculator (gap-closure for picker range mismatch)

**Snap-to-50 in `TdeeCalculator.buildSplit()` so suggestion-applied kcal values are always present in the Android DrumPicker / iOS Picker(.wheel) ranges (800..6000 step 50), with macros now derived from the snapped kcal to keep card display and picker state consistent.**

## Performance

- **Duration:** ~4 min
- **Started:** 2026-04-28T14:56:17Z
- **Completed:** 2026-04-28T15:00:00Z (approx)
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Shared-layer snap-to-50 closes WR-05 / Gap 3 at the root cause: tapping a suggestion card now applies a picker-aligned kcal, so DrumPicker (Android) and Picker(.wheel) (iOS) visibly scroll to the new value on both platforms with no platform-specific change.
- Macros stay consistent with displayed kcal: `fatRaw = kcal * 0.25 / 9.0` and `carbsRaw = (kcal - proteinG*4 - fatG*9) / 4.0` now consume the snapped `kcal`, so the card "2250 kcal · 175 g protein · …" matches what the picker shows after tap.
- Two new picker-range invariant tests pin the new behaviour; all 14 tests in `TdeeCalculatorTest` pass on `:shared:iosSimulatorArm64Test`.

## Task Commits

Each task was committed atomically:

1. **Task 1: Snap kcal to multiple of 50 in TdeeCalculator.buildSplit()** — `9c99758` (feat)
2. **Task 2: Add kcal-snap tests + widen tolerance on three existing TDEE-based kcal tests** — `1e80853` (test)

## Files Created/Modified

- `shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/TdeeCalculator.kt` — `buildSplit()` snaps `kcalDouble` to the nearest multiple of 50 via `((kcalDouble + 25.0).toInt() / 50) * 50` and clamps to `(800..6000)`. The local `kcal` variable now holds the snapped integer; downstream macro derivation (`fatRaw`, `carbsRaw`) and the returned `MacroSplit(kcal = kcal, ...)` automatically reflect the snap with no further changes.
- `shared/src/commonTest/kotlin/com/pumpernickel/domain/nutrition/TdeeCalculatorTest.kt` — two new tests: `cutSuggestionKcalIsMultipleOf50AndInPickerRange` (asserts `cut.kcal % 50 == 0`, `cut.kcal in 800..6000`, and `cut.kcal == 2250` for the reference 80kg/180cm/30/male/moderately-active stats) and `allSuggestionKcalSnappedToFifty` (asserts the same invariant for cut/maintain/bulk). `cutSuggestionIsTdeeMinus500`, `maintainSuggestionEqualsTdee`, and `bulkSuggestionIsTdeePlus300` widened from `absoluteTolerance = 1.0` to `25.0` with a justifying comment.

## Diff Detail

### TdeeCalculator.buildSplit() — line 54 replacement

Before (single line):
```kotlin
val kcal = kcalDouble.roundToInt()
```

After (7 lines including comment):
```kotlin
// Snap to nearest multiple of 50 (round-half-up) and clamp to picker range
// (800..6000 step 50) so the value is always present on both platforms.
// Per WR-05: Android DrumPicker and iOS Picker(.wheel) ranges step by 50;
// a non-matching selection silently fails to scroll. Snapping here keeps the
// shared calculator authoritative — no platform-side workaround needed.
val kcalSnapped = ((kcalDouble + 25.0).toInt() / 50) * 50
val kcal = kcalSnapped.coerceIn(800, 6000)
```

`bmr()`, `tdee()`, `suggestions()`, and `roundToStep()` unchanged. The `kotlin.math.roundToInt` import is still used by `roundToStep` (line 80).

### Reference numerical impact (80kg / 180cm / 30 / male / MODERATELY_ACTIVE)

| Suggestion | Pre-fix kcal (raw) | Post-fix kcal (snap-to-50) | Picker hit? |
|------------|--------------------|-----------------------------|--------------|
| cut        | 2259               | **2250**                    | yes          |
| maintain   | 2759               | **2750**                    | yes          |
| bulk       | 3059               | **3050**                    | yes          |

### Two new tests

```kotlin
@Test fun cutSuggestionKcalIsMultipleOf50AndInPickerRange() {
    // WR-05 / Gap 3: kcal must be a multiple of 50 so DrumPicker (Android) and
    // Picker(.wheel) (iOS) ranges (800..6000 step 50) include the value.
    val s = TdeeCalculator.suggestions(stats())  // 80kg/180cm/30/male/moderately
    // Reference cut TDEE = 2759 - 500 = 2259, raw roundToInt = 2259.
    // Round-half-up snap to nearest 50 → 2250.
    assertEquals(0, s.cut.kcal % 50, "cut kcal must be a multiple of 50; got ${s.cut.kcal}")
    assertTrue(s.cut.kcal in 800..6000, "cut kcal must be in picker range; got ${s.cut.kcal}")
    assertEquals(2250, s.cut.kcal, "expected snap of 2259 → 2250")
}

@Test fun allSuggestionKcalSnappedToFifty() {
    val s = TdeeCalculator.suggestions(stats())
    // All three suggestions must satisfy the picker-range invariant.
    assertEquals(0, s.cut.kcal % 50,      "cut kcal not snapped: ${s.cut.kcal}")
    assertEquals(0, s.maintain.kcal % 50, "maintain kcal not snapped: ${s.maintain.kcal}")
    assertEquals(0, s.bulk.kcal % 50,     "bulk kcal not snapped: ${s.bulk.kcal}")
    // And all must lie within the picker bounds (800..6000).
    assertTrue(s.cut.kcal in 800..6000)
    assertTrue(s.maintain.kcal in 800..6000)
    assertTrue(s.bulk.kcal in 800..6000)
}
```

### Three tolerance widenings (1.0 → 25.0)

`cutSuggestionIsTdeeMinus500`, `maintainSuggestionEqualsTdee`, `bulkSuggestionIsTdeePlus300` — each now uses `absoluteTolerance = 25.0` with a leading comment:

```kotlin
// Tolerance widened to 25.0 because TdeeCalculator now snaps kcal to nearest 50 (WR-05 fix).
```

The bmr-equality tests (`bmrMaleKnownValue`, `bmrFemaleKnownValue`) keep `absoluteTolerance = 0.01` because `bmr()` is unchanged.

## Platform Impact (no change required)

Per the plan output spec: no Android (`composeApp/.../NutritionGoalsEditorScreen.kt` line 181 `(800..6000 step 50)`) or iOS (`iosApp/.../NutritionGoalsEditorView.swift` line 197 `stride(from: 800, through: 6000, by: 50)`) change was needed. The shared `TdeeCalculator.suggestions()` now returns kcal values that are always inside both picker ranges, so the existing `LaunchedEffect(selectedItem) { items.indexOf(...) }` (Android) and `Picker(selection: ...) { ForEach(...) }` (iOS) bindings find a matching tag and scroll correctly.

## Decisions Made

- **Snap in shared layer, not platform layers** — single source of truth, no risk of platforms drifting (e.g., Android snapping to 50 but iOS forgetting to). Aligns with the plan's "shared root cause" framing.
- **Round-half-up via `((x + 25.0).toInt() / 50) * 50`** — produces 2250 from 2259 (closer to 2250 than 2300), 2275 → 2300, 2274 → 2250. Standard banker-friendly half-up rule via integer truncation.
- **`coerceIn(800, 6000)` after snap** — defensive: picker bounds are tight (800 / 6000 are multiples of 50 already), so snap+clamp is idempotent on legal inputs but safe against future stat extremes (e.g., very low BMR + 500 deficit could push below 800).
- **Tolerance 25.0 not 50.0** — round-half-up to nearest 50 means max divergence from `(tdee ± delta).toInt()` is `25` (e.g., 2274 snaps down to 2250, distance = 24; 2275 snaps up to 2300, distance = 25). Using exactly 25.0 is the tightest correct tolerance.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Restored gitignored Room schema build artifacts so test runner could compile**
- **Found during:** Task 2 verification (running `:shared:iosSimulatorArm64Test`)
- **Issue:** `shared/schemas/com.pumpernickel.data.db.AppDatabase/` is gitignored (build artifact regenerated by KSP). The fresh worktree shipped only `8.json`; KSP processing `AppDatabase` failed with "Schema '6.json' / '7.json' required for migration was not found at the schema out folder", blocking all `:shared` test tasks. This is unrelated to TdeeCalculator changes — pre-existing environmental gap caused by gitignored schemas not being regenerated yet on this worktree.
- **Fix:** Copied `6.json` and `7.json` from the main checkout's `shared/schemas/com.pumpernickel.data.db.AppDatabase/` directory into this worktree's same path. These are gitignored generated artifacts, so the copy does not affect git status.
- **Files modified:** `shared/schemas/com.pumpernickel.data.db.AppDatabase/6.json` (copied), `shared/schemas/com.pumpernickel.data.db.AppDatabase/7.json` (copied) — both gitignored, not committed.
- **Verification:** `:shared:iosSimulatorArm64Test --tests "...TdeeCalculatorTest"` exited 0 with `BUILD SUCCESSFUL`; test report shows 14/14 passing.
- **Committed in:** N/A (gitignored artifacts; not part of any commit)

**2. [Rule 3 - Blocking] Substituted `:shared:iosSimulatorArm64Test` for the plan's `:shared:commonTest` task**
- **Found during:** Task 2 verification
- **Issue:** The plan asks to run `./gradlew :shared:commonTest --rerun-tasks`. That task does not exist in this project — `:shared` exposes per-target tests (`testDebugUnitTest`, `iosSimulatorArm64Test`, `iosX64Test`, `iosArm64Test`, etc.) but no aggregated `commonTest` target. This is a plan-authoring assumption that does not match the current Gradle setup.
- **Fix:** Ran `./gradlew :shared:iosSimulatorArm64Test --tests "com.pumpernickel.domain.nutrition.TdeeCalculatorTest"`. iOS simulator native tests execute the same `commonMain`+`commonTest` Kotlin sources that any `commonTest` task would, and platform-independent code (`TdeeCalculator`, no expect/actual) yields identical behaviour across targets.
- **Files modified:** none (verification-only command change)
- **Verification:** `BUILD SUCCESSFUL`, `TEST-com.pumpernickel.domain.nutrition.TdeeCalculatorTest.xml` reports `tests="14" failures="0" errors="0"`.
- **Committed in:** N/A

---

**Total deviations:** 2 auto-fixed (both Rule 3 - Blocking, both unrelated to plan scope)
**Impact on plan:** Neither deviation altered plan scope, code, or tests. Both were necessary to verify the planned implementation works. Plan was executed exactly as specified.

## Issues Encountered

None beyond the two Rule 3 deviations above. The TDD flow was straightforward: implementation in Task 1 was a 7-line replacement, test additions in Task 2 mirrored the plan's `<action>` block verbatim, and the iOS simulator native test target ran in 17 seconds.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- **Plan 16-08 (init-guard)** can proceed in Wave 2 — it covers the picker-init lifecycle, which is now decoupled from the kcal-snap concern.
- **Plan 16-09 (iOS rings refresh)** can proceed in Wave 2 — it depends on Overview-tab macro-ring rendering, also independent of this snap fix.
- **Future suggestion-card work** (any new variant: re-comp, fat split, vegan macros, etc.) inherits the snap-to-picker-grid invariant for free as long as the new builder reuses or wraps `buildSplit()`.

## Test Output

```
> Task :shared:iosSimulatorArm64Test

BUILD SUCCESSFUL in 17s
9 actionable tasks: 7 executed, 2 up-to-date

TEST-com.pumpernickel.domain.nutrition.TdeeCalculatorTest.xml:
  tests="14" skipped="0" failures="0" errors="0" time="0.003"
```

All 14 tests pass:

| # | Test                                                | Status |
|---|-----------------------------------------------------|--------|
| 1 | bmrMaleKnownValue                                   | ✓      |
| 2 | bmrFemaleKnownValue                                 | ✓      |
| 3 | tdeeAppliesMultiplier                               | ✓      |
| 4 | tdeeMultipliersForEachTier                          | ✓      |
| 5 | cutSuggestionIsTdeeMinus500 (tolerance 25.0)        | ✓      |
| 6 | maintainSuggestionEqualsTdee (tolerance 25.0)       | ✓      |
| 7 | bulkSuggestionIsTdeePlus300 (tolerance 25.0)        | ✓      |
| 8 | cutSuggestionKcalIsMultipleOf50AndInPickerRange ★   | ✓      |
| 9 | allSuggestionKcalSnappedToFifty ★                   | ✓      |
| 10| proteinCut2Point2PerKg                              | ✓      |
| 11| proteinMaintain2PerKg                               | ✓      |
| 12| proteinBulk1Point8PerKg                             | ✓      |
| 13| sugarAlwaysFifty                                    | ✓      |
| 14| carbsNonNegativeOnExtremeLowKcal                    | ✓      |

★ = new in this plan.

## Self-Check: PASSED

- File `shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/TdeeCalculator.kt` — FOUND
- File `shared/src/commonTest/kotlin/com/pumpernickel/domain/nutrition/TdeeCalculatorTest.kt` — FOUND
- File `.planning/phases/16-set-nutrition-goals-kcal-protein-carbs-fat-per-day-surface-p/16-07-SUMMARY.md` — FOUND
- Commit `9c99758` (Task 1: feat — snap kcal) — FOUND
- Commit `1e80853` (Task 2: test — kcal-snap tests + tolerance widening) — FOUND

---
*Phase: 16-set-nutrition-goals-kcal-protein-carbs-fat-per-day-surface-p*
*Plan: 07*
*Completed: 2026-04-28*
