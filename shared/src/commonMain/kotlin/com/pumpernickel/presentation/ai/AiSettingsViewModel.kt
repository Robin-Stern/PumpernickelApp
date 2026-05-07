package com.pumpernickel.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.data.repository.SettingsRepository
import com.pumpernickel.domain.ai.SecureKeyStore
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _apiKeyConfigured = MutableStateFlow(false)

    @NativeCoroutinesState
    val apiKeyConfigured: StateFlow<Boolean> = _apiKeyConfigured.asStateFlow()

    init {
        viewModelScope.launch {
            _apiKeyConfigured.value = secureKeyStore.readApiKey() != null
        }
    }

    fun setApiKey(value: String) {
        viewModelScope.launch {
            secureKeyStore.writeApiKey(value)
            _apiKeyConfigured.value = true
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            secureKeyStore.clearApiKey()
            _apiKeyConfigured.value = false
        }
    }

    /**
     * D-18-06 — selecting a preset resets baseUrl + model to per-preset defaults.
     * Custom preset clears both fields so the user enters them manually.
     */
    fun setProviderPreset(preset: String) {
        val (defaultBase, defaultModel) = PROVIDER_DEFAULTS[preset]
            ?: ("" to "")
        viewModelScope.launch {
            settingsRepository.setAiProviderPreset(preset)
            settingsRepository.setAiBaseUrl(defaultBase)
            settingsRepository.setAiModel(defaultModel)
        }
    }

    fun setBaseUrl(url: String) {
        viewModelScope.launch { settingsRepository.setAiBaseUrl(url) }
    }

    fun setModel(value: String) {
        viewModelScope.launch { settingsRepository.setAiModel(value) }
    }

    companion object {
        // D-18-06 provider preset defaults (preset key -> base URL, model).
        // Custom preset has empty defaults so the user fills them.
        private val PROVIDER_DEFAULTS: Map<String, Pair<String, String>> = mapOf(
            "openai" to ("https://api.openai.com/v1" to "gpt-4o-mini"),
            "together" to ("https://api.together.xyz/v1" to "meta-llama/Llama-3.3-70B-Instruct-Turbo"),
            "openrouter" to ("https://openrouter.ai/api/v1" to "meta-llama/llama-3.3-70b-instruct"),
            "groq" to ("https://api.groq.com/openai/v1" to "llama-3.3-70b-versatile"),
            "custom" to ("" to "")
        )
    }
}
