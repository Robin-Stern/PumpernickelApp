package com.pumpernickel.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

actual fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 600_000   // 10 min — matches per-request override in OpenAICompatibleClient
        socketTimeoutMillis = 120_000    // 2 min between bytes for slow LLM token streams
    }
    engine {
        config {
            // OkHttp defaults are 10s — much shorter than the Ktor request timeout.
            // For AI endpoints free-tier inference can run up to 10 minutes, so the
            // socket-level read/write timeouts must match the Ktor ceiling.
            // connectTimeout stays at 30s — TCP handshake is fast and unrelated to
            // slow LLM generation.
            readTimeout(600, TimeUnit.SECONDS)
            writeTimeout(600, TimeUnit.SECONDS)
            connectTimeout(30, TimeUnit.SECONDS)
        }
    }
}
