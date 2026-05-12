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
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stream: Boolean = false
)

/**
 * Per-event payload from a streaming /chat/completions response.
 *
 * Each Server-Sent Events frame from an OpenAI-compatible endpoint is shaped
 * `data: {"choices":[{"delta":{"content":"...","reasoning":"..."}}]}` and
 * terminates with `data: [DONE]`. We only care about `delta.content` (the
 * actual answer text the user wants) and `delta.reasoning` (chain-of-thought
 * for visibility — Qwen, DeepSeek R1, o1, etc.).
 */
@Serializable
data class StreamChunkResponse(
    val choices: List<StreamChunkChoice> = emptyList()
)

@Serializable
data class StreamChunkChoice(
    val index: Int = 0,
    val delta: StreamChunkDelta = StreamChunkDelta(),
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class StreamChunkDelta(
    val role: String? = null,
    val content: String? = null,
    val reasoning: String? = null
)

@Serializable
data class ChatMessage(
    val role: String,            // "system" | "user" | "assistant"
    // Nullable: some providers (Qwen via Together, GPT with tool_calls) return
    // {"role":"assistant","content":null,"tool_calls":[...]}. Non-nullable broke
    // ChatResponse deserialization completely — user just saw "could not parse".
    val content: String? = null,
    val refusal: String? = null,
    // Reasoning models (Qwen QwQ, DeepSeek R1, o1) emit chain-of-thought into
    // a `reasoning` field. When max_tokens is too small the reasoning eats all
    // tokens and `content` ends up empty. Detecting this lets us give the user
    // a useful error instead of a generic "empty response".
    val reasoning: String? = null
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
