package com.pumpernickel.di

import com.pumpernickel.data.geofence.DebugGeofenceProvider
import com.pumpernickel.domain.repository.WorkoutRepository
import com.pumpernickel.presentation.exercises.CreateExerciseViewModel
import com.pumpernickel.presentation.exercises.ExerciseCatalogViewModel
import com.pumpernickel.presentation.exercises.ExerciseDetailViewModel
import com.pumpernickel.presentation.history.WorkoutHistoryViewModel
import com.pumpernickel.presentation.nutrition.DailyLogViewModel
import com.pumpernickel.presentation.nutrition.FoodEntryViewModel
import com.pumpernickel.presentation.nutrition.RecipeCreationViewModel
import com.pumpernickel.presentation.nutrition.RecipeListViewModel
import com.pumpernickel.presentation.overview.OverviewViewModel
import com.pumpernickel.presentation.settings.SettingsViewModel
import com.pumpernickel.presentation.templates.TemplateEditorViewModel
import com.pumpernickel.presentation.templates.TemplateListViewModel
import com.pumpernickel.domain.geofence.EarlyExitTracker
import com.pumpernickel.infrastructure.geofence.GeofenceProvider
import com.pumpernickel.infrastructure.permissions.PermissionController
import com.pumpernickel.presentation.workout.WorkoutSessionViewModel
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

object KoinHelper {
    fun getExerciseCatalogViewModel(): ExerciseCatalogViewModel =
        KoinPlatform.getKoin().get()

    fun getExerciseDetailViewModel(): ExerciseDetailViewModel =
        KoinPlatform.getKoin().get()

    fun getCreateExerciseViewModel(): CreateExerciseViewModel =
        KoinPlatform.getKoin().get()

    fun getTemplateListViewModel(): TemplateListViewModel =
        KoinPlatform.getKoin().get()

    fun getTemplateEditorViewModel(): TemplateEditorViewModel =
        KoinPlatform.getKoin().get()

    fun getWorkoutSessionViewModel(): WorkoutSessionViewModel =
        KoinPlatform.getKoin().get()

    fun getWorkoutHistoryViewModel(): WorkoutHistoryViewModel =
        KoinPlatform.getKoin().get()

    fun getSettingsViewModel(): SettingsViewModel =
        KoinPlatform.getKoin().get()

    fun getOverviewViewModel(): OverviewViewModel =
        KoinPlatform.getKoin().get()

    fun getDailyLogViewModel(): DailyLogViewModel =
        KoinPlatform.getKoin().get()

    fun getFoodEntryViewModel(): FoodEntryViewModel =
        KoinPlatform.getKoin().get()

    fun getRecipeListViewModel(): RecipeListViewModel =
        KoinPlatform.getKoin().get()

    fun getRecipeCreationViewModel(): RecipeCreationViewModel =
        KoinPlatform.getKoin().get()

    /** Phase 19 — exposed for AppDelegate cold-start + Wave 4 SwiftUI views. */
    fun getGeofenceProvider(): GeofenceProvider =
        KoinPlatform.getKoin().get()

    /** Phase 19 — exposed for Wave 4 SwiftUI Permission Rationale Sheet. */
    fun getPermissionController(): PermissionController =
        KoinPlatform.getKoin().get()

    /** Phase 19 — exposed for Wave 4 SwiftUI Settings detail sheet. */
    fun getEarlyExitTracker(): EarlyExitTracker =
        KoinPlatform.getKoin().get()

    /** Exposed for DebugGeofencePanel.swift to read the active session's regionId. */
    fun getWorkoutRepository(): WorkoutRepository =
        KoinPlatform.getKoin().get()

    /**
     * DEBUG-only: loads a Koin module that overrides GeofenceProvider with
     * DebugGeofenceProvider so Phase 19 flows can be exercised on the Simulator.
     * Called from AppDelegate ONLY inside `#if DEBUG`.
     */
    fun loadDebugGeofenceOverride() {
        loadKoinModules(
            module {
                single<GeofenceProvider> { DebugGeofenceProvider() }
            }
        )
    }

    /**
     * Returns the current GeofenceProvider cast to DebugGeofenceProvider, or null
     * if the release binding is active. Used by DebugGeofencePanel.swift to call
     * trigger methods.
     */
    fun getDebugGeofenceProvider(): DebugGeofenceProvider? {
        val provider = KoinPlatform.getKoin().getOrNull<GeofenceProvider>()
        return provider as? DebugGeofenceProvider
    }
}
