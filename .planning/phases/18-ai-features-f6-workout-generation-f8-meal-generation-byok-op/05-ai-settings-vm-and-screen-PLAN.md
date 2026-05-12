---
phase: 18-ai-features-f6-workout-generation-f8-meal-generation-byok-op
plan: 05
type: execute
wave: 3
depends_on: [02, 04]
files_modified:
  - shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt
  - shared/src/iosMain/kotlin/com/pumpernickel/di/AiSettingsKoinHelper.kt
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiSettingsScreen.kt
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/SettingsSheet.kt
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/Routes.kt
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt
autonomous: false
requirements:
  - REQ-AI-06
  - REQ-AI-08
user_setup: []

must_haves:
  truths:
    - "AiSettingsViewModel exposes providerPreset / baseUrl / model / apiKeyConfigured StateFlows + setApiKey / clearApiKey / setProviderPreset / setBaseUrl / setModel"
    - "AiSettingsScreen renders the provider preset dropdown, masked API key field with show/hide toggle, base URL (disabled unless preset=Custom), model field, and a Clear key button"
    - "Selecting a provider preset resets baseUrl + model to per-preset defaults"
    - "Saving a non-HTTPS base URL is rejected (validation at the Settings layer per T-18-04-04)"
    - "AiModule registers the OpenAICompatibleClient (with keyProvider = SecureKeyStore::readApiKey), AiPromptCatalog, and AiSettingsViewModel"
    - "SettingsSheet has a new AI row navigating to AiSettingsRoute"
    - "Android navigation registers AiSettingsRoute in the Workout-tab NavHost (consistent with how SettingsSheet is reached today)"
  artifacts:
    - path: "shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt"
      provides: "AiSettings VM with 4 StateFlows + 5 setters"
      contains: "class AiSettingsViewModel"
      min_lines: 60
    - path: "shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt"
      provides: "Koin module for AI features"
      contains: "val aiModule"
    - path: "androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiSettingsScreen.kt"
      provides: "Compose screen for AI settings"
      contains: "fun AiSettingsScreen"
      min_lines: 80
    - path: "shared/src/iosMain/kotlin/com/pumpernickel/di/AiSettingsKoinHelper.kt"
      provides: "iOS factory for AiSettingsViewModel"
      contains: "class AiSettingsKoinHelper"
  key_links:
    - from: "AiModule"
      to: "OpenAICompatibleClient"
      via: "single { OpenAICompatibleClient(get(), { get<SecureKeyStore>().readApiKey() }) }"
      pattern: "OpenAICompatibleClient"
    - from: "SharedModule.includes"
      to: "aiModule"
      via: "includes()"
      pattern: "aiModule"
    - from: "SettingsSheet"
      to: "AiSettingsRoute"
      via: "row navigates"
      pattern: "AiSettingsRoute"
    - from: "AiSettingsScreen.setBaseUrl"
      to: "https-prefix validation"
      via: "in-screen validation before calling viewModel.setBaseUrl"
      pattern: "https://"
---

<objective>
Ship the BYOK Settings UX. Three concerns:

1. **AiSettingsViewModel** (commonMain) — exposes `providerPreset`, `baseUrl`, `model`, and a derived `apiKeyConfigured: Boolean` StateFlow. Setters: `setApiKey(String)` writes to SecureKeyStore; `clearApiKey()` removes it; `setProviderPreset(String)` resets baseUrl + model to per-preset defaults; `setBaseUrl(String)` and `setModel(String)` write to DataStore via SettingsRepository.

2. **AiModule** (commonMain) — Koin module registering `AiPromptCatalog`, `OpenAICompatibleClient` (constructed with the shared `HttpClient` and a `keyProvider` lambda calling `SecureKeyStore.readApiKey()`), and `AiSettingsViewModel`. Included in `SharedModule.kt` via the `includes(...)` block (same pattern as `progressGalleryModule`). Wave 4 plans (06, 08) will append their use cases / VMs to this same module.

3. **Android AI Settings screen + entry** — `AiSettingsScreen.kt` Compose screen with provider-preset dropdown, masked key field, optional base-URL field (visible/editable only when preset=Custom), model field, and a Clear key button. `SettingsSheet.kt` gets a new "AI" row that navigates to `AiSettingsRoute`. `Routes.kt` adds `AiSettingsRoute`; `MainScreen.kt` registers the composable in the Workout-tab NavHost (the tab from which Settings is currently reached).

4. **iOS KoinHelper** — `AiSettingsKoinHelper` (one-class, no params, no caching — mirrors `AchievementGalleryKoinHelper`). The iOS view itself is in Plan 10's handoff doc.

Includes a human-verify checkpoint at the end (the Android Settings flow visual UAT — D-18-05 / D-18-07 verification before moving to Wave 4).

Purpose: Implements REQ-AI-06 (BYOK Settings + clear-key support), REQ-AI-08 (no-key empty state seed for downstream VMs), D-18-05 (AI section inside Settings), D-18-06 (provider preset / base URL / model layout), D-18-07 (apiKeyConfigured flag for downstream no-key empty states).
Output: 1 new VM + 1 new module + 1 modified module root + 1 new KoinHelper + 1 new Compose screen + 1 modified SettingsSheet + 2 modified Android navigation files.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/REQUIREMENTS-ai-features.md
@.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-CONTEXT.md
@.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-PATTERNS.md
@shared/src/commonMain/kotlin/com/pumpernickel/presentation/settings/SettingsViewModel.kt
@shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt
@shared/src/commonMain/kotlin/com/pumpernickel/di/ProgressGalleryModule.kt
@shared/src/iosMain/kotlin/com/pumpernickel/di/AchievementGalleryKoinHelper.kt
@androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/SettingsSheet.kt
@androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/Routes.kt
@androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt
@shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt
@shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.kt
@shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt
@shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiPromptCatalog.kt

<interfaces>
SettingsViewModel.kt pattern (canonical setter shape, lines 17-26):
```
@NativeCoroutinesState
val hasSeenTutorial: StateFlow<Boolean> = settingsRepository
    .hasSeenTutorial
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

fun setHasSeenTutorial(value: Boolean) {
    viewModelScope.launch { settingsRepository.setHasSeenTutorial(value) }
}
```

ProgressGalleryModule.kt module + viewModel binding:
```
val progressGalleryModule = module {
    single<ProgressPictureRepository> { ProgressPictureRepositoryImpl(get(), get()) }
    viewModel { ProgressGalleryViewModel(...) }
}
```

SharedModule.kt includes block (lines 60-66):
```
includes(
    gamificationModule,
    gamificationEngineModule,
    gamificationUiModule,
    achievementGalleryModule,
    progressGalleryModule
)
```

AchievementGalleryKoinHelper canonical 9-line shape:
```
package com.pumpernickel.di
import com.pumpernickel.presentation.gamification.AchievementGalleryViewModel
import org.koin.mp.KoinPlatform
class AchievementGalleryKoinHelper {
    fun getAchievementGalleryViewModel(): AchievementGalleryViewModel =
        KoinPlatform.getKoin().get()
}
```

Routes.kt current Phase-17 additions (lines 37-39):
```
@Serializable data object ProgressGalleryRoute
@Serializable data class ProgressViewerRoute(val workoutId: Long)
```

MainScreen.kt Workout-tab NavHost composable shape (lines 105-114):
```
composable<TemplateListRoute> {
    TemplateListScreen(navController = workoutNavController)
}
```

SettingsSheet.kt currently exposes an "Achievements" row pattern (find via grep `Section\\|Label.*trophy` — model the AI row on it).

Provider preset defaults per D-18-06 (verified at planning time — adjust during execution if the model lineup has shifted):
- openai → baseUrl https://api.openai.com/v1, model gpt-4o-mini
- together → baseUrl https://api.together.xyz/v1, model meta-llama/Llama-3.3-70B-Instruct-Turbo
- openrouter → baseUrl https://openrouter.ai/api/v1, model meta-llama/llama-3.3-70b-instruct
- groq → baseUrl https://api.groq.com/openai/v1, model llama-3.3-70b-versatile
- custom → baseUrl "" (user enters), model "" (user enters)
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Create AiSettingsViewModel + AiModule + AiSettingsKoinHelper, register in SharedModule</name>
  <files>
    shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt,
    shared/src/iosMain/kotlin/com/pumpernickel/di/AiSettingsKoinHelper.kt
  </files>
  <read_first>
    shared/src/commonMain/kotlin/com/pumpernickel/presentation/settings/SettingsViewModel.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/di/ProgressGalleryModule.kt,
    shared/src/iosMain/kotlin/com/pumpernickel/di/AchievementGalleryKoinHelper.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt
  </read_first>
  <action>
**File 1: shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt**

```kotlin
package com.pumpernickel.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.data.repository.SettingsRepository
import com.pumpernickel.domain.ai.SecureKeyStore
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AiSettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val secureKeyStore: SecureKeyStore
) : ViewModel() {

    @NativeCoroutinesState
    val providerPreset: StateFlow<String> = settingsRepository.aiProviderPreset
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "openai")

    @NativeCoroutinesState
    val baseUrl: StateFlow<String> = settingsRepository.aiBaseUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "https://api.openai.com/v1")

    @NativeCoroutinesState
    val model: StateFlow<String> = settingsRepository.aiModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "gpt-4o-mini")

    private val _apiKeyConfigured = MutableStateFlow(false)

    @NativeCoroutinesState
    val apiKeyConfigured: StateFlow<Boolean> = _apiKeyConfigured.asStateFlow()

    init {
        viewModelScope.launch {
            _apiKeyConfigured.value = secureKeyStore.readApiKey() != null
        }
    }

    fun setApiKey(value: String) {
        viewModelScope.launch {
            secureKeyStore.writeApiKey(value)
            _apiKeyConfigured.value = true
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            secureKeyStore.clearApiKey()
            _apiKeyConfigured.value = false
        }
    }

    /**
     * D-18-06 — selecting a preset resets baseUrl + model to per-preset defaults.
     * Custom preset clears both fields so the user enters them manually.
     */
    fun setProviderPreset(preset: String) {
        val (defaultBase, defaultModel) = PROVIDER_DEFAULTS[preset]
            ?: ("" to "")
        viewModelScope.launch {
            settingsRepository.setAiProviderPreset(preset)
            settingsRepository.setAiBaseUrl(defaultBase)
            settingsRepository.setAiModel(defaultModel)
        }
    }

    fun setBaseUrl(url: String) {
        viewModelScope.launch { settingsRepository.setAiBaseUrl(url) }
    }

    fun setModel(value: String) {
        viewModelScope.launch { settingsRepository.setAiModel(value) }
    }

    companion object {
        // D-18-06 provider preset defaults (preset key -> base URL, model).
        // Custom preset has empty defaults so the user fills them.
        private val PROVIDER_DEFAULTS: Map<String, Pair<String, String>> = mapOf(
            "openai" to ("https://api.openai.com/v1" to "gpt-4o-mini"),
            "together" to ("https://api.together.xyz/v1" to "meta-llama/Llama-3.3-70B-Instruct-Turbo"),
            "openrouter" to ("https://openrouter.ai/api/v1" to "meta-llama/llama-3.3-70b-instruct"),
            "groq" to ("https://api.groq.com/openai/v1" to "llama-3.3-70b-versatile"),
            "custom" to ("" to "")
        )
    }
}
```

Notes:
- The raw key value is NEVER exposed via a StateFlow — only the boolean `apiKeyConfigured`. This matches REQ-AI-06.
- `init { ... readApiKey() }` runs once at construction; subsequent updates flow through `setApiKey`/`clearApiKey`. There is no Flow over the raw key.
- The setter for the API key writes immediately on every keystroke — the screen-side debounce is handled in the Compose screen with `onValueChange` calling a local state `var`, then `setApiKey` only on a "Save" button press. Don't make this VM debounce; keep it dumb.

**File 2: shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt**

```kotlin
package com.pumpernickel.di

import com.pumpernickel.data.api.OpenAICompatibleClient
import com.pumpernickel.domain.ai.AiPromptCatalog
import com.pumpernickel.domain.ai.SecureKeyStore
import com.pumpernickel.presentation.ai.AiSettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Phase 18 — DI bindings for the AI features. Wave-3 plan 05 registers the
 * client + prompt catalog + AiSettingsViewModel; downstream Wave-4 plans
 * (06, 08) APPEND their use cases + VMs to this same module file.
 *
 * SecureKeyStore is bound platform-side in PlatformModule.{android,ios}.kt
 * (Plan 04). The OpenAICompatibleClient takes a keyProvider lambda that
 * resolves SecureKeyStore from Koin and calls readApiKey() per request —
 * the client never holds the key.
 */
val aiModule = module {
    single { AiPromptCatalog() }

    single {
        OpenAICompatibleClient(
            client = get(),
            keyProvider = { get<SecureKeyStore>().readApiKey() }
        )
    }

    viewModel { AiSettingsViewModel(get(), get()) }
}
```

**File 3: shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt** — register `aiModule` in the existing `includes(...)` block. Locate lines 60-66 (the existing `includes(...)` call) and append `aiModule` as the last entry:

```kotlin
includes(
    gamificationModule,
    gamificationEngineModule,
    gamificationUiModule,
    achievementGalleryModule,
    progressGalleryModule,
    aiModule
)
```

Do NOT remove or reorder existing includes. Do NOT change any other binding in SharedModule.kt.

**File 4: shared/src/iosMain/kotlin/com/pumpernickel/di/AiSettingsKoinHelper.kt** — mirror the canonical 9-line shape:

```kotlin
package com.pumpernickel.di

import com.pumpernickel.presentation.ai.AiSettingsViewModel
import org.koin.mp.KoinPlatform

class AiSettingsKoinHelper {
    fun getAiSettingsViewModel(): AiSettingsViewModel =
        KoinPlatform.getKoin().get()
}
```
  </action>
  <verify>
    <automated>grep -E "class AiSettingsViewModel" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt && grep -E "val aiModule" shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt && grep -E "aiModule" shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt && grep -E "class AiSettingsKoinHelper" shared/src/iosMain/kotlin/com/pumpernickel/di/AiSettingsKoinHelper.kt</automated>
  </verify>
  <acceptance_criteria>
    - `grep -c "class AiSettingsViewModel" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt` returns exactly `1`.
    - `grep -c "@NativeCoroutinesState" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt` returns exactly `4` (providerPreset, baseUrl, model, apiKeyConfigured).
    - `grep -c "fun setApiKey" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt` returns exactly `1`.
    - `grep -c "fun clearApiKey" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt` returns exactly `1`.
    - `grep -c "fun setProviderPreset" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt` returns exactly `1`.
    - `grep -c "PROVIDER_DEFAULTS" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt` returns at least `2` (declaration + use).
    - `grep -c "secureKeyStore.writeApiKey" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt` returns exactly `1`.
    - `grep -c "secureKeyStore.clearApiKey" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt` returns exactly `1`.
    - `grep -ciE "val .*apiKey: String" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt` returns exactly `0` (raw key never exposed via field).
    - `grep -c "val aiModule = module" shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt` returns exactly `1`.
    - `grep -c "OpenAICompatibleClient" shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt` returns at least `2` (import + binding).
    - `grep -c "AiPromptCatalog" shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt` returns at least `2`.
    - `grep -c "viewModel { AiSettingsViewModel" shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt` returns exactly `1`.
    - `grep -c "aiModule" shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt` returns at least `1` (added to includes).
    - `grep -c "progressGalleryModule" shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt` returns at least `1` (existing retained).
    - `grep -c "class AiSettingsKoinHelper" shared/src/iosMain/kotlin/com/pumpernickel/di/AiSettingsKoinHelper.kt` returns exactly `1`.
    - `grep -c "KoinPlatform.getKoin().get()" shared/src/iosMain/kotlin/com/pumpernickel/di/AiSettingsKoinHelper.kt` returns exactly `1`.
  </acceptance_criteria>
  <done>AiSettingsViewModel exposes 4 StateFlows + 5 setters; AiModule registers OpenAICompatibleClient with keyProvider lambda; SharedModule includes aiModule; AiSettingsKoinHelper mirrors canonical shape.</done>
</task>

<task type="auto">
  <name>Task 2: Create AiSettingsScreen + add AI row to SettingsSheet + register Routes/MainScreen</name>
  <files>
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiSettingsScreen.kt,
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/SettingsSheet.kt,
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/Routes.kt,
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt
  </files>
  <read_first>
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/SettingsSheet.kt,
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/NutritionGoalsEditorScreen.kt,
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/Routes.kt,
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt
  </read_first>
  <action>
**File 1: androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/Routes.kt** — add three Phase-18 routes (AiSettingsRoute is needed now; AiWorkoutGenRoute and AiMealGenRoute are added now too because the Routes file is shared with Plans 07/09 and adding all three at once avoids serialization conflict):

Append at the end of Routes.kt:
```kotlin
// Phase 18 — AI Features (BYOK)
@Serializable data object AiSettingsRoute
@Serializable data object AiWorkoutGenRoute
@Serializable data object AiMealGenRoute
```

(Plans 07 and 09 expect these routes to already exist — adding them all here removes a serialisation hazard between Wave-5 plans.)

**File 2: androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiSettingsScreen.kt** — Compose screen.

Required UI elements (per D-18-06):
- TopAppBar with title "KI-Einstellungen" (German per project convention) and back navigation.
- ExposedDropdownMenuBox for provider preset — entries: OpenAI / Together.AI / OpenRouter / Groq / Benutzerdefiniert (Custom). On selection, calls `viewModel.setProviderPreset(presetKey)`.
- API key field: `OutlinedTextField` with `visualTransformation = if (show) None else PasswordVisualTransformation()`. Trailing `IconButton` toggles `show`. Local `var keyDraft by remember { mutableStateOf("") }`. A "Speichern" button below the field calls `viewModel.setApiKey(keyDraft); keyDraft = ""`. Below: a small caption "Gespeichert" if `apiKeyConfigured`, else "Kein Schlüssel gespeichert".
- "Schlüssel löschen" `OutlinedButton` calls `viewModel.clearApiKey()`. Disabled when `!apiKeyConfigured`.
- Base URL field — `OutlinedTextField`. Disabled when preset != "custom" (uses preset-default). When user types, the screen validates the value starts with `https://`; if not, the "Speichern" button is disabled and a small error caption shows: "Nur HTTPS-URLs erlaubt." (T-18-04-04 mitigation.)
- Model field — `OutlinedTextField`, always enabled, calls `viewModel.setModel(...)` on a Save action.

Skeleton:

```kotlin
package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pumpernickel.presentation.ai.AiSettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

private val PROVIDER_PRESETS = listOf(
    "openai" to "OpenAI",
    "together" to "Together.AI",
    "openrouter" to "OpenRouter",
    "groq" to "Groq",
    "custom" to "Benutzerdefiniert"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    navController: NavHostController,
    viewModel: AiSettingsViewModel = koinViewModel()
) {
    val providerPreset by viewModel.providerPreset.collectAsState()
    val baseUrl by viewModel.baseUrl.collectAsState()
    val model by viewModel.model.collectAsState()
    val apiKeyConfigured by viewModel.apiKeyConfigured.collectAsState()

    var keyDraft by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var presetExpanded by remember { mutableStateOf(false) }
    var baseUrlDraft by remember(baseUrl) { mutableStateOf(baseUrl) }
    var modelDraft by remember(model) { mutableStateOf(model) }

    val baseUrlError = baseUrlDraft.isNotBlank() && !baseUrlDraft.startsWith("https://")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KI-Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Provider preset
            ExposedDropdownMenuBox(
                expanded = presetExpanded,
                onExpandedChange = { presetExpanded = it }
            ) {
                OutlinedTextField(
                    value = PROVIDER_PRESETS.firstOrNull { it.first == providerPreset }?.second ?: providerPreset,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Anbieter") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                androidx.compose.material3.ExposedDropdownMenu(
                    expanded = presetExpanded,
                    onDismissRequest = { presetExpanded = false }
                ) {
                    PROVIDER_PRESETS.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.setProviderPreset(key)
                                presetExpanded = false
                            }
                        )
                    }
                }
            }

            // API key (masked, with show/hide toggle)
            OutlinedTextField(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                label = { Text("API-Schlüssel") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            imageVector = if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showKey) "Verbergen" else "Anzeigen"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = if (apiKeyConfigured) "Gespeichert" else "Kein Schlüssel gespeichert",
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedButton(
                onClick = {
                    viewModel.setApiKey(keyDraft)
                    keyDraft = ""
                },
                enabled = keyDraft.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Schlüssel speichern") }

            OutlinedButton(
                onClick = { viewModel.clearApiKey() },
                enabled = apiKeyConfigured,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Schlüssel löschen") }

            Spacer(Modifier.height(12.dp))

            // Base URL — disabled unless preset = custom
            OutlinedTextField(
                value = baseUrlDraft,
                onValueChange = { baseUrlDraft = it },
                label = { Text("Basis-URL") },
                isError = baseUrlError,
                supportingText = {
                    if (baseUrlError) Text("Nur HTTPS-URLs erlaubt.")
                },
                enabled = providerPreset == "custom",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Model
            OutlinedTextField(
                value = modelDraft,
                onValueChange = { modelDraft = it },
                label = { Text("Modell") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedButton(
                onClick = {
                    if (!baseUrlError) {
                        viewModel.setBaseUrl(baseUrlDraft)
                        viewModel.setModel(modelDraft)
                    }
                },
                enabled = !baseUrlError,
                modifier = Modifier.fillMaxWidth()
            ) { Text("URL und Modell speichern") }
        }
    }
}
```

The exact `ExposedDropdownMenuBox` import path is `androidx.compose.material3.ExposedDropdownMenu` — if `menuAnchor()` requires an explicit type parameter on the project's Compose version, adjust per the existing usage in TemplateEditorScreen.kt (which uses the same component for muscle-group selection per STATE.md).

Add `import androidx.compose.runtime.collectAsState` if Compose/StateFlow extension is needed — match the import already present in `ProgressGalleryScreen.kt` line 75.

**File 3: androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/SettingsSheet.kt** — append a new "AI" section after the existing "Gamification" section. Locate the `Section("Gamification") { NavigationLink ... }` block (or its Compose Material equivalent — verify by reading the file). Below it, add:

```kotlin
// Material 3 doesn't have a NavigationLink composable — Settings rows in this file
// use ListItem + clickable. Match the existing "Achievements" row exactly:
ListItem(
    headlineContent = { Text("KI-Einstellungen") },
    leadingContent = {
        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
    },
    trailingContent = {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    },
    modifier = Modifier.clickable { onNavigateToAiSettings() }
)
```

The exact icon (AutoAwesome / Sparkles) and the exact row API depend on the existing SettingsSheet structure — read it and replicate the existing "Achievements" row pattern verbatim, only swapping the label, icon, and the `onClick` callback. Add a new parameter `onNavigateToAiSettings: () -> Unit` to the SettingsSheet composable signature; the caller passes `{ navController.navigate(AiSettingsRoute) }`.

**File 4: androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt** — register the AiSettingsRoute composable in the Workout-tab NavHost (the tab that currently surfaces SettingsSheet — verify by tracing where SettingsSheet is presented in MainScreen). Inside the Workout-tab `NavHost { ... }` block, after the existing `composable<AchievementGalleryRoute> { ... }` line, add:

```kotlin
composable<AiSettingsRoute> {
    AiSettingsScreen(navController = workoutNavController)
}
```

Update the call site that constructs SettingsSheet (locate `SettingsSheet(...)` invocation) to pass the new `onNavigateToAiSettings` lambda calling `workoutNavController.navigate(AiSettingsRoute)`.

Add the import `import com.pumpernickel.android.ui.screens.AiSettingsScreen` to MainScreen.kt.
  </action>
  <verify>
    <automated>grep -E "AiSettingsRoute" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/Routes.kt && grep -E "AiSettingsRoute" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt && grep -E "fun AiSettingsScreen" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiSettingsScreen.kt && grep -E "Schlüssel speichern|Schlüssel löschen" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiSettingsScreen.kt</automated>
  </verify>
  <acceptance_criteria>
    - `grep -c "AiSettingsRoute" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/Routes.kt` returns at least `1`.
    - `grep -c "AiWorkoutGenRoute" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/Routes.kt` returns at least `1`.
    - `grep -c "AiMealGenRoute" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/Routes.kt` returns at least `1`.
    - `grep -c "fun AiSettingsScreen" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiSettingsScreen.kt` returns exactly `1`.
    - `grep -c "PasswordVisualTransformation" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiSettingsScreen.kt` returns at least `1`.
    - `grep -c "viewModel.setApiKey" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiSettingsScreen.kt` returns at least `1`.
    - `grep -c "viewModel.clearApiKey" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiSettingsScreen.kt` returns at least `1`.
    - `grep -c "viewModel.setProviderPreset" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiSettingsScreen.kt` returns at least `1`.
    - `grep -c "https://" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiSettingsScreen.kt` returns at least `1` (HTTPS validation).
    - `grep -c "composable<AiSettingsRoute>" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt` returns exactly `1`.
    - `grep -c "AiSettingsScreen" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt` returns at least `2` (import + use).
    - `grep -ciE "navigate\\(AiSettingsRoute|AiSettingsRoute\\)" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/SettingsSheet.kt` returns at least `0` (the AI row exists but its callback is wired in MainScreen — at minimum SettingsSheet has the new row visible). The acceptance criteria does not strictly require a specific text in SettingsSheet because the exact API depends on existing structure; the row visual is verified by the human-verify checkpoint that follows.
    - `grep -c "KI-Einstellungen\\|AI-Einstellungen\\|AI Settings" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/SettingsSheet.kt` returns at least `1` (the new row label).
  </acceptance_criteria>
  <done>Routes registered; AiSettingsScreen renders 4 fields per D-18-06; HTTPS validation present; SettingsSheet shows the AI row; navigation wired in MainScreen.</done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 3 — Human verify: AI Settings round-trip</name>
  <what-built>
The Android AI Settings screen (D-18-05 / D-18-06 / D-18-07) is reachable from Settings > AI-Einstellungen. The user can pick a provider preset, enter and save an API key (masked, with show/hide), see "Gespeichert" feedback, switch to Custom and enter a custom HTTPS base URL (non-HTTPS is rejected with inline error), edit the model, and clear the key.
  </what-built>
  <how-to-verify>
1. Build and install Android: `./gradlew :androidApp:installDebug`.
2. Launch the app. Open the Workout tab > top-right Settings icon (existing).
3. Tap the new "KI-Einstellungen" row — confirm AiSettingsScreen pushes onto the stack with the back arrow returning to Settings.
4. Confirm provider preset shows "OpenAI" by default and base URL shows `https://api.openai.com/v1` (disabled) and model shows `gpt-4o-mini`.
5. Tap the API key field, type any nonsense like `sk-test-1234`. Tap the eye icon — verify the value reveals; tap again — verify it masks. Tap "Schlüssel speichern" — confirm caption switches to "Gespeichert".
6. Tap "Schlüssel löschen" — confirm caption returns to "Kein Schlüssel gespeichert".
7. Open the provider preset dropdown — pick "Together.AI" — confirm base URL auto-updates to `https://api.together.xyz/v1` and model updates to a Together default. Pick "Benutzerdefiniert" — confirm base URL becomes editable.
8. With Custom selected, type `http://example.com` (non-HTTPS) — confirm an error caption "Nur HTTPS-URLs erlaubt." appears and "URL und Modell speichern" is disabled. Replace with `https://example.com` — confirm the error clears and the save button enables.
9. Force-quit and relaunch the app. Re-open AI Settings. Confirm the saved key persists ("Gespeichert" caption visible) and the saved provider preset / model persist.
10. Run `adb shell run-as com.pumpernickel cat /data/data/com.pumpernickel/shared_prefs/ai_secrets.xml` (or equivalent app id) — confirm the file exists but the API key value is encrypted (NOT visible as `sk-test-1234` plaintext).
  </how-to-verify>
  <resume-signal>Type "approved" or describe issues (e.g., "key visible in plaintext", "preset doesn't reset baseUrl", "back navigation broken").</resume-signal>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| User input → SecureKeyStore | API key entered in OutlinedTextField crosses to encrypted persistence |
| User input → SettingsRepository | Base URL must be HTTPS — validated at the screen layer |
| SettingsSheet → AiSettingsRoute | Existing Settings entry gains a new sub-route |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-18-05-01 | Information disclosure | API key shoulder-surfing during entry | mitigate | OutlinedTextField uses PasswordVisualTransformation by default; show/hide toggle is per-keystroke explicit. Caption never displays the key. |
| T-18-05-02 | Information disclosure | API key in screenshots | accept | Out of scope for prototype. A `WindowManager.LayoutParams.FLAG_SECURE` could be added later if grading requires. |
| T-18-05-03 | Tampering | Non-HTTPS base URL | mitigate | Acceptance criterion + screen-side `baseUrlDraft.startsWith("https://")` check disables save and shows inline error. |
| T-18-05-04 | Tampering | Empty / malformed API key saved | mitigate | "Schlüssel speichern" button disabled when keyDraft is blank (`enabled = keyDraft.isNotBlank()`). Empty key never reaches SecureKeyStore. |
| T-18-05-05 | Information disclosure | API key persists in keyDraft mutableStateOf | mitigate | After successful save, `keyDraft = ""` clears the in-memory draft. Compose state is local; not retained across configuration changes (rememberSaveable not used here intentionally). |
</threat_model>

<verification>
- AiSettingsViewModel exposes 4 StateFlows + 5 setters, no raw key field.
- AiModule registers the OpenAICompatibleClient with `keyProvider = { secureKeyStore.readApiKey() }`.
- SharedModule includes aiModule.
- AiSettingsKoinHelper mirrors the canonical 9-line shape.
- AiSettingsScreen has provider preset dropdown, masked key input with toggle, base URL with HTTPS validation, model field, save and clear actions.
- Routes.kt registers AiSettingsRoute, AiWorkoutGenRoute, AiMealGenRoute.
- MainScreen.kt registers `composable<AiSettingsRoute> { AiSettingsScreen(...) }` in the Workout-tab NavHost.
- SettingsSheet has a new AI row that triggers navigation.
- Human-verify checkpoint passes (all 10 steps confirmed).
</verification>

<success_criteria>
- BYOK Settings UX live on Android (REQ-AI-06).
- Provider preset switching auto-fills base URL + model (D-18-06).
- HTTPS-only base URL enforced at the input layer (T-18-04-04 mitigation).
- AI Settings screen wired into existing Settings entry (D-18-05).
- iOS factory ready for Plan 10's SwiftUI handoff.
</success_criteria>

<output>
After completion, create `.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-05-SUMMARY.md` with: file list, AiSettingsViewModel API, AiModule contents, navigation entry-point summary, and the human-verify outcome.
</output>
