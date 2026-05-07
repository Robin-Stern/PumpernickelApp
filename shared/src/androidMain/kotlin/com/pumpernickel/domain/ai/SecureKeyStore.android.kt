package com.pumpernickel.domain.ai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class SecureKeyStore(private val context: Context) {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    actual suspend fun writeApiKey(value: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_API_KEY, value).apply()
    }

    actual suspend fun readApiKey(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_API_KEY, null)
    }

    actual suspend fun clearApiKey() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    companion object {
        private const val PREFS_NAME = "ai_secrets"
        private const val KEY_API_KEY = "openai.api.key"
    }
}
