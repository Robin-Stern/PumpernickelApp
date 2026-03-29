# Phase 3: Workout Session - Context

**Gathered:** 2026-03-28
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver the core workout execution flow. Users can select a template and execute it set-by-set — logging reps and weight, resting between sets with a countdown timer, tracking progress (exercise X of Y, set X of Y, elapsed duration), and saving the completed workout. The session persists across app crashes and can be resumed on next launch. This is the heart of the app — the primary user interaction loop.

</domain>

<decisions>
## Implementation Decisions

### Workout Navigation Flow
- **D-01:** Linear progression, exercise-by-exercise, set-by-set — mirrors the gymtracker firmware FSM. Default flow advances automatically from set → rest → next set → next exercise.
- **D-02:** User can view an exercise overview and tap to jump to any exercise (non-sequential access), but the default progression is linear.
- **D-03:** Workout starts from the template list — tapping a template offers "Start Workout" which loads the template into an active session.

### Rest Timer Behavior & Alerts
- **D-04:** Rest timer auto-starts immediately after the user marks a set as complete (per WORK-04). Countdown matches the exercise's configured `restPeriodSec`.
- **D-05:** Skip button available during rest — the timer is a guide, not a gate. User can proceed to the next set early.
- **D-06:** No extend/pause controls — keep the timer simple. User can skip if they rested enough.
- **D-07:** Alert via haptic vibration when rest period ends (WORK-05). Foreground haptic only for prototype — background local notifications (UNUserNotificationCenter) deferred to v2.
- **D-08:** Timer displayed inline within the workout screen as a prominent countdown — no full-screen takeover. Current exercise context remains visible.

### Set Logging UX
- **D-09:** Reps and weight pre-filled from template targets for each set. User adjusts only what differs from the plan.
- **D-10:** Individual set confirmation — user marks each set complete one at a time (WORK-03), which triggers the rest timer (D-04).
- **D-11:** User can tap a completed set within the current exercise to edit its reps/weight retroactively.
- **D-12:** Weight input uses the established kg×10 integer pattern (Phase 2 D-06). Display as decimal kg (e.g., "50.5 kg"). Lbs toggle deferred to Phase 4.

### Session Persistence & Crash Recovery
- **D-13:** Active session state saved to Room after every set completion — most granular and resilient approach for WORK-09.
- **D-14:** On app launch, check for an unfinished session entity. If found, prompt user: "Resume workout?" or "Discard" — auto-detection, no manual recovery needed.
- **D-15:** Active session entity stores: template reference, current exercise index, completed sets with actual reps/weight, start time, and last-updated timestamp.

### Workout Completion & Storage
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

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Gymtracker Workout Model (Reference Implementation)
- `/Users/olli/schenanigans/gymtracker/api/src/api/workout.rs` — Workout, WorkoutExercise, Set request structures (field names, types, nesting pattern)
- `/Users/olli/schenanigans/gymtracker/api/src/repository/workout.rs` — Workout persistence: transaction-based batch insert, sync_id deduplication
- `/Users/olli/schenanigans/gymtracker/api/migrations/20260118_init_schema.sql` — Database schema: workouts → workout_exercises → sets (nested 1:N:N)

### Existing Codebase (Phase 1+2 Patterns)
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt` — Room database v2, needs new entities and DAOs for workout session
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/WorkoutTemplateEntity.kt` — Template entity pattern to follow
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/TemplateExerciseEntity.kt` — Template exercise with targets (sets, reps, weightKgX10, restPeriodSec)
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/WorkoutTemplateDao.kt` — DAO pattern with Flow reads + suspend writes
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/TemplateRepository.kt` — Repository pattern (interface + impl, domain mapping)
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/WorkoutTemplate.kt` — Domain models with toDomain() extensions
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/templates/TemplateEditorViewModel.kt` — Complex ViewModel with multiple StateFlows, save/load patterns
- `shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt` — Koin DI registration pattern
- `shared/src/iosMain/kotlin/com/pumpernickel/di/KoinHelper.kt` — iOS ViewModel exposure pattern
- `iosApp/iosApp/Views/Templates/TemplateListView.swift` — SwiftUI list with Flow observation pattern
- `iosApp/iosApp/Views/Templates/TemplateEditorView.swift` — Complex SwiftUI form with multiple async observations

### Project Specs
- `.planning/REQUIREMENTS.md` — Phase 3 requirements: WORK-01 through WORK-09
- `.planning/ROADMAP.md` — Phase 3 success criteria and scope
- `CLAUDE.md` — Technology stack and version constraints

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **WorkoutTemplateEntity + TemplateExerciseEntity**: Template data with targets (sets, reps, weightKgX10, restPeriodSec) — workout session loads these as the "plan" for execution
- **TemplateRepository**: Fetch template with exercises to initialize a workout session
- **TemplateEditorViewModel**: Pattern for complex multi-state ViewModel with @NativeCoroutinesState — workout session VM will be similarly complex
- **KoinHelper + SharedModule**: Established DI wiring for adding new DAOs, repositories, and ViewModels
- **SwiftUI Flow observation pattern**: `asyncSequence(for: viewModel.xyzFlow)` with TaskGroup — reuse for workout state observation

### Established Patterns
- **Architecture**: KMP shared business logic + SwiftUI iOS UI
- **State management**: ViewModel with MutableStateFlow, exposed as StateFlow with @NativeCoroutinesState, *Flow suffix convention for iOS
- **DI**: Koin with sharedModule, platformModule, KoinHelper for iOS access
- **Data layer**: Entity → Domain model via toDomain() extension, repository interface + impl
- **Timestamps**: `kotlin.time.Clock.System.now().toEpochMilliseconds()` for epoch millis
- **Room**: Schema version 2, BundledSQLiteDriver, fallbackToDestructiveMigration
- **iOS navigation**: NavigationStack + NavigationLink, TabView for bottom nav

### Integration Points
- **AppDatabase**: Needs new entities (ActiveSession, CompletedWorkout, WorkoutSet) and DAOs — schema version bump to 3
- **SharedModule (Koin)**: Register new DAOs, WorkoutRepository, WorkoutSessionViewModel
- **KoinHelper**: Add getter for WorkoutSessionViewModel
- **TemplateListView**: Needs "Start Workout" action to launch workout session flow
- **MainTabView**: May need to handle active session state (e.g., showing active workout indicator)

</code_context>

<specifics>
## Specific Ideas

- The workout FSM mirrors gymtracker's firmware flow: template selection → SET N → reps/weight entry → rest timer countdown → next set/exercise → finish → save
- Pre-filling from template targets reduces friction — the user is executing a plan, not building one from scratch
- Crash recovery via per-set persistence means the worst case is losing only the current in-progress set, never a whole workout
- The rest timer is a guide (skippable) rather than a gate — experienced lifters know when they're ready
- Haptic vibration for timer alerts keeps it simple for the prototype; local notifications for background alerts are a natural v2 enhancement

</specifics>

<deferred>
## Deferred Ideas

- **Background rest timer notifications** (UNUserNotificationCenter) — noted in STATE.md as a research spike candidate; deferred to v2 as foreground haptic is sufficient for prototype
- **RPE (Rate of Perceived Exertion) per set** — useful but not in WORK-01–09 requirements; belongs in v2 WORK-12
- **Superset grouping** — explicitly v2 (WORK-13)
- **Ad-hoc workout without template** — explicitly v2 (WORK-10)
- **Set tagging (warm-up, drop set, failure)** — explicitly v2 (WORK-12)

</deferred>

---

*Phase: 03-workout-session*
*Context gathered: 2026-03-28*
