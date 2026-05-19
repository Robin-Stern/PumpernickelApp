package com.pumpernickel.data.api

import com.pumpernickel.domain.ai.AiError
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * D-22-12 — Refresh-Token grant against the Claude OAuth token endpoint.
 *
 * Endpoint: POST https://claude.ai/oauth/token
 * Body:     `application/json` { grant_type, refresh_token, client_id }
 * Response: { access_token, refresh_token?, expires_in, token_type, scope }
 *
 * On any failure (network, 4xx, malformed body) callers MUST surface an
 * [AiError.AuthOrQuota] with status 401 so the UI can prompt for fresh
 * OAuth (D-22-12: "Bitte mit Claude.ai neu verbinden"). The original
 * refresh-token is considered invalid after a 4xx and gets cleared by the
 * caller (see [AnthropicClient.ensureFreshCredential]).
 */
class AnthropicOAuthClient(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) {
    suspend fun refresh(refreshToken: String, clientId: String): AnthropicOAuthRefreshResponse {
        try {
            val response = client.post(TOKEN_ENDPOINT) {
                contentType(ContentType.Application.Json)
                setBody(
                    AnthropicOAuthRefreshRequest(
                        refreshToken = refreshToken,
                        clientId = clientId
                    )
                )
            }
            val body = response.bodyAsText()
            val status = response.status.value
            if (status !in 200..299) {
                println("[AnthropicOAuth] refresh non-2xx status=$status body=${body.take(512)}")
                throw AiError.AuthOrQuota(status)
            }
            return json.decodeFromString<AnthropicOAuthRefreshResponse>(body)
        } catch (ce: CancellationException) {
            throw ce
        } catch (ai: AiError) {
            throw ai
        } catch (t: Throwable) {
            println("[AnthropicOAuth] refresh failed: ${t::class.simpleName}: ${t.message}")
            throw AiError.AuthOrQuota(401)
        }
    }

    companion object {
        // D-22-12 — Claude OAuth token endpoint. Note: `claude.ai`, NOT `api.anthropic.com`.
        const val TOKEN_ENDPOINT = "https://claude.ai/oauth/token"
    }
}
