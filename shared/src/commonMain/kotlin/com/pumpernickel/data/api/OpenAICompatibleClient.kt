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
        val key = keyProvider()
        if (key == null) {
            println("[AI] keyProvider returned null — no API key configured (or Keychain read failed)")
            throw AiError.AuthOrQuota(401)
        }
        val normalisedBase = baseUrl.trimEnd('/')
        val url = "$normalisedBase/chat/completions"
        println("[AI] POST $url model=${request.model} messages=${request.messages.size} keyLen=${key.length}")
        try {
            val response = client.post(url) {
                header("Authorization", "Bearer $key")
                contentType(ContentType.Application.Json)
                timeout {
                    requestTimeoutMillis = 120_000  // bumped from 60s — provider responses for
                                                    // structured-output requests with large schemas
                                                    // can take 30-90s on free-tier inference.
                }
                setBody(request)
            }
            val responseText = response.bodyAsText()
            println("[AI] response status=${response.status.value} bodyLen=${responseText.length}")
            val statusCode = response.status.value
            if (statusCode !in 200..299) {
                println("[AI] non-2xx body (truncated 1KB): ${responseText.take(1024)}")
                // Surface provider error messages to the user instead of swallowing them.
                // Keep the body short — Together/OpenAI errors are usually JSON like
                // {"error":{"message":"...","code":"..."}} so 400 chars is plenty.
                val excerpt = responseText.take(400).replace("\n", " ")
                throw when (statusCode) {
                    401, 403 -> AiError.AuthOrQuota(statusCode)
                    in 500..599 -> AiError.Provider(statusCode)
                    else -> AiError.SchemaInvalid("HTTP $statusCode — $excerpt")
                }
            }
            if (responseText.length > 64 * 1024) {
                throw AiError.SchemaInvalid("response exceeded 64KB cap")
            }
            return json.decodeFromString<ChatResponse>(responseText)
        } catch (ce: CancellationException) {
            throw ce  // do not wrap — let coroutine cancellation propagate
        } catch (ai: AiError) {
            println("[AI] AiError: $ai")
            throw ai
        } catch (t: Throwable) {
            println("[AI] caught throwable: ${t::class.simpleName}: ${t.message}")
            throw AiError.fromThrowable(t)
        }
    }
}
