package com.pumpernickel.di

import com.pumpernickel.data.db.AchievementStateSeeder
import com.pumpernickel.domain.gamification.ApplyRetroactiveGamificationUseCase

/**
 * First-launch gamification trigger. Runs two idempotent steps in order:
 * 1. seed achievement_state table (one locked row per catalog entry)
 * 2. retroactive XP replay (walks completed_workouts + consumption_entries)
 *
 * Both steps short-circuit on re-runs, so calling this on every app launch
 * is safe. Platform startup paths call `run()` from a background coroutine
 * scope after Koin has started.
 */
class GamificationStartup(
    private val seeder: AchievementStateSeeder,
    private val applyRetroactive: ApplyRetroactiveGamificationUseCase
) {
    suspend fun run() {
        seeder.seedIfEmpty()
        applyRetroactive.applyIfNeeded()
    }
}
