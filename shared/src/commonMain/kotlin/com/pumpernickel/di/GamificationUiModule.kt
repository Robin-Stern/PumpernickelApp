package com.pumpernickel.di

import org.koin.dsl.module

/**
 * Feature-scoped Koin module for the Overview-strip + unlock-modal VM
 * (GamificationViewModel) and any other presentation-layer gamification
 * wiring that is NOT the achievement gallery.
 *
 * Populated by:
 *   - Plan 08 (wave 5): viewModel { GamificationViewModel(get(), get()) }
 */
val gamificationUiModule = module {
    // Bindings populated by plan 08.
}
