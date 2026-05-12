package com.pumpernickel.data.api

import com.pumpernickel.domain.ai.AiError
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * OpenAI-compatible HTTPS client (REQ-AI-06 — single BYOK provider).
 * Works against OpenAI, Together.AI, OpenRouter, Groq, and any compatible
 * endpoint by changing the base URL only (D-18-06).
 *
 * Two methods: [chatCompletion] for the legacy buffered call and
 * [chatCompletionStreaming] for SSE token-by-token. Both share the same auth /
 * timeout / error mapping. UseCases prefer streaming so the UI can show what
 * the model is generating in real time.
 */
class OpenAICompatibleClient(
    private val client: HttpClient,
    private val keyProvider: suspend () -> String?
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Buffered (non-streaming) call. Returns the parsed [ChatResponse].
     * Kept around for any future non-AI POST that wants to share the same
     * auth wiring; AI generation paths use [chatCompletionStreaming].
     */
    suspend fun chatCompletion(
        baseUrl: String,
        request: ChatRequest
    ): ChatResponse {
        validateUrl(baseUrl)
        val key = requireKey()
        val url = endpoint(baseUrl)
        println("[AI] POST $url model=${request.model} messages=${request.messages.size} keyLen=${key.length}")
        try {
            val response = client.post(url) {
                header("Authorization", "Bearer $key")
                contentType(ContentType.Application.Json)
                timeout { requestTimeoutMillis = 180_000 }
                setBody(request)
            }
            val responseText = response.bodyAsText()
            val statusCode = response.status.value
            println("[AI] response status=$statusCode bodyLen=${responseText.length}")
            if (statusCode in 200..299) {
                println("[AI] 2xx body (truncated 2KB): ${responseText.take(2048)}")
            } else {
                println("[AI] non-2xx body (truncated 1KB): ${responseText.take(1024)}")
                throw mapHttpError(statusCode, responseText)
            }
            if (responseText.length > 64 * 1024) {
                throw AiError.SchemaInvalid("response exceeded 64KB cap")
            }
            return json.decodeFromString<ChatResponse>(responseText)
        } catch (ce: CancellationException) {
            throw ce
        } catch (ai: AiError) {
            println("[AI] AiError: $ai")
            throw ai
        } catch (t: Throwable) {
            println("[AI] caught throwable: ${t::class.simpleName}: ${t.message}")
            throw AiError.fromThrowable(t)
        }
    }

    /**
     * Streaming call via Server-Sent Events. Forces `stream=true` on the
     * request body and reads the response line-by-line, parsing each
     * `data: {...}` frame as a [StreamChunkResponse]. Each chunk's
     * `delta.content` is appended to the answer text and `delta.reasoning`
     * to the reasoning text (for reasoning models like Qwen QwQ / DeepSeek R1).
     *
     * [onProgress] fires on every received chunk so the ViewModel can drive
     * a live preview in the UI. The accumulated content (NOT reasoning) is
     * what gets returned for downstream JSON parsing — reasoning is purely
     * for visibility.
     *
     * @return The fully accumulated `delta.content` text after `[DONE]`.
     */
    suspend fun chatCompletionStreaming(
        baseUrl: String,
        request: ChatRequest,
        onProgress: (content: String, reasoning: String) -> Unit
    ): String {
        validateUrl(baseUrl)
        val key = requireKey()
        val url = endpoint(baseUrl)
        val streamingRequest = request.copy(stream = true)
        println("[AI] STREAM POST $url model=${request.model} messages=${request.messages.size} keyLen=${key.length}")

        val content = StringBuilder()
        val reasoning = StringBuilder()
        var sawDone = false

        try {
            client.preparePost(url) {
                header("Authorization", "Bearer $key")
                header("Accept", "text/event-stream")
                contentType(ContentType.Application.Json)
                timeout { requestTimeoutMillis = 180_000 }
                setBody(streamingRequest)
            }.execute { response ->
                val statusCode = response.status.value
                if (statusCode !in 200..299) {
                    val errBody = response.bodyAsText()
                    println("[AI] stream non-2xx status=$statusCode body=${errBody.take(1024)}")
                    throw mapHttpError(statusCode, errBody)
                }

                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    if (line.isBlank()) continue
                    if (!line.startsWith("data:")) continue

                    val payload = line.substring(5).trim()
                    if (payload == "[DONE]") {
                        sawDone = true
                        break
                    }

                    val chunk = try {
                        json.decodeFromString<StreamChunkResponse>(payload)
                    } catch (e: Exception) {
                        println("[AI] stream skip malformed chunk: ${payload.take(120)}")
                        continue
                    }
                    val delta = chunk.choices.firstOrNull()?.delta ?: continue

                    var changed = false
                    delta.content?.takeIf { it.isNotEmpty() }?.let {
                        content.append(it)
                        changed = true
                    }
                    delta.reasoning?.takeIf { it.isNotEmpty() }?.let {
                        reasoning.append(it)
                        changed = true
                    }
                    if (changed) {
                        onProgress(content.toString(), reasoning.toString())
                    }
                }
            }

            val finalContent = content.toString()
            println("[AI] stream complete done=$sawDone contentLen=${finalContent.length} reasoningLen=${reasoning.length}")
            if (finalContent.length > 64 * 1024) {
                throw AiError.SchemaInvalid("response exceeded 64KB cap")
            }
            return finalContent
        } catch (ce: CancellationException) {
            throw ce
        } catch (ai: AiError) {
            println("[AI] stream AiError: $ai")
            throw ai
        } catch (t: Throwable) {
            println("[AI] stream caught throwable: ${t::class.simpleName}: ${t.message}")
            throw AiError.fromThrowable(t)
        }
    }

    // MARK: - shared helpers

    private fun validateUrl(url: String) {
        if (url.isBlank()) throw AiError.Network()
        if (!url.startsWith("https://")) {
            throw AiError.SchemaInvalid("Insecure URL rejected (must use https)")
        }
    }

    private suspend fun requireKey(): String {
        val key = keyProvider()
        if (key == null) {
            println("[AI] keyProvider returned null — no API key configured")
            throw AiError.AuthOrQuota(401)
        }
        return key
    }

    private fun endpoint(baseUrl: String): String =
        "${baseUrl.trimEnd('/')}/chat/completions"

    private fun mapHttpError(statusCode: Int, body: String): AiError {
        val excerpt = body.take(400).replace("\n", " ")
        return when (statusCode) {
            401, 403 -> AiError.AuthOrQuota(statusCode)
            in 500..599 -> AiError.Provider(statusCode)
            else -> AiError.SchemaInvalid("HTTP $statusCode — $excerpt")
        }
    }
}
