package com.pumpernickel.di

import com.pumpernickel.presentation.gamification.RanksAndAchievementsViewModel
import org.koin.mp.KoinPlatform

/**
 * iOS-side factory for the phase-15.1 rank ladder VM.
 * Swift callers: `RanksAndAchievementsKoinHelper().getRanksAndAchievementsViewModel()`.
 * Mirrors the Phase 15 one-helper-per-VM convention (GamificationUiKoinHelper,
 * AchievementGalleryKoinHelper) per D-151-10. Class, not object; no params;
 * no caching; one getter.
 */
class RanksAndAchievementsKoinHelper {
    fun getRanksAndAchievementsViewModel(): RanksAndAchievementsViewModel =
        KoinPlatform.getKoin().get()
}
