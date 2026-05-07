package com.pumpernickel.di

import com.pumpernickel.presentation.ai.AiSettingsViewModel
import org.koin.mp.KoinPlatform

class AiSettingsKoinHelper {
    fun getAiSettingsViewModel(): AiSettingsViewModel =
        KoinPlatform.getKoin().get()
}
