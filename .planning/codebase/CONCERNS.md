# Codebase Concerns

**Analysis Date:** 2026-05-18

## Clean Architecture Smells

> **CRITICAL CONTEXT:** The project intends layered MVVM (data → domain → presentation) — the package tree under `shared/src/commonMain/kotlin/com/pumpernickel/` reflects that with `data/`, `domain/`, `presentation/` siblings. However, the dependency direction is broken in multiple places. The result: a "Clean Architecture refactor" is currently impossible without first fixing the cycles below. Each smell is cited with `file:line` evidence.

### Smell 1 — Repository interfaces co-located in the **data** package (inverted Clean dependency)

**What happens:** Repository interfaces live in `com.pumpernickel.data.repository` alongside their `Impl` classes. In strict Clean Architecture, the **interface** belongs to domain (so domain doesn't know that an implementation exists), and the **Impl** belongs to data.

**Evidence:**
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/WorkoutRepository.kt:18` — `interface WorkoutRepository` (interface)
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/WorkoutRepository.kt:84` — `class WorkoutRepositoryImpl` (impl, same file)
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/TemplateRepository.kt:18` + line 39 — interface + Impl same file
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/GamificationRepository.kt:22` + line 67 — interface + Impl same file
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ExerciseRepository.kt:17` + line 26 — interface + Impl same file
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ProgressPictureRepository.kt:24` + line 63 — same pattern
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/FoodRepository.kt:8` (interface) + `FoodRepositoryImpl.kt:17` (impl) — only repo where they are split into separate files; interface still lives in `data.repository`

**Why it's a smell:** Domain code (e.g., `domain/ai/WorkoutAiUseCase.kt`, `domain/gamification/GamificationEngine.kt`) must import from `com.pumpernickel.data.repository.*` to call repos — domain now depends on data.

**Fix approach:** Move every `*Repository.kt` interface file (everything except the `*Impl` halves) into a new `com.pumpernickel.domain.repository` package. Update domain imports first (no behavior change), then Koin module bindings in `commonMain/kotlin/com/pumpernickel/di/SharedModule.kt`.

---

### Smell 2 — `SettingsRepository` is a concrete class (no interface) **and** implements a domain interface

**What happens:** `SettingsRepository` is a concrete class declared in `data/repository/`, but it directly implements the domain port `PendingGeofenceExitStore`. The class is also injected by concrete type, not by abstraction, into every consumer.

**Evidence:**
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt:28` — `class SettingsRepository(...) : PendingGeofenceExitStore`
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt:3-9` — direct `androidx.datastore.*` imports (DataStore is a data concern — fine for the impl, not for the contract)
- Every consumer imports the concrete class: `presentation/workout/WorkoutSessionViewModel.kt:6`, `presentation/overview/OverviewViewModel.kt:7`, `domain/geofence/EarlyExitTracker.kt:3`, etc.

**Why it's a smell:** Tests cannot fake settings without DataStore. The class mixes 5+ unrelated concerns (weight unit, theme, accent color, nutrition goals, geofence cold-start sentinel, early-exit budget, BYOK debug flag).

**Fix approach:** Extract a `SettingsRepository` interface into `domain/repository/`, leave the `DataStore` implementation in `data/`. Consider further splitting by concern (`UserSettings`, `GeofencePersistence`, `NutritionGoalsSettings`).

---

### Smell 3 — Domain layer imports Room **entities** and **DAOs**

**What happens:** Files inside `com.pumpernickel.domain` import classes from `com.pumpernickel.data.db`. This is a hard violation of the dependency rule (domain must not know about persistence).

**Evidence:**
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/GamificationEngine.kt:3` — `import com.pumpernickel.data.db.CompletedWorkoutDao`
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/GamificationEngine.kt:4` — `import com.pumpernickel.data.db.ExerciseDao`
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/GamificationEngine.kt:5` — `import com.pumpernickel.data.db.NutritionDao`
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/GamificationEngine.kt:6-7` — `import com.pumpernickel.data.repository.GamificationRepository / SettingsRepository`
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/NutritionGoalDayPolicy.kt:3` — `import com.pumpernickel.data.db.ConsumptionEntryEntity` (a pure D-04 predicate that takes a Room entity directly as input)
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/NutritionGoalDayPolicy.kt:32` — `fun isGoalDay(entries: List<ConsumptionEntryEntity>, goals: NutritionGoals): Boolean`

**Why it's a smell:** The "pure" domain predicate is bound to the Room schema. If `ConsumptionEntryEntity` gains a column, the policy signature changes. Tests for `NutritionGoalDayPolicy` (`commonTest/.../NutritionGoalDayPolicyTest.kt`) must construct Room entities to exercise pure math.

**Fix approach:** Introduce a `domain/model/ConsumptionEntry` data class; map at the repository boundary. Update `GamificationEngine`'s constructor to take repository abstractions only (no DAOs).

---

### Smell 4 — Domain layer imports Ktor DTOs and HTTP client

**What happens:** AI use cases under `domain/ai/` import response DTOs and the HTTP client from `data/api/` (Ktor-shaped types).

**Evidence:**
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt:3-11` — imports `com.pumpernickel.data.api.ChatMessage`, `ChatRequest`, `JsonSchemaSpec`, `OpenAICompatibleClient`, `ResponseFormat`, `WorkoutAiInlineExercise`, `WorkoutAiResponse`, `WorkoutAiTemplate`, `WorkoutAiTemplateExercise`
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt:5-11` — similarly imports `OpenAICompatibleClient` and `RecipeAiResponse`
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/SearchFoodsRemoteUseCase.kt:3` — `import com.pumpernickel.data.api.OpenFoodFactsApi`

**Why it's a smell:** Domain use-cases are coupled to the wire format. Switching providers (e.g., OpenAI → Anthropic) ripples into domain code.

**Fix approach:** Define a `domain/ai/AiClient` port that returns domain types (`WorkoutAiPreview`, `RecipeAiPreview`). The `OpenAICompatibleClient` becomes the adapter implementing that port.

---

### Smell 5 — Domain **models** map directly from Room entities (back-reference into data)

**What happens:** Domain data classes declare extension functions `EntityType.toDomain()` inside the same file, importing the Room entity. The domain model **depends on** the persistence representation.

**Evidence:**
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Exercise.kt:3` — `import com.pumpernickel.data.db.ExerciseEntity`
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Exercise.kt:24` — `fun ExerciseEntity.toDomain(): Exercise`
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/WorkoutTemplate.kt:3-4` — imports `TemplateExerciseEntity`, `WorkoutTemplateEntity`
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/WorkoutTemplate.kt:27` — `fun WorkoutTemplateEntity.toDomain(...)`

**Why it's a smell:** This is the most common Clean Architecture anti-pattern in the codebase. Removing it requires moving the `toDomain()` extensions into `data/repository/mapper/` or as private helpers inside each `RepositoryImpl`.

**Fix approach:** Move mapping extensions to `data/repository/mappers/ExerciseMapper.kt`, `TemplateMapper.kt`, etc. Strip the `import com.pumpernickel.data.*` lines from `domain/model/`.

---

### Smell 6 — Presentation layer (ViewModels) injects Room DAOs directly

**What happens:** `ProgressGalleryViewModel` is constructed with two raw Room DAOs.

**Evidence:**
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressGalleryViewModel.kt:5-6` — `import com.pumpernickel.data.db.GamificationDao` + `import com.pumpernickel.data.db.NutritionDao`
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressGalleryViewModel.kt:66-69` — constructor takes `gamificationDao: GamificationDao` and `nutritionDao: NutritionDao`
- The author flagged it themselves in the file's KDoc (`ProgressGalleryViewModel.kt:55-62`): _"Deviation (Rule 3) from plan ctor pin: the plan pins exactly 3 ctor params... Adding `nutritionDao` + `settingsRepository` as the minimum dependencies required to call the real API is the smallest correct fix"_.

**Why it's a smell:** Presentation reaches two layers past its allowed dependency. The VM also does N+1 per-tile aggregation (acknowledged in the KDoc line 50-53 as "known hot-path cost").

**Fix approach:** Add `getPrLedgerEntriesForWorkout(workoutId)` and `observeEntriesForDate(isoDate)` to repository interfaces; pass enriched tiles up from the repository.

---

### Smell 7 — Android Composable injects a `Repository` directly via Koin

**What happens:** A `@Composable` reaches past every layer and pulls `WorkoutRepository` straight into the UI.

**Evidence:**
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/DebugGeofencePanel.kt:21` — `import com.pumpernickel.data.repository.WorkoutRepository`
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/DebugGeofencePanel.kt:34` — `val workoutRepository: WorkoutRepository = koinInject()`
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/DebugGeofencePanel.kt:39` — `workoutRepository.getActiveSession()` called from inside a `LaunchedEffect`

**Why it's a smell:** Composable owns business state (region id), bypassing any ViewModel. Caveat: file is debug-only per the KDoc, but the pattern is still risky if copy-pasted.

**Fix approach:** Introduce `DebugGeofenceViewModel` that owns the `activeRegionId` state; expose `activeRegionId: StateFlow<String?>` and `onTriggerEnter()/onTriggerExit()` callbacks.

---

### Smell 8 — Room/SQLite exception type leaks into presentation

**What happens:** A ViewModel catches `androidx.sqlite.SQLiteException` directly, importing it inline.

**Evidence:**
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt:488` — `} catch (e: androidx.sqlite.SQLiteException) {` with inline FQCN

**Why it's a smell:** Presentation now knows the persistence library. The comment (lines 489-493) admits this is defense-in-depth for a known race — but the race itself is a deeper concern (see "Fragile Areas" below).

**Fix approach:** Translate `SQLiteException` into a domain `WorkoutSaveResult` sealed class inside `WorkoutRepositoryImpl.saveCompletedSet`. The VM should pattern-match on the result.

---

### Smell 9 — Use cases are inconsistent and partially absent

**What happens:** A `domain/nutrition/` package exists with 13 properly-named use cases (e.g., `AddFoodUseCase.kt`, `LookupBarcodeUseCase.kt`), but the **workout** vertical has only **one** use case (`GetUndertrainedMusclesUseCase.kt`). Workout business logic instead lives inside `WorkoutSessionViewModel` (1171 lines).

**Evidence:**
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/workout/` — contains only `GetUndertrainedMusclesUseCase.kt` and `UndertrainedMuscle.kt`
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/` — 13 use cases (well-structured)
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt:225-286` (`startWorkout`) — builds `SessionExercise`/`SessionSet`, loads previous performance, computes PBs, persists active session — all without going through a use case
- `WorkoutSessionViewModel.kt:438-495` (`completeSet`) — orchestrates session DB write, rest-timer scheduling, exception handling, cursor advance
- `WorkoutSessionViewModel.kt:730-795` and `1010-1080` — workout-save / abandoned-workout orchestration directly inside the VM

**Why it's a smell:** The VM mixes UI state, domain orchestration, and Room I/O. Testing requires faking 11 collaborators (see ctor lines 107-119). A Clean refactor would need to extract roughly 8 use cases first (`StartWorkoutUseCase`, `CompleteSetUseCase`, `AdvanceCursorUseCase`, `SaveCompletedWorkoutUseCase`, `AbortWorkoutUseCase`, `ResumeWorkoutUseCase`, `ComputePreFillUseCase`, `ComputeNextCursorUseCase`).

---

### Smell 10 — ViewModel performs data mapping that belongs in the repository

**What happens:** `WorkoutSessionViewModel` reshapes data shapes that the repository should already deliver in domain form.

**Evidence:**
- `WorkoutSessionViewModel.kt:229-248` — manually maps `TemplateExercise → SessionExercise` with `(0 until te.targetSets).map { idx -> SessionSet(...) }`
- `WorkoutSessionViewModel.kt:253` — `previousWorkout.exercises.associateBy { it.exerciseId }` (lookup-map construction in the VM)
- `WorkoutSessionViewModel.kt:317-330` — same `template.exercises.map { te -> SessionExercise(...) }` repeated for the rehydrate path

**Why it's a smell:** The two map-blocks (lines 229-248 and 318-336) are near-duplicates — domain logic for building a session lives in the VM in two places.

**Fix approach:** Add `domain/model/SessionExercise.kt` with `fun WorkoutTemplate.toSessionExercises(): List<SessionExercise>` (pure) or extract into `StartWorkoutUseCase` / `ResumeWorkoutUseCase`.

---

### Smell 11 — Domain orchestrator parked under `data/repository`

**What happens:** `RetroactiveWalker` is a one-shot orchestrator that runs the entire gamification replay logic — it is a use case in everything but name. It lives in `data/repository/`.

**Evidence:**
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/RetroactiveWalker.kt:29` — `class RetroactiveWalker(...)`
- `shared/src/commonMain/kotlin/com/pumpernickel/di/GamificationEngineModule.kt:30` — `single { RetroactiveWalker(get(), get(), get(), get()) }` (4 deps including engine + repos)

**Why it's a smell:** The "Walker" runs domain rules (`GamificationEngine.processWorkout`) over historical data — it is orchestration, not persistence.

**Fix approach:** Move to `domain/gamification/ApplyRetroactiveGamificationUseCase.kt`.

---

### Smell 12 — Platform `actual` types declared inside `domain/` (mixed responsibility)

**What happens:** `expect class BiometricGate`, `PhotoVault`, `PhotoCaptureLauncher`, `SecureKeyStore`, `NotificationService` are all in `commonMain/.../domain/...`, but their `actual` implementations on iOS sit in `iosMain/.../domain/progresspic/PhotoVault.ios.kt` (etc.) — i.e., the iOS file imports `platform.LocalAuthentication.*` / `platform.Security.*` / `platform.UserNotifications.*` while still being packaged as "domain".

**Evidence:**
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.kt:13` — `expect class BiometricGate`
- `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.ios.kt` — actual using `LAContext`
- `shared/src/iosMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.ios.kt:41-50` — direct `platform.Security.*` imports inside a "domain" file
- `shared/src/androidMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.android.kt` — directly references `androidx.security.crypto` types

**Inconsistency with Android:** the Android side has equivalent platform code under `androidMain/.../feature/biometric/`, `.../feature/photo/`, `.../feature/permissions/`, `.../feature/location/`, `.../feature/geofence/` — i.e., **the Android tree uses `feature/` for the same role the iOS tree uses `domain/` for**. There are two conflicting placement conventions.

**Why it's a smell:** "Domain" connotes pure business logic. Treating it as "every interface, regardless of whether the implementation calls UIKit" muddies the layer's meaning.

**Fix approach:** Either (a) move every `expect`/`actual` infrastructure port into `infrastructure/` or `platform/` packages, or (b) commit to the existing layout but document that `domain.progresspic` / `domain.ai` are "ports" not "pure logic". Whichever you choose, **unify Android and iOS** placement — pick one of `feature/` or `domain/`.

---

### Smell 13 — Domain `EarlyExitTracker` depends on a concrete data class

**What happens:** A domain service is constructed with the concrete `SettingsRepository` (a data class), not an interface.

**Evidence:**
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/geofence/EarlyExitTracker.kt:3` — `import com.pumpernickel.data.repository.SettingsRepository`

**Why it's a smell:** Same dependency-direction violation as Smell 1, but inside the domain layer.

**Fix approach:** Replace with a narrow domain port (e.g., `EarlyExitBudgetStore`) implemented by `SettingsRepository`.

---

### Smell 14 — Global mutable singleton holds API-key state

**What happens:** `ApiKeyState` is a Kotlin `object` (process-wide singleton) carrying a mutable `MutableStateFlow<Boolean>`.

**Evidence:**
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/ApiKeyState.kt:20-26` — `object ApiKeyState { private val _configured = MutableStateFlow(false); val configured: StateFlow<Boolean> = _configured.asStateFlow(); fun set(value: Boolean) { _configured.value = value } }`

**Why it's a smell:** Hidden global coupling. Any code can mutate it; tests cannot reset it deterministically between runs. The KDoc explicitly admits it exists because `SecureKeyStore.readApiKey()` is one-shot and ViewModels miss updates — the right fix is a `Flow<ApiKeyStatus>` on the `SecureKeyStore` port.

**Fix approach:** Make `SecureKeyStore` expose `val apiKeyStatus: Flow<Boolean>` directly; delete the singleton.

---

## Tech Debt

**Workout Vertical Bloat:**
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt` — 1616 lines (largest file in the repo)
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt` — 1171 lines, 26+ `fun` declarations, 11 ctor dependencies
- Impact: This is the most-changed feature; any modification risks regression in unrelated areas (geofence, gamification, rest timer, abort flow).
- Fix approach: Decompose into screen sections (`WorkoutSessionTopBar`, `WorkoutSessionSetEntry`, `WorkoutSessionRestTimer`, `WorkoutSessionExerciseList`) and extract use cases from the VM (see Smell 9).

**Acknowledged TODOs (tuning):**
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/RankLadder.kt:11` — `TODO(tuning): re-anchor BASE_XP after play-testing per D-07 / D-09 Claude-discretion.`
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/XpFormula.kt:47` — `TODO(tuning): the /100 divisor is a D-07 anchor; re-calibrate after play-testing.`
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/AchievementCatalog.kt:10` — `TODO(tuning): thresholds are D-07 / D-15 Claude-discretion anchors.`
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/OverviewRankStrip.kt:91` — `TODO(polish): swap to per-rank icon/badge asset`
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/OverviewRankStrip.kt:129` — `TODO(polish): per-rank gradient colours`
- Impact: Gamification feel is uncalibrated; visual polish missing.

**`println` used as logging across the AI + OFF API + ViewModel layers:**
- `shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt:46,59,61,63,73,76,104,124,143,164,172,175,192` — 13 `println` calls (includes truncated response bodies + AiError dumps)
- `shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsApi.kt:22,32,34,37` — 4 `println` calls
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/overview/OverviewViewModel.kt:148` — `println("GoalDayTrigger failed: ${t.message}")`
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt:493, 759, 1034, 1049, 1147` — error swallow points
- Impact: No log levels, no filtering, no structured logs. Production builds spam stdout with full API response previews (`responseText.take(2048)`).
- Fix approach: Add a KMP-compatible logger interface in `domain/log/Logger.kt`, wire `Napier` or a simple expect/actual print sink.

**Repository file `WorkoutRepository.kt` has 70+ line duplication:**
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/WorkoutRepository.kt:189-223` (`saveCompletedWorkout`) vs lines 225-259 (`saveAbandonedWorkout`) — bodies identical except for the `abandoned = false / true` boolean.
- Fix approach: Add a private `persistWorkout(workout, abandoned)` helper.

**`shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt`** carries 348 lines mixing 7 unrelated stored values (weight unit, theme, accent, nutrition goals, debug grace period, early-exit budget, geofence cold-start sentinel) — see Smell 2.

---

## Known Bugs

**FK race between set completion and grace-period auto-abort:**
- Files: `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt:488-494` (catch block), `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/WorkoutRepository.kt:142-158` (`saveCompletedSet`)
- Trigger: Geofence grace period expires while the user is mid-`completeSet` — parent `ActiveSessionEntity` is deleted, child `ActiveSessionSetEntity` insert violates FK.
- Workaround in place: swallow `SQLiteException` and let the abort UI take over. The comment calls this "defense-in-depth"; it implies the timing guards above are not airtight.
- Recommendation: Wrap `completeSet` + grace-period delete in the same DB transaction or use a `Mutex` in the VM to serialise the two paths.

**iOS `GlobalScope` for app-wide gamification startup:**
- File: `shared/src/iosMain/kotlin/com/pumpernickel/di/GamificationStartupIos.kt:23` — `GlobalScope.launch(Dispatchers.Default) { ... }`
- Symptoms: Coroutine cannot be cancelled; uncaught exceptions in `walker.applyIfNeeded()` would crash silently on iOS.
- KDoc admits it (`GamificationStartupIos.kt:13`): _"NOTE: uses GlobalScope because iOS has no Application-scope coroutine."_
- Recommendation: Create a `CoroutineScope(SupervisorJob() + Dispatchers.Default)` owned by the iOS app delegate and pass it in via Koin.

---

## Security Considerations

**BYOK API key in EncryptedSharedPreferences / Keychain (good baseline, but full key still printed in error paths):**
- Files: `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.kt`, `androidMain/.../SecureKeyStore.android.kt`, `iosMain/.../SecureKeyStore.ios.kt`
- Risk: `OpenAICompatibleClient.kt:46` logs `keyLen=${key.length}` and the full response body — if the user's prompt or completion echoes the key (e.g. `Authorization` header in a debug message from the provider), it lands in stdout.
- Current mitigation: only length, not the value, is logged.
- Recommendation: Strip every `println` from `OpenAICompatibleClient` for release builds (`#if !DEBUG` style); or convert to a `Logger.debug` that no-ops in release.

**Debug screens reachable in release builds:**
- File: `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/DebugGeofencePanel.kt`
- Risk: The KDoc says _"wrap call site with `if (BuildConfig.DEBUG) { DebugGeofencePanel() }`"_ but the wrap is not enforced at call sites.
- Recommendation: Move debug-only Composables under `androidApp/src/debug/` source set so they cannot be linked into release.

**No certificate pinning / TLS validation pinning** for the OpenAI-compatible client (`HttpClientFactory.kt`).
- Files: `commonMain/.../api/HttpClientFactory.kt`, `androidMain/.../api/HttpClientFactory.android.kt`, `iosMain/.../api/HttpClientFactory.ios.kt`
- Risk: MITM via compromised CA. Acceptable for a prototype but worth flagging before launch.

---

## Performance Bottlenecks

**N+1 in `ProgressGalleryViewModel`:**
- File: `shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressGalleryViewModel.kt:42-53` (the KDoc spells out the known cost)
- Cause: Per-tile DAO call to `GamificationDao.getPrLedgerEntriesForWorkout`.
- Mitigation: bounded by photographed workouts (<50 in prototype). Acceptable for now.
- Improvement path: Single aggregating DAO query returning `Map<workoutId, prCount>` joined with the gallery list.

**`Flow.first()` calls in ViewModels block UI threads briefly:**
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt:227` — `templateRepository.getTemplateById(templateId).first()`
- `WorkoutSessionViewModel.kt:306, 966`
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/overview/OverviewViewModel.kt:156, 173`
- Cause: Suspending on a cold Flow at startup. The `ProgressGalleryViewModel:75` comment shows the author already refactored one occurrence with `onStart { emit(default) }` — apply the same pattern elsewhere.

**Heavy `WorkoutSessionViewModel` constructor (11 collaborators) inflates first-screen Koin resolution.** Splitting into smaller VMs would shrink the graph.

---

## Fragile Areas

**`WorkoutSessionViewModel` (1171 lines):**
- Files: `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt`
- Why fragile: 11 ctor dependencies; manages 5+ overlapping flows (rest timer, elapsed timer, geofence observer, grace period, location capture). The `try { … } catch (SQLiteException)` block at line 488 is a band-aid over a race condition (see Known Bugs).
- Safe modification: Add a regression test in `commonTest` **before** changing anything inside `completeSet`, `saveCompletedWorkout`, or the geofence handler.
- Test coverage: **No `WorkoutSessionViewModelTest` exists.**

**`GamificationEngine` (499 lines, 5 collaborators incl. 3 DAOs):**
- Files: `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/GamificationEngine.kt`
- Why fragile: Mixes live (`onWorkoutSaved`) and retroactive paths through the same primitives, idempotency relies on `(source, eventKey)` unique index — a typo in `EventKeys` would re-award XP silently.
- Test coverage: Pure helpers (`XpFormula`, `RankLadder`, `StreakCalculator`, `AchievementRules`) are tested, but engine orchestration is not.

**CSV/JSON serialised inside SQLite TEXT columns:**
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/ExerciseEntity.kt:15` — `val instructions: String  // JSON string`
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/ExerciseEntity.kt:16` — `val images: String  // JSON string`
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/ExerciseEntity.kt:18` — `val primaryMuscles: String  // Comma-separated`
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/TemplateExerciseEntity.kt:29` — `val perSetReps: String?  // CSV "10,8,6"`
- Why fragile: No SQL filtering possible (`WHERE primaryMuscles CONTAINS 'chest'` doesn't exist as an index-friendly query). `try/catch (Exception)` in `Exercise.kt:32-41` swallows parse failures into empty lists.
- Improvement path: Introduce Room `@TypeConverter`s (or normalise into a join table for `primaryMuscles`).

---

## Scaling Limits

**Room `fallbackToDestructiveMigration(dropAllTables = true)`:**
- `shared/src/iosMain/kotlin/com/pumpernickel/data/db/Database.ios.kt:12` — `.fallbackToDestructiveMigrationFrom(dropAllTables = true, 6)`
- `shared/src/androidMain/kotlin/com/pumpernickel/platform/Database.android.kt:11` — `.fallbackToDestructiveMigration(dropAllTables = true)`
- Current capacity: schema 11 with AutoMigration 6→7→8→9→10→11 in `data/db/AppDatabase.kt:30-35`.
- Limit: Any future migration that AutoMigration cannot synthesize will silently wipe user data. iOS has a `From(6)` guard; Android has none.
- Scaling path: Replace Android-side fallback with explicit `Migration` classes; same guard on iOS.

**Single-row `ActiveSessionEntity` with `id = 1` sentinel:**
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/WorkoutRepository.kt:98-108` — `ActiveSessionEntity(id = 1, ...)`
- Comment in `WorkoutSessionViewModel.kt:166-168` flags this explicitly: _"ActiveSessionEntity.id is a singleton sentinel (=1) and MUST NOT be used"_ — workaround uses `startTimeMillis` instead for the region id.
- Limit: Cannot support multiple concurrent workouts (intentional for v1) but the sentinel design is implicit and error-prone.

---

## Dependencies at Risk

**Room KMP 2.8.4** is the linchpin and pulled in via `commonMain` — currently stable. The user's CLAUDE.md explicitly avoids Room 3.0 alpha. Low risk for the May 2026 deadline.

**`androidx.sqlite.SQLiteException` imported in `commonMain`** (`WorkoutSessionViewModel.kt:488`) — would break if iOS Room stopped re-exporting the AndroidX-named exception. Currently works because Room KMP keeps the type name. Documented as a Smell (#8) above.

**`kmp-nativecoroutines` (Rick Clephas)** — third-party, used heavily across ViewModels for `@NativeCoroutinesState` (visible in `WorkoutSessionViewModel.kt:122, 127, 131, ...`). If the library lags Kotlin releases, every VM compile breaks. Mitigation: pin Kotlin version exactly.

---

## Missing Critical Features

**Logger abstraction:** All "logging" goes through `println` (see Tech Debt). Until replaced, production builds will leak structured data to stdout.

**Clock injection:** Every domain/data file calls `kotlin.time.Clock.System.now()` directly.
- 18+ occurrences including `GamificationEngine.kt:498`, `WorkoutRepository.kt:156,174,181`, `TemplateRepository.kt:85,97,112`, `CreateExerciseViewModel.kt:75`, `OverviewViewModel.kt:30`.
- Impact: Tests for time-dependent logic (rest timer, streak day boundaries, geofence grace period) cannot run deterministically without faking `Clock.System`.
- Fix approach: Add a `domain/time/Clock.kt` interface; replace direct `Clock.System.now()` calls with the injected dependency.

**ViewModel + Repository tests:** `commonTest/` only has 7 files (`XpFormulaTest`, `RankLadderTest`, `StreakCalculatorTest`, `AchievementCatalogTest`, `AchievementRulesTest`, `NutritionGoalDayPolicyTest`, `TdeeCalculatorTest`) — all pure-domain helpers. **Zero VM tests, zero repository tests.**

---

## Test Coverage Gaps

**No tests for any ViewModel:**
- Untested files: every `presentation/.../*ViewModel.kt` (22 ViewModels including the 1171-line `WorkoutSessionViewModel`).
- Risk: state-machine bugs (workout abort vs save race, geofence cold-start replay, rest timer cancellation) go uncaught.
- Priority: **High** — `WorkoutSessionViewModel` first.

**No tests for any Repository:**
- Untested files: every `data/repository/*.kt` impl.
- Risk: SQL/Room schema changes (CSV split parsing, JSON decoding) can silently break mapping. `Exercise.kt:32-41` already swallows parse failures with `try { … } catch (_: Exception) { emptyList() }`.
- Priority: **High** — `WorkoutRepositoryImpl.saveCompletedWorkout` and `ExerciseRepositoryImpl` first.

**No tests for `GamificationEngine` orchestration** (only its pure helpers).
- Priority: **Medium** — orchestration spans retroactive + live paths with idempotency relying on string keys.

**No integration test for the geofence grace-period FK race** (see Known Bugs).
- Priority: **Medium-High** — the existing `try/catch` is silent; a regression would not be detected.

**No tests for AI flows (`WorkoutAiUseCase`, `RecipeAiUseCase`, `AiGenerationManager`)** — these include schema-constrained JSON parsing and timeout handling, both error-prone.
- Priority: **Medium**.

---

*Concerns audit: 2026-05-18*
