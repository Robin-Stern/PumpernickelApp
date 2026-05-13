package com.pumpernickel.domain.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide reactive state for whether an API key is currently configured.
 *
 * Why this exists: SecureKeyStore.readApiKey() is a one-shot suspend call.
 * ViewModels checking it in `init {}` only see the value at construction time —
 * if the user saves a key in Settings and navigates back to Workout/Meal Gen,
 * the existing VM still believes there's no key. Same problem for the Settings
 * screen itself across provider switches.
 *
 * Solution: SecureKeyStore.write/clear/read implementations push their result
 * into [configured] so every observer reacts in real time. AiSettingsViewModel
 * bootstraps the state on first init by calling readApiKey().
 */
object ApiKeyState {
    private val _configured = MutableStateFlow(false)
    val configured: StateFlow<Boolean> = _configured.asStateFlow()

    fun set(value: Boolean) {
        _configured.value = value
    }
}
