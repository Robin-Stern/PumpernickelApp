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
 * Both are set to 180s here so AI requests have headroom; the Ktor side is
 * tightened back down per-request inside OpenAICompatibleClient.
 */
actual fun createHttpClient(): HttpClient = HttpClient(Darwin) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 180_000
    }
    engine {
        configureSession {
            timeoutIntervalForRequest = 180.0
            timeoutIntervalForResource = 300.0
        }
    }
}
