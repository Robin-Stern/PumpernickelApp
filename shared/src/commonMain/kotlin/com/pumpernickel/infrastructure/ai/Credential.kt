package com.pumpernickel.infrastructure.ai

import kotlinx.serialization.Serializable

/**
 * D-22-07 — Typsichere Repräsentation eines gespeicherten Credentials.
 *
 * - [ApiKey] wird als roher String in Keychain/EncryptedSharedPrefs gelegt
 *   (Slot: `${provider}.api.key`).
 * - [OAuthToken] wird als JSON-encoded String gespeichert (Slot:
 *   `${provider}.oauth.token`). `expiresAtEpochSeconds` ist UNIX-time, NICHT
 *   relative TTL — Token-Refresh-Logik im AnthropicClient (Plan 03) prüft
 *   `expiresAtEpochSeconds < now + 60` für Pre-Refresh (D-22-12).
 *
 * Diese sealed class lebt in `infrastructure/ai/` (nicht `domain/ai/`) weil sie
 * Storage-Wire-Format ist, kein Domain-Concept — analog zu Phase 20's
 * SecureKeyStore-Location.
 */
sealed class Credential {
    @Serializable
    data class ApiKey(val value: String) : Credential()

    @Serializable
    data class OAuthToken(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtEpochSeconds: Long
    ) : Credential()
}
