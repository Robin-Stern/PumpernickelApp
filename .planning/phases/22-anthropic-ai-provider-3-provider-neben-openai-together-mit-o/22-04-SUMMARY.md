---
phase: 22-anthropic-ai-provider-3-provider-neben-openai-together-mit-o
plan: 04
subsystem: infrastructure / ai-adapters
tags: [ai, adapter, dispatcher, anthropic, provider-switch, phase-22]
requires:
  - "AnthropicClient + AnthropicMessage + AnthropicMessagesRequest from Plan 22-03 (parallel worktree — cross-plan dep)"
  - "ProviderId enum from Plan 22-01 (already merged in this worktree base)"
  - "SettingsRepository.activeProvider/baseUrlByProvider/modelByProvider flows from Plan 22-02 (already merged)"
  - "AiClient + OpenAiCompatibleAiClient port from Phase 20 (untouched)"
provides:
  - "AnthropicAiClient: AiClient-Adapter über AnthropicClient — Schema-Body lebt im system-Prompt (D-22-10)"
  - "DispatchingAiClient: AiClient-Facade — settings.activeProvider.first() pro Aufruf, delegiert an Sub-Impl"
  - "WorkoutAiUseCase nutzt per-provider Config (baseUrlByProvider/modelByProvider)"
  - "RecipeAiUseCase nutzt per-provider Config (baseUrlByProvider/modelByProvider)"
affects:
  - "Plan 22-06 (Settings Migration) — bootstrappt legacy aiBaseUrl/aiModel in per-provider Maps; bestätigt dass Use-Cases nach Migration nahtlos weiterarbeiten"
  - "Plan 22-07 (Koin Wiring) — `single<AiClient> { DispatchingAiClient(...) }`, mit OpenAiCompatibleAiClient + AnthropicAiClient als injizierte Sub-Impls"
  - "Plan 22-08 (Settings UI) — provider-switch via setActiveProvider(p) ist beim allernächsten AI-Call sichtbar; kein App-Restart"
  - "AiGenerationManager bleibt unverändert (delegiert nur an Use-Cases, kennt keinen Provider)"
tech-stack:
  patterns:
    - "Adapter-Pattern (AiClient-Port, AnthropicClient = wire-format-detail in data/api/)"
    - "Facade/Dispatch-Pattern (DispatchingAiClient liest settings pro Aufruf)"
    - "Provider-agnostic Use-Cases (kein ProviderId-Branching im Use-Case-Code)"
    - "PATTERNS.md Option C — per-call provider resolution; kein Koin-Reload, kein runBlocking"
key-files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/AnthropicAiClient.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/DispatchingAiClient.kt
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt
decisions:
  - "AnthropicAiClient ignoriert baseUrl-Argument (D-22-09 fixed endpoint), aber implementiert die Port-Signatur — DispatchingAiClient passt es trotzdem durch"
  - "AnthropicAiClient ignoriert schema-Argument in completeJsonSchema (D-22-10: schema-Body lebt im system-prompt)"
  - "completeJsonSchema + completeJsonObject in AnthropicAiClient bauen identischen Request-Body — Anthropic unterscheidet das auf Wire-Ebene nicht"
  - "OpenAI + Together teilen sich openAiAdapter im DispatchingAiClient — gleiches Wire-Format, baseUrl-Argument trennt sie"
  - "AiError.SchemaInvalid als defensive Null-Reaktion auf fehlende Provider-Map-Einträge — sollte mit Plan-02-Defaults nie feuern"
metrics:
  duration: "~6 min"
  tasks_completed: 2
  files_modified: 4
  completed_at: "2026-05-19T09:34:00Z"
---

# Phase 22 Plan 04: Anthropic-Adapter + Provider-Dispatcher + Use-Case-Migration

**One-liner:** Wave-2-Plan vervollständigt: AnthropicAiClient als Port-Adapter, DispatchingAiClient als per-call Provider-Switch-Facade, Use-Cases lesen baseUrl/model aus per-provider Maps statt legacy Single-Slot-Flows.

## What Was Built

### Task 1 — AnthropicAiClient adapter (commit `70c07db`)

Neue Datei `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/AnthropicAiClient.kt`. Dünner Wrapper über `AnthropicClient` (aus Plan 22-03, paralleler Worktree). Implementiert das `AiClient`-Port-Interface mit drei spezifischen Anpassungen:

| Parameter | Anthropic-Verhalten | Begründung |
| --- | --- | --- |
| `baseUrl` | **IGNORIERT** | D-22-09 — Anthropic-Endpoint ist hardcoded in `AnthropicClient.MESSAGES_ENDPOINT`. Port-Signatur wird trotzdem gehorcht, damit DispatchingAiClient die baseUrl unverändert für OpenAI/Together durchpassen kann. |
| `schema` | **IGNORIERT** in `completeJsonSchema` | D-22-10 — Anthropic kennt kein `response_format` / `json_schema`. Existing prompts (workout-system-prompt.md, recipe-system-prompt.md) embedden den Schema-Body bereits in den system-Prompt. |
| `systemPrompt` | → `AnthropicMessagesRequest.system` (top-level) | Anthropic rejected role="system" als Eintrag in `messages[]`. |
| `userPrompt` | → `messages=[AnthropicMessage(role="user", content=userPrompt)]` | Single-user-message-Pattern. Keine system-role-message im Array. |
| `temperature` | 0.7 | Symmetrisch zu OpenAiCompatibleAiClient |
| `maxTokens` | 4096 | Symmetrisch (Reasoning-Headroom für Workout-Schema). |

`completeJsonSchema` und `completeJsonObject` bauen identischen Request-Body — Anthropic unterscheidet die beiden Pfade auf der Wire nicht (kein `response_format`-Field). Beide rufen `client.chatCompletionStreaming(request, onProgress)` auf; das `stream=false` im Body wird vom `AnthropicClient.chatCompletionStreaming` überschrieben.

### Task 2 — DispatchingAiClient + Use-Case-Migration (commit `e5525e4`)

#### DispatchingAiClient (`shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/DispatchingAiClient.kt`)

```kotlin
class DispatchingAiClient(
    private val settings: SettingsRepository,
    private val openAiAdapter: OpenAiCompatibleAiClient,
    private val anthropicAdapter: AnthropicAiClient
) : AiClient {
    private suspend fun resolve(): AiClient = when (settings.activeProvider.first()) {
        ProviderId.OpenAI, ProviderId.Together -> openAiAdapter
        ProviderId.Anthropic -> anthropicAdapter
    }
    // both completeJsonSchema + completeJsonObject delegate via resolve()
}
```

Eigenschaften:
- **Per-call resolution**: `settings.activeProvider.first()` wird bei jedem AI-Aufruf neu evaluiert. DataStore-Snapshot ist günstig genug, dass ein Cache überflüssig ist.
- **Keine Koin-Module-Reload-Komplexität**, keine `runBlocking`-Hack, kein Flow-observable factory plumbing.
- **OpenAI + Together teilen `openAiAdapter`**: gleicher OpenAI-kompatibler /chat/completions-Wire-Format, unterschiedliche `baseUrl` wird vom Caller (`WorkoutAiUseCase` / `RecipeAiUseCase`) durchgereicht.
- Provider-Switch im Settings-UI ist beim allernächsten AI-Call sichtbar — kein App-Restart.

#### Use-Case-Migration (WorkoutAiUseCase + RecipeAiUseCase)

Beide Use-Cases ersetzen die legacy Single-Slot-Reads:

```kotlin
// vorher (Phase 18):
val baseUrl = settingsRepository.aiBaseUrl.first()
val model = settingsRepository.aiModel.first()

// nachher (Phase 22):
val activeProvider = settingsRepository.activeProvider.first()
val baseUrl = settingsRepository.baseUrlByProvider.first()[activeProvider]
    ?: throw AiError.SchemaInvalid("No base URL configured for provider $activeProvider")
val model = settingsRepository.modelByProvider.first()[activeProvider]
    ?: throw AiError.SchemaInvalid("No model configured for provider $activeProvider")
```

Defensive null-checks werfen `AiError.SchemaInvalid` — sollten mit Plan-02-Defaults (alle drei Provider haben Default-Mappings) nie feuern. Die Use-Cases sind **provider-agnostisch**: kein `ProviderId`-Branching im Code, der Switch lebt komplett in `DispatchingAiClient`.

#### Verifikations-Gates (alle bestanden)

| Check | Ergebnis |
| --- | --- |
| `grep "class AnthropicAiClient"` | OK |
| `grep ": AiClient"` (AnthropicAiClient) | OK |
| `grep "system = systemPrompt"` | OK |
| `grep "AnthropicMessage(role = \"user\", content = userPrompt)"` | OK |
| `! grep "role = \"system\""` (AnthropicAiClient) | OK (keine system-role-message) |
| `grep "class DispatchingAiClient"` | OK |
| `grep "settings.activeProvider.first()"` | OK |
| `grep "ProviderId.Anthropic -> anthropicAdapter"` | OK |
| `grep "ProviderId.OpenAI, ProviderId.Together -> openAiAdapter"` | OK |
| `grep "settingsRepository.baseUrlByProvider.first()\[activeProvider\]"` × 2 | OK (1× pro Use-Case) |
| `grep "settingsRepository.modelByProvider.first()\[activeProvider\]"` × 2 | OK (1× pro Use-Case) |
| `! grep "settingsRepository.aiBaseUrl.first()"` (in domain/ai/) | OK (komplett entfernt) |
| `! grep "settingsRepository.aiModel.first()"` (in domain/ai/) | OK (komplett entfernt) |
| Global: `grep -rn "settingsRepository.aiBaseUrl\|settingsRepository.aiModel" domain/ai/` | 0 Treffer |
| Global: `grep -rn "baseUrlByProvider\|modelByProvider" domain/ai/` | 4 Treffer (genau 2 pro Use-Case) |

## Cross-Plan Dependency (Plan 22-03)

Dieser Worktree (Wave 2, `depends_on: [1, 2, 3]`) sieht Plan 22-03's Wire-Format-DTOs (`AnthropicClient`, `AnthropicMessage`, `AnthropicMessagesRequest` in `shared/src/commonMain/kotlin/com/pumpernickel/data/api/`) NICHT, weil 22-03 parallel in einem anderen Worktree läuft. Daher würde `./gradlew :shared:compileCommonMainKotlinMetadata` lokal hier scheitern (`Unresolved reference: AnthropicClient` / `AnthropicMessage` / `AnthropicMessagesRequest` in AnthropicAiClient.kt).

Das ist **erwartet und plan-konform** (siehe `<parallel_execution>`-Note im Executor-Prompt). Der Orchestrator merged Plan 22-03 und Plan 22-04 zusammen am Ende von Wave 2, und die Union wird kompilieren. Die typsicheren Signaturen, gegen die ich gebaut habe (aus 22-03-PLAN.md `<context>/<interfaces>`):

- `class AnthropicClient(...) { suspend fun chatCompletionStreaming(request: AnthropicMessagesRequest, onProgress: (content, reasoning) -> Unit): String }`
- `data class AnthropicMessagesRequest(model, system, messages, maxTokens, temperature, stream)`
- `data class AnthropicMessage(role: String, content: String)`

## Verification State in diesem Worktree

- **Compile-Gate übersprungen** wegen Cross-Plan-Dep (Plan 22-03). Wird im Wave-2-Merge gegen die Union beider Worktrees verifiziert.
- **Grep-Gates aus `<verify>`-Blocks beider Tasks**: alle bestanden.
- **Plan-Level `<verification>`-Gates**: alle bestanden.
- **Plan-Level `<success_criteria>`**: alle erfüllt:
  - [x] AiClient-Port bleibt unverändert (D-20-Refactor respektiert)
  - [x] Anthropic-Adapter ist ein dünner Wrapper über AnthropicClient
  - [x] Provider-Switch ist via DispatchingAiClient zur Laufzeit ohne Restart möglich
  - [x] Use-Cases sind provider-agnostisch (kein `ProviderId`-Branch in Use-Case-Code)
  - [x] Bestehende Prompts (D-22-10) werden 1:1 ans top-level `system`-Field gepasst

## Deviations from Plan

None — Plan wurde 1:1 wie geschrieben ausgeführt. Beide `<action>`-Blöcke wurden wörtlich umgesetzt:

- Task 1 entspricht exakt dem Code-Beispiel im `<action>`-Block (Package, Imports, Klassen-Body, Kommentare).
- Task 2 entspricht exakt dem Code-Beispiel im `<action>`-Block; die Vorher/Nachher-Patches in den Use-Cases wurden 1:1 angewendet, mit zusätzlichem Erklärungskommentar oberhalb (D-22-04 / D-22-09 / Migration-Notiz).

## Hinweis zu AiGenerationManager (out of scope)

Wie im `<output>` des Plans festgehalten: `AiGenerationManager` bleibt unverändert. Es delegiert nur an die Use-Cases und kennt keinen Provider direkt. Eine Suche bestätigt das:

```bash
$ grep -rn "settingsRepository.aiBaseUrl\|settingsRepository.aiModel" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/
(no matches)
```

Falls AiGenerationManager irgendwo doch baseUrl/model lesen würde, wäre das hier sichtbar — ist es nicht. Die Phase-22-Migration ist auf Use-Case-Ebene komplett.

## Files Touched

| Datei | Änderung | Commit |
| --- | --- | --- |
| `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/AnthropicAiClient.kt` | NEU (+72 Zeilen) | `70c07db` |
| `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/DispatchingAiClient.kt` | NEU (+53 Zeilen) | `e5525e4` |
| `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt` | MOD (legacy reads → per-provider Map-Lookup) | `e5525e4` |
| `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt` | MOD (legacy reads → per-provider Map-Lookup) | `e5525e4` |

## API Contract Notes für Folge-Pläne

- **Plan 22-06 (Settings Migration):** Sobald die Migration `setActiveProvider(...)` + `setModel(...)` + `setBaseUrl(...)` für den von der legacy-Config inferred Provider setzt, lesen die migrierten Use-Cases genau diese Werte. Migration ist transparent.
- **Plan 22-07 (Koin Wiring):** Erwartet `single<AiClient> { DispatchingAiClient(get<SettingsRepository>(), get<OpenAiCompatibleAiClient>(), get<AnthropicAiClient>()) }`. Die zwei Sub-Adapter müssen als separate Koin-Bindings existieren (oder via Provider-Functions zugeführt werden).
- **Plan 22-08 (Settings UI):** `setActiveProvider(p)` ist der einzige Hebel für Provider-Switch — kein zusätzliches Notify-Mechanism nötig, weil `DispatchingAiClient.resolve()` jedes Mal frisch liest.
- **AiGenerationManager:** Bleibt provider-agnostisch und braucht keine Anpassung in dieser Phase.

## Self-Check: PASSED

- File `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/AnthropicAiClient.kt`: FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/DispatchingAiClient.kt`: FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt`: FOUND (modified)
- File `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt`: FOUND (modified)
- Commit `70c07db` (Task 1 — AnthropicAiClient): FOUND in `git log`
- Commit `e5525e4` (Task 2 — DispatchingAiClient + Use-Case migration): FOUND in `git log`
- All plan-specified `<verify>`-grep-gates: PASSED
- All plan-level `<verification>`-checks: PASSED
- Cross-plan gradle compile intentionally deferred to Wave-2 merge (Plan 22-03 dependency)
