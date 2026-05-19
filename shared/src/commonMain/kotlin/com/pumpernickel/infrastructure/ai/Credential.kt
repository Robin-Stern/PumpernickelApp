package com.pumpernickel.infrastructure.ai

import kotlinx.serialization.SerialName
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
    data class ApiKey(
        // WR-11 — pin the wire field name so future Kotlin field renames do
        // not invalidate stored credentials.
        @SerialName("value") val value: String
    ) : Credential()

    @Serializable
    data class OAuthToken(
        // WR-11 — explicit SerialName locks the on-disk JSON shape across
        // Kotlin-side field renames. The wire format must remain stable so
        // existing stored tokens survive refactors.
        @SerialName("accessToken") val accessToken: String,
        @SerialName("refreshToken") val refreshToken: String?,
        @SerialName("expiresAtEpochSeconds") val expiresAtEpochSeconds: Long
    ) : Credential()
}
