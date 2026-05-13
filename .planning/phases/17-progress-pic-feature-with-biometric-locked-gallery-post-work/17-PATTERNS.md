# Phase 17: Progress-pic feature with biometric-locked gallery — Pattern Map

**Mapped:** 2026-04-28
**Files analyzed:** 26 (NEW: 19, MODIFY: 7)
**Analogs found:** 25 / 26 (one file — `BiometricGate` — has a "no analog" partial match; closest is `createDataStore.{ios,android}.kt` for the expect/actual shape)

## File Classification

| File | NEW/MODIFY | Role | Data Flow | Closest Analog | Match Quality |
|------|------------|------|-----------|----------------|---------------|
| `shared/.../data/db/ProgressPictureEntity.kt` | NEW | Room entity | CRUD | `shared/.../data/db/CompletedWorkoutExerciseEntity.kt` | exact (FK + index) |
| `shared/.../data/db/ProgressPictureDao.kt` | NEW | Room DAO | CRUD | `shared/.../data/db/CompletedWorkoutDao.kt` | exact (Flow + suspend mix) |
| `shared/.../data/db/AppDatabase.kt` | MODIFY | DB schema | config | `shared/.../data/db/AppDatabase.kt` (current v8) | self (precedent v7→v8) |
| `shared/.../data/repository/ProgressPictureRepository.kt` | NEW | Repository | CRUD | `shared/.../data/repository/GamificationRepository.kt` | exact (interface + Impl) |
| `shared/.../domain/progresspic/PhotoVault.kt` | NEW | Service (expect) | file-I/O | `shared/.../data/preferences/createDataStore.kt` | partial (factory not class) |
| `shared/.../domain/progresspic/PhotoCaptureLauncher.kt` | NEW | Service (expect) | event-driven | `shared/.../data/preferences/createDataStore.kt` | partial (no camera precedent) |
| `shared/.../domain/progresspic/BiometricGate.kt` | NEW | Service (expect) | request-response | `shared/.../data/preferences/createDataStore.kt` | partial (expect shape only) |
| `shared/.../presentation/progresspic/ProgressGalleryViewModel.kt` | NEW | ViewModel | streaming | `shared/.../presentation/gamification/AchievementGalleryViewModel.kt` | exact |
| `shared/.../presentation/progresspic/ProgressViewerViewModel.kt` | NEW | ViewModel | streaming | `shared/.../presentation/gamification/AchievementGalleryViewModel.kt` | role-match |
| `shared/.../presentation/progresspic/ProgressPicturePromptViewModel.kt` | NEW | ViewModel | event-driven | `shared/.../presentation/gamification/GamificationViewModel.kt` | role-match |
| `shared/.../di/SharedModule.kt` (or new `ProgressGalleryModule.kt`) | MODIFY/NEW | DI config | config | `shared/.../di/AchievementGalleryModule.kt` | exact |
| `shared/src/androidMain/.../progresspic/PhotoVault.android.kt` | NEW | Service (actual) | file-I/O | `shared/src/androidMain/.../createDataStore.android.kt` | role-match |
| `shared/src/androidMain/.../progresspic/PhotoCaptureLauncher.android.kt` | NEW | Service (actual) | event-driven | `shared/src/androidMain/.../createDataStore.android.kt` | partial (no camera analog) |
| `shared/src/androidMain/.../progresspic/BiometricGate.android.kt` | NEW | Service (actual) | request-response | `shared/src/androidMain/.../createDataStore.android.kt` | partial (no biometric analog) |
| `androidApp/.../ui/navigation/Routes.kt` | MODIFY | Routes | config | `androidApp/.../ui/navigation/Routes.kt` (self) | self |
| `androidApp/.../ui/navigation/MainScreen.kt` | MODIFY | Navigation | config | `androidApp/.../ui/navigation/MainScreen.kt` (self, Overview tab block) | self |
| `androidApp/.../ui/screens/OverviewScreen.kt` | MODIFY | Compose screen | streaming | `androidApp/.../ui/screens/OverviewScreen.kt` (self, banner + rank-strip pattern) | self |
| `androidApp/.../ui/screens/ProgressGalleryScreen.kt` | NEW | Compose screen | streaming | `androidApp/.../ui/screens/AchievementGalleryScreen.kt` | exact (LazyVerticalGrid) |
| `androidApp/.../ui/screens/ProgressViewerScreen.kt` | NEW | Compose screen | request-response | `androidApp/.../ui/screens/AchievementGalleryScreen.kt` | partial (HorizontalPager not seen) |
| `androidApp/.../ui/screens/WorkoutSessionScreen.kt` | MODIFY | Compose screen | event-driven | `androidApp/.../ui/screens/WorkoutSessionScreen.kt` (self, FinishedContent line 1059) | self |
| `androidApp/.../ui/components/ProgressPicturePromptCard.kt` | NEW | Compose component | event-driven | `androidApp/.../ui/screens/OverviewScreen.kt` `NutritionGoalsBanner` (line 561) | role-match |
| `androidApp/.../AndroidManifest.xml` | MODIFY | config | config | `androidApp/.../AndroidManifest.xml` (self) | self |
| `androidApp/.../res/xml/backup_rules.xml` | NEW | config | config | (no Android XML resource analog in repo) | none |
| `androidApp/.../res/xml/data_extraction_rules.xml` | NEW | config | config | (no Android XML resource analog in repo) | none |
| `androidApp/build.gradle.kts` | MODIFY | build | config | `androidApp/build.gradle.kts` (self, camerax dep precedent) | self |
| `shared/src/iosMain/.../progresspic/PhotoVault.ios.kt` | NEW | Service (actual) | file-I/O | `shared/src/iosMain/.../createDataStore.ios.kt` | exact (NSFileManager URL pattern) |
| `shared/src/iosMain/.../progresspic/PhotoCaptureLauncher.ios.kt` | NEW | Service (actual) | event-driven | (no iOS camera/picker analog in shared) | none |
| `shared/src/iosMain/.../progresspic/BiometricGate.ios.kt` | NEW | Service (actual) | request-response | `shared/src/iosMain/.../createDataStore.ios.kt` | partial (expect/actual shape only) |
| `shared/src/iosMain/.../di/ProgressGalleryKoinHelper.kt` | NEW | DI bridge | config | `shared/src/iosMain/.../di/AchievementGalleryKoinHelper.kt` | exact |
| `shared/src/iosMain/.../di/ProgressViewerKoinHelper.kt` | NEW | DI bridge | config | `shared/src/iosMain/.../di/RanksAndAchievementsKoinHelper.kt` | exact |
| `iosApp/iosApp/Info.plist` | MODIFY | config | config | `iosApp/iosApp/Info.plist` (self) | self |
| `shared/.../presentation/workout/WorkoutSessionViewModel.kt` | MODIFY | ViewModel | event-driven | self (Finished data class line 50) | self |
| `.planning/phases/17-.../17-IOS-HANDOFF.md` | NEW | doc | doc | Phase 15.1 IOS-HANDOFF (per CONTEXT D-151-16) | (precedent only) |

---

## Pattern Assignments

### `shared/src/commonMain/kotlin/com/pumpernickel/data/db/ProgressPictureEntity.kt` (NEW — Room entity, CRUD)

**Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/data/db/CompletedWorkoutExerciseEntity.kt` (lines 1-25). This is the closest analog — same `ForeignKey(parent=CompletedWorkoutEntity, child=workoutId, CASCADE)` + `Index("workoutId")` shape that Phase 17 needs.

**Imports + entity declaration to copy** (lines 1-25):
```kotlin
package com.pumpernickel.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "completed_workout_exercises",
    foreignKeys = [ForeignKey(
        entity = CompletedWorkoutEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("workoutId")]
)
data class CompletedWorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: String,
    val exerciseName: String,
    val exerciseOrder: Int
)
```

**Adaptation for Phase 17:** Same FK/Index block. Differences from the analog: `@PrimaryKey val id: String` (UUID, not autoGenerate), additional `relativePath: String`, `capturedAtMillis: Long`, `sortOrder: Int` columns per CONTEXT D-17-08. Table name `progress_pictures`.

**Secondary reference for `@ColumnInfo(defaultValue = "0")`** on bool/int columns: `XpLedgerEntity.kt` line 18 uses `@ColumnInfo(defaultValue = "0") val retroactive: Boolean = false` — useful pattern if the planner decides to attach defaults for additive AutoMigration safety (not strictly required for an all-new table).

---

### `shared/src/commonMain/kotlin/com/pumpernickel/data/db/ProgressPictureDao.kt` (NEW — Room DAO, CRUD)

**Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/data/db/CompletedWorkoutDao.kt` (lines 1-65). Mixes `@Insert` + `@Query` (suspend and `Flow`-returning) on a workout-scoped child table — exactly the shape needed for `progress_pictures`.

**Imports + DAO declaration** (lines 1-10):
```kotlin
package com.pumpernickel.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CompletedWorkoutDao {
    @Insert
    suspend fun insertWorkout(workout: CompletedWorkoutEntity): Long
```

**Suspend single-row insert** (lines 11-12 — apply for `insertProgressPicture(...)`):
```kotlin
@Insert
suspend fun insertWorkout(workout: CompletedWorkoutEntity): Long
```

**Bulk insert pattern** (lines 16-17 — for "save all photos for a workout in one tx"):
```kotlin
@Insert
suspend fun insertSets(sets: List<CompletedWorkoutSetEntity>)
```

**Flow query for grouping/list view** (lines 19-20 — apply for "all workouts that have ≥1 photo"):
```kotlin
@Query("SELECT * FROM completed_workouts ORDER BY startTimeMillis DESC")
fun getAllWorkouts(): Flow<List<CompletedWorkoutEntity>>
```

**Suspend query by FK** (line 34-35 — apply for `getPicturesForWorkout(workoutId)`):
```kotlin
@Query("SELECT * FROM completed_workout_exercises WHERE workoutId = :workoutId ORDER BY exerciseOrder ASC")
suspend fun getExercisesForWorkout(workoutId: Long): List<CompletedWorkoutExerciseEntity>
```

**JOIN/aggregation pattern for tile-list query** (lines 22-32 — apply for "workouts with ≥1 photo + cover photo + volume + PR count"):
```kotlin
@Query("""
    SELECT w.id, w.templateId, w.name, w.startTimeMillis, w.durationMillis,
           COUNT(DISTINCT e.id) AS exerciseCount,
           COALESCE(SUM(CAST(s.actualReps AS INTEGER) * CAST(s.actualWeightKgX10 AS INTEGER)), 0) AS totalVolume
    FROM completed_workouts w
    LEFT JOIN completed_workout_exercises e ON e.workoutId = w.id
    LEFT JOIN completed_workout_sets s ON s.workoutExerciseId = e.id
    GROUP BY w.id
    ORDER BY w.startTimeMillis DESC
""")
fun getWorkoutSummaries(): Flow<List<WorkoutSummaryDto>>
```

**Adaptation for Phase 17:** A new DTO (e.g. `ProgressGalleryTileDto`) returning `(workoutId, name, startTimeMillis, durationMillis, totalVolumeKgX10, coverRelativePath, photoCount)` joined to `progress_pictures` with `WHERE EXISTS (SELECT 1 FROM progress_pictures p WHERE p.workoutId = w.id)` and a sub-select for the cover photo (`MAX(capturedAtMillis)` per CONTEXT D-17-11). PR count comes from a separate `xp_ledger` query (see `GamificationDao.getPrLedgerEntriesForWorkout` line 67) or is derived in the repository — Claude's discretion per CONTEXT.

**Delete patterns** (DAO does not currently have delete methods — borrow Room standard syntax):
```kotlin
@Query("DELETE FROM progress_pictures WHERE id = :id")
suspend fun deleteById(id: String)

@Query("DELETE FROM progress_pictures WHERE workoutId = :workoutId")
suspend fun deleteByWorkoutId(workoutId: Long)
```

---

### `shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt` (MODIFY — bump v8 → v9)

**Analog:** the same file (self-precedent — Phase 15 added `AutoMigration(7, 8)` and Phase pre-15 added `AutoMigration(6, 7)`).

**Current state to extend** (lines 9-32):
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
        ConsumptionEntryEntity::class,
        XpLedgerEntity::class,
        AchievementStateEntity::class,
        RankStateEntity::class
    ],
    version = 8,
    autoMigrations = [
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8)
    ]
)
```

**Required edits for Phase 17:**
- Add `ProgressPictureEntity::class` to the `entities` array.
- Bump `version = 8` to `version = 9`.
- Add `AutoMigration(from = 8, to = 9)` to the `autoMigrations` array.
- Add `abstract fun progressPictureDao(): ProgressPictureDao` after line 40 (`gamificationDao()`).

---

### `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ProgressPictureRepository.kt` (NEW — Repository, CRUD)

**Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/GamificationRepository.kt`. Same `interface ... + class ...Impl(private val dao: ...)` + Flow-on-read / suspend-on-write shape. Includes mapper extension functions at the bottom.

**Imports + interface preamble** (lines 1-22):
```kotlin
package com.pumpernickel.data.repository

import com.pumpernickel.data.db.AchievementStateEntity
import com.pumpernickel.data.db.GamificationDao
// ...
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Data-layer repository for gamification state. Read-side exposes Flow<domain>,
 * write-side is suspend. ...
 */
interface GamificationRepository {
    val totalXp: Flow<Long>
    val rankState: Flow<RankState>
```

**Suspend write method shape** (lines 31-37):
```kotlin
suspend fun awardXp(
    source: String,
    eventKey: String,
    amount: Int,
    awardedAtMillis: Long,
    retroactive: Boolean = false
): Boolean
```

**Impl wrapper for DAO calls** (lines 67-99):
```kotlin
class GamificationRepositoryImpl(
    private val dao: GamificationDao
) : GamificationRepository {

    override val totalXp: Flow<Long> = dao.totalXpFlow()

    override suspend fun awardXp(
        source: String,
        eventKey: String,
        amount: Int,
        awardedAtMillis: Long,
        retroactive: Boolean
    ): Boolean {
        val entry = XpLedgerEntity(
            source = source,
            eventKey = eventKey,
            xpAmount = amount,
            awardedAtMillis = awardedAtMillis,
            retroactive = retroactive
        )
        val rowId = dao.insertLedgerEntry(entry)
        return rowId != -1L
    }
```

**Mapper at bottom of file** (lines 145-163):
```kotlin
// ----- Mappers -----

private fun RankStateEntity?.toDomain(totalXpFromLedger: Long): RankState {
    if (this == null || this.isUnranked) return RankState.Unranked
    // ...
}
```

**Adaptation for Phase 17:** `ProgressPictureRepository` interface should expose:
- `fun observeGalleryTiles(): Flow<List<ProgressGalleryTile>>` — combines DAO tile DTO with `PhotoVault` path resolution.
- `fun observePicturesForWorkout(workoutId: Long): Flow<List<ProgressPicture>>`.
- `suspend fun savePicture(workoutId: Long, bytes: ByteArray, capturedAtMillis: Long): ProgressPicture` — calls `PhotoVault.write(...)` then DAO `insert`.
- `suspend fun deletePicture(id: String)` — DAO row delete + `PhotoVault.delete(id)`.
- `suspend fun deleteForWorkout(workoutId: Long)` — used by cascade-delete cleanup per CONTEXT D-17-09.

The Impl takes `(private val dao: ProgressPictureDao, private val vault: PhotoVault)`. Mapper extensions at the bottom convert `ProgressPictureEntity` → `ProgressPicture` domain model.

---

### `shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.kt` (NEW — `expect class`, file-I/O)

**Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/data/preferences/createDataStore.kt` (lines 1-13) for the **shape of the expect/actual indirection** + `shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt` line 44 (`expect object AppDatabaseConstructor`) for the `expect` declaration syntax. **No exact analog** for a multi-method file-I/O service — this file pioneers the pattern.

**Existing `expect` declarations to mirror** (`AppDatabase.kt` line 43-44):
```kotlin
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
```

**`createDataStore.kt` factory + path-resolution-by-platform shape** (lines 1-13):
```kotlin
package com.pumpernickel.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import okio.Path.Companion.toPath

fun createDataStore(producePath: () -> String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { producePath().toPath() }
    )

internal const val DATA_STORE_FILE_NAME = "pumpernickel_settings.preferences_pb"
```

**Adaptation for Phase 17:** `PhotoVault` should be `expect class PhotoVault` with suspending methods:
```kotlin
expect class PhotoVault {
    suspend fun write(id: String, bytes: ByteArray): String  // returns relativePath
    suspend fun read(relativePath: String): ByteArray?
    suspend fun delete(relativePath: String)
    suspend fun deleteAll(relativePaths: List<String>)
}
```

The class form (not factory) follows `AppDatabaseConstructor` precedent and lets Koin construct it as a `single<PhotoVault> { ... }` per platform. CONTEXT D-17-05 locks the relative-path string format `progress_pics/{uuid}.jpg`; the actual class resolves against `<filesDir>` (Android) / `<Documents>` (iOS).

---

### `shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.kt` (NEW — `expect class`, event-driven)

**Analog (partial — no camera precedent):** `createDataStore.kt` for the expect/actual shape only. The class needs a callback-based or `suspend`-returning API to bridge to platform-specific async camera/picker UIs.

**Recommended shape (no exact analog — derived from CONTEXT D-17-03):**
```kotlin
expect class PhotoCaptureLauncher {
    /**
     * Opens the system camera. Returns the captured JPEG bytes (already
     * resized to 1600 long edge per D-17-07) or null if the user cancelled.
     */
    suspend fun captureFromCamera(): ByteArray?

    /**
     * Opens the system photo picker. Returns the chosen JPEG bytes
     * (resized) or null if cancelled.
     */
    suspend fun pickFromLibrary(): ByteArray?
}
```

**Note:** Android implementation will need an Activity / ActivityResult contract bridge (executor passes the current `ComponentActivity` via Koin or via a per-call hook on the screen). iOS implementation uses `UIImagePickerController` / `PHPickerViewController` wrapped in `suspendCancellableCoroutine`. Specifics are deferred to platform actuals — see plan-level discretion in CONTEXT.

---

### `shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.kt` (NEW — `expect class`, request-response)

**Analog (partial — no biometric precedent):** Same expect/actual shape as `PhotoVault`/`PhotoCaptureLauncher`. No existing OS-level auth call in the codebase.

**Recommended shape (derived from CONTEXT D-17-15 / D-17-16 / D-17-17):**
```kotlin
sealed class UnlockResult {
    data object Success : UnlockResult()
    data object Cancelled : UnlockResult()      // user backed out
    data object Failed : UnlockResult()         // biometric mismatch
    data class Error(val message: String) : UnlockResult()
}

expect class BiometricGate {
    /**
     * Prompts for OS-level auth (biometric + passcode fallback per D-17-15).
     * On a no-credential device, resolves to Success without challenge
     * (D-17-16). Reason string is shown in the system prompt.
     */
    suspend fun requestUnlock(reason: String): UnlockResult
}
```

---

### `shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressGalleryViewModel.kt` (NEW — ViewModel, streaming)

**Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/presentation/gamification/AchievementGalleryViewModel.kt` (lines 1-69). Identical role: gallery-list VM that maps a Flow from a repository to a `UiState` data class with `isLoading` + grouped tiles, exposed via `@NativeCoroutinesState val uiState: StateFlow<...>`.

**Imports + UiState/Tile data classes** (lines 1-30):
```kotlin
package com.pumpernickel.presentation.gamification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.data.repository.GamificationRepository
import com.pumpernickel.domain.gamification.AchievementCatalog
import com.pumpernickel.domain.gamification.AchievementProgress
import com.pumpernickel.domain.gamification.Category
import com.pumpernickel.domain.gamification.Tier
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AchievementGalleryUiState(
    val tilesByCategory: Map<Category, List<AchievementTile>> = emptyMap(),
    val isLoading: Boolean = true
)

data class AchievementTile(
    val id: String,
    val displayName: String,
    val flavourCopy: String,
    val category: Category,
    val tier: Tier,
    val threshold: Long,
    val currentProgress: Long,
    val unlockedAtMillis: Long?
)
```

**ViewModel + StateFlow wiring** (lines 32-44):
```kotlin
class AchievementGalleryViewModel(
    private val gamificationRepository: GamificationRepository
) : ViewModel() {

    @NativeCoroutinesState
    val uiState: StateFlow<AchievementGalleryUiState> = gamificationRepository
        .achievements
        .map { progressRows -> buildUiState(progressRows) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AchievementGalleryUiState(isLoading = true)
        )
```

**Adaptation for Phase 17:**
- `ProgressGalleryViewModel(private val repo: ProgressPictureRepository, private val biometricGate: BiometricGate)`.
- `data class ProgressGalleryUiState(val tiles: List<ProgressTile> = emptyList(), val isLoading: Boolean = true)`.
- `data class ProgressTile(val workoutId: Long, val workoutName: String, val dateLabel: String, val volumeKgLabel: String, val prCount: Int, val isGoalDay: Boolean, val coverRelativePath: String, val photoCount: Int)`.
- `fun onTileTapped(workoutId: Long)` — `viewModelScope.launch { when (biometricGate.requestUnlock(...)) { Success -> _navEvent.emit(NavToViewer(workoutId)); else -> {} } }` per CONTEXT D-17-14 / D-17-17.
- One-shot nav events via `MutableSharedFlow<NavEvent>` annotated `@NativeCoroutines` (see `GamificationViewModel.unlockEvents` line 53-54 below for shape).

---

### `shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressViewerViewModel.kt` (NEW — ViewModel, streaming)

**Analog:** Same as `ProgressGalleryViewModel` — `AchievementGalleryViewModel`. Role-match: takes a constructor argument (`workoutId: Long`), exposes a `Flow<List<ProgressPicture>>` for the carousel, plus a `delete(id: String)` action.

**Constructor with parameter (analog: `WorkoutHistoryDetailScreen` indirectly via `loadWorkoutDetail(workoutId)`):** Phase 17 shared VM should accept `workoutId: Long` directly via Koin's `parametersOf` — the planner can pass it like `koinViewModel { parametersOf(workoutId) }` on Android and via a KoinHelper that takes a `Long` on iOS. Pattern reference: `GamificationViewModel.unlockEvents` shape (line 53-54 below) for one-shot delete confirmation events:

```kotlin
/**
 * One-shot unlock events for the modal queue. Not a StateFlow — each
 * event fires exactly once when the engine detects a promotion or
 * achievement tier unlock. ...
 */
@NativeCoroutines
val unlockEvents: SharedFlow<UnlockEvent> = gamificationEngine.unlockEvents
```

**Adaptation for Phase 17:** Expose `uiState: StateFlow<ProgressViewerUiState>` (with `pictures: List<ProgressPicture>`, `isLoading`, optional `error`), plus a `fun delete(id: String)` that calls `repo.deletePicture(id)` and re-emits.

---

### `shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressPicturePromptViewModel.kt` (NEW — ViewModel, event-driven)

**Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/presentation/gamification/GamificationViewModel.kt` (lines 26-55). Small event-driven VM with both a `StateFlow` (current photoCount) and a `SharedFlow` for one-shot UI events ("photo saved", "capture failed").

**VM shell** (lines 26-55):
```kotlin
class GamificationViewModel(
    private val gamificationRepository: GamificationRepository,
    private val gamificationEngine: GamificationEngine
) : ViewModel() {

    @NativeCoroutinesState
    val rankState: StateFlow<RankState> = gamificationRepository
        .rankState
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            RankState.Unranked
        )

    @NativeCoroutines
    val unlockEvents: SharedFlow<UnlockEvent> = gamificationEngine.unlockEvents
}
```

**Adaptation for Phase 17:** `ProgressPicturePromptViewModel(private val repo: ProgressPictureRepository, private val launcher: PhotoCaptureLauncher)`:
- `@NativeCoroutinesState val photoCount: StateFlow<Int>` (count for the just-saved workout, observed via `repo.observePicturesForWorkout(workoutId).map { it.size }`).
- `fun setWorkoutId(id: Long)` — the prompt screen calls this after `WorkoutSessionState.Finished` renders.
- `fun captureFromCamera()` / `fun pickFromLibrary()` — `viewModelScope.launch { launcher.captureFromCamera()?.let { repo.savePicture(workoutId, it, ...) } }`.
- Per CONTEXT line 198, planner may instead fold this into `WorkoutSessionViewModel`. Either is acceptable.

---

### `shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt` or new `ProgressGalleryModule.kt` (MODIFY/NEW — DI config)

**Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/di/AchievementGalleryModule.kt` (lines 1-9, complete file).

**Whole file to copy** (1-9):
```kotlin
package com.pumpernickel.di

import com.pumpernickel.presentation.gamification.AchievementGalleryViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val achievementGalleryModule = module {
    viewModel { AchievementGalleryViewModel(get()) }
}
```

**Mounting in `SharedModule.kt`** (lines 60-65):
```kotlin
val sharedModule = module {
    // Gamification feature modules (plan 03) -- mounted here once; each plan
    // adds its bindings to its own feature module file.
    includes(
        gamificationModule,
        gamificationEngineModule,
        gamificationUiModule,
        achievementGalleryModule
    )
```

**DAO binding pattern** (line 77):
```kotlin
single<CompletedWorkoutDao> { get<AppDatabase>().completedWorkoutDao() }
```

**Repository binding** (lines 84-86):
```kotlin
single<ExerciseRepository> { ExerciseRepositoryImpl(get(), get()) }
single<TemplateRepository> { TemplateRepositoryImpl(get(), get()) }
single<WorkoutRepository> { WorkoutRepositoryImpl(get(), get()) }
```

**Adaptation for Phase 17:** Create `ProgressGalleryModule.kt` (parity with `AchievementGalleryModule`) containing:
```kotlin
val progressGalleryModule = module {
    single<ProgressPictureDao> { get<AppDatabase>().progressPictureDao() }
    single<PhotoVault> { PhotoVault() }              // platform-specific, but no ctor params per analog
    single<BiometricGate> { BiometricGate() }
    single<PhotoCaptureLauncher> { PhotoCaptureLauncher() }
    single<ProgressPictureRepository> { ProgressPictureRepositoryImpl(get(), get()) }
    viewModel { ProgressGalleryViewModel(get(), get()) }
    viewModel { (workoutId: Long) -> ProgressViewerViewModel(workoutId, get()) }
    viewModel { ProgressPicturePromptViewModel(get(), get()) }
}
```
Then add `progressGalleryModule` to the `includes(...)` list at line 60-65 of `SharedModule.kt`.

**Note on platform-specific DI for `PhotoVault`/`PhotoCaptureLauncher`/`BiometricGate`:** if the actual class needs platform context (Android `Context`, iOS no-arg), follow the `PlatformModule.android.kt` precedent (lines 13-16):
```kotlin
actual val platformModule: Module = module {
    single<RoomDatabase.Builder<AppDatabase>> { getDatabaseBuilder(androidContext()) }
    single<DataStore<Preferences>> { createDataStoreAndroid(get()) }
}
```
Move the platform-context-needing bindings (`PhotoVault.android`, `PhotoCaptureLauncher.android`, `BiometricGate.android`) into `PlatformModule.android.kt` and the iOS no-arg ones into `PlatformModule.ios.kt`. Common-side `progressGalleryModule` then just does `single<ProgressPictureRepository> { ... }` and `viewModel { ... }` declarations.

---

### `shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.android.kt` (NEW — actual, file-I/O)

**Analog:** `shared/src/androidMain/kotlin/com/pumpernickel/data/preferences/createDataStore.android.kt` (lines 1-12).

**Whole file to copy** (1-12):
```kotlin
package com.pumpernickel.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

fun createDataStoreAndroid(context: Context): DataStore<Preferences> = createDataStore(
    producePath = {
        context.filesDir.resolve(DATA_STORE_FILE_NAME).absolutePath
    }
)
```

**Adaptation for Phase 17:**
```kotlin
package com.pumpernickel.domain.progresspic

import android.content.Context
import java.io.File

actual class PhotoVault(private val context: Context) {
    private val rootDir: File by lazy {
        File(context.filesDir, "progress_pics").apply { mkdirs() }
    }

    actual suspend fun write(id: String, bytes: ByteArray): String {
        val file = File(rootDir, "$id.jpg")
        file.writeBytes(bytes)
        return "progress_pics/$id.jpg"
    }
    actual suspend fun read(relativePath: String): ByteArray? {
        val file = File(context.filesDir, relativePath)
        return if (file.exists()) file.readBytes() else null
    }
    actual suspend fun delete(relativePath: String) {
        File(context.filesDir, relativePath).delete()
    }
    actual suspend fun deleteAll(relativePaths: List<String>) {
        relativePaths.forEach { delete(it) }
    }
}
```
Use `Dispatchers.IO` via repository or `withContext(Dispatchers.IO) { ... }` inside each method (planner discretion). The `context.filesDir` resolution is the exact same pattern as line 9 of the analog.

---

### `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.ios.kt` (NEW — actual, file-I/O)

**Analog:** `shared/src/iosMain/kotlin/com/pumpernickel/data/preferences/createDataStore.ios.kt` (lines 1-23). Exact match — same `NSDocumentDirectory + NSFileManager` URL resolution.

**Whole file to copy** (1-23):
```kotlin
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.pumpernickel.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

fun createDataStoreIos(): DataStore<Preferences> = createDataStore(
    producePath = {
        val directory = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null
        )
        requireNotNull(directory).path + "/$DATA_STORE_FILE_NAME"
    }
)
```

**Adaptation for Phase 17:**
- Same `@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)` header.
- Same `NSFileManager.defaultManager.URLForDirectory(NSDocumentDirectory, ...)` to find the Documents root.
- After `mkdirs()` for `progress_pics/`, write each photo with `NSData.dataWithBytes(...)` → `writeToFile(path, options=NSDataWritingFileProtectionComplete, error=...)` per CONTEXT D-17-06 (`NSFileProtectionComplete` flag). Use `NSURL.setResourceValue(true, forKey=NSURLIsExcludedFromBackupKey, error=...)` after each write to mark the file out of iCloud backup.

---

### `shared/src/iosMain/kotlin/com/pumpernickel/di/ProgressGalleryKoinHelper.kt` (NEW — DI bridge)

**Analog:** `shared/src/iosMain/kotlin/com/pumpernickel/di/AchievementGalleryKoinHelper.kt` (lines 1-9, complete file).

**Whole file to copy** (1-9):
```kotlin
package com.pumpernickel.di

import com.pumpernickel.presentation.gamification.AchievementGalleryViewModel
import org.koin.mp.KoinPlatform

class AchievementGalleryKoinHelper {
    fun getAchievementGalleryViewModel(): AchievementGalleryViewModel =
        KoinPlatform.getKoin().get()
}
```

**Adaptation for Phase 17:**
```kotlin
package com.pumpernickel.di

import com.pumpernickel.presentation.progresspic.ProgressGalleryViewModel
import org.koin.mp.KoinPlatform

class ProgressGalleryKoinHelper {
    fun getProgressGalleryViewModel(): ProgressGalleryViewModel =
        KoinPlatform.getKoin().get()
}
```

---

### `shared/src/iosMain/kotlin/com/pumpernickel/di/ProgressViewerKoinHelper.kt` (NEW — DI bridge)

**Analog:** `shared/src/iosMain/kotlin/com/pumpernickel/di/RanksAndAchievementsKoinHelper.kt` (lines 1-17). Notes "one helper per VM, no caching" — the exact convention CONTEXT D-151-10 codifies and Phase 17 inherits.

**Whole file to copy** (1-17):
```kotlin
package com.pumpernickel.di

import com.pumpernickel.presentation.gamification.RanksAndAchievementsViewModel
import org.koin.mp.KoinPlatform

/**
 * iOS-side factory for the phase-15.1 rank ladder VM.
 * Swift callers: `RanksAndAchievementsKoinHelper().getRanksAndAchievementsViewModel()`.
 * Mirrors the Phase 15 one-helper-per-VM convention (GamificationUiKoinHelper,
 * AchievementGalleryKoinHelper) per D-151-10. Class, not object; no params;
 * no caching; one getter.
 */
class RanksAndAchievementsKoinHelper {
    fun getRanksAndAchievementsViewModel(): RanksAndAchievementsViewModel =
        KoinPlatform.getKoin().get()
}
```

**Adaptation for Phase 17:** `ProgressViewerKoinHelper` takes a `Long workoutId` and resolves via `parametersOf`:
```kotlin
class ProgressViewerKoinHelper {
    fun getProgressViewerViewModel(workoutId: Long): ProgressViewerViewModel =
        KoinPlatform.getKoin().get { parametersOf(workoutId) }
}
```

---

### `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/Routes.kt` (MODIFY — add route)

**Analog:** `Routes.kt` itself — `@Serializable data object/data class` pattern (lines 6-35).

**Existing patterns** (lines 28-35):
```kotlin
// Gamification
@Serializable data object AchievementGalleryRoute

// Overview tab — Ranks & Achievements Browser (Phase 15.1)
@Serializable data object OverviewRootRoute
@Serializable data object RanksAndAchievementsRoute

// Overview tab — Nutrition Goals Editor (Phase 16)
@Serializable data object NutritionGoalsEditorRoute
```

**Adaptation for Phase 17:** Append:
```kotlin
// Overview tab — Progress gallery (Phase 17)
@Serializable data object ProgressGalleryRoute
@Serializable data class ProgressViewerRoute(val workoutId: Long)
```
The `data class WorkoutHistoryDetailRoute(val workoutId: Long)` at line 19 is the precedent for the `Long`-parameterised viewer route.

---

### `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt` (MODIFY — wire NavHost)

**Analog:** Same file — Overview-tab NavHost block (lines 163-181).

**Existing pattern to extend** (lines 163-181):
```kotlin
1 -> NavHost(
    navController = overviewNavController,
    startDestination = OverviewRootRoute
) {
    composable<OverviewRootRoute> {
        OverviewScreen(navController = overviewNavController)
    }
    composable<RanksAndAchievementsRoute> {
        RankLadderScreen(navController = overviewNavController)
    }
    composable<AchievementGalleryRoute> {
        // D-151-15: duplicate registration on the Overview tab for its own
        // back stack. The workout-tab registration at ~line 155 stays.
        AchievementGalleryScreen(navController = overviewNavController)
    }
    composable<NutritionGoalsEditorRoute> {
        NutritionGoalsEditorScreen(navController = overviewNavController)
    }
}
```

**Parameterised `composable` with `toRoute<>()`** (lines 151-156, for `ProgressViewerRoute`):
```kotlin
composable<WorkoutHistoryDetailRoute> { backStackEntry ->
    val route = backStackEntry.toRoute<WorkoutHistoryDetailRoute>()
    WorkoutHistoryDetailScreen(
        workoutId = route.workoutId,
        navController = workoutNavController
    )
}
```

**Adaptation for Phase 17:** Inside the Overview tab block (after `composable<NutritionGoalsEditorRoute>`):
```kotlin
composable<ProgressGalleryRoute> {
    ProgressGalleryScreen(navController = overviewNavController)
}
composable<ProgressViewerRoute> { backStackEntry ->
    val route = backStackEntry.toRoute<ProgressViewerRoute>()
    ProgressViewerScreen(
        workoutId = route.workoutId,
        navController = overviewNavController
    )
}
```

---

### `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/OverviewScreen.kt` (MODIFY — add Progress entry)

**Analog:** Same file — the rank-strip and nutrition-goals-banner pattern (lines 119-145).

**Existing pattern to mirror** (lines 119-145):
```kotlin
// ── Rank Strip (D-18) ──
OverviewRankStrip(
    rankState = rankState,
    onTap = { navController.navigate(RanksAndAchievementsRoute) }
)

// ── Muscle Activity Section ──
MuscleActivityCard(uiState)

// ── Nutrition Goals Banner (D-16-13) ──
AnimatedVisibility(
    visible = bannerVisible,
    exit = slideOutVertically() + fadeOut(animationSpec = tween(300))
) {
    NutritionGoalsBanner(
        onTap = { navController.navigate(NutritionGoalsEditorRoute) },
        onDismiss = { viewModel.dismissBanner() }
    )
}
```

**Card with `clickable { navigate() }` pattern** (lines 564-612, `NutritionGoalsBanner` body):
```kotlin
Card(
    modifier = Modifier
        .fillMaxWidth()
        .clickable { onTap() },
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.TrackChanges,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Persönliche Ziele setzen", style = MaterialTheme.typography.bodyMedium)
            Text("Berechne deinen Tagesbedarf und passe deine Makros an.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
             tint = MaterialTheme.colorScheme.primary)
    }
}
```

**Adaptation for Phase 17:** Insert a new Progress entry card/button into the `Column` at lines 110-148. Suggested placement: after the Muscle Activity section (line 127), before the nutrition banner. Use the same `Card + clickable` pattern shown above. Tap navigates to `ProgressGalleryRoute`.

---

### `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ProgressGalleryScreen.kt` (NEW — Compose screen, streaming)

**Analog:** `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AchievementGalleryScreen.kt` (lines 1-228). Exact-match shape — `Scaffold + TopAppBar + LazyVerticalGrid(GridCells.Fixed(2))` with `@Composable` cards.

**Imports + screen entry** (lines 1-72):
```kotlin
package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
// ...
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
// ...
import androidx.navigation.NavHostController
import com.pumpernickel.presentation.gamification.AchievementGalleryViewModel
import com.pumpernickel.presentation.gamification.AchievementTile
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementGalleryScreen(
    navController: NavHostController,
    viewModel: AchievementGalleryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Achievements", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
```

**Tile card structure** (lines 137-201, adapted for blurred photo):
```kotlin
Card(
    modifier = Modifier
        .fillMaxWidth()
        .alpha(if (isLocked) 0.45f else 1f),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = containerColor)
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.EmojiEvents,
            // ...
        )
```

**Adaptation for Phase 17:**
- Same `Scaffold + TopAppBar + LazyVerticalGrid(GridCells.Fixed(2))` skeleton.
- Title: "Fortschritt" (German per app convention).
- Each tile is a Card whose background is the blurred cover photo (loaded from disk via `PhotoVault.read(coverRelativePath)` decoded by Coil 3 — note: Coil 3 not currently in deps; planner may need to add it OR use a basic `BitmapFactory.decodeByteArray` + `Image(bitmap=...)`). Apply `Modifier.blur(radius = 24.dp)` per CONTEXT D-17-13.
- Bottom translucent caption strip overlays the card via `Box { Image(...); Column(modifier = Modifier.align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha=0.6f))))) { ... } }` — repo has no exact gradient analog; closest is `OverviewScreen.NutritionGoalsBanner` for the translucent surface look (line 565: `containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)`).
- Tap → `viewModel.onTileTapped(workoutId)`. The VM fires biometric, on Success emits a nav event the screen collects via `LaunchedEffect { vm.navEvents.collect { navController.navigate(ProgressViewerRoute(it.workoutId)) } }`.

---

### `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ProgressViewerScreen.kt` (NEW — Compose screen, request-response)

**Analog (partial):** `AchievementGalleryScreen.kt` for the `Scaffold + TopAppBar + collectAsState` skeleton; no exact `HorizontalPager` carousel analog exists in the repo. Planner introduces `androidx.compose.foundation.pager.HorizontalPager` from `compose-foundation` (already on the classpath via `libs.compose.foundation` per `androidApp/build.gradle.kts` line 20).

**Reuse from `AchievementGalleryScreen`** (lines 49-78 — Scaffold + TopAppBar + popBackStack):
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementGalleryScreen(
    navController: NavHostController,
    viewModel: AchievementGalleryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Achievements", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
```

**`koinViewModel` with parameter (no exact analog — synthesised from CONTEXT):** Use `koinViewModel { parametersOf(workoutId) }`:
```kotlin
@Composable
fun ProgressViewerScreen(
    workoutId: Long,
    navController: NavHostController,
    viewModel: ProgressViewerViewModel = koinViewModel { parametersOf(workoutId) }
) { ... }
```

**Adaptation for Phase 17:**
- `Box(Modifier.fillMaxSize().background(Color.Black))` as outer container per CONTEXT discretion (full-screen black background).
- `HorizontalPager(state = rememberPagerState { uiState.pictures.size })` showing each `ProgressPicture` un-blurred (read bytes via `PhotoVault.read(picture.relativePath)` → decoded `ImageBitmap`).
- Caption strip pinned at bottom with the same metrics line as the gallery tile (date, volume, PR count, goal-day chip).
- Optional `IconButton` overflow for delete (per CONTEXT discretion line 102) — calls `viewModel.delete(picture.id)`.

---

### `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt` (MODIFY — Finished branch hosts photo prompt card)

**Analog:** Same file — the existing `FinishedContent` composable (lines 1059-1122).

**Existing Finished branch** (lines 226-234):
```kotlin
is WorkoutSessionState.Finished -> {
    FinishedContent(
        finished = state,
        onDone = {
            viewModel.resetToIdle()
            navController.popBackStack()
        }
    )
}
```

**Existing `FinishedContent` body** (lines 1059-1122 — current shape, before patch):
```kotlin
@Composable
private fun FinishedContent(
    finished: WorkoutSessionState.Finished,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.weight(1f))

        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Workout Complete!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(24.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            // ...
        ) { /* SummaryRows */ }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onDone,
            // ...
        ) {
            Text("Done", fontWeight = FontWeight.SemiBold)
        }
    }
}
```

**Adaptation for Phase 17:**
- The `WorkoutSessionState.Finished` data class (in `WorkoutSessionViewModel.kt` line 50) gains `val workoutId: Long` (CONTEXT line 190).
- Inside `FinishedContent`, between the summary `Surface` and the `Done` button, insert a `ProgressPicturePromptCard(workoutId = finished.workoutId)` composable. The card observes its own `ProgressPicturePromptViewModel` via `koinViewModel { parametersOf(finished.workoutId) }` (or the VM exposes a `setWorkoutId` method called via `LaunchedEffect`).
- Per CONTEXT D-17-01 the prompt is non-blocking — the `Done` button stays enabled and unrelated to whether a photo was attached.

---

### `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/ProgressPicturePromptCard.kt` (NEW — Compose component, event-driven)

**Analog:** `OverviewScreen.kt` `NutritionGoalsBanner` (lines 561-612). Same shape: `Card` with translucent surfaceVariant container, icon + title + description column, action affordance(s) on the right.

**Card pattern** (lines 564-612):
```kotlin
@Composable
private fun NutritionGoalsBanner(
    onTap: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(...)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Persönliche Ziele setzen", style = MaterialTheme.typography.bodyMedium)
                Text("Berechne deinen Tagesbedarf...", style = MaterialTheme.typography.labelSmall)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, ...)
            IconButton(onClick = onDismiss, ...) { Icon(Icons.Default.Close, ...) }
        }
    }
}
```

**Adaptation for Phase 17:**
- Three actions (CONTEXT D-17-01) → use a `Row` of three `OutlinedButton`s or a `Column` with the three buttons stacked. The card title above ("Foto hinzufügen?") and an `IconButton(onClick = onSkip)` on the trailing edge close the prompt.
- Buttons:
  - `"📷 Take Photo"` → `viewModel.captureFromCamera()`.
  - `"🖼 Pick from Library"` → `viewModel.pickFromLibrary()`.
  - `"Skip"` → close the card (local state).
- After the first photo is saved, the card text should switch to "Foto hinzugefügt — noch eins?" (CONTEXT line 21 / D-17-02). Drive this from `viewModel.photoCount.collectAsState()`.

---

### `androidApp/src/androidMain/AndroidManifest.xml` (MODIFY — backup attrs)

**Analog:** Same file (current state).

**Current `<application>` block** (lines 8-13):
```xml
<application
    android:name=".PumpernickelApplication"
    android:allowBackup="true"
    android:label="PumpernickelApp"
    android:supportsRtl="true"
    android:theme="@android:style/Theme.Material.Light.NoActionBar">
```

**Adaptation for Phase 17 (per CONTEXT D-17-06):**
```xml
<application
    android:name=".PumpernickelApplication"
    android:allowBackup="true"
    android:label="PumpernickelApp"
    android:supportsRtl="true"
    android:theme="@android:style/Theme.Material.Light.NoActionBar"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:fullBackupContent="@xml/backup_rules">
```
Per CONTEXT line 33: do NOT flip `android:allowBackup="false"` globally — the fine-grained rules are correct.

---

### `androidApp/src/androidMain/res/xml/backup_rules.xml` + `data_extraction_rules.xml` (NEW — config, no analog)

No prior Android XML resource exists in the repo (`androidApp/src/main/res/values/strings.xml` is the only `res/` file, and it's in `src/main/res`, not `src/androidMain/res`). Planner will need to create the directory `androidApp/src/androidMain/res/xml/`.

**Recommended `backup_rules.xml`** (Android 11- — `fullBackupContent`):
```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="file" path="progress_pics/" />
</full-backup-content>
```

**Recommended `data_extraction_rules.xml`** (Android 12+ — `dataExtractionRules`):
```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="file" path="progress_pics/" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="file" path="progress_pics/" />
    </device-transfer>
</data-extraction-rules>
```

---

### `androidApp/build.gradle.kts` (MODIFY — biometric dependency)

**Analog:** Same file — `camerax` dependency block (lines 25-30) is the precedent for adding an Android-only dependency.

**Existing** (lines 25-30):
```kotlin
implementation(libs.camerax.camera2)
implementation(libs.camerax.lifecycle)
implementation(libs.camerax.view)
implementation(libs.mlkit.barcode)
implementation(libs.kotlinx.datetime)
implementation("com.google.guava:guava:33.4.0-android")
```

**Note:** the `mlkit.barcode` dep uses `version.ref` in `libs.versions.toml`; the `guava` dep is hard-coded inline. Either pattern is acceptable for `androidx.biometric`.

**Adaptation for Phase 17 (per CONTEXT D-17-15):** Add to `libs.versions.toml`:
```toml
[versions]
biometric = "1.2.0-alpha05"  # planner picks exact stable; alpha05 widely cited 2024
[libraries]
androidx-biometric = { module = "androidx.biometric:biometric", version.ref = "biometric" }
```
Then `androidApp/build.gradle.kts` line ~30:
```kotlin
implementation(libs.androidx.biometric)
```

---

### `iosApp/iosApp/Info.plist` (MODIFY — usage descriptions)

**Analog:** Same file (current state, lines 1-10).

**Current full file:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>CADisableMinimumFrameDurationOnPhone</key>
	<true/>
	<key>NSCameraUsageDescription</key>
	<string>Barcode scanning requires camera access</string>
</dict>
</plist>
```

**Adaptation for Phase 17 (per CONTEXT line 156-158):**
- Update the existing `NSCameraUsageDescription` value to cover the photo-capture purpose too (e.g. "Camera access for barcode scanning and progress photos.") OR keep the original and rely on iOS's single-string-per-key constraint.
- Add `<key>NSPhotoLibraryUsageDescription</key><string>...</string>` — needed for `PHPickerViewController`.
- Add `<key>NSFaceIDUsageDescription</key><string>...</string>` — needed for `LAContext.deviceOwnerAuthentication` on Face ID devices (iOS will crash without it).
- Reason strings in German per app convention (CONTEXT D-17-15: "Fortschrittsbild entsperren" etc.).

---

### `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt` (MODIFY — Finished state)

**Analog:** Same file — current `Finished` data class (lines 50-55).

**Current** (lines 50-55):
```kotlin
data class Finished(
    val workoutName: String,
    val durationMillis: Long,
    val totalSets: Int,
    val totalExercises: Int
) : WorkoutSessionState()
```

**Site that constructs it** (lines 593-598):
```kotlin
val totalSets = completedExercises.sumOf { it.sets.size }
_sessionState.value = WorkoutSessionState.Finished(
    workoutName = reviewing.templateName,
    durationMillis = reviewing.durationMillis,
    totalSets = totalSets,
    totalExercises = completedExercises.size
)
```

**The save call that returns the workout id** (line 574):
```kotlin
val workoutId = workoutRepository.saveCompletedWorkout(completedWorkout)
```

**Adaptation for Phase 17 (per CONTEXT line 190):** Extend `Finished` with:
```kotlin
data class Finished(
    val workoutName: String,
    val durationMillis: Long,
    val totalSets: Int,
    val totalExercises: Int,
    val workoutId: Long,
    val photoCount: Int = 0
) : WorkoutSessionState()
```
And in the constructor at line 593, add `workoutId = workoutId, photoCount = 0`. The `photoCount` can stay `0` initially; the prompt screen drives a `repo.observePicturesForWorkout(workoutId)` flow that surfaces the live count separately.

---

### `.planning/phases/17-.../17-IOS-HANDOFF.md` (NEW — doc)

**Precedent:** Phase 15.1 D-151-16 emitted `15.1-IOS-HANDOFF.md`. Phase 17 follows the same convention.

**Content (planner will fill in):** SwiftUI surfaces the user will hand-write:
- `iosApp/iosApp/Views/Overview/ProgressGalleryView.swift` — observes `ProgressGalleryKoinHelper().getProgressGalleryViewModel()` via `asyncSequence(for:)` (see `Utilities/FlowObservation.swift` lines 1-22 + `Views/Gamification/AchievementGalleryView.swift` lines 13-67 for canonical patterns).
- `iosApp/iosApp/Views/Overview/ProgressViewerView.swift` — `TabView(selection:)` carousel with `.tabViewStyle(.page)`.
- Edit to `iosApp/iosApp/Views/Overview/OverviewView.swift` adding a new entry button (mirror `NutritionGoalsBannerView` at line 406-439).
- Edit to `iosApp/iosApp/Views/Workout/WorkoutFinishedView.swift` (lines 1-58) injecting the photo-prompt card.
- Permission/biometric notes pointing to `BarcodeScannerView.swift` (lines 58-77, `checkCameraPermission()`) for the camera-permission UX precedent.
- Use of `LAContext` with `.deviceOwnerAuthentication` (CONTEXT D-17-15).

---

## Shared Patterns

### KMPNativeCoroutines `@NativeCoroutinesState`
**Source:** `shared/.../presentation/gamification/AchievementGalleryViewModel.kt` lines 36-44; `shared/.../presentation/gamification/GamificationViewModel.kt` lines 37-44, 53-54.
**Apply to:** All three new shared VMs (`ProgressGalleryViewModel`, `ProgressViewerViewModel`, `ProgressPicturePromptViewModel`).
```kotlin
@NativeCoroutinesState
val uiState: StateFlow<ProgressGalleryUiState> = repo.observeGalleryTiles()
    .map { /* build UiState */ }
    .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ProgressGalleryUiState(isLoading = true)
    )
```
For one-shot events (delete success, biometric outcome, navigate), use `@NativeCoroutines val foo: SharedFlow<...>` (line 53-54 of `GamificationViewModel`).

---

### Koin `viewModel { ... }` registration
**Source:** `shared/.../di/SharedModule.kt` lines 109-123; `shared/.../di/AchievementGalleryModule.kt` line 8.
**Apply to:** All three new VMs in `progressGalleryModule`:
```kotlin
viewModel { ProgressGalleryViewModel(get(), get()) }
viewModel { (workoutId: Long) -> ProgressViewerViewModel(workoutId, get()) }
viewModel { ProgressPicturePromptViewModel(get(), get()) }
```

---

### `koinViewModel()` consumption in Compose
**Source:** `androidApp/.../ui/screens/AchievementGalleryScreen.kt` lines 50-55:
```kotlin
@Composable
fun AchievementGalleryScreen(
    navController: NavHostController,
    viewModel: AchievementGalleryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
```
**Apply to:** `ProgressGalleryScreen`, `ProgressPicturePromptCard` (no params), `ProgressViewerScreen` (with `parametersOf(workoutId)`).

---

### Repository interface + Impl + Mappers
**Source:** `shared/.../data/repository/GamificationRepository.kt`. Interface at top, `class ...Impl(private val dao: ...)` below, `private fun ...toDomain(): ...` mapper extensions at the bottom of the file.
**Apply to:** `ProgressPictureRepository.kt`. Mapper `private fun ProgressPictureEntity.toDomain(): ProgressPicture { ... }`.

---

### expect/actual pattern for platform code
**Source:** `shared/src/commonMain/kotlin/com/pumpernickel/data/preferences/createDataStore.kt` (factory shape) + `shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt` line 43-44 (`expect object ... : ...`).
**Apply to:** `PhotoVault`, `PhotoCaptureLauncher`, `BiometricGate`. Use `expect class Foo` (not factory) so Koin DI can inject platform context cleanly via `PlatformModule.{android,ios}.kt` (see line 13-16 of each).

---

### iOS Swift ↔ Kotlin VM bridge (KMPNativeCoroutinesAsync)
**Source:** `iosApp/iosApp/Utilities/FlowObservation.swift` (lines 1-22, comment block) + `iosApp/iosApp/Views/Gamification/AchievementGalleryView.swift` lines 14, 56-67.
```swift
private let viewModel = AchievementGalleryKoinHelper().getAchievementGalleryViewModel()
@State private var uiState: SharedAchievementGalleryUiState?

var body: some View { ... .task { await observeUiState() } }

private func observeUiState() async {
    do {
        for try await state in asyncSequence(for: viewModel.uiStateFlow) {
            self.uiState = state
        }
    } catch { print("...") }
}
```
**Apply to:** `ProgressGalleryView.swift`, `ProgressViewerView.swift` (in 17-IOS-HANDOFF.md spec).

---

### `@Serializable data object` / `data class` Routes
**Source:** `androidApp/.../navigation/Routes.kt` lines 18-19, 28, 32, 35.
**Apply to:** New `ProgressGalleryRoute` (data object) + `ProgressViewerRoute(val workoutId: Long)` (data class — analog: `WorkoutHistoryDetailRoute`).

---

### NavHost wiring (Overview tab)
**Source:** `androidApp/.../navigation/MainScreen.kt` lines 163-181 + line 151-157 (`composable<Route> { backStackEntry -> val route = backStackEntry.toRoute<...>(); Screen(...) }`).
**Apply to:** Adding `composable<ProgressGalleryRoute>` and `composable<ProgressViewerRoute>` inside the `1 -> NavHost(navController = overviewNavController, ...)` block.

---

### KoinHelper convention (one helper per VM)
**Source:** `shared/src/iosMain/.../di/AchievementGalleryKoinHelper.kt` (lines 1-9) + the convention doc-comment at `RanksAndAchievementsKoinHelper.kt` lines 6-12.
**Apply to:** `ProgressGalleryKoinHelper.kt`, `ProgressViewerKoinHelper.kt`. No caching, no params (except `workoutId` for the viewer helper, passed via `parametersOf(...)`).

---

### Material 3 translucent strip / banner
**Source:** `androidApp/.../ui/screens/OverviewScreen.kt` lines 564-612 (`NutritionGoalsBanner`):
```kotlin
Card(
    modifier = Modifier.fillMaxWidth().clickable { onTap() },
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
)
```
**Apply to:** `ProgressPicturePromptCard.kt` (whole card body), and the bottom caption strip in `ProgressGalleryScreen` tile (use `Modifier.background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha=0.6f))))` overlaid on the blurred photo `Box`).

---

## No Analog Found

Files with no close match in the codebase (planner should synthesise from the recommendations above + Android/iOS platform docs):

| File | Role | Reason |
|------|------|--------|
| `shared/src/iosMain/.../progresspic/PhotoCaptureLauncher.ios.kt` | Service (actual) | No `PHPickerViewController` / `UIImagePickerController` precedent in shared module. `BarcodeScannerView.swift` is in the iOS app, uses different APIs (`AVCaptureSession`), and lives outside `shared/`. The actual will need to use `suspendCancellableCoroutine` to bridge UIKit delegate callbacks to Kotlin suspend. |
| `androidApp/.../progresspic/PhotoCaptureLauncher.android.kt` | Service (actual) | No `ActivityResultContracts.TakePicture` / `PickVisualMedia` precedent in repo. Existing camera (CameraX) is wired into the Android-app module for barcode preview, not as a callback-based image picker. The actual needs an `ActivityResultRegistry` reference (passed via Koin or via a `LocalContext`-aware setup at the screen level). |
| `androidApp/.../progresspic/BiometricGate.android.kt` | Service (actual) | No `androidx.biometric.BiometricPrompt` precedent. Pattern: hold a reference to the current `FragmentActivity` (passed via Koin or accessor), call `BiometricPrompt(activity, executor, callback).authenticate(promptInfo)`, bridge callback to a `suspendCancellableCoroutine`. |
| `shared/src/iosMain/.../progresspic/BiometricGate.ios.kt` | Service (actual) | No `LAContext` precedent. Pattern: `LAContext().evaluatePolicy(LAPolicy.deviceOwnerAuthentication, localizedReason: reason) { success, error -> ... }`, bridged via `suspendCancellableCoroutine`. |
| `androidApp/src/androidMain/res/xml/backup_rules.xml` + `data_extraction_rules.xml` | config | No `res/xml` resource files in the repo at all — the planner introduces this directory. |

---

## Metadata

**Analog search scope:**
- `shared/src/commonMain/kotlin/com/pumpernickel/{data,domain,presentation,di}/**/*.kt`
- `shared/src/{androidMain,iosMain}/kotlin/com/pumpernickel/**/*.kt`
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/{ui/screens,ui/navigation,ui/components}/**/*.kt`
- `iosApp/iosApp/Views/**/*.swift`
- `iosApp/iosApp/Utilities/**/*.swift`
- Build files: `gradle/libs.versions.toml`, `androidApp/build.gradle.kts`
- Manifests: `androidApp/src/androidMain/AndroidManifest.xml`, `iosApp/iosApp/Info.plist`

**Files scanned:** ~45 source files across shared, androidApp, iosApp.
**Pattern extraction date:** 2026-04-28.

---

## PATTERN MAPPING COMPLETE
