package com.pumpernickel.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.domain.ai.ApiKeyState
import com.pumpernickel.domain.repository.SettingsRepository
import com.pumpernickel.infrastructure.ai.AnthropicOAuthFlow
import com.pumpernickel.infrastructure.ai.Credential
import com.pumpernickel.infrastructure.ai.ProviderId
import com.pumpernickel.infrastructure.ai.SecureKeyStore
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Phase 22 — Multi-Provider Settings ViewModel.
 *
 * Three exposed flows match the Settings-UI shape (D-22-05):
 *  - [activeProvider]      : current global active provider
 *  - [modelByProvider]     : per-provider model choice (default-fallback applied
 *                            in SettingsRepositoryImpl)
 *  - [connectedProviders]  : set of providers with any credential present
 *
 * Plus two transient flows:
 *  - [oauthInProgress] : true while AnthropicOAuthFlow.authorize() is running.
 *                        UI uses this to show a spinner over the connect-sheet.
 *  - [lastError]       : last error from a connect/save action; cleared by
 *                        [clearError] or by the next successful action.
 *
 * Legacy aiProviderPreset / aiBaseUrl / aiModel / apiKeyConfigured fields are
 * REMOVED — UI fully migrated to the per-provider model.
 */
class AiSettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val secureKeyStore: SecureKeyStore,
    private val anthropicOAuthFlow: AnthropicOAuthFlow
) : ViewModel() {

    @NativeCoroutinesState
    val activeProvider: StateFlow<ProviderId> = settingsRepository.activeProvider
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProviderId.OpenAI)

    @NativeCoroutinesState
    val modelByProvider: StateFlow<Map<ProviderId, String>> = settingsRepository.modelByProvider
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Set of providers that have ANY credential stored. Refreshed from
     * SecureKeyStore.listProviders() — must be re-read after writeCredential /
     * clearCredential. We re-read on init and after every state-mutating action.
     */
    private val _connectedProviders = MutableStateFlow<Set<ProviderId>>(emptySet())
    @NativeCoroutinesState
    val connectedProviders: StateFlow<Set<ProviderId>> = _connectedProviders.asStateFlow()

    private val _oauthInProgress = MutableStateFlow(false)
    @NativeCoroutinesState
    val oauthInProgress: StateFlow<Boolean> = _oauthInProgress.asStateFlow()

    /** UI hint state — last error from connect/save actions; null after success. */
    private val _lastError = MutableStateFlow<String?>(null)
    @NativeCoroutinesState
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    init {
        viewModelScope.launch { refreshConnectedProviders() }
    }

    private suspend fun refreshConnectedProviders() {
        val set = secureKeyStore.listProviders()
        _connectedProviders.value = set
        ApiKeyState.set(set.isNotEmpty())
    }

    fun setActiveProvider(provider: ProviderId) {
        viewModelScope.launch {
            settingsRepository.setActiveProvider(provider)
        }
    }

    fun setModel(provider: ProviderId, model: String) {
        viewModelScope.launch { settingsRepository.setModel(provider, model) }
    }

    /**
     * D-22-01 — OAuth-Primary path. Opens the browser, exchanges code for token,
     * writes Credential.OAuthToken to the Anthropic slot, and sets
     * activeProvider = Anthropic on success.
     *
     * Note: [AnthropicOAuthFlow.authorize] returns a fully-constructed
     * [Credential.OAuthToken] (or null on user-cancel) — we forward it directly
     * to [SecureKeyStore.writeCredential] without re-wrapping.
     */
    fun startAnthropicOAuth() {
        if (_oauthInProgress.value) return
        viewModelScope.launch {
            _oauthInProgress.value = true
            _lastError.value = null
            try {
                val token: Credential.OAuthToken? = anthropicOAuthFlow.authorize()
                if (token == null) {
                    _lastError.value = "OAuth-Vorgang abgebrochen"
                    return@launch
                }
                secureKeyStore.writeCredential(ProviderId.Anthropic, token)
                settingsRepository.setActiveProvider(ProviderId.Anthropic)
                refreshConnectedProviders()
            } catch (t: Throwable) {
                _lastError.value = "OAuth fehlgeschlagen: ${t.message ?: t::class.simpleName}"
            } finally {
                _oauthInProgress.value = false
            }
        }
    }

    /**
     * D-22-02 — Anthropic API-Key fallback. Validates by simple write; runtime
     * proves validity on first API call (auth-or-quota error if bad).
     */
    fun setAnthropicApiKey(key: String) {
        viewModelScope.launch {
            secureKeyStore.writeCredential(ProviderId.Anthropic, Credential.ApiKey(key.trim()))
            settingsRepository.setActiveProvider(ProviderId.Anthropic)
            refreshConnectedProviders()
        }
    }

    /** Persist OpenAI or Together API key (and any future ApiKey-based provider). */
    fun setApiKeyFor(provider: ProviderId, key: String) {
        viewModelScope.launch {
            secureKeyStore.writeCredential(provider, Credential.ApiKey(key.trim()))
            refreshConnectedProviders()
        }
    }

    fun disconnect(provider: ProviderId) {
        viewModelScope.launch {
            secureKeyStore.clearCredential(provider)
            // If we just disconnected the active provider, fall back to OpenAI as default.
            val currentActive = settingsRepository.activeProvider.first()
            if (currentActive == provider) {
                settingsRepository.setActiveProvider(ProviderId.OpenAI)
            }
            refreshConnectedProviders()
        }
    }

    fun clearError() {
        _lastError.value = null
    }
}
