package com.pumpernickel.infrastructure.ai

/**
 * Phase 22 Plan 10 test fixture — in-memory [SecureKeyStoreSurface] that
 * mirrors the platform-actual storage layout: the legacy `openai.api.key`
 * slot is physically the same as `ProviderId.OpenAI.apiKeyAccount`. Tests
 * for [com.pumpernickel.data.repository.SettingsMigration] rely on this so
 * the OpenAI-vs-Together edge case (writeCredential to OpenAI must NOT trigger
 * clearLegacyApiKey, which would wipe the value just written) is faithfully
 * reproduced.
 *
 * @param initialLegacyKey value to return from [readLegacyApiKey] on the
 *     first call. Also seeded into the internal store under
 *     `ProviderId.OpenAI.apiKeyAccount` to match the production storage
 *     layout (the Phase-18 single-slot is the same slot OpenAI uses now).
 * @param failOnRead if true, [readLegacyApiKey] throws to simulate a transient
 *     Keychain failure (used in the `run_skipsSentinelOnFailure` test).
 */
class FakeSecureKeyStore(
    initialLegacyKey: String? = null,
    var failOnRead: Boolean = false
) : SecureKeyStoreSurface {

    private val store = mutableMapOf<String, Credential>()

    /**
     * Legacy single-slot reference. Plain String, NOT a Credential — mirrors
     * the Phase-18 storage shape that the Plan-06 migration reads via
     * [readLegacyApiKey].
     */
    var legacyKey: String? = initialLegacyKey

    val writeCalls = mutableListOf<Pair<ProviderId, Credential>>()
    var clearLegacyCallCount = 0

    init {
        // Seed the OpenAI slot so a Pfad-A migration (OpenAI-inferred) finds
        // the same physical slot from both readLegacyApiKey() and
        // readCredential(OpenAI).
        if (initialLegacyKey != null) {
            store[ProviderId.OpenAI.apiKeyAccount] = Credential.ApiKey(initialLegacyKey)
        }
    }

    override suspend fun writeCredential(provider: ProviderId, credential: Credential) {
        store[provider.apiKeyAccount] = credential
        writeCalls += (provider to credential)
        if (provider == ProviderId.OpenAI && credential is Credential.ApiKey) {
            // Same physical slot — keep them in sync.
            legacyKey = credential.value
        }
    }

    override suspend fun readCredential(provider: ProviderId): Credential? =
        store[provider.apiKeyAccount]

    override suspend fun clearCredential(provider: ProviderId) {
        store.remove(provider.apiKeyAccount)
        if (provider == ProviderId.OpenAI) legacyKey = null
    }

    override suspend fun listProviders(): Set<ProviderId> =
        store.keys.mapNotNull { key ->
            ProviderId.entries.firstOrNull { it.apiKeyAccount == key }
        }.toSet()

    override suspend fun readLegacyApiKey(): String? {
        if (failOnRead) throw RuntimeException("simulated keychain failure")
        return legacyKey
    }

    override suspend fun clearLegacyApiKey() {
        clearLegacyCallCount++
        legacyKey = null
        store.remove(ProviderId.OpenAI.apiKeyAccount)
    }
}
