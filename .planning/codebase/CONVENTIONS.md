# Coding Conventions

**Analysis Date:** 2026-05-18

## Naming Patterns

**Files:**
- One public class/interface per file, file name matches the class name (e.g., `WorkoutTemplateDao.kt`, `TemplateEditorViewModel.kt`).
- `expect`/`actual` files use platform suffix in the file name: `Platform.kt` (commonMain), `Platform.android.kt`, `Platform.ios.kt`, `Database.android.kt`, `HttpClientFactory.ios.kt`.
- Compose screens end in `Screen` (`TemplateListScreen.kt`, `WorkoutSessionScreen.kt`); modal/sheet variants end in `Sheet` (`ExerciseOverviewSheet.kt`, `SettingsSheet.kt`); dialogs end in `Dialog` (`EarlyExitConfirmDialog.kt`).
- ViewModels end in `ViewModel` (`TemplateEditorViewModel.kt`, `WorkoutSessionViewModel.kt`).
- Room entities end in `Entity` (`WorkoutTemplateEntity.kt`, `ActiveSessionSetEntity.kt`).
- Room DAOs end in `Dao` (`WorkoutTemplateDao.kt`, `CompletedWorkoutDao.kt`).
- Repository interfaces named `XRepository`, implementations named `XRepositoryImpl` and live in the same file or sibling file under `data/repository/`. Example: `TemplateRepository` + `TemplateRepositoryImpl` in `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/TemplateRepository.kt`.
- Use cases end in `UseCase` (`AddFoodUseCase`, `GetUndertrainedMusclesUseCase`) and live in `domain/<feature>/`.
- Query projection DTOs end in `Dto` (`WorkoutSummaryDto.kt`, `ExercisePbDto.kt`, `ExerciseSetRirDto.kt`).
- Koin modules end in `Module` (`SharedModule.kt`, `GamificationModule.kt`); KMP helpers for Swift end in `KoinHelper` (`WorkoutAiKoinHelper.kt`).

**Functions:**
- camelCase. ViewModel mutators are imperative verbs: `onNameChanged`, `addExercise`, `moveExercise`, `save`, `clearSaveResult`, `loadTemplate`.
- DAO methods read like SQL intent: `getAllTemplates`, `insertTemplate`, `updateTemplateName`, `deleteTemplate`, `touchTemplate`.
- Repository methods that return reactive data start with `get…(): Flow<…>`; one-shot mutations use `suspend fun` with verbs (`createTemplate`, `addExercise`).

**Variables:**
- camelCase for locals/properties. Backing state uses leading-underscore convention: `private val _name = MutableStateFlow("")` with public `val name: StateFlow<String> = _name.asStateFlow()` (see `TemplateEditorViewModel.kt:25-27`).
- Constants in `companion object` or top-level use SCREAMING_SNAKE_CASE: `XpFormula.PR_XP` (`shared/.../domain/gamification/XpFormula.kt`).
- Domain integers that represent fractional kg use the suffix `KgX10` (fixed-point ×10 — e.g., `actualWeightKgX10`, `targetWeightKgX10`). Time-of-day uses `Millis` suffix (`startTimeMillis`, `completedAtMillis`).

**Types:**
- PascalCase. Domain models live in `domain/model/` (e.g., `WorkoutTemplate`, `CompletedWorkout`, `SessionExercise`, `MuscleGroup`).
- UI state machines use nested `sealed class` per screen (e.g., `WorkoutSessionState.Idle/Active/Reviewing/Finished` and `RestState.NotResting/Resting/RestComplete` inside `WorkoutSessionViewModel.kt:44-81`).
- Error types use `sealed class` with nested `data class` / `data object` variants — see `AiError` in `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt`.

## Code Style

**Formatting:**
- No `.editorconfig`, no ktlint, no detekt configured (none found in repo root, `gradle/`, or `shared/`). Style is "IntelliJ default Kotlin formatter".
- Indent: 4 spaces. Brace style: K&R. Trailing commas appear in multi-arg constructors (e.g., `WorkoutSessionViewModel(...)` ViewModel signatures).
- Imports are explicit (no wildcard imports observed); JetBrains/IDE auto-import order is preserved.

**Linting:**
- No linter. Compiler warnings are the only static-analysis gate.
- Opt-ins are declared globally in `shared/build.gradle.kts:38`: `languageSettings.optIn("kotlin.experimental.ExperimentalObjCName")`.
- File-level opt-in used for advanced coroutine APIs: `@OptIn(ExperimentalCoroutinesApi::class)` on `TemplateRepositoryImpl` (`TemplateRepository.kt:38`).
- `-Xexpect-actual-classes` compiler flag is enabled (`shared/build.gradle.kts:31`) to allow `expect class` in shared code.

## Import Organization

**Order:**
1. `androidx.*` (Room, ViewModel, Lifecycle, Compose)
2. `com.pumpernickel.*` (own packages)
3. `io.ktor.*` / `org.koin.*` / `com.rickclephas.*`
4. `kotlinx.*` (coroutines, serialization, datetime)
5. `kotlin.*` (stdlib)

Within each group imports are alphabetical (IDE default). Example: `TemplateEditorViewModel.kt:3-16`.

**Path Aliases:**
- None. KMP project uses raw Kotlin package paths.

## Error Handling

**Result types vs exceptions — mixed but rule-based:**

- **Domain/UseCase boundaries crossing platform code use `kotlin.Result<T>`.** Only two `Result<…>` return types in `commonMain`, both at the geofence platform seam:
  - `GeofenceProvider.registerGeofence(...): Result<Unit>` (`shared/src/commonMain/kotlin/com/pumpernickel/domain/geofence/GeofenceProvider.kt:36`)
  - `DebugGeofenceProvider.registerGeofence(...): Result<Unit>` (`.../data/geofence/DebugGeofenceProvider.kt:41`)
- **Network/IO errors use a dedicated `sealed class` mapped from `Throwable`.** See `AiError` (`shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt`) with variants `Timeout`, `Network`, `AuthOrQuota(httpStatus)`, `Provider(httpStatus)`, `SchemaInvalid(detail)`, `Cancelled`, plus a `fromThrowable(t: Throwable): AiError` factory that translates Ktor exception classes. Callers MUST rethrow `CancellationException` before mapping.
- **ViewModel save/mutation flows wrap the operation in `try { … } catch (e: Exception) { … }` and surface a nested `SaveResult` sealed class as a one-shot `StateFlow`.** Pattern in `TemplateEditorViewModel.kt:38-45, 228-272`:
  ```kotlin
  sealed class SaveResult {
      data class Success(val templateId: Long) : SaveResult()
      data class Error(val message: String) : SaveResult()
  }
  private val _saveResult = MutableStateFlow<SaveResult?>(null)
  // …
  try { repository.updateTemplateName(…); _saveResult.value = SaveResult.Success(id) }
  catch (e: Exception) { _saveResult.value = SaveResult.Error(e.message ?: "Failed to save template") }
  finally { _isSaving.value = false }
  ```
  UI must call `clearSaveResult()` after consuming.
- **Repositories let exceptions propagate.** Room operations are not wrapped — DAOs are called directly and exceptions surface to the ViewModel layer (`TemplateRepositoryImpl`, `FoodRepositoryImpl`, `WorkoutRepositoryImpl`).
- **`println("[AI] …")` is used as ad-hoc logging in `OpenAICompatibleClient.kt`** — no structured logger.

## Logging

**Framework:** None. `println(...)` with bracketed tag prefix (`"[AI] POST $url …"`) is used in network code (`shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt:46, 59`). All other code is silent.

**Patterns:**
- When logging, prefix with `[Feature]` tag in brackets.
- Do not log secrets. The AI client logs `keyLen=${key.length}` rather than the key itself.

## Comments

**When to Comment:**
- Above non-obvious algorithmic decisions, with a doc-ref ID in the form `D-XX-YY` or `REQ-XX-NN` linking back to the design doc (e.g., `// D-08 default`, `// D-19-16 — UI state for the geofence status chip`, `// REQ-AI-06 — single BYOK provider`). These tags appear pervasively (200+ occurrences in `commonMain`).
- Above sealed-class hierarchies that model a state machine — block comments enumerate every variant.
- Above any branch in repository code that explains atomicity / consistency tradeoffs (see `TemplateRepository.kt:148-151` on partial-failure tolerance).

**JSDoc/TSDoc:**
- Use KDoc (`/** … */`) for public classes that document protocol/algorithm intent. Examples: `OpenAICompatibleClient` class doc (`OpenAICompatibleClient.kt:18-27`), `AiError` (`AiError.kt:11-23`).
- Single-line `//` comments for short inline rationale.
- Method-level KDoc is selective — only on non-obvious public API.

## Function Design

**Size:**
- ViewModels mutator functions are typically 5–30 lines. Larger orchestration (e.g., `save()` in `TemplateEditorViewModel`) reaches ~50 lines and is acceptable when it sequences one transactional flow.
- DAO methods are 1–4 lines (mostly a single annotation + signature, since Room generates the body).

**Parameters:**
- Use named constructor injection: every ViewModel, Repository impl, and UseCase receives its collaborators via the primary constructor (`private val` properties). See `TemplateRepositoryImpl(private val templateDao, private val exerciseRepository)` in `TemplateRepository.kt:39-41`.
- Default values are used to express domain defaults in DAOs/entities (`@PrimaryKey(autoGenerate = true) val id: Long = 0`, `rir: Int = 2`).
- Mutator functions accept primitive params rather than DTOs (`updateExerciseTargets(id: Long, sets: Int, reps: Int, restSec: Int)`).

**Return Values:**
- `suspend fun` for one-shot writes, returning `Long` (new row id) or `Unit`.
- `fun … : Flow<T>` for reactive reads. Lists return `Flow<List<T>>`; "single or null" returns `Flow<T?>`.
- Composition of multiple flows uses `combine` + `stateIn(viewModelScope, SharingStarted.Eagerly, default)` to expose derived `StateFlow`s (e.g., `TemplateEditorViewModel.isFormValid`, `TemplateEditorViewModel.kt:48-53`).

## Module Design

**Exports:**
- Visibility is mostly default `public`. `private val _xxx` is the only widely used visibility modifier (for backing state inside ViewModels).
- `internal` is not used.

**Barrel Files:**
- None. Every type is imported by its fully qualified path.

## Compose Patterns

**State hoisting:**
- Screens fetch their ViewModel via `koinViewModel()` from `org.koin.compose.viewmodel` and immediately convert StateFlows to Compose state: `val templates by viewModel.templates.collectAsState()` (`TemplateListScreen.kt:62-63`).
- Screens own *navigation* state and *transient UI flags* via `var showDeleteDialog by remember { mutableStateOf(false) }` (`TemplateListScreen.kt:65-67`). Persistent app state lives in the ViewModel.
- `LaunchedEffect(key)` is used to trigger side effects in response to state changes (`TemplateListScreen.kt:139`).
- `rememberSwipeToDismissBoxState`, `rememberSaveable`-style usage appears in `CreateExerciseScreen.kt`, `AnatomyPickerSheet.kt`, `ExercisePickerScreen.kt`, `AiSettingsScreen.kt`, `AiWorkoutGenScreen.kt`.

**remember vs ViewModel:**
- `remember` / `mutableStateOf` ONLY for UI-local concerns (dialog open/closed, dismiss thresholds, transient text-field focus).
- All persistable, domain, and cross-screen state lives in `MutableStateFlow` inside a `ViewModel` and is exposed as `StateFlow` via `asStateFlow()`. Strict rule observed across all 22 ViewModels.

**iOS interop:**
- StateFlows exposed to Swift are annotated with `@NativeCoroutinesState` (from `com.rickclephas.kmp:kmp-nativecoroutines`) so SKIE/kmp-nativecoroutines generates Swift-friendly publishers. See `TemplateEditorViewModel.kt:26, 30, 34, 44, 48`.

**Material 3:**
- Compose Material 3 components only (`MaterialTheme`, `Scaffold`, `TopAppBar`, `ListItem`, `FloatingActionButton`, `SwipeToDismissBox`, `AlertDialog`). Colors flow from `MaterialTheme.colorScheme.*`.
- Icons from `androidx.compose.material.icons.Icons.Default` + `material-icons-extended`.
- `@OptIn(ExperimentalMaterial3Api::class)` is placed at the top of any screen using experimental M3 surfaces (Scaffold, TopAppBar, SwipeToDismissBox).

## Coroutine Patterns

**Scope:**
- ViewModels: `viewModelScope.launch { … }` for fire-and-forget work bound to the ViewModel lifecycle.
- `AiGenerationManager` uses an app-lifetime `CoroutineScope(SupervisorJob() + Dispatchers.Default)` for cross-screen AI generation (`shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiGenerationManager.kt:26-28`).
- No top-level `GlobalScope` usage.

**Dispatcher usage:**
- `commonMain` deliberately avoids `Dispatchers.IO` because it isn't available everywhere in KMP (call out documented in `AiGenerationManager.kt:26`). Use `Dispatchers.Default` in shared code.
- Room's writer is moved off the main thread with `setQueryCoroutineContext(Dispatchers.IO)` at Koin DB build (`shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt:78`).
- Inside ViewModels, do NOT specify a dispatcher on `viewModelScope.launch` — repository/DAO calls are already suspending and Room dispatches itself.

**Flow patterns:**
- Cold flow pipelines compose with `map`, `combine`, `flatMapLatest`, `first()`, `flowOf(...)` (see `TemplateRepositoryImpl.getAllTemplates/getTemplateById`).
- ViewModel-exposed flows use `stateIn(viewModelScope, SharingStarted.Eagerly, initialValue)` when derived; pass-through state uses `asStateFlow()`.
- For one-shot reads from a `Flow`, use `.first()` (e.g., `TemplateRepositoryImpl.getTemplateExercises` at `TemplateRepository.kt:75`).
- `SharedFlow`/`Channel` are not used — one-shot UI events are modelled as nullable `StateFlow<Event?>` cleared by an explicit `clearXxx()` method.

**Concurrency primitives:**
- `Mutex` + `withLock` for "seed once" guards (`FoodRepositoryImpl.seedMutex`, `shared/.../data/repository/FoodRepositoryImpl.kt:22-32`).

## Package Naming

Root: `com.pumpernickel`. Layered by responsibility, never by feature at the top level:

```
com.pumpernickel
├── data
│   ├── api         # Ktor clients, OpenFoodFacts, OpenAI-compatible
│   ├── db          # Room entities, DAOs, AppDatabase, seeders, DTOs
│   ├── geofence    # Debug/in-memory geofence providers
│   ├── preferences # DataStore factories
│   └── repository  # Repository interfaces + Impl
├── domain
│   ├── ai          # AiError, AiGenerationManager, BYOK key store
│   ├── gamification, geofence, location, nutrition, permissions,
│   │   progresspic, workout                  # feature-grouped business logic
│   └── model       # Pure-Kotlin domain types shared across features
├── presentation
│   └── <feature>   # ViewModels grouped by feature (workout, templates,
│                   #   nutrition, history, overview, settings, ai, …)
├── di              # Koin modules: SharedModule + per-feature modules
├── platform        # expect/actual platform glue (Database, DataStore)
└── feature         # androidMain-only platform feature shims
                    #   (biometric, geofence, location, permissions, photo)
```

Android app code lives under `com.pumpernickel.android.ui.{navigation,screens,components,theme}` in `androidApp/src/androidMain/kotlin/`.

---

*Convention analysis: 2026-05-18*
