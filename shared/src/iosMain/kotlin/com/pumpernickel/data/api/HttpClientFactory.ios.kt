package com.pumpernickel.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Two timeout layers must match — they are independent and the smallest wins:
 *
 * 1. Ktor [HttpTimeout].requestTimeoutMillis  — the Kotlin coroutine-level
 *    timeout. We override this per-request inside OpenAICompatibleClient.
 * 2. Darwin engine NSURLSessionConfiguration timeouts — the iOS network-stack
 *    timeout. URLSession defaults to 60s timeoutIntervalForRequest. If only
 *    Ktor's timeout was bumped, URLSession would still cancel the socket at
 *    60s — which is exactly what we observed with Gemma 4 31B (free-tier
 *    inference can take 90-150s end-to-end).
 *
 * Both are set to 600s (10 min) here so AI requests have headroom for slow
 * free-tier LLM generations; the Ktor per-request override inside
 * OpenAICompatibleClient matches the same 10-min ceiling.
 */
actual fun createHttpClient(): HttpClient = HttpClient(Darwin) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 600_000   // 10 min — matches the per-request override in OpenAICompatibleClient
        socketTimeoutMillis = 120_000    // 2 min between bytes for slow LLM token streams
    }
    engine {
        configureSession {
            timeoutIntervalForRequest = 600.0   // 10 min — between-packet timeout for the URLSession
            timeoutIntervalForResource = 600.0  // 10 min — total resource lifetime
        }
    }
}
