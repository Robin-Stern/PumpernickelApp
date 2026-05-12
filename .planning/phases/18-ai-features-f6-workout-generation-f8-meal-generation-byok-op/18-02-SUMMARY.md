---
phase: 18-ai-features-f6-workout-generation-f8-meal-generation-byok-op
plan: "02"
subsystem: ai-transport
tags: [ktor, openai, error-handling, byok, http-client]
dependency_graph:
  requires: []
  provides:
    - AiError sealed class with fromThrowable companion (domain/ai)
    - OpenAICompatibleClient suspend chatCompletion (data/api)
    - AiChatDto @Serializable wire types (data/api)
    - HttpClientFactory with ContentNegotiation + HttpTimeout (both actuals)
  affects:
    - shared HttpClient used by OpenFoodFactsApi (additive plugins, non-breaking)
tech_stack:
  added: []
  patterns:
    - Ktor 3.x HttpTimeout(60_000) + ContentNegotiation(Json) installed on shared HttpClient
    - suspend () -> String? key provider lambda — key never stored as class field
    - kotlinx.io.IOException for Ktor 3.x (not io.ktor.utils.io.errors.IOException)
    - 64KB response-size cap enforced before deserialization
    - CancellationException rethrown before AiError.fromThrowable wrapping
key_files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/api/AiChatDto.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt
  modified:
    - shared/src/androidMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.android.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.ios.kt
decisions:
  - "Used kotlinx.io.IOException (not io.ktor.utils.io.errors.IOException) — Ktor 3.x migrated to kotlinx-io"
  - "Both ktor-client-content-negotiation and ktor-serialization-kotlinx-json were already in commonMain.dependencies — no new build.gradle.kts changes needed"
  - "OpenAICompatibleClient uses explicit bodyAsText() + json.decodeFromString rather than ContentNegotiation auto-deserialize to allow 64KB cap check before parse"
metrics:
  duration: "2m 12s"
  completed: "2026-05-07"
  tasks: 3
  files_created: 3
  files_modified: 2
---

# Phase 18 Plan 02: OpenAI Client and Error — Summary

**One-liner:** Ktor-backed OpenAI-compatible HTTPS transport with sealed AiError hierarchy, BYOK key-provider lambda, 64KB response cap, and 60s timeout installed on both Android (OkHttp) and iOS (Darwin) HttpClient actuals.

## Files Created / Modified

Three new files and two modified actuals implement the shared AI transport layer. `AiError.kt` provides the sealed hierarchy; `AiChatDto.kt` provides the @Serializable wire types; `OpenAICompatibleClient.kt` provides the suspend transport; the two factory actuals gain `ContentNegotiation` + `HttpTimeout` plugins.

## AiError Class Hierarchy

`sealed class AiError : Throwable()` with six cases: `Timeout` (60s exceeded), `Network` (IOException / DNS / connection), `AuthOrQuota(httpStatus)` (4xx), `Provider(httpStatus)` (5xx), `SchemaInvalid(detail)` (parse/validation failure), and `Cancelled` (user-initiated, no toast). The `fromThrowable` companion maps Ktor 3.x exception types: `HttpRequestTimeoutException`, `SocketTimeoutException`, `ConnectTimeoutException` → `Timeout`; `ClientRequestException` → `AuthOrQuota`; `ServerResponseException` → `Provider`; `kotlinx.io.IOException` → `Network`.

## HttpTimeout + ContentNegotiation Confirmation

Both `HttpClientFactory.android.kt` (OkHttp engine) and `HttpClientFactory.ios.kt` (Darwin engine) now install `ContentNegotiation(Json { ignoreUnknownKeys = true; encodeDefaults = true })` and `HttpTimeout(requestTimeoutMillis = 60_000)`. The `expect` declaration in `commonMain` is unchanged. Required Ktor dependencies (`ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json`) were already present in `commonMain.dependencies` — no `build.gradle.kts` changes were needed.

## chatCompletion Signature

```kotlin
suspend fun chatCompletion(baseUrl: String, request: ChatRequest): ChatResponse
```

Key properties: API key read just-in-time via `suspend () -> String?` constructor lambda (never stored on `this`); null key throws `AiError.AuthOrQuota(401)` before any HTTP call; `Authorization: Bearer $key` header set per request; per-request `timeout { requestTimeoutMillis = 60_000 }` as defence-in-depth over the factory-level plugin; 64KB cap checked on raw response text before `json.decodeFromString`; `CancellationException` rethrown before `AiError.fromThrowable`; no `println` or log call involving the key (T-18-02-01 mitigated).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Corrected IOException import for Ktor 3.x**
- **Found during:** Task 1 implementation
- **Issue:** Plan specified `import io.ktor.utils.io.errors.IOException` (Ktor 2.x path). Ktor 3.x migrated to `kotlinx.io.IOException` — using the old import would cause a compilation error.
- **Fix:** Used `kotlinx.io.IOException` which matches what Ktor 3.x's own `ConnectTimeoutException` and `SocketTimeoutException` extend (verified in ktor-client-core-3.4.2 sources).
- **Files modified:** `AiError.kt`
- **Commit:** ecc95e6

None other — plan executed as written.

## Threat Flags

No new threat surface beyond what is documented in the plan's `<threat_model>`. All three T-18-02-01/02/03 mitigations are implemented: no key logging (0 println calls), 64KB response cap before deserialize, 60s timeout at both factory and per-request level.

## Self-Check: PASSED

- `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt` — FOUND
- `shared/src/commonMain/kotlin/com/pumpernickel/data/api/AiChatDto.kt` — FOUND
- `shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt` — FOUND
- Commit ecc95e6 (AiError) — FOUND
- Commit 6d51977 (HttpClientFactory) — FOUND
- Commit a1e96c8 (AiChatDto + OpenAICompatibleClient) — FOUND
