package com.pumpernickel.di

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
import com.pumpernickel.presentation.workout.WorkoutSessionViewModel
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
}
