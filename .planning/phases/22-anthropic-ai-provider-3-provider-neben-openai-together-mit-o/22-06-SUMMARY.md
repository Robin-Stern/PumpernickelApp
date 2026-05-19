---
phase: 22-anthropic-ai-provider-3-provider-neben-openai-together-mit-o
plan: 06
subsystem: di / migration / ai-wiring
tags: [koin, multi-provider, migration, oauth, anthropic, dispatcher, phase-22, wave-3]
requires:
  - "Plan 22-01 — ProviderId + Credential sealed class + SecureKeyStore Multi-Slot (readLegacyApiKey / clearLegacyApiKey / writeCredential / readCredential)"
  - "Plan 22-02 — SettingsRepository.{activeProvider, modelByProvider, baseUrlByProvider, migratedToMultiProvider} + setters"
  - "Plan 22-03 — AnthropicClient(client, secureKeyStore, oauthClient, oauthClientId) + AnthropicOAuthClient(client)"
  - "Plan 22-04 — AnthropicAiClient + DispatchingAiClient + provider-agnostic Use-Cases"
  - "Plan 22-05 — expect class OAuthBrowserLauncher (iOS no-arg actual + Android Context-actual) + AnthropicOAuthFlow.CLIENT_ID const"
provides:
  - "SettingsMigration class — lazy idempotent legacy → multi-provider migration, sentinel set LAST"
  - "MigratingAiClient — outermost AiClient wrapper that runs SettingsMigration.run() before each delegate call"
  - "AiModule full rewrite — DispatchingAiClient + MigratingAiClient + Anthropic stack + AnthropicOAuthFlow + SettingsMigration all registered"
  - "PlatformModule.ios.kt + .android.kt — OAuthBrowserLauncher bindings (no-arg iOS, androidContext() Android)"
affects:
  - "Plan 22-08 (Settings UI / Connect-Sheet) — must rewrite AiSettingsViewModel + WorkoutAiViewModel + RecipeAiViewModel onto new readCredential/writeCredential API (still broken in this plan, see Out-of-scope)"
  - "Plan 22-10 (Tests) — SettingsMigration is unit-test-ready (suspend + injectable Repository + SecureKeyStore)"
tech-stack:
  added: []
  patterns:
    - "Lazy migration via outermost wrapper (no Application/AppDelegate hook)"
    - "Crash-safe migration ordering: writeCredential → clearLegacyApiKey → setActiveProvider → setBaseUrl/setModel → setMigratedToMultiProvider (sentinel LAST)"
    - "Per-call provider resolution in DispatchingAiClient + per-call migration check in MigratingAiClient"
    - "Koin layer-cake: Use-Case → AiClient (MigratingAiClient) → DispatchingAiClient → {OpenAi, Anthropic}"
key-files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsMigration.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/MigratingAiClient.kt
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt
    - shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt
decisions:
  - "Migration via MigratingAiClient wrapper (not Application bootstrap): users who never use AI never trigger Keychain probes."
  - "Sentinel set LAST + outer try/catch returns early without setting sentinel: a mid-flight failure (e.g. Keychain transient error) lets the next AI call retry the migration."
  - "OpenAICompatibleClient.keyProvider routes Anthropic-active state to the OpenAI slot defensively. In normal flow DispatchingAiClient never delegates to openAiAdapter when active=Anthropic, but the fallback prevents a crash if a use-case were to inject OpenAICompatibleClient directly in the future."
  - "AnthropicClient receives AnthropicOAuthFlow.CLIENT_ID at Koin level — single source of truth for the OAuth client identifier between the authorize flow and the refresh-grant flow."
  - "DispatchingAiClient stays unbound from Koin's AiClient port; only the wrapped MigratingAiClient binds to single<AiClient>. DispatchingAiClient remains injectable as concrete class for testability."
metrics:
  duration: "~10 min"
  completed: "2026-05-19"
  tasks: 2/2
  files_changed: 5
  commits: 2
---

# Phase 22 Plan 06: Multi-Provider DI Wiring + Lazy Migration Summary

**One-liner:** Verdrahtet alle Wave-1/2-Bausteine in Koin, registriert eine outermost `MigratingAiClient`-Schicht die `SettingsMigration` lazy beim ersten AI-Call ausführt, und hängt `OAuthBrowserLauncher` plattformspezifisch in die Platform-Module.

## Was wurde gebaut

### Task 1 — SettingsMigration (commit `1f9d4a9`)

Neue Datei `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsMigration.kt`. Eine einzige `class SettingsMigration(SettingsRepository, SecureKeyStore)` mit `suspend fun run()`.

**Flow:**

1. `if (settingsRepository.migratedToMultiProvider.first()) return` — idempotenter Guard
2. `val legacyKey = secureKeyStore.readLegacyApiKey()`
3. Wenn legacyKey vorhanden:
   - Lies legacy `aiBaseUrl` + `aiModel`
   - `inferredProvider = inferProvider(oldBaseUrl)` — `together.ai` → `Together`, sonst `OpenAI`
   - `writeCredential(inferredProvider, ApiKey(legacyKey))` — physisch identisch zum legacy slot wenn provider==OpenAI, ansonsten neuer Slot
   - **Nur** bei `inferredProvider != OpenAI`: `clearLegacyApiKey()` (sonst würde der gerade geschriebene Wert wieder gelöscht — siehe Edge-Case unten)
   - `setActiveProvider(inferredProvider)`
   - Bei custom `aiBaseUrl != "https://api.openai.com/v1"` und `provider != Anthropic`: `setBaseUrl(inferredProvider, oldBaseUrl)`
   - Bei custom `aiModel != "gpt-4o-mini"`: `setModel(inferredProvider, oldModel)`
4. **Letzter Schritt:** `setMigratedToMultiProvider(true)` — Crash-Safety
5. Auto-recover bei Throwable: `return` ohne Sentinel-Set → nächster Aufruf retries

### Task 2 — AiModule + MigratingAiClient + PlatformModule (commit `e83750b`)

**MigratingAiClient.kt** (commonMain, neu): dünner Wrapper über `AiClient`. Ruft `migration.run()` vor jedem `completeJsonSchema` / `completeJsonObject`-Call auf, delegiert dann an `delegate`. Da `SettingsMigration.run()` nach dem ersten Erfolg `return` ohne work macht (DataStore-Sentinel-Read), ist der per-Call-Overhead post-migration genau ein DataStore-Snapshot.

**AiModule.kt** (commonMain, vollständig refaktoriert):

| Koin-Binding | Typ | Inhalt |
|---|---|---|
| `AiPromptCatalog` | single | unverändert |
| `OpenAICompatibleClient` | single | `keyProvider` rewritten: liest `activeProvider`, mappt Anthropic → OpenAI defensiv, ruft `readCredential(provider)` und entpackt `Credential.ApiKey.value` |
| `OpenAiCompatibleAiClient` | single | unverändert (Plan-20-Adapter) |
| `AnthropicOAuthClient` | single | `(client = get())` |
| `AnthropicClient` | single | `(client, secureKeyStore, oauthClient, oauthClientId = AnthropicOAuthFlow.CLIENT_ID)` |
| `AnthropicAiClient` | single | `(get<AnthropicClient>())` |
| `AnthropicOAuthFlow` | single | `(httpClient = get(), browserLauncher = get())` |
| `SettingsMigration` | single | `(settingsRepository = get(), secureKeyStore = get())` |
| `DispatchingAiClient` | single | `(settings, openAiAdapter, anthropicAdapter)` |
| `single<AiClient>` | single (typed) | **`MigratingAiClient(delegate = get<DispatchingAiClient>(), migration = get())`** — Use-Cases bekommen diesen |
| `AiSettingsViewModel` | viewModel | unverändert (broken bis Plan 22-08, siehe Out-of-scope) |
| `WorkoutAiUseCase`, `RecipeAiUseCase`, `AiGenerationManager` | single | unverändert — injecten `AiClient` |
| `WorkoutAiViewModel`, `RecipeAiViewModel` | viewModel | unverändert (broken bis Plan 22-08) |

**PlatformModule.ios.kt** + **PlatformModule.android.kt**: jeweils nach dem `SecureKeyStore`-Eintrag um `OAuthBrowserLauncher` ergänzt (iOS: no-arg; Android: `androidContext()`).

## Layer-Cake (Call-Flow)

```
WorkoutAiUseCase.callWithJsonSchema(...)
        │
        ▼  (Koin: single<AiClient>)
MigratingAiClient.completeJsonSchema(...)
        │  1. migration.run()       // idempotent, sentinel-guarded
        ▼
DispatchingAiClient.completeJsonSchema(...)
        │  2. resolve() reads settings.activeProvider per-call
        │
        ├──→  OpenAi/Together active → OpenAiCompatibleAiClient → OpenAICompatibleClient (Ktor)
        │                                                              keyProvider reads SecureKeyStore.readCredential(activeProvider)
        │
        └──→  Anthropic active       → AnthropicAiClient → AnthropicClient (Ktor)
                                                          ensureFreshCredential() reads SecureKeyStore.readCredential(Anthropic)
                                                          OAuth-token-refresh via AnthropicOAuthClient.refresh(...)
```

## Migration-Trigger: lazy auf erstem AI-Call

**Keine** Application.onCreate / AppDelegate.didFinishLaunchingWithOptions / koin-startup-hook. Die Migration läuft in dem Coroutine-Scope, der den ersten AI-Use-Case-Call startet (typischerweise `viewModelScope.launch` aus `WorkoutAiViewModel` oder `RecipeAiViewModel`).

**Konsequenzen:**

- **Users, die nie AI benutzen, zahlen 0 Migration-Cost.** Keychain-Probes, DataStore-Reads, alles fällt weg.
- **Erster AI-Call ist marginal langsamer** (1 DataStore-Read + ggf. 1 Keychain-Read + ggf. 3-5 DataStore-Writes). Im Streaming-Context ist das im single-digit-ms-Bereich, deutlich unter der ersten Netzwerk-Latenz.
- **Crash mid-migration wird transparent geheilt.** Sentinel ist nicht gesetzt → nächster AI-Call läuft die Migration nochmal, idempotent.
- **Sentinel-set ohne Migration-Work** (legacyKey == null): Sentinel wird trotzdem gesetzt → Keychain wird nicht bei jedem AI-Call abgefragt.

## Migration Edge-Case: `provider == OpenAI` vs `provider == Together`

**Kritischer Detail:** `LEGACY_KEY` und `ProviderId.OpenAI.apiKeyAccount` sind **physisch derselbe Keychain/Prefs-Slot** (`"openai.api.key"` — siehe Plan-22-01-Summary "Backward-compat note"). Daraus folgen zwei Pfade:

### Pfad A — `inferredProvider == ProviderId.OpenAI`

1. `writeCredential(OpenAI, ApiKey(legacyKey))` schreibt in **denselben Slot** wo `readLegacyApiKey()` gerade gelesen hat. No-op semantisch, aber stellt sicher dass `ApiKeyState` korrekt gesetzt ist (iOS-actual).
2. `clearLegacyApiKey()` wird **NICHT** aufgerufen — würde sonst den frisch geschriebenen Wert löschen.
3. Effekt: keine Daten-Bewegung, nur Sentinel-Set + ggf. baseUrl/model-Persistierung.

### Pfad B — `inferredProvider == ProviderId.Together`

1. User hatte sein Together-API-Key im legacy `openai.api.key`-Slot liegen (Phase-18-Architektur: single slot, baseUrl-Override macht den Provider-Switch).
2. `writeCredential(Together, ApiKey(legacyKey))` schreibt den Wert in `"together.api.key"` (separater Slot).
3. `clearLegacyApiKey()` löscht den `"openai.api.key"`-Slot → der Key liegt jetzt **nur** im Together-Slot.
4. `setBaseUrl(Together, oldBaseUrl)` persistiert ggf. die custom Together-baseURL (typisch `https://api.together.xyz/v1`).

Ohne den `if (inferredProvider != ProviderId.OpenAI) clearLegacyApiKey()`-Guard würde Pfad A den eigenen Schreibvorgang wieder rückgängig machen — dieser Edge-Case ist der subtile-Punkt aus dem Plan-`<objective>` und wird hier korrekt behandelt.

### Pfad C — kein legacy key vorhanden

User hat AI noch nie konfiguriert (frischer Install der Phase-22-Version, oder Update von einer Pre-Phase-18-Version). Nur Sentinel wird gesetzt; aktiver Provider bleibt auf DataStore-Default `OpenAI`, alle Provider-Maps bleiben leer und liefern die `SettingsRepositoryImpl.Companion`-Defaults zurück.

## Koin-Bindings-Übersicht: single vs. viewModel

| Lifetime | Bindings |
|---|---|
| `single` | `AiPromptCatalog`, `OpenAICompatibleClient`, `OpenAiCompatibleAiClient`, `AnthropicOAuthClient`, `AnthropicClient`, `AnthropicAiClient`, `AnthropicOAuthFlow`, `SettingsMigration`, `DispatchingAiClient`, `MigratingAiClient` (= `AiClient`), `WorkoutAiUseCase`, `RecipeAiUseCase`, `AiGenerationManager` |
| `viewModel` | `AiSettingsViewModel`, `WorkoutAiViewModel`, `RecipeAiViewModel` |
| Platform `single` | `OAuthBrowserLauncher` (iOS no-arg, Android `androidContext()`) |

**Note**: `DispatchingAiClient` ist als **konkrete** `single { DispatchingAiClient(...) }` registriert (nicht auf `AiClient` typisiert). Nur die `MigratingAiClient`-Wrapper-Instanz ist mit `single<AiClient> { ... }` typisiert — das ist die einzige Instanz, die Use-Cases via `inject<AiClient>` bekommen. Der innere `DispatchingAiClient` ist nur über `get<DispatchingAiClient>()` injizierbar (z.B. in Tests), was per Design ist.

## Out-of-scope: AiSettingsViewModel / WorkoutAiViewModel / RecipeAiViewModel bleiben broken

Per `<parallel_execution>`-Note in diesem Worktree: Plan 22-06's Scope umfasst nur `AiModule.kt` + `SettingsMigration.kt` + `MigratingAiClient.kt` + die zwei PlatformModule-Files. Die folgenden **vier Konsumenten** der entfernten Phase-18-API (`readApiKey/writeApiKey/clearApiKey`) bleiben mit `Unresolved reference`-Errors stehen und werden erst in **Plan 22-08 (Settings UI + Connect-Sheet)** repariert:

| File | Errors |
|---|---|
| `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt` | Zeile 43 `readApiKey`, 48 `writeApiKey`, 52 `clearApiKey`, 69 `clearApiKey` |
| `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt` | Zeile 51 `readApiKey` |
| `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt` | Zeile 105 `readApiKey` |

Zusätzlich: 1 vorbestehender Room-KSP-Compile-Fehler in `data/db/AppDatabase.kt:49` (`AppDatabaseConstructor` — commonMain-only metadata Eigenheit, doc'd in 22-05-SUMMARY). Dieser ist nicht Plan-22-induziert.

**Nach Plan 22-06 ist der commonMain `:shared:compileCommonMainKotlinMetadata` also weiter rot, aber:**

- Alle Plan-22-06-Files (5) sind **fehlerfrei** in der Compile-Output (verifiziert via `grep -E "(AiModule|MigratingAiClient|SettingsMigration|PlatformModule)" gradle-output → 0 Treffer`).
- AiModule's frühere `readApiKey()`-Referenz (Zeile 36) ist **entfernt** — der einzige Plan-22-01-Konsument den dieser Plan reparieren kann ist repariert.
- Die verbleibenden 4 Errors sind exakt die in Plan-22-01-SUMMARY "CommonMain-Konsumenten die JETZT broken sind" angekündigten und für Plan 22-08 reservierten.

## Erfüllt die Plan-`<must_haves>`-Truths

| Truth | Erfüllt durch |
|---|---|
| "SettingsMigration ist idempotent — zweiter Run no-op" | `if (settingsRepository.migratedToMultiProvider.first()) return` als allererste Anweisung in `run()` |
| "Legacy openai.api.key wird zu ProviderId.OpenAI-Slot migriert" | `writeCredential(OpenAI, ApiKey(legacyKey))` ist im OpenAI-Pfad ein Replace-im-selben-Slot |
| "Legacy aiBaseUrl=together.ai → activeProvider=Together" | `inferProvider(): if baseUrl.contains("together.ai") → Together` + `setActiveProvider(inferredProvider)` |
| "Koin liefert für AiClient den DispatchingAiClient; OpenAiCompatibleAiClient + AnthropicAiClient bleiben als Sub-Singletons verfügbar" | `single<AiClient> { MigratingAiClient(get<DispatchingAiClient>(), get()) }` + separate `single` für OpenAiCompatibleAiClient + AnthropicAiClient + DispatchingAiClient. Effektiv ist die `AiClient`-Auflösung `MigratingAiClient(DispatchingAiClient(...))` — die `DispatchingAiClient`-Schicht ist transparent durch den Wrapper sichtbar (siehe Layer-Cake oben) |
| "OAuthBrowserLauncher + AnthropicClient + AnthropicOAuthClient sind in Koin registriert" | Alle drei via `single { ... }` in `aiModule` + `platformModule` |
| "Migration läuft beim ersten AI-Use-Case-Aufruf (lazy via AiClient init-Path) — KEINE Application/AppDelegate-Modifikation" | `MigratingAiClient.completeJsonSchema/Object` ruft `migration.run()` vor delegate — exakt der "AiClient call-path". `iosApp/iosApp/iOSApp.swift` und `androidApp/.../Application.kt` bleiben unangetastet |

## Erfüllt die Plan-`<verification>`-Gates

- ✅ `single<AiClient>` exakt 1× in AiModule.kt (das `MigratingAiClient`-Binding)
- ✅ `MigratingAiClient(` Konstruktor-Call in AiModule + Klassen-Definition in MigratingAiClient.kt
- ✅ Alle 7 grep-Gates aus Task-2 `<verify>`-Block: PASSED
- ✅ `:shared:compileCommonMainKotlinMetadata` zeigt **keine** Errors in den 5 Plan-22-06-Files; nur pre-existing Errors aus Plan-22-01-doc'd Konsumenten + Room-KSP-commonMain-Eigenheit (alle für Plan 22-08 / Final-Wave dokumentiert)

## Deviations from Plan

None — Plan 22-06 wurde 1:1 wie geschrieben ausgeführt. Beide `<action>`-Blöcke (Task 1 `SettingsMigration.kt`-Body und Task 2's vier Schritte: AiModule-Refactor + MigratingAiClient.kt + PlatformModule.ios + PlatformModule.android) entsprechen wörtlich dem PLAN.md.

Keine Auto-Fixes erforderlich:
- Cross-plan Dependencies (Plan 22-01, -02, -03, -04, -05) waren bei Worktree-Erstellung bereits in den Base-Commit `de41647` gemerged → alle benötigten Symbole (`ProviderId`, `Credential`, `SecureKeyStore.readLegacyApiKey`, `SettingsRepository.activeProvider`, `AnthropicClient`, `AnthropicOAuthClient`, `OAuthBrowserLauncher`, `AnthropicOAuthFlow.CLIENT_ID`, `DispatchingAiClient`, `AnthropicAiClient`) waren verfügbar.
- Die `kotlin.time.Clock`-vs-`kotlinx.datetime.Clock`-Import-Frage aus Plan 22-05 ist hier nicht relevant — `SettingsMigration.kt` nutzt keinen Clock; und das `AnthropicOAuthFlow` ist bereits korrekt verdrahtet.

## Threat-Model Compliance

Plan-Threat-Register (T-22-06 bis T-22-08): alle mitigated bzw. accepted wie spezifiziert.

| ID | Disposition | Beweis |
|---|---|---|
| T-22-06 (T — partial-failure) | mitigate | Try/catch in `SettingsMigration.run()` returns ohne Sentinel-Set; nächster AI-Call retries. Write-Order: `writeCredential` vor `clearLegacyApiKey` (kein Datenverlust bei Crash zwischen den beiden) |
| T-22-07 (I — keyProvider-Logs) | mitigate | `OpenAICompatibleClient` (Plan 18) loggt `keyLen` only; die neue Lambda gibt nur `cred.value` zurück, schreibt nicht selbst — Logging-Verhalten unverändert |
| T-22-08 (E — provider-switch race) | accept | `DispatchingAiClient.resolve()` ist per-call (siehe Plan 22-04). Settings-Switch wirkt ab dem nächsten neuen Call, in-flight requests laufen mit altem Provider zu Ende |

## Self-Check: PASSED

- File `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsMigration.kt`: FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/MigratingAiClient.kt`: FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt`: FOUND (modified)
- File `shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt`: FOUND (modified)
- File `shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt`: FOUND (modified)
- Commit `1f9d4a9` (Task 1 — SettingsMigration): FOUND in `git log --oneline -3`
- Commit `e83750b` (Task 2 — AiModule + MigratingAiClient + PlatformModule wiring): FOUND in `git log --oneline -3`
- All plan-specified `<verify>`-grep-gates: PASSED (7/7)
- Compile-error-grep over plan-22-06 files: 0 errors
- Remaining errors (AiSettingsViewModel.kt × 4, WorkoutAiViewModel.kt × 1, RecipeAiViewModel.kt × 1, AppDatabase.kt × 1): pre-existing, plan-22-08-reserved, plan-22-06 scope-confirmed via `<parallel_execution>`-note
