---
phase: 20-clean-architecture-refactor-dependency-rule-fixen-repository
plan: 01
subsystem: clean-architecture-foundation
tags: [refactor, port, narrow-port, ai, ports-and-adapters, wave-1]
requires: []
provides:
  - "com.pumpernickel.domain.repository.EarlyExitBudgetStore (narrow domain port for Plan 20-04)"
  - "com.pumpernickel.infrastructure.ai.AiClient (generic AI port for Plan 20-06)"
  - "com.pumpernickel.infrastructure.ai.AiJsonSchema (pure-Kotlin schema DTO)"
affects: []
tech-stack:
  added: []
  patterns:
    - "narrow-port (Smell 13)"
    - "ports-and-adapters (Smell 4)"
key-files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/EarlyExitBudgetStore.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/AiClient.kt
  modified: []
decisions:
  - "AiClient port lives under infrastructure/ai/ not domain/ai/ (D-20-02 default per CONTEXT.md §Specifics): the abstraction is a network port, the use-case is the domain logic that uses it."
  - "AiClient surface mirrors the existing two-path response_format handling (json_schema primary + json_object fallback, D-18-14) — two methods, not one generic, because the call sites today branch on responseFormat type."
  - "AiJsonSchema.schemaJson is a raw JSON String, not a JsonElement: keeps kotlinx.serialization.json out of the port surface. Adapter (Plan 20-06) re-parses the String into a JsonElement when building the wire DTO."
  - "apiKey is NOT a port parameter — adapter resolves it internally via SecureKeyStore as today. Keeps the port narrow."
  - "EarlyExitBudgetStore method/property names match the existing SettingsRepository API verbatim (earlyExits, incrementEarlyExitUsed) so the Plan 20-04 swap is purely mechanical."
metrics:
  duration: "~12 min"
  completed: 2026-05-18
  tasks: 2
  files_created: 2
  files_modified: 0
---

# Phase 20 Plan 01: Foundation — Narrow Ports + AiClient Interface Summary

Two new pure-interface files placed in `domain/repository/` and a brand-new
`infrastructure/ai/` package — narrow `EarlyExitBudgetStore` port + generic
`AiClient`/`AiJsonSchema` port surface — that downstream plans (20-04, 20-06)
will wire up. Zero behavioural change, zero imports of `data/*`, `io.ktor.*`,
or `kotlinx.serialization.*`.

## What was built

### Task 1 — `EarlyExitBudgetStore` narrow domain port

`shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/EarlyExitBudgetStore.kt`

```kotlin
interface EarlyExitBudgetStore {
    val earlyExits: Flow<EarlyExitBudget>
    suspend fun incrementEarlyExitUsed()
}
```

API derived empirically from the existing call-site:

```
$ grep -n "settingsRepository\." shared/src/commonMain/kotlin/com/pumpernickel/domain/geofence/EarlyExitTracker.kt
25:    val budget: Flow<EarlyExitBudget> = settingsRepository.earlyExits
33:        val current = settingsRepository.earlyExits.first()
35:        settingsRepository.incrementEarlyExitUsed()
```

Only those two members. The port mirrors them verbatim — no name changes,
no semantic shift. `EarlyExitBudget` is already a pure domain data class in
`domain/geofence/EarlyExitTracker.kt`, so referencing it does not introduce
a layer-rule violation (both files live under `domain/`).

### Task 2 — `AiClient` + `AiJsonSchema` infrastructure port

`shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/AiClient.kt`

```kotlin
interface AiClient {
    suspend fun completeJsonSchema(
        baseUrl: String, model: String,
        systemPrompt: String, userPrompt: String,
        schema: AiJsonSchema,
        onProgress: (content: String, reasoning: String) -> Unit = { _, _ -> }
    ): String

    suspend fun completeJsonObject(
        baseUrl: String, model: String,
        systemPrompt: String, userPrompt: String,
        onProgress: (content: String, reasoning: String) -> Unit = { _, _ -> }
    ): String
}

data class AiJsonSchema(
    val name: String,
    val schemaJson: String,
    val strict: Boolean = false
)
```

Signature derived from observed call sites:

- `WorkoutAiUseCase.callWithJsonSchema(...)` (lines 179–209) → primary
  `json_schema` path; `client.chatCompletionStreaming(baseUrl, request, onProgress)`
  with a `ChatRequest` carrying `ResponseFormat(type="json_schema",
  jsonSchema=JsonSchemaSpec(name, schema, strict=false))`.
- `WorkoutAiUseCase.callWithJsonObject(...)` (lines 211–230) → fallback
  with `ResponseFormat(type="json_object")` and schema-in-prompt.
- `RecipeAiUseCase` mirrors both paths exactly.

Two methods (not one generic) match this branching at the source of truth
instead of forcing the caller to pass an enum discriminator. Streaming
`onProgress` is preserved because both use-cases rely on per-chunk reasoning
visibility for the live-preview UI (Phase 18).

### Output

- `shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/EarlyExitBudgetStore.kt`
  (created — 49 lines incl. KDoc)
- `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/AiClient.kt`
  (created — 111 lines incl. KDoc)

Total: 2 files, 160 insertions, 0 deletions.

## Verification

| Check | Command | Result |
| --- | --- | --- |
| iOS X64 compile (full clib build over commonMain) | `./gradlew :shared:compileKotlinIosX64 --quiet` | **BUILD SUCCESSFUL** (exit 0, only pre-existing warnings in unrelated iOS files) |
| No `data.*` imports in narrow port | `grep -E "com\.pumpernickel\.data" .../EarlyExitBudgetStore.kt` | exit 1 (no match) |
| No `ktor` / `kotlinx.serialization` imports in AiClient | `grep -E "^import (io\.ktor\|kotlinx\.serialization)" .../AiClient.kt` | empty (only KDoc text mentions those names) |
| Both files exist | `find .../domain/repository .../infrastructure/ai -type f` | both listed |

### Pre-existing failure of `compileCommonMainKotlinMetadata`

The plan's primary verification command, `./gradlew :shared:compileCommonMainKotlinMetadata`,
fails on `shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt:49` with:

```
Object 'AppDatabaseConstructor' is not abstract and does not implement abstract member:
fun initialize(): T
```

This is a **pre-existing Room KMP code-generation quirk** unrelated to
Plan 20-01:

```
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
```

The `actual` is generated by KSP per platform target (Android, iosArm64,
iosX64, iosSimulatorArm64) but the platform-agnostic `compileCommonMainKotlinMetadata`
task runs before KSP-generated `actual`s are available. The task therefore
fails on every checkout regardless of Plan 20-01 content.

**Baseline test:** I temporarily moved both new files (`domain/repository/`
and `infrastructure/ai/`) outside the source tree and re-ran
`./gradlew :shared:compileCommonMainKotlinMetadata` — the failure persisted
identically. Files were restored verbatim immediately after.

This is **out of scope for Plan 20-01** (Rule 3 scope boundary — not caused
by this task's changes). The `compileKotlinIosX64` path successfully
compiles the same `commonMain` sources via the iOS-specific compilation
pipeline (where KSP `actual`s exist), proving Plan 20-01's files compile
correctly. Logged for future cleanup.

## Deviations from Plan

### Out-of-scope, documented only

- **[Rule 3 - Scope] Pre-existing `compileCommonMainKotlinMetadata` failure on `data/db/AppDatabase.kt:49`** — Room `AppDatabaseConstructor expect object` lacks a Common-Metadata-pipeline `actual`. Baseline-tested; not caused by Plan 20-01. Not fixed.

### Approach deviations

- **Plan's `<approach>` step 5 suggested a single generic `completeJson(...)`** with optional schema, and noted "two specialised methods is acceptable, document in commit" as fallback. I picked the two-method shape (`completeJsonSchema` / `completeJsonObject`) because the existing call sites at `WorkoutAiUseCase:179–230` and `RecipeAiUseCase:157–198` already split into two methods on `responseFormat.type`. Mirroring that split at the port surface keeps the Plan 20-06 adapter migration one-to-one with no merging logic. Documented in commit message.
- **`apiKey` omitted from the port surface** (plan step 5 listed it as a parameter). The existing `OpenAICompatibleClient.chatCompletionStreaming(baseUrl, request, onProgress)` does not take an `apiKey` argument — it resolves the key internally via `SecureKeyStore`. Keeping that abstraction inside the adapter keeps the port narrower and preserves the current auth model; Plan 20-06 will keep this same boundary.

## Decisions Made

1. **AiClient placement: `infrastructure/ai/`** — per D-20-02 / CONTEXT.md §Specifics default. The abstraction is a network port; the use-case (in `domain/ai/`) consumes it.
2. **Two-method AiClient surface** instead of one generic — matches the two existing call-paths (`json_schema` primary, `json_object` fallback per D-18-14) without forcing callers to encode the path via a discriminator argument.
3. **`AiJsonSchema.schemaJson: String`** — keeps `kotlinx.serialization.json.JsonElement` outside the port boundary. The adapter (Plan 20-06) re-parses the string when building the wire `JsonSchemaSpec` DTO.
4. **`apiKey` not on the port surface** — adapter resolves via `SecureKeyStore`, matching status quo.
5. **`EarlyExitBudgetStore` API verbatim from current call site** — `earlyExits` + `incrementEarlyExitUsed` so the Plan 20-04 swap is a single-line dependency change in `EarlyExitTracker`'s constructor.

## Self-Check: PASSED

- File `shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/EarlyExitBudgetStore.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/AiClient.kt` — FOUND
- Commit `18e8dd5` — FOUND (`git log --oneline --all | grep 18e8dd5` → `18e8dd5 refactor(20-01): add EarlyExitBudgetStore + AiClient port files`)
- `:shared:compileKotlinIosX64` — BUILD SUCCESSFUL (exit 0)
- `grep com.pumpernickel.data` in EarlyExitBudgetStore.kt — empty (no `data/*` import)
- `grep '^import (io\.ktor\|kotlinx\.serialization)'` in AiClient.kt — empty (no forbidden imports; KDoc text mentions are not imports)
