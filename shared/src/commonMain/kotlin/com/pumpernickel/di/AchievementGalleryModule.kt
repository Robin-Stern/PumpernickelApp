package com.pumpernickel.di

import com.pumpernickel.presentation.gamification.AchievementGalleryViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val achievementGalleryModule = module {
    viewModel { AchievementGalleryViewModel(get()) }
}
