package com.pumpernickel.infrastructure.ai

/**
 * D-22-07 — IDs der drei unterstützten AI-Provider. Enum-Name wird als
 * DataStore-String (SettingsRepository.activeProvider) und Keychain/Prefs-
 * Slot-Suffix verwendet (`openai.api.key`, `together.api.key`,
 * `anthropic.api.key`, `anthropic.oauth.token`).
 */
enum class ProviderId(
    /** Persistenz-Form für DataStore-String und Keychain-account. */
    val wireName: String
) {
    OpenAI("openai"),
    Together("together"),
    Anthropic("anthropic");

    /** Keychain-account / EncryptedSharedPrefs-Key für API-Key-Slot. */
    val apiKeyAccount: String get() = "$wireName.api.key"

    /** Keychain-account / EncryptedSharedPrefs-Key für OAuth-Token-Slot (nur Anthropic). */
    val oauthTokenAccount: String get() = "$wireName.oauth.token"

    companion object {
        fun fromWireNameOrNull(s: String?): ProviderId? =
            entries.firstOrNull { it.wireName == s }
    }
}
