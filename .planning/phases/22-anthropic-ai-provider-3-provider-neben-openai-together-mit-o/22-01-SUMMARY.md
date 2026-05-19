---
phase: 22-anthropic-ai-provider-3-provider-neben-openai-together-mit-o
plan: 01
subsystem: infrastructure/ai
tags: [storage, credentials, multi-provider, keychain, encrypted-shared-prefs, foundation]
requires:
  - Phase-18 SecureKeyStore (commonMain expect + iOS Keychain + Android EncryptedSharedPreferences)
provides:
  - ProviderId enum (OpenAI/Together/Anthropic) mit wireName + apiKeyAccount + oauthTokenAccount
  - Credential sealed class (ApiKey + OAuthToken, beide @Serializable)
  - SecureKeyStore Multi-Slot expect-API + iOS + Android actuals
  - Legacy-Read-Pfad (readLegacyApiKey / clearLegacyApiKey) für Plan-06-Migration
affects:
  - Plan 22-04 (AnthropicAiClient + Wiring) — broken consumers AiSettingsViewModel + AiModule
  - Plan 22-06 (Migration) — wird readLegacyApiKey + writeCredential konsumieren
  - Plan 22-07 (Settings + Provider-Switch) — wird AiSettingsViewModel auf neue API umschreiben
tech-stack:
  added:
    - kotlinx.serialization.json.Json (war bereits im stack — neu in SecureKeyStore actuals)
  patterns:
    - sealed-class für storage wire format (statt Domain-Modell)
    - per-provider Slot-Naming `${wireName}.api.key` / `${wireName}.oauth.token`
    - OAuth-Primary read-order (D-22-01): OAuth slot wins über api.key slot
key-files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/ProviderId.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/Credential.kt
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/SecureKeyStore.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/infrastructure/ai/SecureKeyStore.ios.kt
    - shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/ai/SecureKeyStore.android.kt
decisions:
  - D-22-07-impl: ProviderId.apiKeyAccount und ProviderId.oauthTokenAccount sind Computed Properties statt const String — vermeidet Drift zwischen wireName und account-suffix
  - D-22-07-impl: Credential sealed class lebt in infrastructure/ai/ (nicht domain/ai/) weil sie Storage-wire-Format ist, kein Domain-Concept
  - D-22-01-impl: Read-Order in readCredential: OAuth slot zuerst, api.key slot fallback — OAuth-Primary
  - LEGACY_KEY == ProviderId.OpenAI.apiKeyAccount (== "openai.api.key") absichtlich identisch — Plan-18-Slot IST der OpenAI-Slot unter neuem Naming
metrics:
  duration: ~12min
  completed: 2026-05-19
  tasks: 2/2
  files_changed: 5
  commits: 2
---

# Phase 22 Plan 01: SecureKeyStore Multi-Slot Foundation Summary

Multi-Slot platform-secure Credential-Storage für drei AI-Provider (OpenAI/Together/Anthropic) — sealed `Credential`-Class (ApiKey + OAuthToken) mit per-Provider Keychain/EncryptedSharedPrefs-Slots; Legacy-Read-Pfad bleibt für Plan-06-Migration.

## What Was Built

### Task 1 — ProviderId + Credential sealed class (commit `1024795`)

Zwei neue commonMain-Files:

- **`ProviderId.kt`** — Enum mit drei Konstanten (OpenAI, Together, Anthropic), je mit `wireName` (DataStore-String) und zwei abgeleiteten Slot-Namen:
  - `apiKeyAccount` → `"${wireName}.api.key"`
  - `oauthTokenAccount` → `"${wireName}.oauth.token"`
  - Plus `fromWireNameOrNull(s)` für DataStore-Deserialization

- **`Credential.kt`** — Sealed class:
  - `ApiKey(value: String)` — als roher String im api.key-Slot
  - `OAuthToken(accessToken, refreshToken, expiresAtEpochSeconds)` — JSON-encoded im oauth.token-Slot
  - Beide Sub-Klassen `@Serializable`; sealed class selbst NICHT (wird je nach Variant in unterschiedliche Storage-Slots geschrieben)

### Task 2 — SecureKeyStore Multi-Slot expect + actuals (commit `6689502`)

**Final expect-Surface:**
```kotlin
expect class SecureKeyStore {
    suspend fun writeCredential(provider: ProviderId, credential: Credential)
    suspend fun readCredential(provider: ProviderId): Credential?
    suspend fun clearCredential(provider: ProviderId)
    suspend fun listProviders(): Set<ProviderId>
    suspend fun readLegacyApiKey(): String?
    suspend fun clearLegacyApiKey()
}
```

**iOS actual (`SecureKeyStore.ios.kt`):**
- Service: `PumpernickelApp.AI` (unverändert seit Phase 18)
- `kSecAttrAccount` jetzt parametriert über `account: String` durch `baseQueryDict(account, extras)`
- Interne Helpers `writeRaw / readRaw / deleteRaw` kapseln die Keychain-Mechanik (SecItemUpdate→Add fallback, SecItemCopyMatching, SecItemDelete)
- OAuth-Slot Roundtrip via `Json.encodeToString(OAuthToken.serializer(), token)`
- D-22-01 OAuth-Primary: `readCredential` versucht zuerst OAuth-Slot, fällt auf api.key-Slot zurück
- `ApiKeyState.set(true)` bei Write; `ApiKeyState.set(listProvidersBlocking().isNotEmpty())` nach Clear

**Android actual (`SecureKeyStore.android.kt`):**
- Prefs-File `ai_secrets` (unverändert seit Phase 18), AES256_SIV keys / AES256_GCM values
- Slot-Keys: `prefs.edit().putString(provider.apiKeyAccount | provider.oauthTokenAccount, ...)`
- OAuth-Slot Roundtrip wie iOS via `Json`
- `LEGACY_KEY = "openai.api.key"` const im Companion — identisch mit `ProviderId.OpenAI.apiKeyAccount`

### Keychain-account vs EncryptedSharedPreferences-Key-Naming

| ProviderId  | API-Key-Slot          | OAuth-Token-Slot          |
| ----------- | --------------------- | ------------------------- |
| OpenAI      | `openai.api.key`      | `openai.oauth.token`      |
| Together    | `together.api.key`    | `together.oauth.token`    |
| Anthropic   | `anthropic.api.key`   | `anthropic.oauth.token`   |

Identisches Naming auf iOS (kSecAttrAccount) und Android (Prefs-Key).

### Backward-compat note

`LEGACY_KEY == ProviderId.OpenAI.apiKeyAccount`. Das ist Absicht: vor der Migration in Plan 06 existiert nur dieser eine Slot — er IST bereits der OpenAI-Slot unter dem neuen Naming. `readLegacyApiKey()` und `readCredential(ProviderId.OpenAI)` lesen daher physisch den GLEICHEN Slot. Die Migration in Plan 06 ist deshalb effektiv ein DataStore-Flag-Set + ggf. provider-inference aus `aiBaseUrl`, kein Daten-Copy.

## CommonMain-Konsumenten die JETZT broken sind

Diese Konsumenten referenzieren die Phase-18-API (`writeApiKey`/`readApiKey`/`clearApiKey`), die in Task 2 entfernt wurde. Build-Reparatur erfolgt in den genannten Folge-Plänen — gemäß Plan-Wave-Strategie ist das erwarteter Zustand bis Plan 07 (Wave 3) den commonMain wieder grün stellt.

| File | Zeile(n) | Repariert in |
| ---- | -------- | ------------ |
| `shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt` | 36 — `get<SecureKeyStore>().readApiKey()` | Plan 22-04 (AnthropicAiClient + Wiring) |
| `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt` | 43, 48, 52, 69 — `readApiKey()` / `writeApiKey(value)` / `clearApiKey()` | Plan 22-07 (Settings + Provider-Switch) |
| `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt` | 105 — `readApiKey()` | Plan 22-07 (oder bereits Plan 22-04 via shared AiInferenceClient injection) |
| `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt` | 51 — `readApiKey()` | Plan 22-07 (oder bereits Plan 22-04 via shared AiInferenceClient injection) |

KDoc-Mentions in `ApiKeyState.kt`, `WorkoutAiViewModel.kt`, `RecipeAiViewModel.kt` sind Comments — kein Compile-Block.

Wave 1 Plan 02 (Anthropic OAuth — Sealed AuthMode) und Plan 03 (AnthropicClient skeleton + SSE-parser) kompilieren unabhängig, weil sie keine SecureKeyStore-Aufrufe machen — sie sind die "stable peers" zu Plan 01 in Wave 1.

## Deviations from Plan

None — Plan wurde exakt wie geschrieben ausgeführt, beide Tasks 1:1. Keine Auto-Fixes nötig: das geplante Broken-CommonMain-Stage ist ein bewusst dokumentierter Wave-Übergangs-Zustand, kein Bug.

## Self-Check: PASSED

- File `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/ProviderId.kt`: FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/Credential.kt`: FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/SecureKeyStore.kt`: FOUND (Multi-Slot expect)
- File `shared/src/iosMain/kotlin/com/pumpernickel/infrastructure/ai/SecureKeyStore.ios.kt`: FOUND (Multi-Slot actual)
- File `shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/ai/SecureKeyStore.android.kt`: FOUND (Multi-Slot actual)
- Commit `1024795` (Task 1): FOUND
- Commit `6689502` (Task 2): FOUND
- Verify-grep `expect class SecureKeyStore`: PASSED
- Verify-grep `writeCredential(provider: ProviderId, credential: Credential)`: PASSED
- Verify-grep absence of `fun writeApiKey|fun readApiKey()|fun clearApiKey()`: PASSED
- Verify-grep `actual suspend fun writeCredential` in iosMain: PASSED
- Verify-grep `actual suspend fun writeCredential` in androidMain: PASSED
- Verify-grep `provider.apiKeyAccount` in androidMain: PASSED
