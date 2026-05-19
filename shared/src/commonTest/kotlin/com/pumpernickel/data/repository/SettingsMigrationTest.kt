package com.pumpernickel.data.repository

import com.pumpernickel.infrastructure.ai.Credential
import com.pumpernickel.infrastructure.ai.FakeSecureKeyStore
import com.pumpernickel.infrastructure.ai.ProviderId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase 22 Plan 10 / D-22-13 — unit tests for the idempotent Plan-06
 * SettingsMigration. Covers:
 *
 *  1. sentinel-only path (no legacy key present)
 *  2. idempotency (sentinel pre-set → no work)
 *  3. OpenAI-inferred (default baseUrl) → credential written, legacy slot
 *     NOT cleared (same physical slot)
 *  4. Together-inferred (`together.ai` baseUrl) → credential moves to
 *     Together slot, legacy OpenAI slot cleared
 *  5. Custom baseUrl preservation (e.g. openrouter.ai) under inferred OpenAI
 *  6. Non-default legacy model preservation
 *  7. mid-flight failure (Keychain throw) → sentinel NOT set so the next
 *     call retries
 *
 * The fakes [FakeSettingsRepository] + [FakeSecureKeyStore] live in the same
 * source set and faithfully mirror the production storage layout (the legacy
 * slot is physically the same key as `ProviderId.OpenAI.apiKeyAccount`).
 */
class SettingsMigrationTest {

    @Test
    fun run_setsSentinelEvenWithoutLegacyKey() = runTest {
        val settings = FakeSettingsRepository()
        val store = FakeSecureKeyStore(initialLegacyKey = null)
        val migration = SettingsMigration(settings, store)

        migration.run()

        assertTrue(settings.migratedToMultiProvider.first())
        assertTrue(store.writeCalls.isEmpty(), "no legacy key → no credential writes")
    }

    @Test
    fun run_secondRunNoOp() = runTest {
        val settings = FakeSettingsRepository(initialMigrated = true)
        val store = FakeSecureKeyStore(initialLegacyKey = "sk-xyz")

        SettingsMigration(settings, store).run()

        // Sentinel is set → migration must return BEFORE reading the legacy
        // key (the production code uses `migratedToMultiProvider.first()` as
        // the very first guard). We assert that side effect: no credential
        // writes, no setActiveProvider calls, no setMigrated calls.
        assertTrue(store.writeCalls.isEmpty(), "second run must not touch credential store")
        assertTrue(settings.setActiveCalls.isEmpty(), "second run must not re-set active provider")
        assertTrue(settings.setMigratedCalls.isEmpty(), "second run must not re-set sentinel")
    }

    @Test
    fun run_legacyOpenAiKey_setsOpenAiCredentialAndActiveProvider() = runTest {
        val settings = FakeSettingsRepository(initialLegacyBaseUrl = "https://api.openai.com/v1")
        val store = FakeSecureKeyStore(initialLegacyKey = "sk-openai")

        SettingsMigration(settings, store).run()

        assertEquals(
            Credential.ApiKey("sk-openai"),
            store.readCredential(ProviderId.OpenAI),
            "OpenAI credential slot must contain the migrated key"
        )
        assertEquals(ProviderId.OpenAI, settings.activeProvider.first())
        assertEquals(
            0, store.clearLegacyCallCount,
            "OpenAI inference must NOT clear the legacy slot — it is the SAME physical slot"
        )
        assertTrue(settings.migratedToMultiProvider.first())
    }

    @Test
    fun run_legacyTogetherUser_movesToTogetherSlotAndClearsLegacy() = runTest {
        val settings = FakeSettingsRepository(initialLegacyBaseUrl = "https://api.together.ai/v1")
        val store = FakeSecureKeyStore(initialLegacyKey = "sk-tog")

        SettingsMigration(settings, store).run()

        assertEquals(
            Credential.ApiKey("sk-tog"),
            store.readCredential(ProviderId.Together),
            "Together-inferred legacy key must move to Together slot"
        )
        assertEquals(ProviderId.Together, settings.activeProvider.first())
        assertEquals(
            1, store.clearLegacyCallCount,
            "Together-inferred path must clear the legacy OpenAI slot exactly once"
        )
    }

    @Test
    fun run_preservesCustomBaseUrl_forOpenAiCompatProvider() = runTest {
        val settings = FakeSettingsRepository(initialLegacyBaseUrl = "https://openrouter.ai/api/v1")
        val store = FakeSecureKeyStore(initialLegacyKey = "sk-or")

        SettingsMigration(settings, store).run()

        // inferProvider folds OpenRouter/Groq into OpenAI; setBaseUrl must
        // preserve the user's custom URL for the OpenAI-compatible adapter.
        assertTrue(
            settings.setBaseUrlCalls.any {
                it.first == ProviderId.OpenAI && it.second == "https://openrouter.ai/api/v1"
            },
            "expected setBaseUrl(OpenAI, openrouter URL) but got ${settings.setBaseUrlCalls}"
        )
    }

    @Test
    fun run_preservesNonDefaultModel() = runTest {
        val settings = FakeSettingsRepository(initialLegacyModel = "gpt-4o")
        val store = FakeSecureKeyStore(initialLegacyKey = "sk-x")

        SettingsMigration(settings, store).run()

        assertTrue(
            settings.setModelCalls.any {
                it.first == ProviderId.OpenAI && it.second == "gpt-4o"
            },
            "expected setModel(OpenAI, gpt-4o) but got ${settings.setModelCalls}"
        )
    }

    @Test
    fun run_skipsSentinelOnFailure() = runTest {
        val settings = FakeSettingsRepository()
        val store = FakeSecureKeyStore(initialLegacyKey = "sk-x", failOnRead = true)

        SettingsMigration(settings, store).run()

        // On Keychain failure the migration must NOT set the sentinel — the
        // next AI call retries from scratch (Plan-06 SUMMARY: crash-safety).
        assertFalse(
            settings.migratedToMultiProvider.first(),
            "sentinel must not be set when migration mid-flight throws"
        )
    }
}
