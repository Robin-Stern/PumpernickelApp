# Phase 1: Foundation & Exercise Catalog - Research

**Researched:** 2026-03-28
**Domain:** KMP project setup, Room database with seeding, native UI interop (SwiftUI/Compose), anatomy SVG porting
**Confidence:** HIGH

## Summary

Phase 1 builds the entire KMP foundation from scratch (greenfield project -- no existing source files) and delivers a browsable exercise catalog with ~873 seeded exercises. The critical architectural decision (D-01) switches from Compose Multiplatform shared UI to KMP shared business logic with platform-native UI (SwiftUI for iOS, Compose for Android, iOS-first). This changes the Swift interop story significantly: the project now NEEDS a Flow-to-Swift bridge library, which the original CLAUDE.md explicitly said was unnecessary.

The primary technical challenges are: (1) KMP project scaffolding with proper multi-target Gradle setup, (2) Room KMP database with first-launch JSON seeding of 873 exercises (~1MB), (3) porting anatomy SVG path data (~255 paths across front/back body views) to SwiftUI Shape/Path and Compose Canvas, and (4) bridging Kotlin StateFlow to SwiftUI observation. A muscle group mismatch between the exercise DB (17 groups including "abductors" and "middle back") and the anatomy SVG (16 groups with "obliques" but no "abductors"/"middle back") must be resolved with a mapping layer.

**Primary recommendation:** Use KMP-NativeCoroutines 1.0.2 (not SKIE) for Swift interop since SKIE does not yet support Kotlin 2.3.20. Use Room KMP 2.8.4 with RoomDatabase.Callback.onCreate for JSON seeding. Use the nicklockwood/SVGPath Swift package to parse SVG path data strings in SwiftUI. Port exercise data as a bundled JSON resource with expect/actual file reading.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** KMP shared business logic + SwiftUI for iOS + Compose for Android (iOS-first). No Compose Multiplatform shared UI.
- **D-02:** Development order: business logic in KMP first, then iOS (SwiftUI), then Android (Compose) adaptation.
- **D-03:** Store ALL fields from gymtracker's `free_exercise_db.json`: name, force, level, mechanic, equipment, primaryMuscles, secondaryMuscles, instructions, category, images, id.
- **D-04:** Image paths stored in DB but NOT displayed in prototype.
- **D-05:** Custom and seeded exercises live in the same table with an `isCustom` flag. Custom exercises have nullable fields that seeded ones always fill.
- **D-06:** Seeded exercises are read-only (not editable or deletable by user).
- **D-07:** Flat scrollable list with search bar at top and muscle group filter chips.
- **D-08:** Each exercise row shows: name (primary text) + primary muscle group (subtitle/chip).
- **D-09:** Tapping an exercise navigates to a full detail screen showing all fields.
- **D-10:** Filter by muscle group only. Search handles the rest.
- **D-11:** Port the anatomy SVG from gymtracker's web component to a native component. Front and back body views with tappable muscle regions.
- **D-12:** The anatomy SVG is used EVERYWHERE muscle groups appear.
- **D-13:** Muscle region mapping follows gymtracker's muscleRegionMap.ts (16 muscle groups, ~30 individual regions mapped to groups).
- **D-14:** Required fields for custom exercise: name, primary muscle group, equipment, category. Selected via fixed lists derived from seeded data.
- **D-15:** Muscle group selection uses the anatomy SVG picker (per D-12).
- **D-16:** Bottom navigation with 3 tabs: Workout, Overview, Nutrition. Only Workout tab is functional.
- **D-17:** Workout tab home screen shows an empty state with CTA ("Browse Exercises" button).
- **D-18:** Overview and Nutrition placeholder tabs show styled empty states.
- **D-19:** Exercise catalog accessed via button on Workout tab home screen.
- **D-20:** Create Exercise action lives on a FAB (+) on the exercise catalog screen.

### Claude's Discretion
None -- all areas discussed and decided.

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| EXER-01 | App ships with a seeded exercise catalog sourced from gymtracker's `free_exercise_db.json` (~873 exercises with muscle groups, equipment, level) | Room KMP 2.8.4 with RoomDatabase.Callback.onCreate for first-launch seeding; kotlinx-serialization 1.10.0 for JSON parsing; expect/actual for resource file reading |
| EXER-02 | User can create custom exercises with name and primary muscle group | Room DAO @Insert with isCustom flag; anatomy SVG picker for muscle group selection; equipment/category dropdowns from seeded distinct values |
| EXER-03 | User can browse and search the exercise catalog when building templates or during workouts | Room DAO @Query with LIKE for text search + WHERE clause for muscle group filtering; StateFlow from DAO exposed to SwiftUI via KMP-NativeCoroutines |
| NAV-01 | App has bottom navigation with three tabs: Workout, Overview, Nutrition (only Workout functional) | SwiftUI TabView for iOS; Compose NavigationBar for Android; navigation state in shared ViewModel |
</phase_requirements>

---

## Standard Stack

### Core (from CLAUDE.md, verified)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin | 2.3.20 | Language | Latest stable per CLAUDE.md. Required for KMP. |
| Gradle (Kotlin DSL) | 8.x+ | Build system | Use version catalogs (libs.versions.toml). |
| Room KMP | 2.8.4 | Local SQLite database | Annotation-based DAOs. Stable KMP support for Android + iOS. |
| AndroidX SQLite Bundled | 2.6.2 | SQLite driver | BundledSQLiteDriver for consistent cross-platform behavior. |
| Koin | 4.2.0 | Dependency injection | Kotlin-first, no code generation. KMP support built-in. |
| kotlinx-serialization | 1.10.0 | JSON parsing, navigation routes | Required for parsing free_exercise_db.json and type-safe nav routes. |
| kotlinx-coroutines | 1.10.2 | Async/concurrency | StateFlow, Flow, coroutine scopes for ViewModels. |
| kotlinx-datetime | 0.7.1 | Date/time | Multiplatform date/time for timestamps. |
| Jetpack ViewModel (KMP) | 2.10.0 | Screen state holder | Lifecycle-aware state management in shared module. |
| Jetpack Lifecycle (KMP) | 2.10.0 | Lifecycle management | Required by ViewModel. |
| KSP | matching 2.3.20 | Code generation for Room | Must match Kotlin version (e.g., 2.3.20-1.0.x). |

### Swift Interop (NEW -- required by D-01 architecture change)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| KMP-NativeCoroutines | 1.0.2 | Kotlin Flow to Swift async/await bridge | Supports Kotlin 2.3.20. Converts StateFlow/Flow to Swift AsyncSequence. SKIE does NOT yet support Kotlin 2.3.20 (PR #180 pending). |

### iOS-Specific (Swift Package Manager)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| SVGPath (nicklockwood) | 1.3.0 | SVG path data parsing | Parses SVG d="" attribute strings into SwiftUI Path objects. Cross-platform Swift. Handles scaling via `in: rect` parameter. |
| KMPNativeCoroutinesAsync | 1.0.2 | Swift-side async helpers | Swift package companion to KMP-NativeCoroutines. Provides `asyncSequence(for:)`. |

### Navigation

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Jetpack Navigation Compose (KMP) | 2.9.2 | Android screen navigation | Only used on Android side. iOS uses SwiftUI NavigationStack natively. |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| KMP-NativeCoroutines | SKIE 0.10.10 | SKIE is more automatic (no annotations needed) but does NOT support Kotlin 2.3.20 yet. Switch to SKIE when 0.10.11+ ships with Kotlin 2.3.20 support. |
| KMP-NativeCoroutines | Manual FlowWrapper | Zero dependencies but significant boilerplate: need custom CommonFlow class, Swift ObservableObject adapter. KMP-NativeCoroutines generates this automatically. |
| SVGPath (Swift) | Manual path parsing | SVG path syntax has ~20 commands (M, L, C, Q, A, Z, etc.) with relative/absolute variants. Hand-rolling a parser is error-prone. |
| Room Callback seeding | createFromAsset | createFromAsset is NOT available in KMP common code (Android-only). Must use Callback.onCreate approach. |

**Installation (libs.versions.toml):**
```toml
[versions]
kotlin = "2.3.20"
room = "2.8.4"
sqlite = "2.6.2"
ksp = "2.3.20-1.0.31"  # Verify exact KSP version matching Kotlin 2.3.20
koin = "4.2.0"
coroutines = "1.10.2"
serialization = "1.10.0"
datetime = "0.7.1"
lifecycle = "2.10.0"
navigation = "2.9.2"
kmpNativeCoroutines = "1.0.2"

[libraries]
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
androidx-sqlite-bundled = { module = "androidx.sqlite:sqlite-bundled", version.ref = "sqlite" }
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-android = { module = "io.insert-koin:koin-android", version.ref = "koin" }
koin-compose-viewmodel = { module = "io.insert-koin:koin-compose-viewmodel", version.ref = "koin" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "datetime" }
lifecycle-viewmodel = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel", version.ref = "lifecycle" }

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
androidx-room = { id = "androidx.room", version.ref = "room" }
kotlinx-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
kmp-nativecoroutines = { id = "com.rickclephas.kmp.nativecoroutines", version.ref = "kmpNativeCoroutines" }
```

---

## Architecture Patterns

### Recommended Project Structure

```
PumpernickelApp/
+-- build.gradle.kts                    # Root build file
+-- settings.gradle.kts                  # Module declarations
+-- gradle/libs.versions.toml            # Version catalog
+-- shared/                              # KMP shared module
|   +-- build.gradle.kts                 # KMP targets: android, iosArm64, iosSimulatorArm64, iosX64
|   +-- src/
|   |   +-- commonMain/
|   |   |   +-- kotlin/com/pumpernickel/
|   |   |   |   +-- data/
|   |   |   |   |   +-- db/
|   |   |   |   |   |   +-- AppDatabase.kt          # @Database, @ConstructedBy
|   |   |   |   |   |   +-- ExerciseDao.kt           # @Dao interface
|   |   |   |   |   |   +-- ExerciseEntity.kt        # @Entity
|   |   |   |   |   |   +-- DatabaseSeeder.kt        # JSON parsing + insert logic
|   |   |   |   |   +-- repository/
|   |   |   |   |   |   +-- ExerciseRepository.kt    # Repository interface + impl
|   |   |   |   +-- domain/
|   |   |   |   |   +-- model/
|   |   |   |   |   |   +-- Exercise.kt              # Domain model (not entity)
|   |   |   |   |   |   +-- MuscleGroup.kt           # Enum of 16 muscle groups
|   |   |   |   |   |   +-- MuscleRegion.kt          # Enum of ~30 SVG regions -> groups
|   |   |   |   +-- presentation/
|   |   |   |   |   +-- exercises/
|   |   |   |   |   |   +-- ExerciseCatalogViewModel.kt
|   |   |   |   |   |   +-- ExerciseDetailViewModel.kt
|   |   |   |   |   |   +-- CreateExerciseViewModel.kt
|   |   |   |   +-- di/
|   |   |   |   |   +-- SharedModule.kt              # Koin module (DB, repos, VMs)
|   |   |   |   +-- Platform.kt                      # expect declarations
|   |   |   +-- resources/
|   |   |   |   +-- free_exercise_db.json             # Bundled seed data (~1MB)
|   |   +-- androidMain/
|   |   |   +-- kotlin/com/pumpernickel/
|   |   |   |   +-- Platform.android.kt              # actual implementations
|   |   |   |   +-- data/db/Database.android.kt       # getDatabaseBuilder(context)
|   |   |   |   +-- di/PlatformModule.android.kt      # Android Koin module
|   |   +-- iosMain/
|   |   |   +-- kotlin/com/pumpernickel/
|   |   |   |   +-- Platform.ios.kt                   # actual implementations
|   |   |   |   +-- data/db/Database.ios.kt            # getDatabaseBuilder() using NSFileManager
|   |   |   |   +-- di/PlatformModule.ios.kt           # iOS Koin module
|   |   |   |   +-- di/KoinInitIos.kt                  # initKoinIos() entry point
+-- androidApp/                           # Android app module
|   +-- src/main/
|   |   +-- kotlin/com/pumpernickel/android/
|   |   |   +-- PumpernickelApp.kt        # Application class, startKoin
|   |   |   +-- ui/                        # Compose UI screens
|   |   |   |   +-- navigation/
|   |   |   |   +-- exercises/
|   |   |   |   +-- theme/
+-- iosApp/                               # Xcode project
|   +-- iosApp/
|   |   +-- PumpernickelApp.swift          # @main, calls doInitKoinIos()
|   |   +-- Views/
|   |   |   +-- MainTabView.swift          # TabView with 3 tabs
|   |   |   +-- Exercises/
|   |   |   |   +-- ExerciseCatalogView.swift
|   |   |   |   +-- ExerciseDetailView.swift
|   |   |   |   +-- CreateExerciseView.swift
|   |   |   +-- Anatomy/
|   |   |   |   +-- AnatomyPickerView.swift     # Bottom sheet with front/back SVG
|   |   |   |   +-- AnatomyFrontShape.swift      # SwiftUI Shape from SVG paths
|   |   |   |   +-- AnatomyBackShape.swift       # SwiftUI Shape from SVG paths
|   |   |   |   +-- MuscleRegionPaths.swift       # SVG path data strings
|   |   |   +-- Common/
|   |   |   |   +-- PlaceholderTabView.swift
|   |   |   |   +-- WorkoutEmptyStateView.swift
|   |   +-- Utilities/
|   |   |   +-- FlowObservation.swift      # AsyncSequence helpers
```

### Pattern 1: MVVM with Shared ViewModel + Platform UI

**What:** ViewModels live in KMP shared module, expose StateFlow. Platform UI (SwiftUI/Compose) observes state.
**When to use:** All screens in this project.

```kotlin
// shared/commonMain -- ViewModel
class ExerciseCatalogViewModel(
    private val repository: ExerciseRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedMuscleGroup = MutableStateFlow<MuscleGroup?>(null)
    val selectedMuscleGroup: StateFlow<MuscleGroup?> = _selectedMuscleGroup.asStateFlow()

    @NativeCoroutines
    val exercises: StateFlow<List<Exercise>> = combine(
        _searchQuery,
        _selectedMuscleGroup
    ) { query, group ->
        repository.searchExercises(query, group)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onMuscleGroupSelected(group: MuscleGroup?) {
        _selectedMuscleGroup.value = group
    }
}
```

```swift
// iosApp -- SwiftUI View
import KMPNativeCoroutinesAsync
import Shared

struct ExerciseCatalogView: View {
    let viewModel: ExerciseCatalogViewModel

    @State private var exercises: [Exercise] = []
    @State private var searchQuery: String = ""

    var body: some View {
        List(exercises, id: \.id) { exercise in
            ExerciseRow(exercise: exercise)
        }
        .searchable(text: $searchQuery)
        .onChange(of: searchQuery) { _, newValue in
            viewModel.onSearchQueryChanged(query: newValue)
        }
        .task {
            do {
                for try await value in asyncSequence(for: viewModel.exercises) {
                    exercises = value
                }
            } catch {
                // handle cancellation
            }
        }
    }
}
```

### Pattern 2: Room KMP Database Seeding via Callback

**What:** Use RoomDatabase.Callback.onCreate to seed data on first database creation.
**When to use:** First-launch seeding of the 873 exercises.

```kotlin
// shared/commonMain
object DatabaseSeedCallback : RoomDatabase.Callback() {
    override fun onCreate(connection: SQLiteConnection) {
        // NOTE: onCreate receives raw SQLiteConnection, not DAO access.
        // Strategy: Use raw SQL INSERT statements, or defer seeding
        // to first DAO access with a "seeded" flag in a metadata table.
    }
}

// Alternative (recommended): Deferred seeding on first repository access
class ExerciseRepositoryImpl(
    private val dao: ExerciseDao,
    private val seeder: DatabaseSeeder
) : ExerciseRepository {

    private val seeded = AtomicBoolean(false)

    override fun getExercises(): Flow<List<Exercise>> {
        return flow {
            if (!seeded.getAndSet(true)) {
                val count = dao.getExerciseCount()
                if (count == 0) {
                    seeder.seedExercises(dao)
                }
            }
            emitAll(dao.getAllExercises().map { entities ->
                entities.map { it.toDomain() }
            })
        }
    }
}
```

### Pattern 3: Expect/Actual for Resource Reading

**What:** Platform-specific file reading for the bundled JSON.
**When to use:** Loading free_exercise_db.json from app resources.

```kotlin
// shared/commonMain
expect fun readResourceFile(fileName: String): String

// shared/androidMain
actual fun readResourceFile(fileName: String): String {
    return Thread.currentThread().contextClassLoader!!
        .getResourceAsStream(fileName)!!
        .bufferedReader()
        .readText()
}

// shared/iosMain
actual fun readResourceFile(fileName: String): String {
    val path = NSBundle.mainBundle.pathForResource(
        name = fileName.substringBeforeLast("."),
        ofType = fileName.substringAfterLast(".")
    ) ?: error("Resource not found: $fileName")
    return NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
        ?: error("Could not read file: $fileName")
}
```

### Pattern 4: Koin DI Setup for KMP with Native UI

**What:** Koin module setup that works for both Android (startKoin in Application) and iOS (callable from Swift).

```kotlin
// shared/commonMain/di/SharedModule.kt
val sharedModule = module {
    single<AppDatabase> { getRoomDatabase(get()) }
    single<ExerciseDao> { get<AppDatabase>().exerciseDao() }
    single<ExerciseRepository> { ExerciseRepositoryImpl(get(), get()) }
    single<DatabaseSeeder> { DatabaseSeeder(get()) }
    viewModel { ExerciseCatalogViewModel(get()) }
    viewModel { ExerciseDetailViewModel(get()) }
    viewModel { CreateExerciseViewModel(get()) }
}

expect val platformModule: Module

fun initKoin(additionalModules: List<Module> = emptyList()) {
    startKoin {
        modules(sharedModule + platformModule + additionalModules)
    }
}

// shared/iosMain/di/KoinInitIos.kt
fun initKoinIos() {
    initKoin()
}
```

```swift
// iosApp/PumpernickelApp.swift
import SwiftUI
import Shared

@main
struct PumpernickelApp: App {
    init() {
        KoinInitIosKt.doInitKoinIos()
    }
    var body: some Scene {
        WindowGroup {
            MainTabView()
        }
    }
}
```

### Anti-Patterns to Avoid

- **Sharing navigation logic in KMP:** Navigation is inherently platform-specific. SwiftUI NavigationStack and Compose NavHost have incompatible paradigms. Keep navigation in platform UI code. Share only ViewModel state and domain logic.
- **Using Room createFromAsset in common code:** Not available in KMP. Use Callback.onCreate or deferred seeding instead.
- **Exposing Room entities to UI layer:** Always map Entity to domain model. Entities have Room annotations; domain models are clean Kotlin data classes.
- **Using SKIE with Kotlin 2.3.20:** As of 2026-03-28, SKIE 0.10.10 does not support Kotlin 2.3.20. PR #180 is pending. Use KMP-NativeCoroutines 1.0.2 instead.
- **Putting SVG path data in the shared KMP module:** SVG paths are UI concerns. Store them in platform code (Swift files for iOS, Kotlin resource for Android). The shared module only provides the MuscleGroup/MuscleRegion enums.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Kotlin Flow to Swift AsyncSequence | Custom FlowWrapper + ObservableObject adapter | KMP-NativeCoroutines 1.0.2 | Handles cancellation, generics, and AsyncSequence conformance automatically. Manual wrappers miss edge cases. |
| SVG path string parsing in Swift | Custom SVG path parser | nicklockwood/SVGPath 1.3.0 | SVG path syntax has ~20 commands with relative/absolute variants. The library handles M, L, C, Q, A, Z, and arc commands correctly. |
| JSON deserialization | Manual string parsing | kotlinx-serialization 1.10.0 | Type-safe, compile-time checked, handles nullability. 873 exercises with nested arrays. |
| Database migrations | Manual SQL ALTER scripts | Room auto-migration | Room generates migration code from schema diffs. For v1, not needed yet but architecture should support it. |
| Muscle group enum mapping | Hardcoded string comparisons | Kotlin sealed class/enum with mapping | The exercise DB has 17 muscles, anatomy SVG has 16 groups. A mapping layer resolves "middle back" -> "lats" and handles "abductors" -> "glutes" gracefully. |

**Key insight:** The Kotlin-to-Swift boundary is the most error-prone area. KMP-NativeCoroutines eliminates the entire class of Flow observation bugs that manual wrappers introduce (missed cancellation, memory leaks, generic type erasure).

---

## Common Pitfalls

### Pitfall 1: Muscle Group Mismatch Between DB and SVG

**What goes wrong:** The exercise database has 17 primary muscle groups (includes "abductors" and "middle back"), but the anatomy SVG only has 16 groups (includes "obliques" but NOT "abductors" or "middle back"). Filtering/display breaks for exercises with these muscle groups.
**Why it happens:** The exercise DB and anatomy SVG come from different sources within gymtracker.
**How to avoid:** Create a mapping layer in the shared module:
- "middle back" (34 exercises as primary) maps to "lats" (anatomically trapezius/rhomboids, but lats is the closest SVG group)
- "abductors" (8 exercises as primary) maps to "glutes" (hip abductors are part of the gluteal group)
- "obliques" exists in SVG but not as a distinct DB group -- "abdominals" exercises may include oblique work
**Warning signs:** Empty filter results for certain muscle groups, or exercises that don't highlight any anatomy region.

**Verified data:**
- Exercise DB primary muscles (17): abdominals, abductors, adductors, biceps, calves, chest, forearms, glutes, hamstrings, lats, lower back, middle back, neck, quadriceps, shoulders, traps, triceps
- Anatomy SVG groups (16): abdominals, adductors, biceps, calves, chest, forearms, glutes, hamstrings, lats, lower back, neck, obliques, quadriceps, shoulders, traps, triceps
- In DB but NOT in SVG: abductors (8 primary), middle back (34 primary)
- In SVG but NOT in DB: obliques

### Pitfall 2: Room KSP Version Must Match Kotlin Exactly

**What goes wrong:** Build fails with cryptic KSP errors if the KSP version prefix doesn't match the Kotlin version.
**Why it happens:** KSP versions are formatted as `{kotlin-version}-{ksp-version}` (e.g., `2.3.20-1.0.31`). Using wrong prefix causes compilation failure.
**How to avoid:** Always verify: `ksp = "2.3.20-X.X.X"` where the prefix matches `kotlin = "2.3.20"` exactly.
**Warning signs:** "Incompatible KSP version" or "Cannot find symbol" errors during build.

### Pitfall 3: RoomDatabase.Callback.onCreate Gets SQLiteConnection, Not DAO

**What goes wrong:** Trying to use DAOs inside the onCreate callback. The callback provides raw SQLiteConnection, which doesn't support Room's annotation-generated queries.
**Why it happens:** The database isn't fully initialized when onCreate fires.
**How to avoid:** Use deferred seeding pattern: check exercise count on first repository access, seed if zero. This runs AFTER database initialization and allows full DAO usage.
**Warning signs:** Crashes or "database not ready" errors during first launch.

### Pitfall 4: KMP-NativeCoroutines Requires @NativeCoroutines Annotation

**What goes wrong:** Kotlin Flow/suspend properties are not visible as AsyncSequence in Swift.
**Why it happens:** Unlike SKIE (which is automatic), KMP-NativeCoroutines requires explicit `@NativeCoroutines` annotation on every Flow/suspend function that Swift needs to access.
**How to avoid:** Annotate all public Flow properties and suspend functions in ViewModels with `@NativeCoroutines`. Also add `@NativeCoroutinesState` for StateFlow properties that should be directly accessible as Swift properties.
**Warning signs:** Swift compiler errors about missing AsyncSequence conformance.

### Pitfall 5: Exercise DB JSON Nullable Fields

**What goes wrong:** Deserialization crashes on null values.
**Why it happens:** The exercise DB has nullable fields: force (29 nulls), mechanic (87 nulls), equipment (77 nulls out of 873).
**How to avoid:** Define the `@Serializable` data class with nullable types for force, mechanic, and equipment. Never assume all fields are present.
**Warning signs:** `JsonDecodingException: Unexpected null` on first launch.

### Pitfall 6: iOS Framework Static Linking with Room

**What goes wrong:** Runtime crashes on iOS related to SQLite.
**Why it happens:** When using BundledSQLiteDriver, the framework must be static. If using NativeSQLiteDriver, you need `-lsqlite3` linker flag.
**How to avoid:** Use `isStatic = true` in the framework configuration and use BundledSQLiteDriver (recommended approach from CLAUDE.md).
**Warning signs:** "Symbol not found" crashes on iOS simulator/device.

### Pitfall 7: Swift Naming Conventions for Kotlin Functions

**What goes wrong:** Swift can't find Kotlin functions.
**Why it happens:** Kotlin function `initKoinIos()` becomes `KoinInitIosKt.doInitKoinIos()` in Swift -- file gets `Kt` suffix, functions get `do` prefix.
**How to avoid:** Document all Kotlin-to-Swift name mappings. Test from Swift immediately after writing Kotlin APIs.
**Warning signs:** "Unresolved identifier" in Xcode.

---

## Code Examples

### Exercise Entity (Room)

```kotlin
// Source: Room KMP official docs + D-03/D-05 decisions
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey
    val id: String,                        // e.g., "3_4_Sit-Up"
    val name: String,                       // e.g., "3/4 Sit-Up"
    val force: String?,                     // pull, push, static (29 nulls in DB)
    val level: String,                      // beginner, intermediate, expert
    val mechanic: String?,                  // compound, isolation (87 nulls)
    val equipment: String?,                 // 12 distinct values (77 nulls)
    val category: String,                   // 7 categories
    val instructions: String,              // JSON array stored as string, or use TypeConverter
    val images: String,                    // JSON array stored as string
    val isCustom: Boolean = false,          // D-05: false for seeded
    // Muscle groups stored as comma-separated or via junction table
    // Option A (simpler): comma-separated strings
    val primaryMuscles: String,            // e.g., "abdominals"
    val secondaryMuscles: String           // e.g., "calves,hamstrings" or ""
)
```

### Exercise DAO

```kotlin
@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name ASC")
    fun getAllExercises(): Flow<List<ExerciseEntity>>

    @Query("""
        SELECT * FROM exercises
        WHERE (:query = '' OR name LIKE '%' || :query || '%')
        AND (:muscleGroup IS NULL OR primaryMuscles LIKE '%' || :muscleGroup || '%')
        ORDER BY name ASC
    """)
    fun searchExercises(query: String, muscleGroup: String?): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun getExerciseById(id: String): Flow<ExerciseEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<ExerciseEntity>)

    @Insert
    suspend fun insert(exercise: ExerciseEntity)

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun getExerciseCount(): Int

    @Query("SELECT DISTINCT equipment FROM exercises WHERE equipment IS NOT NULL ORDER BY equipment")
    suspend fun getDistinctEquipment(): List<String>

    @Query("SELECT DISTINCT category FROM exercises ORDER BY category")
    suspend fun getDistinctCategories(): List<String>
}
```

### Muscle Group Enum (Shared Module)

```kotlin
// Maps between exercise DB muscle names, anatomy SVG groups, and display names
enum class MuscleGroup(val dbName: String, val displayName: String) {
    CHEST("chest", "Chest"),
    SHOULDERS("shoulders", "Shoulders"),
    BICEPS("biceps", "Biceps"),
    TRICEPS("triceps", "Triceps"),
    FOREARMS("forearms", "Forearms"),
    TRAPS("traps", "Traps"),
    LATS("lats", "Lats"),
    NECK("neck", "Neck"),
    QUADRICEPS("quadriceps", "Quadriceps"),
    HAMSTRINGS("hamstrings", "Hamstrings"),
    GLUTES("glutes", "Glutes"),
    CALVES("calves", "Calves"),
    ADDUCTORS("adductors", "Adductors"),
    ABDOMINALS("abdominals", "Abdominals"),
    OBLIQUES("obliques", "Obliques"),
    LOWER_BACK("lower back", "Lower Back");

    companion object {
        // DB muscle name -> MuscleGroup (handles mismatches)
        fun fromDbName(name: String): MuscleGroup? = when (name) {
            "middle back" -> LATS        // 34 exercises, anatomically close
            "abductors" -> GLUTES        // 8 exercises, hip abductors
            else -> entries.find { it.dbName == name }
        }
    }
}

// SVG region ID -> MuscleGroup mapping (from muscleRegionMap.ts)
enum class MuscleRegion(val group: MuscleGroup, val view: AnatomyView) {
    UPPER_PECTORALIS(MuscleGroup.CHEST, AnatomyView.FRONT),
    MID_LOWER_PECTORALIS(MuscleGroup.CHEST, AnatomyView.FRONT),
    ANTERIOR_DELTOID(MuscleGroup.SHOULDERS, AnatomyView.FRONT),
    LATERAL_DELTOID(MuscleGroup.SHOULDERS, AnatomyView.BOTH),
    POSTERIOR_DELTOID(MuscleGroup.SHOULDERS, AnatomyView.BACK),
    // ... all 30 regions from muscleRegionMap.ts
    ;

    val svgId: String get() = name.lowercase().replace('_', '-')
}

enum class AnatomyView { FRONT, BACK, BOTH }
```

### SwiftUI Anatomy Picker (Conceptual)

```swift
// Uses nicklockwood/SVGPath to parse SVG d="" strings
import SVGPath

struct AnatomyFrontView: View {
    let selectedGroup: MuscleGroup?
    let onRegionTapped: (MuscleGroup) -> Void

    var body: some View {
        Canvas { context, size in
            let scale = size.width / 676.49  // Original SVG viewBox width
            for region in MuscleRegionPaths.frontRegions {
                let path = try! Path(svgPath: region.pathData)
                    .applying(CGAffineTransform(scaleX: scale, y: scale))
                let color = region.group == selectedGroup
                    ? Color.accentColor.opacity(0.8)
                    : Color(.systemGray5)
                context.fill(path, with: .color(color))
            }
        }
        .aspectRatio(676.49 / 1203.49, contentMode: .fit)
        .onTapGesture { location in
            // Hit-test against each region path
        }
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Compose Multiplatform shared UI for iOS | KMP shared logic + SwiftUI native UI (D-01) | Phase 1 context discussion | Requires Swift interop library (KMP-NativeCoroutines). No ComposeViewController needed. |
| SKIE for Flow bridging | KMP-NativeCoroutines | 2026-03-28 (SKIE lacks Kotlin 2.3.20 support) | Requires @NativeCoroutines annotations on ViewModels. Switch back to SKIE when it ships 2.3.20 support. |
| Room createFromAsset for DB seeding | RoomDatabase.Callback.onCreate or deferred seeding | KMP limitation | createFromAsset is Android-only in Room KMP. Use common code seeding approach. |
| Android-only Room SupportSQLiteDatabase | KMP SQLiteConnection in callbacks | Room 2.7+ | Callback signatures changed for KMP support. |

**Deprecated/outdated:**
- CLAUDE.md says "SKIE is unnecessary" -- this was based on Compose Multiplatform shared UI. With D-01 (native UI), Swift interop IS needed. KMP-NativeCoroutines replaces SKIE.
- CLAUDE.md mentions `koin-compose-viewmodel` -- still valid for Android Compose, but iOS uses direct ViewModel access via Koin's `KoinComponent` or a helper.

---

## Open Questions

1. **Exact KSP version for Kotlin 2.3.20**
   - What we know: KSP versions follow `{kotlin}-{ksp}` format. Kotlin 2.3.20 needs `2.3.20-X.X.X`.
   - What's unclear: The exact latest KSP release matching 2.3.20 (e.g., `2.3.20-1.0.31` vs newer).
   - Recommendation: Check `npm view` equivalent for Maven -- run `./gradlew dependencies` after initial setup to verify resolution. The KMP Wizard will likely select the right version.

2. **SVG Path Hit Testing in SwiftUI**
   - What we know: SVGPath library parses d="" strings into Path objects. SwiftUI Canvas can draw them.
   - What's unclear: Efficient hit-testing (determining which muscle region was tapped) for ~139 paths. `path.contains(point)` works but needs proper coordinate transform.
   - Recommendation: Use `path.contains(point)` after applying the same scale transform. Group paths by region ID so tapping any path in a group selects the whole muscle group.

3. **Resource Bundling for 1MB JSON on iOS**
   - What we know: Android can read from classpath resources. iOS needs NSBundle.
   - What's unclear: Whether KMP's Compose Resources (`Res.readBytes()`) works when NOT using Compose Multiplatform, or if expect/actual is the only path.
   - Recommendation: Use expect/actual for resource reading. Place JSON in `shared/src/commonMain/resources/` and in the iOS Xcode project as a bundle resource.

4. **SKIE Future Compatibility**
   - What we know: SKIE PR #180 for Kotlin 2.3.20 is under review as of 2026-03-27. A community test build (0.10.11-RC2) exists.
   - What's unclear: When the official release will ship.
   - Recommendation: Start with KMP-NativeCoroutines. If SKIE ships Kotlin 2.3.20 support before Phase 2, consider switching (SKIE requires less annotation boilerplate).

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java JDK | Kotlin/Gradle compilation | Yes | OpenJDK 23.0.1 | -- |
| Xcode | iOS builds, simulator | Yes | 26.3 | -- |
| iOS Simulator | iOS testing | Yes | iOS 26.3 (iPhone 17 Pro, etc.) | -- |
| CocoaPods | iOS dependency management | Yes | 1.16.2 | SPM (preferred for SVGPath + KMP-NativeCoroutines) |
| Node.js | GSD tooling | Yes | 22.22.0 | -- |
| Python 3 | JSON analysis scripts (dev only) | Yes | 3.14.3 | -- |
| Android Studio | IDE, KMP plugin | Unknown | Not verified via CLI | Must be installed manually |
| Kotlin CLI | Optional (Gradle handles it) | No (not installed standalone) | -- | Gradle wrapper downloads Kotlin |
| Gradle | Build system | Not installed globally | -- | Use Gradle wrapper (`./gradlew`) |

**Missing dependencies with no fallback:**
- Android Studio must be installed for the KMP plugin and Android builds. Cannot be verified via CLI. User likely has it as this is a university mobile app development course.

**Missing dependencies with fallback:**
- No global Gradle/Kotlin -- use Gradle wrapper (standard practice).

---

## Project Constraints (from CLAUDE.md)

- **Tech stack:** Kotlin Multiplatform + Compose Multiplatform per Lastenheft (NOTE: D-01 overrides shared UI to native UI)
- **Platform focus:** iOS first
- **Storage:** Local/offline only (Room)
- **Timeline:** University deadline ~end of May 2026
- **Scope:** Workout feature only for current milestone
- **No backend, no nutrition, no gamification**
- **Avoid:** Realm, Room 3.0 alpha, Hilt/Dagger, LiveData, RxJava, Ktor (not needed yet), Accompanist
- **Use:** Room for structured data, DataStore for settings, MVVM (ViewModel + StateFlow), Navigation Compose for Android
- **GSD workflow enforcement:** Use /gsd commands for all changes

---

## Canonical Reference Files

The following external files MUST be read by the planner/implementer:

| File | Purpose | Size |
|------|---------|------|
| `/Users/olli/schenanigans/gymtracker/api/free_exercise_db.json` | Exercise seed data (873 exercises) | ~1MB |
| `/Users/olli/schenanigans/gymtracker/web/src/lib/components/anatomy/AnatomyFront.svelte` | Front body SVG paths (139 path elements) | 297 lines |
| `/Users/olli/schenanigans/gymtracker/web/src/lib/components/anatomy/AnatomyBack.svelte` | Back body SVG paths (116 path elements) | 270 lines |
| `/Users/olli/schenanigans/gymtracker/web/src/lib/components/anatomy/muscleRegionMap.ts` | Region ID to muscle group mapping (30 regions -> 16 groups) | 66 lines |

### Exercise DB Schema (Verified)

| Field | Type | Nullable | Distinct Values |
|-------|------|----------|-----------------|
| name | string | no | 873 unique |
| force | string | yes (29 nulls) | pull, push, static |
| level | string | no | beginner, intermediate, expert |
| mechanic | string | yes (87 nulls) | compound, isolation |
| equipment | string | yes (77 nulls) | bands, barbell, body only, cable, dumbbell, e-z curl bar, exercise ball, foam roll, kettlebells, machine, medicine ball, other |
| primaryMuscles | string[] | no | 17 distinct muscles |
| secondaryMuscles | string[] | no (can be empty) | 17 distinct muscles |
| instructions | string[] | no | Varies |
| category | string | no | cardio, olympic weightlifting, plyometrics, powerlifting, strength, stretching, strongman |
| images | string[] | no | Varies |
| id | string | no | 873 unique |

### SVG Anatomy Data

- **Front view:** viewBox 0 0 676.49 1203.49, interactive regions: neck, upper-trapezius, anterior-deltoid, lateral-deltoid, upper-pectoralis, mid-lower-pectoralis, long-head-bicep, short-head-bicep, wrist-extensors, wrist-flexors, upper-abdominals, lower-abdominals, obliques, inner-thigh, groin, rectus-femoris, inner-quadricep, outer-quadricep, gastrocnemius, soleus, tibialis
- **Back view:** viewBox 0 0 676.49 1203.49, interactive regions: neck, upper-trapezius, posterior-deltoid, lateral-deltoid, lateral-head-triceps, long-head-triceps, medial-head-triceps, traps-middle, lower-trapezius, lats, lowerback, wrist-extensors, wrist-flexors, gluteus-maximus, gluteus-medius, inner-thigh, medial-hamstrings, lateral-hamstrings, gastrocnemius, soleus
- **Non-interactive elements:** feet, hands, body outline (rendered but not tappable)

---

## Sources

### Primary (HIGH confidence)
- [Room KMP setup guide](https://developer.android.com/kotlin/multiplatform/room) - Database setup, platform-specific builders, BundledSQLiteDriver, KSP configuration
- [KMP-NativeCoroutines GitHub](https://github.com/rickclephas/KMP-NativeCoroutines) - Version 1.0.2 confirmed, Kotlin 2.3.20 support verified, Swift async/await integration
- [SKIE GitHub releases](https://github.com/touchlab/SKIE/releases) - Version 0.10.10 confirmed, Kotlin 2.3.20 NOT supported (issue #174, PR #180 pending)
- [SKIE Flows in SwiftUI docs](https://skie.touchlab.co/features/flows-in-swiftui) - Observing view pattern (preview feature)
- [SVGPath Swift library](https://github.com/nicklockwood/SVGPath) - Version 1.3.0, SwiftUI Path support
- [Apple SwiftUI Drawing Paths and Shapes tutorial](https://developer.apple.com/tutorials/swiftui/drawing-paths-and-shapes) - Path/Shape pattern
- gymtracker source files (local) - Exercise DB structure, anatomy SVG paths, muscle region mapping

### Secondary (MEDIUM confidence)
- [Koin KMP docs](https://insert-koin.io/docs/reference/koin-mp/kmp/) - KMP advanced patterns
- [Koin DI with KMP native UI](https://dev.to/saad4software/kmp-native-ui-dependency-injection-with-koin-4d9c) - iOS initialization pattern (doInitKoinIos)
- [JetBrains KMP Wizard](https://kmp.jetbrains.com/) - Project scaffolding reference
- [Kotlin Multiplatform FAQ](https://kotlinlang.org/docs/multiplatform/faq.html) - Architecture guidance

### Tertiary (LOW confidence)
- SKIE Kotlin 2.3.20 timeline - Community test builds exist (0.10.11-RC2) but no official release date confirmed

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All versions verified against CLAUDE.md, compatibility checked
- Architecture: HIGH - KMP shared logic + native UI is a well-documented pattern with multiple production references
- Pitfalls: HIGH - Muscle group mismatch verified with actual data analysis, nullable fields counted, SKIE incompatibility confirmed via GitHub issue
- SVG porting: MEDIUM - SVGPath library confirmed, but hit-testing approach needs validation during implementation
- Database seeding: MEDIUM - RoomDatabase.Callback.onCreate confirmed in KMP, but deferred seeding pattern is more practical (based on community experience, not official docs)

**Research date:** 2026-03-28
**Valid until:** 2026-04-28 (30 days -- stable stack, only SKIE status may change sooner)
