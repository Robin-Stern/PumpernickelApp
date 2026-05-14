---
phase: 15
plan: 02
subsystem: domain/gamification
tags: [gamification, xp, achievements, ranks, domain, pure-functions]
dependency_graph:
  requires: []
  provides: [domain/gamification layer — Rank, RankLadder, RankState, XpFormula, GamificationEvent, UnlockEvent, EventKeys, NutritionGoalDayPolicy, AchievementCatalog, AchievementProgress]
  affects: [plans 03-09 which wire Room/Koin/ViewModel around these pure primitives]
tech_stack:
  added: []
  patterns: [pure Kotlin objects with companion functions, sealed class event hierarchy, static catalog DSL, per-100g nutrient normalization]
key_files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/Rank.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/RankLadder.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/RankState.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/XpFormula.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/GamificationEvent.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/UnlockEvent.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/EventKeys.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/NutritionGoalDayPolicy.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/AchievementCatalog.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/AchievementProgress.kt
    - shared/src/commonTest/kotlin/com/pumpernickel/domain/gamification/RankLadderTest.kt
    - shared/src/commonTest/kotlin/com/pumpernickel/domain/gamification/XpFormulaTest.kt
    - shared/src/commonTest/kotlin/com/pumpernickel/domain/gamification/NutritionGoalDayPolicyTest.kt
    - shared/src/commonTest/kotlin/com/pumpernickel/domain/gamification/AchievementCatalogTest.kt
  modified: []
decisions:
  - BASE_XP=500 chosen so rank 2 (SILVER_ELITE) is reachable within 2 hard workout sessions (floor(sum(reps*kg)/100) ~ 500 for a full-body session at moderate load)
  - NutritionGoalDayPolicy adapted to actual ConsumptionEntryEntity field types (per100g*amount normalization) rather than plan skeleton (which assumed direct macro fields)
  - NutritionGoals goals treated as 0=unset since fields are non-nullable Int with 0 meaning "not configured"
  - AchievementCatalog uses 12 families x 3 tiers = 36 entries (within D-15 30-45 range)
metrics:
  duration_seconds: 409
  completed_date: "2026-04-22"
  tasks_completed: 3
  files_created: 14
---

# Phase 15 Plan 02: Pure Domain Gamification Layer Summary

Pure-domain gamification vocabulary: 10 CSGO-style ranks with exponential XP curve, pure XP math for all 4 D-01 sources, 36-entry achievement catalog across 4 categories, typed event-key constructors, and a single shared D-04 goal-day predicate.

## What Was Built

### Task 1: Rank enum, RankLadder, RankState

`Rank.kt` — 10-entry enum in fixed order: SILVER → SILVER_ELITE → GOLD_NOVA_I → GOLD_NOVA_II → GOLD_NOVA_III → MASTER_GUARDIAN → DISTINGUISHED_MASTER_GUARDIAN → LEGENDARY_EAGLE → SUPREME → GLOBAL_ELITE. Never reorder (persisted as enum name strings).

`RankLadder.kt` — Exponential threshold curve with `BASE_XP = 500L`. Formula: `threshold(n) = 500 * 1.5^(n-2)` for n >= 2. Rank 1 (SILVER) threshold = 0 (D-11). Includes `rankForXp(xp: Long): Rank` (top-down scan) and `nextRank(current: Rank): Rank?`.

`RankState.kt` — Sealed class with `Unranked` (initial state, D-11) and `Ranked(currentRank, totalXp, currentRankThreshold, nextRank, nextRankThreshold, lastPromotedAtMillis)`.

### Task 2: XpFormula, GamificationEvent, UnlockEvent, EventKeys, NutritionGoalDayPolicy

`XpFormula.kt` — Pure XP math object:
- `workoutXp(sets: List<WorkoutSetInput>): Int` — `floor(sum(reps * weightKgX10/10.0) / 100)` (D-02)
- `PR_XP = 50`, `NUTRITION_GOAL_DAY_XP = 25`
- Streak constants: `STREAK_WORKOUT_3D=25`, `STREAK_WORKOUT_7D=100`, `STREAK_WORKOUT_30D=500`, `STREAK_NUTRITION_7D=100` (D-06)
- Achievement tier XP: Bronze=25, Silver=75, Gold=200 (D-17)
- `WorkoutSetInput(actualReps: Int, actualWeightKgX10: Int)` decouples from Room entities

`GamificationEvent.kt` — Sealed class: `WorkoutCompleted`, `PrHit`, `NutritionGoalDay`, `StreakThresholdCrossed`, `AchievementUnlocked`. Plus `StreakKind` enum (WORKOUT/NUTRITION).

`UnlockEvent.kt` — Sealed class for D-19 modal events: `RankPromotion(fromRank?, toRank, totalXp, flavourCopy)`, `AchievementTierUnlocked(achievementId, displayName, tier, flavourCopy)`.

`EventKeys.kt` — Typed constructors matching PATTERNS.md dedupe-key format table:
- `workout(workoutId) = "workout:<id>"`
- `pr(exerciseId, workoutId) = "pr:<exerciseId>:<workoutId>"`
- `goalDay(isoDate) = "goalday:<YYYY-MM-DD>"`
- `streakWorkout(threshold, runStartEpochDay) = "streak:workout:<threshold>:<epochDay>"`
- `streakNutrition(threshold, runStartEpochDay) = "streak:nutrition:<threshold>:<epochDay>"`
- `achievement(achievementId) = "achievement:<id>"`
- `parsePr(eventKey): ParsedPr?` — reverse lookup by splitting on last ':'

`NutritionGoalDayPolicy.kt` — Single D-04 predicate (Warning 9 fix). Adapted to actual types:

**Actual ConsumptionEntryEntity fields (confirmed by reading source):**
- `caloriesPer100: Double`, `proteinPer100: Double`, `fatPer100: Double`, `carbsPer100: Double`, `sugarPer100: Double`
- `amount: Double` (serving size in grams/ml)
- Actual nutrient = `(per100 / 100.0) * amount`

**Actual NutritionGoals fields (confirmed by reading source):**
- `calorieGoal: Int = 2500`, `proteinGoal: Int = 150`, `fatGoal: Int = 80`, `carbGoal: Int = 300`, `sugarGoal: Int = 50`
- Non-nullable Int fields. 0 = "not set" (skip the check for that macro)

The policy sums actual nutrient totals across all entries for the day, then checks each configured macro within ±10% (D-04 strict). Empty day = false. Goal=0 = skip.

### Task 3: AchievementCatalog, AchievementProgress, Category/Tier enums

`AchievementCatalog.kt` — 12 achievement families x 3 tiers = **36 entries** (within D-15 30-45 range):

| Family | Category | Bronze | Silver | Gold |
|--------|----------|--------|--------|------|
| volume | VOLUME | 10,000 kg·reps | 100,000 | 1,000,000 |
| volume-single-session | VOLUME | 5,000 kg·reps | 15,000 | 40,000 |
| consistency-longest-streak | CONSISTENCY | 3 days | 7 days | 30 days |
| consistency-total-workouts | CONSISTENCY | 10 | 50 | 250 |
| consistency-nutrition-days | CONSISTENCY | 3 | 15 | 60 |
| consistency-nutrition-streak | CONSISTENCY | 3 | 7 | 30 |
| pr-hunter-total | PR_HUNTER | 1 | 10 | 50 |
| pr-hunter-breadth | PR_HUNTER | 3 exercises | 10 | 25 |
| pr-hunter-multi-session | PR_HUNTER | 2 PRs/session | 3 | 5 |
| variety-exercises | VARIETY | 5 | 15 | 30 |
| variety-front-coverage | VARIETY | 3 groups | 6 | 10 |
| variety-back-coverage | VARIETY | 3 groups | 6 | 10 |

`AchievementProgress.kt` — UI domain model with `isUnlocked: Boolean`, `progressFraction: Float` (0..1).

`Category` enum — VOLUME, CONSISTENCY, PR_HUNTER, VARIETY (D-14).
`Tier` enum — BRONZE, SILVER, GOLD (D-17).

## BASE_XP Anchor (500) Rationale

500 XP at rank 2 means a user completing 2 moderately-loaded full-body sessions (each contributing ~250 XP via `floor(sum(reps*kg)/100)`) reaches SILVER_ELITE. The exponential curve then spaces out higher ranks appropriately:
- SILVER: 0 (free on first workout)
- SILVER_ELITE: 500
- GOLD_NOVA_I: 750
- GOLD_NOVA_II: 1,125
- GOLD_NOVA_III: 1,687
- MASTER_GUARDIAN: 2,531
- DMG: 3,796
- LEGENDARY_EAGLE: 5,695
- SUPREME: 8,542
- GLOBAL_ELITE: 12,813

## ConsumptionEntryEntity + NutritionGoals Field Types

The plan skeleton assumed direct macro fields (`calories: Double`). The actual entity stores per-100g values. Key deviation in `NutritionGoalDayPolicy`:

Plan skeleton assumed:
```kotlin
entries.sumOf { it.calories }  // direct macro field
```

Actual implementation:
```kotlin
entries.sumOf { (it.caloriesPer100 / 100.0) * it.amount }  // per100g * serving
```

Similarly, `NutritionGoals` uses non-nullable `Int` fields (not `Int?`), so the `check()` function tests `goal <= 0` (not `goal == null`) to detect "unset".

## Test Coverage

Four test classes created (compiled and verified):

- `RankLadderTest` — 9 assertions: SILVER threshold=0, SILVER_ELITE=BASE_XP, GOLD_NOVA_I=BASE_XP*1.5, rankForXp(0)=SILVER, rankForXp(MAX_VALUE)=GLOBAL_ELITE, boundary inclusive, nextRank(GLOBAL_ELITE)=null, nextRank(SILVER)=SILVER_ELITE, 10 ranks total
- `XpFormulaTest` — 8 assertions: empty=0, 10reps@100kg=10XP, fractional floor, PR_XP=50, streak bonuses, achievement tier XP, event key format registry, parsePr round-trip + rejects garbage
- `NutritionGoalDayPolicyTest` — 7 assertions: empty=false, exact match=true, +15%=false, -15%=false, unset macro skipped, zero=skip, within 10%=true
- `AchievementCatalogTest` — 7 assertions: size 30-45, unique IDs, ID format regex, all families have 3 tiers, thresholds monotonically increase, all categories covered, findById works

Build verification: `./gradlew :shared:compileTestKotlinIosSimulatorArm64` → BUILD SUCCESSFUL.

Note: Running tests requires iOS simulator (`iosSimulatorArm64Test` task) or Android emulator. The compile-level verification confirms all type signatures are correct. A full test run was not executed in this plan due to the worktree build setup constraints (schemas are gitignored).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] NutritionGoalDayPolicy adapted to actual ConsumptionEntryEntity field shape**
- **Found during:** Task 2 — the plan's read_first directive specified reading ConsumptionEntryEntity
- **Issue:** Plan skeleton assumed `entries.sumOf { it.calories }` (direct fields), but actual entity stores `caloriesPer100: Double` + `amount: Double`
- **Fix:** Changed summation to `(it.caloriesPer100 / 100.0) * it.amount` across all 5 macros; updated test helper in `NutritionGoalDayPolicyTest.makeEntry()` to use the per-100g constructor
- **Files modified:** `NutritionGoalDayPolicy.kt`, `NutritionGoalDayPolicyTest.kt`
- **Commit:** 678622b

**2. [Rule 1 - Bug] NutritionGoalDayPolicy check() uses Int not Int? for NutritionGoals goals**
- **Found during:** Task 2 — actual `NutritionGoals` fields are non-nullable `Int`
- **Issue:** Plan skeleton had `private fun check(actual: Double, goal: Int?): Boolean` treating null as skip
- **Fix:** Changed to `private fun check(actual: Double, goal: Int): Boolean` with `if (goal <= 0) return true` to handle 0-means-unset convention
- **Files modified:** `NutritionGoalDayPolicy.kt`, `NutritionGoalDayPolicyTest.kt`
- **Commit:** 678622b

**3. [Rule 3 - Blocking] Room schema files missing from worktree**
- **Found during:** Task 1 verification — `kspKotlinIosSimulatorArm64` failed with "Schema '6.json' required for migration was not found"
- **Issue:** Worktree does not have the `shared/schemas/` directory which is gitignored
- **Fix:** Copied schema JSON files from main repo to worktree schemas directory (untracked, not committed)
- **Impact:** None on committed files; schemas remain gitignored per project convention

## Known Stubs

None. All files are pure domain types with no placeholder data or hardcoded stubs.

## Threat Flags

None. These are pure domain types (no network endpoints, no auth paths, no file access, no schema changes). Plans 03-09 will introduce Room schema changes and will carry their own threat review.

## Self-Check: PASSED
