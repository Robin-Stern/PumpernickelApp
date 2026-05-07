package com.pumpernickel.domain.ai

/**
 * REQ-AI-06 — BYOK API key storage. The key is the ONLY secret managed here;
 * non-secret config (provider preset, base URL, model name) lives in
 * SettingsRepository / DataStore.
 *
 * Implementations:
 * - Android: EncryptedSharedPreferences via androidx.security.crypto
 *   (AES256_SIV keys, AES256_GCM values, MasterKey.DEFAULT_MASTER_KEY_ALIAS).
 * - iOS: Keychain Services with kSecClass=GenericPassword,
 *   kSecAttrService="PumpernickelApp.AI", kSecAttrAccount="openai.api.key",
 *   kSecAttrAccessible=kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly.
 *
 * The key is NEVER returned to logs, NEVER persisted to DataStore, NEVER held
 * as a long-lived field. Callers read it just-in-time per request and let it
 * fall out of scope after the HTTPS call.
 */
expect class SecureKeyStore {
    suspend fun writeApiKey(value: String)
    suspend fun readApiKey(): String?
    suspend fun clearApiKey()
}
