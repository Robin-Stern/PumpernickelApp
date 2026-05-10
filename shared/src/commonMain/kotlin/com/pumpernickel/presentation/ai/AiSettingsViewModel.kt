package com.pumpernickel.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.data.repository.SettingsRepository
import com.pumpernickel.domain.ai.ApiKeyState
import com.pumpernickel.domain.ai.SecureKeyStore
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AiSettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val secureKeyStore: SecureKeyStore
) : ViewModel() {

    @NativeCoroutinesState
    val providerPreset: StateFlow<String> = settingsRepository.aiProviderPreset
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "openai")

    @NativeCoroutinesState
    val baseUrl: StateFlow<String> = settingsRepository.aiBaseUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "https://api.openai.com/v1")

    @NativeCoroutinesState
    val model: StateFlow<String> = settingsRepository.aiModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "gpt-4o-mini")

    /**
     * Source of truth lives in [ApiKeyState] (process-wide). Other VMs and the
     * Settings screen all observe the same flow, so write/clear/provider-switch
     * propagate everywhere in real time. Bootstrapped once on init via readApiKey().
     */
    @NativeCoroutinesState
    val apiKeyConfigured: StateFlow<Boolean> = ApiKeyState.configured

    init {
        viewModelScope.launch {
            // Bootstraps ApiKeyState from Keychain. readApiKey() itself updates
            // ApiKeyState now, so no explicit set() needed.
            secureKeyStore.readApiKey()
        }
    }

    fun setApiKey(value: String) {
        viewModelScope.launch { secureKeyStore.writeApiKey(value) }
    }

    fun clearApiKey() {
        viewModelScope.launch { secureKeyStore.clearApiKey() }
    }

    /**
     * D-18-06 — selecting a preset resets baseUrl + model to per-preset defaults.
     * ALSO clears the saved API key: a key issued by OpenAI won't work on Together,
     * so leaving the previous key around silently broken with a green
     * "Schlüssel gespeichert" indicator was the user's #1 confusion. After a
     * preset switch we always force a fresh entry, which keeps the indicator honest.
     */
    fun setProviderPreset(preset: String) {
        val (defaultBase, defaultModel) = PROVIDER_DEFAULTS[preset]
            ?: ("" to "")
        viewModelScope.launch {
            settingsRepository.setAiProviderPreset(preset)
            settingsRepository.setAiBaseUrl(defaultBase)
            settingsRepository.setAiModel(defaultModel)
            secureKeyStore.clearApiKey()
        }
    }

    fun setBaseUrl(url: String) {
        viewModelScope.launch { settingsRepository.setAiBaseUrl(url) }
    }

    fun setModel(value: String) {
        viewModelScope.launch { settingsRepository.setAiModel(value) }
    }

    companion object {
        // D-18-06 provider preset defaults. Custom is empty so the user fills it.
        // Default models verified May 2026 — see iosApp settings model picker for
        // additional per-provider suggestions surfaced as quick-pick rows.
        private val PROVIDER_DEFAULTS: Map<String, Pair<String, String>> = mapOf(
            "openai" to ("https://api.openai.com/v1" to "gpt-4o-mini"),
            "together" to ("https://api.together.ai/v1" to "openai/gpt-oss-20b"),
            "openrouter" to ("https://openrouter.ai/api/v1" to "openai/gpt-oss-20b:free"),
            "groq" to ("https://api.groq.com/openai/v1" to "llama-3.3-70b-versatile"),
            "custom" to ("" to "")
        )
    }
}
