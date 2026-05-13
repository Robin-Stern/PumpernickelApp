package com.pumpernickel.data.api

import kotlinx.serialization.Serializable

/**
 * D-18-13 — single LLM call returns an array of templates (length 1 for "no split",
 * up to N for PPL / UL / Full Body / Custom splits).
 *
 * D-18-09 — inlineNewExercises lets the LLM emit Exercise rows the app doesn't
 * have yet. App resolves exerciseName references to either an existing Exercise
 * (case-insensitive trimmed name match) or a freshly-staged inline exercise on
 * Save (D-18-12 — transactional commit on Save, NOT on receive).
 */
@Serializable
data class WorkoutAiResponse(
    val templates: List<WorkoutAiTemplate> = emptyList(),
    val inlineNewExercises: List<WorkoutAiInlineExercise> = emptyList(),
    val refusal: String? = null
)

@Serializable
data class WorkoutAiTemplate(
    val name: String,
    val description: String? = null,
    val exercises: List<WorkoutAiTemplateExercise> = emptyList()
)

@Serializable
data class WorkoutAiTemplateExercise(
    val exerciseName: String,
    val targetSets: Int,
    val targetReps: Int,
    val restPeriodSec: Int,
    val note: String? = null
)

@Serializable
data class WorkoutAiInlineExercise(
    val name: String,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val equipment: String? = null,
    val force: String? = null,
    val mechanic: String? = null,
    val level: String = "intermediate",
    val category: String = "strength",
    val instructions: List<String> = emptyList()
)
