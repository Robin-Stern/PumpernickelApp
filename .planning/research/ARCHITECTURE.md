# Architecture Patterns

**Domain:** Fitness/workout tracking mobile app (KMP Compose Multiplatform)
**Researched:** 2026-03-28

## Recommended Architecture

**Pattern:** MVVM + Clean Architecture (single shared module, feature packages)
**Why:** This is the standard, well-documented approach for KMP Compose Multiplatform apps. A single shared module with feature packages (not separate Gradle modules per feature) is the right granularity for a prototype/university project. Full multi-module Clean Architecture is overkill at this scale.

### High-Level Structure

```
PumpernickelApp/
├── composeApp/                          # Shared KMP module (all platforms)
│   └── src/
│       ├── commonMain/kotlin/
│       │   └── com.pumpernickel.app/
│       │       ├── App.kt               # Root @Composable, NavHost, Koin init
│       │       ├── navigation/
│       │       │   └── AppNavigation.kt  # NavHost + route definitions
│       │       │
│       │       ├── data/                 # Data layer
│       │       │   ├── local/
│       │       │   │   ├── AppDatabase.kt        # Room @Database definition
│       │       │   │   ├── dao/
│       │       │   │   │   ├── TemplateDao.kt
│       │       │   │   │   ├── ExerciseDao.kt
│       │       │   │   │   └── WorkoutDao.kt
│       │       │   │   └── entity/
│       │       │   │       ├── WorkoutTemplateEntity.kt
│       │       │   │       ├── ExerciseEntity.kt
│       │       │   │       ├── CompletedWorkoutEntity.kt
│       │       │   │       └── ...
│       │       │   └── repository/
│       │       │       ├── TemplateRepositoryImpl.kt
│       │       │       ├── ExerciseRepositoryImpl.kt
│       │       │       └── WorkoutRepositoryImpl.kt
│       │       │
│       │       ├── domain/              # Domain layer (interfaces + models)
│       │       │   ├── model/
│       │       │   │   ├── WorkoutTemplate.kt
│       │       │   │   ├── Exercise.kt
│       │       │   │   ├── CompletedWorkout.kt
│       │       │   │   ├── CompletedExercise.kt
│       │       │   │   ├── CompletedSet.kt
│       │       │   │   └── WorkoutSession.kt     # Active session state
│       │       │   └── repository/
│       │       │       ├── TemplateRepository.kt  # Interface
│       │       │       ├── ExerciseRepository.kt  # Interface
│       │       │       └── WorkoutRepository.kt   # Interface
│       │       │
│       │       ├── feature/             # Feature packages (presentation)
│       │       │   ├── workout/
│       │       │   │   ├── session/
│       │       │   │   │   ├── WorkoutSessionViewModel.kt  # THE core VM
│       │       │   │   │   ├── WorkoutSessionState.kt      # UI state sealed class
│       │       │   │   │   ├── ActiveWorkoutScreen.kt      # Set entry screen
│       │       │   │   │   ├── RestTimerScreen.kt          # Countdown rest
│       │       │   │   │   ├── WorkoutFinishScreen.kt      # Recap + save
│       │       │   │   │   └── components/                 # Shared composables
│       │       │   │   ├── templates/
│       │       │   │   │   ├── TemplateListViewModel.kt
│       │       │   │   │   ├── TemplateListScreen.kt
│       │       │   │   │   ├── TemplateDetailViewModel.kt
│       │       │   │   │   ├── TemplateDetailScreen.kt
│       │       │   │   │   └── TemplateEditScreen.kt
│       │       │   │   └── history/
│       │       │   │       ├── HistoryListViewModel.kt
│       │       │   │       ├── HistoryListScreen.kt
│       │       │   │       └── HistoryDetailScreen.kt
│       │       │   ├── overview/        # Future: F3 dashboard
│       │       │   └── nutrition/       # Future: F2 nutrition
│       │       │
│       │       ├── di/                  # Koin modules
│       │       │   ├── AppModule.kt     # ViewModels
│       │       │   ├── DataModule.kt    # Database, DAOs, Repositories
│       │       │   └── PlatformModule.kt # expect declarations
│       │       │
│       │       └── ui/                  # Shared UI components
│       │           ├── theme/
│       │           ├── components/      # Reusable composables
│       │           └── navigation/      # Bottom nav bar
│       │
│       ├── androidMain/kotlin/
│       │   └── com.pumpernickel.app/
│       │       ├── di/PlatformModule.android.kt  # actual DB builder
│       │       └── MainApplication.kt
│       │
│       └── iosMain/kotlin/
│           └── com.pumpernickel.app/
│               ├── di/PlatformModule.ios.kt      # actual DB builder
│               └── MainViewController.kt
│
├── androidApp/                          # Android thin wrapper
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── MainActivity.kt             # setContent { App() }
│
└── iosApp/                              # Xcode project
    └── iosApp/
        └── ContentView.swift            # UIKit host for ComposeView
```

### Component Boundaries

| Component | Responsibility | Communicates With |
|-----------|---------------|-------------------|
| **Navigation (NavHost)** | Route management, screen transitions, back stack | Screens, ViewModels (via scoping) |
| **WorkoutSessionViewModel** | Active workout state machine, timer, set tracking | WorkoutRepository, TemplateRepository |
| **TemplateListViewModel** | Template CRUD list operations | TemplateRepository |
| **TemplateDetailViewModel** | Single template view/edit | TemplateRepository, ExerciseRepository |
| **HistoryListViewModel** | Completed workout browsing | WorkoutRepository |
| **Room Database (AppDatabase)** | Local SQLite persistence | DAOs (generated) |
| **Repository implementations** | Data access, entity-to-model mapping | DAOs, domain models |
| **Koin DI** | Wiring everything together | All components |

### Data Flow

```
User Action
    |
    v
Screen (@Composable) --- observes StateFlow ---> ViewModel
    |                                               |
    | (events/intents)                              | (business logic)
    v                                               v
ViewModel.onEvent(event) ---> Repository Interface (domain)
                                        |
                                        v
                              RepositoryImpl (data) ---> Room DAO ---> SQLite
                                        |
                                        v
                              Domain Model (mapped from Entity)
                                        |
                                        v
                              StateFlow<UiState> updated
                                        |
                                        v
                              Compose recomposes screen
```

**Data flows one direction:** User -> Screen -> ViewModel -> Repository -> Database. State flows back via `StateFlow` observation. No component reaches "up" the chain.

## Mapping the Firmware FSM to Mobile Architecture

The firmware uses an explicit FSM (`AppState` enum + `Transition` table) because embedded systems need manual state management. In a mobile app with Compose + Navigation, the FSM decomposes into two complementary mechanisms:

### What the Firmware FSM Does (and How to Replace It)

| Firmware Concept | Mobile Equivalent | Why |
|-----------------|-------------------|-----|
| `AppState` enum (77 states) | Navigation routes + ViewModel state | Navigation handles screen-to-screen; ViewModel handles within-screen state |
| `getTransitions()` table | Navigation graph + ViewModel event handlers | NavHost defines valid routes; ViewModel validates transitions |
| `NavigationIntent` | Navigation arguments (type-safe routes) | `@Serializable data class` routes carry context |
| `switchTo` (no history) | `navController.navigate() { popUpTo(...) }` | Pop back stack to prevent return to intermediate states |
| `navigateTo` (with history) | `navController.navigate()` (default) | Standard push onto back stack |
| File-scope globals (`workoutCurrentExerciseIdx`, etc.) | `WorkoutSessionViewModel` state | Scoped to workout navigation sub-graph lifetime |

### The Workout Session: One ViewModel, Multiple Screens

The firmware's core workout loop (`WORKOUT_START_SET -> WORKOUT_SET_ENTRY -> WORKOUT_REST -> next/finish`) uses shared mutable globals across states. In mobile, this maps to **one `WorkoutSessionViewModel` shared across all workout session screens**.

```kotlin
// Route definitions for the workout flow
@Serializable object WorkoutRoutes {
    @Serializable object TemplateList          // Select a template
    @Serializable data class TemplateDetail(val templateId: Long)
    @Serializable data class ActiveWorkout(val templateId: Long)  // Entry point
    @Serializable object SetEntry              // Log reps/weight
    @Serializable object RestTimer             // Countdown timer
    @Serializable object WorkoutFinish         // Recap + save
}
```

```kotlin
// The WorkoutSessionViewModel replaces firmware's global workout state
class WorkoutSessionViewModel(
    private val workoutRepository: WorkoutRepository,
    private val templateRepository: TemplateRepository,
) : ViewModel() {

    // Replaces: workoutCurrentExerciseIdx, workoutCurrentSetIdx, exerciseOrder[]
    private val _sessionState = MutableStateFlow<WorkoutSessionState>(WorkoutSessionState.NotStarted)
    val sessionState: StateFlow<WorkoutSessionState> = _sessionState.asStateFlow()

    // Replaces: CompletedWorkout being built across FSM states
    private val _activeWorkout = MutableStateFlow<ActiveWorkoutData?>(null)
    val activeWorkout: StateFlow<ActiveWorkoutData?> = _activeWorkout.asStateFlow()

    // Replaces: WorkoutSetEntryState::sharedStartMs / sharedDurationSec
    private val _restTimer = MutableStateFlow<RestTimerState?>(null)
    val restTimer: StateFlow<RestTimerState?> = _restTimer.asStateFlow()

    fun startWorkout(templateId: Long) { /* Load template, initialize session */ }
    fun confirmSet(reps: Int, weightKgX10: Int) { /* Save set, advance state */ }
    fun startRestTimer() { /* Begin countdown using viewModelScope coroutine */ }
    fun skipRest() { /* Advance to next set/exercise */ }
    fun finishWorkout() { /* Save completed workout to database */ }
    fun reorderExercise(from: Int, to: Int) { /* Swap in pending queue */ }
}
```

```kotlin
// Session state sealed class -- replaces the firmware's AppState enum for workout flow
sealed class WorkoutSessionState {
    object NotStarted : WorkoutSessionState()

    data class Active(
        val templateName: String,
        val currentExerciseIndex: Int,
        val currentSetIndex: Int,
        val totalExercises: Int,
        val exerciseOrder: List<Int>,      // Replaces exerciseOrder[] array
        val currentExercise: Exercise,
        val targetSets: Int,
        val targetReps: Int,
        val targetWeightKgX10: Int,
        val completedSets: List<CompletedSet>,
    ) : WorkoutSessionState()

    data class Resting(
        val remainingSeconds: Int,
        val totalSeconds: Int,
        val nextExerciseName: String?,      // Replaces WorkoutRestState's next-exercise hint
        val isLastExercise: Boolean,
    ) : WorkoutSessionState()

    data class Finishing(
        val workoutName: String,
        val exercises: List<CompletedExercise>,
        val startTime: String,
        val endTime: String,
    ) : WorkoutSessionState()

    object Saved : WorkoutSessionState()
}
```

### How the ViewModel Replaces Each Firmware State

| Firmware State | Mobile Screen | ViewModel Action |
|---------------|---------------|-----------------|
| `READ_WORKOUTS_LIST` (with START_WORKOUT intent) | `TemplateListScreen` | `TemplateListViewModel.loadTemplates()` |
| `WORKOUT_START_SET` | `ActiveWorkoutScreen` (showing "SET N" UI) | `sessionState = Active(...)` |
| `WORKOUT_SET_ENTRY` | `ActiveWorkoutScreen` (reps/weight pickers shown) | Same screen, pickers are part of Active state UI |
| `WORKOUT_REST` | `RestTimerScreen` | `sessionState = Resting(...)`, coroutine ticks timer |
| `WORKOUT_FINISH` | `WorkoutFinishScreen` | `sessionState = Finishing(...)` |
| `WORKOUT_SAVE` | `WorkoutFinishScreen` (save button) | `finishWorkout()` -> `sessionState = Saved` |
| `WORKOUT_RESUME_PROMPT` | Dialog on `TemplateListScreen` | Check for active session in DB on app launch |
| `WORKOUT_CONTEXT_MENU` | Bottom sheet on `ActiveWorkoutScreen` | `showContextMenu = true` in UI state |
| `WORKOUT_EXERCISE_LIST` | Bottom sheet content | Part of context menu composable |
| `WORKOUT_EXERCISE_MOVE_MODE` | Drag-to-reorder in list | `reorderExercise(from, to)` |
| `WORKOUT_ABANDON_CONFIRM` | Alert dialog | `showAbandonDialog = true` |

### Key Insight: Fewer Screens, More State

The firmware needs 15+ states for the workout flow because each "screen" is an explicit state in the FSM. In mobile Compose, **many firmware states collapse into UI state within a single screen**:

- `WORKOUT_START_SET` + `WORKOUT_SET_ENTRY` = one `ActiveWorkoutScreen` (set entry is always visible, no separate "doing set" screen needed on a phone)
- `WORKOUT_CONTEXT_MENU` + `WORKOUT_EXERCISE_LIST` + `WORKOUT_EXERCISE_MOVE_MODE` = bottom sheet overlay on `ActiveWorkoutScreen`
- `WORKOUT_ABANDON_CONFIRM` = alert dialog, not a screen
- `WORKOUT_FINISH` + `WORKOUT_RECAP` + `WORKOUT_SAVE` = one `WorkoutFinishScreen` with recap content and a save button

**Final screen count for workout flow: 4 screens** (TemplateList, ActiveWorkout, RestTimer, WorkoutFinish) vs 15+ firmware states.

### Navigation Graph Structure

```kotlin
// In AppNavigation.kt
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { BottomNavBar(navController) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = MainRoutes.Workout,
            modifier = Modifier.padding(padding)
        ) {
            // Tab 1: Workout
            navigation<MainRoutes.Workout>(startDestination = WorkoutRoutes.TemplateList) {
                composable<WorkoutRoutes.TemplateList> {
                    TemplateListScreen(
                        onTemplateSelected = { id -> navController.navigate(WorkoutRoutes.ActiveWorkout(id)) },
                        onTemplateEdit = { id -> navController.navigate(WorkoutRoutes.TemplateDetail(id)) }
                    )
                }
                composable<WorkoutRoutes.TemplateDetail> { /* ... */ }

                // Workout session sub-graph (shared ViewModel scope)
                navigation<WorkoutRoutes.ActiveWorkout>(startDestination = WorkoutRoutes.SetEntry) {
                    composable<WorkoutRoutes.SetEntry> { entry ->
                        val parentEntry = remember(entry) {
                            navController.getBackStackEntry(WorkoutRoutes.ActiveWorkout)
                        }
                        val sessionVm: WorkoutSessionViewModel = koinViewModel(
                            viewModelStoreOwner = parentEntry
                        )
                        ActiveWorkoutScreen(sessionVm, navController)
                    }
                    composable<WorkoutRoutes.RestTimer> { entry ->
                        // Same ViewModel, scoped to ActiveWorkout sub-graph
                        val parentEntry = remember(entry) {
                            navController.getBackStackEntry(WorkoutRoutes.ActiveWorkout)
                        }
                        val sessionVm: WorkoutSessionViewModel = koinViewModel(
                            viewModelStoreOwner = parentEntry
                        )
                        RestTimerScreen(sessionVm, navController)
                    }
                    composable<WorkoutRoutes.WorkoutFinish> { /* same pattern */ }
                }
            }

            // Tab 2: Overview (future)
            composable<MainRoutes.Overview> { PlaceholderScreen("Overview") }

            // Tab 3: Nutrition (future)
            composable<MainRoutes.Nutrition> { PlaceholderScreen("Nutrition") }
        }
    }
}
```

**Critical detail:** The `WorkoutSessionViewModel` is scoped to the `ActiveWorkout` navigation sub-graph. It lives as long as the user is in any workout session screen and is automatically destroyed when the user navigates out (back to template list or saves). This replaces the firmware pattern of resetting global state on `WORKOUT_SAVE -> START` transition.

### Rest Timer Implementation

The firmware uses `millis()` elapsed time comparisons. In KMP, use a coroutine in `viewModelScope`:

```kotlin
// Inside WorkoutSessionViewModel
private var timerJob: Job? = null

fun startRestTimer(durationSeconds: Int) {
    timerJob?.cancel()
    val startTime = Clock.System.now()
    _sessionState.value = WorkoutSessionState.Resting(
        remainingSeconds = durationSeconds,
        totalSeconds = durationSeconds,
        nextExerciseName = getNextExerciseName(),
        isLastExercise = isOnLastExercise(),
    )

    timerJob = viewModelScope.launch {
        while (true) {
            delay(1000L)
            val elapsed = (Clock.System.now() - startTime).inWholeSeconds.toInt()
            val remaining = (durationSeconds - elapsed).coerceAtLeast(0)
            _sessionState.value = (_sessionState.value as? WorkoutSessionState.Resting)
                ?.copy(remainingSeconds = remaining)
                ?: break
            if (remaining <= 0) break
        }
    }
}
```

## Patterns to Follow

### Pattern 1: Unidirectional Data Flow (UDF)
**What:** Events flow down (user -> ViewModel), state flows up (ViewModel -> UI via StateFlow).
**When:** Always. Every screen follows this pattern.
**Example:**
```kotlin
// ViewModel exposes state, accepts events
class TemplateListViewModel(
    private val repository: TemplateRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(TemplateListUiState())
    val uiState: StateFlow<TemplateListUiState> = _uiState.asStateFlow()

    fun onEvent(event: TemplateListEvent) {
        when (event) {
            is TemplateListEvent.DeleteTemplate -> deleteTemplate(event.id)
            is TemplateListEvent.Refresh -> loadTemplates()
        }
    }
}

// Screen observes state, sends events
@Composable
fun TemplateListScreen(viewModel: TemplateListViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Render uiState, call viewModel.onEvent(...) on user action
}
```

### Pattern 2: Repository Pattern with Room
**What:** Repository interface in domain layer, implementation in data layer with Room DAOs.
**When:** All data access. No DAO calls from ViewModels directly.
**Example:**
```kotlin
// domain/repository/TemplateRepository.kt
interface TemplateRepository {
    fun getTemplates(): Flow<List<WorkoutTemplate>>
    suspend fun getTemplateById(id: Long): WorkoutTemplate?
    suspend fun insertTemplate(template: WorkoutTemplate): Long
    suspend fun deleteTemplate(id: Long)
}

// data/repository/TemplateRepositoryImpl.kt
class TemplateRepositoryImpl(
    private val templateDao: TemplateDao
) : TemplateRepository {
    override fun getTemplates(): Flow<List<WorkoutTemplate>> =
        templateDao.getAll().map { entities -> entities.map { it.toDomainModel() } }
}
```

### Pattern 3: Koin DI Module Organization
**What:** Separate Koin modules for data, domain (if use cases exist), and presentation.
**When:** Always. Declare in commonMain, platform-specific actuals for database builder.
**Example:**
```kotlin
// di/DataModule.kt
val dataModule = module {
    single { getRoomDatabase(get()) }  // get() provides platform-specific builder
    single { get<AppDatabase>().templateDao() }
    single { get<AppDatabase>().exerciseDao() }
    single { get<AppDatabase>().workoutDao() }
    single<TemplateRepository> { TemplateRepositoryImpl(get()) }
    single<WorkoutRepository> { WorkoutRepositoryImpl(get()) }
}

// di/AppModule.kt
val appModule = module {
    viewModelOf(::TemplateListViewModel)
    viewModelOf(::TemplateDetailViewModel)
    viewModelOf(::WorkoutSessionViewModel)
    viewModelOf(::HistoryListViewModel)
}
```

### Pattern 4: Session Persistence for Crash Recovery
**What:** Save active workout state to a dedicated Room table on every set confirmation, so a crash or app kill does not lose workout progress.
**When:** During active workout sessions. Mirrors the firmware's approach of writing to flash on every set.
**Example:**
```kotlin
// On confirmSet(), persist immediately
suspend fun confirmSet(reps: Int, weightKgX10: Int) {
    // 1. Update in-memory state
    // 2. Persist to active_session table (Room)
    workoutRepository.saveActiveSession(currentSession)
    // 3. Advance to next state
}

// On app launch, check for interrupted session
fun checkForActiveSession() {
    viewModelScope.launch {
        val session = workoutRepository.getActiveSession()
        if (session != null) {
            _showResumeDialog.value = true
        }
    }
}
```

## Anti-Patterns to Avoid

### Anti-Pattern 1: God ViewModel
**What:** Putting all workout logic (templates, session, history) in one massive ViewModel.
**Why bad:** Untestable, unclear lifecycle, hard to reason about state.
**Instead:** One ViewModel per logical concern. `TemplateListViewModel` for browsing, `WorkoutSessionViewModel` for active sessions, `HistoryListViewModel` for history. The session ViewModel is the largest by necessity, but template and history management are separate.

### Anti-Pattern 2: Direct DAO Access from Composables
**What:** Calling `database.templateDao().getAll()` inside a `@Composable` or passing DAOs to screens.
**Why bad:** Tight coupling to Room, no place for mapping/business logic, untestable.
**Instead:** Always go through Repository -> ViewModel -> StateFlow.

### Anti-Pattern 3: Exposing MutableStateFlow to UI
**What:** Making `_uiState` public or exposing `MutableStateFlow` from ViewModel.
**Why bad:** UI can accidentally mutate state, breaking unidirectional flow.
**Instead:** Expose `StateFlow` (read-only) via `.asStateFlow()`.

### Anti-Pattern 4: Navigation Logic in Composables
**What:** Complex conditional navigation inside `@Composable` functions based on ViewModel state.
**Why bad:** Navigation becomes tangled with rendering, hard to test, race conditions.
**Instead:** ViewModel emits navigation events as one-shot `SharedFlow` or `Channel`, screen collects and calls `navController.navigate()`. Or: ViewModel sets a "navigation target" state that the screen observes.

### Anti-Pattern 5: Duplicating Firmware's Explicit FSM
**What:** Creating a `WorkoutState` enum with 15 values and a giant `when` block to mirror the firmware.
**Why bad:** Navigation already manages screen state. Adding a parallel FSM creates two sources of truth.
**Instead:** Let Navigation own screen-to-screen transitions. Let ViewModel own within-screen state. The sealed class `WorkoutSessionState` is sufficient -- it has 4-5 variants, not 15.

## Data Model (Room Entities)

Adapted from the firmware's `DataModels.h`, using Room conventions:

```kotlin
// Weight stored as Int (kg * 10) for 0.1kg precision, matching firmware convention.
// Display logic converts: 615 -> "61.5 kg"

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val equipment: String,
    val primaryMuscleId: Int = 0,
    val secondaryMuscleId: Int = 0,
)

@Entity(tableName = "workout_templates")
data class WorkoutTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "template_exercises",
    foreignKeys = [
        ForeignKey(entity = WorkoutTemplateEntity::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"]),
    ]
)
data class TemplateExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val exerciseId: Long,
    val orderIndex: Int,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKgX10: Int,   // kg * 10 (0.1kg precision)
    val restPeriodSeconds: Int,
)

@Entity(tableName = "completed_workouts")
data class CompletedWorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long?,
    val name: String,
    val startTime: String,    // ISO8601
    val endTime: String,      // ISO8601
    val durationMs: Long,
)

@Entity(
    tableName = "completed_exercises",
    foreignKeys = [
        ForeignKey(entity = CompletedWorkoutEntity::class, parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE),
    ]
)
data class CompletedExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val orderIndex: Int,
    val notes: String = "",
)

@Entity(
    tableName = "completed_sets",
    foreignKeys = [
        ForeignKey(entity = CompletedExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseEntryId"], onDelete = ForeignKey.CASCADE),
    ]
)
data class CompletedSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseEntryId: Long,
    val setIndex: Int,
    val reps: Int,
    val weightKgX10: Int,     // kg * 10 (0.1kg precision)
    val restPeriodSeconds: Int,
)

// For crash recovery: persisted active session
@Entity(tableName = "active_session")
data class ActiveSessionEntity(
    @PrimaryKey val id: Long = 1,  // Singleton row
    val templateId: Long,
    val currentExerciseIndex: Int,
    val currentSetIndex: Int,
    val exerciseOrderJson: String,  // JSON array of exercise indices
    val startTime: String,
    val completedDataJson: String,  // JSON snapshot of completed sets so far
)
```

**Key difference from firmware:** The firmware uses flat packed structs with fixed-size arrays (`MAX_EXERCISES_PER_WORKOUT = 10`, `MAX_SETS_PER_EXERCISE = 5`) because of RAM constraints. The mobile app uses relational tables with no artificial limits. The `active_session` table for crash recovery uses JSON for the variable-size workout-in-progress data because it is transient (deleted on save).

## Suggested Build Order

Dependencies between components dictate this order:

### Phase 1: Foundation (build first -- everything depends on these)
1. **Room Database + Entities** -- Everything needs data. Define entities, DAOs, database class.
2. **Domain models** -- Simple data classes. No dependencies.
3. **Repository interfaces + implementations** -- Bridge between DB and presentation.
4. **Koin DI setup** -- Wire database, repos. Platform-specific `expect/actual` for DB builder.

### Phase 2: Template Management (needed before workout can start)
5. **TemplateListViewModel + Screen** -- Users must see and select templates.
6. **TemplateDetailViewModel + Screen** -- View/edit a single template.
7. **Exercise management** -- Seed exercises, exercise picker for templates.
8. **Navigation shell** -- Bottom nav bar, NavHost, route definitions.

### Phase 3: Workout Session (the core feature, depends on templates existing)
9. **WorkoutSessionViewModel** -- The heart of the app. State machine, timer, set tracking.
10. **ActiveWorkoutScreen** -- Set entry UI (reps/weight pickers).
11. **RestTimerScreen** -- Countdown with coroutine timer.
12. **WorkoutFinishScreen** -- Recap, save.
13. **Session persistence** -- active_session table for crash recovery.
14. **Exercise reordering** -- Bottom sheet with drag-to-reorder.

### Phase 4: History + Polish
15. **HistoryListViewModel + Screen** -- Browse completed workouts.
16. **HistoryDetailScreen** -- View exercises/sets of a completed workout.
17. **Session resume prompt** -- Check for active session on app launch.

**Rationale:** You cannot test workout sessions without templates. You cannot test templates without a database. You cannot test history without completed workouts. This order ensures each phase can be tested end-to-end.

## Scalability Considerations

| Concern | Prototype (now) | If Backend Added Later |
|---------|-----------------|----------------------|
| Data storage | Room local only | Add Ktor HTTP client + sync repository layer |
| Authentication | None | Add auth module, token storage in expect/actual |
| Template source | Local CRUD | Sync from backend, local cache with Room |
| Workout upload | Not needed | Background WorkManager/iOS BGTask for upload |
| Architecture impact | Minimal | Repository interfaces absorb this: swap `LocalTemplateRepo` for `SyncingTemplateRepo` |

The repository pattern pays off here: when a backend is added, only the `data/repository/` implementations change. ViewModels and screens remain untouched.

## Sources

- [Kotlin Multiplatform Navigation docs](https://kotlinlang.org/docs/multiplatform/compose-navigation.html) -- Official navigation library setup (HIGH confidence)
- [Kotlin Multiplatform ViewModel docs](https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html) -- Official lifecycle-viewmodel-compose usage (HIGH confidence)
- [Android Room KMP setup](https://developer.android.com/kotlin/multiplatform/room) -- Official Room KMP dependencies and expect/actual pattern (HIGH confidence)
- [KMP Architecture Best Practices (Carrion.dev)](https://carrion.dev/en/posts/kmp-architecture/) -- Clean Architecture + MVVM for KMP (MEDIUM confidence)
- [Koin Compose Multiplatform docs](https://insert-koin.io/docs/reference/koin-compose/compose/) -- DI integration patterns (HIGH confidence)
- [Compose Multiplatform 1.10.0 release](https://blog.jetbrains.com/kotlin/2026/01/compose-multiplatform-1-10-0/) -- Navigation 3 and stable features (HIGH confidence)
- [ViewModel Scoping APIs (Android)](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-apis) -- Navigation-scoped ViewModel pattern (HIGH confidence)
- Firmware reference: `/Users/olli/schenanigans/gymtracker/firmware/src/statemachine/` -- FSM states, transitions, data models (direct inspection)
