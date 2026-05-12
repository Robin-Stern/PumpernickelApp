package com.pumpernickel.domain.ai

import com.pumpernickel.domain.model.MuscleGroup

/**
 * D-18-12 — preview is in-memory staging. Until commit() runs, NOTHING is
 * written to the DB. inlineNewExercises is the list of Exercise rows the
 * LLM emitted that don't match anything in the catalog; commit persists
 * them with source="AI" before linking from staged templates.
 */
data class WorkoutAiPreview(
    val templates: List<StagedTemplate>,
    val inlineNewExercises: List<StagedExercise>
)

data class StagedTemplate(
    val name: String,
    val description: String?,
    val exercises: List<StagedTemplateExercise>
)

data class StagedTemplateExercise(
    val exerciseName: String,        // resolved name (matches an existing Exercise OR a StagedExercise)
    val resolvedExerciseId: String?, // non-null if matched against existing Exercise; null if matches a StagedExercise
    val targetSets: Int,
    val targetReps: Int,
    val restPeriodSec: Int,
    val note: String?
)

data class StagedExercise(
    val name: String,
    val primaryMuscles: List<MuscleGroup>,
    val secondaryMuscles: List<MuscleGroup>,
    val equipment: String?,
    val force: String?,
    val mechanic: String?,
    val level: String,
    val category: String,
    val instructions: List<String>
)

/**
 * D-18-03 — F6 form fields.
 */
data class WorkoutAiForm(
    val targetMuscles: List<MuscleGroup>,
    val exerciseCount: Int,                  // 3..8 typical (Claude's discretion in Plan 07)
    val splitStyle: WorkoutAiSplit
)

enum class WorkoutAiSplit(val templateCount: Int) {
    NONE(1),
    PUSH_PULL_LEGS(3),
    UPPER_LOWER(2),
    FULL_BODY(1)
}
