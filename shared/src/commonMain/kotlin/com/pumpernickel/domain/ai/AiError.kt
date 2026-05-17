package com.pumpernickel.domain.ai

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

/**
 * Five canonical AI error classes per D-18-08 + Cancelled sentinel for D-18-16
 * (user pressed Cancel during in-flight generation — no toast).
 *
 * Mapping (D-18-08):
 *   1. Timeout       — request exceeded 300s (D-18-16)
 *   2. Network       — DNS / connection / IO failure
 *   3. AuthOrQuota   — HTTP 4xx (invalid key, quota exhausted, malformed request)
 *   4. Provider      — HTTP 5xx (provider-side problem)
 *   5. SchemaInvalid — model returned non-JSON or JSON that fails our schema
 *                      (after the one auto-retry per D-18-14)
 *   + Cancelled      — coroutine cancelled by user (no error UI)
 */
sealed class AiError : Throwable() {
    object Timeout : AiError()
    data class Network(val detail: String? = null) : AiError()
    data class AuthOrQuota(val httpStatus: Int) : AiError()
    data class Provider(val httpStatus: Int) : AiError()
    data class SchemaInvalid(val detail: String) : AiError()
    object Cancelled : AiError()

    companion object {
        /**
         * Maps a Throwable from the Ktor stack into an AiError. CancellationException
         * MUST be rethrown by callers BEFORE invoking this — coroutine cancellation
         * must propagate, not be wrapped.
         */
        fun fromThrowable(t: Throwable): AiError = when (t) {
            is HttpRequestTimeoutException,
            is SocketTimeoutException,
            is ConnectTimeoutException -> Timeout
            is ClientRequestException -> AuthOrQuota(t.response.status.value)
            is ServerResponseException -> Provider(t.response.status.value)
            is SerializationException -> SchemaInvalid("Malformed JSON: ${t.message}")
            else -> Network(t.message ?: t::class.simpleName)
        }
    }
}
