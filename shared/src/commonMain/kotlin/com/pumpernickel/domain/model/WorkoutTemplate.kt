package com.pumpernickel.domain.model

import com.pumpernickel.data.db.TemplateExerciseEntity
import com.pumpernickel.data.db.WorkoutTemplateEntity

data class WorkoutTemplate(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val exercises: List<TemplateExercise> = emptyList()
)

data class TemplateExercise(
    val id: Long,
    val exerciseId: String,
    val exerciseName: String,
    val primaryMuscles: List<MuscleGroup>,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKgX10: Int,
    val restPeriodSec: Int,
    val exerciseOrder: Int
)

fun WorkoutTemplateEntity.toDomain(
    exercises: List<TemplateExercise> = emptyList()
) = WorkoutTemplate(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    exercises = exercises
)

fun TemplateExerciseEntity.toDomain(
    exerciseName: String,
    primaryMuscles: List<MuscleGroup>
) = TemplateExercise(
    id = id,
    exerciseId = exerciseId,
    exerciseName = exerciseName,
    primaryMuscles = primaryMuscles,
    targetSets = targetSets,
    targetReps = targetReps,
    targetWeightKgX10 = targetWeightKgX10,
    restPeriodSec = restPeriodSec,
    exerciseOrder = exerciseOrder
)

fun formatWeightKg(kgX10: Int): String {
    val whole = kgX10 / 10
    val decimal = kgX10 % 10
    return if (decimal == 0) "$whole kg" else "$whole.$decimal kg"
}

fun parseWeightKgX10(input: String): Int? {
    val value = input.toDoubleOrNull() ?: return null
    if (value < 0) return null
    return (value * 10).toInt()
}
