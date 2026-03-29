# Phase 6: Personal Best Display - Research

**Researched:** 2026-03-29
**Domain:** Room DAO aggregate queries, ViewModel StateFlow, SwiftUI display
**Confidence:** HIGH

## Summary

Phase 6 adds a "PB: 62.5 kg" label to the set entry screen during an active workout. The firmware reference implements this via a running average weight stored in a separate `ExerciseTrend` struct (totalVolume / totalReps = averageWeight). For the mobile app, we can compute this directly from the existing `completed_workout_sets` table via a Room DAO aggregate query -- no new tables or schema migration needed.

The implementation touches three layers: (1) a new Room DAO query that computes average weight per exercise across all completed workouts, (2) a new StateFlow in WorkoutSessionViewModel that loads PB data when a workout starts, and (3) a SwiftUI label in WorkoutSessionView's header section. The data already exists in the `completed_workout_sets` table joined through `completed_workout_exercises` -- we just need a query that aggregates it by `exerciseId`.

**Primary recommendation:** Add a single DAO query method `getAverageWeightByExerciseId(exerciseId: String): Int?` to CompletedWorkoutDao, expose it through WorkoutRepository, load it in WorkoutSessionViewModel as a Map<String, Int> (exerciseId -> avgWeightKgX10), and display it as a text label on the SwiftUI set entry screen.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ENTRY-07 | User sees personal best (running average weight) for current exercise during set entry | Room DAO aggregate query on completed_workout_sets, exposed via StateFlow to SwiftUI. Firmware parity: averageWeight = totalVolume / totalReps formula. No schema migration needed. |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- **Tech stack**: Kotlin Multiplatform + Compose Multiplatform, iOS first
- **Storage**: Room KMP 2.8.4, local/offline only
- **Architecture**: MVVM with StateFlow + collectAsState/asyncSequence observation
- **DI**: Koin 4.2.0
- **Weight representation**: kgX10 integer math throughout (no floats)
- **SwiftUI interop**: KMPNativeCoroutinesAsync with `*Flow` suffix convention
- **Unit display**: WeightUnit.formatWeight(kgX10:) handles kg/lbs conversion
- **GSD Workflow**: Do not make direct repo edits outside a GSD workflow

## Standard Stack

No new libraries needed. This phase uses only existing dependencies:

### Core (already in project)
| Library | Version | Purpose | Why Used Here |
|---------|---------|---------|---------------|
| Room KMP | 2.8.4 | DAO aggregate query | New `@Query` method on existing CompletedWorkoutDao |
| Kotlin Coroutines | 1.10.2 | Suspend function for DAO query | Query is a one-shot suspend call, not a Flow |
| KMPNativeCoroutinesAsync | 1.0.2 | Swift observation of StateFlow | Existing `*Flow` pattern for new personalBest StateFlow |

**Installation:** No new dependencies required.

## Architecture Patterns

### Data Flow: PB Computation

The firmware stores PB as a pre-computed `ExerciseTrend` struct that gets updated incrementally on each set completion. For the mobile app, we compute it on-demand from completed workout history via SQL aggregate:

```
completed_workout_sets.actualWeightKgX10 * actualReps  (per set)
                    |
         SUM across all sets for exerciseId = total volume
         SUM of all actualReps for exerciseId = total reps
                    |
         total volume / total reps = average weight (kgX10)
```

**Firmware formula (TrendCalculator.cpp line 62):**
```cpp
trend.averageWeight_kg_x10 = (uint16_t)(trend.totalVolume_kg_x10 / trend.totalReps);
```

This is a weighted average (volume-weighted by reps), not a simple arithmetic mean of weights. A set of 10 reps at 50kg contributes more than a set of 1 rep at 50kg. This is the correct sports science interpretation -- it answers "on average, how heavy was each rep?"

### Recommended Approach: On-Demand SQL Aggregate

**Why not a separate trend table (like firmware)?**
- The firmware uses a separate file because flash reads are expensive and it cannot do SQL queries.
- Room can compute this aggregate in ~1ms from indexed tables. No need for denormalization.
- Avoids schema migration, avoids keeping a trend table in sync with completed workouts.
- If performance becomes an issue with thousands of workouts, a materialized trend table can be added later.

### Pattern 1: Room DAO Aggregate Query

**What:** SQL query that joins completed_workout_exercises and completed_workout_sets to compute average weight per exercise.

**SQL:**
```sql
SELECT
    SUM(CAST(s.actualWeightKgX10 AS INTEGER) * CAST(s.actualReps AS INTEGER)) / SUM(s.actualReps)
FROM completed_workout_exercises e
INNER JOIN completed_workout_sets s ON s.workoutExerciseId = e.id
WHERE e.exerciseId = :exerciseId
AND s.actualReps > 0
```

**Key details:**
- `INNER JOIN` (not LEFT JOIN) ensures we only get rows with actual sets
- `WHERE s.actualReps > 0` prevents division by zero and excludes zero-rep sets
- Integer division matches firmware behavior (kgX10 precision is sufficient)
- Returns `null` when no history exists (new exercise)
- The query can return a single `Int?` -- no DTO class needed

### Pattern 2: Batch Load at Workout Start

**What:** Load PB for all exercises in the template at once when the workout starts (same pattern as `previousPerformance`).

**Why batch, not per-exercise:**
- A workout has 3-8 exercises. Loading all PBs in one coroutine launch at start avoids per-exercise-switch loading.
- The ViewModel already loads `previousPerformance` at start time. PB loading fits the same lifecycle.
- Alternative: load PB when cursor moves to a new exercise. This is more complex (cursor change detection) for negligible benefit.

**Two implementation options for batch loading:**

Option A -- Loop over exercise IDs:
```kotlin
val pbMap = mutableMapOf<String, Int>()
for (exercise in template.exercises) {
    val avgWeight = completedWorkoutDao.getAverageWeightForExercise(exercise.exerciseId)
    if (avgWeight != null) pbMap[exercise.exerciseId] = avgWeight
}
```

Option B -- Single query with GROUP BY:
```sql
SELECT e.exerciseId,
       SUM(CAST(s.actualWeightKgX10 AS INTEGER) * CAST(s.actualReps AS INTEGER)) / SUM(s.actualReps)
FROM completed_workout_exercises e
INNER JOIN completed_workout_sets s ON s.workoutExerciseId = e.id
WHERE e.exerciseId IN (:exerciseIds)
AND s.actualReps > 0
GROUP BY e.exerciseId
```

**Recommendation:** Option B (single query) is cleaner and faster for the typical 3-8 exercises. Requires a small DTO class for the result.

### Pattern 3: StateFlow Exposure

**What:** New `_personalBest` MutableStateFlow in WorkoutSessionViewModel, observed by SwiftUI.

```kotlin
private val _personalBest = MutableStateFlow<Map<String, Int>>(emptyMap())
@NativeCoroutinesState
val personalBest: StateFlow<Map<String, Int>> = _personalBest.asStateFlow()
```

**SwiftUI observation:** follows existing `previousPerformance` pattern exactly:
```swift
@State private var personalBest: [String: KotlinInt] = [:]

// In task group:
group.addTask { await observePersonalBest() }

private func observePersonalBest() async {
    do {
        for try await value in asyncSequence(for: viewModel.personalBestFlow) {
            self.personalBest = value
        }
    } catch { ... }
}
```

### Pattern 4: SwiftUI Label Display

**What:** Text label in the header section showing "PB: 62.5 kg" below the exercise name.

**Placement:** In `headerSection()`, after the existing "Last: ..." previous performance label. The PB label is logically distinct from "Last" (last workout's performance) -- it shows the all-time average.

```swift
if let pbWeightKgX10 = personalBest[exercise.exerciseId] {
    Text("PB: \(weightUnit.formatWeight(kgX10: pbWeightKgX10))")
        .font(.subheadline)
        .foregroundColor(.blue)
        .frame(maxWidth: .infinity, alignment: .leading)
}
```

**Color differentiation:** Use `.blue` (or similar) to distinguish PB from the orange "Last: ..." label.

### Anti-Patterns to Avoid

- **Storing PB in a separate Room table:** Premature optimization. The aggregate query is fast enough for the data volumes in a personal fitness app (hundreds of workouts, not millions).
- **Computing PB in Swift/UI layer:** Business logic must stay in KMP common code. The DAO query runs in KMP, result flows to Swift via StateFlow.
- **Using Float for average calculation:** The project uses kgX10 integer math everywhere. SQL integer division is fine (0.1kg precision matches firmware).
- **Reactive Flow for PB:** PB does not change during a workout (the workout is not yet saved to completed_workouts). A one-shot suspend query at workout start is sufficient. No need for `Flow<>` observation of PB.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Weighted average calculation | Manual Kotlin loop over all sets | SQL aggregate (SUM * SUM / SUM) in Room DAO | SQL is declarative, handles empty set edge cases, runs on IO dispatcher automatically |
| Weight formatting with unit | Custom string formatting in Swift | `WeightUnit.formatWeight(kgX10:)` | Already exists, handles kg/lbs conversion with integer math |
| KMP-to-Swift state bridging | Custom callback/delegate pattern | `@NativeCoroutinesState` + `asyncSequence` | Established project pattern, used for 5+ StateFlows already |

## Common Pitfalls

### Pitfall 1: Division by Zero in SQL
**What goes wrong:** If all sets for an exercise have `actualReps = 0`, `SUM(s.actualReps)` returns 0 and SQL integer division crashes or returns null.
**Why it happens:** Edge case with bad data or if 0-rep guard (ENTRY-06) was not in place for older data.
**How to avoid:** Add `WHERE s.actualReps > 0` to the query. This filters out zero-rep sets before aggregation.
**Warning signs:** Null result for an exercise that has workout history.

### Pitfall 2: KotlinInt vs Swift Int Type Bridging
**What goes wrong:** Room returns `Int?` (Kotlin), which bridges to `KotlinInt?` in Swift. Using it in `formatWeight(kgX10:)` requires `.int32Value`.
**Why it happens:** KMP-to-Swift numeric bridging wraps Kotlin primitives in KotlinInt/KotlinLong objects.
**How to avoid:** The Map type `Map<String, Int>` bridges to `[String: KotlinInt]`. Access as `pbValue.int32Value` or cast. Alternatively, store the map values as Int32 in the StateFlow to simplify Swift access.
**Warning signs:** Compile error in Swift: "Cannot convert value of type 'KotlinInt' to expected argument type 'Int32'".

### Pitfall 3: Confusing "Personal Best" with "Max Weight"
**What goes wrong:** Implementing PB as `MAX(actualWeightKgX10)` instead of the weighted average.
**Why it happens:** "Personal best" colloquially means "highest ever." But the requirement (ENTRY-07) and firmware reference both specify "running average weight" (volume / total reps).
**How to avoid:** Use the firmware formula: `SUM(weight * reps) / SUM(reps)`. The UAT criterion says "PB: 62.5 kg computed from all previous completed sets" -- this is the weighted average.
**Warning signs:** PB showing an unusually high value that doesn't represent typical performance.

### Pitfall 4: PB Not Updating After Workout Completion
**What goes wrong:** User finishes a workout, starts a new one for the same template, but PB still shows old value.
**Why it happens:** PB is loaded at workout start. The previous workout was just saved to `completed_workouts` moments ago.
**How to avoid:** This is actually fine -- PB is loaded fresh at `startWorkout()` time, which runs AFTER the previous workout's `finishWorkout()` has saved to Room. The sequence is: finishWorkout -> saveCompletedWorkout -> resetToIdle -> (user selects template) -> startWorkout -> loadPB. No issue.
**Warning signs:** None expected if the load happens in `startWorkout()`.

## Code Examples

### Room DAO Query (batch, with GROUP BY)

```kotlin
// In CompletedWorkoutDao.kt
data class ExercisePbDto(
    val exerciseId: String,
    val avgWeightKgX10: Int
)

@Query("""
    SELECT e.exerciseId,
           SUM(CAST(s.actualWeightKgX10 AS INTEGER) * CAST(s.actualReps AS INTEGER)) / SUM(s.actualReps) AS avgWeightKgX10
    FROM completed_workout_exercises e
    INNER JOIN completed_workout_sets s ON s.workoutExerciseId = e.id
    WHERE e.exerciseId IN (:exerciseIds)
    AND s.actualReps > 0
    GROUP BY e.exerciseId
""")
suspend fun getPersonalBests(exerciseIds: List<String>): List<ExercisePbDto>
```

### Repository Method

```kotlin
// In WorkoutRepository interface
suspend fun getPersonalBests(exerciseIds: List<String>): Map<String, Int>

// In WorkoutRepositoryImpl
override suspend fun getPersonalBests(exerciseIds: List<String>): Map<String, Int> {
    return completedWorkoutDao.getPersonalBests(exerciseIds)
        .associate { it.exerciseId to it.avgWeightKgX10 }
}
```

### ViewModel Integration

```kotlin
// In WorkoutSessionViewModel, inside startWorkout() after loading exercises
val exerciseIds = exercises.map { it.exerciseId }
_personalBest.value = workoutRepository.getPersonalBests(exerciseIds)

// Same pattern in resumeWorkout()
val exerciseIds = updatedExercises.map { it.exerciseId }
_personalBest.value = workoutRepository.getPersonalBests(exerciseIds)
```

### SwiftUI Display (in headerSection)

```swift
// After the existing "Last: ..." label
if let pbKgX10 = personalBest[exercise.exerciseId] {
    Text("PB: \(weightUnit.formatWeight(kgX10: pbKgX10.int32Value))")
        .font(.subheadline)
        .foregroundColor(.blue)
        .frame(maxWidth: .infinity, alignment: .leading)
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Firmware: pre-computed ExerciseTrend in flash | Mobile: on-demand SQL aggregate | This phase | No trend table needed; query is fast for personal-scale data |
| Firmware: PB labeled as "PB:61.0" (kg only) | Mobile: unit-aware "PB: 61.0 kg" or "PB: 134.5 lbs" | This phase | WeightUnit.formatWeight handles unit conversion |

## Open Questions

1. **Should PB include sets from the current active workout?**
   - What we know: Firmware loads PB from trend data that includes all previously confirmed sets (including current workout). The mobile app loads PB at workout start, so the current workout's sets are NOT included.
   - What's unclear: Whether the user expects PB to update mid-workout as they log sets.
   - Recommendation: Keep it simple -- load once at start. The PB is a historical reference, not a live metric. Mid-workout updates add complexity for minimal value. Can be revisited if users request it.

2. **Label text: "PB" vs "Avg" vs "Best"?**
   - What we know: Firmware uses "PB:" prefix. The metric is actually a weighted average, not a max.
   - What's unclear: Whether "PB" is misleading for a weighted average.
   - Recommendation: Use "PB:" for firmware parity. The user (developer) knows what it means from the firmware context. Can be renamed later if needed.

## Sources

### Primary (HIGH confidence)
- Firmware reference: `WorkoutSetEntryState.cpp` lines 104-110 -- PB lookup from ExerciseTrend
- Firmware reference: `TrendCalculator.cpp` lines 56-62 -- average weight formula (totalVolume / totalReps)
- Firmware reference: `DataModels.h` lines 98-110 -- ExerciseTrend struct definition
- Existing codebase: `CompletedWorkoutDao.kt` -- current DAO with existing aggregate query pattern (getWorkoutSummaries)
- Existing codebase: `WorkoutSessionViewModel.kt` -- StateFlow pattern for previousPerformance (lines 76-78)
- Existing codebase: `WorkoutSessionView.swift` -- observation pattern for previousPerformance (lines 227-236)

### Secondary (MEDIUM confidence)
- Room KMP documentation: `@Query` with aggregate functions and DTO result mapping (verified via existing codebase usage in getWorkoutSummaries)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- no new libraries, all existing patterns
- Architecture: HIGH -- direct firmware parity formula, existing DAO/ViewModel/SwiftUI patterns
- Pitfalls: HIGH -- edge cases verified against firmware behavior and existing codebase

**Research date:** 2026-03-29
**Valid until:** 2026-04-28 (stable -- no external dependencies or version-sensitive patterns)
