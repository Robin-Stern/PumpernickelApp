package com.pumpernickel.di

import com.pumpernickel.presentation.gamification.GamificationViewModel
import org.koin.mp.KoinPlatform

/**
 * iOS-side factory for the gamification Overview strip + unlock modal.
 * Swift callers: `GamificationUiKoinHelper().getGamificationViewModel()`.
 * Naming mirrors the existing `KoinHelper` pattern (one getter per VM,
 * no params, no caching).
 */
class GamificationUiKoinHelper {
    fun getGamificationViewModel(): GamificationViewModel =
        KoinPlatform.getKoin().get()
}
