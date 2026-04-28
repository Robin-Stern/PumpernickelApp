package com.pumpernickel.di

import com.pumpernickel.data.db.AchievementStateSeeder
import com.pumpernickel.data.db.AppDatabase
import com.pumpernickel.data.db.GamificationDao
import com.pumpernickel.data.repository.GamificationRepository
import com.pumpernickel.data.repository.GamificationRepositoryImpl
import org.koin.dsl.module

/**
 * Feature-scoped Koin module for gamification DATA-layer bindings.
 * Created in phase-15 plan 03. Mounted from SharedModule.kt via includes(...).
 *
 * Contents:
 *   - GamificationDao accessor (AppDatabase -> DAO)
 *   - GamificationRepository (interface + impl binding)
 *   - AchievementStateSeeder (first-launch catalog seeder)
 *
 * This file SHOULD NOT grow beyond data-layer wiring. Engine / ViewModel
 * bindings live in GamificationEngineModule / GamificationUiModule /
 * AchievementGalleryModule respectively.
 */
val gamificationModule = module {
    single<GamificationDao> { get<AppDatabase>().gamificationDao() }
    single<GamificationRepository> { GamificationRepositoryImpl(get()) }
    single { AchievementStateSeeder(get<GamificationDao>()) }
}
