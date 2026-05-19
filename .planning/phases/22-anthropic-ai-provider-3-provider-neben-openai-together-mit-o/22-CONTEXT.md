# Phase 22: Anthropic AI Provider — 3. Provider neben OpenAI/Together mit OAuth-Login + Modell-Picker - Context

**Gathered:** 2026-05-19
**Status:** Ready for planning

<domain>
## Phase Boundary

Anthropic-Modelle (Opus 4.7 / Sonnet 4.6 / Haiku 4.5) als **dritter AI-Provider** neben dem bestehenden OpenAI-kompatiblen Pfad (OpenAI / Together / OpenRouter) verfügbar machen. Auth über OAuth gegen die User-eigene Claude-Pro/Max-Subscription (kein API-Credit-Konto nötig), mit API-Key als Fallback für Console-User. Multi-Provider-Koexistenz: alle drei Provider können gleichzeitig verbunden sein, aber **ein globaler aktiver Provider** wird in Settings gewählt.

**Motivation:** iOS-Demo-Test 2026-05-18 hat Together-AI als "echt sauer langsam" identifiziert. Anthropic (insbesondere Haiku 4.5) ist merklich schneller bei vergleichbarer Qualität. Demo-Deadline Ende Mai 2026.

**In scope:**
- Neuer `AnthropicClient` (`shared/.../data/api/`) — separater Client, Anthropic Messages API (`/v1/messages`)
- Neues gemeinsames Interface `AiInferenceClient` (`shared/.../data/api/` oder `domain/ai/`) — `OpenAICompatibleClient` und `AnthropicClient` implementieren es
- OAuth-Flow mit PKCE über `ASWebAuthenticationSession` (iOS) / Chrome CustomTabs (Android) gegen `claude.ai/oauth/authorize`
- API-Key-Paste als zweiter Auth-Pfad im Anthropic-Connect-Sheet (Empfehlung = OAuth, Fallback = Key)
- Token-Storage erweitert `SecureKeyStore` zu `Map<ProviderId, Credential>` (OAuth-Token, API-Key — beide platform-secure)
- One-time Migration: bestehender `openai.api.key` wird in neue Provider-Map als `openai`-Eintrag überführt
- `SettingsRepository` erweitert um `activeProvider: ProviderId` + `modelByProvider: Map<ProviderId, String>`
- Settings-UI: Provider-Liste mit Radio-Toggle (aktiv) + pro Provider ein Modell-Dropdown + "+ Provider hinzufügen"
- Anthropic-Modell-Dropdown: Opus 4.7 / Sonnet 4.6 / Haiku 4.5, Default Opus 4.7
- Bestehende Workout-/Recipe-Prompts (`workout-system-prompt.md`, `recipe-system-prompt.md`) werden 1:1 weiterverwendet — kein Prompt-Rewrite in dieser Phase
- Koin-Wiring: aktiver Provider entscheidet welche `AiInferenceClient`-Impl injiziert wird

**Out of scope (verschoben in Folge-Phasen / Backlog):**
- Per-Task-Defaults (Workout-Default vs Recipe-Default Modell) — bewusst gegen entschieden
- Per-Action-Picker direkt im Gen-Screen — bewusst gegen entschieden
- Anthropic-spezifische Features (Extended Thinking, Citations, Cache-Control, Tool-Use) — können später optional dazu, jetzt nicht
- Prompt-Optimierung für Anthropic (XML-Tags, system-handling-Varianten) — bestehende Prompts werden unverändert übernommen
- Re-Test der Workout-/Recipe-Qualität gegen Anthropic-Modelle — Demo-Verifikation reicht
- Background-Mini-Bar für laufende AI-Generation → Phase 23
- iOS Workout-Gen-Parität (Zielmuskel, Sets/Reps) → Phase 23

</domain>

<decisions>
## Implementation Decisions

### Auth-Strategie

- **D-22-01:** **OAuth (Claude Pro/Max)** als Primary-Auth-Pfad. PKCE-Flow nachgebaut wie Claude Code: `ASWebAuthenticationSession` (iOS) / Chrome CustomTabs (Android) öffnet `claude.ai/oauth/authorize`, returned Bearer-Token wird gegen `api.anthropic.com/v1/messages` verwendet und gegen die User-Subscription verbraucht — **nicht** gegen API-Credits. Damit kann der User seine vorhandene Pro/Max-Subscription nutzen, ohne separat API-Tokens kaufen zu müssen.

  **Why:** User hat Claude Pro/Max aber keine API-Credits. Console-API-Key wäre separates Billing. OAuth ist der einzige Pfad, der die existierende Subscription nutzt.

  **How to apply:** Researcher muss die Claude-Code-OAuth-Flow-Details verifizieren (Authorize-URL, Token-Endpoint, Scopes, Refresh-Token-Handling, Header-Format gegen `api.anthropic.com`). Planner muss platform-specifics festlegen (URLSchemes für iOS-Redirect, intent-filter für Android-Redirect).

- **D-22-02:** **API-Key als Fallback** im Connect-Sheet. UI: OAuth-Button als Primary/Recommended-Action ("Mit Claude.ai-Konto verbinden"), API-Key-Paste als Secondary-Link ("Du hast einen API-Key von console.anthropic.com? Hier paste’n"). Validation via Test-Request gegen `/v1/messages` mit minimalem Prompt.

  **Why:** Zukunftssicher für Console-User; ein zusätzlicher API-Key-Pfad ist im AnthropicClient ohnehin trivial (nur Header-Wechsel: `x-api-key` statt OAuth-Bearer).

### Client-Architektur

- **D-22-03:** **Separater `AnthropicClient` + gemeinsames Interface `AiInferenceClient`** (kein Adapter, keine Normalisierung auf OpenAI-Schema).

  Interface (in commonMain):
  ```kotlin
  interface AiInferenceClient {
      suspend fun chatCompletion(...): Result<AiResponse, AiError>
      fun chatCompletionStreaming(...): Flow<AiStreamChunk>
  }
  ```
  Bestehender `OpenAICompatibleClient` wird angepasst um das Interface zu implementieren (seine public API ändert sich minimal). Neuer `AnthropicClient` implementiert es parallel.

  **Why:** Anthropic Messages-API ist nicht OpenAI-kompatibel (top-level `system`, andere SSE-Events `content_block_delta`, `anthropic-version`-Header Pflicht, andere tool-use-Shape). Adapter-Approach würde Anthropic-Features verstecken und Mapping-Bugs einladen. Strategy-Pattern hält beide Backends Feature-treu.

- **D-22-04:** **Koin wählt Impl zur Laufzeit** basierend auf `SettingsRepository.activeProvider`. Use-Cases (`WorkoutAiUseCase`, `RecipeAiUseCase`, `AiGenerationManager`) bekommen `AiInferenceClient` injiziert — sie wissen nicht welches Backend.

  **Why:** Provider-Switching ohne Use-Case-Änderungen. Saubere Trennung in Clean-Architecture-Sinne (Phase 20).

  **How to apply:** Koin-Wiring vermutlich via `factory<AiInferenceClient> { resolveActiveProvider(get()) }` oder `single` mit observable invalidation bei Provider-Switch. Planner entscheidet.

### Provider/Modell-Picker UX

- **D-22-05:** **Settings-only, ein globaler aktiver Provider+Modell.** Kein Picker in Gen-Screens.

  Settings-Sektion "AI-Provider":
  - Liste der verbundenen Provider mit Radio-Buttons (genau einer aktiv)
  - Pro Provider ein Modell-Dropdown
  - "+ Provider hinzufügen"-Button öffnet Connect-Sheet
  - Aktiver Provider+Modell gilt global für alle AI-Generationen (Workout + Recipe)

  **Why:** Cleaner Gen-Screen ohne Picker-Clutter. User-Vision (Memory: `[[ai-provider-extension]]`) explizit für simple Settings-Switching. Per-Task-Defaults wurde bewusst gegen entschieden.

  **How to apply:** Planner: Settings-Screen erweitern um neue Sektion. Per-Provider-Settings-Sub-Sheet wenn Provider getappt wird (Modell wechseln, Verbindung trennen, neu authentifizieren).

- **D-22-06:** **Anthropic-Modelle (in Dropdown-Reihenfolge):** Opus 4.7 (`claude-opus-4-7`) [Default] / Sonnet 4.6 (`claude-sonnet-4-6`) / Haiku 4.5 (`claude-haiku-4-5-20251001`).

  Default = Opus 4.7. Letzte Modell-Wahl pro Provider wird persistiert in `SettingsRepository.modelByProvider`. Bei erstem Connect: Default-Modell wird gesetzt.

### Multi-Provider-Koexistenz & Storage-Migration

- **D-22-07:** **`SecureKeyStore` erweitern zu Map-basierter Storage** mit Provider-spezifischen Keys:
  - `openai.api.key` (bleibt bestehen — migriert in neue Struktur)
  - `together.api.key` (neu, wenn User Together als separaten Provider anlegt — derzeit nutzt er Together via baseUrl-Override)
  - `anthropic.oauth.token` (neu, JSON: `{access_token, refresh_token, expires_at}`)
  - `anthropic.api.key` (neu, optional, falls API-Key-Fallback genutzt)

  Common-Interface: `suspend fun readCredential(provider: ProviderId): Credential?` + `suspend fun writeCredential(provider: ProviderId, credential: Credential)`. `Credential` ist sealed class mit `ApiKey(value)` und `OAuthToken(access, refresh, expiresAt)`.

  **Why:** Platform-secure-Storage (Keychain iOS / EncryptedSharedPreferences Android) wird beibehalten — nur die Slot-Anzahl steigt. Sealed-Credential macht den Token-Refresh-Pfad explicit-typed.

- **D-22-08:** **One-time Migration beim ersten Start auf Phase-22-Version:**
  1. App startet → `SettingsMigration.run()` (in `data/repository/` oder `infrastructure/migration/`)
  2. Wenn `SecureKeyStore.readApiKey()` (alter API) non-null UND neue Map leer → schreibe Key als `openai`-Eintrag in neue Map
  3. Wenn `SettingsRepository.aiBaseUrl != default` → infer Provider (`together.ai` → `together`-Eintrag, anderes → `openai`-Eintrag mit custom baseUrl)
  4. Setze `activeProvider = openai` (oder `together`, je nach inferred)
  5. Lösche alten `openai.api.key`-Slot aus SecureKeyStore (cleanup)
  6. Schreibe Migration-Flag in DataStore (`migrated_to_multi_provider = true`)

  **Why:** User merkt nichts. Keine "bitte neu connecten"-UX. Alte Settings funktionieren weiter.

  **How to apply:** Planner muss Migration-Trigger entscheiden (Application.onCreate vs erster AI-Use-Case-Call). Migration muss idempotent sein (Flag verhindert re-run). Unit-Tests in commonTest mit fake SecureKeyStore.

- **D-22-09:** **`SettingsRepository`-Erweiterungen** (DataStore-Keys):
  - `active_provider: String` (Enum-Serialisierung — `openai` / `together` / `anthropic`)
  - `model_openai: String` (existiert evtl. schon — sonst default `gpt-4o-mini`)
  - `model_together: String` (default — Researcher checkt Together-Models)
  - `model_anthropic: String` (default `claude-opus-4-7`)
  - `base_url_openai`, `base_url_together` (für OpenAI-compat-Pfade — Anthropic hat fixed Endpoint)
  - Existing `aiBaseUrl` wird deprecated → migriert in `base_url_<provider>`

### Prompts

- **D-22-10:** **Bestehende Prompts unverändert.** `workout-system-prompt.md` und `recipe-system-prompt.md` werden vom `AnthropicClient` über das top-level `system`-Feld der Messages-API gepasst. Kein XML-Tag-Wrapping, kein Anthropic-spezifisches Prompt-Rewriting in dieser Phase.

  **Why:** Phase-Boundary aus Roadmap-Titel: "Default Opus 4.7 mit **bestehenden Prompts**". Prompt-Optimierung wäre eigene Phase.

  **How to apply:** Bei der Implementation: `AiPromptCatalog.workoutSystemPrompt` einfach an `MessagesRequest.system` durchreichen. Anthropic akzeptiert markdown — die `.md`-Files werden weiter so geladen wie sie sind.

### Streaming

- **D-22-11:** **Streaming weiterhin Default-Modus** für UI (siehe `AiGenerationManager`-Pattern). `AnthropicClient.chatCompletionStreaming` parsed die named SSE-Events:
  - `message_start` → emit `AiStreamChunk.Start(metadata)`
  - `content_block_delta` → emit `AiStreamChunk.Token(text)`
  - `message_delta` → ignorieren (stop_reason kommt am Ende)
  - `message_stop` → emit `AiStreamChunk.Done`
  - `error` → emit `AiStreamChunk.Error(...)`

  Gemeinsame `AiStreamChunk`-Sealed-Class in commonMain ist die Abstraction über beide SSE-Formate.

  **Why:** UI darf nicht zwischen Providern unterscheiden müssen. Streaming-Konsistenz erfordert eine gemeinsame Chunk-Shape.

### Token-Refresh

- **D-22-12:** **Automatischer Token-Refresh** im `AnthropicClient` per Request-Interceptor:
  - Vor jedem Request: wenn `OAuthToken.expiresAt < now + 60sec` → refresh
  - Refresh via `POST claude.ai/oauth/token` mit `refresh_token`-Grant
  - Bei Refresh-Fehler (refresh_token expired/revoked) → token löschen + Notification "Bitte mit Claude.ai neu verbinden" → Settings-Deep-Link
  - **Kein** silent re-auth während laufender Generation — User-Action erforderlich

  **Why:** Reibungslose UX solange Subscription aktiv ist; klares Error-State wenn nicht mehr.

  **How to apply:** Researcher muss bestätigen ob Anthropic OAuth refresh_token überhaupt rotiert (manche OAuth-Provider geben nur access_token zurück und erwarten neuen Browser-Login). Planner setzt Fallback-Strategie auf.

### Tests

- **D-22-13:** Automatisierte Tests dort wo logic-heavy und low-cost:
  - **Migration** (D-22-08): ja — commonTest mit fake `SecureKeyStore` + `SettingsRepository`. Verifiziere idempotency, Mapping-Korrektheit, Cleanup des alten Slots.
  - **Anthropic SSE-Parser** (D-22-11): ja — commonTest mit Sample-Responses aus Anthropic-Docs. Edge-cases: leerer content_block, error-Event mid-stream.
  - **`AiInferenceClient`-Switch via Koin** (D-22-04): ja — DI-test, verifiziert dass `activeProvider`-Wechsel die richtige Impl returned.
  - **OAuth-Flow End-to-End**: nein — manuelle Verifikation auf beiden Plattformen (zu viel platform-mocking)
  - **Anthropic API-Roundtrip (network)**: nein — würde Mock-Server brauchen, nicht prioritär für Uni-Projekt

### Claude's Discretion

- Naming der neuen Interfaces/Klassen (`AiInferenceClient` vs `AiProvider` vs `LLMClient` — Bikeshed)
- Genaue Koin-Wiring-Strategie für Provider-Switch (factory vs single mit invalidation vs SharedFlow-based reload)
- File-Layout: Common-Interface in `data/api/` oder `domain/ai/` (Dependency-Rule beachten — Interface in `domain/`, Impls in `data/`)
- Migration-Trigger: Application.onCreate vs lazy beim ersten AI-Use-Case-Call
- UI-Polish: Provider-Logos im Settings-Screen ja/nein, Connect-Sheet-Animation
- Reihenfolge der Modelle im Dropdown (Opus → Sonnet → Haiku ist Default, könnte alphabetisch sein)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Codebase-Maps
- `.planning/codebase/INTEGRATIONS.md` § "AI inference (BYOK — Bring Your Own Key)" — aktuelle `OpenAICompatibleClient`-Architektur, `SecureKeyStore`-Setup, Streaming-Pattern, Timeout-Werte
- `.planning/codebase/ARCHITECTURE.md` — Clean-Architecture-Schichten nach Phase 20 (Dependency-Rule für neues Interface beachten)
- `.planning/codebase/STRUCTURE.md` — Package-Layout (commonMain / iosMain / androidMain)
- `.planning/codebase/CONVENTIONS.md` — Koin-Wiring-Pattern, KMP-ViewModel-Convention, expect/actual-Pattern

### Vorgängerphase
- `.planning/phases/20-clean-architecture-refactor-dependency-rule-fixen-repository/20-13-SUMMARY.md` — Phase-20-Endzustand: wo data/ vs domain/ vs presentation/ wohnt. Neuer Code muss dependency-rule respektieren.
- `.planning/phases/21-bug-wave-aus-ios-demo-test-2026-05-18-ai-workout-state-loss-/21-CONTEXT.md` — gerade abgeschlossene Bug-Wave; B1 (AI-Workout state-loss) hat `WorkoutAiViewModel` angefasst — Konflikte mit Phase 22 prüfen

### Bestehende AI-Source-Files (Researcher MUSS lesen)
- `shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt` — Vorbild für neuen Client, definiert Timeout-Werte, HTTPS-Gate, 64KB-Cap, Streaming-Pattern, AiError-Sealed-Class
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.kt` — `expect class`, muss extended werden für Multi-Provider-Storage
- `shared/src/iosMain/kotlin/com/pumpernickel/data/api/SecureKeyStore.ios.kt` — Keychain-Wiring (service `PumpernickelApp.AI`, accessibility `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`)
- `shared/src/androidMain/kotlin/com/pumpernickel/data/api/SecureKeyStore.android.kt` — EncryptedSharedPreferences (file `ai_secrets`, MasterKeys.AES256_GCM)
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt` — DataStore-Settings, erweitern um activeProvider + modelByProvider
- `shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt` (~Zeile 98 + AiModule) — Koin-Wiring für HttpClient + AI
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/` (WorkoutAiUseCase, RecipeAiUseCase, AiGenerationManager) — Use-Cases die `AiInferenceClient` konsumieren
- `shared/src/commonMain/resources/workout-system-prompt.md` + `recipe-system-prompt.md` — bestehende Prompts, werden 1:1 weiterverwendet

### Platform-OAuth-Wiring (Researcher MUSS verifizieren)
- iOS: `ASWebAuthenticationSession` (Apple Auth Services) — Redirect-URI-Schema in `iosApp/iosApp/Info.plist` (`CFBundleURLTypes`) registrieren
- Android: `androidx.browser:browser` (Chrome CustomTabs) + intent-filter in `androidApp/.../AndroidManifest.xml` für OAuth-Redirect

### Anthropic API & OAuth
- Anthropic Messages API: https://docs.anthropic.com/en/api/messages — `/v1/messages` endpoint, `system` field, content blocks
- Anthropic Streaming: https://docs.anthropic.com/en/api/messages-streaming — SSE event types (`message_start`, `content_block_delta`, `message_stop`, `error`)
- Anthropic Models Overview: https://docs.anthropic.com/en/docs/about-claude/models — Modell-IDs, Context-Length, Pricing
- Claude Code OAuth-Flow (Pro/Max-Auth-Vorbild): Researcher muss recherchieren — kein offizielles Doc-Link bekannt, evtl. `claude-code` repo oder `@anthropic-ai/sdk` Quelle prüfen. Stichworte: PKCE, `claude.ai/oauth/authorize`, Subscription-Bearer-Token

### Memory-Refs (User-Vision-Hintergrund)
- `[[ai-provider-extension]]` — User-Vision-Note für diese Phase (im Memory-Store)
- `[[ai-background-generation-ux]]` — Folge-Vision (Mini-Bar, Phase 23) — kein Build-Input für Phase 22, aber Architecture sollte global-AiGenerationState-Flow nicht blockieren

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`OpenAICompatibleClient`** (`shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt`) — Vorbild für `AnthropicClient`. Übernimmt: HTTPS-Gate, 600s Request-Timeout, 64KB-Cap, AiError-Sealed-Class, keyProvider-Lambda-Pattern. **Wird angepasst** um `AiInferenceClient`-Interface zu implementieren.
- **`SecureKeyStore`** (commonMain `expect class`, iOS Keychain, Android EncryptedSharedPreferences) — wird extended zu Map-basierter Multi-Slot-Storage statt Single-Key-Slot.
- **`SettingsRepository`** + DataStore — neue Keys (`active_provider`, `model_<provider>`, `base_url_<provider>`) reihen sich ein in bestehendes Pattern.
- **Ktor `HttpClient`** als Koin `single` in `SharedModule.kt:98` — wird vom `AnthropicClient` mitgenutzt (kein zweiter HttpClient).
- **`AiPromptCatalog`** lädt `workout-system-prompt.md` / `recipe-system-prompt.md` aus `commonMain/resources/` — beide werden vom `AnthropicClient` über `system`-Field gepasst.
- **`AiError`-Sealed-Class** und Streaming-Flow-Pattern — gemeinsam für beide Clients (in commonMain).
- **`AiGenerationService` (Android Foreground)** + **iOS BGTaskScheduler** (`com.pumpernickel.ai_generation`) — provider-agnostisch, brauchen keine Änderung.

### Established Patterns
- **expect/actual** für Platform-Code: `createHttpClient()`, `SecureKeyStore`, `createDataStore()` — neuer OAuth-Browser-Launcher wird denselben Pattern brauchen (`expect class OAuthBrowserLauncher` mit `ASWebAuthenticationSession`/CustomTabs als actuals).
- **Koin-Wiring**: `single<RemoteFoodSearchClient> { OpenFoodFactsAdapter(get()) }` — Provider-Switch wird ähnlich aussehen (`factory<AiInferenceClient> { resolveByActiveProvider(get()) }`).
- **Clean-Architecture (Phase 20)**: `domain/ai/` darf nicht von `data/api/` importieren. Interface `AiInferenceClient` gehört in `domain/ai/`; Impls in `data/api/`.
- **Streaming Flow-Pattern**: `OpenAICompatibleClient.chatCompletionStreaming(): Flow<...>` — `AnthropicClient` reproduziert gleiches Pattern mit Anthropic-SSE-Parser.
- **String-Resources**: Android in `androidApp/src/main/res/values/strings.xml`, iOS hardcoded. Neue Strings für Connect-Sheets, Error-Toasts.
- **KMPNativeCoroutinesAsync** für iOS-Konsumption von Flows — Settings-UI auf iOS verwendet `asyncSequence(for:)` für `aiSettings`-StateFlow.

### Integration Points
- **`AnthropicClient`** ↔ Ktor `HttpClient` (Koin) ↔ `SecureKeyStore` (für OAuth-Token oder API-Key) ↔ `SettingsRepository` (aktives Anthropic-Modell)
- **OAuth-Flow** ↔ neuer `expect class OAuthBrowserLauncher` ↔ iOS `ASWebAuthenticationSession` / Android `CustomTabsIntent` + intent-filter Redirect-Handler ↔ `SettingsRepository`-Flag "Anthropic verbunden" + `SecureKeyStore.writeCredential(anthropic, OAuthToken(...))`
- **Provider-Switch** ↔ Settings-UI ↔ `SettingsRepository.activeProvider = ...` ↔ Koin-Reload oder factory-Recompute ↔ nächster `AiInferenceClient`-Inject liefert neuen Provider
- **Migration** ↔ Application/Bootstrap ↔ alter `SecureKeyStore.openai.api.key` + alter `SettingsRepository.aiBaseUrl` ↔ neue Multi-Provider-Struktur ↔ Migration-Flag in DataStore
- **Use-Case-Konsum unverändert**: `WorkoutAiUseCase`, `RecipeAiUseCase`, `AiGenerationManager` injecten `AiInferenceClient` (vorher konkretes `OpenAICompatibleClient`) — minimal-invasiv

</code_context>

<specifics>
## Specific Ideas

- User hat **Claude Pro/Max-Subscription, keine API-Credits** — daher ist OAuth nicht "nice to have" sondern Voraussetzung damit User selbst die App testen kann.
- Modell-Reihenfolge im Dropdown soll User-Wahrnehmung respektieren: Opus 4.7 (Default, "best quality") → Sonnet 4.6 ("balanced") → Haiku 4.5 ("fastest" — explizit für Speed-sensitive Use-Cases wie Recipe-Gen).
- Connect-Sheet UX-Vision (aus Memory): "OAuth-Login mit Anthropic (kein API-Key-Eintippen wie bei Together)" — Together-AI-Key-Paste war im Demo-Test als reibungsstark wahrgenommen.
- Settings-UI sollte erkennen lassen welcher Provider gerade verbunden ist (grüner Punkt/Checkmark) vs nur angelegt aber nicht authentifiziert.

</specifics>

<deferred>
## Deferred Ideas

- **Per-Task-Defaults** (Workout-Default vs Recipe-Default Modell) — bewusst gegen entschieden in Area 3, kann aber in eigener Folge-Phase nachgereicht werden, falls Speed/Quality-Trade-off auffällt.
- **Per-Action-Picker** im Gen-Screen — analog dazu, optional als Power-User-Feature in eigener Phase.
- **Anthropic-spezifische Features**: Extended Thinking (Reasoning-Tokens für komplexere Workout-Gen), Citations, Prompt-Cache-Control (Cost-Saving für identische System-Prompts) — alle nicht in Phase 22, könnten in eigener "AI-Optimization"-Phase.
- **Prompt-Optimierung für Anthropic** (XML-Tag-Wrapping, system-handling-Varianten) — bewusst out-of-scope; evaluate erst nach Demo-Feedback.
- **Together-AI als eigener "verbundener Provider"** mit dediziertem Slot (statt nur baseUrl-Override im OpenAI-Pfad) — Migration setzt Together-User auf `openai`-Eintrag mit custom baseUrl; explicit Together-Slot könnte später separat eingerichtet werden.
- **Token-Refresh-Background-Job** (z.B. WorkManager Android, BGTaskScheduler iOS) — derzeit nur per-Request-Refresh; falls Token in der Praxis oft expired sind, könnte ein periodischer Refresh-Job sinnvoll werden.
- **OAuth für andere Provider** (OpenAI hat ChatGPT-OAuth, nicht für API) — out of scope.

</deferred>

---

*Phase: 22-anthropic-ai-provider-3-provider-neben-openai-together-mit-o*
*Context gathered: 2026-05-19*
