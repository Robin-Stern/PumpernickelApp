package com.pumpernickel.di

import com.pumpernickel.data.db.AppDatabase
import com.pumpernickel.data.db.ProgressPictureDao
import com.pumpernickel.domain.repository.ProgressPictureRepository
import com.pumpernickel.data.repository.ProgressPictureRepositoryImpl
import com.pumpernickel.domain.gamification.NutritionGoalDayPolicy
import com.pumpernickel.presentation.progresspic.ProgressGalleryViewModel
import com.pumpernickel.presentation.progresspic.ProgressPicturePromptViewModel
import com.pumpernickel.presentation.progresspic.ProgressViewerViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Phase 17 — bindings for the progress-pic feature.
 *
 * Mirrors the AchievementGalleryModule convention (one feature module per
 * vertical, included in SharedModule.kt). Platform-context-needing actuals
 * (PhotoVault, PhotoCaptureLauncher, BiometricGate) are bound in
 * PlatformModule.{android,ios}.kt — see Plan 17-07 Task 2.
 *
 * **NutritionGoalDayPolicy registration:** the policy is a Kotlin `object`
 * (singleton). Per the 17-06 hand-off note, it is registered here as
 * `single { NutritionGoalDayPolicy }` so `get()` resolves the object reference
 * — this module is the natural home because the policy is only consumed by
 * `ProgressGalleryViewModel` from this module.
 *
 * **ProgressGalleryViewModel ctor (5 params):** the plan-pinned 3-param ctor
 * was expanded to 5 in 17-06 (Rule 3 deviation — see 17-06-SUMMARY) because
 * the real `NutritionGoalDayPolicy.isGoalDay(entries, goals)` API needs both
 * `NutritionDao` (entries) and `SettingsRepository` (goals) injected.
 * Wiring template from 17-06-SUMMARY "Next Plan Readiness":
 *   ProgressGalleryViewModel(repository, gamificationDao, nutritionGoalDayPolicy,
 *                            nutritionDao, settingsRepository)
 *
 * **Parametrized viewer/prompt VMs:** both Viewer and Prompt take `workoutId: Long`
 * via Koin's `parametersOf` factory pattern. iOS callers use the per-VM
 * KoinHelpers (see 17-07 Task 3); Android callers use
 * `koinViewModel { parametersOf(workoutId) }` from the screen.
 */
val progressGalleryModule = module {
    // DAO accessor (D-17-19 — Room DAO comes from the shared AppDatabase singleton).
    single<ProgressPictureDao> { get<AppDatabase>().progressPictureDao() }

    // NutritionGoalDayPolicy is an object — register the reference so get() resolves it.
    // GamificationDao is bound by gamificationModule (already included in SharedModule).
    // NutritionDao + SettingsRepository are bound globally in SharedModule.
    single { NutritionGoalDayPolicy }

    // Repository
    single<ProgressPictureRepository> {
        ProgressPictureRepositoryImpl(get(), get())
    }

    // ViewModels
    viewModel {
        ProgressGalleryViewModel(
            repository = get(),
            gamificationDao = get(),
            nutritionGoalDayPolicy = get(),
            nutritionDao = get(),
            settingsRepository = get()
        )
    }
    viewModel { (workoutId: Long) ->
        ProgressViewerViewModel(
            workoutId = workoutId,
            repository = get(),
            biometricGate = get()
        )
    }
    viewModel { (workoutId: Long) ->
        ProgressPicturePromptViewModel(
            workoutId = workoutId,
            repository = get(),
            launcher = get()
        )
    }
}
