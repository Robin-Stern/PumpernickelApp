package com.pumpernickel.infrastructure.ai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.pumpernickel.domain.ai.ApiKeyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

actual class SecureKeyStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val prefs by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    actual suspend fun writeCredential(
        provider: ProviderId,
        credential: Credential
    ) = withContext(Dispatchers.IO) {
        val (key, value) = when (credential) {
            is Credential.ApiKey -> provider.apiKeyAccount to credential.value
            is Credential.OAuthToken -> provider.oauthTokenAccount to
                json.encodeToString(Credential.OAuthToken.serializer(), credential)
        }
        prefs.edit().putString(key, value).apply()
        ApiKeyState.set(true)
    }

    actual suspend fun readCredential(provider: ProviderId): Credential? =
        withContext(Dispatchers.IO) {
            // D-22-01 OAuth-Primary: try OAuth slot first, fall back to API-Key slot.
            val oauthRaw = prefs.getString(provider.oauthTokenAccount, null)
            if (oauthRaw != null) {
                return@withContext try {
                    json.decodeFromString(Credential.OAuthToken.serializer(), oauthRaw)
                } catch (_: Throwable) {
                    null
                }
            }
            prefs.getString(provider.apiKeyAccount, null)?.let { Credential.ApiKey(it) }
        }

    actual suspend fun clearCredential(provider: ProviderId) = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove(provider.apiKeyAccount)
            .remove(provider.oauthTokenAccount)
            .apply()
        ApiKeyState.set(hasAnyCredentialSync())
    }

    actual suspend fun listProviders(): Set<ProviderId> = withContext(Dispatchers.IO) {
        ProviderId.entries.filter { p ->
            prefs.contains(p.apiKeyAccount) || prefs.contains(p.oauthTokenAccount)
        }.toSet()
    }

    actual suspend fun readLegacyApiKey(): String? = withContext(Dispatchers.IO) {
        prefs.getString(LEGACY_KEY, null)
    }

    actual suspend fun clearLegacyApiKey() = withContext(Dispatchers.IO) {
        prefs.edit().remove(LEGACY_KEY).apply()
    }

    private fun hasAnyCredentialSync(): Boolean =
        ProviderId.entries.any { p ->
            prefs.contains(p.apiKeyAccount) || prefs.contains(p.oauthTokenAccount)
        }

    companion object {
        private const val PREFS_NAME = "ai_secrets"
        // NOTE: LEGACY_KEY == ProviderId.OpenAI.apiKeyAccount intentionally — the
        // Phase-18 fixed slot IS the OpenAI api-key slot under the new naming
        // scheme. SettingsMigration (Plan 06) reads via this legacy helper before
        // any code starts using readCredential(ProviderId.OpenAI).
        private const val LEGACY_KEY = "openai.api.key"
    }
}
