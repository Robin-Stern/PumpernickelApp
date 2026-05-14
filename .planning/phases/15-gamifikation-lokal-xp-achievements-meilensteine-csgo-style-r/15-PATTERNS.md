# Phase 15: Gamifikation lokal — Pattern Mapping

**Generated:** 2026-04-22
**Scope:** Map every file-to-create / file-to-modify to its closest existing analog so the planner and executor can mirror conventions exactly.

---

## Table of Contents

1. [Data flow (at a glance)](#data-flow-at-a-glance)
2. [Files to CREATE](#files-to-create)
   - Domain layer (XP engine + catalog)
   - Room entities (ledger + state)
   - DAO
   - Repository
   - Seeder
   - ViewModels
   - Android UI (screens + modal)
   - iOS UI (views + modal)
3. [Files to MODIFY](#files-to-modify)
   - `AppDatabase.kt` (v7 → v8)
   - `SettingsRepository.kt` (retroactive sentinel flag)
   - `SharedModule.kt` (Koin wiring)
   - `KoinHelper.kt` (iOS VM factory)
   - `WorkoutSessionViewModel.kt` (post-save unlock hook)
   - `OverviewViewModel.kt` (expose rank/XP state)
   - `OverviewView.swift` / `OverviewScreen.kt` (rank strip)
   - `SettingsView.swift` / `SettingsSheet.kt` (Achievements entry)
   - `MainScreen.kt` / `MainTabView.swift` (route to gallery)
   - `Routes.kt` (nav routes)
4. [Cross-cutting pattern excerpts](#cross-cutting-pattern-excerpts)

---

## Data flow (at a glance)

```
┌──────────────────────────────────────────────────────────────────────────┐
│ Trigger: WorkoutSessionViewModel.saveReviewedWorkout()          (D-20)    │
│ Trigger: GoalDayChecker onAppResume after midnight              (D-22)    │
└────────────────────────────────┬─────────────────────────────────────────┘
                                 ▼
                   ┌──────────────────────────┐
                   │  GamificationEvent       │  (sealed class: WorkoutCompleted,
                   │  (domain/gamification/)  │   PrHit, NutritionGoalDay, StreakThreshold,
                   │                          │   AchievementUnlocked)
                   └────────────┬─────────────┘
                                ▼
                   ┌──────────────────────────┐
                   │  GamificationEngine      │  checks dedupe keys, computes XP,
                   │  (domain/gamification/)  │   runs achievement rules, rank promotion
                   └────────────┬─────────────┘
                                ▼
                   ┌──────────────────────────┐
                   │  GamificationRepository  │  writes to Room ledger +
                   │  (data/repository/)      │   achievement_state + rank_state
                   └────────────┬─────────────┘
                                ▼
                   ┌──────────────────────────┐
                   │  Room DAOs (v8)          │  xp_ledger, achievement_state,
                   │  (data/db/)              │   rank_state, gamification_meta
                   └────────────┬─────────────┘
                                ▼
                  ┌──────────────────────────────┐
                  │  StateFlow<RankState>,       │  consumed by
                  │  SharedFlow<UnlockEvent>     │   OverviewViewModel,
                  │  exposed from GamEngine      │   WorkoutSessionViewModel,
                  │  (or new GamificationVM)     │   AchievementGalleryViewModel
                  └────────────┬─────────────────┘
                               ▼
         ┌──────────────────────┴────────────────────┐
         ▼                                            ▼
  Overview strip (rank + XP)              Unlock modal queue (CSGO promo / achievement)
  AchievementGalleryScreen / View         Haptic + full-screen celebratory sheet
```

---

## Files to CREATE

Each entry lists: **role** → **closest analog** → **what to replicate**.

---

### 1. `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/GamificationEvent.kt`

- **Role:** Sealed class enumerating every XP-awarding event (workout completed, PR hit, nutrition goal-day, streak threshold, achievement unlocked).
- **Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt` → `sealed class WorkoutSessionState` / `sealed class RestState` (lines 28–61).
- **Excerpt to mirror:**

```kotlin
sealed class WorkoutSessionState {
    data object Idle : WorkoutSessionState()
    data class Active(
        val templateId: Long,
        val templateName: String,
        val exercises: List<SessionExercise>,
        val currentExerciseIndex: Int,
        val currentSetIndex: Int,
        val startTimeMillis: Long,
        val restState: RestState = RestState.NotResting
    ) : WorkoutSessionState()
    data class Reviewing(...) : WorkoutSessionState()
    data class Finished(...) : WorkoutSessionState()
}
```

- **What to replicate:**
  - Kotlin `sealed class` with `data class` variants for each event source.
  - Each event carries the dedupe key fields directly (workoutId, date, achievementId).
  - File header: `package com.pumpernickel.domain.gamification`.
  - No Room imports — pure domain model.

---

### 2. `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/XpFormula.kt`

- **Role:** Pure functions for computing XP per event (D-02, D-03, D-06, D-17).
- **Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/presentation/overview/OverviewViewModel.kt` → `TrainingIntensity.fromWeightedScore` / `rirMultiplier` companion-object pure-functions (lines 55–70).
- **Excerpt to mirror:**

```kotlin
enum class TrainingIntensity {
    NONE, LOW, MODERATE, HIGH;

    companion object {
        fun fromWeightedScore(score: Double): TrainingIntensity = when {
            score <= 0.0 -> NONE
            score < 5.0  -> LOW
            score <= 12.0 -> MODERATE
            else -> HIGH
        }
        fun rirMultiplier(rir: Int): Double = when {
            rir >= 4 -> 0.5
            rir >= 2 -> 1.0
            rir == 1 -> 1.5
            else     -> 2.0
        }
    }
}
```

- **What to replicate:**
  - `object XpFormula { fun workoutXp(sets: List<CompletedSet>): Int = ... }` style.
  - Constants for streak thresholds (`STREAK_3D = 25`, `STREAK_7D = 100`, `STREAK_30D = 500`, `NUTRITION_7D = 100`, `PR_XP = 50`, achievement XP tier constants `25/75/200`).
  - Keep formula math unit-consistent (divide weight-kg * reps by 100, floor; reuse the weight-kgX10 scaling from `CompletedWorkoutSetEntity.actualWeightKgX10` — convert to kg with `/ 10.0` before multiplying).
  - `// TODO(tuning): re-tune after play-testing per D-07` comments on every magic number.

---

### 3. `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/RankLadder.kt`

- **Role:** Static 10-rank ladder (D-08), exponential threshold curve `base × 1.5^(n-1)` (D-09), rank lookup function.
- **Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/MuscleGroup.kt` (canonical enum list for a fixed domain) + `object MuscleRegionPaths` pattern (`shared/.../domain/model/MuscleRegionPaths.kt` line 14).
- **Excerpt to mirror:**

```kotlin
object MuscleRegionPaths {
    const val VIEW_BOX_WIDTH = 676.49f
    const val VIEW_BOX_HEIGHT = 1203.49f
    val frontRegions: List<MuscleRegionPath> = listOf( ... )
    val backRegions: List<MuscleRegionPath> = listOf( ... )
}
```

- **What to replicate:**
  - `enum class Rank(val displayName: String, val tier: Int)` with 10 entries (Silver … Global Elite).
  - `object RankLadder { const val BASE_XP = 500 ; fun thresholdFor(rank: Rank): Long ; fun rankForXp(xp: Long): Rank ; fun nextRank(current: Rank): Rank? }`.
  - `// TODO(tuning)` comment on `BASE_XP` per D-09.
  - Rank 1 (Silver) threshold = 0 (D-11 — unlocks on first workout).

---

### 4. `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/AchievementCatalog.kt`

- **Role:** Static, code-defined catalog of 10–15 achievements × 3 tiers (D-14, D-15, D-16). Muscle-coverage achievements read region list from `MuscleRegionPaths`.
- **Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/MuscleRegionPaths.kt` (static data source object) + `shared/src/commonMain/kotlin/com/pumpernickel/data/db/NutritionDataSeeder.kt` (hard-coded UUID string IDs for seed entities).
- **Excerpt to mirror (from NutritionDataSeeder):**

```kotlin
class NutritionDataSeeder(private val dao: NutritionDao) {
    suspend fun seedIfEmpty() {
        if (dao.getAllFoods().isNotEmpty()) return
        val foods = listOf(
            FoodEntity(id = "00000000-0000-0000-0000-000000000001", name = "Ei (gekocht)", ...),
            ...
        )
        foods.forEach { dao.insertFood(it) }
    }
}
```

- **What to replicate:**
  - `object AchievementCatalog { val all: List<AchievementDef> = listOf(...) }` with stable string IDs (e.g., `"volume-bronze"`, `"consistency-longest-streak-gold"`).
  - Each `AchievementDef(id, category: Category, tier: Tier, threshold: Long, displayName, flavourCopy)`.
  - `Category` and `Tier` enums in the same file.
  - Muscle-variety achievements reference `MuscleRegionPaths.frontRegions` / `backRegions` by `regionId` — cite `MuscleRegionPaths` directly; do NOT hardcode the region list.
  - For PR-hunter and volume, use `// TODO(tuning)` on thresholds per D-07 / D-15.

---

### 5. `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/GamificationEngine.kt`

- **Role:** Orchestrator. On each event: computes XP (via `XpFormula`), writes ledger row with dedupe key, checks streak thresholds, runs achievement rules, checks rank promotion, emits queued unlock events.
- **Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt` → `saveReviewedWorkout()` (lines 535–585) shows the pattern for a multi-step orchestrator that reads multiple sources, runs logic, and writes a multi-entity transaction.
- **Excerpt to mirror:**

```kotlin
fun saveReviewedWorkout() {
    viewModelScope.launch {
        val reviewing = _sessionState.value as? WorkoutSessionState.Reviewing ?: return@launch
        val completedExercises = reviewing.exercises.mapIndexedNotNull { order, exercise -> ... }
        val completedWorkout = CompletedWorkout( ... )
        workoutRepository.saveCompletedWorkout(completedWorkout)
        workoutRepository.clearActiveSession()
        _hasActiveSession.value = false
        // ... state transition
    }
}
```

- **What to replicate:**
  - Class in `commonMain` (no `ViewModel`), injected via Koin.
  - Constructor takes `GamificationRepository`, `WorkoutRepository`, `SettingsRepository`, `CompletedWorkoutDao`, `NutritionDao`, `CalculateDailyMacrosUseCase`.
  - Exposes `val unlockEvents: SharedFlow<UnlockEvent>` (replay or buffer 8 for D-20 queueing).
  - Exposes `val rankState: StateFlow<RankState>` (subscribe-to-repository flow via `stateIn`).
  - Methods: `suspend fun onWorkoutSaved(workoutId: Long)`, `suspend fun onNutritionGoalDay(date: LocalDate)`, `suspend fun applyRetroactiveIfNeeded()`.
  - Dedupe: before inserting a ledger row, query by `(source, eventKey)` via a DAO `getBySourceAndKey(...)` lookup — pattern analogous to `NutritionDataSeeder.seedIfEmpty()` first-check.

---

### 6. `shared/src/commonMain/kotlin/com/pumpernickel/data/db/XpLedgerEntity.kt`

- **Role:** Append-only XP ledger row. One row per XP-awarding event with a stable dedupe key so retroactive re-runs can never double-count (D-13).
- **Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/data/db/CompletedWorkoutSetEntity.kt` (entity w/ FK + index + `@ColumnInfo(defaultValue=...)`).
- **Excerpt to mirror:**

```kotlin
@Entity(
    tableName = "completed_workout_sets",
    foreignKeys = [ForeignKey(
        entity = CompletedWorkoutExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutExerciseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("workoutExerciseId")]
)
data class CompletedWorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseId: Long,
    val setIndex: Int,
    val actualReps: Int,
    val actualWeightKgX10: Int,
    @ColumnInfo(defaultValue = "2") val rir: Int = 2
)
```

- **What to replicate:**
  - `@Entity(tableName = "xp_ledger", indices = [Index(value = ["source", "eventKey"], unique = true)])` — the unique index enforces idempotency at the DB level.
  - Columns: `id Long PK autoGenerate`, `source String` (workout / pr / nutrition_goal_day / streak / achievement), `eventKey String` (e.g., `"workout:123"`, `"pr:bench_press:2026-04-20"`, `"goalday:2026-04-20"`, `"streak:workout:7"`, `"achievement:volume-gold"`), `xpAmount Int`, `awardedAtMillis Long`, `retroactive Boolean` (D-12 flag so we can distinguish historical vs live).
  - Keep to primitive types / Strings (Room-friendly). No enum types in DB schema (convert at mapper layer like `CompletedWorkoutSetEntity.rir`).

---

### 7. `shared/src/commonMain/kotlin/com/pumpernickel/data/db/AchievementStateEntity.kt`

- **Role:** Per-achievement-tier unlock record (null if not unlocked).
- **Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/data/db/FoodEntity.kt` (string-PK entity).
- **Excerpt to mirror:**

```kotlin
@Entity(tableName = "foods")
data class FoodEntity(
    @PrimaryKey val id: String,
    val name: String,
    val calories: Double,
    ...
)
```

- **What to replicate:**
  - `@Entity(tableName = "achievement_state")`.
  - `@PrimaryKey val achievementId: String` (matches `AchievementCatalog` stable IDs).
  - Columns: `unlockedAtMillis: Long?` (null = locked), `tier: String` (Bronze/Silver/Gold as text per existing enum-as-text convention from `ConsumptionEntryEntity.unit`), `currentProgress: Long` (tracks cumulative value for tier).
  - Seeded at first launch by the new seeder (row per tier, initially locked).

---

### 8. `shared/src/commonMain/kotlin/com/pumpernickel/data/db/RankStateEntity.kt`

- **Role:** Singleton row holding current rank + total XP + last promotion timestamp.
- **Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/data/db/ActiveSessionEntity.kt` (singleton pattern with `@PrimaryKey val id: Long = 1`).
- **Excerpt to mirror:**

```kotlin
@Entity(tableName = "active_sessions")
data class ActiveSessionEntity(
    @PrimaryKey val id: Long = 1,  // Singleton: at most one active session
    val templateId: Long,
    ...
)
```

- **What to replicate:**
  - `@PrimaryKey val id: Long = 1` (singleton).
  - Columns: `totalXp: Long`, `currentRank: String` (rank enum name), `lastPromotedAtMillis: Long?`, `isUnranked: Boolean` (D-11 default true, set false on first workout save).
  - Uses `upsert`-style insert like `WorkoutSessionDao.upsertSession(...)`.

---

### 9. `shared/src/commonMain/kotlin/com/pumpernickel/data/db/GamificationDao.kt`

- **Role:** Room DAO for all three new tables. Ledger insert + dedupe query, achievement state query + update, rank state upsert + flow.
- **Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/data/db/CompletedWorkoutDao.kt` (multi-entity DAO with insert/query/flow mix) + `shared/src/commonMain/kotlin/com/pumpernickel/data/db/NutritionDao.kt` (multi-entity DAO with `OnConflictStrategy.REPLACE`).
- **Excerpt to mirror (from CompletedWorkoutDao):**

```kotlin
@Dao
interface CompletedWorkoutDao {
    @Insert
    suspend fun insertWorkout(workout: CompletedWorkoutEntity): Long

    @Query("SELECT * FROM completed_workouts ORDER BY startTimeMillis DESC")
    fun getAllWorkouts(): Flow<List<CompletedWorkoutEntity>>

    @Query("""
        SELECT e.exerciseId,
               MAX(CAST(s.actualWeightKgX10 AS INTEGER)) AS maxWeightKgX10
        FROM completed_workout_exercises e
        INNER JOIN completed_workout_sets s ON s.workoutExerciseId = e.id
        WHERE e.exerciseId IN (:exerciseIds)
        AND s.actualReps > 0
        GROUP BY e.exerciseId
    """)
    suspend fun getPersonalBests(exerciseIds: List<String>): List<ExercisePbDto>
}
```

- **What to replicate:**
  - `@Dao interface GamificationDao { ... }` in commonMain.
  - `@Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertLedgerEntry(entry: XpLedgerEntity): Long` — `IGNORE` gives idempotent behaviour against the unique `(source, eventKey)` index. A return of `-1L` means "already applied".
  - `@Query("SELECT SUM(xpAmount) FROM xp_ledger") fun totalXpFlow(): Flow<Long?>` — Flow for reactive rank recomputation.
  - `@Query("SELECT * FROM xp_ledger WHERE source = :src AND eventKey = :key LIMIT 1") suspend fun findEntry(src: String, key: String): XpLedgerEntity?` — used when engine needs to check before firing a streak bonus.
  - `@Query("SELECT * FROM rank_state WHERE id = 1") fun rankStateFlow(): Flow<RankStateEntity?>` — consumed by OverviewViewModel.
  - `@Update` + `@Insert(OnConflictStrategy.REPLACE)` for rank_state upsert (mirror `NutritionDao.insertFood` pattern).
  - `@Query("SELECT * FROM achievement_state") fun achievementStateFlow(): Flow<List<AchievementStateEntity>>` — consumed by AchievementGalleryViewModel.
  - `@Query("UPDATE achievement_state SET unlockedAtMillis = :ts, currentProgress = :progress WHERE achievementId = :id") suspend fun unlockAchievement(id: String, ts: Long, progress: Long)`.

---

### 10. `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/GamificationRepository.kt`

- **Role:** Thin repository wrapping `GamificationDao` + `SettingsRepository` (for retroactive sentinel). Exposes flow for rank + XP + achievements, plus write methods called by the engine.
- **Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/WorkoutRepository.kt` (interface + `Impl` split, Flow-based reads, suspend writes) and `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt` (DataStore wrapper with `Flow` + suspend setters).
- **Excerpt to mirror (from WorkoutRepository):**

```kotlin
interface WorkoutRepository {
    suspend fun saveCompletedWorkout(workout: CompletedWorkout)
    fun getWorkoutSummaries(): Flow<List<WorkoutSummary>>
    suspend fun getPersonalBests(exerciseIds: List<String>): Map<String, Int>
}

class WorkoutRepositoryImpl(
    private val workoutSessionDao: WorkoutSessionDao,
    private val completedWorkoutDao: CompletedWorkoutDao
) : WorkoutRepository { ... }
```

- **What to replicate:**
  - `interface GamificationRepository` in commonMain with `GamificationRepositoryImpl` in the same file (repo convention — see WorkoutRepository.kt).
  - Suspend writes: `awardXp(source: String, eventKey: String, amount: Int, retroactive: Boolean = false): Boolean` — returns `true` if inserted, `false` if dedupe IGNORE fired (i.e. `insertLedgerEntry` returned `-1L`).
  - Flow reads: `val totalXp: Flow<Long>`, `val rankState: Flow<RankState>` (maps `RankStateEntity` → domain `RankState`), `val achievements: Flow<List<AchievementProgress>>`.
  - Mapper functions like `WorkoutRepositoryImpl.getWorkoutSummaries` does DTO→domain `.map { dto -> ... }` — same pattern for `RankStateEntity → RankState`.

---

### 11. `shared/src/commonMain/kotlin/com/pumpernickel/data/db/AchievementStateSeeder.kt`

- **Role:** On first launch, if `achievement_state` table is empty, insert one locked row per achievement × tier from `AchievementCatalog`.
- **Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/data/db/NutritionDataSeeder.kt` (entire file).
- **Excerpt to mirror:**

```kotlin
class NutritionDataSeeder(private val dao: NutritionDao) {
    suspend fun seedIfEmpty() {
        if (dao.getAllFoods().isNotEmpty()) return
        val foods = listOf( FoodEntity(id = "...", ...), ... )
        foods.forEach { dao.insertFood(it) }
    }
}
```

- **What to replicate:**
  - Identical class shape: `class AchievementStateSeeder(private val dao: GamificationDao)` with `suspend fun seedIfEmpty()`.
  - Query via `dao.achievementStateFlow().first().isNotEmpty()` → early-return, otherwise insert one locked row per `AchievementCatalog.all` entry.
  - Wire in `SharedModule.kt` next to `NutritionDataSeeder` on line 84 and invoke the same place `NutritionDataSeeder.seedIfEmpty()` is invoked (confirm during planning — check `PumpernickelApplication.kt` / iOS `KoinInitIos.kt` for current seeder trigger path).

---

### 12. `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/RetroactiveWalker.kt`

- **Role:** D-12 / D-13 one-shot walker. Reads the sentinel flag (new `SettingsRepository.retroactiveApplied`), walks `completed_workouts` + `consumption_entries` chronologically, awards XP via `GamificationEngine.awardXp(...)` with `retroactive = true`, sets sentinel on success.
- **Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt` → `resumeWorkout()` (lines 187–280) is the closest analog — single long async operation that rehydrates from DB in chronological order.
- **Excerpt to mirror:**

```kotlin
fun resumeWorkout() {
    viewModelScope.launch {
        val activeSession = workoutRepository.getActiveSession() ?: return@launch
        val template = templateRepository.getTemplateById(activeSession.templateId).first()
            ?: return@launch
        // ... fetch ordered data ...
        val updatedExercises = exercises.mapIndexed { exIdx, exercise ->
            val completedForExercise = activeSession.completedSets
                .filter { it.exerciseIndex == exIdx }
            // overlay completed sets
        }
        // ... update state
    }
}
```

- **What to replicate:**
  - `class RetroactiveWalker(private val engine, private val settingsRepo, private val completedWorkoutDao, private val nutritionDao)`.
  - Single `suspend fun applyIfNeeded()` that:
    1. checks `settingsRepo.retroactiveApplied.first()` — if true, return.
    2. reads all completed workouts sorted by `startTimeMillis ASC` (needs new DAO method — D-12 requires chronological order).
    3. for each workout, computes volume XP + PR XP + streak thresholds using an in-memory running-PB map (so PR detection works with historical state, D-12).
    4. aggregates `consumption_entries` by date, evaluates goal-day per D-04, awards XP.
    5. on completion, `settingsRepo.setRetroactiveApplied(true)`.
  - Must run inside a single Room transaction (D-13). Pattern: use `AppDatabase.withTransaction { ... }` or — preferred with Room KMP 2.8.4 — bundle all DAO writes into a DAO `@Transaction`-annotated method (add `@Transaction suspend fun applyRetroactive(entries: List<XpLedgerEntity>) { ... }` to `GamificationDao`).
  - Dedupe keys (see XpLedgerEntity §6) mean partial-run rollback + re-run is safe.

---

### 13. `shared/src/commonMain/kotlin/com/pumpernickel/presentation/gamification/AchievementGalleryViewModel.kt`

- **Role:** Exposes `StateFlow<AchievementGalleryUiState>` for the gallery screen (list of achievements, grouped by category, with locked/unlocked status + unlock date).
- **Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/presentation/overview/OverviewViewModel.kt` (Flow `stateIn` pattern + UiState data class).
- **Excerpt to mirror:**

```kotlin
data class OverviewUiState(
    val muscleLoad: Map<MuscleGroup, TrainingIntensity> = emptyMap(),
    val todayMacros: RecipeMacros = RecipeMacros(),
    val nutritionGoals: NutritionGoals = NutritionGoals(),
    val isLoading: Boolean = true
)

class OverviewViewModel(...) : ViewModel() {
    private val _uiState = MutableStateFlow(OverviewUiState())

    @NativeCoroutinesState
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    @NativeCoroutinesState
    val nutritionGoals: StateFlow<NutritionGoals> = settingsRepository
        .nutritionGoals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NutritionGoals())
}
```

- **What to replicate:**
  - `data class AchievementGalleryUiState(val byCategory: Map<Category, List<AchievementTile>>, val isLoading: Boolean)` — `AchievementTile` includes locked/unlocked, tier, name, flavour, unlock date.
  - `@NativeCoroutinesState` on public `StateFlow` — required for iOS `asyncSequence(for:)`.
  - Derived from `gamificationRepository.achievements` Flow via `.map { ... }.stateIn(...)`.
  - Construct `AchievementTile` by joining `AchievementCatalog.all` (static defs) with the live `AchievementProgress` flow rows.

---

### 14. `shared/src/commonMain/kotlin/com/pumpernickel/presentation/gamification/GamificationViewModel.kt` *(optional — see planner discretion)*

- **Role:** Single VM shared between Overview strip and unlock-modal host, exposing `rankState: StateFlow<RankState>` and `unlockEvents: SharedFlow<UnlockEvent>`. If the planner decides to fold this into `OverviewViewModel` + `WorkoutSessionViewModel` directly, skip this file. Otherwise, it wraps `GamificationEngine` for UI consumption.
- **Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/presentation/settings/SettingsViewModel.kt` (thin ViewModel that re-exposes repository flows with `stateIn`).
- **Excerpt to mirror:**

```kotlin
class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    @NativeCoroutinesState
    val weightUnit: StateFlow<WeightUnit> = settingsRepository
        .weightUnit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeightUnit.KG)
}
```

- **What to replicate:**
  - `@NativeCoroutinesState val rankState: StateFlow<RankState> = repo.rankState.stateIn(...)`.
  - Use `@NativeCoroutines` (without `State`) for `SharedFlow<UnlockEvent>` — the unlock-events stream is a one-shot event, not a replayable state.

---

### 15. `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AchievementGalleryScreen.kt`

- **Role:** Compose screen rendering locked/unlocked grid under Settings (D-21).
- **Analog:** `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/OverviewScreen.kt` (LazyColumn + StateFlow collection) + `androidApp/.../ExerciseCatalogScreen.kt` (grid presentation — find via `LazyVerticalGrid` uses if needed).
- **Excerpt to mirror (from OverviewScreen.kt, lines 64–100):**

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Übersicht", fontWeight = FontWeight.Bold) }, ...) }
    ) { padding ->
        if (uiState.isLoading) {
            Box(...) { CircularProgressIndicator() }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) { ... }
        }
    }
}
```

- **What to replicate:**
  - `@Composable fun AchievementGalleryScreen(viewModel = koinViewModel(), navController)`.
  - `Scaffold { TopAppBar(title = "Achievements", navigationIcon = back-button → navController.popBackStack()) }`.
  - `LazyVerticalGrid(GridCells.Fixed(2))` per category with sticky headers; locked tiles = desaturated, unlocked = full colour with MaterialTheme.colorScheme.primary accent + unlock date.
  - Follow existing string localisation pattern from `TemplateListScreen` (`stringResource(R.string.xxx)`).

---

### 16. `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/UnlockModal.kt`

- **Role:** Full-screen celebratory modal for rank promotion / achievement unlock (D-19). Queued — one at a time (D-20).
- **Analog:** `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/SettingsSheet.kt` (ModalBottomSheet composable with `onDismiss`) + vibrator pattern from `WorkoutSessionScreen.kt` lines 384–391.
- **Excerpt to mirror (ModalBottomSheet from SettingsSheet.kt):**

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    val viewModel: SettingsViewModel = koinViewModel()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(text = stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
            ...
        }
    }
}
```

- **Excerpt to mirror (haptic from WorkoutSessionScreen.kt):**

```kotlin
@Suppress("DEPRECATION")
val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
} else {
    vibrator?.vibrate(50)
}
```

- **What to replicate:**
  - Prefer a full-screen `Dialog` (not `ModalBottomSheet`) for the celebratory effect — but the dismiss/state pattern is identical.
  - Trigger the vibrator call inside a `LaunchedEffect(unlockEvent)` block so it fires once per unlock.
  - Queue handling: parent (e.g., `MainScreen.kt`) collects `unlockEvents: SharedFlow<UnlockEvent>` via `collectAsState()` or `LaunchedEffect + collect { ... }` into a `mutableStateListOf<UnlockEvent>()` buffer. Render current head; on dismiss, `removeAt(0)`.

---

### 17. `iosApp/iosApp/Views/Gamification/AchievementGalleryView.swift`

- **Role:** SwiftUI equivalent of `AchievementGalleryScreen.kt`. Reached from `SettingsView.swift` via `NavigationLink`.
- **Analog:** `iosApp/iosApp/Views/Exercises/ExerciseCatalogView.swift` (LazyVGrid + `@State` + `asyncSequence(for:)` observation). Also `iosApp/iosApp/Views/Overview/OverviewView.swift` (lines 36–60 show the `asyncSequence` flow observation pattern).
- **Excerpt to mirror (from OverviewView.swift):**

```swift
struct OverviewView: View {
    private let viewModel = KoinHelper.shared.getOverviewViewModel()
    @State private var muscleLoad: [String: SharedTrainingIntensity] = [:]

    var body: some View {
        ScrollView {
            VStack(spacing: 24) { ... }
        }
        .navigationTitle("Übersicht")
        .task { await observeUiState() }
    }

    private func observeUiState() async {
        do {
            for try await state in asyncSequence(for: viewModel.uiStateFlow) {
                isLoading = state.isLoading
                ...
            }
        } catch {
            print("Overview observation error: \(error)")
        }
    }
}
```

- **What to replicate:**
  - `KoinHelper.shared.getAchievementGalleryViewModel()` call pattern — requires adding a factory method to `KoinHelper.kt` (see MODIFY §4).
  - `LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 2))` inside `ScrollView`.
  - `@State private var uiState: AchievementGalleryUiState = ...` + `.task { await observeUiState() }` with `asyncSequence(for: viewModel.uiStateFlow)`.
  - Use SF Symbols for rank icons (trophy, medal, star) per Claude's discretion from CONTEXT D-19.

---

### 18. `iosApp/iosApp/Views/Gamification/UnlockModalView.swift`

- **Role:** iOS full-screen celebratory modal (D-19), triggered by observing `unlockEvents` SharedFlow from `GamificationViewModel` (or whichever VM owns it).
- **Analog:** `iosApp/iosApp/Views/Workout/WorkoutSessionView.swift` lines 420–440 (UINotificationFeedbackGenerator) + lines 690–712 (flow observation with haptic side-effect).
- **Excerpt to mirror (haptic + state observation):**

```swift
Button("Complete Set") {
    let generator = UINotificationFeedbackGenerator()
    generator.notificationOccurred(.success)
    viewModel.completeSet(...)
}

// state observation with haptic on state transition
for try await value in asyncSequence(for: viewModel.sessionStateFlow) {
    let newState = value
    if let active = newState as? WorkoutSessionState.Active {
        if active.restState is RestState.RestComplete && previousRestWasResting {
            let generator = UINotificationFeedbackGenerator()
            generator.prepare()
            generator.notificationOccurred(.success)
        }
    }
    self.sessionState = newState
}
```

- **What to replicate:**
  - Present via `.fullScreenCover(isPresented:)` at the `MainTabView` level so the modal overlays the current tab.
  - Haptic pattern identical to workout set completion — `UINotificationFeedbackGenerator().notificationOccurred(.success)` inside the `.onAppear` of the modal view.
  - Queue state: `@State private var pendingUnlocks: [UnlockEvent] = []` — on dismiss, pop index 0; only show modal when `pendingUnlocks.first != nil`.
  - Observe `unlockEvents` flow in the view owning the queue state — use `asyncSequence(for: viewModel.unlockEventsFlow)` with `for try await event in ... { pendingUnlocks.append(event) }`.

---

### 19. `iosApp/iosApp/Views/Overview/OverviewRankStrip.swift` *(new partial view)*

- **Role:** SwiftUI component rendering rank badge + XP progress bar + name (D-18). Added above the existing nutrition-goals section in `OverviewView.swift`.
- **Analog:** `iosApp/iosApp/Views/Nutrition/MacroRowView.swift` (simple card component with progress bars).
- **What to replicate:**
  - `struct OverviewRankStrip: View { let rankState: RankState ; var body: some View { HStack { ... } } }`.
  - Use `ProgressView(value: rankState.currentXp, total: rankState.nextRankThreshold)` for the progress-to-next hint.
  - Unranked copy: `"Unranked — complete a workout to unlock Silver"` (D-11 literal from CONTEXT specifics).

---

### 20. `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/OverviewRankStrip.kt` *(new composable, optional — could inline in OverviewScreen.kt)*

- **Role:** Compose equivalent of above.
- **Analog:** `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/MacroRow.kt`.
- **What to replicate:** Same structure as iOS — `@Composable fun OverviewRankStrip(rankState: RankState, modifier: Modifier = Modifier) { Row { ... LinearProgressIndicator(progress = ...) ... } }`.

---

## Files to MODIFY

---

### M-1. `shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt` — schema v7 → v8

- **Current state (lines 9–28):**

```kotlin
@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutTemplateEntity::class,
        TemplateExerciseEntity::class,
        ActiveSessionEntity::class,
        ActiveSessionSetEntity::class,
        CompletedWorkoutEntity::class,
        CompletedWorkoutExerciseEntity::class,
        CompletedWorkoutSetEntity::class,
        FoodEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        ConsumptionEntryEntity::class
    ],
    version = 7,
    autoMigrations = [
        AutoMigration(from = 6, to = 7)
    ]
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutTemplateDao(): WorkoutTemplateDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun completedWorkoutDao(): CompletedWorkoutDao
    abstract fun nutritionDao(): NutritionDao
}
```

- **What to change:**
  1. Add 3 entities to `entities` list: `XpLedgerEntity::class`, `AchievementStateEntity::class`, `RankStateEntity::class`.
  2. Bump `version = 7` → `version = 8`.
  3. Add `AutoMigration(from = 7, to = 8)` to the `autoMigrations` array — the existing `AutoMigration(6, 7)` on line 26 is the exact template.
  4. Add `abstract fun gamificationDao(): GamificationDao` next to the other DAO accessors.
- **Migration constraints:**
  - Non-destructive only (additive). All new columns on existing tables (none required by Phase 15) must have `@ColumnInfo(defaultValue = ...)` per the `CompletedWorkoutSetEntity.rir` pattern (line 25: `@ColumnInfo(defaultValue = "2") val rir: Int = 2`).
  - New tables need no migration metadata — Room AutoMigration creates them automatically.
  - Verify: `fallbackToDestructiveMigration(dropAllTables = true)` is set in `Database.android.kt` line 10 — this is a safety net during dev but MUST NOT be relied on. AutoMigration must succeed without falling back, or user data is lost.

---

### M-2. `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt` — retroactive sentinel flag

- **Current pattern to follow (lines 20–50):**

```kotlin
class SettingsRepository(
    private val dataStore: DataStore<Preferences>
) {
    private val weightUnitKey = stringPreferencesKey("weight_unit")
    private val appThemeKey = stringPreferencesKey("app_theme")
    ...

    val weightUnit: Flow<WeightUnit> = dataStore.data.map { preferences ->
        when (preferences[weightUnitKey]) {
            "LBS" -> WeightUnit.LBS
            else -> WeightUnit.KG
        }
    }

    suspend fun setWeightUnit(unit: WeightUnit) {
        dataStore.edit { preferences ->
            preferences[weightUnitKey] = unit.name
        }
    }
}
```

- **What to add:**
  - `import androidx.datastore.preferences.core.booleanPreferencesKey` at the top.
  - `private val retroactiveAppliedKey = booleanPreferencesKey("gamification_retroactive_applied")` in the key list.
  - `val retroactiveApplied: Flow<Boolean> = dataStore.data.map { it[retroactiveAppliedKey] ?: false }`.
  - `suspend fun setRetroactiveApplied(applied: Boolean) { dataStore.edit { it[retroactiveAppliedKey] = applied } }`.
- **Why this file:** D-13 explicitly calls out DataStore for the sentinel ("e.g., `gamification.retroactive_applied=true` in DataStore"). `SettingsRepository` already owns `DataStore<Preferences>` and has exactly this key-value pattern for every existing flag.

---

### M-3. `shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt` — Koin wiring

- **Current pattern (lines 57–115):**

```kotlin
val sharedModule = module {
    single<AppDatabase> { ... }
    single<ExerciseDao> { get<AppDatabase>().exerciseDao() }
    ...
    single<CompletedWorkoutDao> { get<AppDatabase>().completedWorkoutDao() }
    single<NutritionDao> { get<AppDatabase>().nutritionDao() }

    single<DatabaseSeeder> { DatabaseSeeder { readResourceFile("free_exercise_db.json") } }

    single<ExerciseRepository> { ExerciseRepositoryImpl(get(), get()) }
    single<WorkoutRepository> { WorkoutRepositoryImpl(get(), get()) }
    single<SettingsRepository> { SettingsRepository(get()) }

    single { NutritionDataSeeder(get<NutritionDao>()) }

    viewModel { OverviewViewModel(get(), get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get()) }
}
```

- **What to add (in order):**
  1. `single<GamificationDao> { get<AppDatabase>().gamificationDao() }` — after `NutritionDao` singleton (line 69).
  2. `single<GamificationRepository> { GamificationRepositoryImpl(get(), get()) }` — in the Repositories block (line 79).
  3. `single { AchievementStateSeeder(get<GamificationDao>()) }` — next to `NutritionDataSeeder` on line 84.
  4. `single { GamificationEngine(get(), get(), get(), get(), get(), get()) }` — new section between Nutrition Use Cases and ViewModels.
  5. `single { RetroactiveWalker(get(), get(), get(), get()) }` — next to engine.
  6. `viewModel { AchievementGalleryViewModel(get()) }` — in ViewModels block.
  7. `viewModel { GamificationViewModel(get()) }` — if planner creates it (§14).
  8. Update `viewModel { OverviewViewModel(...) }` ctor args — adds 1 more `get()` for `GamificationRepository` (depending on planner's VM decision from §14).
  9. Update `viewModel { WorkoutSessionViewModel(...) }` ctor args — adds 1 more `get()` for `GamificationEngine`.

---

### M-4. `shared/src/iosMain/kotlin/com/pumpernickel/di/KoinHelper.kt` — iOS VM factory

- **Current pattern (lines 18–45):**

```kotlin
object KoinHelper {
    fun getExerciseCatalogViewModel(): ExerciseCatalogViewModel =
        KoinPlatform.getKoin().get()

    fun getOverviewViewModel(): OverviewViewModel =
        KoinPlatform.getKoin().get()

    fun getSettingsViewModel(): SettingsViewModel =
        KoinPlatform.getKoin().get()
}
```

- **What to add:**
  - `fun getAchievementGalleryViewModel(): AchievementGalleryViewModel = KoinPlatform.getKoin().get()`.
  - `fun getGamificationViewModel(): GamificationViewModel = KoinPlatform.getKoin().get()` (if created per §14).
  - Add matching imports at top of file.

---

### M-5. `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt` — post-save unlock hook (D-20)

- **Current code (lines 535–585, `saveReviewedWorkout`):**

```kotlin
fun saveReviewedWorkout() {
    viewModelScope.launch {
        val reviewing = _sessionState.value as? WorkoutSessionState.Reviewing ?: return@launch
        // ... build completedWorkout ...
        workoutRepository.saveCompletedWorkout(completedWorkout)
        workoutRepository.clearActiveSession()
        _hasActiveSession.value = false
        val totalSets = completedExercises.sumOf { it.sets.size }
        _sessionState.value = WorkoutSessionState.Finished(...)
    }
}
```

- **What to change:**
  1. Add `private val gamificationEngine: GamificationEngine` to constructor params.
  2. After `workoutRepository.saveCompletedWorkout(completedWorkout)` and BEFORE `clearActiveSession()`, capture the workout id:

     ```kotlin
     workoutRepository.saveCompletedWorkout(completedWorkout)
     // Compute unlock effects post-save (D-20). Engine reads the just-saved
     // workout + PR pipeline + streak state; emits to its SharedFlow<UnlockEvent>
     // which MainScreen / MainTabView observes to present the queued modals.
     gamificationEngine.onWorkoutSaved()
     workoutRepository.clearActiveSession()
     ```

  3. `saveCompletedWorkout` in `WorkoutRepository` currently returns `Unit` — engine will need the saved workout id. Add `suspend fun saveCompletedWorkout(workout: CompletedWorkout): Long` (returning `workoutId`) and thread it to the engine call, OR have the engine read "latest workout" directly via `completedWorkoutDao.getAllWorkouts().first().first()`. Planner's choice — return-id is cleaner.

---

### M-6. `shared/src/commonMain/kotlin/com/pumpernickel/presentation/overview/OverviewViewModel.kt` — expose rank/XP state (D-18)

- **Current code (lines 80–100):**

```kotlin
class OverviewViewModel(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val settingsRepository: SettingsRepository,
    private val loadConsumptionsForDate: LoadConsumptionsForDateUseCase,
    private val calculateDailyMacros: CalculateDailyMacrosUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(OverviewUiState())

    @NativeCoroutinesState
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    @NativeCoroutinesState
    val nutritionGoals: StateFlow<NutritionGoals> = settingsRepository
        .nutritionGoals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NutritionGoals())
    ...
}
```

- **What to change:**
  - Add constructor param: `private val gamificationRepository: GamificationRepository`.
  - Add a new exposed `StateFlow<RankState>` via the existing `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ...)` idiom, exactly mirroring the `nutritionGoals` flow:

    ```kotlin
    @NativeCoroutinesState
    val rankState: StateFlow<RankState> = gamificationRepository
        .rankState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RankState.Unranked)
    ```
  - Do NOT fold rank into `OverviewUiState` — keep it a separate flow so the Android/iOS UI can collect it independently without rebuilding the muscle/macro state. This matches the `nutritionGoals` separate-flow precedent on line 93–96.

---

### M-7. `iosApp/iosApp/Views/Overview/OverviewView.swift` — insert rank strip above existing sections

- **Current structure (lines 15–28):**

```swift
var body: some View {
    ScrollView {
        VStack(spacing: 24) {
            muscleActivitySection
            nutritionRingsSection
        }
        .padding(.horizontal, 16)
        ...
    }
    .navigationTitle("Übersicht")
}
```

- **What to change:**
  - Add `@State private var rankState: RankState = ...` and observe `viewModel.rankStateFlow` in `.task { await observeRank() }` (mirror `observeUiState()` on line 41).
  - Insert `OverviewRankStrip(rankState: rankState)` as the first child of the `VStack`, above `muscleActivitySection`.
  - Per CONTEXT D-18: strip is NOT a navigation link (gallery lives under Settings per D-21).

---

### M-8. `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/OverviewScreen.kt` — insert rank strip

- **Current structure (lines 97–100):** `Column(modifier = Modifier.fillMaxSize().padding(padding)) { ... muscle activity + nutrition rings ... }`.
- **What to change:**
  - Collect rank flow alongside existing `uiState`: `val rankState by viewModel.rankState.collectAsState(initial = RankState.Unranked)`.
  - Insert `OverviewRankStrip(rankState)` composable at the top of the `Column` (or inline the HStack-equivalent `Row` directly).

---

### M-9. `iosApp/iosApp/Views/Settings/SettingsView.swift` — Achievements link (D-21)

- **Current structure (lines 12–78):**

```swift
var body: some View {
    NavigationStack {
        Form {
            Section("Appearance") { ... }
            Section("Accent Color") { ... }
            Section("Units") { ... }
        }
        .navigationTitle("Settings")
        ...
    }
}
```

- **What to change:**
  - Add a new `Section("Gamification") { NavigationLink("Achievements") { AchievementGalleryView() } }` before the `.navigationTitle` call. Existing sections follow that structure exactly.

---

### M-10. `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/SettingsSheet.kt` — Achievements link

- **Current structure (lines 46–156):** single `ModalBottomSheet` with inline-only settings.
- **What to change:**
  - Add `onNavigateToAchievements: () -> Unit` parameter to the composable signature.
  - Add a new section (after "Weight Unit" block at line 155) with a `ListItem` / `TextButton` → `"Achievements"` that calls `onDismiss()` then `onNavigateToAchievements()`.
  - `TemplateListScreen.kt` (line 61: `var showSettingsSheet by remember { mutableStateOf(false) }`) is where this sheet is wired — thread `onNavigateToAchievements = { navController.navigate(AchievementGalleryRoute) }` through.

---

### M-11. `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/Routes.kt` — new route

- **Current pattern:**

```kotlin
@Serializable data object WorkoutTabRoute
@Serializable data object TemplateListRoute
@Serializable data class TemplateEditorRoute(val templateId: Long? = null)
```

- **What to add:**
  - `@Serializable data object AchievementGalleryRoute` (no params).
  - Register `composable<AchievementGalleryRoute> { AchievementGalleryScreen(navController = workoutNavController) }` in `MainScreen.kt` inside the Workout-tab NavHost (since Settings is triggered from `TemplateListScreen` in the Workout tab — see MainScreen.kt line 97).

---

### M-12. `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt` / `iosApp/iosApp/Views/MainTabView.swift` — unlock modal host

- **Role:** Host the unlock-modal queue at the root level so modals can appear regardless of which tab the user is on when `saveReviewedWorkout` fires.
- **Android current state (MainScreen.kt):** Scaffold with bottom NavigationBar + per-tab NavHosts. No overlay slot.
- **iOS current state (MainTabView.swift):** TabView with three tabs. No overlay slot.
- **What to change:**
  - **Android:** Wrap the existing `Scaffold { ... }` in a `Box`, then after the `Scaffold`, add `UnlockModal(...)` collecting from a `GamificationViewModel: koinViewModel()` owned at this level. Pass `onDismiss = { vm.consumeUnlock() }`.
  - **iOS:** Add `@State private var pendingUnlocks: [UnlockEvent] = []` to `MainTabView` and `.fullScreenCover(isPresented: Binding( ... pendingUnlocks.first != nil ... )) { UnlockModalView(event: pendingUnlocks.first!, onDismiss: { pendingUnlocks.removeFirst() }) }`. Observe `unlockEventsFlow` via `.task`.

---

## Cross-cutting pattern excerpts

### Room AutoMigration convention (M-1)

From `AppDatabase.kt` lines 24–27:

```kotlin
version = 7,
autoMigrations = [
    AutoMigration(from = 6, to = 7)
]
```

And from `CompletedWorkoutSetEntity.kt` line 25:

```kotlin
@ColumnInfo(defaultValue = "2") val rir: Int = 2
```

**Rule:** every new column added to an existing entity needs `@ColumnInfo(defaultValue = ...)` or AutoMigration will fail at compile time. New entities (all three of ours) need no migration metadata — Room creates the tables automatically from the `@Entity` declarations when the `AutoMigration(7, 8)` runs.

---

### DataStore sentinel flag convention (M-2, D-13)

From `SettingsRepository.kt` — the `weightUnit` pattern is the canonical shape for a persisted flag with a Flow and a suspend setter:

```kotlin
private val weightUnitKey = stringPreferencesKey("weight_unit")

val weightUnit: Flow<WeightUnit> = dataStore.data.map { preferences ->
    when (preferences[weightUnitKey]) { "LBS" -> WeightUnit.LBS ; else -> WeightUnit.KG }
}

suspend fun setWeightUnit(unit: WeightUnit) {
    dataStore.edit { preferences -> preferences[weightUnitKey] = unit.name }
}
```

Use `booleanPreferencesKey("gamification_retroactive_applied")` for the Boolean sentinel.

---

### StateFlow exposure convention (M-6)

From `OverviewViewModel.kt` lines 93–96 — the canonical pattern for exposing a repo-backed Flow as an iOS-observable StateFlow:

```kotlin
@NativeCoroutinesState
val nutritionGoals: StateFlow<NutritionGoals> = settingsRepository
    .nutritionGoals
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NutritionGoals())
```

Mirror for `rankState`. The `@NativeCoroutinesState` annotation is **required** for iOS `asyncSequence(for:)` to work.

---

### Event dedupe key convention (XpLedgerEntity, GamificationDao)

Existing dedupe analogies in the codebase:

- `ActiveSessionEntity` uses `@PrimaryKey val id: Long = 1` as a singleton key.
- `CompletedWorkoutSetEntity` uses `(workoutExerciseId, setIndex)` implicitly (no unique index, but the app enforces via business logic).
- `NutritionDao` uses `OnConflictStrategy.REPLACE` on `insertFood` to upsert by `@PrimaryKey val id: String`.

**New pattern for ledger (not directly present in current codebase — introduce cleanly):**

```kotlin
@Entity(
    tableName = "xp_ledger",
    indices = [Index(value = ["source", "eventKey"], unique = true)]
)
data class XpLedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val eventKey: String,
    val xpAmount: Int,
    val awardedAtMillis: Long,
    val retroactive: Boolean
)

@Dao
interface GamificationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLedgerEntry(entry: XpLedgerEntity): Long   // returns -1 if dedupe'd
}
```

Standard event-key formats to document in the planning artifacts and wire into `GamificationEngine`:

| Source                | Event Key Format             |
|-----------------------|------------------------------|
| `workout`             | `workout:<workoutId>`        |
| `pr`                  | `pr:<exerciseId>:<workoutId>`|
| `nutrition_goal_day`  | `goalday:<YYYY-MM-DD>`       |
| `streak_workout`      | `streak:workout:<threshold>:<runStartEpochDay>` |
| `streak_nutrition`    | `streak:nutrition:<threshold>:<runStartEpochDay>` |
| `achievement`         | `achievement:<achievementId>`|

Including `runStartEpochDay` in streak keys prevents double-awarding the same threshold across two independent streak runs.

---

### Koin registration convention (M-3)

From `SharedModule.kt` lines 74–85:

```kotlin
// Repositories
single<ExerciseRepository> { ExerciseRepositoryImpl(get(), get()) }
single<TemplateRepository> { TemplateRepositoryImpl(get(), get()) }
single<WorkoutRepository> { WorkoutRepositoryImpl(get(), get()) }
single<SettingsRepository> { SettingsRepository(get()) }
single<FoodRepository> { FoodRepositoryImpl(get(), get()) }

// Nutrition: API + Seeder
single { createHttpClient() }
single { OpenFoodFactsApi(get()) }
single { NutritionDataSeeder(get<NutritionDao>()) }
```

And lines 104–108:

```kotlin
viewModel { WorkoutSessionViewModel(get(), get(), get()) }
viewModel { OverviewViewModel(get(), get(), get(), get(), get()) }
```

Each `get()` matches one constructor parameter in declaration order. When modifying a VM ctor, the count of `get()`s in the Koin `viewModel { ... }` must be updated to match.

---

### Haptic feedback convention (D-19, §16, §18)

- **iOS:** `UINotificationFeedbackGenerator().notificationOccurred(.success)` — used in `WorkoutSessionView.swift` line 421 (button tap) and line 701 (state transition). Prepare + fire pattern at line 701–703 is the cleanest.
- **Android:** `context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator` + `VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)` — used in `WorkoutSessionScreen.kt` line 384–391. SDK check required.

Fire the haptic inside the same block that presents the modal (on unlock-event arrival, not on modal dismiss).

---

### MuscleRegionPaths enumeration format (D-14 variety checks)

From `MuscleRegionPaths.kt`:

```kotlin
object MuscleRegionPaths {
    val frontRegions: List<MuscleRegionPath> = listOf(
        MuscleRegionPath(id = "neck-front-0", regionId = "neck", groupName = "neck", pathData = "...", side = AnatomySide.FRONT),
        MuscleRegionPath(id = "upper-trapezius-front-0", regionId = "upper-trapezius", groupName = "traps", ...),
        ...
    )
    val backRegions: List<MuscleRegionPath> = listOf( ... )
}

data class MuscleRegionPath(
    val id: String,
    val regionId: String,    // unique key per region (trainable unit)
    val groupName: String,   // e.g. "chest" (higher-level muscle group)
    val pathData: String,
    val side: AnatomySide
)
```

For achievement rules that check "trained all front regions" (D-14):

```kotlin
val allFrontRegionIds = MuscleRegionPaths.frontRegions.map { it.regionId }.toSet()
val allBackRegionIds  = MuscleRegionPaths.backRegions.map { it.regionId }.toSet()
```

Then cross-reference trained-region-ids tracked via `ExerciseEntity.primaryMuscles` (comma-separated `groupName` values — see `ExerciseEntity.kt` line 18). Note the join is `groupName`-to-`primaryMuscles`, not `regionId`-to-`primaryMuscles` — `regionId` is finer-grained than `groupName`. The planner must decide which granularity the achievement uses; recommended: `groupName` (coarser, matches how exercises tag muscles already).

---

### iOS `asyncSequence` Flow observation convention

From `FlowObservation.swift` (comment documenting the pattern) + `OverviewView.swift` line 41–60:

```swift
private func observeUiState() async {
    do {
        for try await state in asyncSequence(for: viewModel.uiStateFlow) {
            isLoading = state.isLoading
            ...
        }
    } catch {
        print("Overview observation error: \(error)")
    }
}

// Trigger from .task { await observeUiState() } in the view body
```

`viewModel.rankStateFlow` and `viewModel.unlockEventsFlow` are the generated Kotlin-side property names (from `@NativeCoroutinesState` / `@NativeCoroutines`) that iOS will see.

---

### Room singleton-row pattern (RankStateEntity)

From `ActiveSessionEntity.kt`:

```kotlin
@Entity(tableName = "active_sessions")
data class ActiveSessionEntity(
    @PrimaryKey val id: Long = 1,  // Singleton: at most one active session
    ...
)
```

And from `WorkoutSessionDao` (the upsert signature to find during planning):

```kotlin
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsertSession(session: ActiveSessionEntity)
```

Mirror exactly for `RankStateEntity` + `upsertRankState(state: RankStateEntity)` in `GamificationDao`.

---

### Settings sheet entry point summary

- **Android:** `TemplateListScreen.kt` line 75–82 (toolbar `IconButton` → `showSettingsSheet = true`) → `SettingsSheet.kt` composable. Thread the new `onNavigateToAchievements: () -> Unit` through.
- **iOS:** `TemplateListView.swift` line 38–43 (toolbar `Button` → `showSettings = true`) → `SettingsView.swift` via `.sheet(isPresented:)`. Inside `SettingsView`, add a `NavigationLink` to `AchievementGalleryView`.

---

*End of PATTERNS.md.*
