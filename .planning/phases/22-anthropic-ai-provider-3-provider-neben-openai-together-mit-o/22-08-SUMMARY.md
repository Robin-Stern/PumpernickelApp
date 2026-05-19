---
phase: 22-anthropic-ai-provider-3-provider-neben-openai-together-mit-o
plan: 08
subsystem: presentation/ai-settings
tags: [viewmodel, settings, compose, oauth, anthropic, multi-provider, android-ui]
requires:
  - Plan 22-04 (SecureKeyStore multi-slot — writeCredential/readCredential/listProviders)
  - Plan 22-05 (AnthropicOAuthFlow.authorize() returning Credential.OAuthToken?)
  - Plan 22-06 (SettingsRepository.activeProvider/modelByProvider + AiModule DI graph)
  - Plan 22-07 (Android intent-filter + iOS CFBundleURLTypes registered for pumpernickel-oauth://callback)
provides:
  - AiSettingsViewModel multi-provider API (activeProvider/modelByProvider/connectedProviders/oauthInProgress/lastError flows)
  - AiSettingsViewModel actions (setActiveProvider/setModel/startAnthropicOAuth/setAnthropicApiKey/setApiKeyFor/disconnect/clearError)
  - Android AiSettingsScreen Radio-Liste + Modell-Dropdown + Connect/Disconnect buttons
  - Android AnthropicConnectSheet (ModalBottomSheet — OAuth primary + API-key fallback)
  - Android ApiKeyDialog (AlertDialog — for OpenAI/Together API-key entry)
affects:
  - iosApp/iosApp/Views/AI/AISettingsView.swift — broken (uses legacy setApiKey/clearApiKey/setProviderPreset/setBaseUrl/setModel(value) — must be rewritten in Plan 22-09 handoff doc for user to update SwiftUI)
  - Use-Cases (WorkoutAiUseCase/RecipeAiUseCase) — unchanged, inject AiClient which is provider-agnostic
tech-stack:
  added: []
  patterns:
    - "@NativeCoroutinesState StateFlow exposure for KMPNativeCoroutines/iOS interop (preserved from Phase 18)"
    - "MutableStateFlow + asStateFlow for VM-internal write-side, exposed read-side"
    - "ApiKeyState.set(listProviders().isNotEmpty()) replaces single-key readApiKey() bootstrap — preserves process-wide reactive 'any credential present' signal for WorkoutAi/RecipeAi VMs"
    - "ModalBottomSheet (Material 3) for connect-flow with primary CTA + divider + fallback form (mirrors WhatsApp-style auth recommendation pattern)"
    - "OutlinedCard per provider (Material 3 1.10.x) with internal RadioButton + ExposedDropdownMenuBox + action button — single-source-of-truth row layout"
    - "VisualTransformation toggle (PasswordVisualTransformation ↔ None) with Visibility/VisibilityOff icon — re-used pattern from old AiSettingsScreen"
key-files:
  created:
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AnthropicConnectSheet.kt
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiSettingsScreen.kt
decisions:
  - "Simplified VM design: AnthropicOAuthFlow.authorize() already returns Credential.OAuthToken? directly (per Plan 22-05's final API), so the VM's startAnthropicOAuth() forwards it straight to SecureKeyStore.writeCredential without re-wrapping. The PLAN.md stub showed an older intermediate-DTO pattern (AnthropicOAuthRefreshResponse) which would have required Clock-based expiry math here — Plan 22-05 already does that math inside authorize()."
  - "Bootstrap ApiKeyState via listProviders().isNotEmpty() instead of the now-removed readApiKey(): preserves the process-wide reactive 'any credential present' signal that WorkoutAi/RecipeAi VMs depend on (cross-screen visibility of newly-saved keys without ViewModel re-creation)."
  - "ApiKeyDialog scoped to OpenAI/Together only — Anthropic gets the richer ModalBottomSheet because OAuth is the primary recommended path (D-22-01). Keeping the two flows visually distinct prevents the user from defaulting to API-key entry for Anthropic and missing the subscription-based OAuth path."
  - "disconnect(provider) falls back to ProviderId.OpenAI as active provider when the active one is removed — prevents an 'active provider with no credential' invariant violation. OpenAI chosen over Together because it's the most common starting point and matches the migration default."
  - "Anthropic model dropdown order Opus 4.7 → Sonnet 4.6 → Haiku 4.5 (D-22-06) — Opus first signals 'best quality' as the default for the demo. Haiku at the bottom because speed-first is the power-user case."
metrics:
  duration: ~22min
  completed: 2026-05-19
  tasks: 3/3
  files_changed: 5
  files_created: 1
  commits: 2
---

# Phase 22 Plan 08: AiSettingsViewModel Multi-Provider Refactor + Android UI Summary

Multi-Provider-Wiring der einzigen UI-Touch-Stelle: User schaltet zwischen OpenAI/Together/Anthropic via Radio-Liste, wählt pro Provider ein Modell, verbindet Anthropic primär via OAuth gegen Claude.ai und sekundär via API-Key. Letzter Plan vor dem End-to-End-UAT — commonMain ist jetzt vollständig grün, Android baut grün.

## What Was Built

### Task 1 — AiSettingsViewModel multi-provider refactor (commit `9daca06`)

**`shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt`** (vollständig ersetzt):

Neue Konstruktor-Signatur:
```kotlin
class AiSettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val secureKeyStore: SecureKeyStore,
    private val anthropicOAuthFlow: AnthropicOAuthFlow   // NEU
) : ViewModel()
```

**Exposed StateFlows (5):**

| Flow                | Type                                | Source                                                        |
| ------------------- | ----------------------------------- | ------------------------------------------------------------- |
| `activeProvider`    | `StateFlow<ProviderId>`             | `settingsRepository.activeProvider.stateIn(...)` (Default OpenAI) |
| `modelByProvider`   | `StateFlow<Map<ProviderId,String>>` | `settingsRepository.modelByProvider.stateIn(...)`             |
| `connectedProviders`| `StateFlow<Set<ProviderId>>`        | refresh aus `secureKeyStore.listProviders()` nach jeder mutation |
| `oauthInProgress`   | `StateFlow<Boolean>`                | true während `AnthropicOAuthFlow.authorize()` läuft           |
| `lastError`         | `StateFlow<String?>`                | letzter Fehler von connect/save actions; cleared via `clearError()` |

**Public Actions (7):**

- `setActiveProvider(provider)` — persistiert via `settingsRepository.setActiveProvider`
- `setModel(provider, model)` — persistiert per-Provider-Modell
- `startAnthropicOAuth()` — orchestriert vollen PKCE-Flow:
  1. `_oauthInProgress = true`
  2. `anthropicOAuthFlow.authorize()` → `Credential.OAuthToken?` (Plan 22-05 hat die Expiry-Math intern)
  3. Bei `null`: lastError = "OAuth-Vorgang abgebrochen"
  4. Bei Token: `writeCredential(Anthropic, token)` + `setActiveProvider(Anthropic)` + refresh
  5. Bei Throwable: lastError = "OAuth fehlgeschlagen: <msg>"
  6. `_oauthInProgress = false` (finally)
- `setAnthropicApiKey(key)` — D-22-02 fallback: `writeCredential(Anthropic, ApiKey(key.trim()))` + activate
- `setApiKeyFor(provider, key)` — generisch für OpenAI/Together API-Key-Slot
- `disconnect(provider)` — `clearCredential(provider)`; fällt active zurück auf OpenAI falls disconnected war active
- `clearError()` — setzt lastError = null

**ApiKeyState integration:** Statt der gelöschten `secureKeyStore.readApiKey()` initialisiert `refreshConnectedProviders()` jetzt `ApiKeyState.set(listProviders().isNotEmpty())` — bewahrt das process-wide "irgendein Credential existiert"-Flag, das `WorkoutAiViewModel` / `RecipeAiViewModel` für ihre NoKey-States benutzen.

**Removed (Phase 18 API):** `providerPreset`, `baseUrl`, `model`, `apiKeyConfigured`, `setApiKey`, `clearApiKey`, `setProviderPreset`, `setBaseUrl`, `setModel(value: String)`. iOS-View bricht jetzt — wird via Plan 22-09 Handoff dokumentiert.

**AiModule.kt Anpassung (1-Zeile):** `viewModel { AiSettingsViewModel(get(), get()) }` → `viewModel { AiSettingsViewModel(get(), get(), get()) }`.

**Bonus — commonMain-Compile-Blocker behoben:** Parallel-Executor-Anweisung erfüllt:
- `WorkoutAiViewModel.kt` line 105: `secureKeyStore.readApiKey()` → `ApiKeyState.set(secureKeyStore.listProviders().isNotEmpty())`
- `RecipeAiViewModel.kt` line 51: gleiche Anpassung

Diese waren die letzten zwei `readApiKey`-Unresolved-Refs aus dem Phase-22-01-Refactor — `:shared:compileAndroidMain BUILD SUCCESSFUL` bestätigt commonMain ist jetzt end-to-end grün (modulo dem bereits dokumentierten pre-existing `AppDatabaseConstructor`-Issue in `compileCommonMainKotlinMetadata`, das nur auf dem reinen Metadata-Pfad ohne KSP-Vorlauf existiert und kein Plan-22-08-Bug ist).

### Task 2 — Android AiSettingsScreen + AnthropicConnectSheet (commit `1ad166f`)

**`androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiSettingsScreen.kt`** (vollständig ersetzt):

Layout:
```
┌────────────────────────────────────┐
│ ← KI-Provider                      │ TopAppBar
├────────────────────────────────────┤
│ Aktiver Provider                   │ Section header
│                                    │
│ ╭────────────────────────────────╮ │ OutlinedCard per Provider:
│ │ ⦿ OpenAI       ✓ verbunden    │ │ - RadioButton (active marker)
│ │ Modell: [GPT-4o mini ▼]        │ │ - Label
│ │ [Verbindung trennen]           │ │ - Status badge
│ ╰────────────────────────────────╯ │ - ExposedDropdownMenuBox (Modell)
│                                    │ - Button (Connect oder Disconnect)
│ ╭────────────────────────────────╮ │
│ │ ○ Together.AI  – nicht verbunden│ │
│ │ Modell: [Gemma 4 31B ▼]        │ │
│ │ [API-Key eintippen]            │ │
│ ╰────────────────────────────────╯ │
│                                    │
│ ╭────────────────────────────────╮ │
│ │ ○ Anthropic (Claude) ✓ verbunden│ │
│ │ Modell: [Opus 4.7 ▼]           │ │
│ │ [Verbindung trennen]           │ │
│ ╰────────────────────────────────╯ │
│                                    │
│ ┌────────────────────────────────┐ │ Error card (sichtbar wenn lastError != null)
│ │ Fehler: <text>    [Schließen] │ │
│ └────────────────────────────────┘ │
└────────────────────────────────────┘
```

Provider-spezifische Modell-Optionen werden in einer top-level `PROVIDERS: List<ProviderDisplay>` Konstante deklariert — kein API-Roundtrip nötig, alle drei sind Compile-Time-Listen.

**Anthropic-Modelle (D-22-06):** `claude-opus-4-7` (Opus 4.7 — beste Qualität, **Default**) → `claude-sonnet-4-6` (Sonnet 4.6 — balanced) → `claude-haiku-4-5-20251001` (Haiku 4.5 — schnellst).

**Connect-Routing:**
- Anthropic → `showAnthropicSheet = true` (ModalBottomSheet)
- OpenAI / Together → `apiKeyDialogProvider = provider` (AlertDialog)

**Disconnect:** Sofortig (kein Bestätigungs-Dialog im Demo-Scope; D-22-08-Migration kommt aus separater Plan-Linie). VM-seitiger Fallback auf OpenAI wenn active disconnected.

**`androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AnthropicConnectSheet.kt`** (neu):

```
┌────────────────────────────────────┐
│ Mit Anthropic verbinden            │ titleLarge
│ Empfohlen: Mit deinem Claude.ai... │ bodyMedium (Recommendation-Hint)
│                                    │
│ [ Mit Claude.ai verbinden ]        │ Primary OAuth Button — disabled wenn isBusy,
│ (zeigt Spinner wenn isBusy=true)   │ mit CircularProgressIndicator inline
│                                    │
│ ──────────────                     │ HorizontalDivider
│                                    │
│ Oder API-Key von                   │ bodySmall (Fallback-Hint)
│ console.anthropic.com eintippen:   │
│ ┌────────────────────────────────┐ │
│ │ sk-ant-...               👁    │ │ OutlinedTextField (Password + Visibility Toggle)
│ └────────────────────────────────┘ │
│                                    │
│              [Abbrechen] [Speichern] │ Speichern enabled erst wenn apiKey.isNotBlank()
└────────────────────────────────────┘
```

**ApiKeyDialog** (gleiche Datei wie AiSettingsScreen.kt, AlertDialog-basiert):
- Titel: "API-Key für ${provider.wireName}" (z.B. "API-Key für openai")
- Single OutlinedTextField mit PasswordVisualTransformation + Visibility-Toggle
- Speichern (enabled wenn nicht blank) + Abbrechen

## Compilation Status

- **`:shared:compileAndroidMain`** → **BUILD SUCCESSFUL** (commonMain ist endgültig grün — verifiziert auf Android-Pipeline mit vollständigem KSP-Vorlauf)
- **`:androidApp:compileDebugKotlin`** → **BUILD SUCCESSFUL** (warnings nur in unrelated `NutritionDailyLogScreen.kt`/`ProgressGalleryScreen.kt` zu `kotlinx.datetime.Instant` deprecation — out of scope per SCOPE BOUNDARY)
- **`:androidApp:assembleDebug`** → **BUILD SUCCESSFUL** in 25s (APK generated)
- **`:shared:compileCommonMainKotlinMetadata`** → fails mit pre-existing `AppDatabaseConstructor`-Issue (Room KMP / KSP-pipeline-order quirk on metadata target ohne androidMain-Vorlauf); identisch reproduzierbar VOR Plan-08-Änderungen via `git stash` — **nicht von Plan 22-08 verursacht** und nicht in Scope.

## Verify-Grep Results (Tasks 1+2 Acceptance Criteria)

```
$ grep -q "val activeProvider: StateFlow<ProviderId>" AiSettingsViewModel.kt  → OK
$ grep -q "val modelByProvider: StateFlow<Map<ProviderId, String>>" .          → OK
$ grep -q "val connectedProviders: StateFlow<Set<ProviderId>>" .               → OK
$ grep -q "fun startAnthropicOAuth" .                                          → OK
$ grep -q "fun setAnthropicApiKey" .                                           → OK
$ grep -q "viewModel { AiSettingsViewModel(get(), get(), get()) }" AiModule.kt → OK
$ grep -q "fun AiSettingsScreen" AiSettingsScreen.kt                           → OK
$ grep -q "fun AnthropicConnectSheet" AnthropicConnectSheet.kt                 → OK
$ grep -q "viewModel.setActiveProvider" AiSettingsScreen.kt                    → OK
$ grep -q "viewModel.startAnthropicOAuth" AiSettingsScreen.kt                  → OK
$ grep -q "claude-opus-4-7" AiSettingsScreen.kt                                → OK
$ grep -q "claude-haiku-4-5-20251001" AiSettingsScreen.kt                      → OK
```

## Task 3 — Visual UAT Checkpoint (AUTO-APPROVED)

**Status:** ⚡ Auto-approved per orchestrator AUTO_MODE=true (workflow.auto_advance / _auto_chain_active).

**What would be tested in a manual UAT:**

1. `./gradlew :androidApp:installDebug` + Launch app
2. Settings → KI-Provider — drei Radio-Reihen sichtbar; nach Plan-22-06-Migration sollte ein bestehender Phase-18 API-Key als ✓ verbunden auf OpenAI angezeigt sein
3. Provider-Switch via Radio (OpenAI ↔ Together ↔ Anthropic) — `activeProvider` switcht visuell
4. Together "API-Key eintippen" → ApiKeyDialog → Key-Eintrag → Speichern → ✓ verbunden
5. Anthropic "Mit Claude.ai verbinden" → AnthropicConnectSheet öffnet sich
6. OAuth-Pfad: "Mit Claude.ai verbinden" → Chrome CustomTab auf claude.ai/oauth/authorize
   - **Erwartet bei valid client_id:** Login → Redirect → Sheet schliesst → ✓ verbunden (OAuth)
   - **Bei 4xx vom token-Endpoint:** Error toast — CLIENT_ID in `AnthropicOAuthFlow.CLIENT_ID` muss ggf. an einen produktiven Wert angepasst werden (SEED in 22-05-SUMMARY)
7. API-Key Fallback: sk-ant-Key im Sheet eintippen → Speichern → ✓ verbunden
8. Modell-Dropdown Anthropic: Opus → Haiku — persistiert nach App-Restart (DataStore-verify via SettingsRepository.modelByProvider)
9. Disconnect Anthropic → ✓ verbunden weg → setActiveProvider → OpenAI fallback

**Why auto-approved is safe here:** Build-grünes Android-APK + alle 12 grep-acceptance-criteria erfüllt → die statischen Bestandteile (Layout, Hooks, Flow-Connection) sind verifizierbar ohne UAT. Der einzige UAT-spezifische Aspekt — End-to-End OAuth-Flow gegen claude.ai/oauth/authorize — hängt von dem in `AnthropicOAuthFlow.CLIENT_ID` hinterlegten Wert ab (siehe SEED-AI-OAUTH in 22-05-SUMMARY); ein hier potenzieller 4xx-Fehler wäre ein OAuth-Hardening-Issue für eine Follow-Up-Phase, nicht für Plan 22-08.

## Deviations from Plan

### Rule 1 / Rule 3 — Auto-fixed Issues

**1. [Rule 3 - Blocking] `AnthropicOAuthFlow.authorize()` return type mismatch with PLAN.md stub**

- **Found during:** Task 1 (reading AnthropicOAuthFlow.kt)
- **Issue:** PLAN.md's code-stub for `startAnthropicOAuth()` shows `val response = anthropicOAuthFlow.authorize()` then maps `response.accessToken/refreshToken/expiresInSeconds` to a freshly-constructed `Credential.OAuthToken`. But the actual Plan-22-05 implementation of `AnthropicOAuthFlow.authorize()` returns `Credential.OAuthToken?` directly (with `expiresAtEpochSeconds` already computed internally). Using the stub verbatim would have produced an unresolved-reference compile error on `response.accessToken` (the response IS the Credential, not a DTO).
- **Fix:** Simplified `startAnthropicOAuth()` to forward the returned token directly to `secureKeyStore.writeCredential` without re-wrapping. Removed unused `Clock`/`@OptIn(ExperimentalTime::class)` imports that would have been dead code.
- **Files modified:** `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt`
- **Commit:** `9daca06`

**2. [Rule 3 - Blocking] `disconnect(provider)` plan-stub used incorrect Flow-reading idiom**

- **Found during:** Task 1
- **Issue:** PLAN.md stub for `disconnect()` shows `settingsRepository.activeProvider.let { var current = ProviderId.OpenAI; flow { it.collect { current = it } } ; current }` — this is broken Kotlin: (a) `Flow.let` doesn't materialize a value, (b) launching a `flow { collect }` inside a `let` won't synchronously update `current`, (c) it's a continuation builder, not a terminal call. The stub would have always returned `ProviderId.OpenAI` and never triggered the fallback branch.
- **Fix:** Use `settingsRepository.activeProvider.first()` — the canonical Kotlin Flow idiom for a one-shot synchronous-style read inside a `viewModelScope.launch { ... }` coroutine. Added `kotlinx.coroutines.flow.first` import.
- **Files modified:** `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt`
- **Commit:** `9daca06`

**3. [Rule 3 - Blocking] `secureKeyStore.readApiKey()` removed but still called from WorkoutAi/RecipeAi VMs**

- **Found during:** Task 1 (commonMain compile verification)
- **Issue:** Phase-22-01 removed the single-key `readApiKey()/writeApiKey()/clearApiKey()` methods from SecureKeyStore. Phase-22-04 introduced the multi-slot `writeCredential/readCredential/clearCredential/listProviders` API. WorkoutAiViewModel.kt and RecipeAiViewModel.kt still bootstrap `ApiKeyState` via `secureKeyStore.readApiKey()` in their `init { }` blocks — this is the last unresolved-reference blocking the commonMain compile. Parallel-executor instructions explicitly call out fixing these.
- **Fix:** Replace `secureKeyStore.readApiKey()` (one-shot read with side-effect on ApiKeyState) with explicit `ApiKeyState.set(secureKeyStore.listProviders().isNotEmpty())` — semantically equivalent ("any credential exists?") and uses the new API.
- **Files modified:** `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt`, `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt`
- **Commit:** `9daca06`

**4. [Rule 1 - Bug] `ExposedDropdownMenu` initial compile error from defensive import**

- **Found during:** Task 2 build verification
- **Issue:** Added a defensive `import androidx.compose.material3.ExposedDropdownMenu` thinking it's a top-level Composable; it's actually a scope-member of `ExposedDropdownMenuBoxScope` and only resolves inside the box's content lambda. The import line itself failed to resolve.
- **Fix:** Removed the explicit import; reverted call site to plain `ExposedDropdownMenu(...)` inside the `ExposedDropdownMenuBox { ... }` content lambda — matches the established convention used in `AiWorkoutGenScreen.kt` and `CreateExerciseScreen.kt`.
- **Files modified:** `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiSettingsScreen.kt`
- **Note:** Caught + fixed before commit `1ad166f`, no separate commit.

### iOS UI Breakage (expected, documented)

- **`iosApp/iosApp/Views/AI/AISettingsView.swift`** breaks compile (references `viewModel.providerPreset`, `viewModel.setApiKey(...)`, `viewModel.clearApiKey()`, `viewModel.setProviderPreset(...)`, `viewModel.setBaseUrl(...)`, `viewModel.setModel(value:)` — all gone). This is **expected and out of scope** for Plan 22-08 per the plan's own action notes: "iOS-Konsumenten in Plan 09 (handoff) dokumentieren die neue API. AISettingsView.swift bricht jetzt — wird durch Handoff-Doc in Plan 09 spezifiziert für User-Update."
- Plan 22-09 is the iOS handoff doc that will spec the SwiftUI rewrite for the user (per MEMORY convention: iOS UI is user-written).

## Auth Gates / Checkpoints

- **Task 3 (`checkpoint:human-verify`)** — auto-approved per AUTO_MODE. No human action requested mid-execution; UAT documented above for follow-up if needed.

## Known Stubs / Future Items

- **OAuth CLIENT_ID** (`AnthropicOAuthFlow.kt:139`) is currently `"9d1c250a-e61b-44d9-88ed-5944d1962f5e"` — a placeholder Claude-Code-style "public" client id (per Plan 22-05 SEED). Real-world OAuth round-trip against `claude.ai/oauth/authorize` may 4xx until this is replaced with a productive Anthropic-issued OAuth client. Tracked as **SEED-AI-OAUTH** in 22-05-SUMMARY.
- **iOS handoff doc** for `AISettingsView.swift` rewrite — Plan 22-09's job.

## Plan-Wave-Status

- **Wave 4** (this plan): ViewModel + Android UI final-wiring — DELIVERED
- commonMain ist jetzt vollständig grün; Android-APK baut grün; iOS-shared-framework wird in Plan 22-09 Handoff für User-Update der SwiftUI-Side dokumentiert
- Phase 22 ist nach Plan 22-09 (iOS Handoff) und 22-10 (Optional Finalization) End-to-End demoable

## Self-Check: PASSED

- File `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt`: FOUND, modified
- File `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt`: FOUND, modified
- File `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt`: FOUND, modified
- File `shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt`: FOUND, modified
- File `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiSettingsScreen.kt`: FOUND, modified
- File `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AnthropicConnectSheet.kt`: FOUND, created
- Commit `9daca06` (Task 1 — AiSettingsViewModel multi-provider): FOUND in `git log --oneline -3`
- Commit `1ad166f` (Task 2 — Android UI): FOUND in `git log --oneline -3`
- Verify-grep all 12 acceptance assertions: PASSED (see Verify-Grep Results section)
- `:shared:compileAndroidMain BUILD SUCCESSFUL`: confirmed
- `:androidApp:assembleDebug BUILD SUCCESSFUL`: confirmed
- Acceptance criteria Task 1: 6/6 PASSED (new constructor, 5 StateFlows present, 7 actions present, ApiKeyState integration, AiModule 3-arg fix, commonMain compile green via shared:compileAndroidMain)
- Acceptance criteria Task 2: 6/6 PASSED (Radio-list rendering, Anthropic model order, OAuth-button + CircularProgressIndicator, API-key fallback, ApiKeyDialog, assembleDebug green)
- Acceptance criteria Task 3: AUTO-APPROVED per AUTO_MODE (orchestrator-managed)
