# Phase 4: History & Settings - Research

**Researched:** 2026-03-29
**Domain:** Room DAO queries (multi-table), DataStore Preferences KMP, SwiftUI list/detail navigation, unit conversion presentation layer
**Confidence:** HIGH

## Summary

Phase 4 adds three capabilities to the existing workout tracking app: (1) browsing completed workout history with list and detail views, (2) displaying previous performance inline during active workouts, and (3) a kg/lbs unit toggle persisted via DataStore Preferences. The data layer already stores completed workouts (Phase 3) with entities for workouts, exercises, and sets -- the primary work is adding Room DAO queries to retrieve this data in useful shapes, building ViewModels to expose it, and creating SwiftUI views following the established patterns.

The only new library dependency is `androidx.datastore:datastore-preferences:1.2.1` for the unit preference. Everything else uses existing stack components (Room, Koin, ViewModel with @NativeCoroutinesState, SwiftUI NavigationStack). No schema migration is needed -- the completed_workouts tables already exist from Phase 3.

**Primary recommendation:** Extend the existing `CompletedWorkoutDao` with new `@Query` methods for history list (already exists as `getAllWorkouts()`), workout detail (exercises + sets by workoutId), and previous performance (latest workout by templateId). Add a `SettingsRepository` backed by DataStore Preferences for the unit toggle. Apply unit conversion purely at the presentation layer.

<user_constraints>

## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** History is accessible from the Workout tab -- a "History" button/icon on the template list screen navigates to the history list. No new tabs needed.
- **D-02:** History list is a separate pushed view from the Workout tab, not a tab replacement or section within the template list.
- **D-03:** Each history row shows: date (formatted, e.g., "Today", "Yesterday", "Mar 27"), template name, exercise count, total volume (sum of reps x weight across all sets), and duration.
- **D-04:** List sorted by date, newest first (HIST-01). Uses existing `CompletedWorkoutDao.getAllWorkouts()` Flow which already sorts DESC.
- **D-05:** Total volume calculated as sum of (actualReps x actualWeightKgX10) across all sets in the workout, displayed converted to the user's selected unit.
- **D-06:** Tapping a history entry pushes a detail view showing the full workout: header with name, date, duration, total volume, followed by exercise sections each listing their sets with reps and weight (HIST-03).
- **D-07:** Detail view layout mirrors the structure: exercise name as section header, sets listed below with set number, reps, and weight.
- **D-08:** During an active workout, each exercise shows the previous performance inline -- a subtitle or secondary text showing what the user did last time for that exercise (HIST-04).
- **D-09:** "Previous performance" is sourced from the most recent completed workout that used the same template. Shows per-exercise: last session's sets with reps and weight (e.g., "Last: 3x10 @ 50.0 kg").
- **D-10:** If no previous workout exists for the template, no previous performance is shown (no empty state needed -- just absent).
- **D-11:** Settings accessible via a gear icon on the Workout tab (top-right navigation bar). Opens a settings sheet/view.
- **D-12:** Settings screen contains a kg/lbs toggle (NAV-02). Minimal settings screen for now -- only the unit toggle.
- **D-13:** Internal storage remains kg x 10 integer (Phase 2 D-06). Unit toggle is display-only -- all weight values are converted at the presentation layer when displaying.
- **D-14:** Selected unit persisted via DataStore Preferences (lightweight key-value store, no Room needed).
- **D-15:** Unit preference applied globally: history list volumes, history detail weights, active workout set values, template editor weights (NAV-03). Conversion factor: 1 kg = 2.20462 lbs.

### Claude's Discretion
- Volume calculation display format (e.g., "1,250 kg" vs "1.2t" -- use reasonable formatting)
- Date formatting specifics (relative dates for recent, absolute for older)
- Whether settings sheet is a SwiftUI sheet or pushed navigation view
- DAO query strategy for fetching previous performance (by templateId or by exerciseId)
- Whether to add a WorkoutHistoryViewModel or extend existing ViewModels
- DataStore Preferences setup and Koin integration approach

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope.

</user_constraints>

<phase_requirements>

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| HIST-01 | User can view a list of completed workouts sorted by date (newest first) | Existing `getAllWorkouts()` DAO Flow already sorts DESC. Extend with exercise/set counts for summary data. |
| HIST-02 | Each history entry shows date, workout name, exercises performed, and total volume | New DAO query joining completed_workouts with exercises and sets to compute volume in a single query. |
| HIST-03 | User can tap a history entry to see full workout detail (all exercises, sets, reps, weight) | New DAO `@Transaction` query fetching workout + exercises + sets by workoutId. |
| HIST-04 | During an active workout, user can see what they did last time for each exercise (previous performance inline) | New DAO query: get most recent completed workout by templateId, return full exercise/set data. Inject into WorkoutSessionViewModel. |
| NAV-02 | User can toggle between kg and lbs as the weight unit | DataStore Preferences 1.2.1 with `PreferenceDataStoreFactory.createWithPath()`. Platform-specific file paths. |
| NAV-03 | Selected unit applies globally to all weight displays and entries | SettingsRepository exposes `weightUnit` as Flow. All weight-displaying views observe and convert at presentation layer. |

</phase_requirements>

## Project Constraints (from CLAUDE.md)

- **Tech stack**: Kotlin Multiplatform + Compose Multiplatform, iOS first
- **Storage**: Local/offline only -- Room for structured data, DataStore Preferences for settings
- **Architecture**: MVVM with ViewModel exposing StateFlow, Compose/SwiftUI observes via `collectAsState()` / `asyncSequence`
- **KMP interop**: `@NativeCoroutinesState` + `*Flow` suffix for iOS asyncSequence observation
- **DI**: Koin with `sharedModule`, `KoinHelper` for iOS
- **Weight format**: kg x 10 integer throughout internal storage
- **No backend**: Local-only prototype
- **DataStore version**: CLAUDE.md says 1.1.x but latest stable is 1.2.1 -- use 1.2.1

## Standard Stack

### Core (already in project)
| Library | Version | Purpose | Status |
|---------|---------|---------|--------|
| Room KMP | 2.8.4 | Completed workout queries | Already installed -- extend DAO |
| Koin | 4.2.0 | DI for new ViewModels, repositories | Already installed -- add registrations |
| ViewModel (KMP) | 2.10.0 | History/Settings ViewModels | Already installed |
| kotlinx-coroutines | 1.10.2 | Flow for reactive queries | Already installed |
| KMPNativeCoroutines | 1.0.2 | iOS StateFlow bridging | Already installed |

### New Dependency
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| DataStore Preferences | 1.2.1 | Persist weight unit setting (kg/lbs) | Official Google library for KMP key-value preferences. Lighter than Room for simple settings. Version 1.2.1 is latest stable (released March 11, 2026). Full KMP support since 1.1.0. |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| DataStore Preferences | Room table for settings | Overkill for a single key-value preference. Room adds entity/DAO/migration overhead. DataStore is purpose-built for this. |
| DataStore Preferences | NSUserDefaults (iOS) + SharedPrefs (Android) | Platform-specific code, no shared logic. DataStore gives cross-platform behavior from commonMain. |

**Installation (add to libs.versions.toml and shared/build.gradle.kts):**

```toml
# libs.versions.toml
[versions]
datastore = "1.2.1"

[libraries]
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
```

```kotlin
# shared/build.gradle.kts
commonMain.dependencies {
    implementation(libs.datastore.preferences)
}
```

**Version verification:** DataStore 1.2.1 released March 11, 2026 (confirmed via developer.android.com/jetpack/androidx/releases/datastore). Compatible with Kotlin 2.3.20 and KMP.

## Architecture Patterns

### Recommended New Files Structure
```
shared/src/commonMain/kotlin/com/pumpernickel/
  data/
    db/
      CompletedWorkoutDao.kt           # EXTEND: add new @Query methods
    preferences/
      createDataStore.kt               # NEW: common DataStore factory
    repository/
      WorkoutRepository.kt             # EXTEND: add history query methods
      SettingsRepository.kt            # NEW: weight unit preference
  domain/model/
    WeightUnit.kt                      # NEW: enum (KG, LBS)
    WorkoutSummary.kt                  # NEW: lightweight model for history list
  presentation/
    history/
      WorkoutHistoryViewModel.kt       # NEW: history list + detail state
    settings/
      SettingsViewModel.kt             # NEW: weight unit toggle

shared/src/iosMain/kotlin/com/pumpernickel/
  data/preferences/
    createDataStore.ios.kt             # NEW: iOS file path for DataStore
  di/
    KoinHelper.kt                      # EXTEND: expose new ViewModels

shared/src/androidMain/kotlin/com/pumpernickel/
  data/preferences/
    createDataStore.android.kt         # NEW: Android file path for DataStore

iosApp/iosApp/Views/
  History/
    WorkoutHistoryListView.swift       # NEW: history list
    WorkoutHistoryDetailView.swift     # NEW: history detail
  Settings/
    SettingsView.swift                 # NEW: unit toggle
  Templates/
    TemplateListView.swift             # MODIFY: add History + Settings buttons
  Workout/
    WorkoutSessionView.swift           # MODIFY: add previous performance display
    WorkoutSetRow.swift                # MODIFY: support dynamic unit label
```

### Pattern 1: Room Multi-Table DAO Queries
**What:** Use `@Query` with SQL JOINs to fetch related data across completed_workouts, completed_workout_exercises, and completed_workout_sets tables. Return custom data classes (not entities) for read-only views.
**When to use:** History list (workout + exercise count + volume), detail view (workout + exercises + sets), previous performance (latest workout for templateId + exercises + sets).
**Example:**

```kotlin
// History list summary query -- returns lightweight data for list display
// Use a custom return type, not the entity itself
data class WorkoutSummaryDto(
    val id: Long,
    val templateId: Long,
    val name: String,
    val startTimeMillis: Long,
    val durationMillis: Long,
    val exerciseCount: Int,
    val totalVolume: Long  // sum of (reps * weightKgX10) across all sets
)

@Dao
interface CompletedWorkoutDao {
    // Existing
    @Query("SELECT * FROM completed_workouts ORDER BY startTimeMillis DESC")
    fun getAllWorkouts(): Flow<List<CompletedWorkoutEntity>>

    // NEW: History list with summary data
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

    // NEW: Exercises for a specific workout
    @Query("SELECT * FROM completed_workout_exercises WHERE workoutId = :workoutId ORDER BY exerciseOrder ASC")
    suspend fun getExercisesForWorkout(workoutId: Long): List<CompletedWorkoutExerciseEntity>

    // NEW: Sets for a specific workout exercise
    @Query("SELECT * FROM completed_workout_sets WHERE workoutExerciseId = :workoutExerciseId ORDER BY setIndex ASC")
    suspend fun getSetsForExercise(workoutExerciseId: Long): List<CompletedWorkoutSetEntity>

    // NEW: Most recent workout for a template (previous performance)
    @Query("SELECT * FROM completed_workouts WHERE templateId = :templateId ORDER BY startTimeMillis DESC LIMIT 1")
    suspend fun getLastWorkoutForTemplate(templateId: Long): CompletedWorkoutEntity?
}
```

**Key detail:** The history list summary query uses a single SQL query with JOINs and GROUP BY to compute exercise count and total volume, avoiding N+1 query problems. For detail and previous performance, use separate queries composed in the repository layer since Room KMP requires suspend functions for non-Android targets (no `@Relation` annotation shortcut needed -- manual composition is clearer for this data shape).

### Pattern 2: DataStore Preferences with Koin in KMP
**What:** Create a singleton DataStore instance via `PreferenceDataStoreFactory.createWithPath()` in common code, with platform-specific file path resolution.
**When to use:** The weight unit preference (kg/lbs).
**Example:**

```kotlin
// commonMain: createDataStore.kt
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import okio.Path.Companion.toPath

fun createDataStore(producePath: () -> String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { producePath().toPath() }
    )

internal const val DATA_STORE_FILE_NAME = "pumpernickel_settings.preferences_pb"

// iosMain: createDataStore.ios.kt
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

// androidMain: createDataStore.android.kt
import android.content.Context

fun createDataStoreAndroid(context: Context): DataStore<Preferences> = createDataStore(
    producePath = {
        context.filesDir.resolve(DATA_STORE_FILE_NAME).absolutePath
    }
)
```

**Critical:** The file extension MUST be `.preferences_pb`. Using any other extension causes `IllegalStateException` at runtime.

### Pattern 3: Unit Conversion at Presentation Layer
**What:** All weight values stored as kgX10 integers. Conversion to lbs happens only when displaying.
**When to use:** Every weight display across the app (history list volume, history detail weights, active workout inputs/display, template editor).
**Example:**

```kotlin
// domain/model/WeightUnit.kt
enum class WeightUnit {
    KG, LBS;

    fun format(kgX10: Int): String {
        return when (this) {
            KG -> {
                val whole = kgX10 / 10
                val decimal = kgX10 % 10
                if (decimal == 0) "$whole kg" else "$whole.$decimal kg"
            }
            LBS -> {
                val lbs = kgX10 * 2.20462 / 10.0
                val formatted = String.format("%.1f", lbs)  // platform-specific
                "$formatted lbs"
            }
        }
    }

    fun formatVolume(totalVolumeKgX10: Long): String {
        return when (this) {
            KG -> {
                val kg = totalVolumeKgX10 / 10.0
                "${String.format("%,.0f", kg)} kg"
            }
            LBS -> {
                val lbs = totalVolumeKgX10 * 2.20462 / 10.0
                "${String.format("%,.0f", lbs)} lbs"
            }
        }
    }
}
```

**Note:** `String.format` is not available in Kotlin common. Use a multiplatform-safe approach -- either a simple manual formatter or use `kotlin.math.roundToInt()` with string concatenation.

### Pattern 4: ViewModel for History (follows TemplateListViewModel pattern)
**What:** A single ViewModel managing both history list and detail state, following the project's established MVVM pattern with `@NativeCoroutinesState` and StateFlow.
**When to use:** The history feature.
**Example:**

```kotlin
class WorkoutHistoryViewModel(
    private val workoutRepository: WorkoutRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    @NativeCoroutinesState
    val workoutSummaries: StateFlow<List<WorkoutSummary>> = workoutRepository
        .getWorkoutSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @NativeCoroutinesState
    val weightUnit: StateFlow<WeightUnit> = settingsRepository
        .weightUnit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeightUnit.KG)

    private val _workoutDetail = MutableStateFlow<CompletedWorkout?>(null)
    @NativeCoroutinesState
    val workoutDetail: StateFlow<CompletedWorkout?> = _workoutDetail.asStateFlow()

    fun loadWorkoutDetail(workoutId: Long) {
        viewModelScope.launch {
            _workoutDetail.value = workoutRepository.getWorkoutDetail(workoutId)
        }
    }
}
```

### Anti-Patterns to Avoid
- **N+1 queries for history list:** Do not fetch all workouts then loop to count exercises/sets. Use a single SQL JOIN with GROUP BY for summary data.
- **Storing weight in both kg and lbs:** Do not duplicate data. Store kg x 10 only, convert at display time.
- **Creating DataStore instances per-call:** DataStore MUST be a singleton. Creating multiple instances for the same file causes `IllegalStateException`.
- **Using `@Relation` for KMP:** While `@Relation` works in Room, it adds complexity with `@Embedded` and requires `@Transaction`. For this relatively simple data shape, explicit queries composed in the repository are cleaner and more debuggable.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Key-value persistence | Custom file I/O or plist wrappers | DataStore Preferences 1.2.1 | Thread-safe, transactional, Flow-based, KMP-native. Handles corruption recovery. |
| Relative date formatting | Manual "if today / yesterday / else" | kotlinx-datetime `Instant` + manual relative formatting | Already in the project. Use `Instant.fromEpochMilliseconds()` for date math, manual relative labels for "Today"/"Yesterday" (no KMP library exists for relative formatting). |
| Number formatting with commas | Custom string manipulation | Platform expect/actual or simple Kotlin math | Volume display needs locale-aware formatting. For a simple approach, divide and concatenate manually. |

**Key insight:** The existing data layer (Phase 3 entities + DAO) provides 80% of what's needed. The primary new work is query composition, not new infrastructure.

## Common Pitfalls

### Pitfall 1: DataStore Singleton Violation
**What goes wrong:** `IllegalStateException: There are multiple DataStores active for the same file` at runtime.
**Why it happens:** Creating a new `DataStore` instance each time instead of reusing a singleton. Common when not using DI properly.
**How to avoid:** Register DataStore as `single` in Koin. The factory function `createDataStore()` should be called exactly once per file.
**Warning signs:** Exception on second access to settings.

### Pitfall 2: DataStore File Extension
**What goes wrong:** `IllegalStateException` on first DataStore access.
**Why it happens:** Using `.json`, `.pb`, or any extension other than `.preferences_pb`.
**How to avoid:** Always use the `preferences_pb` extension for Preferences DataStore files.
**Warning signs:** Crash immediately on first settings read/write.

### Pitfall 3: Volume Calculation Integer Overflow
**What goes wrong:** Total volume (sum of reps * weightKgX10 across all sets) exceeds Int32 range for heavy workouts.
**Why it happens:** A workout with 20 exercises, 4 sets each, 10 reps at 100kg (1000 kgX10) = 20 * 4 * 10 * 1000 = 800,000 per workout. Summing across many workouts in a list could overflow Int32 (max ~2.1 billion).
**How to avoid:** Use `Long` (Int64) for volume calculations in the SQL query (`CAST` to INTEGER in SQLite returns 64-bit). The DTO field `totalVolume` should be `Long`.
**Warning signs:** Negative volume displayed for heavy lifters with many workouts.

### Pitfall 4: Previous Performance Query Timing
**What goes wrong:** Previous performance data loads after the workout UI renders, causing a visual jump/flash.
**Why it happens:** Loading previous performance as a separate async call after workout start.
**How to avoid:** Load previous performance data in `startWorkout()` before emitting the Active state, or emit it as a secondary StateFlow that the UI gracefully handles (show placeholder initially).
**Warning signs:** Flickering subtitle text when starting a workout.

### Pitfall 5: String.format Not Available in KMP Common
**What goes wrong:** Compilation error in commonMain when using `String.format()`.
**Why it happens:** `String.format()` is a JVM-only API, not available in Kotlin/Native (iOS).
**How to avoid:** Use manual string construction (`"${wholePart}.${decimalPart}"`) or create an expect/actual for formatting. For simple cases, `kotlin.math.roundToInt()` + string concatenation works fine.
**Warning signs:** Build failure on iOS target.

### Pitfall 6: SwiftUI Navigation State with Multiple Pushed Views
**What goes wrong:** History list -> detail view navigation conflicts with existing template -> workout navigation on the same NavigationStack.
**Why it happens:** The Workout tab has a single NavigationStack. Adding History as a pushed view from TemplateListView, then pushing detail from history, creates a deep navigation path.
**How to avoid:** Use `navigationDestination(for:)` with typed routing values, or use `NavigationLink(value:)` with proper `navigationDestination` modifiers at the right level. The existing pattern uses `@State` booleans for navigation, which works but needs care with multiple destinations.
**Warning signs:** Back button not working correctly, wrong view appearing on back.

## Code Examples

### SettingsRepository with DataStore Preferences
```kotlin
// Source: Official DataStore KMP docs pattern
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(
    private val dataStore: DataStore<Preferences>
) {
    private val weightUnitKey = stringPreferencesKey("weight_unit")

    val weightUnit: Flow<WeightUnit> = dataStore.data.map { preferences ->
        when (preferences[weightUnitKey]) {
            "LBS" -> WeightUnit.LBS
            else -> WeightUnit.KG  // default
        }
    }

    suspend fun setWeightUnit(unit: WeightUnit) {
        dataStore.edit { preferences ->
            preferences[weightUnitKey] = unit.name
        }
    }
}
```

### Koin Registration for DataStore
```kotlin
// In sharedModule (SharedModule.kt)
single<DataStore<Preferences>> { createDataStore(get()) }  // platform provides path
single<SettingsRepository> { SettingsRepository(get()) }
viewModel { WorkoutHistoryViewModel(get(), get()) }
viewModel { SettingsViewModel(get()) }
```

### SwiftUI History List Observation (follows established pattern)
```swift
// Source: matches TemplateListView.swift pattern
struct WorkoutHistoryListView: View {
    private let viewModel = KoinHelper.shared.getWorkoutHistoryViewModel()
    @State private var summaries: [WorkoutSummary] = []
    @State private var weightUnit: WeightUnit = .kg

    var body: some View {
        List(summaries, id: \.id) { summary in
            NavigationLink(destination: WorkoutHistoryDetailView(workoutId: summary.id)) {
                WorkoutHistoryRow(summary: summary, weightUnit: weightUnit)
            }
        }
        .navigationTitle("History")
        .task {
            await withTaskGroup(of: Void.self) { group in
                group.addTask { await observeSummaries() }
                group.addTask { await observeWeightUnit() }
            }
        }
    }

    private func observeSummaries() async {
        do {
            for try await value in asyncSequence(for: viewModel.workoutSummariesFlow) {
                self.summaries = value
            }
        } catch { print("History observation error: \(error)") }
    }
}
```

### Previous Performance in WorkoutSessionViewModel
```kotlin
// Add to WorkoutSessionViewModel.startWorkout()
// After building exercises from template, before emitting Active state:
val previousWorkout = workoutRepository.getPreviousPerformance(templateId)
// Attach to Active state or as a separate StateFlow
```

### Weight Conversion Utility (KMP-safe)
```kotlin
// No String.format -- use manual formatting
fun formatWeightKgX10(kgX10: Int, unit: WeightUnit): String {
    return when (unit) {
        WeightUnit.KG -> {
            val whole = kgX10 / 10
            val decimal = kgX10 % 10
            if (decimal == 0) "$whole kg" else "$whole.$decimal kg"
        }
        WeightUnit.LBS -> {
            val lbsX10 = (kgX10 * 22046L / 10000).toInt()  // integer math, no floating point
            val whole = lbsX10 / 10
            val decimal = lbsX10 % 10
            if (decimal == 0) "$whole lbs" else "$whole.$decimal lbs"
        }
    }
}

fun formatVolume(totalVolumeKgX10: Long, unit: WeightUnit): String {
    return when (unit) {
        WeightUnit.KG -> {
            val kg = totalVolumeKgX10 / 10
            "$kg kg"
        }
        WeightUnit.LBS -> {
            val lbs = (totalVolumeKgX10 * 22046L / 100000)
            "$lbs lbs"
        }
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| SharedPreferences (Android) / NSUserDefaults (iOS) | DataStore Preferences (KMP) | DataStore KMP stable since 1.1.0 (2024) | Single codebase for preferences, Flow-based reactive updates |
| Room @Relation with @Embedded | Explicit queries composed in repository | Always valid, preferred in KMP | Clearer code, avoids @Transaction complexity, works identically on all KMP targets |
| String.format for number display | Manual string construction or expect/actual | KMP constraint | Required for Kotlin/Native compatibility |

**Deprecated/outdated:**
- SharedPreferences: Still works on Android but not KMP-compatible. DataStore is the replacement.
- Room @Relation: Still works but adds complexity without benefit for this data shape. Use explicit queries.

## Open Questions

1. **Previous performance data shape for SwiftUI display**
   - What we know: Need to show "Last: 3x10 @ 50.0 kg" per exercise during active workout
   - What's unclear: Best way to pass this data from ViewModel to SwiftUI -- as part of Active state or as a separate StateFlow map keyed by exerciseId
   - Recommendation: Use a separate `StateFlow<Map<String, PreviousExercisePerformance>>` keyed by exerciseId, loaded once at workout start. This keeps the Active state clean and avoids recomputing on every state change.

2. **Locale-aware number formatting in KMP common**
   - What we know: Volume display (e.g., "1,250 kg") needs comma separators for readability
   - What's unclear: No built-in KMP common locale formatting
   - Recommendation: For the prototype, use a simple manual grouping function or skip commas. The display will still be clear (e.g., "1250 kg"). If commas are desired, implement a simple `fun Long.withCommas(): String` utility.

## Sources

### Primary (HIGH confidence)
- [DataStore KMP Setup Guide](https://developer.android.com/kotlin/multiplatform/datastore) - DataStore 1.2.1 setup, common/platform patterns, file extension requirement
- [DataStore Releases](https://developer.android.com/jetpack/androidx/releases/datastore) - Version 1.2.1 confirmed as latest stable, released March 11, 2026
- [Room KMP Setup](https://developer.android.com/kotlin/multiplatform/room) - Room DAO patterns, suspend requirement for non-Android KMP targets
- Existing codebase: CompletedWorkoutDao.kt, WorkoutRepository.kt, WorkoutSessionViewModel.kt, TemplateListView.swift, WorkoutSessionView.swift -- established patterns HIGH confidence

### Secondary (MEDIUM confidence)
- [Room Relations Guide](https://proandroiddev.com/room-database-relationships-explained-729d3c705fd9) - @Relation and @Transaction patterns
- [Room Multi-Table Joins](https://proandroiddev.com/room-database-lessons-learnt-from-working-with-multiple-tables-d499c9be94ce) - JOIN query patterns with custom return types
- [DataStore with Koin KMP](https://github.com/waqas028/DataStore-Koin-KMP) - Koin integration pattern for DataStore

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All libraries verified against current versions, DataStore 1.2.1 confirmed stable
- Architecture: HIGH - All patterns directly extend established codebase patterns from Phases 1-3
- Pitfalls: HIGH - Based on official documentation warnings (DataStore singleton, file extension) and KMP-specific constraints (String.format)
- DAO queries: HIGH - Standard Room SQL patterns, verified against official Room docs
- Unit conversion: HIGH - Pure arithmetic, no library dependencies

**Research date:** 2026-03-29
**Valid until:** 2026-04-28 (stable libraries, no expected breaking changes)
