package com.pumpernickel.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.data.repository.SettingsRepository
import com.pumpernickel.domain.model.WeightUnit
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    @NativeCoroutinesState
    val hasSeenTutorial: StateFlow<Boolean> = settingsRepository
        .hasSeenTutorial
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setHasSeenTutorial(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHasSeenTutorial(value)
        }
    }

    @NativeCoroutinesState
    val weightUnit: StateFlow<WeightUnit> = settingsRepository
        .weightUnit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeightUnit.KG)

    @NativeCoroutinesState
    val appTheme: StateFlow<String> = settingsRepository
        .appTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    @NativeCoroutinesState
    val accentColor: StateFlow<String> = settingsRepository
        .accentColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "green")

    fun setWeightUnit(unit: WeightUnit) {
        viewModelScope.launch {
            settingsRepository.setWeightUnit(unit)
        }
    }

    fun setAppTheme(theme: String) {
        viewModelScope.launch {
            settingsRepository.setAppTheme(theme)
        }
    }

    fun setAccentColor(color: String) {
        viewModelScope.launch {
            settingsRepository.setAccentColor(color)
        }
    }

    // D-quick-vn7 — Debug-Modus toggle + Geofence grace-period picker.
    @NativeCoroutinesState
    val debugModeEnabled: StateFlow<Boolean> = settingsRepository
        .debugModeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    @NativeCoroutinesState
    val gracePeriodSeconds: StateFlow<Long> = settingsRepository
        .gracePeriodSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 300L)

    fun setDebugModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDebugModeEnabled(enabled)
        }
    }

    fun setGracePeriodSeconds(seconds: Long) {
        viewModelScope.launch {
            settingsRepository.setGracePeriodSeconds(seconds)
        }
    }
}
