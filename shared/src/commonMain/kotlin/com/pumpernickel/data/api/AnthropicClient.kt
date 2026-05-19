package com.pumpernickel.data.api

import com.pumpernickel.domain.ai.AiError
import com.pumpernickel.infrastructure.ai.Credential
import com.pumpernickel.infrastructure.ai.ProviderId
import com.pumpernickel.infrastructure.ai.SecureKeyStore
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
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
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * D-22-03 — Anthropic Messages API HTTPS client. Analog to
 * [OpenAICompatibleClient] but with Anthropic-specific wire format:
 *  - Top-level `system` field instead of role="system" message
 *  - `max_tokens` required (defaulted to 4096 to match adapter convention)
 *  - `anthropic-version: 2023-06-01` header required
 *  - Named SSE events (`event: <type>\ndata: {...}\n\n`) instead of OpenAI's
 *    `data: {...}\n\n` frames
 *  - Auth dispatch: `Authorization: Bearer <oauth.access>` OR `x-api-key: <key>`
 *    based on [Credential] variant (D-22-01 / D-22-02)
 *  - Pre-request OAuth token refresh when `expiresAtEpochSeconds < now + 60`
 *    (D-22-12)
 *
 * Endpoint is fixed: `https://api.anthropic.com/v1/messages` (D-22-09 — no
 * user-overrideable base URL for Anthropic).
 */
@OptIn(ExperimentalTime::class)
class AnthropicClient(
    private val client: HttpClient,
    private val secureKeyStore: SecureKeyStore,
    private val oauthClient: AnthropicOAuthClient,
    /** D-22-12 — stable client_id used in OAuth refresh requests. */
    private val oauthClientId: String,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) {

    suspend fun chatCompletion(request: AnthropicMessagesRequest): AnthropicMessagesResponse {
        val credential = ensureFreshCredential()
        val url = MESSAGES_ENDPOINT
        println("[Anthropic] POST $url model=${request.model} messages=${request.messages.size} system=${(request.system?.length ?: 0)}")
        try {
            val response = client.post(url) {
                applyAuthHeaders(credential)
                header(HEADER_ANTHROPIC_VERSION, ANTHROPIC_VERSION)
                contentType(ContentType.Application.Json)
                timeout {
                    requestTimeoutMillis = 600_000   // 10 min — symmetric with OpenAICompatibleClient
                    socketTimeoutMillis = 120_000
                }
                setBody(request)
            }
            val text = response.bodyAsText()
            val status = response.status.value
            if (status !in 200..299) {
                println("[Anthropic] non-2xx status=$status body=${text.take(1024)}")
                throw mapHttpError(status, text, isOAuth = credential is Credential.OAuthToken)
            }
            if (text.length > 64 * 1024) {
                throw AiError.SchemaInvalid("response exceeded 64KB cap")
            }
            return json.decodeFromString<AnthropicMessagesResponse>(text)
        } catch (ce: CancellationException) {
            throw ce
        } catch (ai: AiError) {
            throw ai
        } catch (t: Throwable) {
            throw AiError.fromThrowable(t)
        }
    }

    /**
     * D-22-11 — Streaming via named SSE events. Returns accumulated text from
     * all `content_block_delta` frames (type="text_delta") when the stream
     * emits `message_stop` (or terminates).
     */
    suspend fun chatCompletionStreaming(
        request: AnthropicMessagesRequest,
        onProgress: (content: String, reasoning: String) -> Unit
    ): String {
        val credential = ensureFreshCredential()
        val streamingRequest = request.copy(stream = true)
        val url = MESSAGES_ENDPOINT
        println("[Anthropic] STREAM POST $url model=${request.model} messages=${request.messages.size}")

        val content = StringBuilder()
        var sawStop = false

        try {
            client.preparePost(url) {
                applyAuthHeaders(credential)
                header(HEADER_ANTHROPIC_VERSION, ANTHROPIC_VERSION)
                header("Accept", "text/event-stream")
                contentType(ContentType.Application.Json)
                timeout {
                    requestTimeoutMillis = 600_000
                    socketTimeoutMillis = 120_000
                }
                setBody(streamingRequest)
            }.execute { response ->
                val status = response.status.value
                if (status !in 200..299) {
                    val err = response.bodyAsText()
                    println("[Anthropic] stream non-2xx status=$status body=${err.take(1024)}")
                    throw mapHttpError(status, err, isOAuth = credential is Credential.OAuthToken)
                }

                // Anthropic SSE frames: two lines per event ("event: <type>\ndata: {...}")
                // separated by blank line. We track the latest `event:` line so the
                // subsequent `data:` line can be routed to the right handler.
                var currentEventType: String? = null
                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    if (line.isBlank()) {
                        currentEventType = null  // event boundary
                        continue
                    }
                    when {
                        line.startsWith("event:") -> {
                            currentEventType = line.substring(6).trim()
                        }
                        line.startsWith("data:") -> {
                            val payload = line.substring(5).trim()
                            when (currentEventType) {
                                "content_block_delta" -> {
                                    val frame = tryDecode<AnthropicContentBlockDeltaFrame>(payload) ?: continue
                                    if (frame.delta.type == "text_delta" && frame.delta.text.isNotEmpty()) {
                                        content.append(frame.delta.text)
                                        onProgress(content.toString(), "")
                                    }
                                }
                                "error" -> {
                                    val frame = tryDecode<AnthropicErrorFrame>(payload)
                                    val msg = frame?.error?.message ?: payload.take(200)
                                    val errType = frame?.error?.type ?: ""
                                    // overloaded_error / api_error / billing_error → 5xx-equivalent (Provider)
                                    // others (invalid_request_error, authentication_error) → SchemaInvalid/Auth
                                    throw when {
                                        errType.endsWith("authentication_error") -> AiError.AuthOrQuota(401)
                                        errType.endsWith("overloaded_error") -> AiError.Provider(529)
                                        else -> AiError.SchemaInvalid("Anthropic stream error: $msg")
                                    }
                                }
                                "message_stop" -> {
                                    sawStop = true
                                    return@execute  // exit channel loop cleanly
                                }
                                // Ignore: message_start, content_block_start, content_block_stop,
                                // message_delta, ping
                                else -> { /* silent ignore */ }
                            }
                        }
                        // Comment lines, etc.
                    }
                }
            }

            val finalContent = content.toString()
            println("[Anthropic] stream complete stop=$sawStop contentLen=${finalContent.length}")
            if (finalContent.length > 64 * 1024) {
                throw AiError.SchemaInvalid("response exceeded 64KB cap")
            }
            return finalContent
        } catch (ce: CancellationException) {
            throw ce
        } catch (ai: AiError) {
            throw ai
        } catch (t: Throwable) {
            throw AiError.fromThrowable(t)
        }
    }

    // ---- internal helpers ----

    /**
     * D-22-12 — Pre-request token freshness check + refresh. If the stored
     * credential is OAuth and within 60sec of expiry, fire a refresh against
     * claude.ai/oauth/token. On refresh failure, clear the OAuth slot and
     * throw AuthOrQuota(401) so the UI prompts "Bitte neu mit Claude.ai
     * verbinden".
     */
    private suspend fun ensureFreshCredential(): Credential {
        val cred = secureKeyStore.readCredential(ProviderId.Anthropic)
            ?: throw AiError.AuthOrQuota(401)

        if (cred !is Credential.OAuthToken) return cred

        val nowSeconds = Clock.System.now().epochSeconds
        if (cred.expiresAtEpochSeconds > nowSeconds + 60) return cred  // still fresh

        val refresh = cred.refreshToken
            ?: run {
                println("[Anthropic] OAuth token expired and no refresh_token — clearing slot")
                secureKeyStore.clearCredential(ProviderId.Anthropic)
                throw AiError.AuthOrQuota(401)
            }

        return try {
            val refreshed = oauthClient.refresh(refresh, oauthClientId)
            val newCred = Credential.OAuthToken(
                accessToken = refreshed.accessToken,
                refreshToken = refreshed.refreshToken ?: refresh,  // some providers don't rotate
                expiresAtEpochSeconds = nowSeconds + refreshed.expiresInSeconds
            )
            secureKeyStore.writeCredential(ProviderId.Anthropic, newCred)
            newCred
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            println("[Anthropic] OAuth refresh failed — clearing slot. cause=${t::class.simpleName}")
            secureKeyStore.clearCredential(ProviderId.Anthropic)
            throw AiError.AuthOrQuota(401)
        }
    }

    private fun HttpRequestBuilder.applyAuthHeaders(cred: Credential) {
        when (cred) {
            is Credential.OAuthToken -> header(HttpHeaderAuthorization, "Bearer ${cred.accessToken}")
            is Credential.ApiKey -> header(HEADER_X_API_KEY, cred.value)
        }
    }

    private fun mapHttpError(status: Int, body: String, isOAuth: Boolean): AiError {
        val excerpt = body.take(400).replace("\n", " ")
        return when (status) {
            401, 403 -> {
                if (isOAuth) {
                    // D-22-12 — drop the dead token so next attempt re-authenticates.
                    // (Best-effort: fire-and-forget; we're already on a coroutine and
                    // mapHttpError is non-suspend, so we leave the clear to
                    // ensureFreshCredential's failure path — only a fresh REQ would
                    // observe staleness anyway.)
                }
                AiError.AuthOrQuota(status)
            }
            in 500..599 -> AiError.Provider(status)
            else -> AiError.SchemaInvalid("HTTP $status — $excerpt")
        }
    }

    private inline fun <reified T> tryDecode(payload: String): T? = try {
        json.decodeFromString<T>(payload)
    } catch (_: Throwable) { null }

    companion object {
        // D-22-03 — fixed endpoints; no user override (D-22-09 baseUrlByProvider
        // returns the constant from SettingsRepositoryImpl).
        const val MESSAGES_ENDPOINT = "https://api.anthropic.com/v1/messages"
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val HEADER_ANTHROPIC_VERSION = "anthropic-version"
        const val HEADER_X_API_KEY = "x-api-key"
        const val HttpHeaderAuthorization = "Authorization"
    }
}
