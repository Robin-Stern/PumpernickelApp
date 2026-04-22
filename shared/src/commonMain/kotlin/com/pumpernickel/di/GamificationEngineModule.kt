package com.pumpernickel.di

import com.pumpernickel.data.repository.RetroactiveWalker
import com.pumpernickel.domain.gamification.GamificationEngine
import org.koin.dsl.module

/**
 * Feature-scoped Koin module for gamification ENGINE + startup wiring.
 *
 * Populated across multiple plans:
 *   - Plan 04 (wave 3): GamificationEngine binding
 *   - Plan 05 (wave 4): RetroactiveWalker, GamificationStartup bindings
 *   - Plan 07 (wave 4): GoalDayTrigger binding
 *
 * Each plan edits this file to add its bindings without disturbing other
 * modules (Blocker 1 fix -- no wave-level SharedModule.kt conflicts).
 */
val gamificationEngineModule = module {
    single {
        GamificationEngine(
            gamificationRepo = get(),
            completedWorkoutDao = get(),
            nutritionDao = get(),
            exerciseDao = get(),
            settingsRepo = get()
        )
    }
    // Plan 05: retroactive XP replay + ordered first-launch startup helper.
    single { RetroactiveWalker(get(), get(), get(), get()) }
    single { GamificationStartup(get(), get()) }
    // GoalDayTrigger binding added by plan 07.
}
