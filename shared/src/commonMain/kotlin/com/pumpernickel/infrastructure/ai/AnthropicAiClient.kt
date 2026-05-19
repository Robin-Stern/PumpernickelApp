package com.pumpernickel.infrastructure.ai

import com.pumpernickel.data.api.AnthropicClient
import com.pumpernickel.data.api.AnthropicMessage
import com.pumpernickel.data.api.AnthropicMessagesRequest

/**
 * D-22-03 / D-22-10 — concrete adapter implementing the [AiClient] port via
 * the Ktor-based [AnthropicClient] in `data/api/`. Symmetric with
 * [OpenAiCompatibleAiClient].
 *
 * Anthropic-specific deviations from the port contract:
 *  - **baseUrl is IGNORED** — Anthropic endpoint is fixed at
 *    `https://api.anthropic.com/v1/messages` (D-22-09). The port still
 *    accepts a baseUrl argument so [DispatchingAiClient] can pass-through the
 *    OpenAI/Together base URL; the Anthropic adapter just doesn't look at it.
 *  - **schema is IGNORED** in [completeJsonSchema] — Anthropic has no
 *    `response_format` / `json_schema` field. D-22-10 confirms existing
 *    prompts already embed schema requirements in the system prompt; the
 *    schema *body* lives in the system prompt itself, not in the wire DTO.
 *  - **systemPrompt → top-level `system` field**, NEVER a role="system"
 *    message. Anthropic rejects role="system" entries in `messages[]`.
 *
 * Behaviour preserved from [OpenAiCompatibleAiClient]:
 *  - temperature 0.7, max_tokens 4096
 *  - SSE streaming with onProgress
 *  - Pure `AiError` propagation from underlying client
 */
class AnthropicAiClient(
    private val client: AnthropicClient
) : AiClient {

    override suspend fun completeJsonSchema(
        baseUrl: String,                       // ignored — Anthropic endpoint fixed
        model: String,
        systemPrompt: String,
        userPrompt: String,
        schema: AiJsonSchema,                  // ignored — schema lives in systemPrompt
        onProgress: (content: String, reasoning: String) -> Unit
    ): String {
        val request = AnthropicMessagesRequest(
            model = model,
            system = systemPrompt,
            messages = listOf(AnthropicMessage(role = "user", content = userPrompt)),
            maxTokens = 4096,
            temperature = 0.7,
            stream = false  // overridden to true by AnthropicClient.chatCompletionStreaming
        )
        return client.chatCompletionStreaming(request, onProgress)
    }

    override suspend fun completeJsonObject(
        baseUrl: String,                       // ignored — see completeJsonSchema
        model: String,
        systemPrompt: String,
        userPrompt: String,
        onProgress: (content: String, reasoning: String) -> Unit
    ): String {
        // Same body as completeJsonSchema — Anthropic doesn't distinguish
        // json_schema vs json_object at the wire level; both paths inject the
        // schema requirement through the system prompt (D-22-10).
        val request = AnthropicMessagesRequest(
            model = model,
            system = systemPrompt,
            messages = listOf(AnthropicMessage(role = "user", content = userPrompt)),
            maxTokens = 4096,
            temperature = 0.7,
            stream = false
        )
        return client.chatCompletionStreaming(request, onProgress)
    }
}
