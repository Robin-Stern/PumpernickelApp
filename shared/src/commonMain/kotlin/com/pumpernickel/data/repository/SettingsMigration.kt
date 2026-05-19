package com.pumpernickel.data.repository

import com.pumpernickel.domain.repository.SettingsRepository
import com.pumpernickel.infrastructure.ai.Credential
import com.pumpernickel.infrastructure.ai.ProviderId
import com.pumpernickel.infrastructure.ai.SecureKeyStore
import kotlinx.coroutines.flow.first

/**
 * D-22-08 — one-time migration from Phase 18 single-key/single-baseUrl AI
 * settings to Phase 22 Multi-Provider schema. Idempotent: guarded by the
 * `migratedToMultiProvider` sentinel in DataStore.
 *
 * Triggered lazily by [com.pumpernickel.infrastructure.ai.MigratingAiClient]
 * before each AI call — that way users who never use AI never pay startup
 * cost, and the migration runs on background coroutine context (the
 * Use-Case-Caller's scope).
 *
 * Mapping rules:
 *  - Legacy `openai.api.key` slot exists → write as Credential.ApiKey under
 *    inferred provider's slot. Inferred provider is derived from the legacy
 *    `aiBaseUrl` value:
 *      - contains "together.ai"    → ProviderId.Together
 *      - contains "openrouter.ai"  → ProviderId.OpenAI (preserve baseUrl)
 *      - contains "groq.com"       → ProviderId.OpenAI (preserve baseUrl)
 *      - otherwise                 → ProviderId.OpenAI
 *  - Sets `activeProvider = inferredProvider`
 *  - If inferred provider != OpenAI, the legacy slot is cleaned up (otherwise
 *    the legacy and target slots are the same physical key, no cleanup needed)
 *  - Migrates non-blank legacy `aiModel` into per-provider model slot
 *  - Migrates non-default legacy `aiBaseUrl` into per-provider base-URL slot
 *    (Anthropic skipped — baseUrl is fixed)
 *  - Sets sentinel `migratedToMultiProvider = true` LAST so a crash mid-flight
 *    re-runs the migration on next boot
 *
 * If no legacy key is present: still sets the sentinel so we don't keep
 * probing the Keychain every AI call.
 */
class SettingsMigration(
    private val settingsRepository: SettingsRepository,
    private val secureKeyStore: SecureKeyStore
) {
    suspend fun run() {
        if (settingsRepository.migratedToMultiProvider.first()) return

        try {
            val legacyKey = secureKeyStore.readLegacyApiKey()
            if (legacyKey != null) {
                val oldBaseUrl = settingsRepository.aiBaseUrl.first()
                val oldModel = settingsRepository.aiModel.first()
                val inferredProvider = inferProvider(oldBaseUrl)

                // Write to the new credential slot. For inferredProvider == OpenAI, the
                // legacy and target slots are identical (`openai.api.key`); we still call
                // writeCredential to ensure ApiKeyState sentinel is set correctly. For
                // Together, we move the value and clear the legacy slot below.
                secureKeyStore.writeCredential(inferredProvider, Credential.ApiKey(legacyKey))

                if (inferredProvider != ProviderId.OpenAI) {
                    // Legacy slot is `openai.api.key` == ProviderId.OpenAI's apiKeyAccount.
                    // Together-inferred users had their key sitting in the OpenAI slot pre-migration.
                    // Move it to Together and clear the OpenAI slot.
                    secureKeyStore.clearLegacyApiKey()
                }

                settingsRepository.setActiveProvider(inferredProvider)

                // Preserve user's custom baseUrl / model unless they're the legacy defaults.
                if (oldBaseUrl.isNotBlank() && oldBaseUrl != "https://api.openai.com/v1") {
                    if (inferredProvider != ProviderId.Anthropic) {
                        // Anthropic baseUrl is fixed (setBaseUrl is a no-op there)
                        settingsRepository.setBaseUrl(inferredProvider, oldBaseUrl)
                    }
                }
                if (oldModel.isNotBlank() && oldModel != "gpt-4o-mini") {
                    settingsRepository.setModel(inferredProvider, oldModel)
                }

                println("[SettingsMigration] migrated legacy key → provider=$inferredProvider model=$oldModel baseUrl=$oldBaseUrl")
            } else {
                println("[SettingsMigration] no legacy key present — sentinel-only migration")
            }
        } catch (t: Throwable) {
            // Don't crash the app — but also don't set the sentinel. Next call retries.
            println("[SettingsMigration] migration failed: ${t::class.simpleName}: ${t.message}")
            return
        }

        settingsRepository.setMigratedToMultiProvider(true)
    }

    private fun inferProvider(baseUrl: String): ProviderId = when {
        baseUrl.contains("together.ai") -> ProviderId.Together
        // OpenRouter / Groq users stay on the OpenAI-compatible adapter; their custom
        // baseUrl is preserved via setBaseUrl(OpenAI, ...). This intentionally folds
        // multiple legacy presets into a single new provider slot — full per-preset
        // representation is a deferred idea in CONTEXT.md.
        else -> ProviderId.OpenAI
    }
}
