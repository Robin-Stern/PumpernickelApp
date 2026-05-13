package com.pumpernickel.domain.ai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class SecureKeyStore(private val context: Context) {

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

    actual suspend fun writeApiKey(value: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_API_KEY, value).apply()
        ApiKeyState.set(true)
    }

    actual suspend fun readApiKey(): String? = withContext(Dispatchers.IO) {
        val v = prefs.getString(KEY_API_KEY, null)
        ApiKeyState.set(v != null)
        v
    }

    actual suspend fun clearApiKey() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_API_KEY).apply()
        ApiKeyState.set(false)
    }

    companion object {
        private const val PREFS_NAME = "ai_secrets"
        private const val KEY_API_KEY = "openai.api.key"
    }
}
