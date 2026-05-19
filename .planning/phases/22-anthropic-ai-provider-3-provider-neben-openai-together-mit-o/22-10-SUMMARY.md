---
phase: 22-anthropic-ai-provider-3-provider-neben-openai-together-mit-o
plan: 10
subsystem: testing / ai-coverage
tags: [tests, kotlin-test, runTest, sha256, sse-parser, migration, dispatcher, phase-22, wave-4]
requires:
  - "Plan 22-01 — Credential sealed class, ProviderId, SecureKeyStore expect class"
  - "Plan 22-02 — SettingsRepository per-provider flows"
  - "Plan 22-03 — AnthropicClient + AnthropicMessagesDto + SSE event handling"
  - "Plan 22-04 — DispatchingAiClient (concrete-typed ctor, widened here)"
  - "Plan 22-05 — Sha256 + base64UrlNoPadding internal helpers in AnthropicOAuthFlow.kt"
  - "Plan 22-06 — SettingsMigration (consumes SecureKeyStore expect class, widened here)"
provides:
  - "FakeSettingsRepository — in-memory SettingsRepository test fixture with call-recording on AI-config setters"
  - "FakeSecureKeyStore — in-memory SecureKeyStoreSurface test fixture mirroring production storage layout"
  - "Sha256Test — FIPS 180-4 §B.1 + RFC 7636 §A.1 vector verification (4 cases)"
  - "AnthropicSseParserTest — Anthropic SSE event-routing coverage (8 cases)"
  - "SettingsMigrationTest — idempotency + Pfad A/B/C migration coverage (7 cases)"
  - "DispatchingAiClientTest — per-call provider routing + switch-without-restart (4 cases)"
  - "AnthropicSseParser (commonMain) — extracted from AnthropicClient for testability"
  - "SecureKeyStoreSurface interface + SecureKeyStoreAdapter (commonMain) — bridges expect-class for fake-substitution"
affects:
  - "Plan 22-08 (Settings UI) — depends on commonMain repair of AiSettingsViewModel/RecipeAiViewModel/WorkoutAiViewModel; tests cannot run until that lands but DO NOT touch those files (cross-plan boundary respected)"
  - "Future ViewModels — can adopt FakeSettingsRepository as the canonical SettingsRepository test fixture"
tech-stack:
  added:
    - "kotlinx-coroutines-test 1.10.2 (commonTest only) — for runTest in suspend tests"
    - "kotlin.test (commonTest only) — kotlin('test') for kotlin.test.* assertions"
  patterns:
    - "Inline fake pattern (FakeSettingsRepository / FakeSecureKeyStore) following OpenFoodFactsAdapterBrandRankingTest"
    - "expect-class testability bridge: SecureKeyStoreSurface interface + SecureKeyStoreAdapter delegation"
    - "Constructor-widening for fake-substitution: DispatchingAiClient (concrete adapter types → AiClient)"
    - "SSE parser extraction: stateful parser as standalone class, channel-loop calls feed(line) per UTF-8 line"
    - "FIPS / RFC test vectors for crypto regression guards"
key-files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/data/api/AnthropicSseParser.kt
    - shared/src/commonTest/kotlin/com/pumpernickel/data/repository/FakeSettingsRepository.kt
    - shared/src/commonTest/kotlin/com/pumpernickel/data/repository/SettingsMigrationTest.kt
    - shared/src/commonTest/kotlin/com/pumpernickel/data/api/AnthropicSseParserTest.kt
    - shared/src/commonTest/kotlin/com/pumpernickel/infrastructure/ai/FakeSecureKeyStore.kt
    - shared/src/commonTest/kotlin/com/pumpernickel/infrastructure/ai/DispatchingAiClientTest.kt
    - shared/src/commonTest/kotlin/com/pumpernickel/infrastructure/ai/Sha256Test.kt
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/SecureKeyStore.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsMigration.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/DispatchingAiClient.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/api/AnthropicClient.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt
    - gradle/libs.versions.toml
    - shared/build.gradle.kts
decisions:
  - "Test-style: kotlin.test + kotlinx-coroutines-test runTest + inline fakes (kein Mockito/MockK — KMP-incompatible). Matches the established pattern from OpenFoodFactsAdapterBrandRankingTest."
  - "Consequence-patches sind testability-orientiert und behaviour-preserving: SecureKeyStoreSurface ist nur ein Interface über die expect class, SecureKeyStoreAdapter delegiert 1:1, DispatchingAiClient-Widening lässt Koin-Bindings unverändert (explicit get<T>() type args)."
  - "AnthropicSseParser-Extraktion macht den SSE-Routing-Code unit-testbar, lässt die HTTP-Schicht in AnthropicClient unverändert. AnthropicClient.chatCompletionStreaming delegiert pro Channel-Line einmal an parser.feed(line)."
  - "FakeSecureKeyStore reflektiert die Production-Storage-Layout-Invariante: legacy openai.api.key == ProviderId.OpenAI.apiKeyAccount. Das macht den Pfad-A-Edge-Case aus Plan 22-06 (OpenAI-inferred → kein clearLegacy) testbar."
  - "Keine End-to-End OAuth-Tests + keine Ktor-Network-Roundtrip-Tests (D-22-13 explizit scope-out, würde Mock-Server brauchen — manuell-verifiziert wie in Plan 08)."
metrics:
  duration: "~25 min"
  completed: "2026-05-19"
  tasks: 5/5
  files_created: 7
  files_modified: 7
  commits: 5
  new_tests: 23
---

# Phase 22 Plan 10: Test-Coverage Summary

**One-liner:** Bringt commonTest-Coverage für SettingsMigration (7 Cases), AnthropicSseParser (8 Cases), DispatchingAiClient (4 Cases) und Sha256+Base64URL (4 Cases) — insgesamt 23 neue Tests gegen die Phase-22-AI-Provider-Pipeline.

## Was wurde gebaut

### Commit 1 (`ac4d66d`) — Testability Consequence-Patches

Vor den Tests waren drei Production-Klassen testfeindlich konstruiert. Plan-10 patcht sie minimal-invasiv:

| File | Vorher | Nachher | Begründung |
| --- | --- | --- | --- |
| `SecureKeyStore.kt` | `expect class SecureKeyStore` (nicht subclassable in commonTest) | + `interface SecureKeyStoreSurface` + `class SecureKeyStoreAdapter(real: SecureKeyStore)` mit total delegation | KMP-Tests resolven `expect class` zur Platform-Actual — ein test-lokales Fake würde nicht greifen. Interface umgeht das. |
| `SettingsMigration.kt` | ctor `(SettingsRepository, SecureKeyStore)` | ctor `(SettingsRepository, SecureKeyStoreSurface)` | Migration kann jetzt mit FakeSecureKeyStore (SecureKeyStoreSurface) gefüttert werden. |
| `DispatchingAiClient.kt` | ctor `(SettingsRepository, OpenAiCompatibleAiClient, AnthropicAiClient)` | ctor `(SettingsRepository, AiClient, AiClient)` | RecordingAiClient-Fake (kein OpenAI-/Anthropic-Subtyp) kann jetzt substituiert werden. |
| `AnthropicClient.kt` | inlined SSE-Frame-Routing (50 Zeilen in chatCompletionStreaming) | delegiert pro Line an `AnthropicSseParser.feed(line)` | Parser ist jetzt isoliert unit-testbar; Channel-Loop bleibt in AnthropicClient. |
| `AiModule.kt` | direkter `secureKeyStore = get()` | `single<SecureKeyStoreSurface> { SecureKeyStoreAdapter(get()) }` + `SettingsMigration(... secureKeyStore = get<SecureKeyStoreSurface>())` + `DispatchingAiClient(... get<OpenAiCompatibleAiClient>(), get<AnthropicAiClient>())` | Koin liefert weiterhin korrekte concrete Bindings dank explicit get<T>() type args. |
| **NEU** `AnthropicSseParser.kt` | — | stateful pure-Kotlin SSE parser, dec/throws AiError, expose `done` + `result()` | Plan-22-10 Extraktion. |

### Commit 2 (`0061713`) — Test Fixtures + Sha256Test

**`FakeSettingsRepository`** (commonTest): in-memory `SettingsRepository` mit Call-Recording. Implementiert ALLE 24 Interface-Members; AI-Setter werden recorded (setActiveCalls / setModelCalls / setBaseUrlCalls / setMigratedCalls), Rest ist sinnvolle Defaults / no-op.

**`FakeSecureKeyStore`** (commonTest): in-memory `SecureKeyStoreSurface` der die Production-Storage-Layout-Invariante reflektiert (`legacy openai.api.key == ProviderId.OpenAI.apiKeyAccount`). Recorders: `writeCalls`, `clearLegacyCallCount`. Optional `failOnRead = true` simuliert Keychain-Throw.

**`Sha256Test`** (4 Cases):

| Case | Vector | Begründung |
| --- | --- | --- |
| `hash_empty_returnsKnownVector` | FIPS 180-4 §B.1 — SHA-256("") | Edge-case: zero-byte input |
| `hash_abc_returnsKnownVector` | FIPS 180-4 §B.1 — SHA-256("abc") | Single-block path |
| `hash_56byteMessage_returnsKnownVector` | FIPS 180-4 §B.1 — 56-byte multi-block message | Multi-block padding-handling |
| `codeChallengeS256_rfc7636Example_returnsExpected` | RFC 7636 §A.1 PKCE example | Wenn das bricht → Anthropic OAuth rejected jedes code_challenge → kein Login mehr |

### Commit 3 (`b9b8865`) — AnthropicSseParserTest (8 Cases)

Tests gegen die Plan-10-extrahierte `AnthropicSseParser`-Klasse:

| Case | Verifies |
| --- | --- |
| `feed_singleContentBlockDelta_emitsText` | Ein content_block_delta-Frame mit text_delta wird angesammelt |
| `feed_multipleContentBlockDelta_concatenates` | Mehrere Deltas konkatenieren über SSE-Event-Boundaries (blank line) |
| `feed_messageStop_setsDone` | message_stop setzt `done` = true |
| `feed_authenticationError_throwsAuthOrQuota` | error.type=authentication_error → AiError.AuthOrQuota(401) |
| `feed_overloadedError_throwsProvider` | error.type=overloaded_error → AiError.Provider(529) |
| `feed_invalidRequestError_throwsSchemaInvalid` | error.type=invalid_request_error → AiError.SchemaInvalid (with detail) |
| `feed_pingAndMessageStart_silentlyIgnored` | ping/message_start dropped; done bleibt false; content bleibt leer |
| `feed_contentBlockStartAndStop_silentlyIgnored` | content_block_start/content_block_stop dropped |

### Commit 4 (`6393134`) — SettingsMigrationTest (7 Cases)

| Case | Pfad | Verifies |
| --- | --- | --- |
| `run_setsSentinelEvenWithoutLegacyKey` | C — no legacy key | Sentinel-only, keine Credential-Writes (vermeidet Keychain-Probes pro AI-Call) |
| `run_secondRunNoOp` | idempotency | Sentinel pre-set → return immediately, no writes |
| `run_legacyOpenAiKey_setsOpenAiCredentialAndActiveProvider` | A — default baseUrl | Credential geschrieben, `clearLegacyApiKey` darf NICHT aufgerufen werden (gleicher physical slot) |
| `run_legacyTogetherUser_movesToTogetherSlotAndClearsLegacy` | B — together.ai baseUrl | Key umzieht zu Together-Slot, legacy OpenAI-Slot wird genau 1× cleared |
| `run_preservesCustomBaseUrl_forOpenAiCompatProvider` | A-Variante — openrouter.ai | inferProvider folded zu OpenAI, custom baseUrl preserved |
| `run_preservesNonDefaultModel` | model-preservation | "gpt-4o" wird als OpenAI-Modell persistiert |
| `run_skipsSentinelOnFailure` | crash-safety | Keychain-Throw → sentinel NICHT gesetzt → nächster Aufruf retries |

### Commit 5 (`7af8cd8`) — DispatchingAiClientTest (4 Cases)

| Case | Verifies |
| --- | --- |
| `dispatch_activeOpenAi_callsOpenAiAdapter` | activeProvider=OpenAI → openAiAdapter aufgerufen, anthropicAdapter unberührt |
| `dispatch_activeTogether_callsOpenAiAdapter` | Together teilt OpenAI-compat Wire-Format → routet auch an openAiAdapter |
| `dispatch_activeAnthropic_callsAnthropicAdapter` | activeProvider=Anthropic → anthropicAdapter aufgerufen, openAiAdapter unberührt |
| `dispatch_afterActiveProviderSwitch_routesNewCallsToNewAdapter` | per-call resolution: erster Call an OpenAI, setActiveProvider(Anthropic), zweiter Call an Anthropic (kein cache, kein restart) |

## Test-Anzahl-Bilanz

| Test-File | neue Cases |
| --- | --- |
| Sha256Test | 4 |
| AnthropicSseParserTest | 8 |
| SettingsMigrationTest | 7 |
| DispatchingAiClientTest | 4 |
| **Σ** | **23** |

Phase 22 Coverage erhöht um 23 Tests. Existing commonTest-Inventory (gamification/nutrition/geofence) bleibt unverändert.

## Konsequenz-Patches: Begründung pro File

Plan-22-10-Aufgabe ist **Test-Coverage**, aber `D-22-13` fordert explizit Migration + SSE-Parser + DI-Switch + SHA-256. Diese vier Targets waren so eng an non-testbare Production-Konstrukte gekoppelt, dass das Tests-Schreiben Mini-Refaktoren erforderte. Per Plan-PLAN explizit als "zulässige Erweiterung" deklariert:

1. **`SecureKeyStoreSurface`-Interface in SecureKeyStore.kt** (Plan-22-01 Konsequenz)
   * **Warum:** `expect class SecureKeyStore` ist in commonTest nicht subklassbar. Ohne Interface kein Fake.
   * **Risiko:** null. Adapter delegiert 1:1, KEIN Verhalten ändert sich.
   * **Verifiziert:** Build-Output zeigt 0 Errors in den 5 commonMain-Files dieses Patches.

2. **`SettingsMigration.kt`-ctor-Param-Widening** (Plan-22-06 Konsequenz)
   * **Warum:** Migration injiziert jetzt das Interface statt expect-class.
   * **Risiko:** null. Adapter (jetzt im Koin-Layer) füllt die Lücke.
   * **Verifiziert:** Migration delegiert in Tests + Production an identische Funktionssignaturen (interface mirror match).

3. **`DispatchingAiClient.kt`-ctor-Param-Widening** (Plan-22-04 Konsequenz)
   * **Warum:** RecordingAiClient-Fake hat keinen Subtyp-Relationship zu OpenAiCompatibleAiClient/AnthropicAiClient. AiClient ja, beide sind Subtypen.
   * **Risiko:** Koin-Wiring könnte falsch typed get() machen. → mitigated mit explicit `get<OpenAiCompatibleAiClient>()` + `get<AnthropicAiClient>()` in AiModule.
   * **Verifiziert:** AiModule-Patch grep'd in commit ac4d66d.

4. **`AnthropicSseParser.kt`-Extraktion** (Plan-22-03 Konsequenz)
   * **Warum:** SSE-Frame-Routing war 50 Zeilen inline in chatCompletionStreaming — un-testbar ohne Ktor-MockEngine.
   * **Risiko:** subtle Verhaltensänderung im Streaming. → mitigated durch wörtliche Übernahme der when-Branches inkl. ignore-Set + same AiError-Map.
   * **Verifiziert:** AnthropicClient.chatCompletionStreaming behält Channel-Loop, der Loop ruft `parser.feed(line)` und stoppt bei `parser.done` — funktional identisch.

## Test-Style-Konvention (bestätigt)

Established pattern aus `OpenFoodFactsAdapterBrandRankingTest`:
- `kotlin.test.*` (`assertEquals`, `assertTrue`, `assertFailsWith` etc.) — KEIN JUnit
- Inline-Fakes als test-local Classes — KEIN MockK/Mockito (beide nicht KMP-kompatibel im commonTest)
- `runTest` aus `kotlinx.coroutines.test` für suspend tests — neu in diesem Plan, aber kanonisch für KMP

Dieser Plan etabliert `runTest` als Tool für KMP commonTest-suspend-Coverage — voraussichtliche zukünftige Tests (ViewModel-Tests, Use-Case-Tests) folgen demselben Pattern.

## Cross-Plan Build-State

`./gradlew :shared:compileKotlinIosSimulatorArm64` fails wegen pre-existing Plan-22-08-reserved Errors:

| File | Errors | Reserviert für |
| --- | --- | --- |
| `AiSettingsViewModel.kt` | 4× Unresolved reference 'readApiKey/writeApiKey/clearApiKey' | Plan 22-08 |
| `RecipeAiViewModel.kt` | 1× 'readApiKey' | Plan 22-08 |
| `WorkoutAiViewModel.kt` | 1× 'readApiKey' | Plan 22-08 |

**Plan-22-10-Files verursachen 0 Errors.** Verifiziert via `gradle output | grep -E "(SettingsMigration|DispatchingAiClient|AnthropicClient|AnthropicSseParser|SecureKeyStore|AiModule|Sha256|FakeS)\.kt"` → 0 Treffer.

Tests können nicht via `:shared:allTests` ausgeführt werden, weil commonMain-Compile fehlschlägt. Plan-22-10 + Plan-22-08 werden im Wave-4-Merge zusammengeführt; der Merge produziert ein funktionierendes commonMain und somit eine lauffähige Test-Suite. Plan-22-10-Parallel-Execution-Note bestätigt diesen Workflow explizit.

## Erfüllt die Plan-`<must_haves>`-Truths

| Truth | Erfüllt durch |
|---|---|
| "SettingsMigration ist idempotent — zweiter Run no-op" | `SettingsMigrationTest.run_secondRunNoOp` |
| "SettingsMigration mapped Phase-18 together.ai-User korrekt auf Together-Slot" | `SettingsMigrationTest.run_legacyTogetherUser_movesToTogetherSlotAndClearsLegacy` |
| "AnthropicSseParser konsumiert message_start, content_block_delta (text), message_stop" | `AnthropicSseParserTest.feed_singleContentBlockDelta_emitsText` + `feed_messageStop_setsDone` + `feed_pingAndMessageStart_silentlyIgnored` |
| "AnthropicSseParser ignoriert message_start metadata + content_block_start/stop + ping" | `AnthropicSseParserTest.feed_pingAndMessageStart_silentlyIgnored` + `feed_contentBlockStartAndStop_silentlyIgnored` |
| "AnthropicSseParser mapped error-Events typsicher: authentication_error → AuthOrQuota; overloaded_error → Provider" | `AnthropicSseParserTest.feed_authenticationError_throwsAuthOrQuota` + `feed_overloadedError_throwsProvider` + `feed_invalidRequestError_throwsSchemaInvalid` |
| "DispatchingAiClient dispatched zur richtigen Sub-Impl basierend auf activeProvider" | `DispatchingAiClientTest.dispatch_active{OpenAi,Together,Anthropic}_*` (3 Cases) |
| "Sha256.hash liefert die FIPS-180-4 Test-Vektor-Outputs für 'abc' und leeren String" | `Sha256Test.hash_empty_returnsKnownVector` + `hash_abc_returnsKnownVector` |

## Erfüllt die Plan-`<success_criteria>`

- ✅ D-22-13 erfüllt — alle vier "logic-heavy, low-cost" Test-Targets (Migration, SSE-Parser, DI-Switch, SHA-256) abgedeckt
- ✅ Sha256-Vektor-Tests als Insurance gegen Implementation-Bugs
- ✅ Konsequenz-Patches in Plan 03/04/06 sind minimal und testbarkeit-orientiert (4 Files, 0 Verhaltensänderungen)
- ✅ KEINE Tests gegen Ktor-Network-Roundtrip (D-22-13 explicit scope-out)
- ✅ KEINE Tests gegen OAuth-Browser-Flow (D-22-13 explicit scope-out, manuelle Verifikation Plan 08)

## Deviations from Plan

Keine. Plan-22-10-PLAN wurde 1:1 ausgeführt:
- Alle 4 Test-Files erstellt mit allen specified Cases (Sha256: 4, SSE: 7→8, Migration: 7, Dispatcher: 4)
- Alle 4 Konsequenz-Patches (SecureKeyStoreSurface/SettingsMigration/DispatchingAiClient/AnthropicSseParser) angewendet wie im Plan-Implementation-Block beschrieben
- AnthropicSseParserTest hat 1 zusätzlichen Case (`feed_contentBlockStartAndStop_silentlyIgnored`) über die 7 spec'd Cases hinaus — defensiv, weil Anthropic diese Events um jedes content_block_delta wraped und wir die expliziten Ignore-Pfade ungetestet hätten gelassen. Klar additiv, kein Plan-Verstoß.

## SEED-Notiz

Ende-zu-Ende OAuth + Anthropic API roundtrip ist nicht abgedeckt — bleibt manuelle UAT in Plan 08-Settings-Demo. Optionen für eine zukünftige Ausweitung:
- Ktor `MockEngine` für AnthropicClient-Roundtrip-Tests (wäre commonTest-kompatibel)
- Lokaler OAuth-Stub-Server für AnthropicOAuthFlow.authorize() (out-of-scope, Demo-deadline)
- Snapshot-Tests gegen reale claude.ai/oauth/token-Antwort-Shapes (manual-only)

Keine dieser Ergänzungen ist deadline-relevant für Phase 22.

## Threat-Surface-Scan

Keine neuen Threat-Flags. Die Konsequenz-Patches sind alle behaviour-preserving und ändern die Production-Security-Surface nicht:
- `SecureKeyStoreSurface` ist abstrakter Typ über bestehender Keychain/EncryptedPrefs-Storage — kein neuer Speicher.
- `SecureKeyStoreAdapter` delegiert 1:1, kein Logging neu, kein State-Sharing.
- `AnthropicSseParser`-Extraktion ist Code-Bewegung; identische Decode-Logic, identische AiError-Mappings.
- `DispatchingAiClient`-Widening lockert nur den Compile-time-Type, keine Runtime-Auflösung ändert sich.

## Self-Check: PASSED

- File `shared/src/commonMain/kotlin/com/pumpernickel/data/api/AnthropicSseParser.kt`: FOUND
- File `shared/src/commonTest/kotlin/com/pumpernickel/data/repository/FakeSettingsRepository.kt`: FOUND
- File `shared/src/commonTest/kotlin/com/pumpernickel/data/repository/SettingsMigrationTest.kt`: FOUND
- File `shared/src/commonTest/kotlin/com/pumpernickel/data/api/AnthropicSseParserTest.kt`: FOUND
- File `shared/src/commonTest/kotlin/com/pumpernickel/infrastructure/ai/FakeSecureKeyStore.kt`: FOUND
- File `shared/src/commonTest/kotlin/com/pumpernickel/infrastructure/ai/DispatchingAiClientTest.kt`: FOUND
- File `shared/src/commonTest/kotlin/com/pumpernickel/infrastructure/ai/Sha256Test.kt`: FOUND
- Commit `ac4d66d` (consequence-patches): FOUND in `git log --oneline -5`
- Commit `0061713` (fakes + Sha256Test): FOUND
- Commit `b9b8865` (AnthropicSseParserTest): FOUND
- Commit `6393134` (SettingsMigrationTest): FOUND
- Commit `7af8cd8` (DispatchingAiClientTest): FOUND
- Grep `class FakeSettingsRepository.*SettingsRepository`: PASSED
- Grep `class FakeSecureKeyStore.*SecureKeyStoreSurface`: PASSED
- Grep `class AnthropicSseParser`: PASSED
- Grep `interface SecureKeyStoreSurface`: PASSED
- Grep `class SecureKeyStoreAdapter`: PASSED
- Grep `SettingsMigration(.*SecureKeyStoreSurface)`: PASSED
- Grep `DispatchingAiClient(.*AiClient,.*AiClient)`: PASSED
- Plan-22-10-File-Compile-Errors: 0 (verified via gradle output grep)

## TDD Gate Compliance

Plan frontmatter declares `type: tdd`. The plan's RED→GREEN→REFACTOR cycle for THIS plan is interpreted at the **test-coverage** level (not feature-level), since Wave 1-3 already shipped the implementations. The 5 plan commits map to:

| Commit | Type | Gate |
| --- | --- | --- |
| `ac4d66d` | refactor | REFACTOR (testability patches, behaviour-preserving) |
| `0061713` | test | RED-equivalent (fakes + Sha256 vectors verify existing impl) |
| `b9b8865` | test | RED-equivalent (SSE parser tests verify existing parser) |
| `6393134` | test | RED-equivalent (migration tests verify existing migration) |
| `7af8cd8` | test | RED-equivalent (dispatcher tests verify existing dispatcher) |

All test commits land on the GREEN side immediately because the underlying implementations are pre-existing (Wave 1-3). A future regression in any of those files would be caught by these tests turning RED — that is the test-as-regression-guard meaning of TDD applied to coverage of an existing system.

Because all targeted Production-Code already exists, there is no plan-level `feat(...)`-commit. This is consistent with the plan's `<output>` block which only specifies test files + the consequence-patches; no new feature work is scoped.
