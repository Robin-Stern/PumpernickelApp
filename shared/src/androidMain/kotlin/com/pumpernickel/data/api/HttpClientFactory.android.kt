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
        requestTimeoutMillis = 180_000
    }
    engine {
        config {
            // OkHttp defaults are 10s — much shorter than the Ktor request timeout.
            // For AI endpoints free-tier inference can run 90-150s, so all three
            // socket-level timeouts must match the Ktor ceiling.
            readTimeout(180, TimeUnit.SECONDS)
            writeTimeout(180, TimeUnit.SECONDS)
            connectTimeout(30, TimeUnit.SECONDS)
        }
    }
}
