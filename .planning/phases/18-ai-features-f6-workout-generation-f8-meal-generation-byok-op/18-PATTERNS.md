# Phase 18: AI Features (F6 + F8 BYOK) — Pattern Map

**Mapped:** 2026-05-07
**Files analyzed:** 28 new files + 6 modified files
**Analogs found:** 28 / 28 (every new file has a strong existing analog)

## File Classification

### Shared `commonMain` — data / API layer

| New / Modified File | Role | Data Flow | Closest Analog | Match |
|---------------------|------|-----------|----------------|-------|
| `shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt` | api-client | request-response (HTTPS POST + JSON) | `OpenFoodFactsApi.kt` | exact |
| `shared/src/commonMain/kotlin/com/pumpernickel/data/api/AiChatDto.kt` (request bodies + response wrapper) | DTO | serialization | `OpenFoodFactsDto.kt` | exact |
| `shared/src/commonMain/kotlin/com/pumpernickel/data/api/WorkoutAiSchema.kt` | schema model (`@Serializable`) | serialization | `OpenFoodFactsDto.kt` + `domain/model/WorkoutTemplate.kt` | role-match |
| `shared/src/commonMain/kotlin/com/pumpernickel/data/api/RecipeAiSchema.kt` | schema model (`@Serializable`) | serialization | `OpenFoodFactsDto.kt` + `domain/model/Recipe.kt` + `Food.kt` | role-match |
| `shared/src/commonMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.kt` (MODIFIED — extend default factory or add named overload) | platform factory (`expect`) | construction | existing `HttpClientFactory.kt` | exact (file already exists) |
| `shared/src/androidMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.android.kt` (MODIFIED) | actual | construction | existing `HttpClientFactory.android.kt` | exact |
| `shared/src/iosMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.ios.kt` (MODIFIED) | actual | construction | existing `HttpClientFactory.ios.kt` | exact |

### Shared `commonMain` — secure key store (`expect/actual`)

| File | Role | Data Flow | Closest Analog | Match |
|------|------|-----------|----------------|-------|
| `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.kt` | platform-private I/O (`expect class`) | suspend read/write | `domain/progresspic/PhotoVault.kt` | exact |
| `shared/src/iosMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.ios.kt` | iOS Keychain actual | I/O | `domain/progresspic/PhotoVault.ios.kt` | role-match (iOS native interop) |
| `shared/src/androidMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.android.kt` | EncryptedSharedPreferences actual | I/O | `domain/progresspic/PhotoVault.android.kt` | role-match (Context-injected actual) |

### Shared `commonMain` — domain & use cases

| File | Role | Data Flow | Closest Analog | Match |
|------|------|-----------|----------------|-------|
| `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt` (sealed class) | domain error type | event | `domain/progresspic/UnlockResult.kt` (sealed class) | exact |
| `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiPromptCatalog.kt` | resource loader | I/O (loads `.md` from resources) | `Platform.kt` (`expect fun readResourceFile`) | exact |
| `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt` | use case | request-response orchestration | `domain/nutrition/LookupBarcodeUseCase.kt` | role-match |
| `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt` | use case | request-response orchestration | `domain/nutrition/LookupBarcodeUseCase.kt` | role-match |
| `shared/src/commonMain/resources/ai-prompts/workout-system.md` (NEW resource) | static prompt template | static text | existing `shared/src/commonMain/resources/free_exercise_db.json` | role-match |
| `shared/src/commonMain/resources/ai-prompts/recipe-system.md` (NEW resource) | static prompt template | static text | existing `free_exercise_db.json` | role-match |

### Shared `commonMain` — presentation (ViewModels)

| File | Role | Data Flow | Closest Analog | Match |
|------|------|-----------|----------------|-------|
| `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt` | viewmodel | StateFlow + suspend save | `presentation/templates/TemplateEditorViewModel.kt` + `presentation/progresspic/ProgressGalleryViewModel.kt` | exact |
| `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt` | viewmodel | StateFlow + suspend save | `presentation/nutrition/RecipeCreationViewModel.kt` + `progresspic/ProgressGalleryViewModel.kt` | exact |
| `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt` | viewmodel | StateFlow + DataStore + Keychain writes | `presentation/settings/SettingsViewModel.kt` | exact |

### Shared `commonMain` — repository extensions

| File | Role | Data Flow | Closest Analog | Match |
|------|------|-----------|----------------|-------|
| `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt` (MODIFIED — add provider preset / base URL / model fields) | repository | DataStore key/value | itself (existing file — pattern is canonical) | exact (extend file) |

### Shared `commonMain` — DI

| File | Role | Data Flow | Closest Analog | Match |
|------|------|-----------|----------------|-------|
| `shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt` | DI module | wiring | `di/ProgressGalleryModule.kt` | exact |
| `shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt` (MODIFIED — `includes(aiModule)`) | DI root | wiring | itself | exact |
| `shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt` (MODIFIED — `single<SecureKeyStore> { SecureKeyStore() }`) | platform DI | wiring | itself | exact |
| `shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt` (MODIFIED — `single<SecureKeyStore> { SecureKeyStore(androidContext()) }`) | platform DI | wiring | itself | exact |

### Shared `iosMain` — KoinHelpers

| File | Role | Data Flow | Closest Analog | Match |
|------|------|-----------|----------------|-------|
| `shared/src/iosMain/kotlin/com/pumpernickel/di/WorkoutAiKoinHelper.kt` | iOS factory | wiring | `ProgressGalleryKoinHelper.kt` / `AchievementGalleryKoinHelper.kt` | exact |
| `shared/src/iosMain/kotlin/com/pumpernickel/di/RecipeAiKoinHelper.kt` | iOS factory | wiring | `ProgressGalleryKoinHelper.kt` | exact |
| `shared/src/iosMain/kotlin/com/pumpernickel/di/AiSettingsKoinHelper.kt` | iOS factory | wiring | `ProgressGalleryKoinHelper.kt` | exact |

### Shared `commonMain` — DB schema (Room v9 → v10)

| File | Role | Data Flow | Closest Analog | Match |
|------|------|-----------|----------------|-------|
| `shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt` (MODIFIED — bump to `version = 10`, add `AutoMigration(9, 10)`) | DB schema | migration | itself (precedents `AutoMigration(7,8)` and `AutoMigration(8,9)`) | exact |
| `shared/src/commonMain/kotlin/com/pumpernickel/data/db/WorkoutTemplateEntity.kt` (MODIFIED — add `val source: String? = null`) | entity | persistence | itself | exact |
| `shared/src/commonMain/kotlin/com/pumpernickel/data/db/ExerciseEntity.kt` (MODIFIED — add `val source: String? = null`) | entity | persistence | itself | exact |
| `shared/src/commonMain/kotlin/com/pumpernickel/data/db/RecipeEntity.kt` (MODIFIED — add `val source: String? = null`) | entity | persistence | itself | exact |
| `shared/src/commonMain/kotlin/com/pumpernickel/data/db/FoodEntity.kt` (MODIFIED — add `val source: String? = null`) | entity | persistence | itself | exact |
| Domain models — `WorkoutTemplate.kt`, `Exercise.kt`, `Recipe.kt`, `Food.kt` (MODIFIED — propagate `source`) | domain | mapping | itself | exact |

### Android UI

| File | Role | Data Flow | Closest Analog | Match |
|------|------|-----------|----------------|-------|
| `androidApp/.../ui/screens/AiWorkoutGenScreen.kt` | Compose screen | StateFlow → UI | `ui/screens/ProgressGalleryScreen.kt` + `TemplateEditorScreen.kt` | exact |
| `androidApp/.../ui/screens/AiMealGenScreen.kt` | Compose screen | StateFlow → UI | `ui/screens/ProgressGalleryScreen.kt` + `NutritionRecipeCreationScreen.kt` | exact |
| `androidApp/.../ui/screens/AiSettingsScreen.kt` | Compose screen | StateFlow + form | `ui/screens/SettingsSheet.kt` (and Phase-16 `NutritionGoalsEditorScreen.kt`) | role-match |
| `androidApp/.../ui/screens/AiPreviewSheet.kt` | Compose `ModalBottomSheet` | StateFlow → UI | `ui/screens/AnatomyPickerSheet.kt` | exact |
| `androidApp/.../ui/navigation/Routes.kt` (MODIFIED — add `AiWorkoutGenRoute`, `AiMealGenRoute`, `AiSettingsRoute`) | route enum | navigation | itself | exact |
| `androidApp/.../ui/navigation/MainScreen.kt` (MODIFIED — register routes + add AI button entry on Workout + Nutrition tabs) | nav host | navigation | itself | exact |

### iOS UI (per `18-IOS-HANDOFF.md`)

| File | Role | Data Flow | Closest Analog | Match |
|------|------|-----------|----------------|-------|
| `iosApp/iosApp/Views/AI/AiSettingsView.swift` | SwiftUI screen | `asyncSequence` | `Views/Settings/SettingsView.swift` | exact |
| `iosApp/iosApp/Views/AI/AiWorkoutGenView.swift` | SwiftUI screen | `asyncSequence` | `Views/Overview/ProgressGalleryView.swift` + `Views/Templates/TemplateEditorView.swift` | exact |
| `iosApp/iosApp/Views/AI/AiMealGenView.swift` | SwiftUI screen | `asyncSequence` | `Views/Overview/ProgressGalleryView.swift` + `Views/Nutrition/NutritionRecipeCreationView.swift` | exact |
| `iosApp/iosApp/Views/AI/AiPreviewSheet.swift` | SwiftUI modal sheet | `asyncSequence` | `Views/Overview/ProgressGalleryView.swift` | role-match |
| `iosApp/iosApp/Views/Settings/SettingsView.swift` (MODIFIED — add NavigationLink to `AiSettingsView`) | edit | nav | itself | exact |
| `iosApp/iosApp/Views/MainTabView.swift` (MODIFIED — add AI toolbar button on Workout + Nutrition tab roots) | edit | nav | itself | exact |

### Phase artifacts

| File | Role | Data Flow | Closest Analog | Match |
|------|------|-----------|----------------|-------|
| `.planning/phases/18-.../18-IOS-HANDOFF.md` | doc | n/a | `.planning/phases/17-.../17-IOS-HANDOFF.md` (and `15.1-IOS-HANDOFF.md`) | exact |

---

## Pattern Assignments

### `OpenAICompatibleClient.kt` (api-client, request-response)

**Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsApi.kt`

**Imports + class shape pattern** (lines 1-12 of `OpenFoodFactsApi.kt`):
```kotlin
package com.pumpernickel.data.api

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

class OpenFoodFactsApi(private val client: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true }
```

**Core suspend method pattern** (lines 13-19):
```kotlin
suspend fun lookupBarcode(barcode: String): OpenFoodFactsResponse {
    val responseText = client.get("https://world.openfoodfacts.org/api/v2/product/$barcode.json") {
        header("User-Agent", "PumpernickelApp/1.0 (Android/iOS; contact@pumpernickel.app)")
    }.bodyAsText()
    return json.decodeFromString(responseText)
}
```

**Diff for AI client (apply to copy):**
- Use `client.post(...)` instead of `get(...)`; pass JSON body via `setBody(...)` + `contentType(ContentType.Application.Json)`.
- Replace `User-Agent` header with `Authorization: Bearer ${apiKey}` (the key arrives via constructor `suspend (() -> String?)` from `SecureKeyStore.read()` so the class never holds the secret).
- Constructor takes the `HttpClient` plus the `SecureKeyStore` and `SettingsRepository` (for base URL + model). The base URL is per-call (read from settings) so the same client serves preset switches without rebuild.
- Wrap the call in `try { ... } catch (e: …) { throw AiError.fromThrowable(e) }` mapping Ktor `HttpRequestTimeoutException` → `AiError.Timeout`, `IOException` → `AiError.Network`, `ClientRequestException` (4xx) → `AiError.AuthOrQuota`, `ServerResponseException` (5xx) → `AiError.Provider` (per D-18-08).
- Install `HttpTimeout` plugin on the client (60 s per D-18-16) and `ContentNegotiation` with the same `Json { ignoreUnknownKeys = true }`.

**No analog for `response_format: json_schema`** — the JSON body shape (`messages: [...], response_format: {…}, model, temperature`) follows OpenAI's published API; planner verifies during research and emits a Kotlin `@Serializable` `ChatRequest` mirroring it.

---

### `AiChatDto.kt` and `WorkoutAiSchema.kt` / `RecipeAiSchema.kt` (DTO, serialization)

**Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsDto.kt`

**Full file pattern** (all 25 lines — minimal, idiomatic):
```kotlin
package com.pumpernickel.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenFoodFactsResponse(
    val status: Int = 0,
    val product: ProductDto? = null
)

@Serializable
data class ProductDto(
    @SerialName("product_name") val productName: String? = null,
    val nutriments: NutrimentsDto? = null
)
```

**Apply to:**
- `AiChatDto.kt` — `ChatRequest`, `ChatMessage` (`role`, `content`), `ResponseFormat` (sealed: `JsonSchema`, `JsonObject`), `ChatResponse`, `Choice`, `ChoiceMessage`. All `@Serializable` with default-null fields and `@SerialName` where the wire name differs (`response_format`, `finish_reason`, etc.).
- `WorkoutAiSchema.kt` — `WorkoutAiResponse(templates: List<WorkoutAiTemplate>)` and nested data classes mirroring `WorkoutTemplate.kt` + `TemplateExercise.kt` (name, exercises[], targetSets, targetReps, restPeriodSec). New-exercise emits as inline `WorkoutAiExercise(name, primaryMuscles: List<String>, equipment, …)`.
- `RecipeAiSchema.kt` — `RecipeAiResponse(name, ingredients[], steps?)`. Each ingredient is `RecipeAiIngredient(food: RecipeAiInlineFood, amountGrams: Double)` per D-18-10. `RecipeAiInlineFood` mirrors `Food.kt` per-100g shape.

---

### `HttpClientFactory.kt` extension (platform factory)

**Analog:** existing `HttpClientFactory.kt` + `.android.kt` + `.ios.kt`.

**Common (3 lines, lines 1-5):**
```kotlin
package com.pumpernickel.data.api

import io.ktor.client.HttpClient

expect fun createHttpClient(): HttpClient
```

**Android actual** (`HttpClientFactory.android.kt`, all 6 lines):
```kotlin
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun createHttpClient(): HttpClient = HttpClient(OkHttp)
```

**Apply to:**
- Don't fork the file. **Extend** the existing `createHttpClient()` to install `ContentNegotiation(Json { ignoreUnknownKeys = true })` and `HttpTimeout(requestTimeoutMillis = 60_000)` plugins inside the `HttpClient { … }` block. Both are needed by `OpenFoodFactsApi` too (currently it parses with its own `Json` instance, so the additions are non-breaking) — verify at planning time whether to keep a single shared client or expose a named overload `createAiHttpClient()`. **Recommended:** add the plugins to the shared one; the AI client just uses it.

---

### `SecureKeyStore.kt` (expect class, platform-private I/O)

**Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.kt`

**`expect class` shape** (lines 13-25 of `PhotoVault.kt`):
```kotlin
expect class PhotoVault {
    /** Writes the JPEG bytes; returns the relative path stored in the DB row. */
    suspend fun write(id: String, bytes: ByteArray): String

    /** Reads the bytes for an existing relative path; null if missing. */
    suspend fun read(relativePath: String): ByteArray?

    /** Deletes a single file (best-effort; missing file is not an error). */
    suspend fun delete(relativePath: String)
}
```

**Apply to `SecureKeyStore.kt`:**
```kotlin
expect class SecureKeyStore {
    suspend fun writeApiKey(value: String)
    suspend fun readApiKey(): String?
    suspend fun clearApiKey()
}
```
Single key (D-18-06 "API key" field). All three methods are `suspend` (consistent with `PhotoVault`).

---

### `SecureKeyStore.android.kt` (EncryptedSharedPreferences actual)

**Analog:** `shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.android.kt`

**Constructor + Context-injection pattern** (lines 19-25 of `PhotoVault.android.kt`):
```kotlin
actual class PhotoVault(private val context: Context) {

    private val rootDir: File by lazy {
        File(context.filesDir, "progress_pics").apply { mkdirs() }
    }
```

**Suspend + Dispatchers.IO pattern** (lines 50-55):
```kotlin
actual suspend fun write(id: String, bytes: ByteArray): String =
    withContext(Dispatchers.IO) {
        val file = File(rootDir, "$id.jpg")
        file.writeBytes(bytes)
        "progress_pics/$id.jpg"
    }
```

**Apply to `SecureKeyStore.android.kt`:**
- `actual class SecureKeyStore(private val context: Context)`.
- Lazily build an `EncryptedSharedPreferences` (alias `MasterKey.DEFAULT_MASTER_KEY_ALIAS`, `AES256_SIV` for keys, `AES256_GCM` for values) inside a `private val prefs by lazy { ... }` block.
- All `actual suspend fun` bodies wrap in `withContext(Dispatchers.IO)`.
- Add `androidx.security:security-crypto:1.1.x` to the `androidMain` target's `build.gradle.kts`.
- DI binding mirrors `single<PhotoVault> { PhotoVault(androidContext()) }` in `PlatformModule.android.kt` line 20.

---

### `SecureKeyStore.ios.kt` (Keychain Services actual)

**Analog:** `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.ios.kt`

**File-level OptIn + Foundation imports pattern** (lines 1-11 of `PhotoVault.ios.kt`):
```kotlin
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.pumpernickel.domain.progresspic

import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSData
```

**`memScoped { val errorVar = alloc<ObjCObjectVar<NSError?>>() … errorVar.ptr }` pattern** (lines 73-82) is the canonical iOS error-handling cinterop idiom — apply identically to `SecItemAdd` / `SecItemCopyMatching` / `SecItemDelete` calls.

**Apply to `SecureKeyStore.ios.kt`:**
- `actual class SecureKeyStore` — no constructor params (matches Phase-17 iOS no-arg actuals: `single<PhotoVault> { PhotoVault() }` in `PlatformModule.ios.kt` line 19).
- Use `platform.Security.SecItemAdd` / `SecItemCopyMatching` / `SecItemDelete` with a `kSecClassGenericPassword` query, fixed `kSecAttrService = "PumpernickelApp.AI"` and `kSecAttrAccount = "openai.api.key"`, and `kSecAttrAccessible = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` (no iCloud sync, available after first unlock).
- Wrap calls in `memScoped { … }` blocks and surface `OSStatus != errSecSuccess` (other than `errSecItemNotFound` for read-miss) as exceptions caught at the use-case layer.

---

### `AiError.kt` (sealed class)

**Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/UnlockResult.kt` (and the inline `EvaluateOutcome` sealed class in `BiometricGate.ios.kt` lines 145-151).

**Sealed-class pattern** (BiometricGate.ios.kt lines 145-151):
```kotlin
private sealed class EvaluateOutcome {
    object Success : EvaluateOutcome()
    object UserCancelled : EvaluateOutcome()
    object AuthFailed : EvaluateOutcome()
    object BiometryUnavailable : EvaluateOutcome()
    data class Other(val message: String) : EvaluateOutcome()
}
```

**Apply to `AiError.kt`** — five canonical classes per D-18-08:
```kotlin
sealed class AiError {
    object Timeout : AiError()                         // class 1
    object Network : AiError()                         // class 2
    data class AuthOrQuota(val httpStatus: Int) : AiError()   // class 3 (4xx)
    data class Provider(val httpStatus: Int) : AiError()      // class 4 (5xx)
    data class SchemaInvalid(val message: String) : AiError() // class 5
    object Cancelled : AiError()                       // user-cancel, no toast
}
```
Plus a `companion object { fun fromThrowable(t: Throwable): AiError { … } }` mapping Ktor exceptions (mirror the `when (error.code.toLong()) { … }` style at BiometricGate.ios.kt lines 116-129).

---

### `AiPromptCatalog.kt` (resource loader)

**Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/Platform.kt` + `Platform.ios.kt` + `Platform.android.kt` (the `readResourceFile` expect/actual triple).

**`expect` declaration** (`Platform.kt`, all 3 lines):
```kotlin
package com.pumpernickel

expect fun readResourceFile(fileName: String): String
```

**iOS actual** (`Platform.ios.kt` lines 10-18 — uses `NSBundle.mainBundle.pathForResource`):
```kotlin
actual fun readResourceFile(fileName: String): String {
    val parts = fileName.split(".")
    val name = parts.dropLast(1).joinToString(".")
    val ext = parts.lastOrNull() ?: ""
    val path = NSBundle.mainBundle.pathForResource(name, ext)
        ?: error("Resource file not found: $fileName")
    return NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
        ?: error("Could not read resource file: $fileName")
}
```

**Android actual** (`Platform.android.kt` lines 6-9 — uses `context.assets.open`):
```kotlin
actual fun readResourceFile(fileName: String): String {
    val context = GlobalContext.get().get<Context>()
    return context.assets.open(fileName).bufferedReader().readText()
}
```

**Apply to `AiPromptCatalog.kt`:**
- **Reuse** `readResourceFile`. No new `expect/actual` needed.
- Class signature: `class AiPromptCatalog { fun workoutSystemPrompt(locale: String): String = readResourceFile("ai-prompts/workout-system.md").replace("{locale}", locale); fun recipeSystemPrompt(locale: String): String = readResourceFile("ai-prompts/recipe-system.md").replace("{locale}", locale) }`.
- iOS: drop the two `.md` files into `iosApp/iosApp/Resources/` (or `shared/src/iosMain/resources/`) and ensure they ship in the `iosApp` target's bundle. Android: place in `shared/src/commonMain/resources/ai-prompts/` (the existing `free_exercise_db.json` lives in the same dir and is loadable via `context.assets.open(fileName)` — verify resource-shipping path during planning since `commonMain/resources` may need module-level Gradle config to surface in Android assets).
- Locale defaults to `"de"` per D-18-15; the call site reads it from the app's UI locale.

---

### `WorkoutAiUseCase.kt` / `RecipeAiUseCase.kt` (use case, orchestration)

**Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/LookupBarcodeUseCase.kt` (per `SharedModule.kt` line 103: `single { LookupBarcodeUseCase(get(), get()) }`) and the orchestration logic inside `RecipeCreationViewModel.kt` lines 63-76.

**Two-collaborator constructor pattern** (typical use case shape):
```kotlin
class LookupBarcodeUseCase(
    private val api: OpenFoodFactsApi,
    private val foodRepository: FoodRepository
) {
    suspend operator fun invoke(barcode: String): Food? = ...
}
```

**Apply to `WorkoutAiUseCase.kt`:**
- Constructor: `(client: OpenAICompatibleClient, promptCatalog: AiPromptCatalog, exerciseRepository: ExerciseRepository, templateRepository: TemplateRepository, json: Json)`.
- `suspend operator fun invoke(form: WorkoutAiForm): Result<WorkoutAiPreview>` — builds prompt (via catalog + `{locale}` substitution), calls client with primary `response_format: json_schema`, parses, validates against `WorkoutAiSchema`, **resolves Exercise references** (D-18-09 case-insensitive trimmed name match against `ExerciseRepository`; new entries staged with `source = "AI"`), returns a preview-ready domain object **without writing to the DB** (D-18-12 commit-on-Save).
- Retry-once fallback per D-18-14: on schema-invalid or "structured-output not supported" error, retry with `response_format: json_object` + schema embedded in system prompt.
- `commit(preview: WorkoutAiPreview)` is a separate `suspend` method called from the VM on Save — wraps `templateRepository.createTemplate(...)` + per-exercise inserts in a single try-catch, mirroring the `try { … } catch (e: Exception) { _saveResult.value = SaveResult.Error(...) }` pattern from `TemplateEditorViewModel.kt` lines 233-271.

**Apply to `RecipeAiUseCase.kt`:** same shape with `(client, promptCatalog, foodRepository, settingsRepository, calculateRecipeMacrosUseCase, calculateDailyMacrosUseCase, json)`. Computes today's remaining macros up front (D-18-04 / REQ-AI-04), short-circuits when remaining ≤ 0 with a `RemainingExhausted` result. On success, returns inline `Food` matches resolved against `foodRepository.loadFoods()` (case-insensitive trimmed-name match per D-18-10); unmatched foods are staged with `source = "AI"` for transactional commit.

---

### `WorkoutAiViewModel.kt` / `RecipeAiViewModel.kt` (viewmodel, StateFlow + suspend save)

**Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/presentation/templates/TemplateEditorViewModel.kt` + `presentation/progresspic/ProgressGalleryViewModel.kt`.

**`@NativeCoroutinesState val uiState: StateFlow<…>` exposure** (`ProgressGalleryViewModel.kt` lines 72-106):
```kotlin
@NativeCoroutinesState
val uiState: StateFlow<GalleryUiState> = combine(
    repository.observeGalleryTiles(),
    settingsRepository.nutritionGoals.onStart { emit(NutritionGoals()) },
    nutritionDao.observeAllEntries()
) { rawTiles, goals, allEntries ->
    /* derive */
}
    .map { tiles -> GalleryUiState(tiles = tiles, isLoading = false) }
    .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        GalleryUiState(tiles = emptyList(), isLoading = true)
    )
```

**SharedFlow nav-event pattern** (lines 108-124):
```kotlin
private val _navEvents = MutableSharedFlow<NavEvent>(extraBufferCapacity = 1)

@NativeCoroutines
val navEvents: SharedFlow<NavEvent> = _navEvents.asSharedFlow()

fun onTileTapped(workoutId: Long) {
    viewModelScope.launch {
        _navEvents.emit(NavEvent.OpenViewer(workoutId))
    }
}
```

**SaveResult sealed class pattern** (`TemplateEditorViewModel.kt` lines 38-45):
```kotlin
sealed class SaveResult {
    data class Success(val templateId: Long) : SaveResult()
    data class Error(val message: String) : SaveResult()
}

private val _saveResult = MutableStateFlow<SaveResult?>(null)
@NativeCoroutinesState
val saveResult: StateFlow<SaveResult?> = _saveResult.asStateFlow()
```

**Apply to `WorkoutAiViewModel.kt`:**
- Constructor: `(useCase: WorkoutAiUseCase, secureKeyStore: SecureKeyStore, settingsRepository: SettingsRepository)`.
- State machine: a single `MutableStateFlow<WorkoutAiUiState>` with `WorkoutAiUiState` as a sealed class (`NoKey`, `Form(muscles, count, split, isGenerating)`, `Generating(skeletonRowCount)`, `Preview(templates)`, `Error(AiError)`, `Saved(templateIds)`). The `NoKey` branch is selected when `secureKeyStore.readApiKey() == null` (D-18-07).
- `fun generate()` launches in `viewModelScope`, sets state to `Generating`, calls `useCase(form)`, on Result.Failure maps to `Error(AiError)` (D-18-08), on Result.Success transitions to `Preview`.
- `fun cancel()` stores the in-flight `Job` and calls `.cancel()` (D-18-16); the canceled state is "user cancelled" (no toast).
- `fun save()` calls `useCase.commit(preview)` and emits `Saved(templateIds)` via a `SharedFlow<NavEvent>` mirroring the gallery's `_navEvents` so the screen navigates back.

**Apply to `RecipeAiViewModel.kt`:** same shape but state sealed class is `NoKey`, `RemainingExhausted`, `Form(dietStyle?)`, `Generating`, `Preview(recipe, fitsIndicator)`, `Error`, `Saved(recipeId)`.

---

### `AiSettingsViewModel.kt` (viewmodel, StateFlow + DataStore + Keychain writes)

**Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/presentation/settings/SettingsViewModel.kt`

**`stateIn` exposure pattern** (lines 17-21 of `SettingsViewModel.kt`):
```kotlin
@NativeCoroutinesState
val hasSeenTutorial: StateFlow<Boolean> = settingsRepository
    .hasSeenTutorial
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
```

**Setter + viewModelScope.launch pattern** (lines 22-26):
```kotlin
fun setHasSeenTutorial(value: Boolean) {
    viewModelScope.launch {
        settingsRepository.setHasSeenTutorial(value)
    }
}
```

**Apply to `AiSettingsViewModel.kt`:**
- Constructor: `(settingsRepository: SettingsRepository, secureKeyStore: SecureKeyStore)`.
- Expose `providerPreset: StateFlow<String>` (default `"openai"`), `baseUrl: StateFlow<String>` (default per preset), `model: StateFlow<String>` (default per preset), and `apiKeyConfigured: StateFlow<Boolean>` derived from `secureKeyStore.readApiKey() != null` (loaded once via a `viewModelScope.launch` in `init`; never expose the raw key via StateFlow).
- Setters mirror lines 22-26 of `SettingsViewModel.kt`: `fun setApiKey(value: String) { viewModelScope.launch { secureKeyStore.writeApiKey(value) } }`, `fun clearApiKey()`, `fun setProviderPreset(preset: String)` (resets baseUrl + model to preset defaults), etc.

---

### `SettingsRepository.kt` (extend — provider preset / base URL / model)

**Analog:** itself (the existing file is the canonical pattern).

**Existing key + Flow + setter trio** (lines 22-50):
```kotlin
private val weightUnitKey = stringPreferencesKey("weight_unit")

val weightUnit: Flow<WeightUnit> = dataStore.data.map { preferences ->
    when (preferences[weightUnitKey]) {
        "LBS" -> WeightUnit.LBS
        else -> WeightUnit.KG
    }
}

suspend fun setWeightUnit(unit: WeightUnit) {
    dataStore.edit { preferences ->
        preferences[weightUnitKey] = unit.name
    }
}
```

**Apply by adding the trio for each new field:**
```kotlin
private val aiProviderPresetKey = stringPreferencesKey("ai_provider_preset")
private val aiBaseUrlKey       = stringPreferencesKey("ai_base_url")
private val aiModelKey         = stringPreferencesKey("ai_model")

val aiProviderPreset: Flow<String> = dataStore.data.map { it[aiProviderPresetKey] ?: "openai" }
val aiBaseUrl: Flow<String>        = dataStore.data.map { it[aiBaseUrlKey] ?: "https://api.openai.com/v1" }
val aiModel: Flow<String>          = dataStore.data.map { it[aiModelKey] ?: "gpt-4o-mini" }

suspend fun setAiProviderPreset(preset: String) { dataStore.edit { it[aiProviderPresetKey] = preset } }
suspend fun setAiBaseUrl(url: String)            { dataStore.edit { it[aiBaseUrlKey] = url } }
suspend fun setAiModel(model: String)            { dataStore.edit { it[aiModelKey] = model } }
```
**The API key explicitly does NOT go in this file** (REQ-AI-06 / D-18-06 — Keychain / EncryptedSharedPreferences only).

---

### `AiModule.kt` (DI module)

**Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/di/ProgressGalleryModule.kt`

**Module shape pattern** (lines 41-79):
```kotlin
val progressGalleryModule = module {
    // DAO accessor
    single<ProgressPictureDao> { get<AppDatabase>().progressPictureDao() }
    single { NutritionGoalDayPolicy }

    // Repository
    single<ProgressPictureRepository> {
        ProgressPictureRepositoryImpl(get(), get())
    }

    // ViewModels
    viewModel {
        ProgressGalleryViewModel(
            repository = get(),
            gamificationDao = get(),
            ...
        )
    }
    viewModel { (workoutId: Long) ->
        ProgressViewerViewModel(workoutId = workoutId, ...)
    }
}
```

**SharedModule include pattern** (`SharedModule.kt` lines 60-66):
```kotlin
includes(
    gamificationModule,
    gamificationEngineModule,
    gamificationUiModule,
    achievementGalleryModule,
    progressGalleryModule
)
```

**Apply to `AiModule.kt`:**
```kotlin
val aiModule = module {
    single { AiPromptCatalog() }
    single { OpenAICompatibleClient(client = get(), secureKeyStore = get(), settingsRepository = get()) }
    single { WorkoutAiUseCase(get(), get(), get(), get(), Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
    single { RecipeAiUseCase(get(), get(), get(), get(), get(), get(), Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
    viewModel { WorkoutAiViewModel(get(), get(), get()) }
    viewModel { RecipeAiViewModel(get(), get(), get()) }
    viewModel { AiSettingsViewModel(get(), get()) }
}
```
Then add `aiModule` to the `includes(...)` block in `SharedModule.kt` line 60-66. Bind `SecureKeyStore` in `PlatformModule.{android,ios}.kt` (mirrors `BiometricGate` and `PhotoVault` bindings — `PlatformModule.android.kt` line 22, `PlatformModule.ios.kt` line 21).

---

### KoinHelpers — `WorkoutAiKoinHelper.kt` / `RecipeAiKoinHelper.kt` / `AiSettingsKoinHelper.kt`

**Analog:** `shared/src/iosMain/kotlin/com/pumpernickel/di/AchievementGalleryKoinHelper.kt` (the canonical 9-line shape).

**Full file (all 9 lines):**
```kotlin
package com.pumpernickel.di

import com.pumpernickel.presentation.gamification.AchievementGalleryViewModel
import org.koin.mp.KoinPlatform

class AchievementGalleryKoinHelper {
    fun getAchievementGalleryViewModel(): AchievementGalleryViewModel =
        KoinPlatform.getKoin().get()
}
```

**Apply identically:**
- `WorkoutAiKoinHelper.kt`: `class WorkoutAiKoinHelper { fun getWorkoutAiViewModel(): WorkoutAiViewModel = KoinPlatform.getKoin().get() }`.
- `RecipeAiKoinHelper.kt`: same shape returning `RecipeAiViewModel`.
- `AiSettingsKoinHelper.kt`: same shape returning `AiSettingsViewModel`.
- One helper per VM. **Class, not object. No params. No caching. One getter.** (Per `ProgressGalleryKoinHelper.kt` doc lines 13-14.)

---

### Room v9 → v10 migration (`AppDatabase.kt`)

**Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt` (the file being modified — its `AutoMigration(7,8)` and `AutoMigration(8,9)` are the precedents).

**Existing pattern** (lines 9-34):
```kotlin
@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutTemplateEntity::class,
        ...
        ProgressPictureEntity::class
    ],
    version = 9,
    autoMigrations = [
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9)
    ]
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() { ... }
```

**Apply diff:**
- Bump `version = 9` → `version = 10`.
- Append `AutoMigration(from = 9, to = 10)` to the `autoMigrations` array.
- The four entity files each get one new line: `val source: String? = null` appended to the `data class` constructor (additive only — Room AutoMigration handles nullable column adds without a `Spec`).

**Existing entity shape** (`WorkoutTemplateEntity.kt`, all 12 lines):
```kotlin
@Entity(tableName = "workout_templates")
data class WorkoutTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)
```

**Add to each of the 4 entities:**
```kotlin
val source: String? = null  // "USER" | "AI" — nullable; null treated as USER on read (D-18-11)
```

**Domain model propagation** — for each of `WorkoutTemplate.kt`, `Exercise.kt`, `Recipe.kt`, `Food.kt`:
- Add `val source: String? = null` to the data class.
- Update the `toDomain()` extension in the same file (e.g. `Exercise.kt` lines 23-50) to pass through `source = source`.

---

### `AiWorkoutGenScreen.kt` / `AiMealGenScreen.kt` (Compose screens)

**Analog:** `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ProgressGalleryScreen.kt`

**Composable signature + `koinViewModel()` + `collectAsState()` + `LaunchedEffect` nav pattern** (lines 73-89):
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressGalleryScreen(
    navController: NavHostController,
    viewModel: ProgressGalleryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navEvents.collect { event ->
            when (event) {
                is NavEvent.OpenViewer ->
                    navController.navigate(ProgressViewerRoute(workoutId = event.workoutId))
            }
        }
    }
```

**Loading + empty + content branching pattern** (lines 105-141):
```kotlin
if (uiState.isLoading) {
    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
} else if (uiState.tiles.isEmpty()) {
    /* empty state */
} else {
    /* content */
}
```

**Apply to AI screens:** the `when (uiState)` branch on the sealed `WorkoutAiUiState` selects between the `NoKey` empty-state card (with `Open AI Settings` button per D-18-07), the form, the skeleton (D-18-16 — render `count` shimmer rows for F6, fixed N for F8), the `Preview` (delegates to `AiPreviewSheet`), and the `Error` row (uses the per-class copy from `AiError`).

For the form muscle picker, **reuse** `AnatomyPickerSheet` from line 30 of `AnatomyPickerSheet.kt` (the file already supports `selectedGroup: String? + onConfirm: (String) -> Unit + onDismiss` shape; multi-select is a planning-time extension). For the count picker, mirror the drum-picker pattern from `WorkoutSessionScreen.kt` (`LazyColumn + SnapFlingBehavior` per Phase 13 decision in STATE.md).

---

### `AiSettingsScreen.kt` (Compose form)

**Analog:** `androidApp/.../ui/screens/SettingsSheet.kt` lines 48-95.

**Form structure + `viewModel.setX(value)` callback pattern** (lines 50-94):
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(...) {
    val viewModel: SettingsViewModel = koinViewModel()
    val weightUnit by viewModel.weightUnit.collectAsState()
    val appTheme by viewModel.appTheme.collectAsState()
    ...
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(20.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            themeOptions.forEachIndexed { index, key ->
                SegmentedButton(
                    selected = appTheme == key,
                    onClick = { viewModel.setAppTheme(key) },
                    ...
                )
            }
        }
    }
}
```

**Apply to `AiSettingsScreen.kt`:**
- Provider preset: `ExposedDropdownMenuBox` with five entries — `OpenAI`, `Together.AI`, `OpenRouter`, `Groq`, `Custom`. Selecting one calls `viewModel.setProviderPreset(...)` which resets baseUrl + model to defaults.
- API key: `OutlinedTextField(visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { show = !show }) { … } })`. A separate `Clear` `OutlinedButton` calls `viewModel.clearApiKey()`. Below the field: a small caption `if (apiKeyConfigured) "Saved" else "Not set"`.
- Base URL field: `OutlinedTextField`, hidden / disabled when preset != Custom (D-18-06).
- Model field: `OutlinedTextField`, pre-filled from preset, editable.

---

### `AiPreviewSheet.kt` (Compose ModalBottomSheet)

**Analog:** `androidApp/.../ui/screens/AnatomyPickerSheet.kt` lines 30-45.

**ModalBottomSheet skeleton** (lines 28-46):
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnatomyPickerSheet(
    selectedGroup: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var localSelection by remember { mutableStateOf(selectedGroup) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) { ... }
    }
}
```

**Apply to `AiPreviewSheet.kt`:**
- Two callers: F6 (`templates: List<WorkoutTemplate>`) and F8 (`recipe: Recipe`). Recommended: a single sheet with sealed `AiPreviewContent` (`Workout(List<WorkoutTemplate>)`, `Recipe(Recipe, fitsIndicator: MacrosFit)`).
- Workout content: `LazyColumn` of template summaries (name, exercise count, total sets); each gets a per-template `Save` button. A `Save all` button at the bottom commits the whole batch (D-18-12 multi-template).
- Recipe content: name, ingredient list, computed macros vs remaining (`fitsIndicator`).
- Save button calls `viewModel.save(...)`; `Discard` calls `viewModel.discardPreview()` and dismisses. **Until Save, no DB writes** (D-18-12).

---

### Routes + MainScreen (Android navigation)

**Analog:** `androidApp/.../ui/navigation/Routes.kt` and `MainScreen.kt`.

**Routes pattern** (`Routes.kt` lines 36-39):
```kotlin
// Overview tab — Progress gallery (Phase 17)
@Serializable data object ProgressGalleryRoute
@Serializable data class ProgressViewerRoute(val workoutId: Long)
```

**Add to `Routes.kt`:**
```kotlin
// Phase 18 — AI features
@Serializable data object AiSettingsRoute
@Serializable data object AiWorkoutGenRoute
@Serializable data object AiMealGenRoute
```

**MainScreen NavHost composable pattern** (`MainScreen.kt` lines 105-114 — the `composable<TemplateListRoute>` block is the canonical shape). Apply identically: register three new `composable<...>` entries — `AiSettingsRoute` and `AiWorkoutGenRoute` inside the Workout-tab NavHost, `AiMealGenRoute` inside the Nutrition-tab NavHost. Add an AI toolbar action button in the Workout tab's `TemplateListScreen.kt` and the Nutrition tab's `NutritionDailyLogScreen.kt` — placement is Claude's discretion (lean: TopAppBar action icon next to the existing add affordance).

---

### iOS — `AiSettingsView.swift`

**Analog:** `iosApp/iosApp/Views/Settings/SettingsView.swift` (entire 104-line file is the canonical Form+VM+observer shape).

**KoinHelper acquisition pattern** (line 6):
```swift
private let viewModel = KoinHelper.shared.getSettingsViewModel()
```

**Form + Picker + onChange pattern** (lines 14-68):
```swift
NavigationStack {
    Form {
        Section("Appearance") {
            Picker("Theme", selection: Binding(
                get: { theme.themeKey },
                set: { newValue in
                    theme.applyTheme(newValue)
                    viewModel.setAppTheme(theme: newValue)
                }
            )) { ... }
            .pickerStyle(.segmented)
        }
        ...
    }
    .navigationTitle("Settings")
}
```

**`asyncSequence` observer pattern** (lines 95-103):
```swift
.task {
    await observeWeightUnit()
}

private func observeWeightUnit() async {
    do {
        for try await value in asyncSequence(for: viewModel.weightUnitFlow) {
            self.weightUnit = value
        }
    } catch {
        print("Settings weight unit observation error: \(error)")
    }
}
```

**Apply to `AiSettingsView.swift`:** `private let viewModel = AiSettingsKoinHelper().getAiSettingsViewModel()`. Form sections: `Provider`, `API Key` (use `SecureField` for masking; "Show" toggle reveals via `if showKey { TextField } else { SecureField }`), `Base URL` (disabled when preset != Custom), `Model`. Observe `viewModel.providerPresetFlow`, `viewModel.baseUrlFlow`, `viewModel.modelFlow`, `viewModel.apiKeyConfiguredFlow` via `for try await` in a single `.task { await observe() }` (multiple flows can be observed concurrently with `withTaskGroup` per the existing convention in `AppRootView`).

---

### iOS — `AiWorkoutGenView.swift` / `AiMealGenView.swift` / `AiPreviewSheet.swift`

**Analog:** `iosApp/iosApp/Views/Overview/ProgressGalleryView.swift` (canonical "shared VM + StateFlow + per-tile content" shape).

**KoinHelper + `@State uiState: SharedX?` pattern** (lines 11-14):
```swift
private let viewModel = ProgressGalleryKoinHelper().getProgressGalleryViewModel()
@State private var uiState: SharedGalleryUiState?
```

**Body Group with three branches** (lines 22-52):
```swift
var body: some View {
    Group {
        if let state = uiState {
            if state.isLoading { ProgressView() }
            else if state.tiles.isEmpty { emptyState }
            else { ScrollView { LazyVGrid(...) } }
        } else {
            ProgressView()
        }
    }
    .navigationTitle("Fortschritt")
    .task { await observe() }
}

private func observe() async {
    do {
        for try await state in asyncSequence(for: viewModel.uiStateFlow) {
            self.uiState = state
        }
    } catch { print("error: \(error)") }
}
```

**Type alias pattern** (line 210):
```swift
private typealias SharedGalleryUiState = GalleryUiState
private typealias SharedProgressGalleryTile = ProgressGalleryTile
```

**Apply to AI views:**
- Each new view: `private let viewModel = WorkoutAiKoinHelper().getWorkoutAiViewModel()` (or recipe / settings).
- Switch on the sealed `WorkoutAiUiState` branches (each KMP sealed subclass exports as a Swift `is`-castable type — see Phase 15 STATE.md decisions for the `Shared.UnlockEvent` flat-export convention; planner verifies the same for `WorkoutAiUiState`).
- `NoKey` → empty-state with "Open AI Settings" `NavigationLink(destination: AiSettingsView())`.
- `Generating` → skeleton `ForEach(0..<count) { _ in shimmerRow() }` plus a `Cancel` button calling `viewModel.cancel()`.
- `Preview` → push `AiPreviewSheet` as `.sheet(isPresented:)`.
- `Error(error)` → switch on `AiError` cases, render the per-class copy + per-class action button (D-18-08).

---

### iOS — `MainTabView.swift` modification

**Analog:** itself (`iosApp/iosApp/Views/MainTabView.swift` lines 12-39).

**Existing TabView + NavigationStack tab pattern** (lines 13-21):
```swift
NavigationStack {
    TemplateListView()
}
.tabItem {
    Image(systemName: "dumbbell.fill")
    Text("Workout")
}
.tag(0)
```

**Apply diff:**
- Inside `TemplateListView` (Workout-tab root) and `NutritionDailyLogView` (Nutrition-tab root): add a `.toolbar { ToolbarItem(placement: .topBarTrailing) { Button(action: …) { Image(systemName: "sparkles") } } }` that pushes `AiWorkoutGenView()` / `AiMealGenView()` via `NavigationLink`. Symbol `sparkles` is the iOS convention for AI affordances.

---

### iOS — `SettingsView.swift` modification

**Analog:** itself (lines 70-77 of `SettingsView.swift` — the `Section("Gamification") { NavigationLink { AchievementGalleryView() } label: { Label("Achievements", systemImage: "trophy.fill") } }` block is the canonical "add a Settings row that pushes a sub-screen" pattern).

**Apply:**
```swift
Section("AI") {
    NavigationLink {
        AiSettingsView()
    } label: {
        Label("AI Settings", systemImage: "sparkles")
    }
}
```

---

### `18-IOS-HANDOFF.md` (phase artifact)

**Analog:** `.planning/phases/17-progress-pic-feature-with-biometric-locked-gallery-post-work/17-IOS-HANDOFF.md`

**Section structure to copy** (lines 1-30 + table at lines 16-22):
- **Header**: audience, scope, pbxproj note, KMP-vs-SwiftUI clarification.
- **"What you are building" table**: NEW vs MODIFY rows, file path, purpose.
- **"Kotlin contract" sections**: per VM, the Kotlin signature + Swift-side property names (`uiStateFlow`, `navEventsFlow`).
- **"Critical (T-…)" boxes**: gotchas worth flagging (e.g. for AI: never log the API key; don't keep it in a `@State` variable).
- **"Already shipped"** verifications (e.g. Info.plist additions if any).

For Phase 18, no new Info.plist entries are needed (no camera, no biometric, no permissions — HTTPS only). Flag this explicitly per analog line 24-26.

---

## Shared Patterns

### Authentication / API key

**Source:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.kt` (the `expect class` shape) + `iOS/Android` actuals using platform-secure storage idioms.

**Apply to:** `SecureKeyStore.kt` + `SecureKeyStore.{android,ios}.kt` + every Ktor request body in `OpenAICompatibleClient.kt` reading the key inline:
```kotlin
val key = secureKeyStore.readApiKey() ?: throw AiError.AuthOrQuota(401)
client.post(baseUrl + "/chat/completions") {
    header("Authorization", "Bearer $key")
    contentType(ContentType.Application.Json)
    setBody(chatRequest)
}
```
The key is **never** held as a class field; it is read just-in-time per request.

### Error handling

**Source:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/UnlockResult.kt` (sealed-class result type) + `BiometricGate.ios.kt` lines 116-129 (the `when (error.code.toLong())` fan-in mapping).

**Apply to:** `AiError.kt` (sealed class with five concrete branches per D-18-08) + `AiError.fromThrowable(...)` companion mapping Ktor exceptions. Every use case + VM `try { … } catch (e: Exception) { _state.value = … AiError.fromThrowable(e) }`.

For the VM-level error UI: each of the five `AiError` branches renders a distinct `Text(...)` + button per D-18-08:
- `Timeout` → "Generation took too long" + Retry
- `Network` → "No internet connection" + Retry
- `AuthOrQuota` → "Your API key is invalid or out of quota" + Open AI Settings
- `Provider` → "The AI provider had a problem" + Retry
- `SchemaInvalid` → "The AI returned something we couldn't use" + Retry (one auto-retry already happened internally per D-18-14)

### KMP shared VM + native UI

**Source:** Phase 17 convention — every shared VM in `presentation/` exports `@NativeCoroutinesState val uiState: StateFlow<...>`; iOS observes via `for try await … in asyncSequence(for: viewModel.uiStateFlow)`; Android observes via `viewModel.uiState.collectAsState()`.

**Apply to:** all three new VMs (`WorkoutAiViewModel`, `RecipeAiViewModel`, `AiSettingsViewModel`).

### `expect/actual` for platform I/O

**Source:** `PhotoVault.kt` + `BiometricGate.kt` + `HttpClientFactory.kt` + `Platform.kt` — the four canonical `expect/actual` shapes in this project.

**Apply to:** `SecureKeyStore.kt` (new) and the existing `HttpClientFactory.kt` (extend, don't fork, per D-18 / context-doc canonical-refs section).

### Validation (schema)

**Source:** `OpenFoodFactsApi.kt` lines 11-18 (`Json { ignoreUnknownKeys = true }` + `json.decodeFromString(responseText)`) — the entry point pattern. Plus the `init { require(...) }` invariants at `Recipe.kt` lines 16-19 and `Food.kt` lines 22-30.

**Apply to:** `OpenAICompatibleClient.kt` decode path. Then a second validation pass against `WorkoutAiSchema` / `RecipeAiSchema` invariants — on failure throw `AiError.SchemaInvalid(message)` which the use case converts to the retry-once fallback per D-18-14.

### One KoinHelper per VM (iOS)

**Source:** `AchievementGalleryKoinHelper.kt` (9 lines) — `class { fun getXxxViewModel(): XxxViewModel = KoinPlatform.getKoin().get() }`.

**Apply to:** all three new helpers, identical shape. **No object, no caching, no params.**

### German UI copy

**Source:** existing screens (e.g. `ProgressGalleryScreen.kt` line 93 `"Fortschritt"`, line 119 `"Noch keine Fortschrittsfotos. Schließe ein Workout ab und füge ein Foto hinzu."`).

**Apply to:** all UI copy in `AiSettingsScreen.kt`, `AiWorkoutGenScreen.kt`, `AiMealGenScreen.kt`, `AiPreviewSheet.kt`, and the Swift counterparts. Prompt files themselves stay in English (D-18-15) but instruct the model to emit user-visible strings in `{locale}` (default `de`).

### Domain-model `source` field propagation

**Source:** `WorkoutTemplate.kt` lines 25-33 (the `WorkoutTemplateEntity.toDomain(...)` extension function) — the canonical entity → domain mapper shape used across all four affected models.

**Apply to:** every `toDomain()` extension for `WorkoutTemplateEntity`, `ExerciseEntity`, `RecipeEntity`, `FoodEntity` — pass `source = source` through. Repositories that materialize these models (`TemplateRepositoryImpl`, `ExerciseRepositoryImpl`, `FoodRepositoryImpl`) need no changes; they already call `.toDomain(...)`.

---

## No Analog Found

| File | Role | Data Flow | Reason | Planner action |
|------|------|-----------|--------|----------------|
| `ai-prompts/workout-system.md` + `ai-prompts/recipe-system.md` | static prompt templates | text | No prior `.md` resource files exist (only `free_exercise_db.json`). | Use research-backed OpenAI prompt patterns; reference `Exercise.kt` / `Food.kt` / `MuscleGroup.kt` field shapes for the schema documentation embedded in the prompt body. |
| `response_format: json_schema` request shape | wire format | n/a | First structured-output integration in the project. | Verify against OpenAI's [structured outputs docs](https://platform.openai.com/docs/guides/structured-outputs) at planning time; mirror the JSON shape exactly in `AiChatDto.kt`. |
| Provider-preset model defaults (`gpt-4o-mini`, `meta-llama/Llama-3.3-70B-Instruct-Turbo`, `meta-llama/llama-3.3-70b-instruct`, etc.) | data | n/a | No prior provider config in project. | Verify per-provider docs at planning time per D-18-06; defaults are Claude's discretion. |

## Metadata

**Analog search scope:** `shared/src/{common,android,ios}Main/`, `androidApp/src/androidMain/`, `iosApp/iosApp/Views/`, `.planning/phases/17-…/`, `.planning/phases/15.1-…/`.
**Files scanned:** ~60 (data/api, domain/progresspic, presentation/*, di, db, Android screens, iOS views, phase handoff docs).
**Pattern extraction date:** 2026-05-07.

---

## PATTERN MAPPING COMPLETE

**Phase:** 18 - AI Features — F6 Workout Generation + F8 Meal Generation (BYOK, OpenAI-compatible HTTPS)
**Files classified:** 28 new + 6 modified
**Analogs found:** 28 / 28

### Coverage
- Files with exact analog: 21 (Ktor client / DTOs / DI / VMs / KoinHelpers / Compose screens / SwiftUI / Routes / entities / migration)
- Files with role-match analog: 7 (use cases use `LookupBarcodeUseCase` shape; Compose `AiSettingsScreen` blends `SettingsSheet` + `NutritionGoalsEditorScreen`; SwiftUI `AiPreviewSheet` borrows from `ProgressGalleryView`)
- Files with no analog: 0 — even the prompt `.md` files have a resource-loading precedent (`free_exercise_db.json` via `readResourceFile`).

### Key Patterns Identified
- **HTTP client extension, not fork**: `HttpClientFactory` already exposes a single `expect fun createHttpClient(): HttpClient` — extend by installing `ContentNegotiation` + `HttpTimeout` plugins; the AI client takes the same singleton (`SharedModule.kt` line 92).
- **`expect class SecureKeyStore` mirrors `expect class PhotoVault`**: the iOS `actual` is no-arg + Foundation/Security cinterop; Android `actual` takes `Context` + `EncryptedSharedPreferences`.
- **One feature module per vertical**: `aiModule` joins `gamificationModule`, `progressGalleryModule`, etc. in `SharedModule.kt`'s `includes(...)` block. One Koin Helper per VM in iOS DI (3 new helpers).
- **Sealed-class state machine**: VM `uiState: StateFlow<XxxUiState>` where `XxxUiState` is a sealed class — directly reuses `UnlockResult.kt` / `EvaluateOutcome` shape.
- **Resources via `readResourceFile` expect/actual**: prompt `.md` files reuse the existing path that loads `free_exercise_db.json`; no new platform code.
- **Room v9 → v10 additive AutoMigration**: 4 entities each gain a nullable `source: String?`; no `Spec`, no migration class. Precedent is `AutoMigration(8, 9)` in `AppDatabase.kt` line 32.
- **iOS handoff doc**: structure mirrors `17-IOS-HANDOFF.md` exactly — table of NEW vs MODIFY surfaces, Kotlin contract per VM, German UI copy, no Info.plist additions for this phase (no permissions needed).

### File Created
`/Users/olli/Studium/semester_6/mobile_app_dev/PumpernickelApp/.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-PATTERNS.md`

### Ready for Planning
Pattern mapping complete. Planner can now reference per-file analog patterns and concrete excerpts in PLAN.md files for Phase 18.
