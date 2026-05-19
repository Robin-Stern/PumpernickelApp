package com.pumpernickel.infrastructure.ai

/**
 * D-22-07 — Multi-Slot platform-secure credential storage. Phase 22 evolution
 * of the single-key SecureKeyStore from Phase 18.
 *
 * Implementations:
 * - Android: EncryptedSharedPreferences (file `ai_secrets`,
 *   AES256_SIV keys / AES256_GCM values, MasterKey.DEFAULT_MASTER_KEY_ALIAS).
 * - iOS: Keychain (kSecClass=GenericPassword,
 *   kSecAttrService=PumpernickelApp.AI,
 *   kSecAttrAccount=<provider>.<api.key|oauth.token>,
 *   kSecAttrAccessible=kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly).
 *
 * Slot layout per [ProviderId]:
 * - `${provider.wireName}.api.key` (raw String) — used by all three providers'
 *   API-Key auth path.
 * - `${provider.wireName}.oauth.token` (JSON-encoded `Credential.OAuthToken`)
 *   — currently only Anthropic uses this slot (D-22-01).
 *
 * Read-back contract: [readCredential] returns whichever variant is present
 * for the given provider. If both api.key and oauth.token are present
 * (theoretically possible after a user switches between auth modes),
 * OAuth-Token wins (D-22-01 OAuth-Primary).
 *
 * Legacy migration helpers ([readLegacyApiKey] / [clearLegacyApiKey]) read /
 * delete the Phase-18 fixed-slot `openai.api.key` regardless of any
 * [ProviderId] mapping; consumed exclusively by `SettingsMigration` (Plan 06).
 *
 * Credentials are NEVER returned to logs, NEVER persisted to DataStore, NEVER
 * held as a long-lived field. Callers read them just-in-time per request and
 * let them fall out of scope after the HTTPS call.
 */
expect class SecureKeyStore {
    suspend fun writeCredential(provider: ProviderId, credential: Credential)
    suspend fun readCredential(provider: ProviderId): Credential?
    suspend fun clearCredential(provider: ProviderId)

    /** Returns the set of providers that currently have any credential stored. */
    suspend fun listProviders(): Set<ProviderId>

    /** Phase-18 legacy slot read; for Plan-06 migration only. */
    suspend fun readLegacyApiKey(): String?

    /** Phase-18 legacy slot delete; for Plan-06 migration only (idempotent). */
    suspend fun clearLegacyApiKey()
}

/**
 * D-22-13 (Plan 22-10) — Test-surface interface mirroring [SecureKeyStore]'s
 * methods 1:1. Exists so [com.pumpernickel.data.repository.SettingsMigration]
 * can be unit-tested with a fake credential store: Kotlin's `expect class`
 * cannot be subclassed from `commonTest` (KMP test source sets resolve to the
 * platform-actual, not a test-local fake), so Migration injects this interface
 * instead. Production code wires the real [SecureKeyStore] via
 * [SecureKeyStoreAdapter] at the Koin layer.
 */
interface SecureKeyStoreSurface {
    suspend fun writeCredential(provider: ProviderId, credential: Credential)
    suspend fun readCredential(provider: ProviderId): Credential?
    suspend fun clearCredential(provider: ProviderId)
    suspend fun listProviders(): Set<ProviderId>
    suspend fun readLegacyApiKey(): String?
    suspend fun clearLegacyApiKey()
}

/**
 * Bridges the platform-`expect class` [SecureKeyStore] to the
 * [SecureKeyStoreSurface] interface for [com.pumpernickel.data.repository.SettingsMigration]
 * (Plan 22-10 testability patch). Delegation is total — no behaviour change vs.
 * the original expect class.
 */
class SecureKeyStoreAdapter(private val real: SecureKeyStore) : SecureKeyStoreSurface {
    override suspend fun writeCredential(provider: ProviderId, credential: Credential) =
        real.writeCredential(provider, credential)
    override suspend fun readCredential(provider: ProviderId): Credential? =
        real.readCredential(provider)
    override suspend fun clearCredential(provider: ProviderId) =
        real.clearCredential(provider)
    override suspend fun listProviders(): Set<ProviderId> =
        real.listProviders()
    override suspend fun readLegacyApiKey(): String? =
        real.readLegacyApiKey()
    override suspend fun clearLegacyApiKey() =
        real.clearLegacyApiKey()
}
