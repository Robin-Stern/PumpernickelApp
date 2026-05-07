---
phase: 18-ai-features-f6-workout-generation-f8-meal-generation-byok-op
plan: "05"
subsystem: ai-settings-ui
tags: [viewmodel, koin, di, compose, navigation, byok, settings, kmp]
dependency_graph:
  requires:
    - 18-02 (OpenAICompatibleClient + keyProvider lambda shape)
    - 18-03 (AiPromptCatalog)
    - 18-04 (SecureKeyStore expect/actual + SettingsRepository AI fields)
  provides:
    - AiSettingsViewModel (commonMain) — 4 StateFlows + 5 setters
    - AiModule Koin module — registered in SharedModule.includes
    - AiSettingsKoinHelper (iosMain) — iOS Koin factory mirror
    - AiSettingsScreen (androidApp) — full BYOK settings Compose screen
    - AiSettingsRoute / AiWorkoutGenRoute / AiMealGenRoute in Routes.kt
    - SettingsSheet AI row navigating to AiSettingsRoute
  affects:
    - Plan 06: WorkoutAiViewModel can now resolve AiSettingsViewModel.apiKeyConfigured
    - Plan 07: Android workout generation screen uses AiWorkoutGenRoute (pre-registered)
    - Plan 08: RecipeAiViewModel can resolve AiSettingsViewModel.apiKeyConfigured
    - Plan 09: Android meal generation screen uses AiMealGenRoute (pre-registered)
    - Plan 10: iOS AI Settings view uses AiSettingsKoinHelper
tech_stack:
  added: []
  patterns:
    - AiSettingsViewModel mirrors SettingsViewModel StateFlow + setter pattern exactly
    - AiModule follows ProgressGalleryModule single-file feature module convention
    - AiSettingsKoinHelper is the canonical 9-line KoinPlatform.getKoin().get() shape
    - PasswordVisualTransformation for API key masking (T-18-05-01)
    - keyDraft cleared after save (T-18-05-05) — key never lingers in Compose state
    - HTTPS-only base URL enforced at screen layer: startsWith("https://") (T-18-05-03)
    - "Schlüssel speichern" disabled when keyDraft.isBlank() (T-18-05-04)
    - MenuAnchorType.PrimaryNotEditable for ExposedDropdownMenuBox (project convention)
key_files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/di/AiSettingsKoinHelper.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiSettingsScreen.kt
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/Routes.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/SettingsSheet.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/TemplateListScreen.kt
decisions:
  - "SettingsSheet call site is TemplateListScreen (not MainScreen) — added onNavigateToAiSettings to TemplateListScreen call site, not MainScreen"
  - "onNavigateToAiSettings has default = {} in SettingsSheet signature — backward-compatible with any other callers"
  - "All three Phase-18 routes added to Routes.kt now (AiSettingsRoute, AiWorkoutGenRoute, AiMealGenRoute) to avoid serialisation conflicts when Wave-4 plans add their screens"
  - "baseUrlDraft uses remember(baseUrl) so it re-syncs when preset changes reset the upstream Flow"
metrics:
  duration: "~8 min"
  completed: "2026-05-07"
  tasks: 2
  files_created: 4
  files_modified: 5
---

# Phase 18 Plan 05: AI Settings VM and Screen — Summary

**One-liner:** AiSettingsViewModel (4 StateFlows + 5 setters, raw key never exposed) + AiModule Koin registration + Android BYOK Settings screen with provider preset, masked key entry, HTTPS-only base URL validation, and model field — wired into existing Settings sheet entry point.

## Files Created / Modified

### Created

- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt` — ViewModel with `providerPreset`, `baseUrl`, `model`, `apiKeyConfigured` StateFlows and `setApiKey`, `clearApiKey`, `setProviderPreset`, `setBaseUrl`, `setModel` setters; provider preset defaults map (D-18-06)
- `shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt` — Koin module registering `AiPromptCatalog`, `OpenAICompatibleClient` (keyProvider lambda), and `AiSettingsViewModel`
- `shared/src/iosMain/kotlin/com/pumpernickel/di/AiSettingsKoinHelper.kt` — canonical 9-line KoinPlatform factory for iOS SwiftUI integration (Plan 10)
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiSettingsScreen.kt` — Compose screen with provider dropdown, masked key field, show/hide toggle, save + clear buttons, base URL (custom-only editable) with HTTPS validation, model field

### Modified

- `shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt` — appended `aiModule` to the `includes(...)` block after `progressGalleryModule`
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/Routes.kt` — added `AiSettingsRoute`, `AiWorkoutGenRoute`, `AiMealGenRoute` at end of file
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt` — registered `composable<AiSettingsRoute> { AiSettingsScreen(...) }` in Workout-tab NavHost; added `AiSettingsScreen` import
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/SettingsSheet.kt` — added `onNavigateToAiSettings: () -> Unit = {}` parameter; added "KI / BYOK" section with "KI-Einstellungen" row (AutoAwesome icon)
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/TemplateListScreen.kt` — wired `onNavigateToAiSettings = { navController.navigate(AiSettingsRoute) }` on the SettingsSheet call; added `AiSettingsRoute` import

## AiSettingsViewModel API Surface

```kotlin
class AiSettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val secureKeyStore: SecureKeyStore
) : ViewModel() {
    @NativeCoroutinesState val providerPreset: StateFlow<String>     // "openai" default
    @NativeCoroutinesState val baseUrl: StateFlow<String>            // preset default
    @NativeCoroutinesState val model: StateFlow<String>              // preset default
    @NativeCoroutinesState val apiKeyConfigured: StateFlow<Boolean>  // derived from SecureKeyStore

    fun setApiKey(value: String)        // writes to SecureKeyStore; sets apiKeyConfigured = true
    fun clearApiKey()                   // clears from SecureKeyStore; sets apiKeyConfigured = false
    fun setProviderPreset(preset: String) // resets baseUrl + model to per-preset defaults (D-18-06)
    fun setBaseUrl(url: String)         // writes to SettingsRepository
    fun setModel(value: String)         // writes to SettingsRepository
}
```

Raw API key is NEVER exposed via a StateFlow (REQ-AI-06).

## AiModule Contents

```kotlin
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

Wave-4 plans (06, 08) will append their use cases and VMs to this same module file.

## Navigation Entry-Point Summary

| From | Action | Route |
|------|--------|-------|
| TemplateListScreen toolbar → Settings icon | Tap "KI-Einstellungen" row | Navigate to `AiSettingsRoute` |
| AiSettingsScreen | Tap back arrow | `navController.popBackStack()` |
| Workout-tab NavHost | `composable<AiSettingsRoute>` | `AiSettingsScreen(workoutNavController)` |

## Security Mitigations Implemented (from threat model)

| Threat ID | Mitigation |
|-----------|-----------|
| T-18-05-01 | `PasswordVisualTransformation` applied by default; show/hide is per-explicit-toggle |
| T-18-05-03 | `baseUrlDraft.startsWith("https://")` check: save disabled + inline error "Nur HTTPS-URLs erlaubt." |
| T-18-05-04 | "Schlüssel speichern" `enabled = keyDraft.isNotBlank()` — empty string never reaches SecureKeyStore |
| T-18-05-05 | `keyDraft = ""` immediately after `viewModel.setApiKey(keyDraft)` call |
| T-18-05-02 | Accepted — `FLAG_SECURE` deferred as out-of-scope for prototype |

## Deviations from Plan

### Auto-fixed Issues

None — plan executed exactly as written.

### Implementation Note: Call-Site Discovery

The plan stated that SettingsSheet is constructed in MainScreen.kt. At execution time, the actual call site was found to be `TemplateListScreen.kt` (where the Settings icon is in the toolbar). The `onNavigateToAiSettings` lambda was wired in `TemplateListScreen.kt` accordingly. The `onNavigateToAiSettings: () -> Unit = {}` default parameter in SettingsSheet ensures no other call sites break.

## Human-Verify Checkpoint

**Status:** PENDING — Task 3 (human-verify) awaits user verification of the Android Settings flow.

Build command: `./gradlew :androidApp:installDebug`

Verification steps: See Task 3 in PLAN.md — 10-step UAT covering provider preset defaults, API key masking/save/clear, Together.AI preset switch, Custom HTTPS validation, persistence across relaunch, and plaintext key check via adb.

## Known Stubs

None — all StateFlows are wired to real SecureKeyStore and SettingsRepository data sources. The `apiKeyConfigured` flag is seeded from `secureKeyStore.readApiKey()` on init.

## Threat Flags

No new threat surface beyond what is documented in the plan's `<threat_model>`. The screen does not add new network endpoints, auth paths, or schema changes. The base URL validation is implemented at the UI layer as specified.

## Self-Check: PASSED

- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt` — FOUND
- `shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt` — FOUND
- `shared/src/iosMain/kotlin/com/pumpernickel/di/AiSettingsKoinHelper.kt` — FOUND
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiSettingsScreen.kt` — FOUND
- Commit 15a4b9c (Task 1: VM + AiModule + SharedModule + KoinHelper) — FOUND
- Commit a950a9e (Task 2: AiSettingsScreen + Routes + MainScreen + SettingsSheet) — FOUND
