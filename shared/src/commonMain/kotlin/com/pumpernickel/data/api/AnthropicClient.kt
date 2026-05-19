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
                if (status in setOf(401, 403) && credential is Credential.OAuthToken) {
                    // D-22-12 — drop the dead OAuth token so the next attempt
                    // re-authenticates. Avoid leaving stale credentials in storage
                    // when Anthropic revokes a token mid-flight.
                    secureKeyStore.clearCredential(ProviderId.Anthropic)
                }
                throw mapHttpError(status, text)
            }
            if (text.length > MAX_RESPONSE_TEXT_LEN_FOR_SANITY) {
                // WR-09 — sanity check only: `bodyAsText()` above already
                // buffered the full body into memory, so this does NOT protect
                // against an attacker-controlled response forcing OOM. Treat as
                // a post-hoc bound for schema-likely-broken responses.
                throw AiError.SchemaInvalid("response exceeded 64KB sanity cap")
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

        // D-22-11 — SSE parsing delegated to AnthropicSseParser (Plan 22-10
        // extraction for unit-testability). The parser is stateful: it tracks
        // currentEventType across feed(...) calls and exposes `done` once the
        // `message_stop` frame is consumed.
        val parser = AnthropicSseParser(json)

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
                    if (status in setOf(401, 403) && credential is Credential.OAuthToken) {
                        // D-22-12 — see chatCompletion: clear revoked OAuth token before
                        // surfacing AuthOrQuota so the next attempt re-authenticates.
                        secureKeyStore.clearCredential(ProviderId.Anthropic)
                    }
                    throw mapHttpError(status, err)
                }

                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead && !parser.done) {
                    val line = channel.readUTF8Line() ?: break
                    val update = parser.feed(line)
                    if (update != null) onProgress(update, "")
                }
            }

            val finalContent = parser.result()
            println("[Anthropic] stream complete stop=${parser.done} contentLen=${finalContent.length}")
            if (finalContent.length > MAX_RESPONSE_TEXT_LEN_FOR_SANITY) {
                // WR-09 — see chatCompletion: sanity bound, not a security
                // protection. The streamed body accumulates in `parser.result()`.
                throw AiError.SchemaInvalid("response exceeded 64KB sanity cap")
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

    private fun mapHttpError(status: Int, body: String): AiError {
        val excerpt = body.take(400).replace("\n", " ")
        return when (status) {
            401, 403 -> AiError.AuthOrQuota(status)
            in 500..599 -> AiError.Provider(status)
            else -> AiError.SchemaInvalid("HTTP $status — $excerpt")
        }
    }

    companion object {
        // D-22-03 — fixed endpoints; no user override (D-22-09 baseUrlByProvider
        // returns the constant from SettingsRepositoryImpl).
        const val MESSAGES_ENDPOINT = "https://api.anthropic.com/v1/messages"
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val HEADER_ANTHROPIC_VERSION = "anthropic-version"
        const val HEADER_X_API_KEY = "x-api-key"
        const val HttpHeaderAuthorization = "Authorization"

        // WR-09 — post-hoc sanity bound on response text length. NOT a security
        // protection: bodyAsText()/SSE accumulation has already loaded the body
        // into memory. True size-bounded reads would require streamed parsing
        // with running size accounting (deferred).
        private const val MAX_RESPONSE_TEXT_LEN_FOR_SANITY = 64 * 1024
    }
}
