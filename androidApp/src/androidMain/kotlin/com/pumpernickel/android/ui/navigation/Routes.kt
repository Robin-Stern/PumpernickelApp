package com.pumpernickel.android.ui.navigation

import kotlinx.serialization.Serializable

// Tab root routes
@Serializable data object WorkoutTabRoute
@Serializable data object OverviewTabRoute
@Serializable data object NutritionTabRoute

// Workout tab screens (will be implemented in Phase 12+)
@Serializable data object TemplateListRoute
@Serializable data class TemplateEditorRoute(val templateId: Long? = null)
@Serializable data object ExerciseCatalogRoute
@Serializable data class ExerciseDetailRoute(val exerciseId: String)
@Serializable data object CreateExerciseRoute
@Serializable data class ExercisePickerRoute(val templateId: Long)
@Serializable data class WorkoutSessionRoute(val templateId: Long)
@Serializable data object WorkoutHistoryListRoute
@Serializable data class WorkoutHistoryDetailRoute(val workoutId: Long)
