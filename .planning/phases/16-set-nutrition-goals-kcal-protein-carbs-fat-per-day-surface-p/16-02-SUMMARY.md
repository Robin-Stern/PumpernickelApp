---
phase: 16-set-nutrition-goals-kcal-protein-carbs-fat-per-day-surface-p
plan: "02"
subsystem: nutrition
tags: [kotlin, tdee, bmr, mifflin-st-jeor, pure-functions, unit-tests, kotlin-test]

# Dependency graph
requires:
  - phase: 16-01
    provides: "UserPhysicalStats data class + Sex + ActivityLevel enums in domain/model"

provides:
  - "TdeeCalculator object: pure bmr(), tdee(), suggestions() functions"
  - "TdeeSuggestions data class: cut / maintain / bulk MacroSplit"
  - "MacroSplit data class: kcal, proteinG, carbsG, fatG, sugarG"
  - "TdeeCalculatorTest: 12 unit tests covering BMR, TDEE, activity tiers, macro split, rounding"

affects:
  - "16-04: NutritionGoalsEditorViewModel will call TdeeCalculator.suggestions(stats)"
  - "16-05: Android NutritionGoalsEditorScreen uses TdeeSuggestions to pre-fill drum pickers"
  - "16-06: iOS NutritionGoalsEditorView uses TdeeSuggestions to pre-fill Picker(.wheel)"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Pure-function singleton object in domain/nutrition (mirrors XpFormula pattern)"
    - "roundToStep: nearest 5g if >=20g, nearest 1g otherwise for drum picker alignment"
    - "TDD RED/GREEN in worktree: minimal failing test committed, then implementation, then full test suite"

key-files:
  created:
    - "shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/TdeeCalculator.kt"
    - "shared/src/commonTest/kotlin/com/pumpernickel/domain/nutrition/TdeeCalculatorTest.kt"
  modified: []

key-decisions:
  - "BMR: Mifflin-St Jeor: MALE sexConstant=+5, FEMALE sexConstant=-161 (D-16-04)"
  - "TDEE = BMR × ACTIVITY_MULTIPLIER map (1.2/1.375/1.55/1.725/1.9) (D-16-05)"
  - "Deltas: cut=-500, maintain=0, bulk=+300 kcal (D-16-06)"
  - "Macro split: protein 2.2/2.0/1.8 g/kg; fat 25% kcal/9; carbs remainder; sugarG always 50 (D-16-07)"
  - "roundToStep rounds to nearest 5g if >=20g, nearest 1g otherwise per UI-SPEC drum picker alignment"
  - "carbsG coerceAtLeast(0) prevents negative carbs on extreme low-kcal cut scenarios"

patterns-established:
  - "Pure-function Kotlin object (no DB, no Koin, no coroutines) in domain/nutrition/ package"
  - "Helper data classes (TdeeSuggestions, MacroSplit) co-located in same file as calculator"
  - "kotlin.test @Test with absoluteTolerance for floating-point BMR/TDEE assertions"
  - "Private stats() helper in tests with named defaults for compact test bodies"

requirements-completed: []

# Metrics
duration: 3min
completed: "2026-04-28"
---

# Phase 16 Plan 02: TdeeCalculator Summary

**Mifflin-St Jeor TDEE calculator with Cut/Maintain/Bulk macro suggestions, pure Kotlin object locked by 12 unit tests covering all BMR/TDEE/macro-split decisions (D-16-04 through D-16-07)**

## Performance

- **Duration:** 3 min
- **Started:** 2026-04-28T08:31:44Z
- **Completed:** 2026-04-28T08:34:52Z
- **Tasks:** 2 (TDD: 1 RED + 2 GREEN commits)
- **Files modified:** 2

## Accomplishments

- `TdeeCalculator` object with `bmr()`, `tdee()`, `suggestions()` pure functions
- `TdeeSuggestions` + `MacroSplit` data classes co-located in `domain/nutrition/`
- 12 unit tests passing on iOS Simulator arm64 covering all math decisions from D-16-04 to D-16-07
- Purity gate confirmed: no Room, Koin, or coroutine imports

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: Minimal failing TdeeCalculator test** - `db0041f` (test)
2. **Task 1 GREEN: TdeeCalculator implementation** - `fe39efa` (feat)
3. **Task 2 GREEN: Full 12-test TdeeCalculatorTest** - `b8995c1` (feat)

_TDD tasks: RED commit (failing), GREEN commit (implementation), second GREEN (full test expansion)_

## Files Created/Modified

- `shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/TdeeCalculator.kt` — Pure TDEE calculator object with BMR/TDEE/suggestions functions, TdeeSuggestions + MacroSplit data classes
- `shared/src/commonTest/kotlin/com/pumpernickel/domain/nutrition/TdeeCalculatorTest.kt` — 12 unit tests: bmrMale, bmrFemale, tdeeMultiplier, all 5 activity tiers, cut/maintain/bulk kcal, protein cut/maintain/bulk, sugarAlways50, carbsNonNegative

## Public API Surface

```kotlin
// object TdeeCalculator (pure functions, no side effects)
fun bmr(stats: UserPhysicalStats): Double       // Mifflin-St Jeor BMR
fun tdee(stats: UserPhysicalStats): Double      // BMR × activity multiplier
fun suggestions(stats: UserPhysicalStats): TdeeSuggestions  // Cut/Maintain/Bulk

// data class TdeeSuggestions
data class TdeeSuggestions(cut: MacroSplit, maintain: MacroSplit, bulk: MacroSplit)

// data class MacroSplit
data class MacroSplit(kcal: Int, proteinG: Int, carbsG: Int, fatG: Int, sugarG: Int = 50)
```

## Activity Multiplier Table (D-16-05)

| ActivityLevel | Multiplier |
|---|---|
| SEDENTARY | 1.2 |
| LIGHTLY_ACTIVE | 1.375 |
| MODERATELY_ACTIVE | 1.55 |
| VERY_ACTIVE | 1.725 |
| EXTRA_ACTIVE | 1.9 |

## Decisions Made

- Followed plan exactly: all formulas, multipliers, deltas, and macro ratios as specified in D-16-04 through D-16-07
- `roundToStep` private helper applies drum-picker alignment per UI-SPEC (nearest 5g if >=20g, nearest 1g otherwise)
- `carbsG.coerceAtLeast(0)` added per plan to prevent negative carbs on extreme cut scenarios (e.g., 50kg female sedentary)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. Compilation, test execution, and all 12 acceptance criteria checks passed on first attempt.

## Known Stubs

None. `TdeeCalculator` is a pure math layer with no stubs, no hardcoded UI values, and no placeholder data.

## Threat Flags

None. `TdeeCalculator.kt` is a pure-function object with no network endpoints, no auth paths, no file access, and no DB schema.

## TDD Gate Compliance

- RED gate: `db0041f` - `test(16-02): add failing test for TdeeCalculator bmr` (unresolved reference confirmed)
- GREEN gate: `fe39efa` - `feat(16-02): implement TdeeCalculator with TdeeSuggestions and MacroSplit`
- GREEN gate 2: `b8995c1` - `feat(16-02): expand TdeeCalculatorTest with full 12-test suite`

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `TdeeCalculator.suggestions(stats)` is ready for Plan 16-04 (NutritionGoalsEditorViewModel) to call reactively
- `TdeeSuggestions` + `MacroSplit` types are ready for Plans 16-05 (Android) and 16-06 (iOS) to consume for drum picker pre-fill
- Phase 15 engine files untouched: NutritionGoalDayPolicy, GoalDayTrigger, GamificationEngine, XpFormula all unmodified

---
*Phase: 16-set-nutrition-goals-kcal-protein-carbs-fat-per-day-surface-p*
*Completed: 2026-04-28*
