# Phase 3: Workout Session - Research

**Researched:** 2026-03-28
**Domain:** Workout execution FSM, Room schema migration, countdown timer, haptic feedback, crash recovery persistence
**Confidence:** HIGH

## Summary

Phase 3 implements the core workout execution loop: template selection, set-by-set logging with pre-filled targets, rest timer countdown with haptic alert, progress tracking, session persistence for crash recovery, and completed workout storage. This is the most complex phase so far -- it introduces a stateful session (an FSM with states like Idle, Active/Logging, Resting, Finishing), a real-time countdown timer driven by coroutines, platform-specific haptic feedback from SwiftUI, and a new set of Room entities that require a schema migration from v2 to v3.

The existing codebase provides strong patterns to follow: ViewModel with `@NativeCoroutinesState` + `StateFlow`, SwiftUI observation via `asyncSequence(for:)`, Room DAO with `@Transaction` for atomic multi-table writes, Koin DI registration, and the repository pattern with `toDomain()` extensions. The new workout session ViewModel will be the most complex ViewModel in the app, managing multiple concerns (session state, timer, persistence) that should be cleanly separated.

**Primary recommendation:** Build the workout session as a single `WorkoutSessionViewModel` with a sealed class state machine (`Idle`, `Active`, `Resting`, `Finished`), backed by Room entities for crash recovery (persisted after every set completion per D-13), with the countdown timer implemented as a coroutine `while`/`delay` loop in `viewModelScope`. Use Room AutoMigration from v2 to v3 for the new tables. Keep haptic feedback in the SwiftUI layer using `UINotificationFeedbackGenerator` since the iOS app renders with SwiftUI, not Compose Multiplatform.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Linear progression, exercise-by-exercise, set-by-set -- mirrors the gymtracker firmware FSM. Default flow advances automatically from set to rest to next set to next exercise.
- **D-02:** User can view an exercise overview and tap to jump to any exercise (non-sequential access), but the default progression is linear.
- **D-03:** Workout starts from the template list -- tapping a template offers "Start Workout" which loads the template into an active session.
- **D-04:** Rest timer auto-starts immediately after the user marks a set as complete (per WORK-04). Countdown matches the exercise's configured restPeriodSec.
- **D-05:** Skip button available during rest -- the timer is a guide, not a gate. User can proceed to the next set early.
- **D-06:** No extend/pause controls -- keep the timer simple. User can skip if they rested enough.
- **D-07:** Alert via haptic vibration when rest period ends (WORK-05). Foreground haptic only for prototype -- background local notifications deferred to v2.
- **D-08:** Timer displayed inline within the workout screen as a prominent countdown -- no full-screen takeover. Current exercise context remains visible.
- **D-09:** Reps and weight pre-filled from template targets for each set. User adjusts only what differs from the plan.
- **D-10:** Individual set confirmation -- user marks each set complete one at a time (WORK-03), which triggers the rest timer (D-04).
- **D-11:** User can tap a completed set within the current exercise to edit its reps/weight retroactively.
- **D-12:** Weight input uses the established kg*10 integer pattern (Phase 2 D-06). Display as decimal kg (e.g., "50.5 kg"). Lbs toggle deferred to Phase 4.
- **D-13:** Active session state saved to Room after every set completion -- most granular and resilient approach for WORK-09.
- **D-14:** On app launch, check for an unfinished session entity. If found, prompt user: "Resume workout?" or "Discard" -- auto-detection, no manual recovery needed.
- **D-15:** Active session entity stores: template reference, current exercise index, completed sets with actual reps/weight, start time, and last-updated timestamp.
- **D-16:** User explicitly finishes a workout via a "Finish Workout" action. End time recorded automatically (WORK-08).
- **D-17:** Completed workout saved with: name (from template), start time, end time, total duration, and all exercises with their logged sets (actual reps, actual weight).
- **D-18:** After saving, active session entity is cleared. User returns to the template list.

### Claude's Discretion
- Database migration strategy (Room schema version bump, auto-migration vs manual)
- Exact Room entity design for active session and completed workout (table structure, column names)
- ViewModel state machine implementation (sealed class states, transitions)
- SwiftUI screen layout and component decomposition for the workout execution UI
- Whether "Start Workout" is a button on the template list row or on a template detail screen
- Elapsed duration display format (MM:SS vs HH:MM:SS)
- Whether to show a confirmation dialog before discarding an in-progress workout

### Deferred Ideas (OUT OF SCOPE)
- Background rest timer notifications (UNUserNotificationCenter) -- deferred to v2
- RPE (Rate of Perceived Exertion) per set -- deferred to v2 WORK-12
- Superset grouping -- deferred to v2 WORK-13
- Ad-hoc workout without template -- deferred to v2 WORK-10
- Set tagging (warm-up, drop set, failure) -- deferred to v2 WORK-12
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| WORK-01 | User can start a workout by selecting a template, which loads exercises and targets into an active session | Room entities for active session + ViewModel initialization from template data; TemplateRepository already resolves exercises |
| WORK-02 | User can log reps and weight for each set of each exercise | Set logging UI with pre-filled targets from template (D-09); weight uses kgX10 integer pattern |
| WORK-03 | User can mark a set as complete, advancing to the rest timer | ViewModel state transition: Active(Logging) -> Active(Resting); triggers timer start and Room persistence |
| WORK-04 | Rest timer auto-starts after completing a set with countdown matching exercise's configured rest period | Coroutine-based countdown timer in ViewModel using while/delay loop; restPeriodSec from TemplateExerciseEntity |
| WORK-05 | Rest timer alerts the user (vibration) when rest period ends | SwiftUI UINotificationFeedbackGenerator.notificationOccurred(.success) called when timer state transitions to complete |
| WORK-06 | User can see current progress during a workout (current exercise X of Y, current set X of Y) | ViewModel exposes currentExerciseIndex, totalExercises, currentSetIndex, totalSets as StateFlow |
| WORK-07 | User can finish and save a completed workout to local storage | Room @Transaction to atomically save CompletedWorkout + WorkoutExercise + WorkoutSet entities and delete active session |
| WORK-08 | Workout duration is automatically tracked (start time, end time, total duration) | startTimeMillis stored in active session entity; endTimeMillis captured on finish; elapsed computed from Clock.System.now() - startTime |
| WORK-09 | Active workout session persists across app restarts (crash recovery / session resume) | Active session entity saved to Room after every set completion (D-13); app launch checks for unfinished session (D-14) |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- **Tech stack**: Kotlin Multiplatform + Compose Multiplatform (but iOS uses SwiftUI for UI rendering)
- **Platform focus**: iOS first (user handles iOS UI)
- **Storage**: Room KMP 2.8.4 with BundledSQLiteDriver
- **DI**: Koin 4.2.0 with koin-compose-viewmodel
- **State management**: ViewModel with MutableStateFlow, exposed as StateFlow with @NativeCoroutinesState
- **iOS observation**: asyncSequence(for:) via KMPNativeCoroutinesAsync SPM 1.0.2
- **Timestamps**: kotlin.time.Clock.System.now().toEpochMilliseconds()
- **Weight format**: kgX10 integer pattern (Int, not Float)
- **Database**: Currently schema v2, fallbackToDestructiveMigration enabled
- **No backend, no Ktor Client** for prototype
- **Avoid**: Hilt, LiveData, RxJava, SKIE, Accompanist

## Standard Stack

### Core (Already in Project)
| Library | Version | Purpose | Status |
|---------|---------|---------|--------|
| Room KMP | 2.8.4 | Workout session + completed workout persistence | Add new entities/DAOs |
| AndroidX SQLite Bundled | 2.6.2 | SQLite driver | Already configured |
| Koin | 4.2.0 | DI for new repositories + ViewModel | Add registrations |
| kotlinx.coroutines | 1.10.2 | Timer coroutine, viewModelScope | Already in project |
| Lifecycle ViewModel | 2.10.0 | WorkoutSessionViewModel | Already in project |
| KMP NativeCoroutines | 1.0.2 | iOS StateFlow bridging | Already in project |

### No New Dependencies Required

This phase requires **zero new library dependencies**. Everything needed is already in the project:
- Countdown timer: `kotlinx.coroutines` `delay()` in a `while` loop within `viewModelScope`
- Haptic feedback: SwiftUI native `UINotificationFeedbackGenerator` (no Kotlin dependency)
- Elapsed duration tracking: `kotlin.time.Clock.System` (stdlib)
- All persistence: Room 2.8.4 (already configured)

## Architecture Patterns

### Recommended Project Structure (New Files)
```
shared/src/commonMain/kotlin/com/pumpernickel/
  data/
    db/
      ActiveSessionEntity.kt          # Room entity: active workout session header
      ActiveSessionSetEntity.kt        # Room entity: completed sets within active session
      CompletedWorkoutEntity.kt        # Room entity: finished workout header
      CompletedWorkoutExerciseEntity.kt # Room entity: exercise within completed workout
      CompletedWorkoutSetEntity.kt     # Room entity: set within completed workout exercise
      WorkoutSessionDao.kt            # DAO for active session CRUD
      CompletedWorkoutDao.kt           # DAO for saving completed workouts
    repository/
      WorkoutRepository.kt            # Interface + impl for session + completed workout operations
  domain/
    model/
      WorkoutSession.kt               # Domain models: WorkoutSession, SessionExercise, SessionSet
      CompletedWorkout.kt             # Domain models: CompletedWorkout, CompletedExercise, CompletedSet
  presentation/
    workout/
      WorkoutSessionViewModel.kt      # State machine ViewModel: Idle -> Active -> Finished

iosApp/iosApp/Views/Workout/
  WorkoutSessionView.swift            # Main workout execution screen
  WorkoutSetRow.swift                 # Individual set row with reps/weight inputs
  RestTimerView.swift                 # Inline rest timer countdown display
  ExerciseOverviewSheet.swift         # Sheet showing exercise list for non-sequential jump (D-02)
```

### Pattern 1: Sealed Class State Machine (ViewModel)
**What:** Model the workout session as explicit states using Kotlin sealed classes
**When to use:** Any multi-phase user flow with distinct states and transitions
**Recommendation:**

```kotlin
sealed class WorkoutSessionState {
    data object Idle : WorkoutSessionState()

    data class Active(
        val templateName: String,
        val exercises: List<SessionExercise>,
        val currentExerciseIndex: Int,
        val currentSetIndex: Int,
        val startTimeMillis: Long,
        val restState: RestState = RestState.NotResting
    ) : WorkoutSessionState()

    data class Finished(
        val summary: WorkoutSummary
    ) : WorkoutSessionState()
}

sealed class RestState {
    data object NotResting : RestState()
    data class Resting(val remainingSeconds: Int, val totalSeconds: Int) : RestState()
    data object RestComplete : RestState()
}
```

This gives exhaustive `when` matching in both Kotlin and Swift, clear state transitions, and prevents invalid state combinations.

### Pattern 2: Per-Set Room Persistence for Crash Recovery (D-13)
**What:** Save session state to Room after every set completion, not just on app background
**When to use:** When data loss from process death must be minimized
**Recommendation:**

The active session uses two tables:
1. `active_sessions` -- single row (at most one active workout), stores header info
2. `active_session_sets` -- one row per completed set, stores actual reps/weight

On set completion: insert into `active_session_sets` and update `active_sessions.lastUpdatedAt` and cursor (currentExerciseIndex, currentSetIndex). On app launch: query `active_sessions` -- if a row exists, reconstruct full session state from the sets table.

### Pattern 3: Coroutine Countdown Timer
**What:** Countdown timer using `delay()` in a coroutine loop, emitting remaining seconds to StateFlow
**When to use:** Any timed countdown in KMP that needs to update UI every second
**Recommendation:**

```kotlin
private var timerJob: Job? = null

fun startRestTimer(durationSeconds: Int) {
    timerJob?.cancel()
    timerJob = viewModelScope.launch {
        var remaining = durationSeconds
        updateRestState(RestState.Resting(remaining, durationSeconds))
        while (remaining > 0) {
            delay(1000L)
            remaining--
            updateRestState(RestState.Resting(remaining, durationSeconds))
        }
        updateRestState(RestState.RestComplete)
        // After a brief moment, auto-advance to next set
        delay(500L)
        advanceToNextSet()
    }
}

fun skipRest() {
    timerJob?.cancel()
    advanceToNextSet()
}
```

Key points:
- `viewModelScope.launch` ensures automatic cancellation when ViewModel is cleared
- `timerJob?.cancel()` prevents multiple concurrent timers
- `delay(1000L)` is coroutine-friendly (does not block thread)
- Timer state flows to UI via StateFlow, observed in SwiftUI

### Pattern 4: SwiftUI Haptic Feedback (D-07)
**What:** Trigger iOS haptic vibration when rest timer completes
**When to use:** Foreground-only alert for timer completion

```swift
// In WorkoutSessionView.swift
import UIKit

private let hapticGenerator = UINotificationFeedbackGenerator()

// When observing rest state changes:
private func onRestStateChanged(_ restState: RestState) {
    if restState is RestStateRestComplete {
        hapticGenerator.prepare()
        hapticGenerator.notificationOccurred(.success)
    }
}
```

`UINotificationFeedbackGenerator` with `.success` gives a satisfying "done" vibration pattern. Call `.prepare()` just before triggering to reduce latency. This is purely a SwiftUI concern -- no expect/actual needed in KMP since the UI layer is SwiftUI.

### Pattern 5: Room AutoMigration v2 to v3
**What:** Add new tables (active_sessions, active_session_sets, completed_workouts, completed_workout_exercises, completed_workout_sets) without losing existing data
**When to use:** Schema additions that only add new tables (no column renames, no deletions)
**Recommendation:**

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
        CompletedWorkoutSetEntity::class
    ],
    version = 3,
    autoMigrations = [
        AutoMigration(from = 2, to = 3)
    ]
)
```

Room AutoMigration supports adding new tables automatically. Since we are only adding new entities (not modifying existing tables), AutoMigration from v2 to v3 works without a custom `AutoMigrationSpec`. The project already has schema exports enabled (v2 JSON exists at `shared/schemas/com.pumpernickel.data.db.AppDatabase/2.json`), which is a prerequisite for AutoMigration.

**Important:** Remove `fallbackToDestructiveMigration(dropAllTables = true)` from the database builder in `SharedModule.kt`. This is dangerous in production -- it silently wipes all user data (templates, exercises) when the schema version changes. Replace with the AutoMigration declaration. For development, keep destructive fallback only as a last resort and log a warning.

### Anti-Patterns to Avoid
- **God ViewModel:** Do not put timer logic, persistence logic, and state machine transitions all as top-level methods in the ViewModel. Extract the timer into a helper or use well-named private methods. The ViewModel orchestrates, it does not implement every detail.
- **Saving full session JSON as a single column:** Do not serialize the entire session state to a JSON string in one column. Use normalized Room entities (separate tables for sets). This makes querying, partial updates, and crash recovery efficient.
- **Using `GlobalScope` for timer:** Always use `viewModelScope` so the timer is automatically cancelled when the ViewModel is destroyed.
- **Blocking on Room writes in the UI path:** All Room writes (set completion persistence) must be `suspend` functions called from `viewModelScope.launch` -- never blocking the main thread.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Countdown timer scheduling | Custom Timer/Handler/NSTimer via expect/actual | Coroutine `delay()` in viewModelScope | Coroutines are KMP-native, auto-cancel with ViewModel lifecycle, no platform abstraction needed |
| Haptic feedback abstraction | expect/actual HapticManager in KMP common code | Direct UINotificationFeedbackGenerator in SwiftUI | iOS UI is SwiftUI -- calling UIKit haptics from Swift is 2 lines; an expect/actual abstraction adds complexity for no benefit since Android UI is not implemented |
| Elapsed time formatting | Manual string formatting with modulo arithmetic | `kotlin.time.Duration` formatting | Already in stdlib: `Duration.toComponents { h, m, s, _ -> }` handles HH:MM:SS cleanly |
| Atomic multi-table writes | Manual try/catch with individual inserts | Room `@Transaction` on DAO abstract methods | Room wraps the entire method body in a SQLite transaction, rolling back on any failure |
| Crash recovery serialization | SharedPreferences/DataStore with JSON session blob | Room entities with per-set rows | Room gives you queryable, normalized, transactional persistence with no serialization overhead |

**Key insight:** This phase is complex but uses no technology outside what the project already has. The complexity is in the state management and UX flow, not in new library integration.

## Common Pitfalls

### Pitfall 1: Timer Drift and Inaccuracy
**What goes wrong:** Using `delay(1000L)` in a loop accumulates drift because `delay` guarantees *at least* 1000ms, not exactly 1000ms. Over a 90-second rest period, drift can be 1-3 seconds.
**Why it happens:** Coroutine scheduling, GC pauses, and system load add small delays to each tick.
**How to avoid:** For a prototype, this drift is acceptable. If precision matters, anchor the timer to wall-clock time: record `startTime = Clock.System.now()`, then on each tick compute `remaining = totalDuration - (now - startTime)` instead of decrementing a counter. The display will always be accurate.
**Warning signs:** Users report the timer shows 0 but takes a second to trigger the completion alert.

### Pitfall 2: Losing Timer State on Process Death
**What goes wrong:** The coroutine timer is in-memory only. If the app is killed while resting, the timer is lost.
**Why it happens:** Process death destroys the ViewModel and all in-memory state.
**How to avoid:** Per D-13, save the rest timer's start time and duration to Room when starting rest. On session resume (D-14), compute elapsed rest time and either show the remaining countdown or skip directly to "rest complete" if the rest period has passed. The timer is a UI convenience -- the important thing is to remember which set was last completed.
**Warning signs:** After app kill during rest, the app resumes at the right set but shows no timer (acceptable) or shows incorrect timer state (bug).

### Pitfall 3: Concurrent Session Corruption
**What goes wrong:** User somehow starts a second workout while one is already active, creating two active session rows.
**Why it happens:** Race condition if "Start Workout" is tapped twice rapidly, or if the resume check has not completed before a new session is started.
**How to avoid:** Enforce a single active session at the database level. The ViewModel should check for an existing active session before creating a new one. In Room, query `active_sessions` first; if a row exists, present the resume/discard prompt instead of allowing a new session.
**Warning signs:** Multiple rows in `active_sessions` table; UI shows wrong exercise data.

### Pitfall 4: Room Schema Migration Destroys User Data
**What goes wrong:** The current `fallbackToDestructiveMigration(dropAllTables = true)` in SharedModule.kt silently wipes all data when the schema version bumps from 2 to 3.
**Why it happens:** Destructive migration is the fallback when no migration path is defined. It was acceptable during early development with no user data, but now templates and exercises exist.
**How to avoid:** Replace `fallbackToDestructiveMigration` with `AutoMigration(from = 2, to = 3)` in the `@Database` annotation. Test the migration on a device that has v2 data.
**Warning signs:** After updating to the Phase 3 build, all templates and exercises disappear.

### Pitfall 5: SwiftUI State Observation Explosion
**What goes wrong:** The workout session ViewModel exposes 8+ StateFlows. Observing each one individually in SwiftUI (like TemplateEditorView does with 5 separate `observeXxx()` functions) becomes unwieldy.
**Why it happens:** Following the Phase 2 pattern of one `@State` per flow observation.
**How to avoid:** Consider exposing a single `WorkoutSessionState` sealed class as one StateFlow from the ViewModel. SwiftUI observes one flow and destructures the state in the view. This reduces the number of async observation tasks from N to 1. The TemplateEditorView pattern works but does not scale well to 8+ flows.
**Warning signs:** `observeState()` function with 8+ `group.addTask` calls; race conditions where one state updates before another.

### Pitfall 6: Boolean StateFlow Bridging Issues
**What goes wrong:** Phase 1 documented that observing Kotlin `Boolean` StateFlow from Swift sometimes fails with KMPNativeCoroutinesAsync.
**Why it happens:** Swift/Kotlin interop wraps Boolean as `KotlinBoolean` -- `.boolValue` must be called.
**How to avoid:** Use the established pattern: `value.boolValue` for Boolean flows. Or better, bundle booleans into the main state sealed class to avoid standalone Boolean flows entirely.
**Warning signs:** `isSaving`, `isWorkoutActive` observation silently fails or always returns false.

## Code Examples

### Room Entity Design: Active Session (Crash Recovery)

```kotlin
// ActiveSessionEntity.kt
@Entity(tableName = "active_sessions")
data class ActiveSessionEntity(
    @PrimaryKey val id: Long = 1, // Singleton -- at most one active session
    val templateId: Long,
    val templateName: String,
    val currentExerciseIndex: Int,
    val currentSetIndex: Int,
    val startTimeMillis: Long,
    val lastUpdatedMillis: Long
)

// ActiveSessionSetEntity.kt
@Entity(
    tableName = "active_session_sets",
    foreignKeys = [
        ForeignKey(
            entity = ActiveSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class ActiveSessionSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long = 1, // Always references the singleton active session
    val exerciseIndex: Int,  // 0-based index within the template
    val setIndex: Int,       // 0-based set number within the exercise
    val actualReps: Int,
    val actualWeightKgX10: Int,
    val completedAtMillis: Long
)
```

### Room Entity Design: Completed Workout (History Storage)

```kotlin
// CompletedWorkoutEntity.kt
@Entity(tableName = "completed_workouts")
data class CompletedWorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val name: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationMillis: Long
)

// CompletedWorkoutExerciseEntity.kt
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
    val exerciseId: String,      // References exercises.id
    val exerciseName: String,    // Denormalized for history display
    val exerciseOrder: Int
)

// CompletedWorkoutSetEntity.kt
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
    val actualWeightKgX10: Int
)
```

### DAO with @Transaction for Atomic Workout Save

```kotlin
@Dao
abstract class CompletedWorkoutDao {
    @Insert
    protected abstract suspend fun insertWorkout(workout: CompletedWorkoutEntity): Long

    @Insert
    protected abstract suspend fun insertExercise(exercise: CompletedWorkoutExerciseEntity): Long

    @Insert
    protected abstract suspend fun insertSets(sets: List<CompletedWorkoutSetEntity>)

    @Transaction
    open suspend fun saveCompletedWorkout(
        workout: CompletedWorkoutEntity,
        exercises: List<CompletedWorkoutExerciseEntity>,
        sets: List<CompletedWorkoutSetEntity>
    ): Long {
        val workoutId = insertWorkout(workout)
        for (exercise in exercises) {
            val exerciseId = insertExercise(exercise.copy(workoutId = workoutId))
            val exerciseSets = sets.filter {
                it.workoutExerciseId == exercise.id // Map temp IDs
            }.map { it.copy(workoutExerciseId = exerciseId) }
            insertSets(exerciseSets)
        }
        return workoutId
    }
}
```

### WorkoutSessionViewModel Skeleton

```kotlin
class WorkoutSessionViewModel(
    private val workoutRepository: WorkoutRepository,
    private val templateRepository: TemplateRepository
) : ViewModel() {

    private val _sessionState = MutableStateFlow<WorkoutSessionState>(WorkoutSessionState.Idle)
    @NativeCoroutinesState
    val sessionState: StateFlow<WorkoutSessionState> = _sessionState.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    @NativeCoroutinesState
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private var timerJob: Job? = null
    private var elapsedJob: Job? = null

    fun startWorkout(templateId: Long) { /* load template, create active session */ }
    fun resumeWorkout() { /* restore from Room active session */ }
    fun completeSet(reps: Int, weightKgX10: Int) { /* save set, start rest timer */ }
    fun skipRest() { /* cancel timer, advance to next set */ }
    fun editCompletedSet(exerciseIndex: Int, setIndex: Int, reps: Int, weightKgX10: Int) { /* D-11 */ }
    fun jumpToExercise(exerciseIndex: Int) { /* D-02 */ }
    fun finishWorkout() { /* save completed, clear active session */ }
    fun discardWorkout() { /* clear active session */ }
}
```

### SwiftUI Haptic on Rest Complete

```swift
// In WorkoutSessionView.swift
private func observeSessionState() async {
    let generator = UINotificationFeedbackGenerator()
    var previousRestState: Any? = nil

    do {
        for try await state in asyncSequence(for: viewModel.sessionStateFlow) {
            self.sessionState = state

            // Trigger haptic when rest completes (D-07)
            if let active = state as? WorkoutSessionStateActive {
                if active.restState is RestStateRestComplete,
                   !(previousRestState is RestStateRestComplete) {
                    generator.prepare()
                    generator.notificationOccurred(.success)
                }
                previousRestState = active.restState
            }
        }
    } catch {
        print("Session state observation error: \(error)")
    }
}
```

### Elapsed Duration Formatting

```kotlin
// In domain/model or util
fun formatElapsedDuration(elapsedSeconds: Long): String {
    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
```

Recommendation: Use MM:SS by default, switch to H:MM:SS only when the workout exceeds 59 minutes. This keeps the display compact for typical workouts (30-90 minutes).

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Room manual Migration objects | Room AutoMigration for simple schema additions | Room 2.4.0+ (stable in KMP 2.7.0+) | Zero migration code for adding new tables |
| expect/actual CountDownTimer | Coroutine delay() loop in viewModelScope | Always available in KMP | No platform-specific timer code needed |
| fallbackToDestructiveMigration for all dev | AutoMigration + destructive only as last resort | Production readiness | Preserves user data across schema bumps |
| Multiple individual StateFlows | Single sealed class state flow | Best practice for complex ViewModels | Reduces observation complexity, prevents inconsistent intermediate states |

**Deprecated/outdated:**
- `android.os.CountDownTimer`: Android-only, not KMP-compatible
- `java.util.Timer` / `kotlin.concurrent.fixedRateTimer`: JVM-only, does not work in KMP common code
- `NSTimer`: iOS-only, no need since coroutine delay works cross-platform

## Open Questions

1. **AutoMigration in Room KMP -- runtime verification needed**
   - What we know: Room documentation confirms AutoMigration supports adding new tables. The project has schema v2 export. Room KMP docs show `AutoMigration` syntax is supported with `SQLiteConnection`-based migrations.
   - What's unclear: Whether the KSP code generation for `@AutoMigration` works identically on iOS targets as on Android. No KMP-specific limitation has been documented, but this is the project's first migration.
   - Recommendation: Implement AutoMigration. If it fails to compile for iOS targets, fall back to a manual `Migration(2, 3)` that runs `CREATE TABLE` statements for the 5 new tables. This is straightforward since we are only adding tables.

2. **Singleton active session enforcement**
   - What we know: D-15 implies at most one active session. Using `id = 1` as a fixed primary key enforces this at the DB level (`INSERT OR REPLACE`).
   - What's unclear: Whether `INSERT OR REPLACE` with Room's `@Insert(onConflict = OnConflictStrategy.REPLACE)` is the cleanest pattern, or whether a delete-then-insert in a transaction is better.
   - Recommendation: Use `@Insert(onConflict = OnConflictStrategy.REPLACE)` for the active session entity. This naturally enforces the singleton pattern.

3. **Elapsed time ticker accuracy**
   - What we know: The elapsed time display (WORK-08) needs to tick every second during an active workout.
   - What's unclear: Whether running two concurrent coroutine loops (elapsed ticker + rest countdown) causes any issues on iOS.
   - Recommendation: Both run in `viewModelScope` and use `delay()`, which is lightweight. No issue expected. The elapsed ticker can be a separate coroutine job started when the workout begins and cancelled when it finishes.

## Sources

### Primary (HIGH confidence)
- Existing codebase: `AppDatabase.kt`, `WorkoutTemplateDao.kt`, `TemplateRepository.kt`, `TemplateEditorViewModel.kt` -- established patterns for Room entities, DAOs, repositories, and ViewModels
- Existing codebase: `TemplateEditorView.swift`, `TemplateListView.swift` -- established SwiftUI observation patterns
- Gymtracker reference: `workout.rs` API + SQL migrations -- workout data model structure (workouts -> exercises -> sets)
- Room schema export: `shared/schemas/.../2.json` -- current v2 schema baseline for migration
- [Room KMP setup guide](https://developer.android.com/kotlin/multiplatform/room) -- AutoMigration syntax for KMP confirmed, SQLiteConnection-based Migration API
- [Room migration docs](https://developer.android.com/training/data-storage/room/migrating-db-versions) -- AutoMigration supports adding tables/columns, @AutoMigration annotation syntax
- [Compose Multiplatform haptic fix](https://github.com/JetBrains/compose-multiplatform/issues/4598) -- HapticFeedback now supported on iOS (closed as completed)

### Secondary (MEDIUM confidence)
- [Kotlin coroutine timer patterns](https://proandroiddev.com/count-down-timer-with-kotlin-coroutines-flow-a59c36167247) -- while/delay countdown pattern verification
- [Room @Transaction docs](https://www.daniweb.com/programming/mobile-development/tutorials/537522/android-native-run-multiple-statements-in-a-room-transaction) -- @Transaction on abstract DAO methods for multi-table atomic writes
- [KMP countdown timer article](https://medium.com/@adman.shadman/implementing-a-cross-platform-countdown-timer-in-kotlin-multiplatform-for-android-and-ios-6f3f41695607) -- Confirmed coroutine approach is preferred over expect/actual native timers
- [Kotlin sealed class FSM pattern](https://thoughtbot.com/blog/finite-state-machines-android-kotlin-good-times) -- Sealed class state machine in ViewModel approach

### Tertiary (LOW confidence)
- AutoMigration behavior on iOS KSP targets -- not explicitly documented for KMP iOS, inferred from general Room KMP docs. Needs runtime verification.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- no new dependencies, all libraries already verified in Phase 1+2
- Architecture: HIGH -- state machine, repository, DAO patterns all follow established codebase conventions
- Room migration: MEDIUM -- AutoMigration syntax is documented for KMP but this is the project's first migration; fallback to manual migration is straightforward
- Timer implementation: HIGH -- coroutine delay() is standard KMP, well-documented pattern
- Haptic feedback: HIGH -- SwiftUI UINotificationFeedbackGenerator is a 2-line call, no KMP bridging needed
- Pitfalls: HIGH -- identified from codebase analysis and established Room/coroutine gotchas

**Research date:** 2026-03-28
**Valid until:** 2026-04-28 (stable -- no fast-moving dependencies)
