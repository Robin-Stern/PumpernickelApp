package com.pumpernickel.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * D-22-03 / D-22-10 — Anthropic Messages API request body
 * (POST https://api.anthropic.com/v1/messages).
 *
 * Differences from OpenAI:
 *   - `system` is a TOP-LEVEL field (NOT a message with role="system").
 *     Anthropic rejects role="system" in messages[].
 *   - `max_tokens` is REQUIRED (OpenAI treats it as optional).
 *   - No `response_format` / `json_schema` — Anthropic relies on the system
 *     prompt to specify output shape. D-22-10 confirms existing prompts pass
 *     through unchanged.
 *   - `stream=true` opens an SSE channel with named events (see
 *     [AnthropicContentBlockDeltaFrame] / [AnthropicErrorFrame] below).
 */
@Serializable
data class AnthropicMessagesRequest(
    val model: String,
    val system: String? = null,
    val messages: List<AnthropicMessage>,
    @SerialName("max_tokens") val maxTokens: Int,
    val temperature: Double = 0.7,
    val stream: Boolean = false
)

@Serializable
data class AnthropicMessage(
    val role: String,                 // "user" | "assistant" — NEVER "system"
    val content: String                // simple text content (no content-blocks array for our use-case)
)

/**
 * Top-level wrapper for any Anthropic SSE event. The `type` discriminator
 * tells us which body shape to expect. The streaming parser does a
 * cheap String-contains check on the `event:` line BEFORE full deserialization,
 * so a single sealed wrapper is intentionally avoided — frames we don't care
 * about (content_block_start/stop, ping, message_delta) cost zero
 * deserialization work.
 */
@Serializable
data class AnthropicContentBlockDeltaFrame(
    val type: String,                 // "content_block_delta"
    val index: Int = 0,
    val delta: AnthropicTextDelta
)

@Serializable
data class AnthropicTextDelta(
    val type: String,                 // "text_delta" (the only delta type we consume)
    val text: String = ""
)

@Serializable
data class AnthropicErrorFrame(
    val type: String,                 // "error"
    val error: AnthropicErrorBody
)

@Serializable
data class AnthropicErrorBody(
    val type: String,                 // e.g. "overloaded_error", "invalid_request_error", "authentication_error"
    val message: String
)

/**
 * Non-streaming response body (buffered chatCompletion). Not strictly needed
 * for streaming-only callers, but the AnthropicAiClient adapter exposes a
 * buffered path symmetric to OpenAICompatibleClient so the Phase-18 Codepfade
 * beibehalten werden können.
 */
@Serializable
data class AnthropicMessagesResponse(
    val id: String? = null,
    val model: String? = null,
    val role: String? = null,
    val content: List<AnthropicContentBlock> = emptyList(),
    @SerialName("stop_reason") val stopReason: String? = null,
    val usage: AnthropicUsage? = null
)

@Serializable
data class AnthropicContentBlock(
    val type: String,                 // "text" — tool_use etc. not in scope (D-22-09)
    val text: String? = null
)

@Serializable
data class AnthropicUsage(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0
)

/**
 * OAuth refresh-token grant body (D-22-12). Used by AnthropicOAuthClient.refresh.
 * Endpoint per Anthropic OAuth: `POST https://claude.ai/oauth/token`. Caller
 * passes `client_id` (Phase-22 OAuth flow registers a stable client_id — see
 * Plan 22-05 OAuthBrowserLauncher action notes).
 */
@Serializable
data class AnthropicOAuthRefreshRequest(
    @SerialName("grant_type") val grantType: String = "refresh_token",
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("client_id") val clientId: String
)

@Serializable
data class AnthropicOAuthRefreshResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresInSeconds: Long = 0,
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null
)
