package com.pumpernickel.di

import com.pumpernickel.presentation.gamification.GamificationViewModel
import com.pumpernickel.presentation.gamification.RanksAndAchievementsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Gamification presentation bindings. Populated in phase-15 plan 08,
 * extended in phase-15.1 plan 01 with RanksAndAchievementsViewModel.
 * Mounted by SharedModule.kt via includes(gamificationUiModule).
 */
val gamificationUiModule = module {
    viewModel { GamificationViewModel(get(), get()) }
    viewModel { RanksAndAchievementsViewModel(get()) }   // D-151-09 — takes GamificationRepository
}
