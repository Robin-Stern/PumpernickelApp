<!-- refreshed: 2026-05-18 -->
# Architecture

**Analysis Date:** 2026-05-18

## System Overview

```text
┌────────────────────────────────────────────────────────────────────────────┐
│  UI LAYER (platform-specific, NOT in shared module)                        │
├──────────────────────────────────────┬─────────────────────────────────────┤
│  Android: Jetpack Compose            │  iOS: SwiftUI                       │
│  `androidApp/src/androidMain/`       │  `iosApp/iosApp/Views/`             │
│  • screens/                          │  • Views/Workout, Templates, ...    │
│  • components/                       │  • KoinHelper.getXxxViewModel()     │
│  • navigation/MainScreen.kt          │  • FlowObservation utility          │
└──────────────────┬───────────────────┴───────────────────┬─────────────────┘
                   │ collectAsState()                       │ KMPNativeCoroutines
                   ▼                                        ▼
┌────────────────────────────────────────────────────────────────────────────┐
│  PRESENTATION LAYER (shared/commonMain — `com.pumpernickel.presentation`)  │
│  ViewModels (androidx.lifecycle.ViewModel) exposing StateFlow              │
│  • TemplateListViewModel, WorkoutSessionViewModel, OverviewViewModel, ...  │
│  • @NativeCoroutinesState annotations for Swift consumption                │
└──────────────────────────────────┬─────────────────────────────────────────┘
                                   │ depends on Repository + UseCase
                                   ▼
┌────────────────────────────────────────────────────────────────────────────┐
│  DOMAIN LAYER (shared/commonMain — `com.pumpernickel.domain`)              │
│  • model/         — entities (WorkoutTemplate, Exercise, ...)              │
│  • workout/, nutrition/, ai/, gamification/, geofence/, progresspic/       │
│    — use cases, business rules, expect-actual platform contracts           │
│  ⚠ Domain currently imports from `data.db` and `data.api` (see below).    │
└──────────────────────────────────┬─────────────────────────────────────────┘
                                   │ depends on (today, but should not)
                                   ▼
┌────────────────────────────────────────────────────────────────────────────┐
│  DATA LAYER (shared/commonMain — `com.pumpernickel.data`)                  │
│  • db/        — Room entities, DAOs, AppDatabase, seeders                  │
│  • repository/ — Repository interfaces + Impl in the SAME package         │
│  • api/       — Ktor clients, DTOs (OpenFoodFacts, OpenAI-compatible)     │
│  • preferences/createDataStore.kt (expect)                                 │
│  • geofence/DebugGeofenceProvider.kt                                       │
└──────────────────────────────────┬─────────────────────────────────────────┘
                                   │
                                   ▼
┌────────────────────────────────────────────────────────────────────────────┐
│  PLATFORM (shared/androidMain + shared/iosMain)                            │
│  • Room SQLite (BundledSQLiteDriver)                                       │
│  • DataStore Preferences                                                   │
│  • OpenFoodFacts API (Ktor)                                                │
│  • OpenAI-compatible LLM endpoints (Ktor)                                  │
│  • CoreLocation / FusedLocationProvider                                    │
│  • Keychain / EncryptedSharedPreferences (SecureKeyStore)                  │
└────────────────────────────────────────────────────────────────────────────┘
```

## Pattern Overview

**Overall:** Mixed — _aspirationally_ Clean Architecture with **MVVM** for presentation, but with **systemic dependency-rule violations** that demote it to a layered MVVM-with-Repository pattern in practice.

The directory taxonomy under `shared/src/commonMain/kotlin/com/pumpernickel/` mirrors a textbook Clean Architecture split (`data/` ↔ `domain/` ↔ `presentation/`). However, the actual `import` graph shows numerous layer-bleed cases: `domain/model/*` imports Room `*Entity` classes from `data/db/`, several use cases in `domain/ai/` and `domain/nutrition/` import concrete `data.repository.*` classes, and Repository interfaces are co-located with their `Impl` (no inversion). The taxonomy is therefore "Clean-flavoured layered architecture," not Clean Architecture.

**Key Characteristics:**
- Single shared KMP module (`:shared`) for everything common to both platforms — data, domain, presentation
- Android UI lives in a separate Gradle module (`:androidApp`) using Jetpack Compose
- iOS UI is a **non-Gradle Xcode project** (`iosApp/`) consuming the `Shared.framework` directly via Swift — not Compose Multiplatform UI
- ViewModels are shared (`androidx.lifecycle.ViewModel` + StateFlow) and exposed to Swift via `@NativeCoroutinesState`
- Koin DI with one common `sharedModule` (`shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt`) plus per-feature submodules and platform `expect val platformModule`
- Each platform UI obtains ViewModels through different mechanisms: Android via `koinViewModel()` Compose helper; iOS via hand-written `KoinHelper` object (`shared/src/iosMain/kotlin/com/pumpernickel/di/KoinHelper.kt`)

## Layers

**UI Layer (platform-specific, outside `:shared`):**
- Purpose: Render Composables/SwiftUI views, dispatch user intents to ViewModels, observe StateFlow
- Location: `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/` (Compose) and `iosApp/iosApp/Views/` (SwiftUI)
- Contains: Screens, components, navigation graphs, theme
- Depends on: Presentation (ViewModels), Domain (models passed through), platform UI frameworks
- Used by: Nothing — this is the outermost ring

**Presentation Layer:**
- Purpose: Hold and transform UI state; bridge UI to domain/data
- Location: `shared/src/commonMain/kotlin/com/pumpernickel/presentation/`
- Contains: `*ViewModel.kt` classes only (no Composables / SwiftUI — UI is platform-specific)
- Subpackages by feature: `ai/`, `exercises/`, `gamification/`, `history/`, `nutrition/`, `overview/`, `progresspic/`, `settings/`, `templates/`, `workout/`
- Depends on: Domain (use cases, models), Data (Repository interfaces — leaked dependency)
- Used by: Android Compose screens + iOS SwiftUI views via Koin

**Domain Layer:**
- Purpose: Business entities, use cases, platform-agnostic abstractions
- Location: `shared/src/commonMain/kotlin/com/pumpernickel/domain/`
- Contains:
  - `model/` — 16 domain entities (`WorkoutTemplate`, `Exercise`, `Food`, `CompletedWorkout`, `MuscleGroup`, …)
  - `workout/`, `nutrition/`, `ai/`, `gamification/`, `geofence/`, `location/`, `permissions/`, `progresspic/` — use cases + provider interfaces (`LocationProvider`, `GeofenceProvider`, `PermissionController`, `PhotoVault`, `SecureKeyStore`, `BiometricGate`)
- Platform contracts (expect/actual): `domain/ai/NotificationService.kt`, `domain/ai/SecureKeyStore.kt`, `domain/progresspic/PhotoVault.kt`, `domain/progresspic/PhotoCaptureLauncher.kt`, `domain/progresspic/BiometricGate.kt`
- Depends on (per Clean Arch): **should depend on NOTHING**. Today it depends on `data.db.*Entity`, `data.repository.*`, and `data.api.*` (violation — see Dependency Rule Check).
- Used by: Presentation, Data (allowed direction)

**Data Layer:**
- Purpose: Persistence, network I/O, mapping to domain models
- Location: `shared/src/commonMain/kotlin/com/pumpernickel/data/`
- Contains:
  - `db/` — Room `@Entity`, `@Dao`, `AppDatabase`, seeders (`DatabaseSeeder`, `NutritionDataSeeder`, `AchievementStateSeeder`), DTO classes for projection queries (`ExercisePbDto`, `WorkoutSummaryDto`, …)
  - `repository/` — `*Repository` interface + `*RepositoryImpl` class (co-located, e.g. `TemplateRepository.kt` declares both `TemplateRepository` and `TemplateRepositoryImpl`)
  - `api/` — Ktor `HttpClientFactory` (expect/actual), `OpenFoodFactsApi`, `OpenAICompatibleClient`, DTOs (`AiChatDto`, `OpenFoodFactsDto`, `RecipeAiSchema`, `WorkoutAiSchema`)
  - `preferences/createDataStore.kt` (expect/actual)
  - `geofence/DebugGeofenceProvider.kt` — DEBUG-only Koin override
- Depends on: Domain (allowed — used by mappers like `WorkoutTemplateEntity.toDomain()`)
- Used by: Presentation (directly, instead of through use cases), Domain (violation), DI module

## Data Flow

### Primary Request Path — "Show workout templates list"

This trace follows the user opening the Workout tab.

1. **iOS UI**: `iosApp/iosApp/Views/Templates/TemplateListView.swift:6` instantiates `KoinHelper.shared.getTemplateListViewModel()` and observes via `KMPNativeCoroutinesAsync`
   _OR_
   **Android UI**: `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/TemplateListScreen.kt` uses `koinViewModel<TemplateListViewModel>()` and `collectAsState()`
2. **Presentation**: `shared/.../presentation/templates/TemplateListViewModel.kt:18` exposes `templates: StateFlow<List<WorkoutTemplate>>` built from `repository.getAllTemplates()`
3. **Data (Repository)**: `shared/.../data/repository/TemplateRepository.kt:44` `TemplateRepositoryImpl.getAllTemplates()` calls `templateDao.getAllTemplates()` and `flatMapLatest`-joins child exercises through `ExerciseRepository`
4. **Data (DAO)**: `shared/.../data/db/WorkoutTemplateDao.kt:10` Room generates SQL: `SELECT * FROM workout_templates ORDER BY updatedAt DESC` returning `Flow<List<WorkoutTemplateEntity>>`
5. **Mapper**: `shared/.../domain/model/WorkoutTemplate.kt:27` `WorkoutTemplateEntity.toDomain(exercises)` converts entity → domain model
6. **Back up the stack**: Flow re-emits → ViewModel’s `stateIn` re-publishes → UI recomposes

**What's well-separated:**
- ViewModel does not see Room entities — it consumes only `WorkoutTemplate` (domain)
- DAO is the only thing that knows about SQL
- StateFlow + `collectAsState` (Compose) / `@NativeCoroutinesState` (Swift) cleanly decouples UI from emission mechanics

**What's coupled:**
- The mapper `WorkoutTemplateEntity.toDomain()` lives _inside the domain model file_ (`domain/model/WorkoutTemplate.kt`) but imports `data.db.WorkoutTemplateEntity` and `data.db.TemplateExerciseEntity` — domain depends on data
- `TemplateListViewModel` constructor-injects `TemplateRepository` directly — no use case layer, no abstraction. Presentation reaches into `data.repository`.
- Repository interface and impl live in the same package (`data.repository.TemplateRepository` + `TemplateRepositoryImpl` in the same file)

### Secondary Flow — "Generate a workout via AI"

1. UI → `WorkoutAiViewModel` (`shared/.../presentation/ai/WorkoutAiViewModel.kt:42`)
2. ViewModel → `WorkoutAiUseCase` (`shared/.../domain/ai/WorkoutAiUseCase.kt`) ← _proper Clean-style indirection here_
3. `WorkoutAiUseCase` directly imports `data.api.OpenAICompatibleClient`, `data.api.ChatRequest`, `data.api.ResponseFormat`, `data.repository.TemplateRepository`, `data.repository.ExerciseRepository`, `data.repository.SettingsRepository` — **domain → data violation, multiple times**
4. `OpenAICompatibleClient` performs the HTTP call via Ktor
5. The use case writes the resulting template via `TemplateRepository.createTemplate()` + `addExercise()`

**State Management:**
- ViewModels expose `StateFlow<XState>` (kotlinx.coroutines)
- Android: `collectAsState()` in Composables
- iOS: `@NativeCoroutinesState` annotation auto-generates an `AsyncSequence` consumed by SwiftUI via `iosApp/iosApp/Utilities/FlowObservation.swift`
- Domain-layer state holders also exist (e.g. `EarlyExitTracker`, `PendingGeofenceExitStore`)

## Key Abstractions

**Repository (data-side):**
- Purpose: Aggregate one or more DAOs / APIs and expose domain-typed Flows
- Examples: `shared/.../data/repository/TemplateRepository.kt`, `WorkoutRepository.kt`, `FoodRepository.kt`, `ExerciseRepository.kt`, `GamificationRepository.kt`, `ProgressPictureRepository.kt`, `SettingsRepository.kt`
- Pattern: `interface` + `Impl` co-located in the same `data.repository` package. Consumed directly by ViewModels and use cases.

**UseCase (domain-side):**
- Purpose: Encapsulate a single business operation
- Examples: `shared/.../domain/nutrition/AddFoodUseCase.kt`, `LogConsumptionUseCase.kt`, `LookupBarcodeUseCase.kt`, `domain/ai/WorkoutAiUseCase.kt`, `domain/workout/GetUndertrainedMusclesUseCase.kt`
- Pattern: Inconsistent. **Nutrition** and **AI** features have explicit `*UseCase` classes wired through Koin. **Workout templates** have NO use cases — `TemplateListViewModel` and `TemplateEditorViewModel` talk to `TemplateRepository` directly.

**Platform Provider (domain-side expect/actual):**
- Purpose: Domain declares a contract; each platform supplies the impl
- Examples: `domain/location/LocationProvider.kt` (impls in `iosMain/data/location/IosLocationProvider.kt`, `androidMain/feature/location/AndroidLocationProvider.kt`), `domain/geofence/GeofenceProvider.kt`, `domain/permissions/PermissionController.kt`, `domain/progresspic/PhotoVault.kt`, `domain/ai/SecureKeyStore.kt`
- Pattern: This is the **only place where the dependency rule is correctly enforced** — domain defines the interface, data/platform supplies the impl. Good model for the rest of the codebase.

**Domain Model:**
- Purpose: Plain `data class` types describing the business
- Examples: `domain/model/WorkoutTemplate.kt`, `Exercise.kt`, `Food.kt`, `CompletedWorkout.kt`, `MuscleGroup.kt`, `WorkoutSession.kt`
- Pattern: kotlinx-style immutable data classes. Mapper extension functions (e.g. `WorkoutTemplateEntity.toDomain()`) are unfortunately co-located with the domain model, dragging Room entity imports into the domain layer.

## Entry Points

**Android — `MainActivity`:**
- Location: `androidApp/src/androidMain/kotlin/com/pumpernickel/android/MainActivity.kt`
- Triggers: System launches via `PumpernickelApplication` (`androidApp/.../PumpernickelApplication.kt`) which calls `initKoin { androidContext(this) }`
- Responsibilities: Hosts the `MainScreen` Composable with bottom-nav (Workout / Overview / Nutrition)

**iOS — `PumpernickelApp` + `AppDelegate`:**
- Location: `iosApp/iosApp/PumpernickelApp.swift` + `iosApp/iosApp/AppDelegate.swift`
- Triggers: SwiftUI `@main` app starts; `AppDelegate` calls `KoinInitIos.doInitKoin()` (`shared/.../iosMain/di/KoinInitIos.kt`)
- Responsibilities: Hosts `MainTabView.swift` with 3 tabs

**Background — Android `AiGenerationService`:**
- Location: `androidApp/src/androidMain/kotlin/com/pumpernickel/android/AiGenerationService.kt`
- Triggers: Foreground service kicked off by `AiGenerationManager`
- Responsibilities: Keeps AI generation running across process death

**Background — iOS `AiBgTaskRegistrar`:**
- Location: `shared/src/iosMain/kotlin/com/pumpernickel/domain/ai/AiBgTaskRegistrar.kt`
- Triggers: Registers `BGTaskScheduler` task during cold start

## Architectural Constraints

- **Threading:** All Room queries run on `Dispatchers.IO` (set in `SharedModule.kt:78` via `setQueryCoroutineContext`). Coroutine `viewModelScope` is `Dispatchers.Main.immediate` by default. Ktor calls are suspending.
- **Global state:** Koin DI graph is global (`startKoin` in `initKoin()`). Several `single<>` bindings (e.g. `AppDatabase`, `*Repository`, `EarlyExitTracker`). `AndroidPermissionController` and `BiometricGateActivityHolder` rely on an `Activity` reference held in module-level holders (`shared/.../androidMain/.../feature/permissions/PermissionActivityHolder.kt`, `.../feature/biometric/BiometricGateActivityHolder.kt`) — leak risk if not nulled on `onDestroy`.
- **Circular imports:** None at the JVM compile-unit level (Kotlin would fail), but a **conceptual cycle** exists: domain → data (entities, repositories) and data → domain (models, use case interfaces) cross-reference. This is only legal because both layers live in the same Gradle source set; splitting them into separate modules would surface the cycle.
- **`-Xexpect-actual-classes`:** Enabled globally (`shared/build.gradle.kts:31`) — required because Room's KMP expect-actual constructor classes are still experimental.
- **iOS framework export:** Single static framework `Shared.framework` (`shared/build.gradle.kts:23`). All public API needs `@OptIn(ExperimentalObjCName::class)` if renaming for Swift.
- **`allowOverride(true)` in Koin:** Enabled in `SharedModule.kt:142` so `KoinHelper.loadDebugGeofenceOverride()` can swap `GeofenceProvider` at runtime for the iOS Simulator. Production builds must not trigger this path.

## Anti-Patterns

### Domain model file owns the Entity → Domain mapper

**What happens:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/WorkoutTemplate.kt` declares the domain `data class WorkoutTemplate` _and_ a `WorkoutTemplateEntity.toDomain()` extension. The extension forces the file to `import com.pumpernickel.data.db.WorkoutTemplateEntity` — domain code depending on data code. Same pattern in `domain/model/Exercise.kt`.

**Why it's wrong:** The domain layer can no longer be compiled without the data layer. Use cases and tests in `domain/` can never be moved to a separate Gradle module. The dependency rule is inverted.

**Do this instead:** Move all mappers to `shared/src/commonMain/kotlin/com/pumpernickel/data/mappers/` (a new package). Mappers are inherently data-layer concerns because they know about Room entities.

### Use cases import concrete Repository implementations

**What happens:** `domain/ai/WorkoutAiUseCase.kt:6-14` imports `data.api.OpenAICompatibleClient`, `data.api.ChatRequest`, `data.repository.TemplateRepository`, `data.repository.ExerciseRepository`. Same in `RecipeAiUseCase.kt`, all `domain/nutrition/*UseCase.kt`, and `domain/workout/GetUndertrainedMusclesUseCase.kt`. `domain/gamification/GamificationEngine.kt:3-7` imports `data.db.CompletedWorkoutDao`, `data.db.ExerciseDao`, `data.db.NutritionDao` directly.

**Why it's wrong:** Use cases are supposed to be the most stable, most-testable, framework-free part of the system. They cannot be unit-tested without Room/Ktor classpaths once they import `data.db` / `data.api`.

**Do this instead:** Define repository contracts (e.g. `TemplateRepository` as an interface) in `domain/` (e.g. `domain/repository/TemplateRepository.kt`). Move the `Impl` to `data/repository/TemplateRepositoryImpl.kt`. Use cases import only the interface from `domain/`. For the AI use cases, define a `domain/ai/AiChatGateway` interface; implement it in `data/api/`.

### Repository interface and Impl in the same file/package

**What happens:** `shared/.../data/repository/TemplateRepository.kt` declares both `interface TemplateRepository` (lines 18-36) and `class TemplateRepositoryImpl` (lines 39-156).

**Why it's wrong:** The interface cannot be referenced by domain code without also pulling the implementation transitively. There is no inversion — the "interface" is just a header for the class. Tests cannot stub it cleanly when the production class is on the classpath.

**Do this instead:** `interface TemplateRepository` in `domain/repository/`; `class TemplateRepositoryImpl : TemplateRepository` in `data/repository/`. Domain depends only on the interface.

### ViewModel God Object

**What happens:** `shared/.../presentation/workout/WorkoutSessionViewModel.kt` is **1,171 lines** and is constructor-injected with **11 dependencies** (`SharedModule.kt:124`: `viewModel { WorkoutSessionViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }`).

**Why it's wrong:** Impossible to reason about, untestable in isolation, every change risks regressions across rest-timer/geofence/gamification/templates concerns simultaneously.

**Do this instead:** Decompose into use cases (`StartSessionUseCase`, `LogSetUseCase`, `AbortSessionUseCase`, `FinalizeSessionUseCase`) and per-concern state holders. ViewModel should orchestrate, not implement.

### Domain entities tightly mirror Room schema

**What happens:** `WorkoutTemplateEntity` and `WorkoutTemplate` have nearly identical fields (id, name, createdAt, updatedAt, source). `TemplateExerciseEntity.perSetReps` is stored as a comma-separated string and parsed in `domain/model/WorkoutTemplate.kt:50` — schema concerns leaking into the model.

**Why it's wrong:** Defeats the purpose of having a separate domain model. The "mapping" is identity-with-string-splitting.

**Do this instead:** Either accept this as MVVM (drop the `domain/model/` charade and call it a DTO), or enrich domain models with computed properties, value-typed wrappers (`WeightKg`, `Reps`), and proper aggregates so they offer something the entity doesn't.

## Error Handling

**Strategy:** No unified error policy. Each ViewModel handles errors in-state with feature-specific sealed classes (e.g. `domain/ai/AiError.kt`) or by translating exceptions to UI strings. See `CLAUDE.md` open question: _"Error Handling Strategie"_.

**Patterns:**
- `data/api/OpenFoodFactsApi.kt` catches and surfaces network failures as nullable returns or in-state error text
- `data/api/OpenAICompatibleClient.kt` throws domain-mapped `AiError` from `domain/ai/AiError.kt`
- Room exceptions are not caught — they propagate to the coroutine scope and silently kill the Flow
- No app-wide error reporting / crash analytics

## Cross-Cutting Concerns

**Logging:** No structured logger. `println` is used ad-hoc; no `Napier` / `Kermit` integration. Andoid emits via `Log.d` only inside platform-specific `androidMain` code (e.g. `GeofenceBroadcastReceiver`).

**Validation:** Per-feature. Examples: `domain/nutrition/ValidateFoodInputUseCase.kt`. No shared validation framework.

**Authentication:** Local-only app, no backend auth. Per-feature secrets handled via `domain/ai/SecureKeyStore.kt` (expect/actual) — Android uses `EncryptedSharedPreferences`, iOS uses Keychain.

**Dependency Injection:** Koin 4.x. Common module in `di/SharedModule.kt`. Feature submodules `di/GamificationModule.kt`, `GamificationEngineModule.kt`, `GamificationUiModule.kt`, `AchievementGalleryModule.kt`, `ProgressGalleryModule.kt`, `AiModule.kt`. Platform modules via `expect val platformModule: Module` (`shared/.../androidMain/di/PlatformModule.android.kt`, `iosMain/di/PlatformModule.ios.kt`). iOS additionally has hand-rolled `KoinHelper.kt` files (one per feature) to expose ViewModels to Swift.

## Module Structure

**Layer-first within feature-second** — the top-level package split is by Clean-Architecture layer (`data/`, `domain/`, `presentation/`, `di/`). Inside each layer, subpackages are by feature (`workout/`, `nutrition/`, `ai/`, `gamification/`, `templates/`, `exercises/`, `history/`, `overview/`, `progresspic/`, `settings/`, `geofence/`, `location/`, `permissions/`).

This is the inverse of feature-first packaging. Trade-offs:
- ✅ Easy to enforce layer rules with module/package linting (it would be — if the rules were respected today)
- ✅ Matches the Clean Architecture mental model
- ❌ A single feature is scattered across 3+ folders; refactoring "the workout feature" touches `data/db/`, `data/repository/`, `domain/model/`, `domain/workout/`, `presentation/workout/`, `presentation/templates/`, `presentation/history/`, plus the platform UI
- ❌ No Gradle-module enforcement — the layer boundaries exist only in folder names, not in compilation units, so violations slip in without compiler errors

## Dependency Rule Check (Clean Architecture audit)

Rule: **dependencies point inward only.** UI → Presentation → Domain ← Data. Domain depends on nothing.

| From | To | Allowed? | Status |
|------|----|----------|--------|
| `androidApp/.../ui` | `presentation`, `domain.model` | ✅ Yes | OK |
| `iosApp/.../Views` | `presentation` (via KoinHelper) | ✅ Yes | OK |
| `presentation/*` | `domain/*` | ✅ Yes | OK |
| `presentation/*` | `data/repository/*` | ⚠ Allowed only because no domain repository interfaces exist | **Violation in spirit** — every ViewModel injects concrete `data.repository.*Repository` types |
| `domain/model/WorkoutTemplate` | `data.db.WorkoutTemplateEntity` | ❌ No | **Violation** (`domain/model/WorkoutTemplate.kt:3-4`) |
| `domain/model/Exercise` | `data.db.ExerciseEntity` | ❌ No | **Violation** (`domain/model/Exercise.kt:3`) |
| `domain/ai/WorkoutAiUseCase` | `data.api.*`, `data.repository.*` | ❌ No | **Violation** (`domain/ai/WorkoutAiUseCase.kt:3-14`) |
| `domain/ai/RecipeAiUseCase` | `data.api.*`, `data.repository.*` | ❌ No | **Violation** (`domain/ai/RecipeAiUseCase.kt:5-12`) |
| `domain/nutrition/*UseCase` | `data.repository.FoodRepository`, `data.api.OpenFoodFactsApi` | ❌ No | **Violation** (10 use case files) |
| `domain/workout/GetUndertrainedMusclesUseCase` | `data.repository.ExerciseRepository`, `data.repository.WorkoutRepository` | ❌ No | **Violation** |
| `domain/gamification/GamificationEngine` | `data.db.CompletedWorkoutDao`, `data.db.ExerciseDao`, `data.db.NutritionDao`, `data.repository.GamificationRepository`, `data.repository.SettingsRepository` | ❌ No | **Violation** — domain reaching directly into DAOs |
| `domain/gamification/NutritionGoalDayPolicy` | `data.db.ConsumptionEntryEntity` | ❌ No | **Violation** |
| `domain/geofence/EarlyExitTracker` | `data.repository.SettingsRepository` | ❌ No | **Violation** |
| `data/repository/*` | `domain/model/*`, `domain/gamification/*`, `domain/geofence/*` | ✅ Yes | OK — data depending on domain abstractions is correct |
| `data/repository/RetroactiveWalker` | `domain/gamification/GamificationEngine`, `NutritionGoalDayPolicy` | ✅ Yes (data depends on domain logic) | OK |

**Summary:** **Domain → Data is violated in at least 18 files.** The Repository interfaces are not in `domain/`, so even legitimate-looking presentation/data wiring lacks the proper indirection. The only correctly-inverted contracts are the platform providers (`LocationProvider`, `GeofenceProvider`, `PermissionController`, `PhotoVault`, `SecureKeyStore`, `BiometricGate`, `PhotoCaptureLauncher`, `NotificationService`, `PendingGeofenceExitStore`) — these define `expect`/interface in `domain/` and implement in `data/` or `androidMain`/`iosMain`. They show the team _knows_ how to do it right when motivated.

---

*Architecture analysis: 2026-05-18*
