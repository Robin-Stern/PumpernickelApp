package com.pumpernickel.data.api

import com.pumpernickel.domain.ai.AiError
import kotlinx.serialization.json.Json

/**
 * D-22-11 / Plan 22-10 — pure-Kotlin Anthropic SSE parser. Stateful: tracks the
 * current `event:` line across `feed(...)` calls so the next `data:` payload
 * can be routed to the right handler.
 *
 * Extracted from [AnthropicClient.chatCompletionStreaming] for testability —
 * tests in `commonTest` can feed canonical sample frames from
 * https://docs.anthropic.com/en/api/messages-streaming without standing up an
 * HTTP client (D-22-13: no Ktor-network roundtrip tests).
 *
 * Frames consumed:
 *  - `content_block_delta` with `delta.type == "text_delta"` → accumulates text
 *  - `message_stop` → sets [done] to true (caller exits the read loop)
 *  - `error` → throws a typed [AiError] (AuthOrQuota / Provider / SchemaInvalid)
 *
 * Frames silently ignored: `message_start`, `content_block_start`,
 * `content_block_stop`, `message_delta`, `ping`, and any non-event/non-data
 * line. Comments and unknown event types are dropped without error.
 *
 * Error-type mapping (matches the inlined behaviour from Plan 22-03):
 *  - `*authentication_error` → [AiError.AuthOrQuota] with httpStatus = 401
 *  - `*overloaded_error`     → [AiError.Provider] with httpStatus = 529
 *  - any other error.type    → [AiError.SchemaInvalid] with message
 */
class AnthropicSseParser(
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) {
    private var currentEventType: String? = null
    private val content = StringBuilder()

    /** Set to true after a `message_stop` frame is consumed. */
    var done: Boolean = false
        private set

    /**
     * Feed one SSE line. Returns the new accumulated content snapshot if the
     * line appended text (a `content_block_delta` with non-empty text_delta);
     * returns null otherwise (event-line, ignored data-line, blank, etc.).
     * Throws [AiError] on `error`-event data frames.
     */
    fun feed(line: String): String? {
        if (line.isBlank()) {
            currentEventType = null  // event boundary per SSE spec
            return null
        }
        return when {
            line.startsWith("event:") -> {
                currentEventType = line.substring(6).trim()
                null
            }
            line.startsWith("data:") -> {
                val payload = line.substring(5).trim()
                handleDataPayload(payload)
            }
            else -> null  // comment lines, etc.
        }
    }

    /** Returns the accumulated text content collected so far. */
    fun result(): String = content.toString()

    private fun handleDataPayload(payload: String): String? {
        return when (currentEventType) {
            "content_block_delta" -> {
                val frame = tryDecode<AnthropicContentBlockDeltaFrame>(payload) ?: return null
                if (frame.delta.type == "text_delta" && frame.delta.text.isNotEmpty()) {
                    content.append(frame.delta.text)
                    content.toString()
                } else null
            }
            "error" -> {
                val frame = tryDecode<AnthropicErrorFrame>(payload)
                val errType = frame?.error?.type ?: ""
                val msg = frame?.error?.message ?: payload.take(200)
                throw when {
                    errType.endsWith("authentication_error") -> AiError.AuthOrQuota(401)
                    errType.endsWith("overloaded_error") -> AiError.Provider(529)
                    else -> AiError.SchemaInvalid("Anthropic stream error: $msg")
                }
            }
            "message_stop" -> {
                done = true
                null
            }
            // Silent ignores: message_start, content_block_start, content_block_stop,
            // message_delta, ping, null (data line without preceding event), unknown.
            else -> null
        }
    }

    private inline fun <reified T> tryDecode(payload: String): T? = try {
        json.decodeFromString<T>(payload)
    } catch (_: Throwable) { null }
}
