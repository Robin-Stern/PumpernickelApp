---
phase: 18-ai-features-f6-workout-generation-f8-meal-generation-byok-op
plan: 02
type: execute
wave: 1
depends_on: []
files_modified:
  - shared/src/commonMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.kt
  - shared/src/androidMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.android.kt
  - shared/src/iosMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.ios.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/data/api/AiChatDto.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt
autonomous: true
requirements:
  - REQ-AI-06
  - REQ-AI-07
  - REQ-AI-08
user_setup: []

must_haves:
  truths:
    - "An OpenAICompatibleClient can POST a chat completion request with Bearer auth to a configurable base URL and 60s timeout"
    - "The client never holds the API key as a class field — it reads via a suspend lambda per request"
    - "All five AiError classes (Timeout, Network, AuthOrQuota, Provider, SchemaInvalid, Cancelled) exist with a fromThrowable mapping"
    - "HttpTimeout (60s) and ContentNegotiation(Json{ignoreUnknownKeys=true}) are installed on the shared HttpClient — both Android (OkHttp) and iOS (Darwin) actuals build successfully"
    - "The client supports both response_format=json_schema (primary) and response_format=json_object (fallback) request shapes"
  artifacts:
    - path: "shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt"
      provides: "Sealed AiError hierarchy + fromThrowable companion"
      contains: "sealed class AiError"
      min_lines: 30
    - path: "shared/src/commonMain/kotlin/com/pumpernickel/data/api/AiChatDto.kt"
      provides: "ChatRequest/ChatMessage/ResponseFormat/ChatResponse @Serializable DTOs"
      contains: "@Serializable"
      min_lines: 40
    - path: "shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt"
      provides: "Suspend chatCompletion(request, baseUrl) that returns ChatResponse"
      contains: "class OpenAICompatibleClient"
      min_lines: 40
    - path: "shared/src/commonMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.kt"
      provides: "expect fun createHttpClient with HttpTimeout + ContentNegotiation installed in actuals"
      contains: "expect fun createHttpClient"
  key_links:
    - from: "OpenAICompatibleClient.chatCompletion"
      to: "ChatRequest serialization"
      via: "setBody + ContentType.Application.Json"
      pattern: "setBody\\(request\\)"
    - from: "OpenAICompatibleClient.chatCompletion"
      to: "AiError.fromThrowable"
      via: "try/catch wrap"
      pattern: "AiError\\.fromThrowable"
    - from: "HttpClientFactory.android.kt"
      to: "HttpTimeout(60_000)"
      via: "install(HttpTimeout)"
      pattern: "requestTimeoutMillis"
---

<objective>
Build the shared HTTPS transport for the AI features. Four concerns:

1. Extend the existing `HttpClientFactory` (commonMain `expect` + Android/iOS `actual`s) to install `ContentNegotiation(Json{ignoreUnknownKeys=true})` and `HttpTimeout(requestTimeoutMillis = 60_000)` on the shared `HttpClient` — non-breaking for `OpenFoodFactsApi` (it already parses with its own `Json`, so the addition is additive).
2. Add `AiChatDto.kt` with `@Serializable` DTOs mirroring OpenAI's chat-completions wire shape (`messages`, `response_format` covering both `json_schema` and `json_object` per D-18-14, `model`, `temperature`, `ChatResponse` with `choices[].message.content`).
3. Add `OpenAICompatibleClient.kt` — a Ktor-backed suspend client that POSTs to `{baseUrl}/chat/completions` with `Authorization: Bearer ${apiKey}`, reading the key just-in-time via a `suspend () -> String?` lambda so the class never holds the secret. Wraps Ktor exceptions into `AiError` per D-18-08 (Timeout / Network / AuthOrQuota / Provider).
4. Add `AiError.kt` — sealed class covering the five error states from D-18-08 plus `Cancelled`, with a `fromThrowable(t: Throwable): AiError` companion mapping Ktor exceptions.

Purpose: All AI flows (F6 + F8 + AI Settings) ride on this transport. Errors are handled uniformly; auth keys never leak into class state. Implements REQ-AI-06 (BYOK transport), REQ-AI-08 (timeout + per-class error UX) at the data layer.
Output: Six files (3 modified factory triple + 3 new) ready for downstream plans to inject and consume.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/REQUIREMENTS-ai-features.md
@.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-CONTEXT.md
@.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-PATTERNS.md
@shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsApi.kt
@shared/src/commonMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.kt
@shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/UnlockResult.kt

<interfaces>
HttpClientFactory.kt (commonMain, current 5 lines):
```
package com.pumpernickel.data.api
import io.ktor.client.HttpClient
expect fun createHttpClient(): HttpClient
```

OpenFoodFactsApi pattern (canonical Ktor + Json idiom in this project):
```
class OpenFoodFactsApi(private val client: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true }
    suspend fun lookupBarcode(barcode: String): OpenFoodFactsResponse {
        val responseText = client.get("https://...").bodyAsText()
        return json.decodeFromString(responseText)
    }
}
```

OpenAI chat-completions wire shape:
```
POST {baseUrl}/chat/completions
Authorization: Bearer ${apiKey}
Content-Type: application/json

{ "model": "gpt-4o-mini",
  "messages": [{"role": "system", "content": "..."}, {"role": "user", "content": "..."}],
  "response_format": { "type": "json_schema", "json_schema": { "name": "...", "schema": {...}, "strict": true } },
  "temperature": 0.7 }
```
Response: `{"choices":[{"message":{"role":"assistant","content":"..."},"finish_reason":"stop"}],"usage":{...}}`

UnlockResult.kt sealed-class precedent:
```
sealed class UnlockResult {
    object Success : UnlockResult()
    object UserCancelled : UnlockResult()
    data class Failed(val message: String) : UnlockResult()
}
```
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Create AiError sealed class with fromThrowable companion</name>
  <files>shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt</files>
  <read_first>
    shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/UnlockResult.kt,
    .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-CONTEXT.md,
    .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-PATTERNS.md
  </read_first>
  <action>
Create the directory `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/` (mkdir as needed) and write `AiError.kt` with the following content:

```kotlin
package com.pumpernickel.domain.ai

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.utils.io.errors.IOException

/**
 * Five canonical AI error classes per D-18-08 + Cancelled sentinel for D-18-16
 * (user pressed Cancel during in-flight generation — no toast).
 *
 * Mapping (D-18-08):
 *   1. Timeout       — request exceeded 60s (D-18-16)
 *   2. Network       — DNS / connection / IO failure
 *   3. AuthOrQuota   — HTTP 4xx (invalid key, quota exhausted, malformed request)
 *   4. Provider      — HTTP 5xx (provider-side problem)
 *   5. SchemaInvalid — model returned non-JSON or JSON that fails our schema
 *                      (after the one auto-retry per D-18-14)
 *   + Cancelled      — coroutine cancelled by user (no error UI)
 */
sealed class AiError : Throwable() {
    object Timeout : AiError()
    object Network : AiError()
    data class AuthOrQuota(val httpStatus: Int) : AiError()
    data class Provider(val httpStatus: Int) : AiError()
    data class SchemaInvalid(val detail: String) : AiError()
    object Cancelled : AiError()

    companion object {
        /**
         * Maps a Throwable from the Ktor stack into an AiError. CancellationException
         * MUST be rethrown by callers BEFORE invoking this — coroutine cancellation
         * must propagate, not be wrapped.
         */
        fun fromThrowable(t: Throwable): AiError = when (t) {
            is HttpRequestTimeoutException,
            is SocketTimeoutException,
            is ConnectTimeoutException -> Timeout
            is ClientRequestException -> AuthOrQuota(t.response.status.value)
            is ServerResponseException -> Provider(t.response.status.value)
            is IOException -> Network
            else -> Network // unknown transport failure — surfaces as the generic network state
        }
    }
}
```

Notes:
- `AiError : Throwable()` so it can be thrown out of suspend functions and caught with normal try/catch — Ktor's plugins do the same.
- Do NOT catch `CancellationException` inside `fromThrowable`. The OpenAICompatibleClient task catches and rethrows CancellationException BEFORE delegating to `fromThrowable`.
- The exact import paths above correspond to Ktor 2.x / 3.x standard plugin packages — confirm by checking `shared/build.gradle.kts` for the Ktor version. If imports differ, adjust to the version in use (e.g., `io.ktor.client.plugins.*`).
  </action>
  <verify>
    <automated>grep -E "sealed class AiError" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt && grep -E "fun fromThrowable" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt</automated>
  </verify>
  <acceptance_criteria>
    - File `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt` exists.
    - `grep -c "sealed class AiError" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt` returns exactly `1`.
    - `grep -c "object Timeout" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt` returns exactly `1`.
    - `grep -c "object Network" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt` returns exactly `1`.
    - `grep -c "data class AuthOrQuota" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt` returns exactly `1`.
    - `grep -c "data class Provider" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt` returns exactly `1`.
    - `grep -c "data class SchemaInvalid" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt` returns exactly `1`.
    - `grep -c "object Cancelled" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt` returns exactly `1`.
    - `grep -c "fun fromThrowable" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt` returns exactly `1`.
    - `grep -c "ClientRequestException" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt` returns at least `2` (1 import + 1 use).
    - `grep -c "ServerResponseException" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt` returns at least `2`.
  </acceptance_criteria>
  <done>AiError.kt compiles, exposes the five AI error classes plus Cancelled, and maps Ktor exceptions through fromThrowable.</done>
</task>

<task type="auto">
  <name>Task 2: Extend HttpClientFactory with HttpTimeout + ContentNegotiation in both actuals</name>
  <files>
    shared/src/androidMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.android.kt,
    shared/src/iosMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.ios.kt
  </files>
  <read_first>
    shared/src/commonMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.kt,
    shared/src/androidMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.android.kt,
    shared/src/iosMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.ios.kt,
    shared/build.gradle.kts
  </read_first>
  <action>
Modify both `actual` files (NOT the commonMain `expect` — its declaration `expect fun createHttpClient(): HttpClient` stays as-is).

**Android actual** (`HttpClientFactory.android.kt`) — replace the body with:

```kotlin
package com.pumpernickel.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

actual fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 60_000  // D-18-16 — 60s hard timeout
    }
}
```

**iOS actual** (`HttpClientFactory.ios.kt`) — replace the body with the same shape using the Darwin engine:

```kotlin
package com.pumpernickel.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

actual fun createHttpClient(): HttpClient = HttpClient(Darwin) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 60_000
    }
}
```

Verify Ktor dependencies in `shared/build.gradle.kts`. Required artifacts (confirm by grepping the build file):
- `io.ktor:ktor-client-core` (already present from Phase 17 stack)
- `io.ktor:ktor-client-okhttp` (Android, already present)
- `io.ktor:ktor-client-darwin` (iOS, already present)
- `io.ktor:ktor-client-content-negotiation` (commonMain — verify; add if missing)
- `io.ktor:ktor-serialization-kotlinx-json` (commonMain — verify; add if missing)

If `ktor-client-content-negotiation` or `ktor-serialization-kotlinx-json` is missing, add it to `commonMain.dependencies` in `shared/build.gradle.kts` using the same Ktor version already pinned for `ktor-client-core`.

Do NOT change the `expect` declaration in commonMain. Do NOT introduce a separate `createAiHttpClient()` — extend the single shared client per the PATTERNS document.
  </action>
  <verify>
    <automated>grep -E "install\(HttpTimeout\)" shared/src/androidMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.android.kt && grep -E "install\(ContentNegotiation\)" shared/src/iosMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.ios.kt && grep -E "requestTimeoutMillis = 60_000" shared/src/androidMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.android.kt</automated>
  </verify>
  <acceptance_criteria>
    - `grep -c "install(HttpTimeout)" shared/src/androidMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.android.kt` returns exactly `1`.
    - `grep -c "install(ContentNegotiation)" shared/src/androidMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.android.kt` returns exactly `1`.
    - `grep -c "install(HttpTimeout)" shared/src/iosMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.ios.kt` returns exactly `1`.
    - `grep -c "install(ContentNegotiation)" shared/src/iosMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.ios.kt` returns exactly `1`.
    - `grep -c "requestTimeoutMillis = 60_000" shared/src/androidMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.android.kt` returns exactly `1`.
    - `grep -c "requestTimeoutMillis = 60_000" shared/src/iosMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.ios.kt` returns exactly `1`.
    - `grep -c "ignoreUnknownKeys = true" shared/src/androidMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.android.kt` returns exactly `1`.
    - `grep -c "ktor-client-content-negotiation\|content-negotiation" shared/build.gradle.kts` returns at least `1`.
    - `grep -c "ktor-serialization-kotlinx-json\|serialization-kotlinx-json" shared/build.gradle.kts` returns at least `1`.
    - The expect declaration in commonMain is unchanged: `grep -c "expect fun createHttpClient" shared/src/commonMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.kt` returns exactly `1`.
  </acceptance_criteria>
  <done>Both actuals install ContentNegotiation(Json) + HttpTimeout(60_000); the expect declaration is untouched; required Ktor dependencies are present.</done>
</task>

<task type="auto">
  <name>Task 3: Create AiChatDto and OpenAICompatibleClient</name>
  <files>
    shared/src/commonMain/kotlin/com/pumpernickel/data/api/AiChatDto.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt
  </files>
  <read_first>
    shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsApi.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsDto.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt,
    .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-PATTERNS.md
  </read_first>
  <action>
**File 1: AiChatDto.kt** — `@Serializable` data classes mirroring OpenAI's `/chat/completions` wire shape. Use `kotlinx.serialization.json.JsonElement` for the schema body so callers can pass any schema without coupling DTOs to specific schemas.

```kotlin
package com.pumpernickel.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerialName("response_format") val responseFormat: ResponseFormat? = null,
    val temperature: Double = 0.7,
    @SerialName("max_tokens") val maxTokens: Int? = null
)

@Serializable
data class ChatMessage(
    val role: String,            // "system" | "user" | "assistant"
    val content: String
)

/**
 * D-18-14 — primary path uses type=json_schema; fallback uses type=json_object
 * with the schema embedded in the system prompt instead.
 */
@Serializable
data class ResponseFormat(
    val type: String,            // "json_schema" | "json_object" | "text"
    @SerialName("json_schema") val jsonSchema: JsonSchemaSpec? = null
)

@Serializable
data class JsonSchemaSpec(
    val name: String,
    val schema: JsonElement,     // the actual schema body
    val strict: Boolean = true
)

@Serializable
data class ChatResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<ChatChoice> = emptyList(),
    val usage: ChatUsage? = null
)

@Serializable
data class ChatChoice(
    val index: Int = 0,
    val message: ChatMessage,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ChatUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)
```

**File 2: OpenAICompatibleClient.kt** — the suspend client. Reads the API key just-in-time via a `suspend () -> String?` lambda so the class never holds the secret. Mirrors `OpenFoodFactsApi`'s explicit `bodyAsText()` + `Json.decodeFromString` path so the 64KB size cap can be enforced before deserialisation.

```kotlin
package com.pumpernickel.data.api

import com.pumpernickel.domain.ai.AiError
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * Single OpenAI-compatible HTTPS client (REQ-AI-06 — single BYOK provider).
 * Works against OpenAI, Together.AI, OpenRouter, Groq, and any compatible
 * endpoint by changing the base URL only (D-18-06).
 *
 * The API key is NEVER held as a class field. It is read just-in-time per
 * request via the keyProvider lambda — pass `{ secureKeyStore.readApiKey() }`
 * at construction time. If the key is null at request time, this method
 * throws AiError.AuthOrQuota(401) before any HTTP call.
 *
 * Hard timeout: 60s per D-18-16 (also installed at HttpClientFactory level
 * — defence in depth).
 *
 * Hard response-size cap: 64 KB. Larger responses surface as
 * AiError.SchemaInvalid("response exceeded 64KB cap").
 */
class OpenAICompatibleClient(
    private val client: HttpClient,
    private val keyProvider: suspend () -> String?
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun chatCompletion(
        baseUrl: String,
        request: ChatRequest
    ): ChatResponse {
        val key = keyProvider() ?: throw AiError.AuthOrQuota(401)
        val normalisedBase = baseUrl.trimEnd('/')
        val url = "$normalisedBase/chat/completions"
        try {
            val response = client.post(url) {
                header("Authorization", "Bearer $key")
                contentType(ContentType.Application.Json)
                timeout {
                    requestTimeoutMillis = 60_000  // D-18-16
                }
                setBody(request)
            }
            val responseText = response.bodyAsText()
            if (responseText.length > 64 * 1024) {
                throw AiError.SchemaInvalid("response exceeded 64KB cap")
            }
            return json.decodeFromString<ChatResponse>(responseText)
        } catch (ce: CancellationException) {
            throw ce  // do not wrap — let coroutine cancellation propagate
        } catch (ai: AiError) {
            throw ai
        } catch (t: Throwable) {
            throw AiError.fromThrowable(t)
        }
    }
}
```

Implementation notes for the executor:
- The `setBody(request)` call relies on ContentNegotiation (installed by Task 2) to serialise the `ChatRequest` to JSON automatically. If for any reason ContentNegotiation isn't picked up at runtime, fall back to `setBody(json.encodeToString(ChatRequest.serializer(), request))` (manual encode).
- The key is dereferenced at request time only and never stored on `this`. There MUST NOT be any field of type `String` named `key`, `apiKey`, or similar holding the value — keep it as a local `val` inside `chatCompletion`.
- Bearer auth — never log the value of `key`. There MUST NOT be `println(key)` or any `key.toString()`-style call in this file. Logs about the request must use placeholders like `Bearer ***`.
- The `timeout {...}` block on the request is in addition to the client-level `HttpTimeout` plugin (defence in depth — explicit override allowed for callers that need shorter timeouts later).
  </action>
  <verify>
    <automated>grep -E "class OpenAICompatibleClient" shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt && grep -E "suspend fun chatCompletion" shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt && grep -E "Authorization" shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt && grep -E "AiError.fromThrowable" shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt && grep -E "@Serializable" shared/src/commonMain/kotlin/com/pumpernickel/data/api/AiChatDto.kt</automated>
  </verify>
  <acceptance_criteria>
    - `grep -c "@Serializable" shared/src/commonMain/kotlin/com/pumpernickel/data/api/AiChatDto.kt` returns at least `7` (one per data class).
    - `grep -c "data class ChatRequest" shared/src/commonMain/kotlin/com/pumpernickel/data/api/AiChatDto.kt` returns exactly `1`.
    - `grep -c "data class ChatMessage" shared/src/commonMain/kotlin/com/pumpernickel/data/api/AiChatDto.kt` returns exactly `1`.
    - `grep -c "data class ResponseFormat" shared/src/commonMain/kotlin/com/pumpernickel/data/api/AiChatDto.kt` returns exactly `1`.
    - `grep -c "data class JsonSchemaSpec" shared/src/commonMain/kotlin/com/pumpernickel/data/api/AiChatDto.kt` returns exactly `1`.
    - `grep -c "data class ChatResponse" shared/src/commonMain/kotlin/com/pumpernickel/data/api/AiChatDto.kt` returns exactly `1`.
    - `grep -c "@SerialName(\"response_format\")" shared/src/commonMain/kotlin/com/pumpernickel/data/api/AiChatDto.kt` returns exactly `1`.
    - `grep -c "class OpenAICompatibleClient" shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt` returns exactly `1`.
    - `grep -c "suspend fun chatCompletion" shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt` returns exactly `1`.
    - `grep -c "keyProvider: suspend () -> String?" shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt` returns exactly `1` (key never held as field).
    - `grep -c "Authorization" shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt` returns exactly `1`.
    - `grep -c "Bearer " shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt` returns exactly `1`.
    - `grep -c "AiError.fromThrowable" shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt` returns exactly `1`.
    - `grep -c "throw AiError.AuthOrQuota(401)" shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt` returns exactly `1` (null-key branch).
    - `grep -c "throw ce" shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt` returns exactly `1` (CancellationException rethrow).
    - `grep -ci "println" shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt` returns exactly `0` (no debug-print of key or any other content).
    - `grep -E "private val (apiKey|key) *:" shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt | wc -l | tr -d ' '` returns exactly `0` (key never stored as field).
    - `grep -c "64 \* 1024" shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt` returns exactly `1` (response-size cap enforced).
  </acceptance_criteria>
  <done>AiChatDto contains the 6 @Serializable wire DTOs; OpenAICompatibleClient exposes chatCompletion that posts with Bearer auth, enforces a 64 KB cap, wraps errors via AiError.fromThrowable, never stores the key, and never prints anything.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| App → External LLM provider | User-supplied API key crosses to a third-party HTTPS endpoint |
| LLM response → App parser | Untrusted JSON (potentially adversarial / hallucinated) crosses into deserialiser |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-18-02-01 | Information disclosure | API key in logs | mitigate | Acceptance criteria: `grep -ci "println" OpenAICompatibleClient.kt` returns 0. Key passed via suspend lambda, dereferenced as local val, not stored on `this`. |
| T-18-02-02 | Tampering | Adversarial LLM response | mitigate | Hard 64 KB response-size cap enforced before deserialisation; ContentNegotiation parses with `ignoreUnknownKeys = true` so unexpected fields don't crash; downstream plans (06, 08) re-validate parsed JSON against feature schemas before persistence. |
| T-18-02-03 | Denial of service | Slow-loris / hung connection | mitigate | `HttpTimeout(requestTimeoutMillis = 60_000)` at both client level (HttpClientFactory) and per-request (`timeout { requestTimeoutMillis = 60_000 }` in chatCompletion). |
| T-18-02-04 | Spoofing | Non-HTTPS base URL accepted | mitigate | Mitigation belongs in Plan 04 / 05 at the Settings input layer (validate `https://` prefix on save). This plan accepts whatever `baseUrl` callers pass; the boundary is at Settings input. |
| T-18-02-05 | Repudiation | Untracked failed request | accept | Errors surface to the user via per-class UI (Plan 07 / 09); no audit logging required for a prototype per D-AI-07. |
| T-18-02-06 | Elevation of privilege | LLM tool-use mutating local data | mitigate | OUT OF SCOPE per D-AI-01 (no tool-use); the client only sends/receives JSON — no function-calling fields are ever added to ChatRequest. |
</threat_model>

<verification>
- `AiError.kt` exists with the five canonical error classes plus `Cancelled` and a `fromThrowable` companion mapping Ktor exceptions.
- `HttpClientFactory.android.kt` and `.ios.kt` install `ContentNegotiation(Json{ignoreUnknownKeys=true,encodeDefaults=true})` and `HttpTimeout(60_000)`.
- `AiChatDto.kt` declares the six wire DTOs.
- `OpenAICompatibleClient.kt` exposes `suspend fun chatCompletion(baseUrl, request): ChatResponse`, never holds the key as a field, enforces 64 KB cap, and wraps errors via `AiError.fromThrowable`.
- `./gradlew :shared:assembleDebug` and `:shared:linkDebugFrameworkIosSimulatorArm64` (or similar Apple target) both succeed — both actuals compile with the new plugin installs.
</verification>

<success_criteria>
- Shared HTTPS transport ready for downstream AI plans (REQ-AI-06).
- Hard 60s timeout enforced (REQ-AI-08 / D-18-16).
- Five distinct error classes available for per-class user messaging (D-18-08).
- API key never stored as class state; key-less calls fail fast with AuthOrQuota(401) (REQ-AI-06 / D-18-07).
</success_criteria>

<output>
After completion, create `.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-02-SUMMARY.md` with: list of files created/modified, the AiError class hierarchy, the HttpTimeout/ContentNegotiation plugin install confirmation, and the chatCompletion signature. One paragraph maximum per section.
</output>
