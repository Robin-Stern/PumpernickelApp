package com.pumpernickel.infrastructure.ai

import com.pumpernickel.data.api.ChatMessage
import com.pumpernickel.data.api.ChatRequest
import com.pumpernickel.data.api.JsonSchemaSpec
import com.pumpernickel.data.api.OpenAICompatibleClient
import com.pumpernickel.data.api.ResponseFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Phase 20 Plan 07 (Smell 4) — concrete adapter implementing the [AiClient]
 * port (Plan 20-01) by delegating to the Ktor-based [OpenAICompatibleClient]
 * in `data/api/`.
 *
 * Responsibility split:
 *  - **Port surface** (`AiClient.completeJsonSchema` / `completeJsonObject`)
 *    is Ktor-free and uses only pure-Kotlin types (`String`, `AiJsonSchema`,
 *    callback).
 *  - **Adapter (this class)** translates port arguments into the wire DTOs
 *    (`ChatRequest`, `ResponseFormat`, `JsonSchemaSpec`) and parses the raw
 *    `AiJsonSchema.schemaJson` String back into a `JsonElement` for the
 *    wire body. It catches/re-throws `AiError` 1:1 — error mapping itself
 *    happens inside [OpenAICompatibleClient].
 *
 * Behaviour preserved verbatim from the previous direct call-sites in
 * `WorkoutAiUseCase` / `RecipeAiUseCase` (temperature 0.7, max_tokens 4096,
 * SSE streaming with [onProgress]). Auth (`apiKey`) is resolved inside
 * `OpenAICompatibleClient` via the `keyProvider` lambda configured in Koin
 * — the port deliberately does NOT carry it.
 */
class OpenAiCompatibleAiClient(
    private val client: OpenAICompatibleClient,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) : AiClient {

    override suspend fun completeJsonSchema(
        baseUrl: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        schema: AiJsonSchema,
        onProgress: (content: String, reasoning: String) -> Unit
    ): String {
        val parsedSchema: JsonElement = json.parseToJsonElement(schema.schemaJson)
        val request = ChatRequest(
            model = model,
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = userPrompt)
            ),
            responseFormat = ResponseFormat(
                type = "json_schema",
                jsonSchema = JsonSchemaSpec(
                    name = schema.name,
                    schema = parsedSchema,
                    strict = schema.strict
                )
            ),
            temperature = 0.7,
            // Reasoning models (Qwen QwQ, DeepSeek R1) burn thousands of tokens
            // on chain-of-thought before emitting content; 4096 keeps both
            // bounded and sufficient for our schema. Matches the previous
            // direct call-sites in WorkoutAiUseCase / RecipeAiUseCase.
            maxTokens = 4096
        )
        return client.chatCompletionStreaming(baseUrl, request, onProgress)
    }

    override suspend fun completeJsonObject(
        baseUrl: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        onProgress: (content: String, reasoning: String) -> Unit
    ): String {
        val request = ChatRequest(
            model = model,
            messages = listOf(
                ChatMessage(
                    role = "system",
                    content = systemPrompt +
                        "\n\nReturn only JSON matching the schema in the system prompt body."
                ),
                ChatMessage(role = "user", content = userPrompt)
            ),
            responseFormat = ResponseFormat(type = "json_object"),
            temperature = 0.7,
            maxTokens = 4096
        )
        return client.chatCompletionStreaming(baseUrl, request, onProgress)
    }
}
