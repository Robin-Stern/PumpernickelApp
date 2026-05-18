package com.pumpernickel.infrastructure.ai

/**
 * D-20-02 / D-20-06 (Smell 4) — generic AI port for OpenAI-compatible
 * `/chat/completions` endpoints. Lives under `infrastructure/ai/` because it
 * is a network-port abstraction, not a domain contract (per CONTEXT.md
 * §Specifics).
 *
 * Pure Kotlin only — NO `io.ktor.*` imports, NO `kotlinx.serialization.*`
 * imports, NO `com.pumpernickel.data.*` imports. The Ktor + DTO mapping is
 * the adapter's job (Plan 20-06: `OpenAICompatibleClient` becomes the
 * adapter implementing this port).
 *
 * Status quo this port abstracts (verified):
 *  - `WorkoutAiUseCase.callWithJsonSchema(...)` builds a `ChatRequest` with
 *    `responseFormat = ResponseFormat(type = "json_schema",
 *    jsonSchema = JsonSchemaSpec(name="WorkoutAiResponse",
 *    schema=workoutAiSchema(), strict=false))` and calls
 *    `client.chatCompletionStreaming(baseUrl, request, onProgress)`.
 *  - `RecipeAiUseCase.callWithJsonSchema(...)` follows the exact same shape
 *    with `name="RecipeAiResponse"`.
 *  - Both use-cases also have a `callWithJsonObject(...)` fallback that
 *    passes `responseFormat = ResponseFormat(type = "json_object")` (no
 *    schema) when the provider rejects `json_schema`.
 *
 * The port surface mirrors the two response-format paths the use-cases need:
 * primary structured-JSON path (`completeJsonSchema`) and the looser
 * json-object fallback (`completeJsonObject`). Schema bodies are passed as
 * raw JSON strings — `kotlinx.serialization.json.JsonElement` is
 * deliberately not exposed at the port boundary, the adapter parses the
 * string back into a `JsonElement` for the wire DTO.
 *
 * Auth: `apiKey` resolution stays inside the adapter (Plan 20-06 keeps
 * `SecureKeyStore` lookups in `OpenAICompatibleClient`) so the port surface
 * stays narrow. `baseUrl` and `model` flow from
 * `SettingsRepository.aiBaseUrl` / `SettingsRepository.aiModel` at call
 * sites — explicit parameters preserve testability of the adapter.
 */
interface AiClient {

    /**
     * Primary path (D-18-14) — POST `/chat/completions` with
     * `response_format = json_schema`. Streams the response and invokes
     * [onProgress] for every SSE delta chunk so callers can render a live
     * preview. Returns the accumulated `delta.content` as a raw String once
     * the stream terminates with `data: [DONE]`.
     *
     * @param baseUrl provider endpoint (e.g. `https://api.together.xyz/v1`)
     * @param model model identifier passed verbatim to the provider
     * @param systemPrompt content of the "system" role message
     * @param userPrompt content of the "user" role message
     * @param schema named JSON schema, `schemaJson` is the schema body as
     *               serialised JSON text — the adapter re-parses it
     * @param onProgress callback fired on each streaming chunk with
     *                   `(content, reasoning)` deltas; default no-op
     *
     * Adapter MUST translate provider error codes per D-18-08:
     *  - 401/403 → AuthOrQuota auth error
     *  - 429     → AuthOrQuota quota error
     *  - 400/422 → AuthOrQuota structured-output-not-supported (caller may
     *              choose to fall back to [completeJsonObject])
     *  - 5xx     → Provider error
     *  - network/timeout → Network / Timeout errors
     *  - empty content → SchemaInvalid
     *
     * Cancellation: implementations MUST propagate
     * `kotlinx.coroutines.CancellationException`.
     */
    suspend fun completeJsonSchema(
        baseUrl: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        schema: AiJsonSchema,
        onProgress: (content: String, reasoning: String) -> Unit = { _, _ -> }
    ): String

    /**
     * Fallback path (D-18-14) — POST `/chat/completions` with
     * `response_format = json_object` (no schema; the schema is expected to
     * be inlined in the system prompt by the caller). Same streaming
     * contract and error-mapping rules as [completeJsonSchema].
     */
    suspend fun completeJsonObject(
        baseUrl: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        onProgress: (content: String, reasoning: String) -> Unit = { _, _ -> }
    ): String
}

/**
 * Pure-Kotlin representation of a JSON-schema response-format spec. Mirrors
 * the wire DTO `com.pumpernickel.data.api.JsonSchemaSpec` but stays free of
 * `kotlinx.serialization.Serializable` and `kotlinx.serialization.json.JsonElement`
 * imports — the adapter (Plan 20-06) is responsible for the mapping.
 *
 * @param name schema identifier surfaced to the provider (e.g.
 *             `"WorkoutAiResponse"`, `"RecipeAiResponse"`)
 * @param schemaJson the JSON-schema body as serialised JSON text
 * @param strict whether the provider should enforce strict-mode validation;
 *               currently `false` everywhere because several providers
 *               reject union shapes under strict mode (see existing
 *               WorkoutAi / RecipeAi schema construction)
 */
data class AiJsonSchema(
    val name: String,
    val schemaJson: String,
    val strict: Boolean = false
)
