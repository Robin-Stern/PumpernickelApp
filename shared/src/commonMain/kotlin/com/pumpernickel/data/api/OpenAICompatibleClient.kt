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
