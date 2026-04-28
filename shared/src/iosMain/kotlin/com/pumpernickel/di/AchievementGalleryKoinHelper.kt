package com.pumpernickel.di

import com.pumpernickel.presentation.gamification.AchievementGalleryViewModel
import org.koin.mp.KoinPlatform

class AchievementGalleryKoinHelper {
    fun getAchievementGalleryViewModel(): AchievementGalleryViewModel =
        KoinPlatform.getKoin().get()
}
