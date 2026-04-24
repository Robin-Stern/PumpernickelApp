# Phase 2: Template Management - Research

**Researched:** 2026-03-28
**Domain:** Room KMP entities/relations, MVVM template CRUD, SwiftUI drag-and-drop reordering
**Confidence:** HIGH

## Summary

Phase 2 adds full CRUD for workout templates on top of the existing Phase 1 foundation. The core work is: (1) two new Room entities (`WorkoutTemplateEntity`, `TemplateExerciseEntity`) with a foreign key relationship, (2) a new DAO and repository layer, (3) two new ViewModels (template list + template editor), (4) SwiftUI views for template list, template editor with inline target configuration, and an exercise picker mode, and (5) a Room database migration from version 1 to version 2.

The existing codebase provides clear patterns for every layer: entity definition (`ExerciseEntity`), DAO (`ExerciseDao`), repository (`ExerciseRepository`/`ExerciseRepositoryImpl`), ViewModel with `@NativeCoroutinesState` (`ExerciseCatalogViewModel`, `CreateExerciseViewModel`), Koin DI wiring (`SharedModule` + `KoinHelper`), and SwiftUI view with async flow observation (`ExerciseCatalogView`). All new code follows these established patterns directly. The gymtracker Rust backend provides the exact data model schema to mirror (field names, types, relationships).

**Primary recommendation:** Use `fallbackToDestructiveMigration()` for the v1-to-v2 schema change since this is a prototype with no production users. Define entities with `@ForeignKey(onDelete = CASCADE)` for referential integrity. Follow the existing Phase 1 patterns exactly for ViewModel/repository/DI/SwiftUI layers.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Template list becomes the Workout tab home screen, replacing the Phase 1 empty state (WorkoutEmptyStateView). If no templates exist, show an empty state with a "Create Template" CTA.
- **D-02:** "Browse Exercises" moves from being the primary CTA to being accessible within the template creation/editing flow (exercise picker).
- **D-03:** Reuse the existing ExerciseCatalogView in a picker/selection mode when adding exercises to a template. Same anatomy SVG filter and search, but tapping an exercise adds it to the template rather than navigating to detail.
- **D-04:** Exercises are added one at a time. After selecting an exercise, the user returns to the template editor where they can configure targets for that exercise and add more.
- **D-05:** Target configuration (sets, reps, weight, rest period) is done inline in the template editor view. Each exercise row in the template shows editable target fields.
- **D-06:** Weight stored as integer kg x 10 (matching gymtracker's `target_weight_kg_x10` pattern). Display logic converts for the user. Lbs support deferred to Phase 4 (NAV-02, NAV-03).
- **D-07:** Rest period stored in seconds (matching gymtracker's `rest_period_sec`).
- **D-08:** Sensible defaults when adding an exercise to a template: 3 sets, 10 reps, 0 kg (user must set weight), 90 seconds rest.
- **D-09:** Two new Room entities following gymtracker's schema: `WorkoutTemplateEntity` (id, name, createdAt, updatedAt) and `TemplateExerciseEntity` (id, templateId, exerciseId, targetSets, targetReps, targetWeightKgX10, restPeriodSec, exerciseOrder).
- **D-10:** Exercise order is an integer field. Reordering updates the `exerciseOrder` of affected rows.
- **D-11:** Template deletion uses a standard iOS destructive alert dialog ("Delete Template? This cannot be undone.").
- **D-12:** SwiftUI List with `.onMove` modifier for drag-and-drop exercise reordering within a template (TMPL-05). KMP ViewModel exposes a `moveExercise(from, to)` function that updates order indices.

### Claude's Discretion
- Database migration strategy (Room auto-migration vs manual)
- ViewModel state management patterns (follow Phase 1 patterns with StateFlow + @NativeCoroutinesState)
- Navigation between template list -> template editor -> exercise picker (follow Phase 1 navigation patterns)
- Template name validation rules (non-empty, reasonable length)
- Whether to use SwiftUI sheets or full-screen navigation for template creation/editing

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| TMPL-01 | User can create a workout template with a name and a list of exercises | Room entities (D-09), TemplateRepository with create/addExercise, TemplateEditorViewModel, exercise picker reusing ExerciseCatalogView (D-03) |
| TMPL-02 | Each exercise in a template has target sets, target reps, target weight, and rest period duration | TemplateExerciseEntity fields (D-09), inline editing in template editor (D-05), defaults (D-08), weight as kg x 10 (D-06) |
| TMPL-03 | User can edit an existing template (rename, add/remove exercises, change targets) | TemplateRepository update/delete exercise methods, TemplateEditorViewModel loads existing template by ID |
| TMPL-04 | User can delete a template with confirmation | TemplateRepository.deleteTemplate, iOS destructive alert dialog (D-11), CASCADE delete handles child rows |
| TMPL-05 | User can reorder exercises within a template via drag-and-drop | SwiftUI List .onMove (D-12), ViewModel moveExercise(from, to) updates exerciseOrder indices |
</phase_requirements>

## Standard Stack

### Core (Already in Project)
| Library | Version | Purpose | Status |
|---------|---------|---------|--------|
| Room KMP | 2.8.4 | Database entities, DAOs, migrations | Already configured |
| Koin | 4.2.0 | DI for new DAOs, repos, ViewModels | Already configured |
| ViewModel KMP | 2.10.0 | Template list + editor ViewModels | Already configured |
| KMP-NativeCoroutines | 1.0.2 | @NativeCoroutinesState for iOS | Already configured |
| kotlinx-datetime | 0.7.1 | createdAt/updatedAt timestamps | Already configured |
| kotlinx-coroutines | 1.10.2 | Flow, suspend functions | Already configured |

### No New Dependencies
This phase requires zero new libraries. All needed functionality is covered by the existing stack.

## Architecture Patterns

### Recommended Project Structure (New Files)
```
shared/src/commonMain/kotlin/com/pumpernickel/
  data/
    db/
      WorkoutTemplateEntity.kt          # NEW: @Entity with PrimaryKey
      TemplateExerciseEntity.kt         # NEW: @Entity with ForeignKey
      WorkoutTemplateDao.kt             # NEW: @Dao interface
      AppDatabase.kt                    # MODIFY: add entities, version bump, DAO getter
    repository/
      TemplateRepository.kt             # NEW: interface + impl
  domain/
    model/
      WorkoutTemplate.kt               # NEW: domain model + toDomain() mappers
  presentation/
    templates/
      TemplateListViewModel.kt          # NEW: list screen state
      TemplateEditorViewModel.kt        # NEW: create/edit screen state
  di/
    SharedModule.kt                     # MODIFY: register new DAOs, repos, VMs

shared/src/iosMain/kotlin/com/pumpernickel/di/
    KoinHelper.kt                       # MODIFY: add VM getters

iosApp/iosApp/Views/
  Templates/
    TemplateListView.swift              # NEW: template list (Workout tab home)
    TemplateEditorView.swift            # NEW: create/edit template
    ExercisePickerView.swift            # NEW: wrapper around ExerciseCatalogView in picker mode
  Common/
    WorkoutEmptyStateView.swift         # KEEP but no longer used as Workout tab root
  MainTabView.swift                     # MODIFY: Workout tab -> TemplateListView
```

### Pattern 1: Room Entity with ForeignKey (D-09)
**What:** Define `TemplateExerciseEntity` with a foreign key pointing to `WorkoutTemplateEntity`, with CASCADE delete so deleting a template automatically deletes its exercises.
**When to use:** Parent-child relationships where child rows are owned by parent.
**Example:**
```kotlin
// Source: Room ForeignKey documentation + gymtracker schema
@Entity(
    tableName = "workout_templates"
)
data class WorkoutTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,  // epoch millis via kotlinx-datetime
    val updatedAt: Long
)

@Entity(
    tableName = "template_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("templateId")]
)
data class TemplateExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val exerciseId: String,     // References exercises.id (String in existing schema)
    val targetSets: Int,        // Default: 3
    val targetReps: Int,        // Default: 10
    val targetWeightKgX10: Int, // kg * 10, default: 0
    val restPeriodSec: Int,     // seconds, default: 90
    val exerciseOrder: Int      // 0-based ordering
)
```

**Critical note on `exerciseId` type:** The existing `ExerciseEntity` uses `String` for its `@PrimaryKey` (e.g., `"Barbell_Bench_Press_-_Medium_Grip"` and `"custom_xyz_123"`). The `TemplateExerciseEntity.exerciseId` must be `String` to match. This differs from the gymtracker backend which uses `i32` for exercise IDs. Do NOT use `@ForeignKey` for the exercise reference -- it would require Room to enforce referential integrity on a string key across tables, which works but adds complexity. Instead, rely on the application layer to ensure the exercise exists (it always will since exercises cannot be deleted in the current app).

### Pattern 2: DAO with Flow for Reactive Lists (Existing Pattern)
**What:** DAO returning `Flow<List<T>>` for reactive observation, suspend functions for mutations.
**When to use:** All read operations that feed UI state.
**Example:**
```kotlin
// Source: existing ExerciseDao pattern
@Dao
interface WorkoutTemplateDao {
    @Query("SELECT * FROM workout_templates ORDER BY updatedAt DESC")
    fun getAllTemplates(): Flow<List<WorkoutTemplateEntity>>

    @Query("SELECT * FROM workout_templates WHERE id = :id")
    fun getTemplateById(id: Long): Flow<WorkoutTemplateEntity?>

    @Query("SELECT * FROM template_exercises WHERE templateId = :templateId ORDER BY exerciseOrder ASC")
    fun getTemplateExercises(templateId: Long): Flow<List<TemplateExerciseEntity>>

    @Insert
    suspend fun insertTemplate(template: WorkoutTemplateEntity): Long

    @Insert
    suspend fun insertTemplateExercise(exercise: TemplateExerciseEntity): Long

    @Query("UPDATE workout_templates SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTemplateName(id: Long, name: String, updatedAt: Long)

    @Query("DELETE FROM workout_templates WHERE id = :id")
    suspend fun deleteTemplate(id: Long)

    @Query("DELETE FROM template_exercises WHERE id = :id")
    suspend fun deleteTemplateExercise(id: Long)

    @Query("UPDATE template_exercises SET targetSets = :sets, targetReps = :reps, targetWeightKgX10 = :weight, restPeriodSec = :rest WHERE id = :id")
    suspend fun updateExerciseTargets(id: Long, sets: Int, reps: Int, weight: Int, rest: Int)

    @Query("UPDATE template_exercises SET exerciseOrder = :order WHERE id = :id")
    suspend fun updateExerciseOrder(id: Long, order: Int)
}
```

### Pattern 3: Domain Model with Mapper (Existing Pattern)
**What:** Separate domain model from entity, with `toDomain()` extension function.
**Example:**
```kotlin
// Source: existing Exercise.kt / ExerciseEntity.toDomain() pattern
data class WorkoutTemplate(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val exercises: List<TemplateExercise> = emptyList()
)

data class TemplateExercise(
    val id: Long,
    val exerciseId: String,
    val exerciseName: String,      // Joined from exercises table
    val primaryMuscles: List<MuscleGroup>,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKgX10: Int,
    val restPeriodSec: Int,
    val exerciseOrder: Int
)

fun WorkoutTemplateEntity.toDomain(
    exercises: List<TemplateExercise> = emptyList()
) = WorkoutTemplate(
    id = id, name = name, createdAt = createdAt,
    updatedAt = updatedAt, exercises = exercises
)
```

### Pattern 4: ViewModel with MutableStateFlow (Existing Pattern)
**What:** ViewModel exposes `StateFlow` with `@NativeCoroutinesState` for iOS observation.
**Example:**
```kotlin
// Source: existing ExerciseCatalogViewModel + CreateExerciseViewModel patterns
class TemplateListViewModel(
    private val repository: TemplateRepository
) : ViewModel() {

    @NativeCoroutinesState
    val templates: StateFlow<List<WorkoutTemplate>> = repository
        .getAllTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteTemplate(id: Long) {
        viewModelScope.launch { repository.deleteTemplate(id) }
    }
}
```

### Pattern 5: SwiftUI View with KoinHelper + Async Observation (Existing Pattern)
**What:** SwiftUI views get ViewModel from KoinHelper, observe flows via `asyncSequence`.
**Example:**
```swift
// Source: existing ExerciseCatalogView.swift pattern
struct TemplateListView: View {
    private let viewModel = KoinHelper.shared.getTemplateListViewModel()
    @State private var templates: [WorkoutTemplate] = []

    var body: some View {
        // ... List of templates ...
    }

    private func observeTemplates() async {
        do {
            for try await value in asyncSequence(for: viewModel.templates) {
                self.templates = value
            }
        } catch { print("Error: \(error)") }
    }
}
```

### Pattern 6: SwiftUI .onMove for Drag-and-Drop (D-12)
**What:** SwiftUI List with ForEach and `.onMove` modifier for reordering exercises in the template editor.
**When to use:** TMPL-05 drag-and-drop reordering.
**Example:**
```swift
// Source: SwiftUI .onMove documentation
List {
    ForEach(Array(exercises.enumerated()), id: \.element.id) { index, exercise in
        TemplateExerciseRow(exercise: exercise, /* ... */)
    }
    .onMove { source, destination in
        // source: IndexSet of dragged item(s)
        // destination: Int target index
        exercises.move(fromOffsets: source, toOffset: destination)
        // Persist to ViewModel
        if let from = source.first {
            viewModel.moveExercise(from: Int32(from), to: Int32(destination))
        }
    }
}
.environment(\.editMode, .constant(.active))  // Always show drag handles
```

**Key detail:** `.onMove` only works inside `ForEach` within a `List`. The `EditMode` must be active for drag handles to appear. Setting `.environment(\.editMode, .constant(.active))` makes them always visible without needing an Edit button.

### Anti-Patterns to Avoid
- **Using @Relation for template-exercises loading:** Room's `@Relation` with `@Embedded` triggers multiple queries and returns non-reactive results (not wrapped in `Flow`). Instead, query templates and exercises separately as `Flow` and combine in the repository or ViewModel. This gives better control and works naturally with the reactive pattern.
- **Storing exercise name in TemplateExerciseEntity:** The exercise name should be joined at query time or looked up from the exercise table, not duplicated in the template exercise table. This prevents stale data if an exercise name is ever edited.
- **Using Float for weight:** Decision D-06 locks weight to `Int` (kg x 10). Never use Float/Double for weight values -- integer math avoids floating-point comparison issues.
- **Putting business logic in SwiftUI views:** All CRUD operations, validation, and state management go through the KMP ViewModel. SwiftUI views only observe state and dispatch actions.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Database schema migration | Manual SQL CREATE TABLE statements | Room `fallbackToDestructiveMigration()` or `autoMigrations` | Prototype has no user data to preserve; Room handles schema diffing |
| Reactive data observation | Manual callbacks or polling | Room DAO returning `Flow` + `@NativeCoroutinesState` | Already established pattern; Room auto-invalidates on writes |
| Foreign key cascade delete | Manual delete of child rows before parent | `@ForeignKey(onDelete = CASCADE)` on entity | SQLite handles this atomically |
| List reorder persistence | Custom drag gesture recognizer + manual index tracking | SwiftUI `.onMove` modifier | Built-in iOS behavior, handles animation and gesture |
| ID generation | UUID strings for template IDs | Room `@PrimaryKey(autoGenerate = true)` Long | Consistent with Room conventions; gymtracker uses auto-increment integers |

## Common Pitfalls

### Pitfall 1: Room Version Bump Without Migration Strategy
**What goes wrong:** Adding new entities to `@Database` without changing the version number causes a crash on app launch (schema mismatch). Changing version without providing migration causes `IllegalStateException`.
**Why it happens:** Room validates schema at runtime. If the database file exists with version 1 but the code declares version 2, Room needs a migration path.
**How to avoid:** For this prototype, use `fallbackToDestructiveMigration()` on the database builder. This drops and recreates all tables when schema changes. Acceptable because there are no production users and the exercise data is re-seeded automatically.
**Warning signs:** `IllegalStateException: A migration from 1 to 2 was required but not found` at app startup.

### Pitfall 2: Missing @Index on Foreign Key Column
**What goes wrong:** Room warns (or in strict mode, errors) if a foreign key column is not indexed. Performance degrades on JOIN queries.
**Why it happens:** SQLite does not automatically index foreign key columns.
**How to avoid:** Always add `indices = [Index("templateId")]` to the entity annotation when using `@ForeignKey`.
**Warning signs:** Room compiler warning about unindexed foreign key columns.

### Pitfall 3: SwiftUI .onMove Not Working
**What goes wrong:** Drag handles don't appear, or `.onMove` closure is never called.
**Why it happens:** `.onMove` requires `ForEach` to be a direct child of `List`, and `EditMode` must be active.
**How to avoid:** Ensure `ForEach` is inside `List` (not `ScrollView`), and set `.environment(\.editMode, .constant(.active))` to always show drag handles. Alternatively, use a toolbar `EditButton()`.
**Warning signs:** List renders correctly but no drag handles visible; long-press does nothing.

### Pitfall 4: exerciseOrder Gaps After Reorder
**What goes wrong:** After multiple reorders, exerciseOrder values become non-contiguous (e.g., 0, 3, 7). This can cause unexpected ordering.
**Why it happens:** Naive reorder implementations only update the moved item, not surrounding items.
**How to avoid:** After a reorder, normalize all exerciseOrder values to be contiguous (0, 1, 2, ...). The ViewModel's `moveExercise(from, to)` should reindex all exercises in the list.
**Warning signs:** Exercises appear out of order after multiple reorder operations.

### Pitfall 5: ViewModel Singleton vs Factory Scope in Koin
**What goes wrong:** Template editor ViewModel retains state from a previous template when navigating to edit a different template.
**Why it happens:** Koin `viewModel {}` creates a new instance per injection, but if the same instance is cached (e.g., through SwiftUI's KoinHelper singleton pattern), stale state persists.
**How to avoid:** For `TemplateEditorViewModel`, use Koin `factory {}` instead of `viewModel {}` if multiple instances are needed simultaneously. Alternatively, expose a `reset()` or `loadTemplate(id)` function and call it in the view's `.task {}` modifier (same pattern as `ExerciseDetailViewModel.loadExercise(id)`).
**Warning signs:** Opening template B shows template A's exercises briefly before updating.

### Pitfall 6: Timestamp Generation in KMP
**What goes wrong:** Using `kotlinx.datetime.Clock.System` which is deprecated; or using platform-specific time APIs.
**Why it happens:** The kotlinx-datetime API changed.
**How to avoid:** Use `kotlin.time.Clock.System.now().epochSeconds` (or `.toEpochMilliseconds()` via Instant) as established in Phase 1 (see `CreateExerciseViewModel`). Store as `Long` epoch millis in the entity.
**Warning signs:** Compilation warning about deprecated Clock usage.

## Code Examples

### Database Migration Strategy (Recommended: Destructive for Prototype)
```kotlin
// In Database.ios.kt and Database.android.kt
// Source: Room KMP documentation
fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFilePath = NSHomeDirectory() + "/Documents/pumpernickel.db"
    return Room.databaseBuilder<AppDatabase>(dbFilePath)
        // No migration needed -- drops and recreates on version change
        // Safe because: prototype with no production users
        // Exercise data re-seeds automatically via DatabaseSeeder
}

// In SharedModule.kt (database builder configuration)
single<AppDatabase> {
    get<RoomDatabase.Builder<AppDatabase>>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
}
```

### Updated AppDatabase (Version Bump)
```kotlin
// Source: existing AppDatabase.kt + Room documentation
@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutTemplateEntity::class,
        TemplateExerciseEntity::class
    ],
    version = 2
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutTemplateDao(): WorkoutTemplateDao
}
```

### Koin DI Wiring (New Registrations)
```kotlin
// Source: existing SharedModule.kt pattern
val sharedModule = module {
    // ... existing registrations ...

    // New DAO
    single<WorkoutTemplateDao> { get<AppDatabase>().workoutTemplateDao() }

    // New Repository
    single<TemplateRepository> { TemplateRepositoryImpl(get(), get()) }

    // New ViewModels
    viewModel { TemplateListViewModel(get()) }
    viewModel { TemplateEditorViewModel(get(), get()) }
}
```

### KoinHelper Additions (iOS)
```kotlin
// Source: existing KoinHelper.kt pattern
object KoinHelper {
    // ... existing getters ...

    fun getTemplateListViewModel(): TemplateListViewModel =
        KoinPlatform.getKoin().get()

    fun getTemplateEditorViewModel(): TemplateEditorViewModel =
        KoinPlatform.getKoin().get()
}
```

### Template Editor ViewModel (Create + Edit Mode)
```kotlin
// Source: inspired by CreateExerciseViewModel + ExerciseDetailViewModel patterns
class TemplateEditorViewModel(
    private val templateRepository: TemplateRepository,
    private val exerciseRepository: ExerciseRepository
) : ViewModel() {

    private val _templateId = MutableStateFlow<Long?>(null)

    private val _name = MutableStateFlow("")
    @NativeCoroutinesState
    val name: StateFlow<String> = _name.asStateFlow()

    private val _exercises = MutableStateFlow<List<TemplateExercise>>(emptyList())
    @NativeCoroutinesState
    val exercises: StateFlow<List<TemplateExercise>> = _exercises.asStateFlow()

    @NativeCoroutinesState
    val isFormValid: StateFlow<Boolean> = combine(_name, _exercises) { name, exercises ->
        name.isNotBlank() && name.length <= 100
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Load existing template for editing
    fun loadTemplate(id: Long) {
        _templateId.value = id
        viewModelScope.launch {
            // Load and populate fields
        }
    }

    fun onNameChanged(name: String) { _name.value = name }

    fun addExercise(exerciseId: String, exerciseName: String, primaryMuscles: List<MuscleGroup>) {
        // Add with defaults (D-08): 3 sets, 10 reps, 0 weight, 90s rest
    }

    fun removeExercise(templateExerciseId: Long) { /* ... */ }

    fun updateExerciseTargets(id: Long, sets: Int, reps: Int, weightKgX10: Int, restSec: Int) { /* ... */ }

    fun moveExercise(from: Int, to: Int) {
        // Reorder in-memory list, normalize order indices
    }

    fun save() {
        viewModelScope.launch {
            // Create or update template + exercises
        }
    }
}
```

### Weight Display Helper
```kotlin
// Source: gymtracker pattern D-06
// targetWeightKgX10 = 725 means 72.5 kg
fun formatWeightKg(kgX10: Int): String {
    val whole = kgX10 / 10
    val decimal = kgX10 % 10
    return if (decimal == 0) "${whole} kg" else "${whole}.${decimal} kg"
}

// User input: "72.5" -> 725
fun parseWeightKgX10(input: String): Int? {
    val value = input.toDoubleOrNull() ?: return null
    return (value * 10).toInt()
}
```

## Discretion Recommendations

### Database Migration Strategy
**Recommendation: `fallbackToDestructiveMigration()`**
- This is a prototype with zero production users
- The only data lost on schema change is seeded exercises (auto-re-seeded on next launch) and any manually created exercises (acceptable loss during development)
- AutoMigration is technically possible for "add new table" changes and would work, but adds complexity (schema export files must be committed, build configuration changes). Not worth it for a prototype.
- If later phases need to preserve user data across migrations, switch to `autoMigrations` or manual `Migration` objects at that point.

### Navigation Strategy
**Recommendation: SwiftUI NavigationStack push navigation for template list -> editor, sheet for exercise picker**
- Template list -> Template editor: Full NavigationLink push (same as exercise catalog -> exercise detail in Phase 1)
- Template editor -> Exercise picker: `.sheet()` presentation. The picker is a transient selection context, not a new navigation level. Sheets are the iOS convention for "pick something and come back" flows.
- This matches D-04: after selecting an exercise, the user returns to the template editor.

### Template Name Validation
**Recommendation: Non-empty, trimmed, max 100 characters**
- Name must not be blank after trimming whitespace
- Maximum 100 characters (generous for a template name like "Push Day - Chest & Triceps")
- No uniqueness constraint (users may want "Push Day" variants)
- Validate in ViewModel (`isFormValid` StateFlow), disable Save button when invalid

### SwiftUI Sheets vs Full-Screen for Template Editor
**Recommendation: Full-screen push navigation**
- Template creation/editing involves multiple interactions (naming, adding exercises, configuring targets, reordering)
- A sheet would feel cramped and lacks a navigation stack for the exercise picker sub-flow
- Full NavigationLink push gives the editor a proper navigation bar with back button and save action

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `SupportSQLiteDatabase` in migrations | `SQLiteConnection` in KMP migrations | Room 2.7.0 (April 2025) | KMP migrations use `connection.execSQL()` not `database.execSQL()` |
| `@Transaction` annotation on DAO methods | `useWriterConnection { immediateTransaction {} }` for complex transactions | Room 2.7.0 (April 2025) | KMP does not support `withTransaction{}` -- use `useWriterConnection` |
| `kotlinx.datetime.Clock.System` | `kotlin.time.Clock.System` | Kotlin 2.x | Phase 1 already uses the correct API |

**Note on @Transaction:** For simple insert/update/delete operations, Room handles transactions automatically. The `useWriterConnection` pattern is only needed for complex multi-step operations that must be atomic (e.g., saving a template and all its exercises). For this phase, most operations can use individual DAO suspend functions. Batch reorder (updating multiple exerciseOrder values) should use `@Transaction` on a DAO method that performs all updates.

## Open Questions

1. **Exercise picker: new ViewModel or reuse ExerciseCatalogViewModel?**
   - What we know: ExerciseCatalogView already has search + anatomy filter via ExerciseCatalogViewModel. The picker needs the same browsing but with a "select" action instead of "navigate to detail."
   - What's unclear: Whether to create a thin wrapper ViewModel or just use the same ViewModel with a different SwiftUI view.
   - Recommendation: Reuse `ExerciseCatalogViewModel` as-is. The SwiftUI `ExercisePickerView` wraps `ExerciseCatalogView`'s components but replaces `NavigationLink` with a tap-to-select action. No new KMP ViewModel needed for the picker -- the selection action is handled entirely in SwiftUI (dismiss sheet with selected exercise ID, name, muscles).

2. **Template with exercises: single save vs incremental persistence?**
   - What we know: The gymtracker backend uses individual API calls (create template, then add exercises one by one). The mobile app could either save everything at once on "Save" or persist incrementally.
   - What's unclear: UX tradeoff between save-all-at-once (simpler, user can cancel) vs incremental (no data loss if app crashes mid-edit).
   - Recommendation: Save-all-at-once for create mode (create template + insert all exercises in one transaction when user taps Save). For edit mode, persist changes immediately (updating targets, adding/removing exercises hit the DB right away since the template already exists). This gives the best UX: creation can be cancelled, but edits are never lost.

## Project Constraints (from CLAUDE.md)

- **Tech stack:** Kotlin Multiplatform + Compose Multiplatform -- all shared logic in commonMain
- **Platform focus:** iOS first -- SwiftUI views are the primary UI
- **Storage:** Room KMP 2.8.4 (local only, no backend)
- **Architecture:** MVVM with ViewModel exposing StateFlow, observed in SwiftUI via @NativeCoroutinesState
- **DI:** Koin 4.2.0 with viewModel {} DSL, KoinHelper for iOS
- **Navigation:** SwiftUI NavigationStack (Phase 1 pattern)
- **No new dependencies** should be added for this phase
- **GSD workflow** must be followed for all changes

## Sources

### Primary (HIGH confidence)
- Existing codebase: `AppDatabase.kt`, `ExerciseEntity.kt`, `ExerciseDao.kt`, `ExerciseRepository.kt`, `SharedModule.kt`, `KoinHelper.kt`, `ExerciseCatalogViewModel.kt`, `CreateExerciseViewModel.kt`, `ExerciseDetailViewModel.kt` -- all patterns verified by reading source
- Gymtracker Rust backend: `templates.rs`, `workout.rs` -- exact field names and types for template data model
- iOS views: `ExerciseCatalogView.swift`, `ExerciseDetailView.swift`, `MainTabView.swift` -- UI patterns verified
- Room schema v1 JSON: verified current schema structure
- `libs.versions.toml` + `shared/build.gradle.kts`: verified all dependency versions

### Secondary (MEDIUM confidence)
- [Room KMP setup guide](https://developer.android.com/kotlin/multiplatform/room) -- DAO constraints, migration API for KMP
- [Room migration documentation](https://developer.android.com/training/data-storage/room/migrating-db-versions) -- autoMigrations syntax, fallbackToDestructiveMigration
- [Room ForeignKey API reference](https://developer.android.com/reference/android/arch/persistence/room/ForeignKey) -- CASCADE behavior
- [SwiftUI .onMove documentation](https://sarunw.com/posts/swiftui-list-onmove/) -- drag-and-drop reordering implementation
- [Hacking with Swift .onMove guide](https://www.hackingwithswift.com/quick-start/swiftui/how-to-let-users-move-rows-in-a-list) -- EditMode requirements

### Tertiary (LOW confidence)
None -- all findings verified with either codebase or official documentation.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- no new dependencies; all already in project and verified working
- Architecture: HIGH -- directly extends existing Phase 1 patterns with no novel patterns
- Pitfalls: HIGH -- migration and SwiftUI .onMove are well-documented; gymtracker schema is proven
- Data model: HIGH -- directly mirrors gymtracker's proven schema with exact field names

**Research date:** 2026-03-28
**Valid until:** 2026-04-28 (stable -- no fast-moving dependencies)
