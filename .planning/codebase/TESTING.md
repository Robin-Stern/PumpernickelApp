# Testing Patterns

**Analysis Date:** 2026-05-18

## Test Framework

**Runner:**
- `kotlin.test` (the multiplatform stdlib testing API). No test runner is declared as a Gradle dependency in `shared/build.gradle.kts`; Kotlin Multiplatform auto-wires `kotlin.test` per target (JUnit 4 under the JVM/Android, the bundled native runner under iOS).
- Config: none. There is no `commonTest.dependencies { … }` block in `shared/build.gradle.kts` — tests rely entirely on the implicit `kotlin-test` dependency the KMP plugin adds.

**Assertion Library:**
- `kotlin.test` assertions only: `assertEquals`, `assertNotNull`, `assertNull`, `assertTrue`. Imported per-test-file:
  ```kotlin
  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertNull
  ```
- **Kotest is NOT in use** (not in `gradle/libs.versions.toml`, no dependency, no `FunSpec` / `StringSpec` / `shouldBe` usage anywhere in the repo).
- **Turbine is NOT in use** (not in `gradle/libs.versions.toml`, no `test { … }` / `turbineScope` usage anywhere). Despite the `CLAUDE.md` stack guide recommending Turbine, no Flow/StateFlow tests exist yet.
- **MockK / Mockito are NOT in use** (no mocks anywhere). Tests are pure-function tests against value objects.

**Run Commands:**
```bash
./gradlew :shared:allTests             # Run all KMP common tests (Android + iOS targets)
./gradlew :shared:testDebugUnitTest    # Android JVM unit tests of shared/
./gradlew :shared:iosX64Test           # iOS simulator unit tests of shared/
./gradlew check                        # Run all checks (delegates to allTests)
```

## Test File Organization

**Location:**
- Tests live only in `shared/src/commonTest/kotlin/`. They run against every KMP target (Android JVM, iOS Arm64/SimulatorArm64/X64) without modification.
- **No platform-specific test source sets exist** (`androidUnitTest`, `iosTest`, `androidInstrumentedTest` are absent). Search for `*Test*.kt` under `androidApp/src` and `iosApp/` returns zero files.
- **No UI/Compose tests** (no `androidx.compose.ui.test`, no Paparazzi/Roborazzi).
- **No integration tests** (no in-memory Room test using `Room.inMemoryDatabaseBuilder`, no Ktor `MockEngine`).

**Naming:**
- One test class per production class. Class name = production class name + `Test` (`XpFormulaTest`, `StreakCalculatorTest`, `AchievementCatalogTest`, `AchievementRulesTest`, `RankLadderTest`, `NutritionGoalDayPolicyTest`, `TdeeCalculatorTest`).
- Test functions use camelCase descriptive names (no backticks-with-spaces): `emptySetsYieldZeroXp`, `singleSetTenRepsOneHundredKgYieldsTenXp`, `thresholdsCrossedFromScratchToSeven`, `bmrFemaleKnownValue`.
- `@Test fun … { … }` collapsed onto a single visual line is the house style (see `XpFormulaTest.kt:8`).

**Structure:**
```
shared/src/commonTest/kotlin/com/pumpernickel/
└── domain/
    ├── gamification/
    │   ├── AchievementCatalogTest.kt        (60 lines)
    │   ├── AchievementRulesTest.kt          (85 lines)
    │   ├── NutritionGoalDayPolicyTest.kt    (99 lines)
    │   ├── RankLadderTest.kt                (43 lines)
    │   ├── StreakCalculatorTest.kt          (65 lines)
    │   └── XpFormulaTest.kt                 (60 lines)
    └── nutrition/
        └── TdeeCalculatorTest.kt            (130 lines)
```
Test package mirrors the production package exactly (`com.pumpernickel.domain.gamification` for tests of `domain/gamification/*.kt`).

## Test Structure

**Suite Organization:**
```kotlin
package com.pumpernickel.domain.gamification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class XpFormulaTest {
    @Test fun emptySetsYieldZeroXp() {
        assertEquals(0, XpFormula.workoutXp(emptyList()))
    }

    @Test fun singleSetTenRepsOneHundredKgYieldsTenXp() {
        // 10 reps x 100.0 kg = 1000 volume -> /100 = 10 XP
        val sets = listOf(WorkoutSetInput(actualReps = 10, actualWeightKgX10 = 1000))
        assertEquals(10, XpFormula.workoutXp(sets))
    }
}
```
Source: `shared/src/commonTest/kotlin/com/pumpernickel/domain/gamification/XpFormulaTest.kt`.

**Patterns:**
- **No `@BeforeTest` / `@AfterTest` hooks.** All fixtures inline.
- **Test data is constructed inline** at the start of each `@Test` (e.g., `val sets = listOf(WorkoutSetInput(actualReps = 10, actualWeightKgX10 = 1000))`).
- **Private helper factories** with named-default parameters for repeated builds. See `TdeeCalculatorTest.kt:12-18`:
  ```kotlin
  private fun stats(
      weightKg: Double = 80.0,
      heightCm: Int = 180,
      age: Int = 30,
      sex: Sex = Sex.MALE,
      activity: ActivityLevel = ActivityLevel.MODERATELY_ACTIVE
  ) = UserPhysicalStats(weightKg, heightCm, age, sex, activity)
  ```
  And in `AchievementRulesTest.kt:9-17`:
  ```kotlin
  private fun locked(id: String): AchievementProgress = …
  private fun unlocked(id: String): AchievementProgress = …
  ```
- Each `@Test` is single-assertion-focused but multi-assertion when validating a value tuple (e.g., `currentLength` + `runStartEpochDay` together in `StreakCalculatorTest`).
- Failure messages are passed as the last `assertX(expected, actual, message)` argument when the assertion isn't self-explanatory: `"Expected 30-45 entries per D-15, got ${…}"`, `"Already-unlocked achievement must not re-unlock"`.
- Float/Double comparisons use `assertEquals(expected, actual, absoluteTolerance = 0.01)` (`TdeeCalculatorTest.kt:22`).

## Mocking

**Framework:** None — no mocking library is configured.

**Patterns:**
- Tests target pure functions / value objects. The production code under test (`XpFormula`, `StreakCalculator`, `AchievementCatalog`, `AchievementRules`, `RankLadder`, `NutritionGoalDayPolicy`, `TdeeCalculator`) has no I/O, no coroutines, no flows — so mocking is unnecessary.
- For tests that need a "fake" entity, build it directly via its public data-class constructor (e.g., `AchievementProgress(def = …, currentProgress = 0L, unlockedAtMillis = null)` in `AchievementRulesTest.kt:11-12`).

**What to Mock:**
- N/A — current convention is to factor logic into pure functions, then call them directly with constructed inputs.

**What NOT to Mock:**
- N/A — there are no examples of mocking Room DAOs, repositories, or Ktor clients to copy from.

## Fixtures and Factories

**Test Data:**
```kotlin
// AchievementRulesTest.kt:9-17 — small private helpers next to the tests that use them
private fun locked(id: String): AchievementProgress {
    val def = AchievementCatalog.findById(id)!!
    return AchievementProgress(def = def, currentProgress = 0L, unlockedAtMillis = null)
}
private fun unlocked(id: String): AchievementProgress {
    val def = AchievementCatalog.findById(id)!!
    return AchievementProgress(def = def, currentProgress = def.threshold, unlockedAtMillis = 1L)
}
```

**Location:**
- Helpers live as `private fun` at the top of the test class that needs them. There is no `commonTest/.../fixtures/` or `testdata/` directory.

## Coverage

**Requirements:** None enforced. There is no Kover, no JaCoCo, no coverage gate in `build.gradle.kts` or CI.

**View Coverage:**
```bash
# Not configured. Would require adding the Kotlinx Kover plugin.
```

**What's tested (7 files, 542 LoC of tests):**
- Pure domain calculators in `domain/gamification/`:
  - `XpFormula` — volume-XP math, PR/streak/achievement XP constants, `EventKeys` formatter + parser
  - `StreakCalculator` — streak length, gaps, deduplication, threshold-crossed events
  - `AchievementCatalog` — catalog invariants (size, id format, tier completeness, monotonic thresholds)
  - `AchievementRules` — unlock evaluation, multi-tier unlocks, idempotency
  - `RankLadder` — rank threshold logic
  - `NutritionGoalDayPolicy` — goal-day eligibility rules
- Pure nutrition math in `domain/nutrition/`:
  - `TdeeCalculator` — BMR (male/female) and TDEE activity multipliers

**What's NOT tested (significant gaps):**
- **All 22 ViewModels** in `shared/src/commonMain/kotlin/com/pumpernickel/presentation/` — including the workout state machine (`WorkoutSessionViewModel`, 700+ LoC of state transitions), `TemplateEditorViewModel.save()`, AI streaming flows, and the gamification overview ViewModel. No StateFlow assertions anywhere.
- **All 6 Repositories** (`TemplateRepositoryImpl`, `WorkoutRepositoryImpl`, `ExerciseRepositoryImpl`, `FoodRepositoryImpl`, `GamificationRepository`, `ProgressPictureRepository`) — no in-memory Room test exists.
- **All 7 Room DAOs** (`WorkoutTemplateDao`, `WorkoutSessionDao`, `CompletedWorkoutDao`, `ExerciseDao`, `NutritionDao`, `GamificationDao`, `ProgressPictureDao`) — no `@Database` test setup.
- **Room migrations** — 5 auto-migrations are declared (`AppDatabase.kt:29-35`, versions 6→7→8→9→10→11) and the `shared/schemas/` directory is populated, but no migration test verifies them.
- **All UseCases** (`AddFoodUseCase`, `LogConsumptionUseCase`, `CalculateDailyMacrosUseCase`, `GetUndertrainedMusclesUseCase`, `LookupBarcodeUseCase`, etc.) — no tests.
- **Ktor clients** (`OpenFoodFactsApi`, `OpenAICompatibleClient`) — no `MockEngine` tests despite custom timeout/SSE/auth logic and a five-class `AiError` mapping function (`AiError.fromThrowable`) that is high-value to test.
- **Gamification engine** (`GamificationEngine`) — the orchestrator that consumes the tested formulas has no integration test of its own.
- **Composable screens** (44 screens in `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/`) — no Compose UI test, no screenshot test.
- **iOS Swift code** in `iosApp/iosApp/Views/` — no XCTest target.

## Test Types

**Unit Tests:**
- Scope: pure-function domain logic only (XP math, streak math, achievement rules, TDEE math, ladder, policy).
- Approach: input → expected output, single class under test, no doubles.

**Integration Tests:**
- None present. There is no test that wires Room + a repository, or a ViewModel + a fake repository, or Ktor + `MockEngine`.

**E2E Tests:**
- Not used. No `instrumentedTest`, no Maestro/Appium, no XCUITest.

## Common Patterns

**Async Testing:**
- Not exercised. No `runTest`, no `kotlinx-coroutines-test`, no `TestScope`, no `StandardTestDispatcher` usage anywhere in `commonTest`. If/when async tests are added, they will need `kotlinx-coroutines-test` declared in `commonTest.dependencies`.

**Error Testing:**
- Asserting "no error / no unlock / empty result" rather than catching exceptions:
  ```kotlin
  // AchievementRulesTest.kt:42 — assert absence rather than throw
  assertTrue(result.toUnlock.isEmpty(), "Already-unlocked achievement must not re-unlock")
  ```
  ```kotlin
  // XpFormulaTest.kt:55-58 — assert null for invalid parse input
  assertNull(EventKeys.parsePr("workout:42"))
  assertNull(EventKeys.parsePr("pr:onlyone"))
  assertNull(EventKeys.parsePr("pr:ex:notanumber"))
  ```
- `assertFailsWith<T> { … }` is not used anywhere — production code that uses exceptions for failures (`AiError.fromThrowable`, repository try/catch) has no test that drives the failure path.

**Adding a new test:**
1. Create `XxxTest.kt` in the test package mirroring the production package under `shared/src/commonTest/kotlin/com/pumpernickel/<layer>/<feature>/`.
2. `import kotlin.test.Test` plus the specific `assert…` functions you need.
3. One `class XxxTest` with `@Test fun camelCaseDescriptiveName()` methods.
4. Inline factories or `private fun build…()` helpers for repeated fixtures.
5. For float comparisons pass `absoluteTolerance`.
6. Tag the design-doc reference in a `// D-XX-YY` comment when the test enforces a documented requirement.

---

*Testing analysis: 2026-05-18*
