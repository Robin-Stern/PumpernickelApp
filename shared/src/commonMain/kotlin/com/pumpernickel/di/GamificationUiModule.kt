package com.pumpernickel.di

import com.pumpernickel.presentation.gamification.GamificationViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Gamification presentation bindings. Populated in phase-15 plan 08.
 * Mounted by SharedModule.kt via includes(gamificationUiModule).
 */
val gamificationUiModule = module {
    viewModel { GamificationViewModel(get(), get()) }
}
