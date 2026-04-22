package com.pumpernickel.di

import org.koin.dsl.module

/**
 * Feature-scoped Koin module for the AchievementGalleryViewModel.
 * Split out from GamificationUiModule so plan 09 can ship in parallel with
 * plan 08 (same wave, different module files -- Blocker 1 fix).
 *
 * Populated by:
 *   - Plan 09 (wave 5): viewModel { AchievementGalleryViewModel(get()) }
 */
val achievementGalleryModule = module {
    // Bindings populated by plan 09.
}
