package com.pumpernickel.domain.ai

import kotlinx.serialization.Serializable

/**
 * Phase 20 Plan 07 (Smell 4) — pure-Kotlin domain DTO mirroring the JSON wire
 * shape returned by the LLM for the workout-template generation flow.
 *
 * Moved out of `data/api/WorkoutAiSchema.kt` (where the Ktor adapter lives)
 * so `WorkoutAiUseCase` can parse the raw JSON string returned by the
 * `AiClient` port without importing `com.pumpernickel.data.api.*`.
 *
 * `kotlinx.serialization.Serializable` is a KMP-core annotation and does not
 * leak Ktor/HTTP transport detail into the domain layer (see Plan-20-07
 * pitfall guidance — `@Serializable` is allowed in `domain/`).
 *
 * Field shape is verbatim identical to the previous `data/api/WorkoutAiSchema.kt`
 * structures: same property names, same defaults, same types — so the
 * existing JSON contract with providers is preserved 1:1.
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
